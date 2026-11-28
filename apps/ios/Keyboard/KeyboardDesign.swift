import UIKit

enum Deck {
  static func color(_ light: UInt32, _ dark: UInt32) -> UIColor {
    UIColor { traits in
      let n = traits.userInterfaceStyle == .dark ? dark : light
      return UIColor(
        red: CGFloat(n >> 16) / 255, green: CGFloat((n >> 8) & 255) / 255,
        blue: CGFloat(n & 255) / 255, alpha: 1)
    }
  }
  static let background = color(0xF0F2EF, 0x121816), key = color(0xFFFFFF, 0x29312E)
  static let function = color(0xDCE9E2, 0x354B42), brand = color(0x006B58, 0x006B58)
  static let ink = color(0x192923, 0xF5F7F6), muted = color(0x75847D, 0xA4B3AC)
  static let separator = color(0xBECBC3, 0x4A5A53)
  static let gap: CGFloat = 4.5, rowGap: CGFloat = 5, specialRadius: CGFloat = 13
  static let toolbar: CGFloat = 44, letter: CGFloat = 22, utility: CGFloat = 15
  static let keyboardEdge: CGFloat = 1
  static func suggestionHeight(compact: Bool) -> CGFloat { compact ? 27 : 45 }
  static func height(compact: Bool, preference: String, width: CGFloat) -> CGFloat {
    let offset: CGFloat = ["Compact": -18, "Tall": 22][preference] ?? 0
    let fittedWidth = min(460, max(320, width > 0 ? width : 390))
    // The reference is 1,170 px wide with a 162 px row pitch and a 129 px face.
    let pitch = compact ? 36 : (fittedWidth * 162 / 1170).rounded()
    let chrome = (compact ? 35 : toolbar) + suggestionHeight(compact: compact) + 8 + 2 * rowGap
    return chrome + pitch * 4 + offset
  }
}

// Widths are fractions of one shared ten-column deck. The hit box fills the column;
// the optical gaps belong to the face inside each button.
final class KeyLane: UIView {
  let entries: [(UIView, CGFloat)]
  init(_ entries: [(UIView, CGFloat)]) {
    self.entries = entries
    super.init(frame: .zero)
    for (item, _) in entries { addSubview(item) }
  }
  required init?(coder: NSCoder) { fatalError() }
  override func layoutSubviews() {
    super.layoutSubviews()
    let unit = bounds.width / 10
    var x = (bounds.width - entries.reduce(CGFloat.zero) { $0 + $1.1 } * unit) / 2
    for (item, units) in entries {
      let end = x + unit * units
      item.frame = CGRect(x: x, y: 0, width: end - x, height: bounds.height)
      x = end
    }
  }
}

