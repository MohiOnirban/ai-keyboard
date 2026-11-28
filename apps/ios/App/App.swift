import SwiftUI

@main struct KothaKeysApp: App {
  var body: some Scene { WindowGroup { Home() } }
}

struct Home: View {
  @State private var tab = 0
  @AppStorage("theme", store: Settings.prefs) var theme = "System"
  @Environment(\.openURL) private var openURL
  private let name = Bundle.main.object(forInfoDictionaryKey: "CFBundleDisplayName") as? String
    ?? "Keyboard"
  var body: some View {
    TabView(selection: $tab) {
      NavigationStack {
        List {
          Section {
            VStack(alignment: .leading, spacing: 12) {
              Text("কথা হোক নিজের মতো।").font(.system(size: 32, weight: .semibold, design: .rounded))
                .foregroundStyle(Color.accentColor)
              Text(name).font(.title.bold())
              Text(
                "Type in বাংলা, Banglish, or English. Shape a draft into a message that sounds like you."
              )
            }.padding(.vertical, 12)
            Button("Try the practice area") { tab = 1 }
          }
          Section("Enable your keyboard") {
            Text(
              "In Settings, open General → Keyboard → Keyboards → Add New Keyboard and choose \(name)."
            )
            Text("In a messaging app, hold the globe key and choose \(name).")
            Button("Open Settings") {
              openURL(URL(string: UIApplication.openSettingsURLString)!)
            }
          }
          Section("Allow Full Access for AI") {
            Text(
              "Offline typing works immediately. Full Access lets the keyboard send text you choose to the AI service and share your preferences. You review the suggestion and send the message yourself."
            )
          }
          Section("Help") {
            Text(
              "Some apps and secure fields use the system keyboard. When a draft is only partly visible, select the passage you want to replace. If the draft changes, copy the suggestion."
            )
          }
        }.navigationTitle("Keyboard")
      }.tabItem { Label("Keyboard", systemImage: "keyboard") }.tag(0)
      Playground().tabItem { Label("Practice", systemImage: "text.bubble") }.tag(1)
      Preferences().tabItem { Label("Settings", systemImage: "slider.horizontal.3") }.tag(2)
    }.preferredColorScheme(theme == "Dark" ? .dark : theme == "Light" ? .light : nil)
  }
}
struct Playground: View {
  @FocusState private var writing: Bool
  @State private var draft = ""
  @State private var result = ""
  @State private var mode = "rewrite"
  @State private var style = "Respectful"
  @State private var status = ""
  @State private var job: Task<Void, Never>?
  @State private var sequence = UUID()
  func cancel() {
    sequence = UUID()
    job?.cancel()
    job = nil
    result = ""
  }
  var body: some View {
    NavigationStack {
      Form {
        Section("Practice typing") {
          TextEditor(text: $draft).focused($writing).frame(minHeight: 100).accessibilityLabel(
            "Your message")
        }
        Section("AI playground") {
          Picker("Action", selection: $mode) {
            ForEach(["rewrite", "reply", "express"], id: \.self) { Text($0.capitalized) }
          }.pickerStyle(.segmented)
          Picker("Style", selection: $style) { ForEach(Settings.styles, id: \.self) { Text($0) } }
          Text("Generate sends this text and your selected preferences to the AI service.").font(
            .footnote)
          Button(job == nil ? "Generate suggestion" : "Cancel generation") {
            if job != nil {
              cancel()
              status = ""
              return
            }
            writing = false
            let id = UUID()
            sequence = id
            status = "Generating…"
            result = ""
            job = Task {
              do {
                let a = try await Service.shared.generate(draft, mode: mode, style: style)
                guard sequence == id else { return }
                result = a.suggestion
                status = "\(a.used) of \(a.limit) used today"
              } catch {
                guard sequence == id else { return }
                status = error.localizedDescription
              }
              job = nil
            }
          }.disabled(draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
          if !status.isEmpty { Text(status).font(.footnote) }
        }
        if !result.isEmpty {
          Section("Preview") {
            TextEditor(text: $result).frame(minHeight: 70)
            Button("Use suggestion") {
              draft = result
              cancel()
            }
            Button("Discard") { cancel() }
          }
        }
      }.navigationTitle("Practice").onChange(of: draft) { _, _ in
        cancel()
        status = ""
      }.onDisappear { cancel() }
    }
  }
}
struct Preferences: View {
  @State private var status = ""
  @State private var version = UUID()
  @AppStorage("haptics", store: Settings.prefs) private var haptics = false
  @AppStorage("predictiveSuggestions", store: Settings.prefs) private var predictiveSuggestions = true
  @AppStorage("nextWordPrediction", store: Settings.prefs) private var nextWordPrediction = true
  @AppStorage("autocorrect", store: Settings.prefs) private var autocorrect = false
  @AppStorage("automaticCapitalization", store: Settings.prefs) private var automaticCapitalization = false
  @AppStorage("typingLearning", store: Settings.prefs) private var typingLearning = false
  @State private var confirmClearHistory = false
  func value(_ key: String, _ initial: String = "") -> Binding<String> {
    Binding(
      get: { Settings.get(key, initial) },
      set: {
        Settings.prefs.set($0, forKey: key)
        version = UUID()
      })
  }
  func picker(_ title: String, _ key: String, _ items: [String]) -> some View {
    Picker(title, selection: value(key, items[0])) {
      ForEach(items, id: \.self) { item in
        Text(item.isEmpty ? "Unspecified" : item.capitalized).tag(item)
      }
    }
  }
  var body: some View {
    NavigationStack {
      Form {
        Section("Writing preferences") {
          picker("Language", "language", ["auto", "bn", "banglish", "en"])
          picker("Intensity", "intensity", ["balanced", "subtle", "bold"])
          picker(
            "Relationship", "relationship",
            ["", "friend", "crush", "partner", "family", "teacher", "colleague", "stranger"])
          ForEach(
            [
              ["Length", "length", "short", "medium", "long"],
              ["Humor", "humor", "low", "medium", "high"],
              ["Formality", "formality", "casual", "neutral", "formal"],
              ["Emoji", "emoji", "none", "occasional", "frequent"],
            ], id: \.self
          ) { p in picker(p[0], p[1], [""] + Array(p.dropFirst(2))) }
          TextField(
            "Custom instructions",
            text: Binding(
              get: { Settings.get("custom") },
              set: { Settings.prefs.set(String($0.prefix(200)), forKey: "custom") }))
          NavigationLink("Favorite styles") {
            List(Settings.styles, id: \.self) { s in
              Toggle(
                s,
                isOn: Binding(
                  get: { Settings.favorites.contains(s) },
                  set: { on in
                    var f = Settings.favorites.filter { $0 != s }
                    if on { f.append(s) }
                    Settings.prefs.set(f, forKey: "favorites")
                    version = UUID()
                  }))
            }
          }
        }
        Section("Keyboard") {
          picker("Keyboard height", "height", ["Standard", "Compact", "Tall"])
          picker("Appearance", "theme", ["System", "Light", "Dark"])
          Toggle("Haptics", isOn: $haptics)
        }
        Section("Offline typing") {
          Toggle("Predictive suggestions", isOn: $predictiveSuggestions)
          Toggle("Next-word prediction", isOn: $nextWordPrediction)
          Toggle("Autocorrect", isOn: $autocorrect)
          Text("Space applies high-confidence English corrections. Tap Undo or Backspace to restore your spelling.")
            .font(.footnote)
          Toggle("Auto-capitalization", isOn: $automaticCapitalization)
          NavigationLink("Personal dictionary") { PersonalDictionary() }
        }
        Section("Personalized learning") {
          Toggle("Learn from typing", isOn: $typingLearning)
          Text("With Full Access, the keyboard can remember individual words and accepted or undone suggestions on this device. You can turn learning off or clear its history here.")
            .font(.footnote)
          Button("Clear learned words", role: .destructive) { confirmClearHistory = true }
            .confirmationDialog("Clear learned words?", isPresented: $confirmClearHistory) {
              Button("Clear learned words", role: .destructive) {
                Settings.clearTypingHistory()
                status = "Learned words cleared."
              }
            }
        }
        Section("Usage and privacy") {
          Button("Check daily allowance") {
            Task {
              do {
                let u = try await Service.shared.usage()
                status = "\(u.used) of \(u.limit) generations used today."
              } catch { status = error.localizedDescription }
            }
          }
          Text(
            "Ordinary typing stays on this device. AI requests go through the backend and, in live mode, OpenRouter and its model provider. Their processing and retention policies apply. The backend keeps usage and cost records without message text."
          )
          if Settings.aiConsent {
            Button("Reset AI shortcut consent") {
              Settings.prefs.removeObject(forKey: "aiConsent")
              version = UUID()
              status = "AI shortcuts will ask for consent again."
            }
          }
          Button("Clear preferences") {
            Settings.prefs.removePersistentDomain(forName: Settings.group)
            version = UUID()
            status = "Preferences cleared."
          }
          Button("Reset anonymous session") {
            Task {
              do {
                try await Service.shared.reset()
                status = "Session reset."
              } catch { status = error.localizedDescription }
            }
          }
          if !status.isEmpty { Text(status) }
        }
      }.id(version).navigationTitle("Settings")
    }
  }
}

struct PersonalDictionary: View {
  @State private var words = Settings.personalWords
  @State private var newWord = ""
  @FocusState private var editingWord: Bool

  private var entry: String? {
    guard let word = Predictor.normalizedEntry(newWord),
      !words.contains(where: { Predictor.normalized($0) == Predictor.normalized(word) })
    else { return nil }
    return word
  }
  var body: some View {
    Form {
      Section {
        TextField("Add a word", text: $newWord)
          .focused($editingWord)
          .textInputAutocapitalization(.never)
          .autocorrectionDisabled()
        Button("Save word") {
          guard let entry, words.count < 250 else { return }
          words.append(entry)
          words.sort()
          Settings.prefs.set(words, forKey: "personalWords")
          newWord = ""
          editingWord = false
        }.disabled(entry == nil || words.count >= 250)
      } footer: {
        Text("Saved words appear in offline completions and autocorrect keeps them as typed. Add up to 250 words on this device.")
      }
      if !words.isEmpty {
        Section("Saved words") {
          ForEach(words, id: \.self) { Text($0) }
            .onDelete {
              words.remove(atOffsets: $0)
              Settings.prefs.set(words, forKey: "personalWords")
            }
        }
      }
    }
    .navigationTitle("Personal dictionary")
    .toolbar { EditButton() }
  }
}
