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

// UIKit exposes a window around the selection. A revisited word must have
// visible boundaries on both sides or be an explicit full-word selection.
private struct CursorWord {
  var left: String
  var right: String
  var selected: String?
  var earlier: String
  var later: String
  var word: String { selected ?? (left + right) }

  init?(_ anchor: Anchor) {
    let before = anchor.before ?? "", after = anchor.after ?? ""
    func letter(_ c: Character) -> Bool { Predictor.isWordKey(String(c)) }
    func joinsIdentifier(_ c: Character) -> Bool { c.isNumber || "@/_#%+-".contains(c) }
    if let selection = anchor.selected, !selection.isEmpty {
      guard Predictor.normalizedEntry(selection) != nil,
        before.last.map({ !letter($0) && !joinsIdentifier($0) }) != false,
        after.first.map({ !letter($0) && !joinsIdentifier($0) }) != false else { return nil }
      selected = selection
      left = ""; right = ""; earlier = before; later = after
    } else {
      left = String(before.reversed().prefix(while: letter).reversed())
      right = String(after.prefix(while: letter))
      earlier = String(before.dropLast(left.count))
      later = String(after.dropFirst(right.count))
      guard !left.isEmpty || !right.isEmpty, Predictor.normalizedEntry(left + right) != nil,
        earlier.last.map({ !joinsIdentifier($0) }) != false,
        later.first.map({ !joinsIdentifier($0) }) != false else { return nil }
      // A word filling a context window could be a suffix of a longer word.
      if earlier.isEmpty && before.utf16.count >= 40 || later.isEmpty && after.utf16.count >= 40 { return nil }
    }
    // Treat dotted identifiers as indivisible, while allowing a sentence-final period.
    if earlier.hasSuffix("."), earlier.dropLast().last.map(letter) == true { return nil }
    if later.hasPrefix("."), later.dropFirst().first.map(letter) == true { return nil }
  }
}
private struct UndoEdit {
  let original: String
  let inserted: String
  let anchor: Anchor
  let label: String
  var rejected: String?
  var originalWord: String?
}

private enum Candidate: Equatable {
  case word(String)
  case undo(String)

  var title: String {
    switch self {
    case .word(let word): return word
    case .undo(let word): return "Undo · " + word
    }
  }
}

private final class ClickInputView: UIInputView, UIInputViewAudioFeedback {
  var enableInputClicksWhenVisible: Bool { true }
}

private final class CandidateRow: UIStackView {
  private(set) var candidates: [Candidate] = []
  private let buttons: [DeckKey]
  private let dividers = [UIView(), UIView()]

  init(_ buttons: [DeckKey]) {
    self.buttons = buttons
    super.init(frame: .zero)
    axis = .horizontal
    distribution = .fillEqually
    buttons.forEach(addArrangedSubview)
    for divider in dividers {
      divider.backgroundColor = Deck.separator
      divider.isUserInteractionEnabled = false
      divider.isAccessibilityElement = false
      addSubview(divider)
    }
    show([])
  }

  required init(coder: NSCoder) { fatalError() }

  override func layoutSubviews() {
    super.layoutSubviews()
    let scale = max(1, traitCollection.displayScale)
    for (index, divider) in dividers.enumerated() {
      let x = (bounds.width * CGFloat(index + 1) * scale / 3).rounded() / scale
      let inset = (bounds.height * 0.24).rounded()
      divider.frame = CGRect(x: x, y: inset, width: 1 / scale, height: max(0, bounds.height - inset * 2))
    }
  }

  func show(_ words: [Candidate]) {
    candidates = words
    dividers.forEach { $0.isHidden = words.isEmpty }
    for (index, button) in buttons.enumerated() {
      let candidate = index < words.count ? words[index] : nil
      if button.currentTitle != candidate?.title {
        if button.isTracking { button.cancelTracking(with: nil) }
        button.setTitle(candidate?.title, for: .normal)
        button.accessibilityLabel = candidate?.title
      }
      button.isEnabled = candidate != nil
      button.isAccessibilityElement = candidate != nil
    }
  }
}

