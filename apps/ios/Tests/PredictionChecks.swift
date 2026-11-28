import Foundation

@main struct Check {
  static func main() throws {
    let text = try String(contentsOfFile: "shared/predict.tsv", encoding: .utf8)
    let frequencies = try String(contentsOfFile: "shared/wordfreq.tsv", encoding: .utf8)
    let predictor = Predictor(text, frequencies: frequencies)
    func words(_ word: String, after: String = "", language: TypingLanguage = .english,
      local: [String] = [], history: [String: TypingHistory.Counts] = [:]
    ) -> [String] {
      predictor.suggestions(for: word, after: after, language: language, personal: local, history: history)
    }
    func correction(_ word: String, after: String = "", language: TypingLanguage = .english,
      local: [String] = [], history: [String: TypingHistory.Counts] = [:]
    ) -> String? {
      predictor.correction(for: word, after: after, language: language, personal: local, history: history)
    }

    let checks = [
      ("contraction first", words("im").first == "I'm"),
      ("original preserved", words("im").contains("im")),
      ("transposition", words("teh").first == "the"),
      ("missing letter", words("helo").contains("hello")),
      ("extra letter", words("mesage", after: "we can").contains("message")),
      ("adjacent substitution", words("trqin", after: "take the").contains("train")),
      ("missing space", words("liketo", after: "would you").contains("like to")),
      ("partial completion", words("becau", after: "I know").contains("because")),
      ("context", words("", after: "how are").first == "you"),
      ("multi-word context", words("", after: "am going").first == "to"),
      ("Banglish completion", words("am", language: .banglish).contains("amar")),
      ("Bangla completion", words("কেম", language: .bangla).first == "কেমন"),
      ("Banglish context", words("", after: "ami tomake", language: .banglish).first == "bhalobashi"),
      ("personal first", words("zan", local: ["Zanovia"]).first == "Zanovia"),
      ("personal language isolation", words("zan", language: .bangla, local: ["Zanovia"]).isEmpty),
      ("sentence context reset", Predictor.previous(in: "I can. ") == ""),
      ("Bangla context", Predictor.context(in: "আমি একটু ") == "আমি একটু"),
      ("high-confidence automatic edit", correction("teh") == "the"),
      ("automatic contraction at sentence start", correction("im") == "I'm"),
      ("intentional spelling", correction("teh", local: ["Teh"]) == nil),
      ("capitalized name", correction("Teh") == nil),
      ("valid ambiguous word", correction("cant", after: "the ancient") == nil),
      ("Banglish protection", correction("koros", language: .banglish) == nil),
      ("mixed typing protection", correction("koros") == nil),
      ("address protection", correction("hi@example.com") == nil),
    ]
    for (name, success) in checks { precondition(success, name) }

    var history = TypingHistory()
    history.record("zano", in: .english, as: .typed)
    history.record("zano", in: .english, as: .accepted)
    history.record("the", in: .english, as: .rejected)
    history.record("a1@b.com", in: .english, as: .typed)
    history.record("ভালো", in: .english, as: .typed)
    let local = history.words(in: .english)
    precondition(words("zan", history: local).contains("zano"))
    precondition(correction("teh", history: local) == nil)
    precondition(local.count == 2)
    let json = try JSONEncoder().encode(history)
    let restored = try JSONDecoder().decode(TypingHistory.self, from: json)
    precondition(restored == history)
    print("\(checks.count + 4) predictor and local history checks passed")
  }
}
