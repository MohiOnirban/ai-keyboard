import Foundation

@main struct PhoneticChecks {
  static func main() throws {
    let data = try Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[1]))
    let library = try String(contentsOfFile: "shared/avro/avro-lib.js", encoding: .utf8)
    let rules = Phonetic(dictionary: data, library: library)!
    let fixtures = [
      "ami": "আমি", "Bangladesh": "বাংলাদেশ", "krri": "কৃ", "kkhe": "ক্ষে", "ou": "ঔ", "chaa": "চা",
      "strii": "স্ত্রী", "bhaat": "ভাত", "123": "১২৩", "spondon": "স্পন্দন", "shobdo": "শব্দ", "groho": "গ্রহ",
      "shilpo": "শিল্প", "montro": "মন্ত্র", "kSh": "ক্ষ", "bry": "ব্র্য", "Ng": "ঙ", "ShT": "ষ্ট",
      "T": "ট", "t": "ত", "D": "ড", "d": "দ", "R": "ড়", "r": "র", "Tomake": "টমাকে", "": "",
    ]
    for (input, expected) in fixtures { precondition(rules.render(input) == expected, input) }
    for (input, expected) in rules.words { precondition(rules.render(input) == expected) }
    precondition(rules.candidates("Tomake") == ["টমাকে", "তোমাকে", "Tomake"])
    precondition(rules.candidates("tomake") == ["তোমাকে", "তমাকে", "tomake"])
    precondition(
      ["Ami", "tomake", "bhalobashi"].map(rules.render).joined(separator: " ") == "আমি তোমাকে ভালোবাসি")
    var renders = 0
    let start = DispatchTime.now().uptimeNanoseconds
    for _ in 0..<50 {
      for sample in ["spondon", "shilpo", "krri", "montro"] {
        for n in 1...sample.count {
          renders += 1
          _ = rules.render(String(sample.prefix(n)))
        }
      }
    }
    let duration = Double(DispatchTime.now().uptimeNanoseconds - start) / 1_000_000
    print("Host incremental benchmark: \(renders) renders, \(duration) ms total")
    print("Validated \(fixtures.count) phonetic cases and \(rules.words.count) dictionary entries")
  }
}
