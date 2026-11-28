import Foundation
import Security

enum Settings {
  static let group = "group.dev.kothakeys.shared"
  static let prefs = UserDefaults(suiteName: group) ?? .standard
  static let styles = [
    "Flirty", "Romantic", "Funny", "Poetic", "Witty", "Sarcastic", "Confident", "Bold", "Subtle",
    "Mature", "Emotional", "Respectful", "Professional", "Savage", "Comforting", "Apologetic",
    "Culturally inspired", "Custom instructions",
  ]
  static var aiConsent: Bool { prefs.bool(forKey: "aiConsent") }
  static var autocorrect: Bool { prefs.object(forKey: "autocorrect") as? Bool ?? true }
  static var predictiveSuggestions: Bool { prefs.object(forKey: "predictiveSuggestions") as? Bool ?? true }
  static var nextWordPrediction: Bool { prefs.object(forKey: "nextWordPrediction") as? Bool ?? true }
  static var automaticCapitalization: Bool { prefs.object(forKey: "automaticCapitalization") as? Bool ?? true }
  static var automaticSpacing: Bool { prefs.object(forKey: "automaticSpacing") as? Bool ?? true }
  static var spaceAfterSuggestion: Bool { prefs.object(forKey: "spaceAfterSuggestion") as? Bool ?? true }
  static var cursorAware: Bool { prefs.object(forKey: "cursorAware") as? Bool ?? true }
  static var doubleSpacePeriod: Bool { prefs.object(forKey: "doubleSpacePeriod") as? Bool ?? true }
  static var glideTyping: Bool { prefs.object(forKey: "glideTyping") as? Bool ?? true }
  static var gestureTrail: Bool { prefs.object(forKey: "gestureTrail") as? Bool ?? true }
  static var voiceTyping: Bool { prefs.object(forKey: "voiceTyping") as? Bool ?? true }
  static var clipboardHistory: Bool { prefs.bool(forKey: "clipboardHistory") }
  struct Clip: Codable {
    let id: UUID
    let text: String
    var date: Date
    var pinned: Bool
  }
  private static let clipsKey = "localClipboardItems"
  static func clearClips() { prefs.removeObject(forKey: clipsKey) }
  static func saveClips(_ items: [Clip]) {
    guard clipboardHistory else { clearClips(); return }
    if let data = try? JSONEncoder().encode(items) { prefs.set(data, forKey: clipsKey) }
  }
  static func clips() -> [Clip] {
    guard clipboardHistory, let data = prefs.data(forKey: clipsKey),
      let items = try? JSONDecoder().decode([Clip].self, from: data) else { return [] }
    let duration: TimeInterval = ["15 minutes": 900, "1 hour": 3600, "1 day": 86400][get("clipLifetime", "1 hour")] ?? 3600
    let fresh = items.filter { $0.pinned || $0.date.timeIntervalSinceNow > -duration }
    if items.count != fresh.count { saveClips(fresh) }
    return fresh
  }
  static func addClip(_ text: String) {
    guard clipboardHistory, !text.isEmpty, text.utf16.count <= 1600 else { return }
    // Expiring one-time codes and standalone card/phone numbers should not become history.
    if text.range(of: #"(?<!\d)(?:\d[ -]?){4,19}(?!\d)"#, options: .regularExpression) != nil { return }
    var items = clips()
    if let i = items.firstIndex(where: { $0.text == text }) {
      var item = items.remove(at: i)
      item.date = Date()
      items.insert(item, at: 0)
    } else {
      if items.count >= 12 {
        guard let oldest = items.lastIndex(where: { !$0.pinned }) else { return }
        items.remove(at: oldest)
      }
      items.insert(Clip(id: UUID(), text: text, date: Date(), pinned: false), at: 0)
    }
    saveClips(items)
  }
  static var learning: Bool { prefs.bool(forKey: "typingLearning") }
  private static let historyKey = "typingHistory"
  static var typingHistory: TypingHistory {
    guard learning, let data = prefs.data(forKey: historyKey) else { return TypingHistory() }
    return (try? JSONDecoder().decode(TypingHistory.self, from: data)) ?? TypingHistory()
  }
  static func saveTypingHistory(_ history: TypingHistory) {
    if learning, let data = try? JSONEncoder().encode(history) { prefs.set(data, forKey: historyKey) }
  }
  static func clearTypingHistory() { prefs.removeObject(forKey: historyKey) }
  static var personalWords: [String] { prefs.stringArray(forKey: "personalWords") ?? [] }
  static var favorites: [String] {
    prefs.stringArray(forKey: "favorites") ?? ["Funny", "Respectful", "Romantic"]
  }
  static func get(_ key: String, _ fallback: String = "") -> String {
    prefs.string(forKey: key) ?? fallback
  }
}
struct Usage: Decodable {
  let used: Int
  let limit: Int
}
struct Answer: Decodable {
  let suggestion: String
  let used: Int
  let limit: Int
}
struct Problem: LocalizedError {
  let message: String
  var errorDescription: String? { message }
  init(_ message: String) { self.message = message }
}
private final class DirectConnection: NSObject, URLSessionTaskDelegate {
  func urlSession(_ session: URLSession, task: URLSessionTask,
    willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest,
    completionHandler: @escaping (URLRequest?) -> Void) { completionHandler(nil) }
}
actor Service {
  static let shared = Service()
  private struct Credential {
    let token: String
    let reference: Data
  }
  private let net: URLSession = {
    let config = URLSessionConfiguration.ephemeral
    config.urlCache = nil
    config.httpCookieStorage = nil
    config.timeoutIntervalForRequest = 35
    return URLSession(configuration: config, delegate: DirectConnection(), delegateQueue:nil)
  }()
  private var base: URL {
    get throws {
      let configured = Bundle.main.object(forInfoDictionaryKey: "KeyboardAPIBase") as? String
      if let configured, !configured.isEmpty {
        guard let url = URL(string: configured), let host = url.host, !host.isEmpty,
          url.user == nil, url.password == nil, url.query == nil, url.fragment == nil
        else { throw Problem("Configure a valid backend URL for this build.") }
        if url.scheme == "https" { return url }
        #if DEBUG && targetEnvironment(simulator)
          if url.scheme == "http", host == "localhost" || host == "127.0.0.1" { return url }
        #endif
        throw Problem("Configure the HTTPS backend for this build.")
      }
      #if DEBUG && targetEnvironment(simulator)
        return URL(string: "http://localhost:4000")!
      #else
        throw Problem("Configure the HTTPS backend for this build.")
      #endif
    }
  }
  private func key(_ origin: URL) -> [String: Any] {
    [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrService as String: "Kotha installation",
      kSecAttrAccount as String: origin.absoluteString,
      kSecAttrAccessGroup as String: Settings.group,
    ]
  }
  private func stored(_ url: URL) throws -> Credential? {
    var query = key(url)
    query[kSecReturnData as String] = true
    query[kSecReturnPersistentRef as String] = true
    var item: CFTypeRef?
    let status = SecItemCopyMatching(query as CFDictionary, &item)
    if status == errSecItemNotFound { return nil }
    guard status == errSecSuccess, let response = item as? [String: Any],
      let data = response[kSecValueData as String] as? Data,
      let token = String(data: data, encoding: .utf8),
      let reference = response[kSecValuePersistentRef as String] as? Data
    else { throw Problem("Secure session storage unavailable. Check App Group signing.") }
    return Credential(token: token, reference: reference)
  }
  private func remove(_ credential: Credential) throws {
    // A late response may only affect the exact Keychain item used to send it.
    let query = [kSecValuePersistentRef as String: credential.reference] as CFDictionary
    let status = SecItemDelete(query)
    guard status == errSecSuccess || status == errSecItemNotFound else {
      throw Problem("Could not clear the local session.")
    }
  }
  private func send(
    _ path: String, _ method: String, token: String? = nil, body: [String: Any]? = nil,
    id: UUID? = nil, credential: Credential? = nil
  ) async throws -> Data {
    try Task.checkCancellation()
    var req = URLRequest(url: try base.appendingPathComponent(path))
    req.httpMethod = method
    if let bearer = credential?.token ?? token {
      req.setValue("Bearer " + bearer, forHTTPHeaderField: "Authorization")
    }
    if let id { req.setValue(id.uuidString, forHTTPHeaderField: "Idempotency-Key") }
    if let body {
      req.httpBody = try JSONSerialization.data(withJSONObject: body)
      req.setValue("application/json", forHTTPHeaderField: "Content-Type")
    }
    let (data, reply) = try await net.data(for: req)
    let response = reply as? HTTPURLResponse
    let status = response?.statusCode ?? 0
    if status == 401 {
      if path == "v1/session" && method == "DELETE" { return data }
      if let credential { try remove(credential) }
    }
    guard (200..<300).contains(status) else {
      let diagnostic = response?.value(forHTTPHeaderField: "kotha-error-code") ?? ""
      let diagnostics = [
        "budget": "The AI service has reached its spending limit.",
        "provider_restricted": "The configured AI model or provider is unavailable.",
        "provider_quota": "The AI provider has reached its spending limit.",
        "provider_rate": "The AI provider is busy. Try again shortly.",
        "provider_auth": "The AI provider could not authenticate the service.",
        "provider_timeout": "The AI provider took too long to respond.",
        "provider_dns": "The service could not resolve the AI provider address.",
        "provider_tls": "The service could not establish a secure connection to the AI provider.",
        "provider_http": "The AI provider is temporarily unavailable.",
        "provider_response": "The AI provider returned an unusable response.",
        "quota": "The daily AI allowance has been reached.",
      ]
      let statusMessage = [
        401: "Session expired. Try again.", 409: "This request was already processed.",
        429: "Usage or rate limit reached.",
      ][status]
      throw Problem(diagnostics[diagnostic] ?? statusMessage ?? "Generation service unavailable (\(status)).")
    }
    return data
  }
  private func session() async throws -> Credential {
    let url = try base
    if let existing = try stored(url) { return existing }
    struct Session: Decodable { let token: String }
    let data = try await send("v1/sessions", "POST", body: [:])
    let token = try JSONDecoder().decode(Session.self, from: data).token
    var q = key(url)
    q[kSecValueData as String] = Data(token.utf8)
    q[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
    q[kSecReturnPersistentRef as String] = true
    var item: CFTypeRef?
    let status = SecItemAdd(q as CFDictionary, &item)
    if status == errSecSuccess, let reference = item as? Data {
      return Credential(token: token, reference: reference)
    }
    // Another client may have won the add. Revoke only the unused server credential.
    _ = try? await send("v1/session", "DELETE", token: token)
    if status == errSecDuplicateItem, let existing = try stored(url) { return existing }
    throw Problem("Secure session storage unavailable. Check App Group signing.")
  }
  func usage() async throws -> Usage {
    try await JSONDecoder().decode(Usage.self, from: send("v1/usage", "GET", credential: session()))
  }
  func reset() async throws {
    guard let credential = try stored(base) else { return }
    _ = try await send("v1/session", "DELETE", credential: credential)
    try remove(credential)
  }
  func generate(_ input: String, mode: String, style: String, language: String? = nil, instruction: String? = nil) async throws -> Answer {
    guard !input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, input.utf16.count <= 1600
    else { throw Problem("Enter 1–1600 characters.") }
    var body: [String: Any] = [
      "text": input, "mode": mode, "style": style.lowercased(),
      "language": language ?? Settings.get("language", "auto"),
      "intensity": Settings.get("intensity", "balanced"),
    ]
    let rel = Settings.get("relationship")
    if !rel.isEmpty { body["relationship"] = rel }
    var preferences = Dictionary(
        uniqueKeysWithValues: ["length", "humor", "formality", "emoji", "custom"].compactMap {
          let value = Settings.get($0)
          return value.isEmpty ? nil : ($0, value)
        }) as [String: String]
    if let instruction {
      var combined = style == "translation" ? [instruction, preferences["custom"]].compactMap { $0 }.joined(separator: ". ") : instruction
      while combined.utf16.count > 500 { combined.removeLast() }
      preferences["custom"] = combined
    }
    body["preferences"] = preferences
    let data = try await send("v1/generate", "POST", body: body, id: UUID(), credential: session())
    return try JSONDecoder().decode(Answer.self, from: data)
  }
}
