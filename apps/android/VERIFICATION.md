# Android verification

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
