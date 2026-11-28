# Android verification

## Phase 2

Version 0.2.0 (code 3) was built with Android SDK 36, minimum API 24. The signed debug APK was installed and its SHA-256 matched the staged download. The release APK is unsigned. The package includes both shared language files, the frequency data notices and license, and the Avro data and notices. [Debug APK](../../artifacts/phase2/KothaKey-android-debug.apk) · [Unsigned release APK](../../artifacts/phase2/KothaKey-android-release-unsigned.apk) · [Checksums](../../artifacts/phase2/checks/android/SHA256SUMS.txt) · [Signature](../../artifacts/phase2/checks/android/signature.txt) · [Packaged assets](../../artifacts/phase2/checks/android/packaged-language-assets.txt)

The final Gradle build passed 23 JVM tests. Lint reported 0 errors, 27 warnings, 1 hint. The API 35 run completed in 146.549 seconds with 15 passing tests and 2 opt-in skips. The emulator used a local mock service for all four AI workflows. A separate 320 dp run passed both tools navigation and URL geometry checks. [Build transcript](../../artifacts/phase2/checks/android/build-final-verified.log) · [Final instrumentation](../../artifacts/phase2/checks/android/ordinary-api35-final.log) · [320 dp run](../../artifacts/phase2/checks/android/android-320dp-final.log)

The emulator tests exercised the fixed-height tools dashboard, layout restoration, both symbol pages, numeric text entry, long-press numbers, cursor movement, clipboard paste, theme changes, the companion settings shortcut, and the host's contextual Done action. Offline tests exercised the requested English contraction, next-word suggestions, disabling and clearing learning, secure fields, URLs, the personal dictionary, Bangla phonetic conversion and grapheme deletion. Android's active Google voice service opened after tapping Start voice input. Physical haptics and English, Bangla and mixed-speech transcription remain unverified. [Contraction suggestion](../../artifacts/phase2/after/android/33-contraction-suggestion.png) · [Provider handoff](../../artifacts/phase2/after/android/32-android-voice-provider.png)

Gboard and KothaKey captures used the same 1,080 × 2,400 API 35 emulator at 420 dpi. KothaKey keeps 153 px row pitch across the four layouts, compared with 154–155 px on the installed Gboard. Dedicated numeric upper faces measure 137 px, compared with 139–140 px on Gboard. The previous KothaKey symbols and numeric layouts used 179 px row pitch. [Alphabet](../../artifacts/phase2/comparison/android/alphabet.png) · [Symbols](../../artifacts/phase2/comparison/android/symbols.png) · [Secondary symbols](../../artifacts/phase2/comparison/android/secondary-symbols.png) · [Numeric keypad](../../artifacts/phase2/comparison/android/numeric-keypad.png) · [Tools](../../artifacts/phase2/comparison/android/tools.png) · [Measurements](../../artifacts/phase2/checks/android/layout-measurements.txt)

| API 35 emulator measurement | Phase 1.1 geometry APK | Phase 2 APK |
| --- | ---: | ---: |
| Debug APK bytes | 9,788,167 | 10,006,885 |
| Companion cold start median, 3 force-stopped launches | 2,186 ms | 2,291 ms |
| Proportional set size after keyboard opens | 82,171 KiB | 120,751 KiB |
| Proportional set size after 54 verified synthetic taps | 96,499 KiB | 108,017 KiB |
| Process user and system CPU ticks for those taps | 798 | 730 |
| ADB tap sequence elapsed time | 9.51 s | 8.64 s |

The whole-app sample includes software emulator rendering, the companion app and the ADB tap driver. It measures a small synthetic sequence, and its elapsed time includes ADB command overhead. In-process prediction timings were measured separately. Physical-device latency, CPU and battery impact remain open. [Whole-app transcript](../../artifacts/phase2/checks/android/whole-app-benchmark.log) · [Before samples](../../artifacts/phase2/checks/android/compared-before-companion-three-cold-starts.txt) · [After samples](../../artifacts/phase2/checks/android/compared-after-companion-three-cold-starts.txt) · [Prediction measurements](../../docs/phase2.md)

## Phase 1.1

The signed debug APK is version 0.1.1 (version code 2). The isolated API 35 emulator upgraded in place from Phase 1 and ran the final APK. The Android debug signing certificate matches Phase 1. Its SHA-256 is `2b55cb12570676e72744a335ccc834091bce250e774bdb916fa69be259f27d1a`. The compiled release APK is unsigned. [Download the debug APK](../../artifacts/phase11/KothaKey-android-debug.apk) · [Signature](../../artifacts/phase11/checks/android/signature.log)