final class DeckKey: UIButton {
  let face = UIView(), hint = UILabel()
  var alternate: (() -> Void)?
  var isTypingKey = false
  var observeTap: ((CGPoint) -> Void)?
  override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
    if let touch = touches.first { observeTap?(touch.location(in: self)) }
    super.touchesBegan(touches, with: event)
  }
  private let kind: Kind
  private weak var previewHost: UIView?
  private var previewText: String?
  private var preview: UILabel?
  init(_ title: String, kind: Kind = .letter) {
    self.kind = kind
    super.init(frame: .zero)
    face.isUserInteractionEnabled = false
    insertSubview(face, at: 0)
    face.backgroundColor =
      kind == .transparent
      ? .clear : kind == .accent ? Deck.brand : (kind == .function || kind == .pill) ? Deck.function : Deck.key
    face.layer.cornerRadius = Deck.specialRadius
    face.layer.cornerCurve = .continuous
    setTitle(title, for: .normal)
    setTitleColor(kind == .accent ? .white : Deck.ink, for: .normal)
    titleLabel?.font = .systemFont(
      ofSize: kind == .letter ? Deck.letter : Deck.utility,
      weight: kind == .letter ? .regular : .medium)
    titleLabel?.adjustsFontSizeToFitWidth = true
    titleLabel?.minimumScaleFactor = 0.72
    hint.font = .systemFont(ofSize: 9, weight: .medium)
    hint.textColor = Deck.color(0x5A6862, 0xA4B3AC)
    hint.textAlignment = .right
    hint.isAccessibilityElement = false
    addSubview(hint)
    accessibilityLabel = title
  }
  enum Kind { case letter, function, pill, accent, transparent }
  required init?(coder: NSCoder) { fatalError() }
  override var isHighlighted: Bool { didSet { face.alpha = isHighlighted ? 0.55 : 1 } }
  override func layoutSubviews() {
    super.layoutSubviews()
    let verticalGap = isTypingKey ? (bounds.height * 0.10).rounded() : 0.5
    face.frame = bounds.insetBy(dx: isTypingKey ? 3 : Deck.gap / 2, dy: verticalGap)
    face.layer.cornerRadius = isTypingKey ? 5 : Deck.specialRadius
    if isTypingKey { face.layer.cornerCurve = .circular }
    sendSubviewToBack(face)
    hint.frame = CGRect(x: 1, y: verticalGap + 2, width: bounds.width - 7, height: 12)
  }
  func installPreview(in host: UIView, text: String) {
    previewHost = host
    previewText = text
    addTarget(self, action: #selector(showPreview), for: .touchDown)
    addTarget(self, action: #selector(hidePreview), for: [.touchUpInside, .touchUpOutside, .touchCancel, .touchDragExit])
  }

  func setPreviewText(_ text: String) { previewText = text }

  @objc private func showPreview() {
    guard let host = previewHost, let text = previewText, preview == nil else { return }
    let keyRect = convert(face.frame, to: host)
    let available = host.safeAreaLayoutGuide.layoutFrame.insetBy(dx: 2, dy: 0)
    let width = min(available.width, max(42, keyRect.width + 14))
    guard width > 0 else { return }
    let height = max(42, keyRect.height + 12)
    let edge = min(max(keyRect.midX - width / 2, available.minX), available.maxX - width)
    let bubble = UILabel(frame: CGRect(x: edge, y: max(available.minY, keyRect.minY - height + 6),
      width: width, height: height))
    bubble.text = text
    bubble.font = .systemFont(ofSize: 30, weight: .regular)
    bubble.textAlignment = .center
    bubble.textColor = Deck.ink
    bubble.backgroundColor = Deck.key
    bubble.layer.cornerRadius = 7
    bubble.layer.masksToBounds = true
    bubble.isUserInteractionEnabled = false
    bubble.isAccessibilityElement = false
    host.addSubview(bubble)
    preview = bubble
  }

  @objc private func hidePreview() {
    preview?.removeFromSuperview()
    preview = nil
  }

  override func didMoveToWindow() {
    super.didMoveToWindow()
    if window == nil { hidePreview() }
  }

  @objc func held(_ recognizer: UILongPressGestureRecognizer) {
    if recognizer.state == .began {
      hidePreview()
      alternate?()
    }
  }
}

// Small single-weight, original toolbar glyphs constructed in the same 24-point grid.
// A recognizer stays possible for ordinary taps and long-press accents. Crossing a
// letter width cancels the initial key before its touch-up action can insert it.
final class LetterGlideRecognizer: UIGestureRecognizer {
  var pitch: CGFloat = 40
  var samples: [CGPoint] = []
  private var tracked: UITouch?
  override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
    guard tracked == nil, touches.count == 1, let touch = touches.first else {
      state = state == .possible ? .failed : .cancelled
      return
    }
    tracked = touch
    samples = [touch.location(in: view)]
  }
  override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
    guard let tracked, touches.contains(tracked), let first = samples.first else { return }
    for touch in event.coalescedTouches(for: tracked) ?? [tracked] {
      let point = touch.location(in: view)
      if let previous = samples.last, hypot(point.x - previous.x, point.y - previous.y) < pitch * 0.09 { continue }
      if samples.count >= 160 {
        samples = samples.enumerated().compactMap { $0.offset.isMultiple(of: 2) ? $0.element : nil }
      }
      samples.append(point)
    }
    guard let last = samples.last else { return }
    if state == .possible, hypot(last.x - first.x, last.y - first.y) >= pitch * 0.82 { state = .began }
    else if state == .began || state == .changed { state = .changed }
  }
  override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent) {
    guard let tracked, touches.contains(tracked) else { return }
    if state == .began || state == .changed {
      samples.append(tracked.location(in: view))
      state = .ended
    } else { state = .failed }
  }
  override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent) {
    state = state == .possible ? .failed : .cancelled
  }
  override func reset() {
    tracked = nil
    samples.removeAll(keepingCapacity: false)
    super.reset()
  }
}

