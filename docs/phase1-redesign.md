# KothaKey keyboard redesign

কথা হোক নিজের মতো।

## Audit and design decisions

The previous Android keyboard gave each row its own weighted width. The iOS keyboard used an equally distributed stack per row. In both implementations the center row and third-row letters grew as the number of keys decreased. The original Android assistant sat in a separate panel above the keyboard. On iOS, opening it added 66 points to the preferred height.

The Gboard alphabet, symbols and numeric references establish the letter pitch, half-key home-row inset, wider modifiers, labelled spacebar and dedicated keypad. The CleverType toolbar, storyboards and individual result screens establish action order, horizontally scrolling choices, in-panel loading, readable results and an explicit return to typing. Chat controls, competitor logos, subscription screens and recording overlays belong to the reference apps.

Each keyboard uses the same semantic light and dark palette, layout proportions and panel navigation. Native input, composition, clipboard and editor permissions remain platform-specific. The existing Fastify endpoint handles all four tools. Two reserved rewrite styles select grammar correction and translation. The chosen translation target uses the existing language field.

## Geometry and evidence

| Device capture | Original letter pitch by row | Redesigned letter pitch by row |
| --- | --- | --- |
| Pixel 7 emulator, 1,080 px wide | 106 / 117.5 / 151 px | 106 / 106 / 106 px |
| iPhone 18 Pro simulator, 1,206 px wide | 120 / 133 / 171 px | 119 / 119 / 119 px |

The gallery contains 27 original captures and 57 redesigned captures, including all four AI panels and their loading states. The upper part of the practice screenshots belongs to the host app. The keyboard starts at its toolbar or tool heading. Pitch is the median horizontal distance between adjacent letter faces in the original-resolution screenshots, measured independently of the layout code. XCTest also measured 39.6-point Q, A and Z hit boxes with a 19.8-point second-row offset and a 59.4-point third-row offset. The iOS spacebar measured 160.38 points wide. The four iOS assistant panels kept the same 541–795 point content bounds during setup, loading and preview. [Open the native screenshot gallery](../artifacts/phase1/screenshots.html) · [Original keyboard evidence](../artifacts/phase1/before/README.md)

## Install and type

[KothaKey Android debug APK](../artifacts/phase1/KothaKey-android-debug.apk) · [KothaKey iOS simulator ZIP](../artifacts/phase1/KothaKey-iOS-simulator.zip) · [SHA-256 checksums](../artifacts/phase1/SHA256SUMS.txt)

- **Android phone or emulator.** Open the Phase 1 debug APK, then open KothaKey. Choose **Enable keyboard** and enable KothaKey in Android settings. Return to the app and tap **Choose keyboard**. From the repository root, install over USB with `adb install -r artifacts/phase1/KothaKey-android-debug.apk`. A physical phone can use all four offline typing modes immediately. The APK's default AI address points to the Android emulator's host. For AI on a phone, use the USB loopback build and port reversal or configure your HTTPS backend. [Android setup](../apps/android/README.md)
- **iPhone simulator.** Unzip the Phase 1 simulator build and run `xcrun simctl install booted KothaKeys.app` in the extracted folder. Add KothaKey in **Settings → General → Keyboard → Keyboards → Add New Keyboard**. In a text field, use the system globe to select it. Grant **Allow Full Access** to use networked AI from the extension. A physical iPhone needs a device build signed and provisioned by your Apple team. [iOS setup](../apps/ios/README.md)
- **Check offline phonetic typing.** Choose **Phonetic**, enter `Ami tomake bhalobashi` and press Space after each word. The host should contain **আমি তোমাকে ভালোবাসি**. Choose **Banglish** to keep the Roman letters.

## Exercising the development service

Both simulator builds use the local backend by default. Start it with `npm ci` and `npm run dev` in `backend/`. For a repeatable translation check, enter **How are you?**, choose English as the source and Bangla as the target, and generate. The mock returns **তুমি কেমন আছো?**. Check the host draft before choosing an apply action. The development provider recognizes three translation phrases, including **Hello** and **Thank you**. General translation needs the configured inference provider.

See [the API and design contract](../shared/contract.md), [offline typing checks](offline-typing.md) and [development service setup](../backend/README.md).


## Platform behavior

