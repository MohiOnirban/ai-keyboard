# KothaKey Phase 1.1

## Why AI failed on the released Android APK

The public Phase 1 APK (SHA-256 `12b1655517a439458eb1f25a806b3a68955777d703cca5dbd4076a09bfcdf0de`) contains `http://10.0.2.2:4000` in classes3.dex. This was the debug build's default backend origin. The Android emulator maps that address to its host Mac. A normal phone has no such mapping. The old client could therefore fail before creating a session, authenticating, sending a draft or reaching the provider. The manifest declares INTERNET. The configured address is a numeric IP using HTTP, so neither DNS nor TLS negotiation for a public backend takes place on this path. Toggling a VPN does not configure the missing KothaKey server.

The mobile client obtains its own session from Fastify via POST /v1/sessions. An explicit AI action posts to /v1/generate with its session bearer and a per-action UUID. The backend checks limits in PostgreSQL and sends the selected passage to OpenRouter over HTTPS. It validates the structured suggestion, settles usage and returns a preview. Replacing host text is a separate user action. The Supabase project URL serves the database integration and is not the mobile Fastify origin.

## Live service check

A synthetic HTTP request from the development Mac reached the backend, the TLS-verified hosted ledger and OpenRouter's GPT-6 Luna. The key endpoint returned 200 and the model appeared in the provider catalog. The backend returned 200 for health, 201 for session creation, 200 for grammar correction and 204 for revocation. Its reviewed output corrected the subject–verb agreement. The ledger recorded 305 input tokens, 20 output tokens and USD 0.000041. This check exercised the Mac's network route. An independent test on a physical handset in China is needed to establish that handset's DNS, TLS and regional reachability. [Local test evidence](../artifacts/phase11/checks/live.json)

## Offline prediction data

Both keyboards use the existing Avro resources for phonetic input. The offline prediction corpus comes from 100 existing authored example families and their variants. The build input contains 272 English, 263 Banglish and 280 Bangla sentences. Word frequencies supply completions and spelling candidates. Counts of adjacent words supply next-word choices. This small corpus favors the project's conversational vocabulary. Unseen vocabulary and contexts limit its coverage.

Regenerate the language-tagged TSV with `node scripts/prediction-corpus.mjs`. The shared regression script checks it against the curated examples. It contains no personal typing history or evaluation cases. [Source examples](../intelligence/examples.ts) · [Corpus generator](../scripts/prediction-corpus.mjs)

## Prediction coverage

Using the independent evaluation messages in intelligence/scenarios.json, each native predictor received the preceding reference word. For completion it also received the first three Unicode scalars of a word longer than three. The table counts occasions when the exact reference word appeared among the top three. Other sensible suggestions score as misses.

| Language | Android completion | iOS completion | Android next word | iOS next word |
| --- | ---: | ---: | ---: | ---: |
| English | 84 / 191 | 84 / 191 | 27 / 319 | 27 / 319 |
| Banglish | 92 / 186 | 92 / 186 | 15 / 262 | 19 / 262 |
| Bangla | 65 / 159 | 66 / 159 | 12 / 229 | 12 / 229 |

These results measure the default corpus before adding personal vocabulary. Word completion is more reliable than next-word prediction on these held-out messages.

## Use AI on an Android phone

Install the Phase 1.1 debug APK, open the KothaKey companion, and open **Settings → AI connection**. For untethered use, enter the HTTPS origin of the Fastify deployment that you trust, save it, and tap **Check connection**. This calls GET /health without sending a draft or creating a session. A successful check establishes reachability. Use a synthetic grammar request to check inference separately.

For USB development, enable USB debugging on the phone and approve the Mac. Start the backend on the Mac, connect the phone, then run the following command with its ADB serial. Choose **Use USB development address** in KothaKey and tap **Check connection**.

```sh
adb -s DEVICE_SERIAL reverse tcp:4000 tcp:4000
```

The default local service uses the mock provider. To use live inference through that route, the server operator configures the provider key, approved model, TLS database and spending limits on the Mac. A device configured for USB requires a working port reversal each time it reconnects. [Android setup](../apps/android/README.md) · [Server setup](../backend/README.md)