The Gradle run built the debug, release and instrumentation APKs. All 15 JVM tests passed, and Android lint reported zero errors and 22 warnings. The debug package contains the shared prediction corpus and existing Avro assets. [Build transcript](../../artifacts/phase11/checks/android/build-final.log)

The final API 35 instrumentation run completed in 98.096 seconds with 11 passing ordinary tests, two opt-in skips and zero failures. It used the local mock backend and a 1,080 × 2,400 display. [Instrumentation results](../../artifacts/phase11/checks/android/ordinary-api35-verified.log)

The offline keyboard checks cover the persistent toolbar and separate candidate row, English correction and both undo actions, disabling autocorrection, next-word selection, personal dictionary updates, the phonetic Bangla phrase, Bengali and emoji graphemes, and suggestions and AI suppression in a password field. The microphone opened Android’s voice input panel. Physical handset dictation remains unverified. [Suggestion row](../../artifacts/phase11/after/android/25-suggestions-toolbar.png) · [Voice panel](../../artifacts/phase11/after/android/26-voice.png) · [Password field](../../artifacts/phase11/after/android/27-private-field.png)

Grammar displayed first-use consent, accepted a decline without replacing the draft, generated after approval and opened directly for another eligible draft. Selecting a tone generated without a second button. Ask AI and Translation used their explicit buttons. The tests checked draft preservation through preview, explicit replacement and safe handling of changed selections. [Grammar consent](../../artifacts/phase11/after/android/14-fix-grammar-consent.png) · [Grammar preview](../../artifacts/phase11/after/android/15-fix-grammar-preview.png) · [Tone preview](../../artifacts/phase11/after/android/17-improve-tone-preview.png)

## Phase 1 keyboard redesign

The Phase 1 Android debug package is signed with the SDK development key and targets the emulator host mock at `10.0.2.2:4000`. Its SHA-256 is `12b1655517a439458eb1f25a806b3a68955777d703cca5dbd4076a09bfcdf0de`. The unsigned release APK built with SHA-256 `2e95a085535d739955289e0dcfda50f91a3f63fc62638d2650d47c46618af0dc`. [Debug APK](../../artifacts/phase1/KothaKey-android-debug.apk)

The native keyboard uses a fixed-height header and content area. The ten-unit alphabet rows, specialized number layouts, theme, and height preference share that area with Grammar, Improve & Tone, Ask AI, Translation, Emoji and Clipboard. Candidate suggestions temporarily occupy the toolbar slot. Their toolbar button restores direct access to AI while composing.

On the isolated API 35 emulator, the Phase 1 suite checked matching letter widths, a half-key stagger, wider side keys, zero centered in the keypad, unchanged typing-area bounds across modes, light and dark appearance, the OpenRouter-independent mock flows, stale-draft replacement, draft preservation, credential revocation, and offline Bangla phonetic conversion and Bengali and emoji deletion. The final standard-size suite used the 1,080 × 2,400 emulator display. All ten scenarios passed together in 46.536 seconds on the final signed APK, after the test waited for the keyboard-close animation during the height comparison. [Complete native run](../../artifacts/phase1/checks/android/ordinary-final.log)

The keyboard geometry and mode journey separately passed at 840 × 1,920 (320 dp wide) and at 2,400 × 1,080 in landscape. Those viewport checks used a plain Android EditText as the host. The JVM suite passed all 10 tests. Android lint passed with zero errors and 17 warnings. [Run transcripts](../../artifacts/phase1/checks/android)

Loading evidence used a separately built debug APK connected to a local forwarding proxy with a four-second delay. The proxy forwarded to the same mock service. That APK and its reverse port mapping were removed from the emulator after capture. [Before screenshots](../../artifacts/phase1/before), [After screenshots](../../artifacts/phase1/after/android)

Voice input delegates to an enabled Android voice input subtype where available. Actual dictation depends on that provider and requires device verification. The direct Bangla layout uses the existing character pages and grapheme handling. Prediction suggestions are available for the supported English, phonetic and raw Banglish dictionaries. Phase 1 verification used an emulator; physical-device checks remain open.

## Pre-redesign baseline

Android builds used JDK 21, Gradle 9.3.1, Android SDK Platform 36 and Build Tools 36.0.0. Device checks used an isolated Pixel 7 Android 35 Google APIs emulator. The ordinary Android suite used the local mock backend. One separately authorized synthetic live smoke used an isolated local listener.

