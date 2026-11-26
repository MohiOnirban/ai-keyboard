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

// Both toolbar states stay mounted. A candidate transition only changes titles, visibility
// and frames inside this view; it cannot invalidate the outer five-row keyboard layout.
private final class TypingToolbar: UIView {
  private let actions: UIView
  private let tools: UIButton
  private let choices: [UIButton]
  private(set) var candidates: [String] = []

  init(actions: UIView, tools: UIButton, choices: [UIButton]) {
    self.actions = actions
    self.tools = tools
    self.choices = choices
    super.init(frame: .zero)
    addSubview(actions)
    addSubview(tools)
    tools.isHidden = true
    for key in choices {
      addSubview(key)
      key.isHidden = true
    }
  }

  required init?(coder: NSCoder) { fatalError() }

  func show(_ words: [String]) {
    guard candidates != words else { return }
    let previousCount = candidates.count
    candidates = words
    for (index, key) in choices.enumerated() {
      let word = index < words.count ? words[index] : nil
      if key.currentTitle != word {
        if key.isTracking { key.cancelTracking(with: nil) }
        key.setTitle(word, for: .normal)
        key.accessibilityLabel = word
      }
      if key.isHidden != (word == nil) { key.isHidden = word == nil }
    }
    if (previousCount == 0) != words.isEmpty {
      actions.isHidden = !words.isEmpty
      tools.isHidden = words.isEmpty
    }
    if previousCount != words.count { setNeedsLayout() }
  }

  override func layoutSubviews() {
    super.layoutSubviews()
    actions.frame = bounds
    guard !candidates.isEmpty else { return }
    let toolsWidth = min(44, bounds.width)
    tools.frame = CGRect(x: 0, y: 0, width: toolsWidth, height: bounds.height)
    let spacing: CGFloat = 2
    let count = candidates.count
    let width = max(0, bounds.width - toolsWidth - spacing * CGFloat(count - 1)) / CGFloat(count)
    for index in 0..<count {
      choices[index].frame = CGRect(
        x: toolsWidth + CGFloat(index) * (width + spacing),
        y: 0, width: width, height: bounds.height)
    }
  }
}

final class KeyboardController: UIInputViewController {
  private let rows = UIStackView()
  private let rules = Phonetic.bundled()
  private lazy var banglaWords = Array(Set(rules?.words.values.map { $0 } ?? [])).sorted()
  private weak var typingToolbar: TypingToolbar?
  private let layouts = ["English", "বাংলা", "Phonetic", "Banglish"]
  private var layout = 0, page = 0, shift = 0
  private var raw = "", loading = false, editingPreview = false
  private var tool: Tool?
  private var panel: Bool { tool != nil }
  private var editingInput = false, editingInstructions = false
  private var editorRaw = "", askInstruction: String?
  private var localInput = "", prefix = ""
  private var sourceLanguage = "auto", targetLanguage = "bn"
  private var lastWidth: CGFloat = 0
  private var savedPage: Int?, savedShift: Int?, savedLayout: Int?
  private var numericHost: Bool {
    [.numberPad, .phonePad, .decimalPad, .asciiCapableNumberPad, .numbersAndPunctuation].contains(
      proxy.keyboardType ?? .default)
  }
  private var numericPage = false
  private var delayedTraits: DispatchWorkItem?
  private var hasEditor: Bool { editingPreview || editingInput }
  private var currentEditorText: String { editingInput ? localInput : preview }
  private let tones = [
    "My Style", "Casual", "Professional", "Polite", "Romantic", "Funny", "Empathetic", "Shorter",
    "Expand",
  ]
  private let languageNames = [
    "auto": "Detect", "bn": "Bangla", "banglish": "Banglish", "en": "English",
  ]
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
  private static let protectedTypes: Set<UITextContentType> = [
    .oneTimeCode, .password, .newPassword, .creditCardNumber, .creditCardExpiration,
    .creditCardExpirationMonth, .creditCardExpirationYear, .creditCardSecurityCode, .creditCardType,
    .creditCardName, .creditCardGivenName, .creditCardMiddleName, .creditCardFamilyName,
  ]
  private static let wordScalars = CharacterSet.letters.union(.nonBaseCharacters)

