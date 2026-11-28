import Foundation

/// Coordinates in letter-key pitches. Q is (0,0), A is (0.5,1), Z is (1.5,2).
struct GlidePoint {
  let x: Double
  let y: Double
}

final class GlideDecoder {
  private struct Template {
    let word: String
    let length: Double
    let shape: [UInt8]
    let prior: Double
  }
  private struct Match {
    let word: String
    let cost: Double
    let error: Double
  }
  private static let sampleCount = 24
  private static let centers: [Character: GlidePoint] = {
    var result: [Character: GlidePoint] = [:]
    for (y, row) in ["qwertyuiop", "asdfghjkl", "zxcvbnm"].enumerated() {
      let inset = Double(10 - row.count) / 2
      for (x, letter) in row.enumerated() {
        result[letter] = GlidePoint(x: Double(x) + inset, y: Double(y))
      }
    }
    return result
  }()
  private var groups: [Character: [Character: [Template]]] = [:]

  init(vocabulary: [String: Int]) {
    for (word, frequency) in vocabulary {
      guard (2...24).contains(word.count), frequency > 0, let first = word.first, let last = word.last else { continue }
      let path = word.compactMap { Self.centers[$0] }
      guard path.count == word.count else { continue }
      let length = Self.length(path)
      guard length >= 0.75 else { continue }
      let shape = Self.resample(path, length).map { UInt8(($0 * 20).rounded()) }
      let item = Template(word: word, length: length, shape: shape, prior: min(16, log(Double(frequency) + 1)))
      groups[first, default: [:]][last, default: []].append(item)
    }
  }

  func candidates(points: [GlidePoint], contextual: [String] = []) -> [String] {
    guard (3...512).contains(points.count), let first = points.first, let last = points.last,
      points.allSatisfy({ $0.x.isFinite && $0.y.isFinite }) else { return [] }
    let length = Self.length(points)
    guard (0.85...120).contains(length) else { return [] }
    let observed = Self.resample(points, length)
    func endpoints(_ p: GlidePoint) -> [Character] {
      Self.centers.compactMap { Self.distance($0.value, p) <= 0.98 ? $0.key : nil }
    }
    let preferred = contextual.prefix(3).map { $0.lowercased() }
    var best = [Match]()
    for start in endpoints(first) {
      for end in endpoints(last) {
        guard let group = groups[start]?[end] else { continue }
        for item in group {
          let ratio = length / item.length
          guard (0.48...2.05).contains(ratio) else { continue }
          var squared = 0.0
          for i in observed.indices {
            let delta = observed[i] - Double(item.shape[i]) / 20
            squared += delta * delta
          }
          let error = squared / Double(Self.sampleCount)
          guard error <= 0.92 else { continue }
          let context = preferred.firstIndex(of: item.word).map { 0.08 - Double($0) * 0.02 } ?? 0
          let cost = error + 0.24 * abs(log(ratio)) - 0.012 * item.prior - context
          let match = Match(word: item.word, cost: cost, error: error)
          if let index = best.firstIndex(where: { cost < $0.cost || cost == $0.cost && item.word < $0.word }) {
            best.insert(match, at: index)
          } else if best.count < 3 {
            best.append(match)
          }
          if best.count > 3 { best.removeLast() }
        }
      }
    }
    guard let winner = best.first, winner.error <= 0.55 else { return [] }
    if winner.error > 0.32 && best.count > 1 && best[1].cost - winner.cost < 0.07 { return [] }
    return best.map { $0.word }
  }

  private static func distance(_ a: GlidePoint, _ b: GlidePoint) -> Double { hypot(a.x - b.x, a.y - b.y) }
  private static func length(_ points: [GlidePoint]) -> Double {
    var result = 0.0
    for i in 1..<points.count { result += distance(points[i - 1], points[i]) }
    return result
  }
  private static func resample(_ points: [GlidePoint], _ total: Double) -> [Double] {
    var result = [Double](repeating: 0, count: sampleCount * 2)
    var i = 1, start = 0.0, span = distance(points[0], points[1])
    for sample in 0..<sampleCount {
      let at = Double(sample) * total / Double(sampleCount - 1)
      while i < points.count - 1 && start + span < at {
        start += span
        i += 1
        span = distance(points[i - 1], points[i])
      }
      let t = span == 0 ? 0 : min(1, max(0, (at - start) / span))
      let a = points[i - 1], b = points[i]
      result[2 * sample] = a.x + t * (b.x - a.x)
      result[2 * sample + 1] = a.y + t * (b.y - a.y)
    }
    return result
  }
}
