# iOS

Open `KothaKeys.xcodeproj` in Xcode, choose the KothaKeys scheme, select an iPhone simulator, and Run. Offline typing works with the bundled keyboard resources. For AI testing, start the repository's mock backend with `npm run dev` in `backend/`. Debug simulator builds use `http://localhost:4000`.

In Settings → General → Keyboard → Keyboards → Add New Keyboard, choose Kotha Keys. In a text field, hold the globe and choose Kotha Keys, then select English, Banglish, Phonetic or বাংলা using the layout menu. Leave Allow Full Access off for offline typing. To enable the extension's AI feature, open the Kotha Keys entry in Settings and turn on Allow Full Access. For mock AI practice before activation, open the app's Practice tab.

For a rewrite, select the passage in the host application before tapping AI and Generate. When the keyboard has access to partial draft context or pasted text, it can offer insertion at the cursor. Review the suggestion before applying it. If the original target changes, the keyboard cancels an active request or offers to copy an existing result.

From the repository root, build and test with the installed iPhone simulator UUID substituted for DEVICE_ID.

```sh
xcodebuild -project apps/ios/KothaKeys.xcodeproj -scheme KothaKeys -configuration Debug -destination 'platform=iOS Simulator,id=DEVICE_ID' -derivedDataPath artifacts/ios build
xcodebuild -project apps/ios/KothaKeys.xcodeproj -scheme KothaKeys -configuration Debug -destination 'platform=iOS Simulator,id=DEVICE_ID' -derivedDataPath artifacts/ios -parallel-testing-enabled NO test
xcrun simctl install DEVICE_ID artifacts/ios/Build/Products/Debug-iphonesimulator/KothaKeys.app
xcrun simctl launch DEVICE_ID dev.kothakeys.app
```

## Simulator verification

Xcode 27.2 built the Debug app and its embedded keyboard extension for the iPhone 18 Pro simulator running iOS 27.2. The app icon and UserDefaults required-reason privacy manifests are packaged in the app and extension as applicable.

The mock suite in `artifacts/ios-verified-workflows.xcresult` passed three tests.

- The companion generated and applied a suggestion, displayed the daily allowance, revoked the anonymous session, and created a fresh session with zero usage.
- The keyboard extension with Full Access on generated a mock suggestion from a synthetic draft, preserved the draft through preview, and inserted the suggestion after an explicit tap.
- The keyboard extension with Full Access off switched to phonetic typing and committed `আমি তোমাকে ভালোবাসি ` after using Shift and editing the pending word with Backspace. The pending word appears in the keyboard toolbar until it is committed.

The separately authorized live extension case passed in `artifacts/ios-extension-live-one.xcresult`. It made one generation request with a synthetic draft, confirmed the draft remained intact during preview, and inserted the result after an explicit tap.

After the live test, the default mock build was reinstalled. The Full Access off typing check passed again in `artifacts/ios-default-restored.xcresult`.

The host phonetic checks passed 26 cases and 150 dictionary entries, including uppercase Avro distinctions.

### Safari and draft safety

Both follow-up UI checks passed in `artifacts/ios-safari-and-draft.xcresult`.

- The real extension typed `আমি ` into a local Safari textarea in dark landscape mode with Full Access off.
- A mock preview stayed safe after the draft changed to `meeting x`. Tapping Insert preserved the edited text. Cancel closed the preview, and the test restored Full Access to off.

To run the Safari fixture test, serve the fixture from the repository root before starting the simulator tests. The test skips when the fixture page is unavailable.

```sh
python3 -m http.server 4100 --bind 127.0.0.1 --directory apps/ios/Tests
```

## Device signing and release

On a physical iPhone, register the containing app and extension bundle identifiers for your team, enable the identical App Group on both, configure signing, and select the connected device. The App Group also identifies the shared Keychain access group. If you use a different group, update both the entitlement and Settings.group. Set the KEYBOARD_API_BASE build setting to the HTTPS backend on both targets. Enable Developer Mode when prompted, run the containing app, and follow the activation steps. The locally built simulator .app targets the simulator and requires a separate signed device build for an iPhone.

For TestFlight use the team's distribution signing, registered identifiers, App Group provisioning, an App Store Connect record and a signed Release archive for a generic physical device. Validate through Organizer before uploading. Review final branding, icons, privacy disclosures and the store listing, including the keyboard extension requirements in App Review Guideline 4.4.1. [Apple App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/#extensions)
