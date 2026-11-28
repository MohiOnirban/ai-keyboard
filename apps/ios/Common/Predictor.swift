import Foundation

enum TypingLanguage: String {
  case english = "en", bangla = "bn", banglish
}

struct TypingHistory: Codable, Equatable {
  enum Event { case typed, accepted, rejected }
  struct Counts: Codable, Equatable {
    var typed = 0
    var accepted = 0
    var rejected = 0
    var score: Int { min(6, typed) + min(6, accepted) * 2 - min(6, rejected) * 3 }
  }
  private(set) var languages: [String: [String: Counts]] = [:]

  func words(in language: TypingLanguage) -> [String: Counts] { languages[language.rawValue] ?? [:] }

  mutating func record(_ word: String, in language: TypingLanguage, as event: Event) {
    guard let entry = Predictor.normalizedEntry(word), Predictor.belongs(entry, to: language)
    else { return }
    let word = Predictor.normalized(entry)
    var words = words(in: language)
    if words[word] == nil, words.count >= 256 {
      let least = words.min { ($0.value.score, $0.key) < ($1.value.score, $1.key) }
      if let least { words.removeValue(forKey: least.key) }
    }
    var counts = words[word] ?? Counts()
    switch event {
    case .typed: counts.typed = min(32, counts.typed + 1)
    case .accepted: counts.accepted = min(32, counts.accepted + 1)
    case .rejected: counts.rejected = min(32, counts.rejected + 1)
    }
    words[word] = counts
    languages[language.rawValue] = words
  }
}

struct Predictor {
  private struct Lexicon {
    var words: [String: Int] = [:]
    var occurrences: [String: Int] = [:]
    var following: [String: [String: Int]] = [:]
    var apostrophes: [String: [String]] = [:]
    var shortPrefixes: [String: [String]] = [:]
    var starts: [String] = []
    var sorted: [String] = []

    mutating func index() {
      sorted = words.keys.sorted()
      let ranked = words.keys.sorted { (-words[$0, default: 0], $0) < (-words[$1, default: 0], $1) }
      starts = Array(ranked.prefix(12))
      for word in ranked {
        if word.contains("'") {
          apostrophes[word.replacingOccurrences(of: "'", with: ""), default: []].append(word)
        }
        for length in 1...min(3, word.unicodeScalars.count) {
          let prefix = String(word.unicodeScalars.prefix(length))
          if shortPrefixes[prefix, default: []].count < 16 {
            shortPrefixes[prefix, default: []].append(word)
          }
        }
      }
    }

    func completions(_ prefix: String) -> [String] {
      if prefix.unicodeScalars.count <= 3 { return shortPrefixes[prefix] ?? [] }
      var low = 0, high = sorted.count
      while low < high {
        let middle = (low + high) / 2
        if sorted[middle] < prefix { low = middle + 1 } else { high = middle }
      }
      return Array(sorted[low...].prefix { $0.unicodeScalars.starts(with: prefix.unicodeScalars) })
    }
  }

  private enum Edit: Int {
    case split, substitute, insertOrDelete, adjacent, transpose, apostrophe

    var bonus: Double {
      switch self {
      case .split: return 16
      case .substitute: return -2
      case .insertOrDelete: return 8
      case .adjacent: return 12
      case .transpose: return 21
      case .apostrophe: return 31
      }
    }
  }

  private struct Spelling {
    let word: String
    let edit: Edit
    let score: Double
  }

  static let letters = CharacterSet.letters.union(.nonBaseCharacters)
  private static let alphabet = Array("abcdefghijklmnopqrstuvwxyz".utf8)
  private static let adjacent: [UInt8: Set<UInt8>] = {
    let rows = ["qwertyuiop", "asdfghjkl", "zxcvbnm"].map { Array($0.utf8) }
    let offsets = [0.0, 0.5, 1.5]
    var keys: [(UInt8, Double, Int)] = []
    for (y, row) in rows.enumerated() {
      keys += row.enumerated().map { ($0.element, Double($0.offset) + offsets[y], y) }
    }
    return Dictionary(uniqueKeysWithValues: keys.map { letter, x, y in
      (letter, Set(keys.filter { other, ox, oy in
        other != letter && abs(oy - y) <= 1 && abs(ox - x) <= 1.05
      }.map(\.0)))
    })
  }()
  private var languages: [TypingLanguage: Lexicon] = [:]
  private let empty = Lexicon()

