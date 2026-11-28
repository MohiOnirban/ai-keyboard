# KothaKey Phase 2

## Keyboard layout

The supplied symbols and numeric screenshot normalizes to about 85 pixels per row at 600 pixels of keyboard width, matching the established alphabet pitch. The updated symbols page has the digit row, the requested punctuation order, a secondary page and a numeric shortcut. The manual keypad uses three aligned digit columns. Four arithmetic keys divide the height of the left rail, while percentage, Space and Backspace share the right side. Zero aligns with the middle column. All keys send text or editor actions.

The four-square tools button starts the toolbar and the microphone is the last visible action in supported fields. The dashboard uses two columns and opens in the keyboard area. Its tools use the existing theme, clipboard, emoji, typing language, cursor controls, layout, voice and settings paths. The closed keyboard returns to its typing layer. The separate three-position prediction strip uses flat text and hairline dividers.

## Offline engine and data

The two native rankers share the same offline assets. The frequency file contains 10,000 English and 6,000 Bangla entries, occupying 300,300 bytes uncompressed. The context file contains 949 authored training sentences, occupying 55,234 bytes, with 335 English, 310 Bangla and 304 Banglish sentences. Avro and its curated overrides supply phonetic conversion. There is no added runtime library or remote request in ordinary typing. [Research, licensing and voice constraints](phase2-research.md) · [Dictionary attribution](../shared/wordfreq-NOTICE.txt) · [CC BY-SA 4.0 license](../shared/wordfreq-LICENSE.txt) · [Authored dialogue](../intelligence/typing-dialogue.tsv)

Both platforms combine lexical frequency, local context, edit costs and personal vocabulary. The contextual model uses adjacent words and backs off when two-word context is sparse. Suggested edits can include omitted apostrophes, inserted or removed characters, transpositions, neighboring keys and missing spaces. Boundary autocorrection applies a separate conservative English confidence rule. Predictions and loading run off the typing thread. Results are discarded when newer input or a mode change makes them stale. The established draft-verification logic still guards explicit AI application.

Personalized learning starts off. It uses bounded local word observations, accepted suggestions and rejected automatic edits when enabled. A privacy control clears learned data separately from the manually edited dictionary. Ordinary typing is kept local, and password and other recognized sensitive fields are excluded. Mixed English and Banglish vocabularies still depend on the selected mode. Dialect examples broaden coverage without establishing comprehensive regional accuracy.

## Evaluation reproduction

The fixed 66-case evaluation exercises spelling, context, completion, personal words and deliberately unchanged text. Several requested context demonstrations are also represented in ordinary training dialogue, so those fixed examples are a regression set. The independent holdout comes from the existing evaluation messages and contains 536 completion queries and 810 next-word queries. Match counts use the exact reference word, ignoring case outside preservation checks. Other reasonable predictions count as misses. The preservation set is small and is not a population estimate of accidental edits.

```sh
node scripts/typing-holdout.mjs > /tmp/kotha-typing-holdout.tsv
zsh scripts/measure-prediction.sh ios
zsh scripts/measure-prediction.sh android
zsh scripts/measure-prediction.sh ios /tmp/kotha-typing-holdout.tsv
zsh scripts/measure-prediction.sh android /tmp/kotha-typing-holdout.tsv
```

## Before and after

| Category | Android top 1 | Android top 3 | iOS top 1 | iOS top 3 |
| --- | ---: | ---: | ---: | ---: |
| Spelling regression | 0 → 13 / 20 | 7 → 19 / 20 | 6 → 19 / 20 | 9 → 20 / 20 |
| Context regression | 2 → 12 / 15 | 3 → 14 / 15 | 2 → 11 / 15 | 2 → 14 / 15 |
| Independent completion | 0 → 14 / 536 | 228 → 271 / 536 | 200 → 202 / 536 | 242 → 250 / 536 |
| Independent next word | 32 → 38 / 810 | 53 → 60 / 810 | 33 → 48 / 810 | 58 → 90 / 810 |

On the 20 typo cases, correct automatic replacements rose from 1 to 13 on Android and from 1 to 9 on iOS. The remaining cases generally need a tap on a suggestion. On 19 intentional inputs, unwanted automatic changes fell from one to zero on Android and stayed at zero on iOS. These are fixtures, with coverage deliberately weighted toward the reported bugs.

| Host measurement | Android before → after | iOS before → after |
| --- | ---: | ---: |
| Warm prediction p95, ms | 0.241 → 0.130 | 0.203 → 0.119 |
| Model construction, ms | 111 → 228 | 12 → 287 |
| Peak runner RSS, MiB | 84.44 → 104.45 | 7.48 → 11.05 |
| Runner user CPU, s | 0.82 → 1.00 | 0.14 → 0.31 |

Host timing covers 1,584 warm ranker queries per build after eight discarded passes. Construction and process memory include library and runtime overhead and vary with machine load. The UI starts typing while indexing runs off the main thread. This benchmark does not measure touchscreen-to-host delay. Battery impact remains unmeasured.