final class KeyboardController: UIInputViewController, UIGestureRecognizerDelegate {
  private let rows = UIStackView()
  private let rules = Phonetic.bundled()
  private static let predictions = DispatchQueue(label: "dev.kothakeys.prediction", qos: .userInitiated)
  private static let bundledPredictor = Predictor.bundled()
  private var predictor: Predictor?
  private var predictionWork: DispatchWorkItem?
  private var predictionRevision = 0
  private var prefixWasAutomaticallyCapitalized = false
  private var autoShiftSuppressed = false
  private var renderedAutomaticShift = false
  private var cursorDrag: (document: UUID, x: CGFloat)?
  private var glideGesture: LetterGlideRecognizer!
  private let glideTrail = CAShapeLayer()
  private var pendingTapHint: Character?
  private var glideAnchor: Anchor?
  private var glideEdit: (anchor: Anchor, words: [String], leading: String, trailing: String)?
  private static var glideModels: [TypingLanguage: GlideDecoder] = [:] // predictions queue only
  private weak var returnControl: DeckKey?
  private weak var shiftControl: DeckKey?
  private var latinKeys: [(key: DeckKey, letter: String, alternate: String?)] = []
  private var showsVoiceToolbar: Bool { needsInputModeSwitchKey && Settings.voiceTyping }
  private weak var candidateRow: CandidateRow?
  private var candidateAnchor: Anchor?
  private var typingAnchor: Anchor?
  private var pendingSelectionDelete: UUID?
  private var undoCheck: Timer?
  private var correction: UndoEdit? {
    didSet {
      undoCheck?.invalidate()
      undoCheck = nil
      guard let edit = correction else { return }
      let expires = ProcessInfo.processInfo.systemUptime + 12
      // Some web editors move their DOM selection without sending a UIKit delegate
      // callback. Revalidate only during the short lifetime of a visible Undo.
      let check = Timer(timeInterval: 0.35, repeats: true) { [weak self] timer in
        guard let self else { timer.invalidate(); return }
        if !isInputVisible || sensitive || tool != nil || ProcessInfo.processInfo.systemUptime >= expires
          || Anchor(proxy) != edit.anchor {
          correction = nil
          updateCandidates()
        }
      }
      check.tolerance = 0.08
      undoCheck = check
      RunLoop.main.add(check, forMode: .common)
    }
  }
  private var suggestedSpace: Anchor?
  private var manualSpace: Anchor?
  private var touchHints: [Int: Character] = [:]
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
    .username, .emailAddress, .telephoneNumber, .URL,
  ]

  private var sensitive: Bool {
    let input = proxy
    if input.isSecureTextEntry == true { return true }
    guard let type = input.textContentType ?? nil else { return false }
    return Self.protectedTypes.contains(type)
  }
  private var canPredictHere: Bool {
    guard !sensitive, !hasEditor, !numericPage, !numericHost,
      proxy.autocorrectionType != .no else { return false }
    return ![.emailAddress, .URL, .webSearch, .twitter, .namePhonePad].contains(
      proxy.keyboardType ?? .default)
  }
  private var allowsPrediction: Bool { page == 0 && canPredictHere }
  private var showsSymbolPredictions: Bool {
    page > 0 && canPredictHere && Settings.predictiveSuggestions && Settings.nextWordPrediction
  }
  private var allowsPhonetic: Bool {
    layout == 2 && page == 0 && canPredictHere
  }
  private var typingLanguage: TypingLanguage {
    switch layout {
    case 1, 2: return .bangla
    case 3: return .banglish
    default: return .english
    }
  }
  private var personalWords: [String] { Settings.personalWords }
  private var automaticShift: Bool {
    guard shift == 0, layout == 0, allowsPrediction, Settings.automaticCapitalization,
      !autoShiftSuppressed else { return false }
    let before = proxy.documentContextBeforeInput ?? ""
    switch proxy.autocapitalizationType ?? .sentences {
    case .none: return false
    case .allCharacters: return true
    case .words: return before.last?.isWhitespace != false
    case .sentences:
      let isEmpty = before.allSatisfy(\.isWhitespace)
      return isEmpty || before.last == "\n"
        || before.last?.isWhitespace == true
          && before.reversed().first(where: { !$0.isWhitespace }).map { ".!?।".contains($0) } == true
    @unknown default: return false
    }
  }

  private var learningAllowed: Bool {
    hasFullAccess && tool == nil && allowsPrediction && Settings.learning
      && proxy.autocorrectionType != .no
  }

  private func learn(_ value: String, as event: TypingHistory.Event, after previous: String = "") {
    guard learningAllowed else { return }
    var history = Settings.typingHistory
    history.record(value, in: typingLanguage, as: event, after: previous)
    Settings.saveTypingHistory(history)
  }

  private func resetTyping() {
    pendingSelectionDelete = nil
    prefix = ""
    typingAnchor = nil
    candidateAnchor = nil
    correction = nil
    suggestedSpace = nil
    manualSpace = nil
    touchHints = [:]
    pendingTapHint = nil
    glideEdit = nil
    glideAnchor = nil
    glideTrail.path = nil
    prefixWasAutomaticallyCapitalized = false
    predictionWork?.cancel()
    predictionRevision += 1
  }

  override func loadView() {
    view = ClickInputView(frame: .zero, inputViewStyle: .keyboard)
  }

  override func viewDidLoad() {
    super.viewDidLoad()
    rows.axis = .vertical
    rows.spacing = Deck.rowGap
    rows.translatesAutoresizingMaskIntoConstraints = false
    view.addSubview(rows)
    let doubleShift = UITapGestureRecognizer(target: self, action: #selector(lockShift(_:)))
    doubleShift.numberOfTapsRequired = 2
    doubleShift.delaysTouchesEnded = false
    doubleShift.delegate = self
    rows.addGestureRecognizer(doubleShift)
    glideGesture = LetterGlideRecognizer(target: self, action: #selector(handleGlide(_:)))
    glideGesture.delegate = self
    glideGesture.delaysTouchesBegan = false
    glideGesture.delaysTouchesEnded = false
    rows.addGestureRecognizer(glideGesture)
    glideTrail.fillColor = nil
    glideTrail.lineCap = .round
    glideTrail.lineJoin = .round
    glideTrail.zPosition = 4
    rows.layer.addSublayer(glideTrail)
    height = view.heightAnchor.constraint(equalToConstant: 260)
    height.priority = .defaultHigh
    NSLayoutConstraint.activate([
      height, rows.topAnchor.constraint(equalTo: view.topAnchor, constant: 4),
      rows.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: Deck.keyboardEdge),
      rows.trailingAnchor.constraint(
        equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -Deck.keyboardEdge),
      rows.bottomAnchor.constraint(equalTo: view.bottomAnchor, constant: -4),
    ])
    style = Settings.favorites.first ?? "Respectful"
    Self.predictions.async { [weak self] in
      let predictor = Self.bundledPredictor
      DispatchQueue.main.async {
        self?.predictor = predictor
        self?.updateCandidates()
      }
    }
    render()
  }
  override func viewWillDisappear(_ animated: Bool) {
    super.viewWillDisappear(animated)
    isInputVisible = false
    cursorDrag = nil
    delayedTraits?.cancel()
    close()
    deleteTimer?.invalidate()
    deleteTimer = nil
    finishWord()
    resetTyping()
    latinKeys.removeAll()
    for row in rows.arrangedSubviews { row.removeFromSuperview() }
  }
  override func viewWillAppear(_ animated: Bool) {
    super.viewWillAppear(animated)
    isInputVisible = true
    hasDictationKey = !Settings.voiceTyping
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
    let wasPanelOpen = panel
    let activeDocument = documentID(proxy)
    let changed =
      document != activeDocument || traits != proxy.keyboardType
      || wasSensitive != sensitive
    document = activeDocument
    traits = proxy.keyboardType
    wasSensitive = sensitive
    if changed || activeDocument == nil {
      cursorDrag = nil
      if changed { checkLateTraits() }
      close()
      resetTyping()
      numericPage = numericHost
      raw = ""
      composition = nil
      autoShiftSuppressed = false
    } else {
      checkComposition()
      if canPredictHere {
        let anchor = Anchor(proxy)
        if typingAnchor != anchor {
          prefix = ""
          typingAnchor = nil
          touchHints = [:]
          prefixWasAutomaticallyCapitalized = false
        }
        if correction?.anchor != anchor { correction = nil }
        if suggestedSpace != anchor { suggestedSpace = nil }
        if manualSpace != anchor { manualSpace = nil }
      } else {
        resetTyping()
      }
      if let origin, !targetChanged, origin != Anchor(proxy) {
        invalidateTarget()
        return
      }
    }
    guard isViewLoaded, cursorDrag == nil else { return }
    updateReturnKey()
    if changed || (wasPanelOpen && !panel) || (!panel && renderedAutomaticShift != automaticShift) {
      render()
    } else {
      updateCandidates()
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
    let hint = pendingTapHint
    pendingTapHint = nil
    glideEdit = nil
    let wasAutomatic = automaticShift
    let position = allowsPrediction || canPredictHere ? Anchor(proxy) : nil
    let punctuation = s.count == 1 && ",.!?;:।".contains(s)
    if let position, suggestedSpace == position, s == " " {
      suggestedSpace = nil
      manualSpace = position
      return
    }
    if let position, punctuation, suggestedSpace == position,
      replaceLocalSuffix(" ", with: s, at: position) {
      resetTyping()
      render()
      return
    }
    if s == " ", let position, manualSpace == position, Settings.doubleSpacePeriod,
      let before = position.before, before.hasSuffix(" "), let last = before.dropLast().last,
      last.isLetter || last.isNumber, replaceLocalSuffix(" ", with: ". ", at: position) {
      resetTyping()
      rememberEdit(original: "  ", inserted: ". ", label: "period")
      render()
      return
    }
    suggestedSpace = nil
    manualSpace = nil
    if allowsPhonetic, s.count == 1,
      s.utf8.allSatisfy({ (65...90).contains($0) || (97...122).contains($0) })
    {
      correction = nil
      checkComposition()
      if raw.count >= 96 { finishWord() }
      if raw.isEmpty { composition = Anchor(proxy) }
      if composition != nil { raw += s } else { proxy.insertText(s) }
    } else if (s == " " || punctuation), applyAutomaticCorrection(boundary: s) {
      prefix = ""
      typingAnchor = nil
      prefixWasAutomaticallyCapitalized = false
    } else {
      correction = nil
      let before = allowsPrediction ? Anchor(proxy) : nil
      let previousPrefix = before != nil && before == typingAnchor ? prefix : ""
      let previousCapital = prefixWasAutomaticallyCapitalized
      let composed = takeWord()
      if s == " " || s == "\n", let before {
        if !composed.isEmpty && before.selected?.isEmpty != false { learn(composed, as: .typed) }
        if let typed = activePrefix(in: before), !typed.isEmpty {
          learn(typed, as: .typed, after: Predictor.previous(in: earlierText(in: before, excluding: typed)))
        }
      }
      proxy.insertText(composed + s)
      if allowsPrediction, layout != 2, Predictor.isWordKey(s),
        previousPrefix.unicodeScalars.count + s.unicodeScalars.count <= 40
      {
        if previousPrefix.isEmpty { touchHints.removeAll(keepingCapacity: true) }
        if let hint { touchHints[previousPrefix.count] = hint }
        prefix = previousPrefix + s
        typingAnchor = Anchor(proxy)
        prefixWasAutomaticallyCapitalized = previousPrefix.isEmpty ? wasAutomatic : previousCapital
      } else {
        prefix = ""
        typingAnchor = nil
        prefixWasAutomaticallyCapitalized = false
      }
    }
    if wasAutomatic, s.count == 1, s.lowercased() != s, !prefix.isEmpty {
      rememberEdit(original: s.lowercased(), inserted: s, label: "capitalization")
    }
    if s == " ", canPredictHere { manualSpace = Anchor(proxy) }
    if prefix.isEmpty { touchHints.removeAll(keepingCapacity: true) }
    autoShiftSuppressed = false
    updateReturnKey()
    if shift == 1 || wasAutomatic != automaticShift {
      if shift == 1 { shift = 0 }
      if layout == 1 {
        render()
        return
      }
      refreshLatinShift()
    }
    updateCandidates()
  }

  private func activePrefix(in anchor: Anchor) -> String? {
    guard anchor.selected?.isEmpty != false,
      anchor.after?.first.map({ !Predictor.isWordKey(String($0)) }) != false
    else { return nil }
    let before = anchor.before ?? ""
    let typed = typingAnchor == anchor ? prefix : ""
    let scalars = before.unicodeScalars
    guard scalars.suffix(typed.unicodeScalars.count).elementsEqual(typed.unicodeScalars)
    else { return nil }
    let earlier = String(scalars.dropLast(typed.unicodeScalars.count))
    guard earlier.last.map({ !Predictor.isWordKey(String($0)) && !"@/._#%+-".contains($0) }) != false,
      anchor.after?.first.map({ !"@/_#%+-".contains($0) }) != false
    else { return nil }
    return typed
  }

  private func earlierText(in anchor: Anchor, excluding word: String) -> String {
    String((anchor.before ?? "").unicodeScalars.dropLast(word.unicodeScalars.count))
  }

  private func applyAutomaticCorrection(boundary: String) -> Bool {
    guard canPredictHere, Settings.autocorrect, layout != 2,
      proxy.autocorrectionType != .no, let anchor = Anchor(proxy),
      let typed = activePrefix(in: anchor), !typed.isEmpty,
      let word = predictor?.correction(
        for: typed, after: Predictor.context(in: earlierText(in: anchor, excluding: typed)),
        language: typingLanguage, personal: personalWords,
        history: learningAllowed ? Settings.typingHistory.words(in: typingLanguage) : [:],
        allowInitialCapital: prefixWasAutomaticallyCapitalized, touchHints: touchHints,
        allowSegmentation: Settings.automaticSpacing)
    else { return false }
    guard replaceLocalSuffix(typed, with: word + boundary, at: anchor) else { return false }
    rememberEdit(original: typed + boundary, inserted: word + boundary, label: typed,
      rejected: word, originalWord: typed)
    return true
  }

  // Verify the exact caret and suffix before changing a token. A Bengali cluster may
  // be removed in several delete operations, depending on the host.
  private func replaceLocalSuffix(_ old: String, with new: String, at anchor: Anchor) -> Bool {
    guard !sensitive, Anchor(proxy) == anchor, anchor.selected?.isEmpty != false else { return false }
    if old.isEmpty {
      if !new.isEmpty { proxy.insertText(new) }
      return true
    }
    guard let before = anchor.before,
      before.unicodeScalars.suffix(old.unicodeScalars.count).elementsEqual(old.unicodeScalars)
    else { return false }
    if new.unicodeScalars.starts(with: old.unicodeScalars) {
      proxy.insertText(String(new.unicodeScalars.dropFirst(old.unicodeScalars.count)))
      return true
    }
    let common = zip(old, new).prefix(while: { $0.0 == $0.1 }).count
    let earlier = earlierText(in: anchor, excluding: old) + old.prefix(common)
    let remove = String(old.dropFirst(common))
    let insert = String(new.dropFirst(common))
    var remaining = remove.unicodeScalars.count
    for _ in remove.unicodeScalars {
      proxy.deleteBackward()
      guard let moved = Anchor(proxy), moved.document == anchor.document,
        moved.selected?.isEmpty != false, moved.after == anchor.after else { return false }
      let current = moved.before ?? ""
      guard let next = (0..<remaining).reversed().first(where: { count in
        let expected = earlier + String(remove.unicodeScalars.prefix(count))
        return current == expected || (!earlier.isEmpty && current.hasSuffix(expected))
      }) else { return false }
      remaining = next
      if remaining == 0 {
        if !insert.isEmpty { proxy.insertText(insert) }
        return true
      }
    }
    return false
  }

  private func rememberEdit(original: String, inserted: String, label: String,
    rejected: String? = nil, originalWord: String? = nil) {
    guard !sensitive, original != inserted, max(original.utf16.count, inserted.utf16.count) <= 1600,
      let anchor = Anchor(proxy), anchor.selected?.isEmpty != false,
      inserted.isEmpty || anchor.before?.hasSuffix(inserted) == true else { return }
    correction = UndoEdit(original: original, inserted: inserted, anchor: anchor, label: label,
      rejected: rejected, originalWord: originalWord)
  }

  private func undoCorrection() -> Bool {
    guard let edit = correction, Anchor(proxy) == edit.anchor,
      replaceLocalSuffix(edit.inserted, with: edit.original, at: edit.anchor) else { return false }
    if learningAllowed, let rejected = edit.rejected, let word = edit.originalWord {
      var history = Settings.typingHistory
      history.reject(word, suggestion: rejected, in: typingLanguage)
      history.record(word, in: typingLanguage, as: .typed)
      Settings.saveTypingHistory(history)
    }
    resetTyping()
    return true
  }

  // Restore a moved caret only when both visible contexts agree on the exact offset.
  private func restoreCaret(_ original: Anchor) {
    guard let current = Anchor(proxy), current != original, current.document == original.document,
      current.selected?.isEmpty != false else { return }
    let a = original.before ?? "", b = original.after ?? ""
    let c = current.before ?? "", d = current.after ?? ""
    if c.hasPrefix(a) {
      let difference = String(c.dropFirst(a.count))
      if b == difference + d { proxy.adjustTextPosition(byCharacterOffset: -difference.utf16.count) }
    } else if a.hasPrefix(c) {
      let difference = String(a.dropFirst(c.count))
      if d == difference + b { proxy.adjustTextPosition(byCharacterOffset: difference.utf16.count) }
    }
  }

  private func replaceCursorWord(_ target: CursorWord, with replacement: String, at anchor: Anchor) -> Bool {
    guard !sensitive, anchor == Anchor(proxy) else { return false }
    if let selected = target.selected {
      guard proxy.selectedText == selected else { return false }
      proxy.insertText(replacement)
      return true
    }
    var readyToEdit = false
    defer { if !readyToEdit { restoreCaret(anchor) } }
    // Probe ambiguous window boundaries by moving the caret, never by deleting.
    // If a host conceals context or moves to an unexpected position, decline the edit.
    if target.earlier.isEmpty || target.later.isEmpty {
      if target.earlier.isEmpty {
        proxy.adjustTextPosition(byCharacterOffset: -target.left.utf16.count - 1)
        guard let edge = Anchor(proxy), edge.document == anchor.document,
          (edge.before ?? "").isEmpty, (edge.after ?? "").hasPrefix(target.word) else { return false }
        proxy.adjustTextPosition(byCharacterOffset: target.left.utf16.count)
        guard Anchor(proxy) == anchor else { return false }
      }
      if target.later.isEmpty {
        proxy.adjustTextPosition(byCharacterOffset: target.right.utf16.count + 1)
        guard let edge = Anchor(proxy), edge.document == anchor.document,
          (edge.after ?? "").isEmpty, (edge.before ?? "").hasSuffix(target.word) else { return false }
        proxy.adjustTextPosition(byCharacterOffset: -target.right.utf16.count)
        guard Anchor(proxy) == anchor else { return false }
      }
    }
    if !target.right.isEmpty { proxy.adjustTextPosition(byCharacterOffset: target.right.utf16.count) }
    guard let end = Anchor(proxy), end.document == anchor.document,
      end.selected?.isEmpty != false, (end.before ?? "").hasSuffix(target.word),
      end.after ?? "" == target.later else { return false }
    readyToEdit = true
    return replaceLocalSuffix(target.word, with: replacement, at: end)
  }

  @objc private func erase() {
    let wasAutomatic = automaticShift
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
    } else if !sensitive, let anchor = Anchor(proxy), let selected = anchor.selected,
      !selected.isEmpty, selected.utf16.count <= 1600 {
      deleteSelectedText(at: anchor, label: "deletion")
    } else if deleteTimer != nil || correction?.rejected == nil || !undoCorrection() {
      correction = nil
      let previous = allowsPrediction ? Anchor(proxy) : nil
      var typed = previous != nil && previous == typingAnchor ? prefix : ""
      proxy.deleteBackward()
      let updated = allowsPrediction ? Anchor(proxy) : nil
      while !typed.isEmpty,
        updated?.before?.unicodeScalars.suffix(typed.unicodeScalars.count)
          .elementsEqual(typed.unicodeScalars) != true
      {
        typed = String(typed.unicodeScalars.dropLast())
      }
      prefix = typed
      touchHints = touchHints.filter { $0.key < typed.count }
      typingAnchor = typed.isEmpty ? nil : updated
      if typed.isEmpty { prefixWasAutomaticallyCapitalized = false }
    }
    updateReturnKey()
    if deleteTimer == nil {
      if hasEditor || wasAutomatic != automaticShift { render() } else { updateCandidates() }
    }
  }
  @objc private func heldDelete(_ g: UILongPressGestureRecognizer) {
    if g.state == .began {
      let timer = Timer(timeInterval: 0.085, repeats: true) { [weak self] _ in
        self?.erase()
      }
      deleteTimer = timer
      erase()
      RunLoop.main.add(timer, forMode: .common)
    }
    if [.ended, .cancelled, .failed].contains(g.state) {
      deleteTimer?.invalidate()
      deleteTimer = nil
      render()
    }
  }
  private func start(_ input: String, at anchor: Anchor, replace: Bool) {
    guard hasFullAccess, !sensitive else {
      message = "Allow Full Access to use AI."
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
        request = nil
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
    let inserted = (!raw.isEmpty && !replaceSelection ? takeWord() : "") + preview
    proxy.insertText(inserted)
    resetTyping()
    rememberEdit(original: selection, inserted: inserted, label: "AI edit")
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
      UIAction { [weak self, weak key] _ in
        guard self != nil else { return }
        if key?.isTypingKey == true { UIDevice.current.playInputClick() }
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
    resetTyping()
    layout = index
    autoShiftSuppressed = false
    shift = 0
    page = 0
    numericPage = false
    render()
  }
  private func editorLanguageKey() -> DeckKey {
    let key = menu("", choices: layouts.enumerated().map { (String($0.offset), $0.element) }) {
      self.chooseLayout(Int($0) ?? 0)
    }
    key.setImage(UIImage(systemName: "character"), for: .normal)
    key.tintColor = Deck.ink
    key.accessibilityLabel = "Typing language"
    key.accessibilityValue = layouts[layout]
    return key
  }

  private func systemSwitchKey() -> DeckKey {
    let key = glyph("globe", "Next keyboard") {}
    key.addTarget(self, action: #selector(handleInputModeList(from:with:)), for: .allTouchEvents)
    return key
  }

  private func emojiKey() -> DeckKey {
    let key = button("") { self.open(.emoji) }
    key.setImage(Tool.emoji.icon(), for: .normal)
    key.tintColor = Deck.ink
    key.accessibilityLabel = "Emoji"
    key.accessibilityIdentifier = "emoji-key"
    return key
  }

  private func lane(_ entries: [(UIView, CGFloat)], in stack: UIStackView, typing: Bool = false) {
    if typing {
      for (view, _) in entries { (view as? DeckKey)?.isTypingKey = true }
    }
    stack.addArrangedSubview(KeyLane(entries))
  }
  private func stack(_ axis: NSLayoutConstraint.Axis, gap: CGFloat = Deck.rowGap) -> UIStackView {
    let s = UIStackView()
    s.axis = axis
    s.spacing = gap
    return s
  }
  private func choose(_ candidate: Candidate) {
    guard allowsPrediction || allowsPhonetic && !raw.isEmpty || showsSymbolPredictions,
      let anchor = candidateAnchor, anchor == Anchor(proxy)
    else {
      updateCandidates()
      return
    }
    switch candidate {
    case .undo:
      _ = undoCorrection()
    case .word(let word):
      if let glide = glideEdit, anchor == glide.anchor, glide.words.contains(word), let first = glide.words.first {
        let original = glide.leading + first + glide.trailing
        let replacement = glide.leading + word + glide.trailing
        guard replaceLocalSuffix(original, with: replacement, at: anchor) else { updateCandidates(); return }
        resetTyping()
        learn(word, as: .accepted)
        rememberEdit(original: original, inserted: replacement, label: first)
        if !glide.trailing.isEmpty { suggestedSpace = Anchor(proxy) }
        updateCandidates()
        return
      }
      let active = activePrefix(in: anchor)
      let target = raw.isEmpty && (active == nil || active == "") && Settings.cursorAware
        ? CursorWord(anchor) : nil
      let suffix = Settings.spaceAfterSuggestion && target == nil
        && (anchor.after ?? "").isEmpty ? " " : ""
      let new = word + suffix
      var old = ""
      var earlier = anchor.before ?? ""
      if !raw.isEmpty {
        guard !takeWord().isEmpty else { updateCandidates(); return }
        proxy.insertText(new)
      } else if let target {
        old = target.word
        earlier = target.earlier
        guard replaceCursorWord(target, with: word, at: anchor) else { updateCandidates(); return }
      } else if let active {
        old = active
        earlier = earlierText(in: anchor, excluding: active)
        guard replaceLocalSuffix(active, with: new, at: anchor) else { updateCandidates(); return }
      } else { updateCandidates(); return }
      learn(word, as: .accepted, after: Predictor.previous(in: earlier))
      resetTyping()
      rememberEdit(original: old, inserted: new, label: old.isEmpty ? "suggestion" : old)
      if !suffix.isEmpty { suggestedSpace = Anchor(proxy) }
    }
    if automaticShift { render() } else { updateCandidates() }
  }

  private func makeToolbar() -> UIStackView {
    let bar = stack(.horizontal, gap: 0)
    bar.distribution = .fillEqually
    let available = lastWidth > 0 ? lastWidth : view.bounds.width
    let capacity = max(6, Int((available - Deck.keyboardEdge * 2) / 44))
    let primary: [Tool] = [.grammar, .tone, .ask, .translate, .emoji, .clipboard]
    // Face ID iPhones supply the system Dictation control below the extension.
    let voice: [Tool] = showsVoiceToolbar ? [.voice] : []
    let visible: [Tool] = [.more] + primary.prefix(capacity - 1 - voice.count) + voice
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
      b.accessibilityIdentifier = "toolbar-" + tool.rawValue
      b.isEnabled = !sensitive || [.more, .voice].contains(tool)
      b.alpha = b.isEnabled ? 1 : 0.35
      bar.addArrangedSubview(b)
    }
    return bar
  }

  private func makeCandidateRow() -> CandidateRow {
    CandidateRow((0..<3).map { index in
      let key = button("", .transparent) { [weak self] in
        guard let self, let candidateRow, candidateRow.candidates.indices.contains(index)
        else { return }
        choose(candidateRow.candidates[index])
      }
      key.titleLabel?.font = .systemFont(ofSize: 17, weight: .regular)
      key.accessibilityIdentifier = "candidate-\(index)"
      return key
    })
  }

  private var alternatives: [(style: String, text: String)] = []
  private func updateCandidates() {
    guard tool == nil, cursorDrag == nil, let candidateRow else { return }
    predictionWork?.cancel()
    predictionRevision += 1
    candidateAnchor = nil
    let showWords = Settings.predictiveSuggestions && allowsPrediction || showsSymbolPredictions
    guard showWords || !raw.isEmpty && allowsPhonetic || correction != nil,
      let anchor = Anchor(proxy)
    else { candidateRow.show([]); return }

    let undo = correction.flatMap { $0.anchor == anchor ? [Candidate.undo($0.label)] : nil } ?? []
    if let glide = glideEdit, glide.anchor == anchor {
      candidateAnchor = anchor
      candidateRow.show(Array((undo + glide.words.dropFirst().map(Candidate.word)).prefix(3)))
      return
    }
    let phonetic = raw.isEmpty ? [] : rules?.candidates(raw) ?? [raw]
    let active = activePrefix(in: anchor)
    let target = phonetic.isEmpty && (active == nil || active == "") && Settings.cursorAware
      ? CursorWord(anchor) : nil
    let query = phonetic.first ?? target?.word ?? active
    let earlier = target?.earlier ?? earlierText(in: anchor, excluding: phonetic.isEmpty ? query ?? "" : "")
    let context = Predictor.context(in: earlier)
    let following = (target?.later ?? anchor.after ?? "").split(whereSeparator: {
      !Predictor.isWordKey(String($0))
    }).first.map(String.init) ?? ""
    guard showWords, let query, !query.isEmpty || Settings.nextWordPrediction,
      let predictor
    else {
      candidateAnchor = anchor
      candidateRow.show(Array((undo + phonetic.map(Candidate.word)).prefix(3)))
      return
    }

    let revision = predictionRevision
    let language = typingLanguage
    let personal = personalWords
    let history = learningAllowed ? Settings.typingHistory : TypingHistory()
    let local = history.words(in: language)
    let learnedNext = history.following(Predictor.previous(in: earlier), in: language)
    let hints = target == nil && !prefix.isEmpty ? touchHints : [:]
    let segmentation = Settings.automaticSpacing
    let work = DispatchWorkItem { [weak self, weak candidateRow] in
      let matches = predictor.suggestions(
        for: query, after: context, language: language, personal: personal, history: local,
        following: following, touchHints: hints, allowSegmentation: segmentation, learnedNext: learnedNext)
      var seen = Set<String>()
      let ordered = phonetic.isEmpty ? matches
        : Array(phonetic.prefix(1)) + matches.prefix(1) + phonetic.dropFirst() + matches.dropFirst()
      let words = ordered.filter { seen.insert($0).inserted }.map(Candidate.word)
      DispatchQueue.main.async {
        guard let self, let candidateRow, revision == self.predictionRevision,
          self.tool == nil, candidateRow === self.candidateRow, anchor == Anchor(self.proxy)
        else { return }
        self.candidateAnchor = anchor
        candidateRow.show(Array((undo + words).prefix(3)))
      }
    }
    predictionWork = work
    Self.predictions.async(execute: work)
  }
  private var toolbarHeight: CGFloat {
    traitCollection.verticalSizeClass == .compact ? 35 : Deck.toolbar
  }
  private func open(_ next: Tool) {
    guard !sensitive || [.more, .voice].contains(next) else { return }
    close()
    predictionWork?.cancel()
    predictionRevision += 1
    tool = next
    mode = next == .ask ? "express" : "rewrite"
    style = next == .tone ? "My Style" : (Settings.favorites.first ?? "Respectful")
    if next.ai && !hasFullAccess { message = "Allow Full Access to use AI." }
    if next == .grammar, hasFullAccess, Settings.aiConsent, !userSource().isEmpty {
      generate()
    } else {
      render()
    }
  }
  private func render() {
    guard deleteTimer == nil, cursorDrag == nil, isViewLoaded else { return }
    overrideUserInterfaceStyle =
      ["Light": UIUserInterfaceStyle.light, "Dark": .dark][Settings.get("theme")] ?? .unspecified
    view.backgroundColor = Deck.background
    latinKeys.removeAll(keepingCapacity: true)
    shiftControl = nil
    returnControl = nil
    rows.arrangedSubviews.forEach {
      rows.removeArrangedSubview($0)
      $0.removeFromSuperview()
    }
    height.constant = Deck.height(
      compact: traitCollection.verticalSizeClass == .compact,
      preference: Settings.get("height", "Standard"), width: lastWidth)
    rows.distribution = .fill
    if hasEditor {
      renderEditor()
      return
    }
    if let tool {
      if tool.ai { renderAI(tool) } else { renderUtility(tool) }
      return
    }
    renderedAutomaticShift = automaticShift
    let bar = makeToolbar()
    rows.addArrangedSubview(bar)
    bar.heightAnchor.constraint(equalToConstant: toolbarHeight).isActive = true
    let candidates = makeCandidateRow()
    rows.addArrangedSubview(candidates)
    candidates.heightAnchor.constraint(equalToConstant:
      Deck.suggestionHeight(compact: traitCollection.verticalSizeClass == .compact)).isActive = true
    candidateRow = candidates
    updateCandidates()
    renderKeys()
  }
  private static let alternates: [String: [String]] = [
    "a": ["à", "á", "â", "ä", "æ", "ã", "å", "ā"],
    "c": ["ç", "ć", "č"], "e": ["è", "é", "ê", "ë", "ē"],
    "i": ["ì", "í", "î", "ï", "ī"], "n": ["ñ", "ń"],
    "o": ["ò", "ó", "ô", "ö", "œ", "õ", "ø", "ō"],
    "s": ["ß", "ś", "š"], "u": ["ù", "ú", "û", "ü", "ū"],
    "y": ["ÿ", "ý"], "z": ["ź", "ż", "ž"],
    "£": ["$", "€", "৳", "¥", "¢"], "$": ["£", "€", "৳", "¥", "¢"],
    "-": ["–", "—"], ".": ["…"], "'": ["’", "‘"],
    "\"": ["“", "”", "«", "»"], "?": ["¿"], "!": ["¡"],
  ]

  private var uppercasesLatinKeys: Bool { shift != 0 || automaticShift }

  private func character(_ text: String, alternate: String? = nil, uppercase: Bool? = nil) -> DeckKey {
    let followsShift = uppercase != nil
    let displayed = uppercase == true ? text.uppercased() : text
    let key = button(displayed, .letter) { [weak self] in
      guard let self else { return }
      self.insert(followsShift && self.uppercasesLatinKeys ? text.uppercased() : text)
    }
    if !sensitive { key.installPreview(in: view, text: displayed) }
    configureAlternates(of: key, displayed, additional: alternate, installGesture: true)
    if followsShift {
      latinKeys.append((key, text, alternate))
      key.observeTap = { [weak self, weak key] point in
        guard let self, let key, layout == 0, allowsPrediction, text.count == 1 else { return }
        let local = key.convert(point, to: rows)
        let center = key.convert(CGPoint(x: key.bounds.midX, y: key.bounds.midY), to: rows)
        pendingTapHint = nil
        guard hypot(local.x - center.x, local.y - center.y) > key.bounds.width * 0.27 else { return }
        let nearest = latinKeys.filter { $0.key !== key }.min { a, b in
          func distance(_ other: DeckKey) -> CGFloat {
            let mid = other.convert(CGPoint(x: other.bounds.midX, y: other.bounds.midY), to: self.rows)
            return hypot((mid.x - local.x) / key.bounds.width, (mid.y - local.y) / key.bounds.height)
          }
          return distance(a.key) < distance(b.key)
        }
        pendingTapHint = nearest?.letter.first
      }
    }
    return key
  }

  private func configureAlternates(
    of key: DeckKey, _ text: String, additional: String?, installGesture: Bool
  ) {
    let letter = text.lowercased()
    var options = Self.alternates[letter] ?? []
    if text != letter { options = options.map { $0.uppercased() } }
    if let additional { options.append(additional) }
    if options.count == 1, let only = options.first {
      key.alternate = { [weak self] in
        UIDevice.current.playInputClick()
        self?.insert(only)
      }
      key.accessibilityHint = "Hold for " + only
      if installGesture {
        key.addGestureRecognizer(
          UILongPressGestureRecognizer(target: key, action: #selector(DeckKey.held(_:))))
      }
    } else if !options.isEmpty {
      key.menu = UIMenu(children: options.map { option in
        UIAction(title: option) { [weak self] _ in
          UIDevice.current.playInputClick()
          self?.insert(option)
        }
      })
      key.accessibilityHint = "Hold for alternate characters"
    }
  }

  private var shiftSymbol: String {
    shift == 2 ? "capslock.fill" : uppercasesLatinKeys ? "shift.fill" : "shift"
  }

  private var shiftLabel: String {
    shift == 2 ? "Caps Lock" : uppercasesLatinKeys ? "On" : "Off"
  }

  private func refreshLatinShift() {
    renderedAutomaticShift = automaticShift
    let uppercase = uppercasesLatinKeys
    for (key, letter, alternate) in latinKeys {
      let displayed = uppercase ? letter.uppercased() : letter
      key.setTitle(displayed, for: .normal)
      key.accessibilityLabel = displayed
      key.setPreviewText(displayed)
      configureAlternates(of: key, displayed, additional: alternate, installGesture: false)
    }
    shiftControl?.setImage(
      UIImage(systemName: shiftSymbol,
        withConfiguration: UIImage.SymbolConfiguration(pointSize: 18, weight: .medium)), for: .normal)
    shiftControl?.accessibilityValue = shiftLabel
  }

  private func deleteKey() -> DeckKey {
    let b = glyph("delete.left", "Backspace") { self.erase() }
    b.addGestureRecognizer(
      UILongPressGestureRecognizer(target: self, action: #selector(heldDelete(_:))))
    return b
  }
  private func shiftKey() -> DeckKey {
    let key = glyph(shiftSymbol, "Shift") {
      let t = Date.timeIntervalSinceReferenceDate
      if self.automaticShift {
        self.autoShiftSuppressed = true
      } else {
        self.shift = self.shift == 1 && t - self.typedAt < 0.55 ? 2 : self.shift == 0 ? 1 : 0
      }
      self.typedAt = t
      if self.layout == 1 {
        self.render()
      } else {
        self.refreshLatinShift()
      }
    }
    key.accessibilityValue = shiftLabel
    shiftControl = key
    return key
  }

  func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
    if gestureRecognizer === glideGesture {
      guard Settings.glideTyping, canPredictHere, tool == nil, page == 0, layout != 1,
        let hit = touch.view as? DeckKey, latinKeys.contains(where: { $0.key === hit }) else { return false }
      glideGesture.pitch = max(1, hit.bounds.width)
      return true
    }
    guard let shiftControl, layout != 1, page == 0, !numericPage, !numericHost
    else { return false }
    return shiftControl.bounds.contains(touch.location(in: shiftControl))
  }

  @objc private func handleGlide(_ gesture: LetterGlideRecognizer) {
    guard canPredictHere, layout != 1, tool == nil, !hasEditor else {
      glideTrail.path = nil
      glideAnchor = nil
      return
    }
    if gesture.state == .began {
      guard raw.isEmpty, let at = Anchor(proxy), at.selected?.isEmpty != false,
        (at.after?.first).map({ !Predictor.isWordKey(String($0)) }) != false else { return }
      glideAnchor = at
      pendingTapHint = nil
    }
    guard let anchor = glideAnchor else { return }
    switch gesture.state {
    case .began, .changed:
      guard Settings.gestureTrail, let start = gesture.samples.first else { return }
      let path = UIBezierPath()
      path.move(to: start)
      for point in gesture.samples.dropFirst() { path.addLine(to: point) }
      glideTrail.strokeColor = Deck.brand.withAlphaComponent(0.48).cgColor
      glideTrail.lineWidth = max(4, gesture.pitch * 0.18)
      glideTrail.path = path.cgPath
    case .ended:
      glideAnchor = nil
      glideTrail.path = nil
      guard anchor == Anchor(proxy), let predictor,
        let q = latinKeys.first(where: { $0.letter == "q" })?.key,
        let a = latinKeys.first(where: { $0.letter == "a" })?.key else { return }
      let qCenter = q.convert(CGPoint(x: q.bounds.midX, y: q.bounds.midY), to: rows)
      let aCenter = a.convert(CGPoint(x: a.bounds.midX, y: a.bounds.midY), to: rows)
      let pitchY = max(1, aCenter.y - qCenter.y)
      let points = gesture.samples.map { GlidePoint(x: Double(($0.x - qCenter.x) / gesture.pitch),
        y: Double(($0.y - qCenter.y) / pitchY)) }
      let index = layout
      let lang: TypingLanguage = index == 0 ? .english : .banglish
      let context = Predictor.context(in: anchor.before ?? "")
      let capital = uppercasesLatinKeys && index == 0
      let revision = predictionRevision
      let addSpace = Settings.spaceAfterSuggestion
      Self.predictions.async { [weak self] in
        let decoder = Self.glideModels[lang] ?? GlideDecoder(vocabulary: predictor.glideVocabulary(language: lang))
        Self.glideModels[lang] = decoder
        let contextual = predictor.suggestions(for: "", after: context, language: lang, personal: [])
        let words = decoder.candidates(points: points, contextual: contextual)
        DispatchQueue.main.async { [weak self] in
          self?.commitGlide(words, index: index, anchor: anchor, revision: revision,
            capital: capital, addSpace: addSpace)
        }
      }
    default:
      glideAnchor = nil
      glideTrail.path = nil
    }
  }

  private func commitGlide(_ decoded: [String], index: Int, anchor: Anchor, revision: Int,
    capital: Bool, addSpace: Bool) {
    guard !decoded.isEmpty, isInputVisible, tool == nil, canPredictHere,
      layout == index, revision == predictionRevision, anchor == Anchor(proxy) else { return }
    let words = decoded.map { word in
      index == 2 ? rules?.render(word) ?? word : capital ? word.prefix(1).uppercased() + word.dropFirst() : word
    }
    let leading = anchor.before?.last.map({ Predictor.isWordKey(String($0)) }) == true ? " " : ""
    let trailing = addSpace && (anchor.after ?? "").isEmpty ? " " : ""
    let inserted = leading + words[0] + trailing
    proxy.insertText(inserted)
    resetTyping()
    if shift == 1 { shift = 0 }
    rememberEdit(original: "", inserted: inserted, label: "glide")
    if let at = Anchor(proxy) {
      if !trailing.isEmpty { suggestedSpace = at }
      glideEdit = (at, words, leading, trailing)
    }
    refreshLatinShift()
    updateReturnKey()
    updateCandidates()
  }

  @objc private func lockShift(_ gesture: UITapGestureRecognizer) {
    guard gesture.state == .recognized, layout != 1, page == 0, !numericPage, !numericHost
    else { return }
    shift = 2
    autoShiftSuppressed = false
    refreshLatinShift()
  }

  private func setPage(_ value: Int, numeric: Bool = false) {
    if hasEditor { finishEditorWord() } else { finishWord() }
    if numeric || numericPage { resetTyping() }
    page = value
    numericPage = numeric
    render()
  }
  private func updateReturnKey() {
    guard let returnControl else { return }
    let enabled = hasEditor || proxy.enablesReturnKeyAutomatically != true
      || proxy.hasText || !raw.isEmpty
    returnControl.isEnabled = enabled
    returnControl.alpha = enabled ? 1 : 0.54
  }

  private func enterKey() -> DeckKey {
    let label: String
    switch hasEditor ? .default : proxy.returnKeyType {
    case .search, .google, .yahoo: label = "Search"
    case .next: label = "Next"
    case .done: label = "Done"
    case .send: label = "Send"
    case .go: label = "Go"
    case .join: label = "Join"
    case .route: label = "Route"
    case .emergencyCall: label = "Emergency call"
    case .continue: label = "Continue"
    default: label = "Return"
    }
    let key: DeckKey
    if label == "Return" {
      key = glyph("return", label) { self.insert("\n") }
    } else {
      key = button(label.lowercased(), .accent) { self.insert("\n") }
      key.titleLabel?.font = .systemFont(ofSize: 16, weight: .regular)
      key.accessibilityLabel = label
    }
    returnControl = key
    updateReturnKey()
    return key
  }

  private func renderNumericKeys(in keys: UIStackView) {
    for group in ["123", "456", "789"] {
      lane(group.map {
        let key = character(String($0))
        key.titleLabel?.font = .systemFont(ofSize: 26)
        return (key, 10.0 / 3)
      }, in: keys, typing: true)
    }
    let third: CGFloat = 10 / 3
    let decimal = Locale.current.decimalSeparator ?? "."
    let globe = needsInputModeSwitchKey && !hasEditor
    let leading: [(UIView, CGFloat)]
    if !numericHost {
      let width = third / (globe ? 3 : 2)
      leading = [
        (button("ABC") { self.setPage(0) }, width),
        (button(decimal) { self.insert(decimal) }, width),
      ]
    } else if proxy.keyboardType == .decimalPad {
      leading = [(button(decimal) { self.insert(decimal) }, globe ? third / 2 : third)]
    } else {
      leading = globe ? [] : [(UIView(), third)]
    }
    var bottom = leading
    if globe { bottom.append((systemSwitchKey(), third - leading.reduce(0) { $0 + $1.1 })) }
    let zero = character("0")
    zero.titleLabel?.font = .systemFont(ofSize: 26)
    bottom += [(zero, third), (deleteKey(), third)]
    lane(bottom, in: keys, typing: true)
  }

  private func spaceKey() -> DeckKey {
    let title = hasEditor || layout == 0 ? "space" : layouts[layout]
    let space = button(title, .letter) { self.insert(" ") }
    space.titleLabel?.font = .systemFont(ofSize: 16)
    space.accessibilityLabel = "Space"
    space.accessibilityValue = layouts[layout]
    if !hasEditor && !sensitive {
      space.accessibilityHint = "Hold and slide to move the cursor"
      let cursor = UILongPressGestureRecognizer(target: self, action: #selector(dragSpace(_:)))
      cursor.minimumPressDuration = 0.4
      space.addGestureRecognizer(cursor)
      space.accessibilityCustomActions = [
        UIAccessibilityCustomAction(name: "Typing language") { [weak self] _ in
          self?.open(.language)
          return self != nil
        }
      ]
    }
    return space
  }

  @objc private func dragSpace(_ gesture: UILongPressGestureRecognizer) {
    let x = gesture.location(in: view).x
    switch gesture.state {
    case .began:
      guard !sensitive, !hasEditor, let id = documentID(proxy) else { return }
      cursorDrag = (id, x)
      finishWord()
      resetTyping()
      candidateRow?.show([])
    case .changed:
      guard let drag = cursorDrag, drag.document == documentID(proxy), !sensitive else { return }
      let distance = x - drag.x
      let step = Int(distance / 11)
      guard step != 0 else { return }
      proxy.adjustTextPosition(byCharacterOffset: step)
      cursorDrag?.x = drag.x + CGFloat(step) * 11
    case .ended, .cancelled, .failed:
      guard cursorDrag != nil else { return }
      cursorDrag = nil
      render()
    default: break
    }
  }

  private func renderKeys() {
    let keys = stack(.vertical, gap: 0)
    keys.distribution = .fillEqually
    rows.addArrangedSubview(keys)
    if numericPage {
      renderNumericKeys(in: keys)
      return
    }
    if page > 0 {
      let map = page == 1 ? KeyMaps.symbols : KeyMaps.secondary
      lane(map[0].map { (character($0), 1) }, in: keys, typing: true)
      lane(map[1].map { (character($0), 1) }, in: keys, typing: true)
      var last: [(UIView, CGFloat)] = [
        (button(page == 1 ? "#+=" : "123") { self.setPage(self.page == 1 ? 2 : 1) }, 1.3),
        (UIView(), 0.2),
      ]
      last += map[2].map { (character($0), 1.4) }
      last += [(UIView(), 0.2), (deleteKey(), 1.3)]
      lane(last, in: keys, typing: true)
    } else if layout == 1 {
      let map = shift == 0 ? KeyMaps.bangla : KeyMaps.signs
      for group in map {
        lane(group.map { (character($0), 10 / CGFloat(group.count)) }, in: keys, typing: true)
      }
    } else {
      let uppercase = uppercasesLatinKeys
      for (i, group) in KeyMaps.latin.enumerated() {
        var items: [(UIView, CGFloat)] = i == 2 ? [(shiftKey(), 1.3), (UIView(), 0.2)] : []
        for (j, ch) in group.enumerated() {
          let number = i == 0 ? String((j + 1) % 10) : nil
          items.append((character(ch, alternate: number, uppercase: uppercase), 1))
        }
        if i == 2 { items += [(UIView(), 0.2), (deleteKey(), 1.3)] }
        lane(items, in: keys, typing: true)
      }
    }

    let space = spaceKey()
    let mode = button(page > 0 ? "ABC" : "123") { self.setPage(self.page == 0 ? 1 : 0) }
    let extraGlobe = !hasEditor && needsInputModeSwitchKey
    let emojiOrLanguage = hasEditor ? editorLanguageKey() : emojiKey()
    var bottom: [(UIView, CGFloat)]
    if page == 0 && layout == 1 {
      bottom = [(mode, 1.2), (shiftKey(), 1.15), (emojiOrLanguage, 1.15)]
      if extraGlobe { bottom.append((systemSwitchKey(), 1.15)) }
      bottom += [
        (space, extraGlobe ? 2.1 : 3.25), (character("।"), 0.8),
        (deleteKey(), 1.2), (enterKey(), 1.25),
      ]
    } else if [.URL, .emailAddress, .webSearch].contains(proxy.keyboardType ?? .default),
      page == 0, !hasEditor {
      let punctuation = proxy.keyboardType == .emailAddress ? "@" : "/"
      bottom = [(mode, 1.2), (emojiOrLanguage, 1.1)]
      if extraGlobe { bottom.append((systemSwitchKey(), 1.1)) }
      bottom += [
        (space, extraGlobe ? 2.4 : 3.5), (character(punctuation), 0.9),
        (character("."), 0.8), (enterKey(), 2.5),
      ]
    } else {
      bottom = [(mode, 1.25), (emojiOrLanguage, 1.25)]
      if extraGlobe { bottom.append((systemSwitchKey(), 1.1)) }
      bottom += [(space, extraGlobe ? 3.9 : 5), (enterKey(), 2.5)]
    }
    lane(bottom, in: keys, typing: true)
  }

  private func header(_ title: String) {
    let h = stack(.horizontal, gap: 6)
    let closeDashboard = tool == .more
    let back = glyph(closeDashboard ? "xmark" : "chevron.backward",
      closeDashboard ? "Close tools" : "Return to keyboard", .transparent) {
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
  private func generate(consenting: Bool = false) {
    guard hasFullAccess else {
      message = "Allow Full Access to use AI."
      render()
      return
    }
    guard let anchor = Anchor(proxy), !sensitive else {
      message = "Focus a text field to use AI."
      render()
      return
    }
    let draft = userSource()
    if mode == "reply" && draft.isEmpty {
      message = "Paste or select the incoming message first."
      render()
      return
    }
    guard Settings.aiConsent || consenting else {
      render()
      return
    }
    if consenting, !draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
      draft.utf16.count <= 1600
    {
      Settings.prefs.set(true, forKey: "aiConsent")
    }
    start(draft, at: anchor,
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
    if !forAI, canPredictHere { Settings.addClip(s) }
    message = ""
    render()
  }
  private func renderAI(_ t: Tool) {
    header(t.rawValue)
    if t == .tone || (!loading && preview.isEmpty) {
      if t == .tone {
        let options =
          tones
          + (Settings.favorites + Settings.styles).reduce(into: [String]()) { list, v in
            if !tones.contains(v) && !list.contains(v) { list.append(v) }
          }
        chips(options, selected: style) { value in
          guard value != self.style || (!self.loading && self.preview.isEmpty) else { return }
          self.style = value
          if Settings.aiConsent, self.hasFullAccess {
            if (self.loading || !self.preview.isEmpty), !self.targetChanged,
              let anchor = self.origin, anchor == Anchor(self.proxy)
            {
              self.start(self.source, at: anchor, replace: self.replaceSelection)
            } else {
              self.generate()
            }
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
    let draft = hasFullAccess ? userSource() : ""
    let selection = hasFullAccess && proxy.selectedText?.isEmpty == false
    let needsConsent = hasFullAccess && !Settings.aiConsent
      && !draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    if needsConsent {
      let disclosure = UILabel()
      disclosure.text = "AI sends the draft below and your writing preferences to the service. Future Grammar and tone shortcuts can send directly. You review every suggestion."
      disclosure.textColor = Deck.ink
      disclosure.font = .preferredFont(forTextStyle: .footnote)
      disclosure.adjustsFontForContentSizeCategory = true
      disclosure.numberOfLines = 0
      disclosure.setContentCompressionResistancePriority(.required, for: .vertical)
      rows.addArrangedSubview(disclosure)
    }
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
    inputActions.append(button(needsConsent ? "Allow & Generate" : "Generate", .accent) {
      self.generate(consenting: needsConsent)
    })
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
      let letters = button("ABC") {
        self.close()
        self.render()
      }
      var controls: [UIView] = [letters]
      if needsInputModeSwitchKey { controls.append(systemSwitchKey()) }
      controls.append(deleteKey())
      actions(controls)
    case .clipboard:
      renderClipboard()
    case .voice:
      rows.addArrangedSubview(
        scroller(
          "Tap the iOS microphone below the keyboard when it’s available. You can also switch to the Apple keyboard and use its Dictation."
        ))
      actions([button("Switch keyboard", .accent) { self.advanceToNextInputMode() }])
    case .more:
      let choices: [Tool] = [.theme, .clipboard, .emoji, .language, .editing, .layout] + (Settings.voiceTyping ? [.voice] : []) + [.settings]
      toolGrid(choices.map { entry in
        let b = utilityTile(entry.rawValue, icon: entry.icon()) { self.open(entry) }
        b.isEnabled = !sensitive || entry == .voice
        if entry == .settings { b.accessibilityIdentifier = "dashboard-settings" }
        return b
      })
    case .theme:
      utilityChoices(["System", "Light", "Dark"], selected: Settings.get("theme", "System")) {
        Settings.prefs.set($0, forKey: "theme")
        self.render()
      }
    case .language:
      utilityChoices(layouts, selected: layouts[layout]) { name in
        guard let index = self.layouts.firstIndex(of: name) else { return }
        self.close()
        self.chooseLayout(index)
      }
    case .editing:
      toolGrid([
        utilityTile("Move cursor left", symbol: "arrow.left") { self.moveCursor(by: -1) },
        utilityTile("Move cursor right", symbol: "arrow.right") { self.moveCursor(by: 1) },
        utilityTile("Backspace", symbol: "delete.left") {
          self.finishWord()
          self.erase()
          self.close()
          self.render()
        },
        utilityTile("Switch keyboard", symbol: "globe") { self.advanceToNextInputMode() },
      ])
    case .layout:
      toolGrid([
        utilityTile("Letters", symbol: "character") { self.showKeyPage(0) },
        utilityTile("Symbols", symbol: "number") { self.showKeyPage(1) },
        utilityTile("Numbers", symbol: "123.rectangle") { self.showKeyPage(0, numeric: true) },
        menu("Keyboard height", choices: ["Standard", "Compact", "Tall"].map { ($0, $0) }) {
          Settings.prefs.set($0, forKey: "height")
          self.render()
        },
      ])
    case .settings:
      let choices: [(String, String, Bool)] = [
        ("Predictive suggestions", "predictiveSuggestions", Settings.predictiveSuggestions),
        ("Next-word prediction", "nextWordPrediction", Settings.nextWordPrediction),
        ("Autocorrect", "autocorrect", Settings.autocorrect),
        ("Auto-capitalization", "automaticCapitalization", Settings.automaticCapitalization),
        ("Haptics", "haptics", Settings.prefs.bool(forKey: "haptics")),
      ]
      let tools = choices.map { title, key, enabled in
        let b = utilityTile(title, symbol: enabled ? "checkmark.circle.fill" : "circle") {
          Settings.prefs.set(!enabled, forKey: key)
          self.render()
        }
        b.accessibilityValue = enabled ? "On" : "Off"
        return b
      }
      toolGrid(tools)
    default: break
    }
  }

  @discardableResult private func deleteSelectedText(at original: Anchor, label: String) -> Bool {
    guard !sensitive, Anchor(proxy) == original, let text = original.selected, !text.isEmpty else { return false }
    resetTyping()
    // The host owns its selection and may consume an adjacent space during a
    // smart delete. A single native deletion never walks into neighboring words.
    // The extension proxy can lag WebKit, so compare two settled observations.
    let ticket = UUID()
    pendingSelectionDelete = ticket
    proxy.deleteBackward()
    func observe(_ previous: Anchor?, attemptsLeft: Int) {
      DispatchQueue.main.asyncAfter(deadline: .now() + 0.14) { [weak self] in
        guard let self, pendingSelectionDelete == ticket, isInputVisible, !sensitive,
          let after = Anchor(proxy), after.document == original.document else { return }
        guard after == previous, after.selected?.isEmpty != false else {
          if attemptsLeft > 0 { observe(after, attemptsLeft: attemptsLeft - 1) }
          else { pendingSelectionDelete = nil }
          return
        }
        pendingSelectionDelete = nil
        let left = original.before ?? "", right = original.after ?? ""
        let nowLeft = after.before ?? "", nowRight = after.after ?? ""
        let lostLeft = left == nowLeft ? "" : " "
        let lostRight = right == nowRight ? "" : " "
        guard left == nowLeft + lostLeft, right == lostRight + nowRight,
          after.before != nil || after.after != nil else { return }
        // With smart deletion WebKit can report the old boundary whitespace even
        // after its DOM caret has moved. Do not offer a reconstruction in that case.
        if lostLeft.isEmpty && lostRight.isEmpty &&
          (left.last?.isWhitespace == true || right.first?.isWhitespace == true) { return }
        rememberEdit(original: lostLeft + text + lostRight, inserted: "", label: label)
        updateCandidates()
      }
    }
    observe(nil, attemptsLeft: 4)
    return true
  }

  private func copySelection(cutting: Bool) {
    guard canPredictHere, hasFullAccess, let anchor = Anchor(proxy),
      let text = anchor.selected, !text.isEmpty else { return }
    UIPasteboard.general.setItems([["public.utf8-plain-text": text]], options: [.localOnly: true])
    Settings.addClip(text)
    if cutting, anchor == Anchor(proxy) {
      if deleteSelectedText(at: anchor, label: "cut") {
        close()
      } else {
        message = "Copied the selection. Use this app’s Edit menu to cut it."
      }
    } else {
      message = "Copied selection."
    }
    render()
  }

  private func insertClip(_ text: String) {
    guard canPredictHere, hasFullAccess, let anchor = Anchor(proxy) else { return }
    let inserted = takeWord() + text
    proxy.insertText(inserted)
    resetTyping()
    rememberEdit(original: anchor.selected ?? "", inserted: inserted, label: "paste")
    close()
    render()
  }

  private func renderClipboard() {
    guard canPredictHere, hasFullAccess else {
      rows.addArrangedSubview(scroller(sensitive || !canPredictHere ?
        "Clipboard is unavailable in this field." : "Allow Full Access to read the clipboard."))
      return
    }
    let selected = proxy.selectedText?.isEmpty == false
    let copy = button("Copy") { self.copySelection(cutting: false) }
    let cut = button("Cut") { self.copySelection(cutting: true) }
    for key in [copy, cut] { key.isEnabled = selected; key.alpha = selected ? 1 : 0.45 }
    actions([copy, cut, button("Load clipboard") { self.loadPaste(forAI: false) }])
    let scroll = UIScrollView()
    let list = stack(.vertical, gap: 5)
    list.translatesAutoresizingMaskIntoConstraints = false
    scroll.addSubview(list)
    NSLayoutConstraint.activate([
      list.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor),
      list.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor),
      list.trailingAnchor.constraint(equalTo: scroll.contentLayoutGuide.trailingAnchor),
      list.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor),
      list.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor),
    ])
    if let current = pasted {
      let preview = UILabel()
      preview.text = String(current.prefix(130))
      preview.numberOfLines = 2
      preview.font = .preferredFont(forTextStyle: .body)
      preview.textColor = Deck.ink
      list.addArrangedSubview(preview)
      let insert = button("Insert", .accent) { self.insertClip(current) }
      insert.heightAnchor.constraint(equalToConstant: 38).isActive = true
      list.addArrangedSubview(insert)
    }
    let clips = Settings.clips().sorted { $0.pinned && !$1.pinned }
    for clip in clips {
      let line = stack(.horizontal, gap: 4)
      let insert = button(String(clip.text.prefix(40)), .letter) { self.insertClip(clip.text) }
      insert.contentHorizontalAlignment = .leading
      insert.titleLabel?.lineBreakMode = .byTruncatingTail
      insert.accessibilityLabel = "Insert saved item, " + String(clip.text.prefix(60))
      let pin = glyph(clip.pinned ? "pin.fill" : "pin", clip.pinned ? "Unpin item" : "Pin item") {
        var saved = Settings.clips()
        guard let i = saved.firstIndex(where: { $0.id == clip.id }) else { return }
        saved[i].pinned.toggle()
        saved[i].date = Date()
        Settings.saveClips(saved)
        self.render()
      }
      let delete = glyph("trash", "Delete item") {
        Settings.saveClips(Settings.clips().filter { $0.id != clip.id })
        self.render()
      }
      for key in [insert, pin, delete] { line.addArrangedSubview(key) }
      for key in [pin, delete] { key.widthAnchor.constraint(equalToConstant: 44).isActive = true }
      line.heightAnchor.constraint(equalToConstant: 40).isActive = true
      list.addArrangedSubview(line)
    }
    if clips.isEmpty && pasted == nil || !message.isEmpty {
      let label = UILabel()
      label.text = message.isEmpty ? "Load clipboard to preview and insert text." : message
      label.numberOfLines = 0
      label.textColor = Deck.ink
      label.font = .preferredFont(forTextStyle: .subheadline)
      list.addArrangedSubview(label)
    }
    rows.addArrangedSubview(scroll)
    if !clips.isEmpty {
      actions([button("Clear history") { Settings.clearClips(); self.pasted = nil; self.render() }])
    }
  }

  private func utilityTile(
    _ title: String, icon: UIImage? = nil, symbol: String? = nil, action: @escaping () -> Void
  ) -> DeckKey {
    let tile = button("", .pill, action: action)
    var config = UIButton.Configuration.plain()
    config.image = icon ?? symbol.flatMap {
      UIImage(systemName: $0, withConfiguration: UIImage.SymbolConfiguration(pointSize: 17, weight: .medium))
    }
    config.title = title
    config.titleTextAttributesTransformer = UIConfigurationTextAttributesTransformer { style in
      var style = style
      style.font = .systemFont(ofSize: 14, weight: .medium)
      return style
    }
    config.imagePadding = 9
    config.contentInsets = NSDirectionalEdgeInsets(top: 0, leading: 13, bottom: 0, trailing: 8)
    config.baseForegroundColor = Deck.ink
    tile.configuration = config
    tile.contentHorizontalAlignment = .leading
    tile.accessibilityLabel = title
    return tile
  }

  private func toolGrid(_ buttons: [DeckKey]) {
    let grid = stack(.vertical, gap: 6)
    grid.distribution = .fillEqually
    grid.isLayoutMarginsRelativeArrangement = true
    grid.directionalLayoutMargins = NSDirectionalEdgeInsets(top: 4, leading: 9, bottom: 8, trailing: 9)
    for start in stride(from: 0, to: buttons.count, by: 2) {
      let line = stack(.horizontal, gap: 7)
      line.distribution = .fillEqually
      for b in buttons[start..<min(start + 2, buttons.count)] { line.addArrangedSubview(b) }
      if line.arrangedSubviews.count == 1 { line.addArrangedSubview(UIView()) }
      grid.addArrangedSubview(line)
    }
    rows.addArrangedSubview(grid)
    // Keep small utility groups at the top with the same cap heights as the tools grid.
    if buttons.count < 7 {
      grid.heightAnchor.constraint(equalToConstant: CGFloat((buttons.count + 1) / 2) * 42 + 12).isActive = true
      rows.addArrangedSubview(UIView())
    }
  }

  private func utilityChoices(_ choices: [String], selected: String, pick: @escaping (String) -> Void) {
    toolGrid(choices.map { name in
      let tile = utilityTile(name, symbol: selected == name ? "checkmark.circle.fill" : "circle") {
        pick(name)
      }
      if selected == name { tile.accessibilityTraits.insert(.selected) }
      return tile
    })
  }

  private func moveCursor(by offset: Int) {
    finishWord()
    resetTyping()
    proxy.adjustTextPosition(byCharacterOffset: offset)
  }

  private func showKeyPage(_ value: Int, numeric: Bool = false) {
    close()
    setPage(value, numeric: numeric)
  }

}