  init(_ corpus: String, frequencies: String = "") {
    for line in frequencies.split(whereSeparator: \.isNewline) {
      let fields = line.split(separator: "\t", maxSplits: 2)
      guard fields.count == 3, let language = TypingLanguage(rawValue: String(fields[0])),
        let word = Self.normalizedEntry(String(fields[1])), Self.belongs(word, to: language),
        let count = Int(fields[2]), (1...1_000_000_000).contains(count)
      else { continue }
      let key = Self.normalized(word)
      languages[language, default: Lexicon()].words[key, default: 0] += count
    }
    for line in corpus.split(whereSeparator: \.isNewline) {
      let fields = line.split(separator: "\t", maxSplits: 1)
      guard fields.count == 2, let language = TypingLanguage(rawValue: String(fields[0]))
      else { continue }
      for clause in Self.clauses(Self.normalized(String(fields[1]))) {
        var previous = ""
        var prior = ""
        for word in Self.tokens(clause) {
          languages[language, default: Lexicon()].words[word, default: 0] += 4
          languages[language, default: Lexicon()].occurrences[word, default: 0] += 1
          languages[language, default: Lexicon()].following[previous, default: [:]][word, default: 0] += 1
          if !prior.isEmpty {
            languages[language, default: Lexicon()]
              .following[prior + " " + previous, default: [:]][word, default: 0] += 1
          }
          prior = previous
          previous = word
        }
      }
    }
    for language in Array(languages.keys) { languages[language]?.index() }
  }

  static func bundled() -> Predictor {
    func asset(_ name: String) -> String {
      guard let path = Bundle.main.url(forResource: name, withExtension: "tsv")
      else { return "" }
      return (try? String(contentsOf: path, encoding: .utf8)) ?? ""
    }
    return Predictor(asset("predict"), frequencies: asset("wordfreq"))
  }

  static func isWordKey(_ s: String) -> Bool {
    !s.isEmpty && s.unicodeScalars.allSatisfy { letters.contains($0) || $0 == "'" || $0 == "’" }
  }

  static func normalized(_ s: String) -> String {
    s.lowercased().replacingOccurrences(of: "’", with: "'").precomposedStringWithCanonicalMapping
  }

  static func normalizedEntry(_ s: String) -> String? {
    let word = s.trimmingCharacters(in: .whitespacesAndNewlines)
    guard word.unicodeScalars.count <= 40, isWordKey(word),
      let first = word.unicodeScalars.first, CharacterSet.letters.contains(first)
    else { return nil }
    return word.precomposedStringWithCanonicalMapping
  }

  static func belongs(_ word: String, to language: TypingLanguage) -> Bool {
    let hasBangla = word.unicodeScalars.contains { (0x0980...0x09FF).contains($0.value) }
    return hasBangla == (language == .bangla)
  }

  static func context(in text: String) -> String {
    Self.tokens(Self.clauses(Self.normalized(String(text.suffix(160)))).last ?? "")
      .suffix(2).joined(separator: " ")
  }

  static func previous(in text: String) -> String {
    String(context(in: text).split(separator: " ").last ?? "")
  }

  private static func clauses(_ text: String) -> [Substring] {
    text.split(omittingEmptySubsequences: false) { ".!?।\n\r".contains($0) }
  }

  private static func tokens(_ text: Substring) -> [String] {
    text.split(whereSeparator: { !isWordKey(String($0)) })
      .map { $0.trimmingCharacters(in: CharacterSet(charactersIn: "'")) }
      .filter { !$0.isEmpty }
  }

  private struct Context {
    let one: [String: Int]
    let two: [String: Int]

    init(_ previous: String, _ lexicon: Lexicon) {
      let normalized = Predictor.normalized(previous)
      let words = normalized.split(separator: " ").suffix(2)
      one = lexicon.following[String(words.last ?? "")] ?? [:]
      two = words.count == 2 ? lexicon.following[words.joined(separator: " ")] ?? [:] : [:]
    }

