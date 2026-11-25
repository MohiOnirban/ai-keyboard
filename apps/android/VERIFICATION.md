# Android verification

Android builds used JDK 21, Gradle 9.3.1, Android SDK Platform 36 and Build Tools 36.0.0. Device checks used an isolated Pixel 7 Android 35 Google APIs emulator. The only AI service used was the local mock backend.

## Build and packaging

| Check | Observed result |
|---|---|
| Debug APK | Build succeeded, APK installed on the API 35 emulator. |
| Debug signature | Android SDK `apksigner verify` passed. |
| Release APK | Unsigned release APK built successfully. |
| JVM tests | 6 passed. Covered the exact capitalized phrase, dictionary prefix completion, case-sensitive Avro rules, English suggestions, selection bounds and stale rewrite snapshots. |
| Lint | Passed with zero errors. Warnings include available toolchain updates and Android KTX suggestions. |
| Manifest and packaged data | Android network permission and a library-generated internal receiver permission. Debug APK contains 150 project-authored phonetic overrides and all required Avro sources and notices. |

Debug APK location `app/build/outputs/apk/debug/app-debug.apk`. Its SHA-256 is `8311fd9e06535b9cdb95189ab1ebc179914669267d43e8e66203f00fde2e28a6`. Release APK location `app/build/outputs/apk/release/app-release-unsigned.apk`.

## Google Messages

With Wi-Fi and cellular data disabled on the emulator, opened an unsent Google Messages draft to a reserved fictional number. Switched the installed Kotha Keys IME into phonetic mode and pressed the visible keys for `Ami tomake bhalobashi`. Independently read the Messages EditText accessibility value and confirmed `আমি তোমাকে ভালোবাসি`. The synthetic draft was cleared before restoring network access. The screenshot also showed the IME's bottom key row clearing the system navigation area.

## Connected checks

All 7 tests passed in one Android 35 emulator run. They covered sensitive editor classification, IME action flags, Bengali and emoji grapheme boundaries, offline keyboard layouts, the exact requested phrase and dictionary completion, mock rewrite preview, changed-draft safeguards, and revoked-session behavior.

```sh
"$HOME/Library/Android/sdk/platform-tools/adb" -s emulator-5554 shell am instrument -w \
  -e kothaMock true com.kothakeys.keyboard.test/androidx.test.runner.AndroidJUnitRunner
```

```text
com.kothakeys.keyboard.EditorAccessTest:...
com.kothakeys.keyboard.KeyboardJourneyTest:...
com.kothakeys.keyboard.SessionPrivacyTest:.

Time: 48.407

OK (7 tests)
```

For network tests, start the local mock backend and supply `kothaMock=true` as described in the [Android README](README.md).

## Remaining coverage

Physical Android devices and signed distribution builds remain pending. Third-party host verification is limited to the offline Google Messages draft described above. Messenger, WhatsApp, Instagram, Telegram, Gmail, older Android levels and real-device landscape behavior remain pending.