  private var sensitive: Bool {
    let input = proxy
    if input.isSecureTextEntry == true { return true }
    guard let type = input.textContentType ?? nil else { return false }
    return Self.protectedTypes.contains(type)
  }
  override func viewDidLoad() {
    super.viewDidLoad()
    rows.axis = .vertical
    rows.spacing = Deck.rowGap
    rows.translatesAutoresizingMaskIntoConstraints = false
    view.addSubview(rows)
    height = view.heightAnchor.constraint(equalToConstant: 260)
    height.priority = .defaultHigh
    NSLayoutConstraint.activate([
      height, rows.topAnchor.constraint(equalTo: view.topAnchor, constant: 4),
      rows.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 3),
      rows.trailingAnchor.constraint(
        equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -3),
      rows.bottomAnchor.constraint(equalTo: view.bottomAnchor, constant: -4),
    ])
    style = Settings.favorites.first ?? "Respectful"
    render()
  }
  override func viewWillDisappear(_ animated: Bool) {
    super.viewWillDisappear(animated)
    isInputVisible = false
    delayedTraits?.cancel()
    close()
    deleteTimer?.invalidate()
    deleteTimer = nil
    finishWord()
    prefix = ""
    for row in rows.arrangedSubviews { row.removeFromSuperview() }
  }
  override func viewWillAppear(_ animated: Bool) {
    super.viewWillAppear(animated)
    isInputVisible = true
    document = documentID(proxy)
    traits = proxy.keyboardType
    wasSensitive = sensitive
    numericPage = numericHost
    render()
  }
  override func viewDidAppear(_ animated: Bool) {
    super.viewDidAppear(animated)
    checkLateTraits()
    refreshInput()
  }
  // WebKit can notify before its new UITextInputTraits have reached the extension proxy.
  // Coalesce a single trailing metadata read. No document text is read on this timer.
  private func checkLateTraits() {
    delayedTraits?.cancel()
    let work = DispatchWorkItem { [weak self] in
      guard let self, self.isInputVisible else { return }
      if self.traits != self.proxy.keyboardType || self.wasSensitive != self.sensitive {
        self.refreshInput()
      }
    }
    delayedTraits = work
    DispatchQueue.main.asyncAfter(deadline: .now() + 0.3, execute: work)
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
      if changed { checkLateTraits() }
      close()
      prefix = ""
      numericPage = numericHost
      raw = ""
      composition = nil
    } else {
      checkComposition()
      if !prefix.isEmpty, proxy.documentContextBeforeInput?.hasSuffix(prefix) == false {
        prefix = ""
        if tool == nil { updateToolbar() }
      }
      if let origin, !targetChanged, origin != Anchor(proxy) {
        invalidateTarget()
        return
      }
    }
    guard isViewLoaded else { return }
    if changed || (wasPanelOpen && !panel) {
      render()
    } else if wasComposing && raw.isEmpty {
      updateToolbar()
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
      restoreEditor()
      message = "Draft changed. Copy the suggestion or cancel."
    }
    if isViewLoaded { render() }
  }
  private func close() {
    generation = UUID()
    request?.cancel()
    request = nil
    loading = false
    restoreEditor()
    tool = nil
    askInstruction = nil
    localInput = ""
    origin = nil
    targetChanged = false
    replaceSelection = false
    editingPreview = false
    preview = ""
    source = ""
    alternatives = []
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
  private func finishEditorWord(_ suffix: String = "") {
    let word = editorRaw.isEmpty ? "" : rules?.render(editorRaw) ?? editorRaw
    let value = word + suffix
    guard (currentEditorText + value).utf16.count <= (editingInstructions ? 500 : 1600) else {
      return
    }
    if editingPreview { preview += value } else { localInput += value }
    editorRaw = ""
  }
  private func insert(_ s: String) {
    if hasEditor {
      if layout == 2, page == 0, s.count == 1,
        s.utf8.allSatisfy({ (65...90).contains($0) || (97...122).contains($0) })
      {
        if editorRaw.count >= 96 { finishEditorWord() }
        if (currentEditorText + editorRaw + s).utf16.count <= (editingInstructions ? 500 : 1600) {
          editorRaw += s
        }
      } else {
        finishEditorWord(s)
      }
      if shift == 1 { shift = 0 }
      render()
      return
    }
    let allowSuggestions = !sensitive
    if allowSuggestions, layout == 2, page == 0, s.count == 1,
      s.utf8.allSatisfy({ (65...90).contains($0) || (97...122).contains($0) })
    {
      checkComposition()
      if raw.count >= 96 { finishWord() }
      if raw.isEmpty { composition = Anchor(proxy) }
      if composition != nil { raw += s } else { proxy.insertText(s) }
    } else {
      proxy.insertText(takeWord() + s)
    }
    if allowSuggestions, layout != 2, s.unicodeScalars.count == 1,
      let scalar = s.unicodeScalars.first, Self.wordScalars.contains(scalar)
    {
      prefix = String((prefix + s).suffix(32))
    } else {
      prefix = ""
    }
    forceTools = false
    if shift == 1 {
      shift = 0
      render()
    } else if tool == nil {
      updateToolbar()
    }
  }
  @objc private func erase() {
    checkComposition()
    if hasEditor && !editorRaw.isEmpty {
      editorRaw.removeLast()
    } else if editingInput {
      if !localInput.isEmpty { localInput.removeLast() }
    } else if editingPreview {
      if !preview.isEmpty { preview.removeLast() }
    } else if !raw.isEmpty {
      raw.removeLast()
      if raw.isEmpty { composition = nil }
    } else {
      if !prefix.isEmpty { prefix.removeLast() }
      proxy.deleteBackward()
    }
    if deleteTimer == nil { if hasEditor { render() } else if tool == nil { updateToolbar() } }
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
    if source != bounded || origin != anchor { alternatives = [] }
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
    let chosenStyle = tool == .grammar ? "fix grammar" : tool == .translate ? "translation" : style
    let target = tool == .translate ? targetLanguage : nil
    let instruction =
      tool == .translate
      ? "Source language: " + (languageNames[sourceLanguage] ?? "Detect")
      : tool == .ask ? askInstruction : nil
    generation = id
    request?.cancel()
    request = Task { [weak self] in
      guard let self else { return }
      do {
        let answer = try await Service.shared.generate(
          bounded, mode: chosenMode, style: chosenStyle, language: target, instruction: instruction)
        guard id == generation, !Task.isCancelled else { return }
        guard hasFullAccess, !sensitive, origin == Anchor(proxy) else {
          invalidateTarget()
          return
        }
        preview = answer.suggestion
        if tool == .tone && !alternatives.contains(where: { $0.text == self.preview }) {
          alternatives.append((chosenStyle, preview))
          if alternatives.count > 6 { alternatives.removeFirst() }
        }
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
    if !raw.isEmpty && !replaceSelection {
      proxy.insertText(takeWord() + preview)
    } else {
      proxy.insertText(preview)
    }
    prefix = ""
    close()
    render()
  }

  override func viewDidLayoutSubviews() {
    super.viewDidLayoutSubviews()
    if isInputVisible && traits != proxy.keyboardType { refreshInput() }
    let width = view.safeAreaLayoutGuide.layoutFrame.width.rounded()
    if width > 0 && lastWidth != width {
      lastWidth = width
      render()
    }
  }
  private func button(
    _ title: String, _ kind: DeckKey.Kind = .function, action: @escaping () -> Void
  ) -> DeckKey {
    let key = DeckKey(title, kind: kind)
    key.addAction(
      UIAction { [weak self] _ in
        guard self != nil else { return }
        if Settings.prefs.bool(forKey: "haptics") {
          UISelectionFeedbackGenerator().selectionChanged()
        }
        action()
      }, for: .touchUpInside)
    return key
  }
  private func glyph(
    _ image: String, _ name: String, _ kind: DeckKey.Kind = .function,
    _ action: @escaping () -> Void
  ) -> DeckKey {
    let key = button("", kind, action: action)
    key.setImage(
      UIImage(
        systemName: image,
        withConfiguration: UIImage.SymbolConfiguration(pointSize: 18, weight: .medium)),
      for: .normal)
    key.tintColor = kind == .accent ? .white : Deck.ink
    key.accessibilityLabel = name
    return key
  }
  private func menu(
    _ title: String, choices: [(String, String)], action: @escaping (String) -> Void
  ) -> DeckKey {
    let key = button(title) {}
    key.menu = UIMenu(
      children: choices.map { value, label in UIAction(title: label) { _ in action(value) } })
    key.showsMenuAsPrimaryAction = true
    return key
  }
  private func chooseLayout(_ index: Int) {
    if hasEditor { finishEditorWord() } else { finishWord() }
    prefix = ""
    layout = index
    shift = 0
    page = 0
    numericPage = false
    render()
  }
  private func languageKey() -> DeckKey {
    let key = menu("◉", choices: layouts.enumerated().map { (String($0.offset), $0.element) }) {
      self.chooseLayout(Int($0) ?? 0)
    }
    key.setTitle("", for: .normal)
    key.setImage(UIImage(systemName: "character.bubble"), for: .normal)
    key.tintColor = Deck.ink
    key.accessibilityLabel = "Typing language"
    return key
  }
  private func switchKey() -> DeckKey {
    guard needsInputModeSwitchKey, !hasEditor else { return languageKey() }
    let key = glyph("globe", "Next keyboard") {}
    key.addTarget(self, action: #selector(handleInputModeList(from:with:)), for: .allTouchEvents)
    return key
  }
  private func lane(_ entries: [(UIView, CGFloat)], in stack: UIStackView) {
    stack.addArrangedSubview(KeyLane(entries))
  }
  private func stack(_ axis: NSLayoutConstraint.Axis, gap: CGFloat = Deck.rowGap) -> UIStackView {
    let s = UIStackView()
    s.axis = axis
    s.spacing = gap
    return s
  }
  private func words() -> [String] {
    guard !sensitive, !hasEditor else { return [] }
    if !raw.isEmpty {
      var options = rules?.candidates(raw) ?? [raw]
      if let rules,
        let best = rules.words.keys.filter({ $0.hasPrefix(raw) && $0 != raw }).min(by: {
          ($0.count, $0) < ($1.count, $1)
        }),
        let word = rules.words[best], !options.contains(word)
      {
        options.insert(word, at: min(1, options.count))
      }
      return Array(options.prefix(3))
    }
    guard prefix.count >= 2 else { return [] }
    let dict: [String]
    switch layout {
    case 0: dict = KeyMaps.english
    case 1: dict = banglaWords
    case 3: dict = KeyMaps.banglish
    default: return []
    }
    let typed = prefix.lowercased()
    let length = prefix.count
    return Array(
      dict.lazy.filter { $0.lowercased().hasPrefix(typed) && $0.count > length }.prefix(3))
  }
  private func choose(_ word: String) {
    guard !sensitive else { return }
    if !raw.isEmpty {
      guard !takeWord().isEmpty else {
        updateToolbar()
        return
      }
      proxy.insertText(word + " ")
    } else {
      guard !prefix.isEmpty, proxy.selectedText?.isEmpty != false,
        proxy.documentContextBeforeInput?.hasSuffix(prefix) == true
      else {
        prefix = ""
        updateToolbar()
        return
      }
      proxy.insertText(String(word.dropFirst(prefix.count)) + " ")
    }
    prefix = ""
    updateToolbar()
  }
  private func makeToolbar() -> TypingToolbar {
    let bar = stack(.horizontal, gap: 0)
    bar.distribution = .fillEqually
    let available = lastWidth > 0 ? lastWidth : view.bounds.width
    let capacity = max(2, Int((available - 6) / 44))
    let order = Tool.allCases
    let visible: [Tool]
    if capacity >= order.count {
      visible = order
    } else {
      visible = Array(order.filter { $0 != .more }.prefix(capacity - 1)) + [.more]
    }
    for tool in visible {
      let b = button("", tool.ai ? .accent : .transparent) { self.open(tool) }
      b.setImage(tool.icon(), for: .normal)
      b.tintColor = tool.ai ? .white : Deck.ink
      if tool.ai {
        // The brand-colored glyph keeps its full-width tap target.
        b.face.backgroundColor = .clear
        b.configuration = .plain()
        b.configuration?.image = tool.icon()
        b.configuration?.baseForegroundColor = Deck.color(0x006B58, 0x8ADAC1)
      }
      b.accessibilityLabel = tool.rawValue
      b.isEnabled = !sensitive || [.more, .voice].contains(tool)
      b.alpha = b.isEnabled ? 1 : 0.35
      bar.addArrangedSubview(b)
    }
    let grid = glyph("square.grid.2x2", "Show keyboard tools", .transparent) { [weak self] in
      // Keep phonetic composition pending while exposing the tools.
      self?.forceTools = true
      self?.updateToolbar()
    }
    let choices = (0..<3).map { index in
      button("", .transparent) { [weak self] in
        guard let self, let toolbar = self.typingToolbar, toolbar.candidates.indices.contains(index)
        else { return }
        self.choose(toolbar.candidates[index])
      }
    }
    let toolbar = TypingToolbar(actions: bar, tools: grid, choices: choices)
    typingToolbar = toolbar
    toolbar.show(forceTools ? [] : words())
    return toolbar
  }
  private var alternatives: [(style: String, text: String)] = []
  private var forceTools = false
  private func updateToolbar() {
    guard tool == nil, let typingToolbar else { return }
    typingToolbar.show(forceTools ? [] : words())
  }
  private var toolbarHeight: CGFloat {
    traitCollection.verticalSizeClass == .compact ? 35 : Deck.toolbar
  }
  private func open(_ next: Tool) {
    guard !sensitive || [.more, .voice].contains(next) else { return }
    close()
    tool = next
    mode = next == .ask ? "express" : "rewrite"
    style = next == .tone ? "My Style" : (Settings.favorites.first ?? "Respectful")
    if next.ai && !hasFullAccess { message = "Allow Full Access to use AI." }
    render()
  }
  private func render() {
    guard deleteTimer == nil, isViewLoaded else { return }
    overrideUserInterfaceStyle =
      ["Light": UIUserInterfaceStyle.light, "Dark": .dark][Settings.get("theme")] ?? .unspecified
    view.backgroundColor = Deck.background
    rows.arrangedSubviews.forEach {
      rows.removeArrangedSubview($0)
      $0.removeFromSuperview()
    }
    height.constant = Deck.height(
      compact: traitCollection.verticalSizeClass == .compact,
      preference: Settings.get("height", "Standard"))
    rows.distribution = .fill
    if hasEditor {
      renderEditor()
      return
    }
    if let tool {
      if tool.ai { renderAI(tool) } else { renderUtility(tool) }
      return
    }
    let bar = makeToolbar()
    rows.addArrangedSubview(bar)
    bar.heightAnchor.constraint(equalToConstant: toolbarHeight).isActive = true
    renderKeys()
  }
  private func character(_ text: String, alternate: String? = nil) -> DeckKey {
    let key = button(text, .letter) { self.insert(text) }
    if let alternate {
      key.hint.text = alternate
      key.alternate = { [weak self] in self?.insert(alternate) }
      key.accessibilityHint = "Hold for " + alternate
      key.addGestureRecognizer(
        UILongPressGestureRecognizer(target: key, action: #selector(DeckKey.held(_:))))
    }
    return key
  }
  private func deleteKey() -> DeckKey {
    let b = glyph("delete.left", "Backspace") { self.erase() }
    b.addGestureRecognizer(
      UILongPressGestureRecognizer(target: self, action: #selector(heldDelete(_:))))
    return b
  }
  private func shiftKey() -> DeckKey {
    glyph(shift == 2 ? "capslock.fill" : shift == 1 ? "shift.fill" : "shift", "Shift") {
      let t = Date.timeIntervalSinceReferenceDate
      self.shift = self.shift == 1 && t - self.typedAt < 0.55 ? 2 : self.shift == 0 ? 1 : 0
      self.typedAt = t
      self.render()
    }
  }
  private func setPage(_ value: Int, numeric: Bool = false) {
    if hasEditor { finishEditorWord() } else { finishWord() }
    prefix = ""
    page = value
    numericPage = numeric
    render()
  }
  private func enterKey() -> DeckKey {
    let label: String
    switch proxy.returnKeyType {
    case .search, .google, .yahoo: label = "Search"
    case .next: label = "Next"
    case .done: label = "Done"
    case .send: label = "Send"
    case .go: label = "Go"
    case .join: label = "Join"
    default: label = "Return"
    }
    if hasEditor || label == "Return" {
      return glyph("return", "Return", .accent) { self.insert("\n") }
    }
    return button(label, .accent) { self.insert("\n") }
  }
  private func renderKeys() {
    let keys = stack(.vertical)
    keys.distribution = .fillEqually
    rows.addArrangedSubview(keys)
    if numericPage {
      for (i, group) in [["1", "2", "3"], ["4", "5", "6"], ["7", "8", "9"]].enumerated() {
        let op = ["+", "−", "×"][i]
        let left = button(op) { self.insert(op) }
        var items: [(UIView, CGFloat)] = [(left, 1.5)]
        items += group.map { (character($0), 7.0 / 3) }
        let side =
          i == 0
          ? deleteKey()
          : i == 1 ? button("%") { self.insert("%") } : button("=") { self.insert("=") }
        items.append((side, 1.5))
        lane(items, in: keys)
      }
      let decimal = Locale.current.decimalSeparator ?? "."
      lane(
        [
          (button("÷") { self.insert("÷") }, 1.5), (button("ABC") { self.setPage(0) }, 3.5 / 3),
          (button("?123") { self.setPage(1) }, 3.5 / 3), (character("0"), 7.0 / 3),
          (character(decimal), 7.0 / 3), (enterKey(), 1.5),
        ], in: keys)
      if proxy.keyboardType == .phonePad {
        lane(["+", "*", "#", ",", ";"].map { (character($0), 2) }, in: keys)
      }
      return
    }
    if page > 0 {
      let map = page == 1 ? KeyMaps.symbols : KeyMaps.secondary
      lane(map[0].map { (character($0), 1) }, in: keys)
      lane(map[1].map { (character($0), 1) }, in: keys)
      var last: [(UIView, CGFloat)] = [
        (button(page == 1 ? "=\\<" : "?123") { self.setPage(self.page == 1 ? 2 : 1) }, 1.5)
      ]
      last += map[2].map { (character($0), 1) }
      last.append((deleteKey(), 1.5))
      lane(last, in: keys)
    } else if layout == 1 {
      if shift == 0 {
        for group in KeyMaps.bangla {
          lane(group.map { (character($0), 10 / CGFloat(group.count)) }, in: keys)
        }
      } else {
        for group in KeyMaps.signs {
          lane(group.map { (character($0), 10 / CGFloat(group.count)) }, in: keys)
        }
      }
    } else {
      for (i, group) in KeyMaps.latin.enumerated() {
        var items: [(UIView, CGFloat)] = i == 2 ? [(shiftKey(), 1.5)] : []
        for (j, ch) in group.enumerated() {
          let text = shift == 0 ? ch : ch.uppercased()
          items.append((character(text, alternate: i == 0 ? String((j + 1) % 10) : nil), 1))
        }
        if i == 2 { items.append((deleteKey(), 1.5)) }
        lane(items, in: keys)
      }
    }
    let space = button(hasEditor ? "Space" : layouts[layout], .letter) { self.insert(" ") }
    space.titleLabel?.font = .systemFont(ofSize: 14, weight: .medium)
    space.accessibilityLabel = "Space"
    space.accessibilityValue = layouts[layout]
    space.accessibilityHint = "Hold to choose a typing language"
    space.menu = UIMenu(
      children: layouts.enumerated().map { i, name in
        UIAction(title: name) { _ in self.chooseLayout(i) }
      })
    let sym = button(page > 0 ? "ABC" : "?123") { self.setPage(self.page == 0 ? 1 : 0) }
    let dot = proxy.keyboardType == .URL ? "/" : "."
    let comma = proxy.keyboardType == .emailAddress ? "@" : ","
    if layout == 1 && page == 0 {
      lane(
        [
          (sym, 1.2), (shiftKey(), 1.1), (switchKey(), 1.1), (space, 3.3), (character("।"), 0.9),
          (deleteKey(), 1.15), (enterKey(), 1.25),
        ], in: keys)
    } else {
      let shortcut: UIView =
        page > 0 ? button("123") { self.setPage(0, numeric: true) } : switchKey()
      lane(
        [
          (sym, 1.35), (character(comma), 1), (shortcut, 1.12), (space, 4.05),
          (character(dot), 0.98), (enterKey(), 1.5),
        ], in: keys)
    }
  }

  private func header(_ title: String) {
    let h = stack(.horizontal, gap: 6)
    let back = glyph("chevron.backward", "Return to keyboard", .transparent) {
      self.close()
      self.render()
    }
    back.widthAnchor.constraint(equalToConstant: 44).isActive = true
    h.addArrangedSubview(back)
    let label = UILabel()
    label.text = title
    label.textColor = Deck.ink
    label.font = .systemFont(ofSize: 17, weight: .semibold)
    h.addArrangedSubview(label)
    rows.addArrangedSubview(h)
    h.heightAnchor.constraint(equalToConstant: toolbarHeight).isActive = true
  }
  private func scroller(_ text: String, secondary: Bool = false) -> UIScrollView {
    let scroll = UIScrollView()
    scroll.backgroundColor = Deck.key
    scroll.layer.cornerRadius = Deck.specialRadius
    let label = UILabel()
    label.text = text
    label.textColor = Deck.ink
    label.font = .preferredFont(forTextStyle: secondary ? .subheadline : .body)
    label.adjustsFontForContentSizeCategory = true
    label.numberOfLines = 0
    label.translatesAutoresizingMaskIntoConstraints = false
    scroll.addSubview(label)
    NSLayoutConstraint.activate([
      label.leadingAnchor.constraint(
        equalTo: scroll.contentLayoutGuide.leadingAnchor, constant: 12),
      label.trailingAnchor.constraint(
        equalTo: scroll.contentLayoutGuide.trailingAnchor, constant: -12),
      label.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor, constant: 9),
      label.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor, constant: -9),
      label.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor, constant: -24),
    ])
    return scroll
  }
  private func actions(_ buttons: [UIView]) {
    let line = stack(.horizontal, gap: 2)
    line.distribution = .fillEqually
    buttons.forEach(line.addArrangedSubview)
    rows.addArrangedSubview(line)
    line.heightAnchor.constraint(equalToConstant: toolbarHeight).isActive = true
  }
  private func chips(_ choices: [String], selected: String, _ choose: @escaping (String) -> Void) {
    let scroll = UIScrollView()
    scroll.showsHorizontalScrollIndicator = false
    let line = stack(.horizontal, gap: 3)
    scroll.addSubview(line)
    line.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      line.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor),
      line.trailingAnchor.constraint(equalTo: scroll.contentLayoutGuide.trailingAnchor),
      line.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor),
      line.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor),
      line.heightAnchor.constraint(equalTo: scroll.frameLayoutGuide.heightAnchor),
    ])
    var active: UIView?
    for name in choices {
      let b = button(name, name == selected ? .accent : .function) { choose(name) }
      b.widthAnchor.constraint(equalToConstant: max(68, CGFloat(name.count * 8 + 22))).isActive =
        true
      if name == selected {
        active = b
        b.accessibilityTraits.insert(.selected)
      }
      line.addArrangedSubview(b)
    }
    rows.addArrangedSubview(scroll)
    scroll.heightAnchor.constraint(equalToConstant: toolbarHeight).isActive = true
    if let active {
      DispatchQueue.main.async {
        scroll.scrollRectToVisible(active.convert(active.bounds, to: scroll), animated: false)
      }
    }
  }
  private func userSource() -> String {
    if let pasted { return pasted }
    if let selected = proxy.selectedText, !selected.isEmpty { return selected }
    if mode == "reply" { return "" }
    let pending = raw.isEmpty ? "" : rules?.render(raw) ?? raw
    return String((proxy.documentContextBeforeInput ?? "").suffix(400)) + pending
  }
  private func generate() {
    guard let anchor = Anchor(proxy), !sensitive else {
      message = "Focus a text field to use AI."
      render()
      return
    }
    if mode == "reply" && userSource().isEmpty {
      message = "Paste or select the incoming message first."
      render()
      return
    }
    start(
      userSource(), at: anchor,
      replace: pasted == nil && mode != "reply" && anchor.selected?.isEmpty == false)
  }
  private func loadPaste(forAI: Bool) {
    guard !sensitive, hasFullAccess else {
      message = "Allow Full Access to read the clipboard."
      render()
      return
    }
    guard let s = UIPasteboard.general.string, !s.isEmpty else {
      message = "The clipboard has no text."
      render()
      return
    }
    guard !forAI || s.utf16.count <= 1600 else {
      message = "Paste up to 1600 characters."
      render()
      return
    }
    pasted = s
    message = ""
    render()
  }
  private func renderAI(_ t: Tool) {
    header(t.rawValue)
    if !loading && (preview.isEmpty || t == .tone) {
      if t == .tone {
        let options =
          tones
          + (Settings.favorites + Settings.styles).reduce(into: [String]()) { list, v in
            if !tones.contains(v) && !list.contains(v) { list.append(v) }
          }
        chips(options, selected: style) { value in
          self.style = value
          if !self.preview.isEmpty, !self.targetChanged, let anchor = self.origin,
            anchor == Anchor(self.proxy)
          {
            self.start(self.source, at: anchor, replace: self.replaceSelection)
          } else {
            self.render()
          }
        }
      } else if t == .ask {
        chips(["Express", "Rewrite", "Reply"], selected: mode.capitalized) {
          self.mode = $0.lowercased()
          self.render()
        }
      } else if t == .translate {
        let from = ["auto", "bn", "banglish", "en"].map { ($0, languageNames[$0]!) }
        let to = from.filter { $0.0 != "auto" }
        actions([
          menu("From · " + languageNames[sourceLanguage]!, choices: from) {
            self.sourceLanguage = $0
            self.render()
          },
          menu("To · " + languageNames[targetLanguage]!, choices: to) {
            self.targetLanguage = $0
            self.render()
          },
        ])
      }
    }
    if loading {
      let box = stack(.vertical, gap: 8)
      box.alignment = .center
      box.distribution = .fill
      let spacer = UIView()
      let bottom = UIView()
      box.addArrangedSubview(spacer)
      let spinner = UIActivityIndicatorView(style: .medium)
      spinner.startAnimating()
      box.addArrangedSubview(spinner)
      let label = UILabel()
      label.text = "Generating…"
      label.textColor = Deck.ink
      box.addArrangedSubview(label)
      box.addArrangedSubview(bottom)
      spacer.heightAnchor.constraint(equalTo: bottom.heightAnchor).isActive = true
      rows.addArrangedSubview(box)
      actions([
        button("Cancel") {
          self.close()
          self.render()
        }
      ])
      return
    }
    if !preview.isEmpty {
      let text = message.isEmpty ? preview : message + "\n\n" + preview
      if t == .tone && alternatives.count > 1 {
        let scroll = UIScrollView()
        let list = stack(.vertical, gap: 4)
        list.translatesAutoresizingMaskIntoConstraints = false
        scroll.addSubview(list)
        NSLayoutConstraint.activate([
          list.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor),
          list.trailingAnchor.constraint(equalTo: scroll.contentLayoutGuide.trailingAnchor),
          list.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor),
          list.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor),
          list.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor),
        ])
        for (i, item) in alternatives.enumerated() {
          let card = button(
            item.style + "  ·  " + item.text, item.text == preview ? .function : .letter
          ) {
            self.preview = item.text
            self.style = item.style
            self.render()
          }
          card.titleLabel?.numberOfLines = 0
          card.titleLabel?.font = .systemFont(ofSize: 15)
          card.contentHorizontalAlignment = .leading
          var cfg = UIButton.Configuration.plain()
          cfg.title = item.style + "  ·  " + item.text
          cfg.baseForegroundColor = Deck.ink
          cfg.contentInsets = .init(top: 8, leading: 12, bottom: 8, trailing: 12)
          card.configuration = cfg
          card.accessibilityLabel = "Alternative " + String(i + 1) + ", " + item.text
          card.heightAnchor.constraint(greaterThanOrEqualToConstant: 48).isActive = true
          list.addArrangedSubview(card)
        }
        rows.addArrangedSubview(scroll)
      } else {
        rows.addArrangedSubview(scroller(text))
      }
      var list: [UIView] = []
      if !targetChanged, replaceSelection || proxy.selectedText?.isEmpty != false {
        list.append(button(replaceSelection ? "Replace Text" : "Insert", .accent) { self.apply() })
      }
      list.append(button("Edit") { self.beginEditor(result: true) })
      list.append(
        button("Copy") {
          guard self.hasFullAccess, !self.sensitive else { return }
          UIPasteboard.general.string = self.preview
          self.close()
          self.render()
        })
      if !targetChanged {
        list.append(
          button("Regenerate") {
            guard let a = self.origin, a == Anchor(self.proxy) else {
              self.invalidateTarget()
              return
            }
            self.start(self.source, at: a, replace: self.replaceSelection)
          })
      }
      actions(list)
      return
    }
    let draft = userSource()
    let selection = proxy.selectedText?.isEmpty == false
    let detail: String
    if !message.isEmpty {
      detail = message
    } else if !draft.isEmpty {
      let caption =
        pasted != nil
        ? "Provided text" : selection ? "Selected passage" : "Cursor excerpt · insert only"
      detail = caption + "\n" + draft
    } else {
      detail =
        t == .translate
        ? "Provide text to translate between Bangla, Banglish and English."
        : t == .ask
          ? "Enter an idea or request, or paste an incoming message to reply."
          : "Select a passage, provide text, or use the text before the cursor."
    }
    rows.addArrangedSubview(scroller(detail, secondary: true))
    var inputActions: [UIView] = [button("Edit input") { self.beginEditor(result: false) }]
    if t == .ask {
      inputActions.append(
        button("Instructions") { self.beginEditor(result: false, instructions: true) })
    }
    inputActions.append(button("Paste") { self.loadPaste(forAI: true) })
    inputActions.append(button("Generate", .accent) { self.generate() })
    actions(inputActions)
  }
  private func beginEditor(result: Bool, instructions: Bool = false) {
    editingInstructions = instructions
    editorRaw = ""
    savedPage = page
    savedShift = shift
    savedLayout = layout
    savedNumeric = numericPage
    page = 0
    shift = 0
    numericPage = false
    if result {
      editingPreview = true
    } else {
      localInput = instructions ? (askInstruction ?? Settings.get("custom")) : userSource()
      editingInput = true
    }
    render()
  }
  private var savedNumeric = false
  private func restoreEditor() {
    guard let oldPage = savedPage else { return }
    page = oldPage
    shift = savedShift ?? 0
    layout = savedLayout ?? 0
    numericPage = savedNumeric
    savedPage = nil
    savedShift = nil
    savedLayout = nil
    editingPreview = false
    editingInput = false
    editingInstructions = false
    editorRaw = ""
  }
  private func renderEditor() {
    let bar = stack(.horizontal, gap: 5)
    let label = UILabel()
    let shown =
      currentEditorText + (editorRaw.isEmpty ? "" : (rules?.render(editorRaw) ?? editorRaw))
    label.text =
      shown.isEmpty
      ? (editingInstructions ? "Type a writing instruction" : "Type your text") : shown
    label.textColor = Deck.ink
    label.font = .systemFont(ofSize: 15)
    label.lineBreakMode = .byTruncatingHead
    label.numberOfLines = 2
    bar.addArrangedSubview(label)
    let done = button("Done", .accent) {
      self.finishEditorWord()
      if self.editingInstructions {
        self.askInstruction = self.localInput
      } else if self.editingInput {
        self.pasted = self.localInput
        self.message = ""
      }
      self.restoreEditor()
      self.render()
    }
    done.widthAnchor.constraint(equalToConstant: 64).isActive = true
    bar.addArrangedSubview(done)
    rows.addArrangedSubview(bar)
    bar.heightAnchor.constraint(equalToConstant: toolbarHeight).isActive = true
    renderKeys()
  }
  private func renderUtility(_ t: Tool) {
    header(t.rawValue)
    switch t {
    case .emoji:
      let grid = stack(.vertical)
      grid.distribution = .fillEqually
      rows.addArrangedSubview(grid)
      for i in stride(from: 0, to: KeyMaps.emoji.count, by: 8) {
        lane(
          KeyMaps.emoji[i..<(i + 8)].map { e in (button(e, .letter) { self.insert(e) }, 1.25) },
          in: grid)
      }
    case .clipboard:
      rows.addArrangedSubview(
        scroller(
          !message.isEmpty ? message : pasted ?? "Load the current clipboard to preview it here."))
      var options: [UIView] = [button("Load clipboard") { self.loadPaste(forAI: false) }]
      if let pasted {
        options.append(
          button("Insert", .accent) {
            guard !self.sensitive else { return }
            self.insert(pasted)
            self.close()
            self.render()
          })
      }
      actions(options)
    case .voice:
      rows.addArrangedSubview(
        scroller(
          "Use the iOS microphone below the keyboard when available. You can also switch to the Apple keyboard and tap its microphone with Dictation enabled."
        ))
      actions([button("Switch keyboard", .accent) { self.advanceToNextInputMode() }])
    case .more:
      let content = stack(.vertical, gap: 2)
      content.distribution = .fillEqually
      rows.addArrangedSubview(content)
      let options = Tool.allCases.filter { $0 != .more && (!sensitive || $0 == .voice) }
      for i in stride(from: 0, to: options.count, by: 3) {
        let line = stack(.horizontal, gap: 2)
        line.distribution = .fillEqually
        options[i..<min(i + 3, options.count)].forEach { t in
          line.addArrangedSubview(button(t.rawValue) { self.open(t) })
        }
        content.addArrangedSubview(line)
      }
      let globe = glyph("globe", "Next keyboard") {}
      globe.addTarget(
        self, action: #selector(handleInputModeList(from:with:)), for: .allTouchEvents)
      var items: [UIView] = [
        languageKey(),
        glyph("arrow.left", "Move cursor left") {
          self.finishWord()
          self.prefix = ""
          self.proxy.adjustTextPosition(byCharacterOffset: -1)
          self.close()
          self.render()
        },
        glyph("arrow.right", "Move cursor right") {
          self.finishWord()
          self.prefix = ""
          self.proxy.adjustTextPosition(byCharacterOffset: 1)
          self.close()
          self.render()
        },
      ]
      if needsInputModeSwitchKey { items.append(globe) }
      actions(items)
    default: break
    }
  }
}