    func bonus(_ word: String) -> Double {
      let bigram = one[word, default: 0]
      let trigram = two[word, default: 0]
      return (bigram == 0 ? 0 : 15 + 5 * log2(Double(bigram) + 1))
        + (trigram == 0 ? 0 : 18 + 5 * log2(Double(trigram) + 1))
    }
  }

  private func score(_ word: String, using lexicon: Lexicon, in context: Context,
    history: [String: TypingHistory.Counts]
  ) -> Double {
    log2(Double(lexicon.words[word, default: 0]) + 1) * 1.45 + context.bonus(word)
      + Double(history[word]?.score ?? 0)
  }

  func suggestions(
    for prefix: String, after context: String, language: TypingLanguage, personal: [String],
    limit: Int = 3, history: [String: TypingHistory.Counts] = [:]
  ) -> [String] {
    guard limit > 0 else { return [] }
    let query = Self.normalized(prefix)
    let lexicon = languages[language] ?? empty
    let context = Context(context, lexicon)
    let personal = personal.filter { Self.belongs($0, to: language) }
    var candidates: [String: (word: String, rank: Double)] = [:]
    func add(_ word: String, bonus: Double = 0) {
      let key = Self.normalized(word)
      guard key != query, key.unicodeScalars.starts(with: query.unicodeScalars) || bonus != 0
      else { return }
      let rank = score(key, using: lexicon, in: context, history: history) + bonus
      if rank > candidates[key]?.rank ?? -.infinity { candidates[key] = (word, rank) }
    }
    for word in context.two.keys { add(word) }
    for word in context.one.keys { add(word) }
    for word in query.isEmpty ? lexicon.starts : lexicon.completions(query) { add(word) }
    if !query.isEmpty {
      for word in personal where Self.normalized(word).unicodeScalars.starts(with: query.unicodeScalars) {
        add(word, bonus: 96)
      }
      for (word, counts) in history where counts.score > 0
        && word.unicodeScalars.starts(with: query.unicodeScalars) {
        let learnedBoost: Double = counts.accepted > 0 || counts.typed > 1 ? 62 : 18
        add(word, bonus: learnedBoost)
      }
      if !personal.contains(where: { Self.normalized($0) == query }) {
        for candidate in spellings(query, language: language, lexicon: lexicon, context: context, history: history) {
          add(candidate.word, bonus: candidate.score - score(candidate.word, using: lexicon, in: context, history: history))
        }
      }
    }
    let ranked = candidates.values.sorted { (-$0.rank, $0.word.count, $0.word) < (-$1.rank, $1.word.count, $1.word) }
    var result = ranked.prefix(limit).map { Self.matchCase($0.word, to: prefix, language: language) }
    // Retain the typed form when the strip is proposing changes to it.
    if !query.isEmpty, !result.isEmpty, limit >= 2 {
      if result.count == limit { result.removeLast() }
      result.insert(prefix, at: 1)
    }
    return result
  }

  func correction(
    for prefix: String, after context: String, language: TypingLanguage, personal: [String],
    history: [String: TypingHistory.Counts] = [:], allowInitialCapital: Bool = false
  ) -> String? {
    guard language == .english,
      prefix == prefix.lowercased() || allowInitialCapital,
      !personal.contains(where: { Self.normalized($0) == Self.normalized(prefix) })
    else { return nil }
    let query = Self.normalized(prefix)
    let lexicon = languages[.english] ?? empty
    let banglish = languages[.banglish] ?? empty
    guard banglish.words[query] == nil, (history[query]?.typed ?? 0) < 2 else { return nil }
    let context = Context(context, lexicon)
    let ranked = spellings(query, language: .english, lexicon: lexicon, context: context, history: history)
    guard let best = ranked.first, (history[best.word]?.rejected ?? 0) == 0,
      best.score - (ranked.dropFirst().first?.score ?? 0) >= 5
    else { return nil }
    let contextCount = context.one[best.word, default: 0] + context.two[best.word, default: 0] * 2
    let corpusCount = lexicon.occurrences[best.word, default: 0]
    let count = lexicon.words[best.word, default: 0]
    let frequent = count >= 300 || corpusCount >= 3
    if best.edit == .apostrophe && (contextCount > 0 || lexicon.words[query] == nil && frequent)
      || best.edit == .transpose && (frequent || contextCount >= 2)
      || (best.edit == .insertOrDelete || best.edit == .adjacent) && contextCount >= 2
      || best.edit == .split && contextCount >= 4 {
      return Self.matchCase(best.word, to: prefix, language: language)
    }
    return nil
  }

