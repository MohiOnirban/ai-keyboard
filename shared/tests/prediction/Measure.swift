import Foundation

@main
struct PredictionMeasurement {
  private struct Query {
    let kind: String
    let language: TypingLanguage
    let context: String
    let input: String
    let expected: String

    var dictionary: [String] { kind == "personal" ? [expected] : [] }
  }

  static func main() throws {
    let args = CommandLine.arguments
    let corpus = try String(contentsOfFile: args[1], encoding: .utf8)
    let frequencies = args.count > 3 ? try String(contentsOfFile: args[3], encoding: .utf8) : ""
    let start = DispatchTime.now().uptimeNanoseconds
    let model = Predictor(corpus, frequencies: frequencies)
    let cold = DispatchTime.now().uptimeNanoseconds - start
    let data = try String(contentsOfFile: args[2], encoding: .utf8)
    let queries = data.split(whereSeparator: \.isNewline).dropFirst().map { line -> Query in
      let row = line.split(separator: "\t", omittingEmptySubsequences: false).map(String.init)
      return Query(kind: row[0], language: TypingLanguage(rawValue: row[1])!,
        context: Predictor.context(in: row[2] + " "), input: row[3], expected: row[4])
    }

    func correction(_ q: Query) -> String? {
      model.correction(for: q.input, after: q.context, language: q.language, personal: q.dictionary)
    }
    func evaluate(_ q: Query) -> [String] {
      if q.kind == "keep" { return [correction(q) ?? q.input] }
      return model.suggestions(for: q.input, after: q.context, language: q.language, personal: q.dictionary)
    }
    for q in queries {
      let result = evaluate(q)
      let automatic = q.kind == "spelling" ? correction(q) ?? "" : ""
      let matches = result.contains { q.kind == "keep" ? $0 == q.expected : $0.lowercased() == q.expected.lowercased() }
      print([q.kind, q.language.rawValue, q.input, q.expected, result.first ?? "", String(matches), automatic].joined(separator: "\t"))
    }
    var timing = [UInt64]()
    var consumed = 0
    for trial in 0..<32 {
      for q in queries {
        let clock = DispatchTime.now().uptimeNanoseconds
        consumed += evaluate(q).count
        if trial >= 8 { timing.append(DispatchTime.now().uptimeNanoseconds - clock) }
      }
    }
    timing.sort()
    print("TIMING\t\(Double(cold) / 1e6)\t\(timing.count)\t\(Double(timing[timing.count / 2]) / 1e6)\t\(Double(timing[Int(ceil(0.95 * Double(timing.count))) - 1]) / 1e6)\t\(consumed)")
  }
}