Android logs fixed request stages and failure categories, HTTP status where available, and elapsed time. To inspect a failed test, run `adb -s DEVICE_SERIAL logcat -d -s KothaKeyAI:W`. Diagnostic entries omit drafts, generated text, credential values and endpoint addresses. A TLS error requires fixing the certificate or endpoint configuration. A provider restriction indicates that the upstream route was declined and does not establish a country-wide block.

## Delivery and verification

[Android APK](../artifacts/phase11/KothaKey-android-debug.apk) · [iOS Simulator ZIP](../artifacts/phase11/KothaKey-iOS-simulator.zip) · [SHA-256 checksums](../artifacts/phase11/SHA256SUMS.txt) · [Screenshot gallery](../artifacts/phase11/screenshots.html)

Android 0.1.1 uses version code 2 and the same debug signer as Phase 1. The iOS app and extension use version 0.1.1 (2). The simulator archive is installable on an iPhone simulator. A physical iPhone needs Apple signing and provisioning. Each keyboard contains the same verified local corpus. No additional third-party runtime dependency was introduced.

The microphone remains visible in the regular typing toolbar, including at widths where other tools move into More. It hands off to an enabled Android voice input method or system dictation on iOS. Device availability and microphone permissions determine whether voice transcription can run. The language globe and long-press spacebar switch typing modes.

Both platforms support local personal words and user-controlled correction with undo. Ordinary typing uses no AI requests and never automatically uploads learned words. Protected input suppresses prediction. The separate suggestion row, verified host selection and direct-action consent use the existing native keyboard and generation service.

The shared regression suite passed 14 backend tests, 11 communication tests, 8,472 Avro parity comparisons, 26 Swift phonetic cases, 150 dictionary entries and 12 Swift predictor checks. [Shared checks](../artifacts/phase11/checks/shared.log)

The final Android emulator run passed all 11 ordinary cases. The two opt-in fixtures ran separately. One verified the USB health route and rejection of an untrusted certificate. The other exercised Grammar through consent, preview and replacement with the standard APK on API 35. All 15 Android JVM tests passed, and lint reported 22 warnings and zero errors. [Integrated Android run](../artifacts/phase11/checks/android/ordinary-api35-verified.log) · [Native USB mock run](../artifacts/phase11/checks/android/usb-mock.log)

The native Android live follow-up stopped before generation. The development Mac resolved the configured pooler hostname, but the route returned HTTP 400 to a PostgreSQL SSL negotiation. The backend kept certificate verification enabled. The earlier Mac live call and the later emulator mock call exercise different routes. The device test incurred no provider cost. A physical Android handset and a deployed public Fastify origin were unavailable for verification. [Device preflight evidence](../artifacts/phase11/checks/android/device-live-preflight.json)

All 12 iOS simulator workflows passed against the delayed local mock, including offline prediction with Full Access disabled, consent, fixed geometry, cancellation, secure Safari input and explicit text replacement. The packaged default simulator build uses the regular development origin. A generic iPhone arm64 Release build also compiled. [Xcode results](../artifacts/phase11/checks/ios-regression-summary.json)

An additional test on the final iOS source injected a tone-generation failure, edited the input, then generated again. The keyboard used the edited text and kept the host draft intact. [Targeted failure-recovery test](../artifacts/phase11/checks/ios-stale-tone-summary.json)

## Modified files