| Interaction | Android | iOS |
| --- | --- | --- |
| Typing and suggestions | A composing span tracks the current word in the host editor. | The extension shows a pending phonetic word in the candidate row. Space or an explicit candidate commits it to the host. |
| Rewrite target | A verified selection is replaceable. Android 14 or later also permits replacing a bounded whole draft when the host exposes its start, end and cursor. | An explicitly selected passage can be replaced. A cursor excerpt or separately supplied source has an Insert action. UIKit can expose only part of the document. |
| Voice | Hands off to an enabled system voice input method when available. | Offers a keyboard switch for Apple dictation. |
| AI and credentials | The mobile app connects to its configured API origin. | The extension also requires the system’s Allow Full Access permission for networking. |

Suggestions use local dictionaries, spelling corrections and the existing phonetic engine. These are word completions and transliteration choices. The keyboard does not run a sentence-level next-word model. Voice audio and transcription are handled by the chosen system keyboard.

## Typing performance

Median elapsed time from the same optimized host harness, with real progressive word prefixes. Initialization is measured separately. These timings exercise phonetic conversion and candidate generation on the developer Mac.

| Adapter and workload | Original | Redesigned |
| --- | ---: | ---: |
| Kotlin JVM, first 23 prefixes | 0.137 ms | 0.131 ms |
| Kotlin JVM, warm 1,150 prefixes | 5.653 ms | 5.558 ms |
| Swift, first 23 prefixes | 1.407 ms | 1.445 ms |
| Swift, warm 1,150 prefixes | 68.232 ms | 61.989 ms |

A second baseline run took 5.208 ms on Kotlin and 55.222 ms on Swift for the same warm workload. Both redesigned measurements lie between the two baseline measurements. The parser, dictionary and native Swift adapter retain the same source. The host measurements exclude screen rendering and physical touch latency. [Original evidence and benchmark harness](../artifacts/phase1/before/README.md) · [Redesigned Kotlin timing](../artifacts/phase1/checks/android-host-timing.txt) · [Redesigned Swift timing](../artifacts/phase1/checks/ios-host-timing.txt)

A separate Android 15 emulator fixture activated real keyboard keys through accessibility and timestamped the exact update in a native host EditText. Two runs per build gave 80 matched events each. Original median / p95 was 2.718 / 19.019 ms. Redesigned median / p95 was 2.457 / 6.893 ms. The redesigned maximum was 55.213 ms. Run variability and the isolated outlier limit conclusions about tail latency. [Android measurement method and raw samples](../artifacts/phase1/checks/android-ui-latency.md)

A separate iOS simulator measurement timed key insertion in the extension to the host text update. Across 50 warm events per build, the original median / p95 was 2.171 / 2.673 ms. The final redesign measured 2.493 / 3.836 ms. Reusing the suggestion strip controls reduced the earlier redesign’s 12.254 ms p95. These simulator timings cover software delivery to the host and exclude physical touch and display latency. [iOS timing method, individual trials and raw samples](../artifacts/phase1/checks/ios-ui-latency.json)

## Development verification

- Android targets API 36 and supports API 24 and later. The signed debug APK passed APK Signature Scheme v2 verification. The offline phonetic dictionary, grammar and notices matched their source files byte for byte. Android debug and unsigned release builds, ten JVM tests and all ten native emulator tests passed. Separate 320 dp, landscape and delayed-mock runs passed, including all four AI loading states. Lint reported 17 warnings and zero errors. See [Android device and test details](../apps/android/VERIFICATION.md).
- The generic iOS Release build compiled for arm64 with its embedded keyboard extension and iOS 17 minimum deployment target. The app and extension contain the original Avro parser and dictionary. All ten native iOS tests passed together on the packaged simulator configuration. [Xcode test summary](../artifacts/phase1/checks/ios-integrated-summary.json) · [Test cases and run history](../artifacts/phase1/checks/ios-ui-results.json). The iPhone 18 Pro simulator verified selected-passage replacement, cancellation, stale-draft protection, full-access-off phonetic typing, secure-input handoff, tone alternatives, request-specific instructions, dark appearance and landscape. On a direct WebKit focus change, Safari sometimes reported a text keyboard for a number field. The manual numeric shortcut works in that case, and the keypad appeared when Safari supplied numeric traits on re-entry. See [iOS setup and simulator workflows](../apps/ios/README.md).
- The TypeScript backend compiled. Twelve backend tests and eleven communication tests passed. All 8,472 Android Avro parity cases, 26 Swift fixtures and 150 curated entries passed. The local mock API returned the expected grammar correction, tone rewrite, Ask AI output and Bangla translation. An unsupported mock translation left the successful-generation counter unchanged. [Shared regression log](../artifacts/phase1/checks/shared-regression.log)

