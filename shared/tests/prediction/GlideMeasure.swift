import Foundation

@main
struct GlideMeasurement {
  static func main() throws {
    let args = CommandLine.arguments
    let words = try String(contentsOfFile: args[1], encoding: .utf8)
    var frequency: [String: Int] = [:]
    for line in words.split(separator: "\n") {
      let row = line.split(separator: "\t")
      if row[0] == "en" { frequency[String(row[1])] = Int(row[2]) }
    }
    let start = DispatchTime.now().uptimeNanoseconds
    let decoder = GlideDecoder(vocabulary: frequency)
    let cold = DispatchTime.now().uptimeNanoseconds - start
    let data = try String(contentsOfFile: args[2], encoding: .utf8)
    var elapsed = [Double]()
    for line in data.split(separator: "\n") {
      let row = line.split(separator: "\t")
      let points = row[1].split(separator: " ").map { p -> GlidePoint in
        let pair = p.split(separator: ",")
        return GlidePoint(x: Double(pair[0])!, y: Double(pair[1])!)
      }
      let clock = DispatchTime.now().uptimeNanoseconds
      let results = decoder.candidates(points: points)
      elapsed.append(Double(DispatchTime.now().uptimeNanoseconds - clock) / 1e6)
      print(([String(row[0])] + results).joined(separator: "\t"))
    }
    elapsed.sort()
    print("TIMING\t\(Double(cold) / 1e6)\t\(elapsed[Int(ceil(Double(elapsed.count) * 0.95)) - 1])")
  }
}