- [README.md](../README.md)
- [apps/android/README.md](../apps/android/README.md)
- [apps/android/VERIFICATION.md](../apps/android/VERIFICATION.md)
- [apps/android/app/build.gradle.kts](../apps/android/app/build.gradle.kts)
- [apps/android/app/src/androidTest/java/com/kothakeys/keyboard/AiConnectionJourneyTest.kt](../apps/android/app/src/androidTest/java/com/kothakeys/keyboard/AiConnectionJourneyTest.kt)
- [apps/android/app/src/androidTest/java/com/kothakeys/keyboard/KeyboardJourneyTest.kt](../apps/android/app/src/androidTest/java/com/kothakeys/keyboard/KeyboardJourneyTest.kt)
- [apps/android/app/src/main/java/com/kothakeys/keyboard/AiClient.kt](../apps/android/app/src/main/java/com/kothakeys/keyboard/AiClient.kt)
- [apps/android/app/src/main/java/com/kothakeys/keyboard/AiConnection.kt](../apps/android/app/src/main/java/com/kothakeys/keyboard/AiConnection.kt)
- [apps/android/app/src/main/java/com/kothakeys/keyboard/AiPanel.kt](../apps/android/app/src/main/java/com/kothakeys/keyboard/AiPanel.kt)
- [apps/android/app/src/main/java/com/kothakeys/keyboard/DictionarySettings.kt](../apps/android/app/src/main/java/com/kothakeys/keyboard/DictionarySettings.kt)
- [apps/android/app/src/main/java/com/kothakeys/keyboard/KeyboardGrid.kt](../apps/android/app/src/main/java/com/kothakeys/keyboard/KeyboardGrid.kt)
- [apps/android/app/src/main/java/com/kothakeys/keyboard/KeyboardViews.kt](../apps/android/app/src/main/java/com/kothakeys/keyboard/KeyboardViews.kt)
- [apps/android/app/src/main/java/com/kothakeys/keyboard/KothaIme.kt](../apps/android/app/src/main/java/com/kothakeys/keyboard/KothaIme.kt)
- [apps/android/app/src/main/java/com/kothakeys/keyboard/LocalSettings.kt](../apps/android/app/src/main/java/com/kothakeys/keyboard/LocalSettings.kt)
- [apps/android/app/src/main/java/com/kothakeys/keyboard/MainActivity.kt](../apps/android/app/src/main/java/com/kothakeys/keyboard/MainActivity.kt)
- [apps/android/app/src/main/java/com/kothakeys/keyboard/OfflinePrediction.kt](../apps/android/app/src/main/java/com/kothakeys/keyboard/OfflinePrediction.kt)
- [apps/android/app/src/main/java/com/kothakeys/keyboard/SettingsPage.kt](../apps/android/app/src/main/java/com/kothakeys/keyboard/SettingsPage.kt)
- [apps/android/app/src/main/java/com/kothakeys/keyboard/ToolbarGlyph.kt](../apps/android/app/src/main/java/com/kothakeys/keyboard/ToolbarGlyph.kt)
- [apps/android/app/src/main/java/com/kothakeys/keyboard/TypingModel.kt](../apps/android/app/src/main/java/com/kothakeys/keyboard/TypingModel.kt)
- [apps/android/app/src/test/java/com/kothakeys/keyboard/AiConnectionTest.kt](../apps/android/app/src/test/java/com/kothakeys/keyboard/AiConnectionTest.kt)
- [apps/android/app/src/test/java/com/kothakeys/keyboard/TypingModelTest.kt](../apps/android/app/src/test/java/com/kothakeys/keyboard/TypingModelTest.kt)
- [apps/ios/App/App.swift](../apps/ios/App/App.swift)
- [apps/ios/Common/Predictor.swift](../apps/ios/Common/Predictor.swift)
- [apps/ios/Common/Service.swift](../apps/ios/Common/Service.swift)
- [apps/ios/Keyboard/KeyboardController.swift](../apps/ios/Keyboard/KeyboardController.swift)
- [apps/ios/Keyboard/KeyboardDesign.swift](../apps/ios/Keyboard/KeyboardDesign.swift)
- [apps/ios/KothaKeys.xcodeproj/project.pbxproj](../apps/ios/KothaKeys.xcodeproj/project.pbxproj)
- [apps/ios/README.md](../apps/ios/README.md)
- [apps/ios/Tests/CompanionTests.swift](../apps/ios/Tests/CompanionTests.swift)
- [apps/ios/Tests/PredictionChecks.swift](../apps/ios/Tests/PredictionChecks.swift)
- [apps/ios/Tests/delayedMock.mjs](../apps/ios/Tests/delayedMock.mjs)
- [apps/ios/project.rb](../apps/ios/project.rb)
- [backend/README.md](../backend/README.md)
- [backend/src/app.ts](../backend/src/app.ts)
- [backend/src/ledger.ts](../backend/src/ledger.ts)
- [backend/src/provider.ts](../backend/src/provider.ts)
- [backend/test/app.test.ts](../backend/test/app.test.ts)
- [backend/test/provider.test.ts](../backend/test/provider.test.ts)
- [docs/phase11.md](../docs/phase11.md)
- [docs/privacy.md](../docs/privacy.md)
- [scripts/check.sh](../scripts/check.sh)
- [scripts/prediction-corpus.mjs](../scripts/prediction-corpus.mjs)
- [shared/contract.md](../shared/contract.md)
- [shared/predict.tsv](../shared/predict.tsv)