  private func spellings(_ query: String, language: TypingLanguage, lexicon: Lexicon,
    context: Context, history: [String: TypingHistory.Counts]
  ) -> [Spelling] {
    guard language == .english, (2...24).contains(query.utf8.count),
      query.utf8.allSatisfy({ (97...122).contains($0) || $0 == 39 })
    else { return [] }
    let exact = lexicon.words[query, default: 0]
    var edits: [String: Edit] = [:]
    func offer(_ word: String, _ edit: Edit) {
      guard word != query, edit.rawValue > (edits[word]?.rawValue ?? -1) else { return }
      edits[word] = edit
    }
    if !query.contains("'") {
      for word in lexicon.apostrophes[query] ?? [] where exact == 0
        || (lexicon.occurrences[query] == nil && exact < 12_000 && lexicon.occurrences[word] != nil) {
        offer(word, .apostrophe)
      }
    }
    // Valid dictionary words and very short tokens are left intact.
    if exact == 0 && query.utf8.count >= 3 {
      let bytes = Array(query.utf8)
      func lookup(_ bytes: [UInt8], _ edit: Edit) {
        let word = String(decoding: bytes, as: UTF8.self)
        if lexicon.words[word] != nil { offer(word, edit) }
      }
      for index in bytes.indices {
        var variant = bytes
        variant.remove(at: index)
        lookup(variant, .insertOrDelete)
        if index + 1 < bytes.count && bytes[index] != bytes[index + 1] {
          variant = bytes
          variant.swapAt(index, index + 1)
          lookup(variant, .transpose)
        }
        variant = bytes
        for letter in Self.alphabet where letter != bytes[index] {
          variant[index] = letter
          lookup(variant, Self.adjacent[bytes[index]]?.contains(letter) == true ? .adjacent : .substitute)
        }
      }
      for index in 0...bytes.count {
        var variant = bytes
        variant.insert(0, at: index)
        for letter in Self.alphabet {
          variant[index] = letter
          lookup(variant, .insertOrDelete)
        }
      }
      if bytes.count >= 5 {
        for index in 2...(bytes.count - 2) {
          let left = String(decoding: bytes[..<index], as: UTF8.self)
          let right = String(decoding: bytes[index...], as: UTF8.self)
          guard lexicon.words[left] != nil, lexicon.words[right] != nil else { continue }
          let internalCount = lexicon.following[left]?[right] ?? 0
          if internalCount > 0 { offer(left + " " + right, .split) }
        }
      }
    }
    return edits.map { word, edit in
      let score: Double
      if edit == .split, let split = word.firstIndex(of: " ") {
        let left = String(word[..<split]), right = String(word[word.index(after: split)...])
        score = self.score(left, using: lexicon, in: context, history: history) * 0.55
          + self.score(right, using: lexicon, in: Context(left, lexicon), history: history) * 0.55
      } else {
        score = self.score(word, using: lexicon, in: context, history: history)
      }
      return Spelling(word: word, edit: edit, score: score + edit.bonus)
    }.sorted { (-$0.score, $0.word) < (-$1.score, $1.word) }
  }

  private static func matchCase(_ word: String, to prefix: String, language: TypingLanguage) -> String {
    if language == .english && (word == "i" || word.hasPrefix("i'")) { return "I" + word.dropFirst() }
    if prefix.count > 1, prefix == prefix.uppercased() { return word.uppercased() }
    if prefix.first?.isUppercase == true { return word.prefix(1).uppercased() + word.dropFirst() }
    return word
  }
}
