# KothaKey on iOS

## Install in Simulator

For the packaged simulator build, unzip `artifacts/phase1/KothaKey-iOS-simulator.zip` and drag `KothaKeys.app` onto a booted iPhone Simulator. Alternatively, run these commands from the repository root with a booted simulator's UUID.

```sh
xcodebuild -project apps/ios/KothaKeys.xcodeproj -scheme KothaKeys -configuration Debug -destination 'platform=iOS Simulator,id=DEVICE_ID' -derivedDataPath artifacts/ios build
xcrun simctl install DEVICE_ID artifacts/ios/Build/Products/Debug-iphonesimulator/KothaKeys.app
xcrun simctl launch DEVICE_ID dev.kothakeys.app
```

1. Open KothaKey. The Practice tab includes a host text field and a companion AI playground.
2. In iOS Settings, go to **General → Keyboard → Keyboards → Add New Keyboard**, then choose **KothaKey**.
3. Open a normal text field. Hold the system globe and select **KothaKey**. Its language shortcut or a long press on the spacebar selects English, বাংলা, Phonetic or Banglish.
4. For AI, open the KothaKey entry in Settings and enable **Allow Full Access**. Run the mock backend from `backend/` with `npm run dev`. Debug simulator builds connect to `http://localhost:4000` by default. English, Bengali and phonetic typing work offline with Full Access off.
5. In the companion Settings tab, choose System, Light or Dark appearance, adjust keyboard height and haptics, or edit style preferences. The extension reads App Group preferences when iOS permits access.

## Using the keyboard

- Hold a top-row Latin letter to type its number. Tap Shift twice for Caps Lock. Phonetic typing respects uppercase Avro input. Tap a local suggestion or press space to commit a pending word.
- English and raw Banglish candidates come from small bundled prefix lists. Direct Bangla uses local dictionary entries and phonetic candidates use the existing Avro parser and dictionary. Ordinary typing does not call the AI service.
- Tap **?123** for symbols, then the secondary-page shortcut or **123** for the dedicated keypad. Its operators sit alongside the three digit columns. Tap **ABC** to return to letters or **?123** for symbols.
- Tap a toolbar icon to replace the keys with Grammar, Improve & Tone, Ask AI, Translation, Emoji, Clipboard or More. The back button restores the typing layout. A conditional, three-candidate strip shares toolbar space. Its grid icon reveals the tools while retaining a pending phonetic word.
- For a replacement, select the exact passage in the host first. Without a selection, the panel labels the available input as a cursor excerpt and offers insertion. Tap **Generate**, review the result, then choose the action. The extension checks the document identifier, selection and surrounding text again. Moving the target invalidates a pending replacement.
- Tone offers nine primary choices followed by additional styles and favorites. Picking a new tone from a result makes a new explicit generation request. Distinct alternatives for the same source can be selected before applying. Ask supports Express, Rewrite, Reply and request-specific writing instructions. Translation exposes separate source and target controls for Bangla, Banglish and English. The default mock demonstrates a limited set of translations.
- The Return key follows the host’s declared return-key type. An explicit tap inserts the platform newline action through the document proxy. The host controls whether it advances a field, submits a form or inserts a newline.
- Clipboard contents load only after an explicit tap. The voice utility explains how to use iOS dictation and offers keyboard switching. Secure text fields and some phone fields use Apple's keyboard. Hosts may disable extensions. [Apple custom keyboard configuration](https://developer.apple.com/documentation/uikit/configuring-a-custom-keyboard-interface) and [Apple virtual keyboard guidance](https://developer.apple.com/design/human-interface-guidelines/virtual-keyboards)

On iOS 27.2 Safari, moving directly from a textarea to a number input sometimes delivered the default keyboard trait. In that case, use **?123 → 123** to select the keypad. The same number field automatically opened the numeric layout when refocused after a password field. Password entry used the system keyboard.

## Reproduce the simulator checks

Use a booted simulator with an English-language Settings interface. The tests walk through keyboard activation and change Full Access as required. The local mock must be running on port 4000. For loading-state screenshots, run the delayed forwarding proxy in a second terminal. Run the Safari fixture server in a third terminal.

```sh
node apps/ios/Tests/delayedMock.mjs
```

```sh
python3 -m http.server 4100 --bind 127.0.0.1 --directory apps/ios/Tests
```

```sh
xcodebuild -project apps/ios/KothaKeys.xcodeproj -scheme KothaKeys -configuration Debug -destination 'platform=iOS Simulator,id=DEVICE_ID' -derivedDataPath artifacts/ios -parallel-testing-enabled NO -resultBundlePath /tmp/kothakey-phase1.xcresult KEYBOARD_API_BASE=http://localhost:4133 test
```

The proxy delays each generation for four seconds, then forwards it to the real local mock. The shipped simulator build uses the normal backend URL. Xcode provides screenshot attachments in the result bundle. Phase 1 captures are collected under `artifacts/phase1/before` and `artifacts/phase1/after/ios`. Coverage includes the original target staying intact during generation, selecting and inserting results, changing the selection, return to the prior layout, symbol/numeric pages, offline Bengali typing, light/dark appearance, fixed panel geometry, and a local Safari fixture.

All ten workflows passed together on the packaged configuration using the normal local mock. [Final Xcode result summary](../../artifacts/phase1/checks/ios-integrated-summary.json) · [Phase 1 screenshot gallery](../../artifacts/phase1/screenshots.html)

The established pre-redesign results remain available in `artifacts/ios-verified-workflows.xcresult`, `artifacts/ios-safari-and-draft.xcresult` and `artifacts/ios-default-restored.xcresult`. The separately authorized historical live generation is in `artifacts/ios-extension-live-one.xcresult`. Phase 1 uses the mock.

## Physical devices and TestFlight

Open the Xcode project, configure both bundle IDs for your development team, enable the same App Group on the app and extension, and provision the shared Keychain access group. Update `Settings.group` if your registered group differs. Set `KEYBOARD_API_BASE` to the HTTPS backend for both targets. Choose the connected iPhone, enable Developer Mode if requested, then run the containing app and follow the activation steps. A simulator application requires a separate signed device build.

For TestFlight, create an App Store Connect record and distribution provisioning profiles, archive a Release build for a generic device, validate in Organizer and upload under your developer account. Review extension requirements, privacy disclosures, and the final listing before distribution. [App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/#extensions)