Grammar shows the suggested text and an explicit apply action. A word-by-word Show Changes view is still open. Physical keyboard feel, haptic strength and microphone transcription remain device checks. Live model translation and editorial quality were outside the local UI verification. The simulator ZIP and debug APK are development builds.

## Modified files

Paths below refer to changes from the original delivery revision `e47c0e9`. Build products and captures are under `artifacts/phase1/`.

- **apps/android** — [README.md](../apps/android/README.md), [VERIFICATION.md](../apps/android/VERIFICATION.md), [app/src/androidTest/java/com/kothakeys/keyboard/KeyboardJourneyTest.kt](../apps/android/app/src/androidTest/java/com/kothakeys/keyboard/KeyboardJourneyTest.kt), [app/src/liveSmoke/java/com/kothakeys/keyboard/RealProviderKeyboardTest.kt](../apps/android/app/src/liveSmoke/java/com/kothakeys/keyboard/RealProviderKeyboardTest.kt), [app/src/main/java/com/kothakeys/keyboard/AiPanel.kt](../apps/android/app/src/main/java/com/kothakeys/keyboard/AiPanel.kt), [app/src/main/java/com/kothakeys/keyboard/EditorAccess.kt](../apps/android/app/src/main/java/com/kothakeys/keyboard/EditorAccess.kt), [app/src/main/java/com/kothakeys/keyboard/KeyboardGrid.kt](../apps/android/app/src/main/java/com/kothakeys/keyboard/KeyboardGrid.kt), [app/src/main/java/com/kothakeys/keyboard/KeyboardViews.kt](../apps/android/app/src/main/java/com/kothakeys/keyboard/KeyboardViews.kt), [app/src/main/java/com/kothakeys/keyboard/KothaIme.kt](../apps/android/app/src/main/java/com/kothakeys/keyboard/KothaIme.kt), [app/src/main/java/com/kothakeys/keyboard/LocalSettings.kt](../apps/android/app/src/main/java/com/kothakeys/keyboard/LocalSettings.kt), [app/src/main/java/com/kothakeys/keyboard/MainActivity.kt](../apps/android/app/src/main/java/com/kothakeys/keyboard/MainActivity.kt), [app/src/main/java/com/kothakeys/keyboard/SettingsPage.kt](../apps/android/app/src/main/java/com/kothakeys/keyboard/SettingsPage.kt), [app/src/main/java/com/kothakeys/keyboard/ToolbarGlyph.kt](../apps/android/app/src/main/java/com/kothakeys/keyboard/ToolbarGlyph.kt), [app/src/main/java/com/kothakeys/keyboard/TypingModel.kt](../apps/android/app/src/main/java/com/kothakeys/keyboard/TypingModel.kt), [app/src/main/res/values/strings.xml](../apps/android/app/src/main/res/values/strings.xml).
- **apps/ios** — [App/App.swift](../apps/ios/App/App.swift), [App/Info.plist](../apps/ios/App/Info.plist), [Common/Service.swift](../apps/ios/Common/Service.swift), [Keyboard/Info.plist](../apps/ios/Keyboard/Info.plist), [Keyboard/KeyboardController.swift](../apps/ios/Keyboard/KeyboardController.swift), [Keyboard/KeyboardDesign.swift](../apps/ios/Keyboard/KeyboardDesign.swift), [KothaKeys.xcodeproj/project.pbxproj](../apps/ios/KothaKeys.xcodeproj/project.pbxproj), [README.md](../apps/ios/README.md), [Tests/CompanionTests.swift](../apps/ios/Tests/CompanionTests.swift), [Tests/SafariFixture.html](../apps/ios/Tests/SafariFixture.html), [Tests/delayedMock.mjs](../apps/ios/Tests/delayedMock.mjs).
- **intelligence** — [prompt.test.ts](../intelligence/prompt.test.ts), [prompt.ts](../intelligence/prompt.ts).
- **shared** — [contract.md](../shared/contract.md).
- **docs** — [delivery.md](../docs/delivery.md), [phase1-redesign.md](../docs/phase1-redesign.md).
- **root** — [README.md](../README.md).
