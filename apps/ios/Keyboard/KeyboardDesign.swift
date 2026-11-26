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
  static let gap: CGFloat = 3, rowGap: CGFloat = 5, radius: CGFloat = 6, specialRadius: CGFloat = 13
  static let toolbar: CGFloat = 44, letter: CGFloat = 22, utility: CGFloat = 15
  static func height(compact: Bool, preference: String) -> CGFloat {
    let offset: CGFloat = ["Compact": -18, "Tall": 22][preference] ?? 0
    return (compact ? 206 : 262) + offset
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
  init(_ title: String, kind: Kind = .letter) {
    super.init(frame: .zero)
    face.isUserInteractionEnabled = false
    insertSubview(face, at: 0)
    face.backgroundColor =
      kind == .transparent
      ? .clear : kind == .accent ? Deck.brand : kind == .function ? Deck.function : Deck.key
    face.layer.cornerRadius = kind == .letter ? Deck.radius : Deck.specialRadius
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
  enum Kind { case letter, function, accent, transparent }
  required init?(coder: NSCoder) { fatalError() }
  override var isHighlighted: Bool { didSet { face.alpha = isHighlighted ? 0.55 : 1 } }
  override func layoutSubviews() {
    super.layoutSubviews()
    face.frame = bounds.insetBy(dx: Deck.gap / 2, dy: 0.5)
    sendSubviewToBack(face)
    hint.frame = CGRect(x: 1, y: 2, width: bounds.width - 7, height: 12)
  }
  @objc func held(_ recognizer: UILongPressGestureRecognizer) {
    if recognizer.state == .began { alternate?() }
  }
}

// Small single-weight, original toolbar glyphs constructed in the same 24-point grid.
enum Tool: String, CaseIterable {
  case grammar = "Fix Grammar"
  case tone = "Improve & Tone"
  case ask = "Ask AI"
  case translate = "Translation"
  case emoji = "Emoji"
  case clipboard = "Clipboard"
  case more = "More options"
  case voice = "Voice input"
  var ai: Bool { [.grammar, .tone, .ask, .translate].contains(self) }
  private static let icons = Dictionary(uniqueKeysWithValues: allCases.map { ($0, $0.drawIcon()) })
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
        for x in [4, 10, 16] { round(CGRect(x: x, y: 10, width: 3, height: 3), 1.5) }
      case .voice:
        round(CGRect(x: 9, y: 3, width: 6, height: 12), 3)
        line([(5, 12), (6, 16), (9, 18), (15, 18), (18, 16), (19, 12)])
        line([(12, 18), (12, 21)])
        line([(9, 21), (15, 21)])
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
  static let symbols = ["1234567890", "@#$_&-+()/", "!?\"':;/"].map { $0.map(String.init) }
  static let secondary = ["~`|•√π÷×¶∆", "£€¥¢^°={}", "\\©®™℅[]"].map { $0.map(String.init) }
  static let emoji = [
    "🙂", "😄", "😂", "🥹", "😍", "🤔", "😅", "🙃", "🥲", "😎", "🤍", "❤️", "🙏", "👍", "✨", "🎉", "🌸", "🔥", "🙌",
    "💚", "😴", "🥳", "👏", "💬",
  ]
  static let english =
    "about after again almost already always because before better between coming could early evening family friend friends good great happy hello helpful later lunch meeting message morning night please really reply right should sorry thank thanks their there these thing think today together tomorrow tonight would your you're"
    .split(separator: " ").map(String.init)
  static let banglish =
    "abar ache achho achi ajke amar amake amader ami apnar apni aschi bhalo bhalobasha bhai bon bondhu cholo dhonnobad ekhane hobe khabar kemon korbo korte kotha kothay onek porer shomoy tomar tomake tumi"
    .split(separator: " ").map(String.init)
}