## Build and packaging

| Check | Observed result |
|---|---|
| Debug APK | Build succeeded, APK installed on the API 35 emulator. |
| Debug signature | Android SDK `apksigner verify` passed. |
| Release APK | Unsigned release APK built successfully. |
| JVM tests | 10 passed. Covered the exact capitalized phrase, dictionary prefix completion, case-sensitive Avro rules, 8,472-case Avro parity, English suggestions, selection bounds, stale rewrite snapshots and credential origin separation. |
| Lint | Passed with zero errors and 18 warnings. Warnings include available toolchain updates and Android KTX suggestions. |
| Manifest and packaged data | Android network permission and a library-generated internal receiver permission. Debug APK contains 150 project-authored phonetic overrides and all required Avro sources and notices. |

Debug APK location `app/build/outputs/apk/debug/app-debug.apk`. Its SHA-256 is `9732674491c402c5725a3f6f63212d503bb2f14ae1fc26559c9444a754fbaa90`. Release APK location `app/build/outputs/apk/release/app-release-unsigned.apk`, with SHA-256 `c18b383bcd9d28809266aef066be042de17df27ba71a74cecbe16df602d65de5`.

## Google Messages

With Wi-Fi and cellular data disabled on the emulator, opened an unsent Google Messages draft to a reserved fictional number. Switched the installed Kotha Keys IME into phonetic mode and pressed the visible keys for `Ami tomake bhalobashi`. Independently read the Messages EditText accessibility value and confirmed `আমি তোমাকে ভালোবাসি`. The synthetic draft was cleared before restoring network access. The screenshot also showed the IME's bottom key row clearing the system navigation area.

## Connected checks

The seven Android 35 instrumentation tests cover private editor classification, action flags, Bengali and emoji grapheme boundaries, offline keyboard layouts, the requested capitalized phrase and dictionary completion, mock rewrite preview, changed-draft safeguards, and revoked-session handling. Physical letter and Shift taps remain in the typing flows. Layout tests wait for stable window and key bounds. Toolbar actions use standard accessibility clicks and verify that the editor selection has changed before deleting a selected draft.

The independent rerun observed a stale touch landing on the `z` key during a layout transition, which inserted `য`. After synchronizing the touch checks, full runs from preselected English and Bangla passed seven tests each in 53.584s and 68.468s. Their transcripts are under `build/verification/layout-English.log` and `build/verification/layout-Bangla.log`. The final harness also focuses the fixture editor directly, uses semantic actions for layouts and toolbar controls, and waits for fresh stable bounds before each physical phrase key.

```sh
"$HOME/Library/Android/sdk/platform-tools/adb" -s emulator-5554 shell am instrument -w \
  -e kothaMock true -e startingLayout Bangla \
  com.kothakeys.keyboard.test/androidx.test.runner.AndroidJUnitRunner
```

### Final mock runs

| Starting layout | Result | Runner time | Transcript |
|---|---|---:|---|
| English | 7 passed | 36.869s | `build/verification/final-English.log` |
| Bangla | 7 passed | 41.468s | `build/verification/final-Bangla.log` |

Start the local mock backend and supply `kothaMock=true` for the network-facing mock checks. See the [Android README](README.md) for the standard build and test commands.

## Session origin coverage

Equivalent scheme, host and effective port combinations share the same credential slot. The JVM regressions cover changes in all three origin components, default-port normalization and legacy slot separation. Android instrumentation decrypted the stored credential with the correct origin binding, revoked it on the local mock service and observed a 401 without automatic draft resubmission.

## Opt-in real-provider smoke

The isolated package used the same 55-second per-read request timeout as the distributed Android client. The emulator confirmed the 4012 listener health through the temporary ADB reverse mapping. The identical mock preflight passed in 17.747s. The authorized live run passed one test in 52.874s and checked that the synthetic draft stayed unchanged through preview and that explicit Replace applied the exact preview text. The opt-in app and its test package were removed, the 4012 mapping was removed, and the standard 4000 mock IME was restored.

The first direct Android smoke attempt ended without a preview. The confirmed loopback route and longer bounded read timeout supported the completed live test. The health, mock preflight and live transcripts are under `build/verification/`.

## Remaining coverage

Physical Android devices and signed distribution builds remain pending. Third-party host verification is limited to the offline Google Messages draft described above. Messenger, WhatsApp, Instagram, Telegram, Gmail, older Android levels and real-device landscape behavior remain pending.
