# Android verification

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
