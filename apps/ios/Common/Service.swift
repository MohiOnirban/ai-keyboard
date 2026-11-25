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
    let (data, response) = try await net.data(for: req)
    let status = (response as? HTTPURLResponse)?.statusCode ?? 0
    if status == 401 {
      if path == "v1/session" && method == "DELETE" { return data }
      if let credential { try remove(credential) }
    }
    guard (200..<300).contains(status) else {
      throw Problem(
        [
          401: "Session expired. Try again.", 409: "This request was already processed.",
          429: "Usage or rate limit reached.",
        ][status] ?? "Generation service unavailable (\(status)).")
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
  func generate(_ input: String, mode: String, style: String) async throws -> Answer {
    guard !input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, input.utf16.count <= 1600
    else { throw Problem("Enter 1–1600 characters.") }
    var body: [String: Any] = [
      "text": input, "mode": mode, "style": style.lowercased(),
      "language": Settings.get("language", "auto"),
      "intensity": Settings.get("intensity", "balanced"),
    ]
    let rel = Settings.get("relationship")
    if !rel.isEmpty { body["relationship"] = rel }
    body["preferences"] =
      Dictionary(
        uniqueKeysWithValues: ["length", "humor", "formality", "emoji", "custom"].compactMap {
          let value = Settings.get($0)
          return value.isEmpty ? nil : ($0, value)
        }) as [String: String]
    let data = try await send("v1/generate", "POST", body: body, id: UUID(), credential: session())
    return try JSONDecoder().decode(Answer.self, from: data)
  }
}