[Native benchmark results](../artifacts/phase2/checks/prediction/summary.json) · [iOS simulator-native model timing](../artifacts/phase2/checks/ios-simulator-model.json)

## Android verification

The signed Android 0.2.0 debug APK passed 23 JVM tests, 15 ordinary API 35 emulator workflows and two additional 320 dp workflows. Two opt-in connectivity fixtures were skipped in the ordinary run. The emulator switched successfully to Google voice typing. Speech accuracy remains unmeasured. [Android verification and Gboard comparisons](../apps/android/VERIFICATION.md)

At 1,080 pixels wide and 420 dpi, the revised alphabet and symbols row pitch measured 153 pixels. The installed Gboard measured 154–155 pixels. The old KothaKey symbols and numeric views stretched to 179 pixels. Upper number-pad faces now measure 137 pixels tall against 139–140 for Gboard. [Raw measurements](../artifacts/phase2/checks/android/layout-measurements.txt) · [Numeric comparison](../artifacts/phase2/comparison/android/numeric-keypad.png)

The debug APK grew from 9,788,167 to 10,006,885 bytes. Median companion launch was 2,186 → 2,291 ms across three emulator launches. After 54 taps, emulator process PSS rose from 96,499 to 108,017 KiB. The same snapshots recorded 798 → 730 CPU ticks, and the ADB sequence took 9.51 → 8.64 s. Emulator timing and memory vary with system load. [Android whole-app run](../artifacts/phase2/checks/android/whole-app-benchmark.log)

## Delivery

[Android debug APK](../artifacts/phase2/KothaKey-android-debug.apk) · [iOS simulator ZIP](../artifacts/phase2/KothaKey-iOS-simulator.zip) · [SHA-256 checksums](../artifacts/phase2/SHA256SUMS.txt) · [Screenshot gallery](../artifacts/phase2/screenshots.html)

iOS follows the separate iPhone refinement. Both deliverables use the same offline assets. The Android APK is a debug build that installs over the earlier APK using the same signer. A physical iPhone build needs Apple signing and provisioning. [iOS comparison and verification](ios-keyboard-refinement.md)

All four AI features use the existing service. The emulator and simulator AI checks ran against the local mock. For AI away from the development machine, set the reachable HTTPS address of your deployed KothaKey backend. The old APK's emulator address is the verified cause of the earlier phone failure. Public HTTPS deployment and live device generation remain unverified. [Backend setup](../backend/README.md)

## Workspace file inventory

The previous Phase 1.1 work and this refinement share one uncommitted worktree. The following list includes both.

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
- [apps/android/app/src/main/java/com/kothakeys/keyboard/TypingHistory.kt](../apps/android/app/src/main/java/com/kothakeys/keyboard/TypingHistory.kt)
- [apps/android/app/src/main/java/com/kothakeys/keyboard/TypingModel.kt](../apps/android/app/src/main/java/com/kothakeys/keyboard/TypingModel.kt)
- [apps/android/app/src/test/java/com/kothakeys/keyboard/AiConnectionTest.kt](../apps/android/app/src/test/java/com/kothakeys/keyboard/AiConnectionTest.kt)
- [apps/android/app/src/test/java/com/kothakeys/keyboard/OfflinePredictionTest.kt](../apps/android/app/src/test/java/com/kothakeys/keyboard/OfflinePredictionTest.kt)
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
- [docs/ios-keyboard-refinement.md](../docs/ios-keyboard-refinement.md)
- [docs/phase11.md](../docs/phase11.md)
- [docs/phase2-research.md](../docs/phase2-research.md)
- [docs/phase2.md](../docs/phase2.md)
- [docs/privacy.md](../docs/privacy.md)
- [intelligence/typing-dialogue.tsv](../intelligence/typing-dialogue.tsv)
- [scripts/check.sh](../scripts/check.sh)
- [scripts/measure-prediction.sh](../scripts/measure-prediction.sh)
- [scripts/prediction-corpus.mjs](../scripts/prediction-corpus.mjs)
- [scripts/prediction-frequencies.py](../scripts/prediction-frequencies.py)
- [scripts/typing-holdout.mjs](../scripts/typing-holdout.mjs)
- [shared/contract.md](../shared/contract.md)
- [shared/predict.tsv](../shared/predict.tsv)
- [shared/tests/prediction/Measure.kt](../shared/tests/prediction/Measure.kt)
- [shared/tests/prediction/Measure.swift](../shared/tests/prediction/Measure.swift)
- [shared/tests/typing-evaluation.tsv](../shared/tests/typing-evaluation.tsv)
- [shared/wordfreq-LICENSE.txt](../shared/wordfreq-LICENSE.txt)
- [shared/wordfreq-NOTICE.txt](../shared/wordfreq-NOTICE.txt)
- [shared/wordfreq.tsv](../shared/wordfreq.tsv)
