# KothaKey on iOS

## Install in Simulator

For the packaged simulator build, unzip `artifacts/advanced/KothaKey-iOS-simulator.zip` and drag `KothaKeys.app` onto a booted iPhone Simulator. Alternatively, run these commands from the repository root with a booted simulator's UUID.

```sh
xcodebuild -project apps/ios/KothaKeys.xcodeproj -scheme KothaKeys -configuration Debug -destination 'platform=iOS Simulator,id=DEVICE_ID' -derivedDataPath artifacts/ios build
xcrun simctl install DEVICE_ID artifacts/ios/Build/Products/Debug-iphonesimulator/KothaKeys.app
xcrun simctl launch DEVICE_ID dev.kothakeys.app
```

1. Open KothaKey. The Practice tab includes a host text field and a companion AI playground.
2. In iOS Settings, go to **General → Keyboard → Keyboards → Add New Keyboard**, then choose **KothaKey**.
3. Open a normal text field. Hold the system globe and select **KothaKey**. Tap the language key beside the spacebar, open More options, or hold the spacebar to select English, বাংলা, Phonetic or Banglish.
4. For AI, open the KothaKey entry in Settings and enable **Allow Full Access**. Run the mock backend from `backend/` with `npm run dev`. Debug simulator builds connect to `http://localhost:4000` by default. English, Bengali and phonetic typing work offline with Full Access off.
5. In the companion Settings tab, choose System, Light or Dark appearance, adjust keyboard height and haptics, edit style preferences, toggle Autocorrect, or add and remove words in Personal dictionary. The extension reads App Group preferences when iOS permits access.

## Using the keyboard

