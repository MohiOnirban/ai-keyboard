import UIKit

private func documentID(_ proxy: UITextDocumentProxy) -> UUID? {
  // The Objective-C getter may return nil while the host disconnects.
  guard let object = proxy as? NSObject,
    let identifier = object.perform(#selector(getter: UITextDocumentProxy.documentIdentifier))?
      .takeUnretainedValue() as? NSUUID
  else { return nil }
  return identifier as UUID
}

private struct Anchor: Equatable {
  let document: UUID
  let before: String?
  let selected: String?
  let after: String?
  init?(_ p: UITextDocumentProxy) {
    guard let id = documentID(p) else { return nil }
    document = id
    before = p.documentContextBeforeInput
    selected = p.selectedText
    after = p.documentContextAfterInput
  }
}

final class KeyboardController: UIInputViewController {
  private let rows = UIStackView()
  private let rules = Phonetic.bundled()
  private let layouts = ["English", "বাংলা", "Phonetic", "Banglish"]
  private var layout = 0, page = 0, shift = 0
  private var raw = "", panel = false, loading = false, editingPreview = false
  private var pasted: String?
  private var message = "", preview = "", source = "", style = "", mode = "rewrite"
  private var origin: Anchor?, request: Task<Void, Never>?, deleteTimer: Timer?, generation = UUID()
  private var replaceSelection = false, targetChanged = false
  private var composition: Anchor?
  private var height: NSLayoutConstraint!
  private var document: UUID?
  private var traits: UIKeyboardType?
  private var wasSensitive = false
  private var isInputVisible = false
  private var typedAt: TimeInterval = 0
  private var proxy: UITextDocumentProxy { textDocumentProxy }
  private var sensitive: Bool {
    proxy.isSecureTextEntry == true
      || [
        UITextContentType.oneTimeCode, .password, .newPassword, .creditCardNumber,
        .creditCardExpiration, .creditCardExpirationMonth, .creditCardExpirationYear,
        .creditCardSecurityCode, .creditCardType, .creditCardName, .creditCardGivenName,
        .creditCardMiddleName, .creditCardFamilyName,
      ].contains {
        $0.rawValue == proxy.textContentType?.rawValue
      }
  }
  override func viewDidLoad() {
    super.viewDidLoad()
    rows.axis = .vertical
    rows.spacing = 5
    rows.translatesAutoresizingMaskIntoConstraints = false
    view.addSubview(rows)
    height = view.heightAnchor.constraint(equalToConstant: 260)
    height.priority = .defaultHigh
    NSLayoutConstraint.activate([
      height, rows.topAnchor.constraint(equalTo: view.topAnchor, constant: 4),
      rows.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 3),
      rows.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -3),
      rows.bottomAnchor.constraint(equalTo: view.bottomAnchor, constant: -4),
    ])
    style = Settings.favorites.first ?? "Respectful"
    render()
  }
  override func viewWillDisappear(_ animated: Bool) {
    super.viewWillDisappear(animated)
    isInputVisible = false
    close()
    deleteTimer?.invalidate()
    deleteTimer = nil
    finishWord()
    for row in rows.arrangedSubviews { row.removeFromSuperview() }
  }
  override func viewWillAppear(_ animated: Bool) {
    super.viewWillAppear(animated)
    isInputVisible = true
    document = documentID(proxy)
    traits = proxy.keyboardType
    wasSensitive = sensitive
    render()
  }
  override func textDidChange(_ textInput: UITextInput?) {
    super.textDidChange(textInput)
    refreshInput()
  }
  override func selectionDidChange(_ textInput: UITextInput?) {
    super.selectionDidChange(textInput)
    refreshInput()
  }
  private func refreshInput() {
    guard isInputVisible else { return }
    let wasComposing = !raw.isEmpty
    let wasPanelOpen = panel
    let activeDocument = documentID(proxy)
    let changed =
      document != activeDocument || traits != proxy.keyboardType
      || wasSensitive != sensitive
    document = activeDocument
    traits = proxy.keyboardType
    wasSensitive = sensitive
    if changed || activeDocument == nil {
      close()
      raw = ""
      composition = nil
    } else {
      checkComposition()
      if let origin, !targetChanged, origin != Anchor(proxy) {
        invalidateTarget()
        return
      }
    }
    if isViewLoaded && (changed || (wasComposing && raw.isEmpty) || (wasPanelOpen && !panel)) {
      render()
    }
  }
  private func invalidateTarget() {
    targetChanged = true
    if loading {
      generation = UUID()
      request?.cancel()
      request = nil
      loading = false
      origin = nil
      source = ""
      message = "Draft changed. Generate again."
    } else {
      editingPreview = false
      message = "Draft changed. Copy the suggestion or cancel."
    }
    if isViewLoaded { render() }
  }
  private func close() {
    generation = UUID()
    request?.cancel()
    request = nil
    loading = false
    panel = false
    origin = nil
    targetChanged = false
    replaceSelection = false
    editingPreview = false
    preview = ""
    source = ""
    pasted = nil
    message = ""
  }
  private func finishWord() {
    let word = takeWord()
    if !word.isEmpty { proxy.insertText(word) }
  }
  private func takeWord() -> String {
    checkComposition()
    let word = raw.isEmpty ? "" : (rules?.render(raw) ?? raw)
    raw = ""
    composition = nil
    return word
  }
  private func checkComposition() {
    guard let composition, sensitive || composition != Anchor(proxy) else { return }
    raw = ""
    self.composition = nil
  }
  private func insert(_ s: String) {
    let needsRefresh = editingPreview || shift == 1 || layout == 2
    if editingPreview {
      preview += s
    } else if !sensitive, layout == 2, page == 0, s.count == 1,
      s.utf8.allSatisfy({ (65...90).contains($0) || (97...122).contains($0) })
    {
      checkComposition()
      if raw.count >= 96 { finishWord() }
      if raw.isEmpty { composition = Anchor(proxy) }
      if composition != nil { raw += s } else { proxy.insertText(s) }
    } else {
      proxy.insertText(takeWord() + s)
    }
    if shift == 1 { shift = 0 }
    if needsRefresh { render() }
  }
  @objc private func erase() {
    checkComposition()
    if editingPreview {
      if !preview.isEmpty { preview.removeLast() }
    } else if !raw.isEmpty {
      raw.removeLast()
      if raw.isEmpty { composition = nil }
    } else {
      proxy.deleteBackward()
    }
    if deleteTimer == nil { render() }
  }
  @objc private func heldDelete(_ g: UILongPressGestureRecognizer) {
    if g.state == .began {
      deleteTimer = Timer.scheduledTimer(withTimeInterval: 0.085, repeats: true) { [weak self] _ in
        self?.erase()
      }
    }
    if [.ended, .cancelled, .failed].contains(g.state) {
      deleteTimer?.invalidate()
      deleteTimer = nil
      render()
    }
  }
  private func start(_ input: String, at anchor: Anchor, replace: Bool) {
    guard hasFullAccess, !sensitive else {
      message = "Enable Allow Full Access for AI."
      render()
      return
    }
    guard input.utf16.count <= 1600 else {
      message = "Select a shorter passage (1600 characters maximum)."
      render()
      return
    }
    let bounded = input
    guard !bounded.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
      message = "Select text, type a draft, or tap Paste."
      render()
      return
    }
    source = bounded
    origin = anchor
    targetChanged = false
    replaceSelection = replace
    preview = ""
    loading = true
    editingPreview = false
    message = "Generating…"
    let id = UUID()
    let chosenMode = mode
    let chosenStyle = style
    generation = id
    request?.cancel()
    request = Task { [weak self] in
      guard let self else { return }
      do {
        let answer = try await Service.shared.generate(bounded, mode: chosenMode, style: chosenStyle)
        guard id == generation, !Task.isCancelled else { return }
        guard hasFullAccess, !sensitive, origin == Anchor(proxy) else {
          invalidateTarget()
          return
        }
        preview = answer.suggestion
        message = ""
      } catch { if id == generation { message = error.localizedDescription } }
      if id == generation {
        loading = false
        render()
      }
    }
    render()
  }
  private func apply() {
    guard !preview.isEmpty, !sensitive else { return }
    guard let current = Anchor(proxy), !targetChanged, current == origin else {
      message = "Draft changed. Copy the suggestion or cancel."
      render()
      return
    }
    let selection = current.selected ?? ""
    guard replaceSelection ? selection == source : selection.isEmpty else {
      message = "Move the cursor or copy the suggestion."
      render()
      return
    }
    // UIKit replaces the host selection in one insertion. Without a selection, insert at the cursor.
    proxy.insertText(preview)
    close()
    render()
  }
  private var accent: UIColor {
    UIColor {
      $0.userInterfaceStyle == .dark
        ? UIColor(red: 0.46, green: 0.85, blue: 0.72, alpha: 1)
        : UIColor(red: 0.07, green: 0.42, blue: 0.35, alpha: 1)
    }
  }
  private func button(_ title: String, _ action: @escaping () -> Void) -> UIButton {
    let b = UIButton(type: .system)
    b.setTitle(title, for: .normal)
    b.setTitleColor(title == "AI" ? accent : .label, for: .normal)
    b.titleLabel?.font = .systemFont(
      ofSize: title.count > 2 ? 14 : 19, weight: title == "AI" ? .bold : .regular)
    b.titleLabel?.adjustsFontSizeToFitWidth = true
    b.backgroundColor = .secondarySystemGroupedBackground
    b.layer.cornerRadius = 6
    b.addAction(
      UIAction { [weak self] _ in
        if Settings.prefs.bool(forKey: "haptics") {
          UISelectionFeedbackGenerator().selectionChanged()
        }
        guard self != nil else { return }
        action()
      }, for: .touchUpInside)
    return b
  }
  private func row(_ buttons: [UIButton]) {
    let stack = UIStackView(arrangedSubviews: buttons)
    stack.axis = .horizontal
    stack.spacing = 3
    stack.distribution = .fillEqually
    rows.addArrangedSubview(stack)
  }
  private func menu(_ title: String, _ items: [String], _ change: @escaping (String) -> Void)
    -> UIButton
  {
    let b = button(title, {})
    b.menu = UIMenu(children: items.map { value in UIAction(title: value) { _ in change(value) } })
    b.showsMenuAsPrimaryAction = true
    return b
  }
  private func render() {
    guard deleteTimer == nil else { return }
    overrideUserInterfaceStyle =
      ["Light": UIUserInterfaceStyle.light, "Dark": .dark][Settings.get("theme")] ?? .unspecified
    view.backgroundColor = .systemGroupedBackground
    for old in rows.arrangedSubviews {
      rows.removeArrangedSubview(old)
      old.removeFromSuperview()
    }
    let numeric = [UIKeyboardType.numberPad, .phonePad, .decimalPad, .asciiCapableNumberPad]
      .contains(proxy.keyboardType ?? .default)
    let isWide = view.bounds.width > 620 && traitCollection.verticalSizeClass == .compact
    height.constant = (isWide ? 200 : 252) + (panel ? 66 : 0)
    rows.distribution = .fillEqually
    if panel && !sensitive {
      let label = UILabel()
      label.text = if preview.isEmpty {
        message.isEmpty ? "Select a draft or paste a message" : message
      } else {
        message.isEmpty ? preview : message + "\n" + preview
      }
      if !raw.isEmpty {
        label.text = [label.text ?? "", rules?.render(raw) ?? raw].joined(separator: "\n")
      }
      label.font = .preferredFont(forTextStyle: .footnote)
      label.adjustsFontForContentSizeCategory = true
      if !preview.isEmpty {
        let scroll = UIScrollView()
        label.numberOfLines = 0
        label.translatesAutoresizingMaskIntoConstraints = false
        scroll.addSubview(label)
        NSLayoutConstraint.activate([
          label.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor),
          label.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor),
          label.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor, constant: 4),
          label.trailingAnchor.constraint(equalTo: scroll.contentLayoutGuide.trailingAnchor, constant: -4),
          label.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor, constant: -8),
        ])
        rows.addArrangedSubview(scroll)
      } else {
        label.numberOfLines = 2
        rows.addArrangedSubview(label)
      }
      if preview.isEmpty {
        row([
          menu(mode.capitalized, ["rewrite", "reply", "express"]) {
            guard !self.loading else { return }
            self.mode = $0
            self.render()
          },
          menu(
            style,
            Array(NSOrderedSet(array: Settings.favorites + Settings.styles)) as? [String]
              ?? Settings.styles
          ) {
            guard !self.loading else { return }
            self.style = $0
            self.render()
          },
          button("Generate") {
            guard !self.loading, !self.sensitive else { return }
            guard self.hasFullAccess else {
              self.message = "Enable Allow Full Access for AI."
              self.render()
              return
            }
            self.finishWord()
            guard let a = Anchor(self.proxy) else {
              self.message = "Focus a text field to use AI."
              self.render()
              return
            }
            let draft = self.mode == "reply" ? "" : String((a.before ?? "").suffix(400))
            let text = self.pasted ?? a.selected ?? draft
            if self.mode == "reply" && text.isEmpty {
              self.message = "Paste or select the incoming message first."
              self.render()
              return
            }
            let replace = self.pasted == nil && self.mode != "reply" && a.selected?.isEmpty == false
            self.start(text, at: a, replace: replace)
          },
          button("Paste") {
            guard !self.loading, !self.sensitive, self.hasFullAccess else { return }
            if let value = UIPasteboard.general.string, !value.isEmpty, value.utf16.count <= 1600 {
              self.pasted = value
              self.message = "Ready to generate from: " + String(value.prefix(100))
            } else {
              self.pasted = nil
              self.message = "Paste up to 1600 characters."
            }
            self.render()
          },
          button("Close") {
            self.close()
            self.render()
          },
        ])
      } else {
        row([
          button(replaceSelection ? "Replace" : "Insert") { self.apply() },
          button(editingPreview ? "Done" : "Edit") {
            self.editingPreview.toggle()
            self.render()
          },
          button("Copy") {
            UIPasteboard.general.string = self.preview
            self.close()
            self.render()
          }, button("Again") {
            guard let a = self.origin, !self.targetChanged, a == Anchor(self.proxy) else { return }
            self.start(self.source, at: a, replace: self.replaceSelection)
          },
          button("Cancel") {
            self.close()
            self.render()
          },
        ])
      }
    } else {
      var tools = [
        button("←") {
          self.finishWord()
          self.proxy.adjustTextPosition(byCharacterOffset: -1)
          self.render()
        },
        button("→") {
          self.finishWord()
          self.proxy.adjustTextPosition(byCharacterOffset: 1)
          self.render()
        },
      ]
      if !sensitive {
        if !raw.isEmpty {
          var choices = rules?.candidates(raw) ?? [raw]
          if let rules,
            let completion = rules.words.keys.filter({ $0.hasPrefix(raw) && $0 != raw }).min(by: {
              ($0.count, $0) < ($1.count, $1)
            }), let word = rules.words[completion], !choices.contains(word)
          {
            choices.insert(word, at: min(1, choices.count))
          }
          tools.append(contentsOf: choices.prefix(3).enumerated().map { index, word in
            let choice = button(word) {
              guard !self.takeWord().isEmpty else { return }
              self.proxy.insertText(word + " ")
              self.render()
            }
            if index == 0 { choice.setTitleColor(accent, for: .normal) }
            return choice
          })
        } else {
          tools.append(button("Paste") {
            guard !self.sensitive else { return }
            if let s = UIPasteboard.general.string { self.proxy.insertText(s) }
          })
        }
        tools.append(
          button("AI") {
            guard !self.sensitive else { return }
            self.finishWord()
            self.panel = true
            self.message =
              self.hasFullAccess
              ? "AI sends provided text to the service." : "Allow Full Access to use AI."
            self.render()
          })
      }
      row(tools)
    }
    let chars: [[String]]
    if numeric {
      chars = [["1", "2", "3"], ["4", "5", "6"], ["7", "8", "9"], [".", "0", "+"]]
    } else if page == 2 {
      chars = [
        ["🙂", "😄", "😂", "🥹", "😍", "🤔"], ["😅", "🙃", "🥲", "😎", "🤍", "❤️"],
        ["🙏", "👍", "✨", "🎉", "🌸", "🔥"],
      ]
    } else if page == 1 {
      chars = ["1234567890", "@#$%&*-+()", "!?.,:;/_'।"].map { $0.map(String.init) }
    } else if layout == 1 {
      let groups =
        shift == 0
        ? ["অআইঈউঊঋএঐওঔ", "কখগঘঙচছজঝঞ", "টঠডঢণতথদধন", "পফবভমযরলশষসহ"]
        : ["ািীুূৃেৈোৌ", "্ংঃঁড়ঢ়য়ৎ", "০১২৩৪৫৬৭৮৯", "।,?!"]
      if shift == 0 {
        chars = groups.map { $0.map(String.init) }
      } else {
        chars = [
          groups[0].unicodeScalars.map(String.init), ["্", "ং", "ঃ", "ঁ", "ড়", "ঢ়", "য়", "ৎ"],
          groups[2].map(String.init), groups[3].map(String.init),
        ]
      }
    } else {
      chars = ["qwertyuiop", "asdfghjkl", "zxcvbnm"].map {
        $0.map { shift == 0 ? String($0) : String($0).uppercased() }
      }
    }
    for group in chars { row(group.map { s in button(s) { self.insert(s) } }) }
    let globe = button("🌐") {}
    globe.accessibilityLabel = "Next keyboard"
    globe.addTarget(self, action: #selector(handleInputModeList(from:with:)), for: .allTouchEvents)
    let del = button("⌫") { self.erase() }
    del.addGestureRecognizer(
      UILongPressGestureRecognizer(target: self, action: #selector(heldDelete(_:))))
    var bar = [
      globe,
      menu(layouts[layout], layouts) {
        self.finishWord()
        self.layout = self.layouts.firstIndex(of: $0) ?? 0
        self.page = 0
        self.render()
      },
    ]
    if !numeric {
      bar += [
        button(shift == 2 ? "⇪" : "⇧") {
          let now = Date.timeIntervalSinceReferenceDate
          self.shift = self.shift == 1 && now - self.typedAt < 0.5 ? 2 : (self.shift == 0 ? 1 : 0)
          self.typedAt = now
          self.render()
        },
        button(page == 0 ? "123" : "ABC") {
          self.finishWord()
          self.page = self.page == 0 ? 1 : 0
          self.render()
        },
        button("☺") {
          self.finishWord()
          self.page = self.page == 2 ? 0 : 2
          self.render()
        },
      ]
      if proxy.keyboardType == .emailAddress || proxy.keyboardType == .URL {
        bar.append(
          button(proxy.keyboardType == .URL ? "/" : "@") {
            self.insert(self.proxy.keyboardType == .URL ? "/" : "@")
          })
      }
    }
    bar += [button("Space") { self.insert(" ") }, del, button("↵") { self.insert("\n") }]
    row(bar)
  }
}
