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
    var rejectedTargets: [String: Int]?
    var score: Int { min(6, typed) + min(6, accepted) * 2 - min(6, rejected) * 3 }
  }
  private(set) var languages: [String: [String: Counts]] = [:]
  private var pairs: [String: [String: Int]]?

  func following(_ previous: String, in language: TypingLanguage) -> [String: Int] {
    guard let key = Predictor.normalizedEntry(previous) else { return [:] }
    let prefix = Predictor.normalized(key) + "\t"
    return Dictionary(uniqueKeysWithValues: (pairs?[language.rawValue] ?? [:]).compactMap { entry in
      entry.key.hasPrefix(prefix) ? (String(entry.key.dropFirst(prefix.count)), entry.value) : nil
    })
  }

  func words(in language: TypingLanguage) -> [String: Counts] { languages[language.rawValue] ?? [:] }

  mutating func reject(_ original: String, suggestion: String, in language: TypingLanguage) {
    guard let entry = Predictor.normalizedEntry(original), Predictor.belongs(entry, to: language),
      suggestion.count <= 64 else { return }
    let parts = suggestion.split(separator: " ")
    guard (1...7).contains(parts.count), parts.allSatisfy({ Predictor.normalizedEntry(String($0)) != nil }) else { return }
    let key = Predictor.normalized(entry), target = Predictor.normalized(suggestion)
    var words = words(in: language)
    // Reuse the existing history cap even when an unseen word is first encountered via Undo.
    if words[key] == nil, words.count >= 256,
      let least = words.min(by: { ($0.value.score, $0.key) < ($1.value.score, $1.key) }) {
      words.removeValue(forKey: least.key)
    }
    var counts = words[key] ?? Counts()
    var targets = counts.rejectedTargets ?? [:]
    if targets[target] == nil, targets.count >= 8, let least = targets.min(by: { ($0.value, $0.key) < ($1.value, $1.key) }) {
      targets.removeValue(forKey: least.key)
    }
    targets[target] = min(32, targets[target, default: 0] + 1)
    counts.rejectedTargets = targets
    words[key] = counts
    languages[language.rawValue] = words
  }

  mutating func record(_ word: String, in language: TypingLanguage, as event: Event, after previous: String = "") {
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
    if event == .rejected { return }
    if let predecessor = Predictor.normalizedEntry(previous), Predictor.belongs(predecessor, to: language) {
      let key = Predictor.normalized(predecessor) + "\t" + word
      var counts = pairs?[language.rawValue] ?? [:]
      if counts[key] == nil, counts.count >= 256, let least = counts.min(by: { ($0.value, $0.key) < ($1.value, $1.key) }) {
        counts.removeValue(forKey: least.key)
      }
      counts[key] = min(32, counts[key, default: 0] + (event == .accepted ? 2 : 1))
      if pairs == nil { pairs = [:] }
      pairs?[language.rawValue] = counts
    }
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
    var glide: [String: Int] = [:]
    var preceding: [String: [String: Int]] = [:]
    var suffixNext: [String: [String]] = [:]

    mutating func index() {
      sorted = words.keys.sorted()
      glide = words.filter { (2...24).contains($0.key.utf8.count) && $0.key.utf8.allSatisfy { (97...122).contains($0) } }
      for (left, successors) in following where !left.isEmpty && !left.contains(" ") {
        for (right, count) in successors { preceding[right, default: [:]][left] = count }
      }
      var suffixes: [String: [String: (stems: Int, uses: Int)]] = [:]
      for (left, successors) in following where !left.contains(" ") {
        for length in [2, 3] where left.unicodeScalars.count > length {
          let suffix = String(left.unicodeScalars.suffix(length))
          for (word, count) in successors {
            let old = suffixes[suffix]?[word] ?? (0, 0)
            suffixes[suffix, default: [:]][word] = (old.0 + 1, old.1 + count)
          }
        }
      }
      suffixNext = suffixes.mapValues { counts in
        Array(counts.filter { $0.value.stems >= 2 }.sorted {
          (-$0.value.stems, -$0.value.uses, $0.key) < (-$1.value.stems, -$1.value.uses, $1.key)
        }.prefix(3).map(\.key))
      }
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

    func completions(_ prefix: String, exhaustive: Bool = false) -> [String] {
      if !exhaustive && prefix.unicodeScalars.count <= 3 { return shortPrefixes[prefix] ?? [] }
      var low = 0, high = sorted.count
      while low < high {
        let middle = (low + high) / 2
        if sorted[middle] < prefix { low = middle + 1 } else { high = middle }
      }
      return Array(sorted[low...].prefix { $0.unicodeScalars.starts(with: prefix.unicodeScalars) })
    }
  }

  private enum Edit: Int {
    case doubleEdit, split, substitute, insertOrDelete, adjacent, repeated, transpose, apostrophe

    var bonus: Double {
      switch self {
      case .doubleEdit: return 2
      case .split: return 16
      case .substitute: return -2
      case .insertOrDelete: return 8
      case .adjacent: return 12
      case .repeated: return 17
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

  func glideVocabulary(language: TypingLanguage) -> [String: Int] { languages[language]?.glide ?? [:] }

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

    let right: [String: Int]
    let hints: [Int: Character]
    let allowSegmentation: Bool
    let hasPrevious: Bool
    let suffixNext: [String]

    init(_ previous: String, _ lexicon: Lexicon, following: String = "",
      hints: [Int: Character] = [:], allowSegmentation: Bool = true) {
      right = lexicon.preceding[Predictor.normalized(following)] ?? [:]
      self.hints = hints
      self.allowSegmentation = allowSegmentation
      let normalized = Predictor.normalized(previous)
      let words = normalized.split(separator: " ").suffix(2)
      let previousWord = String(words.last ?? "")
      hasPrevious = !previousWord.isEmpty
      suffixNext = [3, 2].filter { previousWord.unicodeScalars.count > $0 }.flatMap {
        lexicon.suffixNext[String(previousWord.unicodeScalars.suffix($0))] ?? []
      }
      one = lexicon.following[String(words.last ?? "")] ?? [:]
      two = words.count == 2 ? lexicon.following[words.joined(separator: " ")] ?? [:] : [:]
    }

    func bonus(_ word: String) -> Double {
      let bigram = one[word, default: 0]
      let trigram = two[word, default: 0]
      if !hasPrevious {
        return 3 * log2(Double(bigram) + 1) + 7 * log2(Double(right[word, default: 0]) + 1)
      }
      return 7 * log2(Double(right[word, default: 0]) + 1) + (bigram == 0 ? 0 : 15 + 5 * log2(Double(bigram) + 1))
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
    limit: Int = 3, history: [String: TypingHistory.Counts] = [:],
    following: String = "", touchHints: [Int: Character] = [:], allowSegmentation: Bool = true,
    learnedNext: [String: Int] = [:]
  ) -> [String] {
    guard limit > 0 else { return [] }
    let query = Self.normalized(prefix)
    let lexicon = languages[language] ?? empty
    let context = Context(context, lexicon, following: following, hints: touchHints, allowSegmentation: allowSegmentation)
    let personal = personal.filter { Self.belongs($0, to: language) }
    var candidates: [String: (word: String, rank: Double)] = [:]
    func add(_ word: String, bonus: Double = 0) {
      let key = Self.normalized(word)
      guard key != query, key.unicodeScalars.starts(with: query.unicodeScalars) || bonus != 0
      else { return }
      let penalty = min(18, (history[query]?.rejectedTargets?[key] ?? 0) * 8)
      let rank = score(key, using: lexicon, in: context, history: history) + bonus - Double(penalty)
      if rank > candidates[key]?.rank ?? -.infinity { candidates[key] = (word, rank) }
    }
    if query.isEmpty {
      for (word, count) in learnedNext where count > 0 { add(word, bonus: count >= 2 ? 100 : 20) }
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
    if query.isEmpty && context.hasPrevious {
      let exact = ranked.map(\.word).filter { context.one[$0] != nil || context.two[$0] != nil || learnedNext[$0] != nil }
      var seen = Set<String>()
      return (exact + context.suffixNext + ranked.map(\.word)).filter { seen.insert($0).inserted }
        .prefix(limit).map { Self.matchCase($0, to: prefix, language: language) }
    }
    var result = ranked.prefix(limit).map { Self.matchCase($0.word, to: prefix, language: language) }
    // Retain the typed form when the strip is proposing changes to it.
    if result.isEmpty && Self.isWordKey(prefix) && Self.belongs(prefix, to: language) { return [prefix] }
    if !query.isEmpty, !result.isEmpty, limit >= 2 {
      if result.count == limit { result.removeLast() }
      result.insert(prefix, at: 1)
    }
    return result
  }

  func correction(
    for prefix: String, after context: String, language: TypingLanguage, personal: [String],
    history: [String: TypingHistory.Counts] = [:], allowInitialCapital: Bool = false,
    following: String = "", touchHints: [Int: Character] = [:], allowSegmentation: Bool = true
  ) -> String? {
    guard language == .english,
      prefix == prefix.lowercased() || allowInitialCapital,
      !personal.contains(where: { Self.normalized($0) == Self.normalized(prefix) })
    else { return nil }
    let query = Self.normalized(prefix)
    let lexicon = languages[.english] ?? empty
    let banglish = languages[.banglish] ?? empty
    guard banglish.words[query] == nil, (history[query]?.typed ?? 0) < 2 else { return nil }
    if prefix == "i" {
      return (history[query]?.rejected ?? 0) == 0 && history[query]?.rejectedTargets?["i"] == nil ? "I" : nil
    }
    guard prefix.dropFirst().allSatisfy({ !$0.isUppercase }) else { return nil }
    let context = Context(context, lexicon, following: following, hints: touchHints, allowSegmentation: allowSegmentation)
    let ranked = spellings(query, language: .english, lexicon: lexicon, context: context, history: history)
    guard let best = ranked.first, (history[best.word]?.rejected ?? 0) == 0,
      history[query]?.rejectedTargets?[Self.normalized(best.word)] == nil,
      best.score - (ranked.dropFirst().first?.score ?? 0) >= 3
    else { return nil }
    let contextCount = context.one[best.word, default: 0] + context.two[best.word, default: 0] * 2
    let adjacentContext = context.hasPrevious ? contextCount : 0
    if best.edit != .split && adjacentContext == 0 && ranked.contains(where: { $0.edit == .split }) { return nil }
    let corpusCount = lexicon.occurrences[best.word, default: 0]
    let count = lexicon.words[best.word, default: 0]
    let frequent = count >= 300 || corpusCount >= 3
    let margin = best.score - (ranked.dropFirst().first?.score ?? 0)
    if best.edit == .apostrophe && (contextCount > 0 || lexicon.words[query] == nil && frequent)
      || best.edit == .transpose && (frequent || contextCount >= 2)
      || best.edit == .repeated && query.count >= 4 && frequent && margin >= 5
      || [.insertOrDelete, .adjacent, .substitute].contains(best.edit) &&
        (adjacentContext >= 2 || context.right[best.word, default: 0] > 0 || query.count >= 6 && count >= 1_000 && margin >= 6)
      || best.edit == .doubleEdit && query.count >= 7 && count >= 5_000 && margin >= 3
      || best.edit == .split && best.score >= 31 && (best.word.split(separator: " ").count >= 3 ||
        !ranked.contains { $0.edit != .split && lexicon.words[$0.word, default: 0] >= 2_000 }) {
      return Self.matchCase(best.word, to: prefix, language: language)
    }
    return nil
  }

  private func spellings(_ query: String, language: TypingLanguage, lexicon: Lexicon,
    context: Context, history: [String: TypingHistory.Counts]
  ) -> [Spelling] {
    if language != .english {
      let scalars = query.unicodeScalars.map(\.value)
      guard (3...24).contains(scalars.count), lexicon.words[query] == nil,
        Self.belongs(query, to: language), let first = query.unicodeScalars.first else { return [] }
      return lexicon.completions(String(first), exhaustive: true).compactMap { word -> Spelling? in
        let other = word.unicodeScalars.map(\.value)
        guard Self.distance(scalars, other, atMost: 1) == 1 else { return nil }
        return Spelling(word: word, edit: .insertOrDelete, score: score(word, using: lexicon, in: context, history: history))
      }.sorted { (-$0.score, $0.word) < (-$1.score, $1.word) }
    }
    guard language == .english, (2...24).contains(query.utf8.count),
      query.utf8.allSatisfy({ (97...122).contains($0) || $0 == 39 })
    else { return [] }
    let exact = lexicon.words[query, default: 0]
    var edits: [String: Edit] = [:]
    var extra: [String: Double] = [:]
    let segment = context.allowSegmentation && exact < 2_000 && lexicon.occurrences[query] == nil &&
      languages[.banglish]?.words[query] == nil ? segmentation(query, lexicon: lexicon, context: context) : nil
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
        let repeated = index > 0 && bytes[index-1] == bytes[index] ||
          index + 1 < bytes.count && bytes[index+1] == bytes[index]
        lookup(variant, repeated ? .repeated : .insertOrDelete)
        if index + 1 < bytes.count && bytes[index] != bytes[index + 1] {
          variant = bytes
          variant.swapAt(index, index + 1)
          lookup(variant, .transpose)
        }
        variant = bytes
        for letter in Self.alphabet where letter != bytes[index] {
          variant[index] = letter
          let near = Self.adjacent[bytes[index]]?.contains(letter) == true
          lookup(variant, near ? .adjacent : .substitute)
          if near, context.hints[index]?.asciiValue == letter { extra[String(decoding: variant, as: UTF8.self)] = 12 }
        }
      }
      for index in 0...bytes.count {
        var variant = bytes
        variant.insert(0, at: index)
        for letter in Self.alphabet {
          variant[index] = letter
          let repeated = index > 0 && bytes[index-1] == letter || index < bytes.count && bytes[index] == letter
          lookup(variant, repeated ? .repeated : .insertOrDelete)
          if index > 0, index < bytes.count - 1, [97,101,105,111,117].contains(letter) {
            let word = String(decoding: variant, as: UTF8.self)
            if edits[word] != nil { extra[word] = max(extra[word, default: 0], 4) }
          }
        }
      }
      if bytes.count >= 7 && edits.isEmpty {
        for word in lexicon.completions(String(query.prefix(3)), exhaustive: true) {
          if Self.distance(bytes, Array(word.utf8), atMost: 2) <= 2 { offer(word, .doubleEdit) }
        }
      }
    }
    var result = edits.map { word, edit in
      Spelling(word: word, edit: edit,
        score: score(word, using: lexicon, in: context, history: history) + edit.bonus + extra[word, default: 0])
    }
    if let segment { result.append(segment) }
    return result.sorted { (-$0.score, $0.word) < (-$1.score, $1.word) }
  }

  private static func distance<T: Equatable>(_ a: [T], _ b: [T], atMost limit: Int) -> Int {
    guard abs(a.count - b.count) <= limit else { return limit + 1 }
    var previous = Array(0...b.count), row = previous
    for i in a.indices {
      row[0] = i + 1
      var minimum = row[0]
      for j in b.indices {
        row[j+1] = min(previous[j+1] + 1, row[j] + 1, previous[j] + (a[i] == b[j] ? 0 : 1))
        minimum = min(minimum, row[j+1])
      }
      if minimum > limit { return limit + 1 }
      swap(&previous, &row)
    }
    return previous[b.count]
  }

  private struct SegmentPath {
    let phrase: String, last: String
    let score: Double
    let parts: Int, observed: Int, minimum: Int
  }

  private func segmentation(_ query: String, lexicon: Lexicon, context: Context) -> Spelling? {
    let bytes = Array(query.utf8)
    guard (5...24).contains(bytes.count) else { return nil }
    var paths = Array(repeating: [SegmentPath](), count: bytes.count + 1)
    paths[0] = [SegmentPath(phrase: "", last: "", score: 0, parts: 0, observed: 0, minimum: Int.max)]
    for end in 1...bytes.count {
      for start in max(0, end - 13)..<end where !(start == 0 && end == bytes.count) && !paths[start].isEmpty {
        let word = String(decoding: bytes[start..<end], as: UTF8.self)
        guard word.count > 1 || word == "i" || word == "a", let count = lexicon.words[word],
          count >= 500 || lexicon.occurrences[word] != nil else { continue }
        for previous in paths[start] where previous.parts < 7 {
          let attested = previous.parts > 0 && lexicon.following[previous.last]?[word] != nil
          let score = previous.score + log2(Double(count) + 1) - 27 + (attested ? 3.5 : 0)
            + (previous.parts == 0 ? min(3, context.bonus(word)) : 0)
          if paths[end].contains(where: { $0.last == word && $0.score >= score }) { continue }
          let path = SegmentPath(phrase: start == 0 ? word : previous.phrase + " " + word,
            last: word, score: score, parts: previous.parts + 1, observed: previous.observed + (attested ? 1 : 0),
            minimum: min(count, previous.minimum))
          paths[end].removeAll { $0.last == word }
          paths[end].append(path)
          paths[end].sort { $0.score > $1.score }
          if paths[end].count > 2 { paths[end].removeLast() }
        }
      }
    }
    guard let best = paths[bytes.count].first else { return nil }
    let literal = lexicon.words[query, default: 0]
    guard literal == 0 || best.observed == best.parts - 1 && best.minimum >= literal * 150 else { return nil }
    let credible = best.observed == best.parts - 1 ||
      best.minimum >= (best.observed > 0 || best.parts >= 3 ? 100_000 : 250_000) && (best.observed > 0 || best.parts <= 3)
    guard credible || best.minimum >= 1_500 && (best.observed > 0 || best.parts == 2) else { return nil }
    let second = paths[bytes.count].dropFirst().first
    let confident = credible && best.score - (second?.score ?? -.infinity) >= 1.5
    return Spelling(word: best.phrase, edit: .split, score: (confident ? 46 : 23) + best.score / Double(best.parts * 5))
  }

  private static func matchCase(_ word: String, to prefix: String, language: TypingLanguage) -> String {
    let word = language == .english ? word.split(separator: " ").map { token in
      token == "i" || token.hasPrefix("i'") ? "I" + token.dropFirst() : String(token)
    }.joined(separator: " ") : word
    if prefix.count > 1, prefix == prefix.uppercased() { return word.uppercased() }
    if prefix.first?.isUppercase == true { return word.prefix(1).uppercased() + word.dropFirst() }
    return word
  }
}
