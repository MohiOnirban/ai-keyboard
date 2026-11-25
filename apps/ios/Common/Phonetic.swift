import Foundation
import JavaScriptCore

/// One offline context per keyboard. Only the bundled parser is evaluated.
final class Phonetic {
  let words: [String: String]
  private let engine: JSValue
  private let context: JSContext

  init?(dictionary: Data, library: String) {
    struct DictionaryFile: Decodable { let words: [String: String] }
    guard let entries = try? JSONDecoder().decode(DictionaryFile.self, from: dictionary),
      let context = JSContext()
    else { return nil }
    context.evaluateScript(library)
    guard let engine = context.evaluateScript("OmicronLab.Avro.Phonetic"),
      let parse = engine.objectForKeyedSubscript("parse"), !parse.isUndefined,
      context.exception == nil
    else { return nil }
    self.context = context
    self.engine = engine
    words = entries.words
  }

  func render(_ word: String) -> String {
    candidates(word).first ?? word
  }

  func candidates(_ word: String) -> [String] {
    let lower = word.lowercased()
    let parsed = parse(word)
    let preferred = words[lower]
    let values = word != lower && parsed != parse(lower)
      ? [parsed, preferred, word]
      : [preferred, parsed, word]
    var seen = Set<String>()
    return values.compactMap { $0 }.filter { seen.insert($0).inserted }
  }

  private func parse(_ word: String) -> String {
    return engine.invokeMethod("parse", withArguments: [word])?.toString() ?? word
  }

  static func bundled() -> Phonetic? {
    guard let url = Bundle.main.url(forResource: "phonetic", withExtension: "json"),
      let parser = Bundle.main.url(forResource: "avro-lib", withExtension: "js"),
      let data = try? Data(contentsOf: url),
      let js = try? String(contentsOf: parser, encoding: .utf8)
    else { return nil }
    return Phonetic(dictionary: data, library: js)
  }
}