- Tap Shift twice for Caps Lock. Hold a letter for its available number or accented variants. Phonetic typing respects uppercase Avro input. Tap a suggestion or press Space to commit a pending word. During a press, the character preview stays inside the extension. The keyboard uses the iOS input-click setting and offers a separate haptics switch.
- The three-slot suggestion strip ranks frequency, the previous one or two words, spelling proximity and local preferences. It keeps the typed form available when other forms are suggested. For example, typing `hey how ya doing? im` puts **I'm** first. Phonetic mode keeps Avro's Bangla conversion and adds local Bengali completions.
- Suggestions, next-word prediction, capitalization and conservative English autocorrection start enabled. Existing explicit preferences are honored. Space and punctuation can apply a correction or split a joined phrase. Tap **Undo** or press Backspace immediately to restore the original spelling. The companion has separate switches for missing spaces, space after suggestions, double-space period and cursor suggestions. Signed-in and secure fields may use the system keyboard or share reduced input traits.
- **Settings → Personal dictionary** stores words you explicitly add. **Settings → Personalized learning** starts disabled and controls learned words, word pairs, accepted suggestions and undone corrections. Learning uses the shared local container with Full Access, excludes protected fields and avoids fields that request no autocorrection. Turning learning off stops reading and recording history. **Clear learned words** removes that history. Normal typing uses local resources.
- Use **123** for numbers and punctuation, **#+=** for additional symbols, and **ABC** for letters. The emoji key beside **123** opens a grid with **ABC** and Backspace. **Tools → Keyboard layout → Numbers** opens large 1–9 digits in three columns and a centered zero. On supported number and decimal fields, KothaKey opens the host-appropriate number pad automatically.
- Hold Space briefly and slide left or right to move the insertion point. Use **Tools → Typing language** for English, Banglish, Phonetic Bangla or direct বাংলা. The system globe appears in the keyboard when iOS requires it. On Face ID devices, iOS supplies that control below the extension.
- **Tools** opens a two-column dashboard with Theme, Clipboard, Emoji, Typing language, Text editing, Keyboard layout, Voice input and Settings. **Close tools** returns to the key page you were using. Text editing includes cursor movement and Backspace. The in-keyboard Settings tile controls suggestions, next words, autocorrect, capitalization and haptics. Use the companion to manage local history and dictionary entries.
- For a replacement, select the exact passage in the host first. Without a selection, the panel labels the available input as a cursor excerpt and offers insertion. The first AI shortcut request asks for consent with **Allow & Generate**. After consent, tap **Fix Grammar** or select a tone to generate directly when a draft is available. Ask AI and Translation retain **Generate**. Review the result before choosing an action. Changing tones cancels the earlier request. Moving the target invalidates a pending replacement. Reset shortcut consent in the companion settings.
- Tone offers nine primary choices followed by additional styles and favorites. Picking a new tone from a result makes a new explicit generation request. Distinct alternatives for the same source can be selected before applying. Ask supports Express, Rewrite, Reply and request-specific writing instructions. Translation exposes separate source and target controls for Bangla, Banglish and English. The default mock demonstrates a limited set of translations.
- The Return key follows the host’s declared return-key type. An explicit tap inserts the platform newline action through the document proxy. The host controls whether it advances a field, submits a form or inserts a newline.
- Tap inside a committed word, move to its end or select it to refresh suggestions. A replacement preserves adjacent words and punctuation. The compact Undo action also handles cut, selected deletion, AI edits and accepted suggestions while the host still exposes the same target. Safari can consume an adjacent space during a selection delete while reporting the old caret through the keyboard proxy. In that case KothaKey completes the native cut and withholds its Undo button.
- Swipe across the letter rows to enter a word. Glide uses a local dictionary and the measured key geometry. Alternatives share the suggestion strip and Undo restores the prior text. The companion has independent glide and trail switches. English is the primary glide vocabulary. Banglish and phonetic use the smaller romanized vocabulary.
- **Tools → Clipboard** provides Copy and Cut for a selection. Tap **Load clipboard** to preview and **Insert** to paste. With Full Access and opt-in history, you can pin, insert, delete or clear saved items. Unpinned entries expire after your selected interval. Disabling history clears the list.
- **Tools → Voice input** gives you a **Switch keyboard** action to reach Apple Dictation. The extension keeps its handoff in Tools on Face ID devices with the system Dictation area. The extension cannot access a microphone. Apple's iOS 27 Dictation list includes English locales and omits Bangla. Mixed Bangla-English speech was not verified. Secure text and some phone fields use Apple's keyboard. [Apple custom keyboard guide](https://developer.apple.com/library/archive/documentation/General/Conceptual/ExtensibilityPG/CustomKeyboard.html) · [Apple Dictation languages](https://www.apple.com/ios/feature-availability/#dictation)

On iOS 27.2 Safari, moving directly from a textarea to a number input sometimes delivered the default keyboard trait. In that case, use **Tools → Keyboard layout → Numbers** to select the keypad. The same number field automatically opened the numeric layout when refocused after a password field. Password entry used the system keyboard.

## Reproduce the simulator checks

Run the standalone predictor and history fixtures from the repository root. The app target bundles the original Avro assets, and the keyboard extension also bundles the shared sentence corpus, frequency lists and their notices.

```sh
xcrun swiftc apps/ios/Common/Predictor.swift apps/ios/Tests/PredictionChecks.swift -o /tmp/kothakey-prediction-checks
/tmp/kothakey-prediction-checks
```

Use a booted simulator with an English-language Settings interface. The tests walk through keyboard activation and change Full Access as required. The local mock must be running on port 4000. For loading-state screenshots, run the delayed forwarding proxy in a second terminal. Run the Safari fixture server in a third terminal.

```sh
node apps/ios/Tests/delayedMock.mjs
```

```sh
python3 -m http.server 4100 --bind 127.0.0.1 --directory apps/ios/Tests
```

```sh
xcodebuild -project apps/ios/KothaKeys.xcodeproj -scheme KothaKeys -configuration Debug -destination 'platform=iOS Simulator,id=DEVICE_ID' -derivedDataPath artifacts/ios -parallel-testing-enabled NO -resultBundlePath /tmp/kothakey-phase2.xcresult KEYBOARD_API_BASE=http://localhost:4133 test
```

The proxy delays generation for four seconds and forwards requests to the local mock. It simulates a provider error for the synthetic Professional tone regression fixture so the test can verify editing and retrying the draft. The shipped simulator build uses the direct mock URL without that fixture. Xcode provides screenshot attachments in the result bundle. Phase 1.1 captures are collected under `artifacts/phase11/after/ios`. Phase 1 captures remain in `artifacts/phase1`. Coverage includes the original target staying intact during generation, selecting and inserting results, changing the selection, return to the prior layout, symbol/numeric pages, offline Bengali typing, light/dark appearance, fixed panel geometry, and a local Safari fixture.

Phase 2 exercised the installed extension on the iPhone 18 Pro simulator running iOS 27.2. The evidence includes same-editor Apple comparisons, key gestures, Bangla and Banglish entry, secure and numeric Safari fields, prediction, learning and all four local mock AI tools. The ZIP was installed and launched separately. A paired physical iPhone was unavailable. [Simulator captures](../../artifacts/phase2/after/ios) · [Integrated checks](../../artifacts/phase2/checks/ios-native-summary.json) · [Targeted checks](../../artifacts/phase2/checks/ios-native-final-summary.json) · [Final gesture checks](../../artifacts/phase2/checks/ios-native-gesture-summary.json)

Phase 1.1 passed 12 simulator workflows on the delayed local mock. Two targeted runs on the latest source verified direct AI and recovery after a failed tone request. The packaged direct-mock build was installed and launched separately. [Regression summary](../../artifacts/phase11/checks/ios-regression-summary.json) · [Direct AI summary](../../artifacts/phase11/checks/ios-final-ai-summary.json) · [Edited draft retry summary](../../artifacts/phase11/checks/ios-stale-tone-summary.json)

The earlier Phase 1 run passed ten workflows on the direct local mock. [Final Xcode result summary](../../artifacts/phase1/checks/ios-integrated-summary.json) · [Phase 1 screenshot gallery](../../artifacts/phase1/screenshots.html)

The established pre-redesign results remain available in `artifacts/ios-verified-workflows.xcresult`, `artifacts/ios-safari-and-draft.xcresult` and `artifacts/ios-default-restored.xcresult`. The separately authorized historical live generation is in `artifacts/ios-extension-live-one.xcresult`. Phase 1 uses the mock.

## Physical devices and TestFlight

Open the Xcode project, configure both bundle IDs for your development team, enable the same App Group on the app and extension, and provision the shared Keychain access group. Update `Settings.group` if your registered group differs. Set `KEYBOARD_API_BASE` to the HTTPS backend for both targets. Choose the connected iPhone, enable Developer Mode if requested, then run the containing app and follow the activation steps. A simulator application requires a separate signed device build.

For TestFlight, create an App Store Connect record and distribution provisioning profiles, archive a Release build for a generic device, validate in Organizer and upload under your developer account. Review extension requirements, privacy disclosures, and the final listing before distribution. [App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/#extensions)