enum Tool: String, CaseIterable {
  case grammar = "Fix Grammar"
  case tone = "Improve & Tone"
  case ask = "Ask AI"
  case translate = "Translation"
  case emoji = "Emoji"
  case clipboard = "Clipboard"
  case more = "Tools"
  case voice = "Voice input"
  case theme = "Theme"
  case language = "Typing language"
  case editing = "Text editing"
  case layout = "Keyboard layout"
  case settings = "Settings"
  var ai: Bool { [.grammar, .tone, .ask, .translate].contains(self) }
  private static let symbols: [Self: String] = [
    .theme: "paintpalette", .language: "globe", .editing: "cursorarrow.rays",
    .layout: "keyboard", .settings: "gearshape",
  ]
  private static let icons = Dictionary(uniqueKeysWithValues: allCases.map { tool in
    let symbol = symbols[tool].flatMap {
      UIImage(systemName: $0, withConfiguration: UIImage.SymbolConfiguration(pointSize: 19, weight: .medium))
    }
    return (tool, symbol ?? tool.drawIcon())
  })
  func icon() -> UIImage { Self.icons[self]! }
  private func drawIcon() -> UIImage {
    let image = UIGraphicsImageRenderer(size: CGSize(width: 24, height: 24)).image { _ in
      UIColor.black.setStroke()
      func line(_ points: [(CGFloat, CGFloat)], close: Bool = false) {
        let p = UIBezierPath()
        p.lineWidth = 1.65
        p.lineCapStyle = .round
        p.lineJoinStyle = .round
        for (i, pair) in points.enumerated() {
          let dot = CGPoint(x: pair.0, y: pair.1)
          if i == 0 { p.move(to: dot) } else { p.addLine(to: dot) }
        }
        if close { p.close() }
        p.stroke()
      }
      func round(_ rect: CGRect, _ r: CGFloat) {
        let p = UIBezierPath(roundedRect: rect, cornerRadius: r)
        p.lineWidth = 1.65
        p.stroke()
      }
      switch self {
      case .grammar:
        line([(3, 15), (7, 5), (11, 15)])
        line([(5, 11), (9, 11)])
        line([(12, 16), (15, 19), (21, 11)])
      case .tone:
        line([(3, 6), (12, 6)])
        line([(3, 12), (9, 12)])
        line([(3, 18), (7, 18)])
        line([(10, 19), (11, 15), (18, 6), (21, 8), (14, 17), (10, 19)])
      case .ask:
        line(
          [
            (4, 5), (17, 5), (19, 7), (19, 14), (16, 17), (10, 17), (6, 21), (6, 17), (3, 15),
            (3, 7),
          ], close: true)
        line([(7, 10), (15, 10)])
        line([(7, 13), (12, 13)])
      case .translate:
        line([(2, 6), (14, 6)])
        line([(8, 3), (8, 6)])
        line([(11, 7), (9, 11), (4, 15)])
        line([(5, 9), (10, 14)])
        line([(12, 21), (16, 11), (21, 21)])
        line([(14, 17), (19, 17)])
      case .emoji:
        round(CGRect(x: 3, y: 3, width: 18, height: 18), 9)
        line([(8, 9), (8.5, 9)])
        line([(15, 9), (15.5, 9)])
        line([(7.5, 14), (10, 16), (14, 16), (16.5, 14)])
      case .clipboard:
        round(CGRect(x: 5, y: 5, width: 14, height: 16), 3)
        round(CGRect(x: 9, y: 3, width: 6, height: 4), 1.5)
        line([(9, 11), (15, 11)])
        line([(9, 15), (13, 15)])
      case .more:
        for y in [4, 13] {
          for x in [4, 13] { round(CGRect(x: x, y: y, width: 7, height: 7), 1.7) }
        }
      case .voice:
        round(CGRect(x: 9, y: 3, width: 6, height: 12), 3)
        line([(5, 12), (6, 16), (9, 18), (15, 18), (18, 16), (19, 12)])
        line([(12, 18), (12, 21)])
        line([(9, 21), (15, 21)])
      default: break
      }
    }
    return image.withRenderingMode(.alwaysTemplate)
  }
}

enum KeyMaps {
  static let latin = ["qwertyuiop", "asdfghjkl", "zxcvbnm"].map { $0.map(String.init) }
  static let bangla: [[String]] = ["অআইঈউঊঋএঐওঔ", "কখগঘঙচছজঝঞ", "টঠডঢণতথদধন", "পফবভমযরলশষসহ"].map {
    $0.map { String($0) }
  }
  static let signs: [[String]] = [
    "ািীুূৃেৈোৌ".unicodeScalars.map(String.init),
    ["্", "ং", "ঃ", "ঁ", "ড়", "ঢ়", "য়", "ৎ"], "০১২৩৪৫৬৭৮৯".map(String.init),
  ]
  static let symbols = [
    "1234567890".map(String.init),
    ["-", "/", ":", ";", "(", ")", "£", "&", "@", "\""],
    [".", ",", "?", "!", "'"],
  ]
  static let secondary = [
    ["[", "]", "{", "}", "#", "%", "^", "*", "+", "="],
    ["_", "\\", "|", "~", "<", ">", "€", "$", "¥", "•"],
    symbols[2],
  ]
  static let emoji = [
    "🙂", "😄", "😂", "🥹", "😍", "🤔", "😅", "🙃", "🥲", "😎", "🤍", "❤️", "🙏", "👍", "✨", "🎉", "🌸", "🔥", "🙌",
    "💚", "😴", "🥳", "👏", "💬",
  ]
}
