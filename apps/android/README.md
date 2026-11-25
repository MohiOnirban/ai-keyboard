# Kotha Keys for Android

This Android project contains a native Kotlin input method and a Jetpack Compose companion. Four layouts work offline: English QWERTY, Bangla characters, Avro-style Bangla phonetic, and raw Banglish. The keyboard includes inline composing candidates, a small English correction list, grapheme deletion, symbols, emoji, cursor and clipboard controls, and light and dark palettes.

## Build and install

Use JDK 17 or newer, Android SDK Platform 36, Build Tools 36.0.0 and Platform Tools. The checked-in wrapper uses Gradle 9.3.1. Initialize `ANDROID_HOME` to your Android SDK directory.

From this directory, build the APK and run the JVM tests.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Start Kotha Keys. Use **Enable keyboard** to open Android's input method settings, allow Kotha Keys, and return to the app. Use **Choose keyboard** to select it. Tap **Try typing here** and use **文** to cycle layouts. The **◎** button opens Android's system keyboard picker. In phonetic mode try `ami bhalo achi`. Use Shift in Bangla character mode to reveal additional letters.

The debug APK defaults to `http://10.0.2.2:4000`, the emulator alias for the host. Start the repository backend in mock mode from the monorepo root.

```sh
cd backend
AI_PROVIDER=mock npm run dev
```

For a USB-connected Android device, build against loopback and reverse the port.

```sh
./gradlew :app:assembleDebug -PkothaBackendUrl=http://127.0.0.1:4000
adb reverse tcp:4000 tcp:4000
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Use a configured HTTPS service when testing away from the development machine.

## AI and editor compatibility

Tap **AI**, choose Rewrite, Reply, or Express, then tap **Generate**. Rewrite uses an active selection, or on Android 14 and newer a short draft ending at the cursor when the keyboard can reliably determine the editor’s selection and draft bounds. Reply and Express use text explicitly pasted or typed into the panel. Press **Replace** or **Insert** only after reviewing the result. If the editor has changed, the preview offers safe insertion and copying. Private fields and specialized address and numeric fields do not expose the assistant. Android applications can withhold editor context or refuse editing operations.

Pressing Generate is the network action. Closing the keyboard or changing focused fields clears its pending request and preview. A request that already reached the service can finish there. A successful provider response can count toward the installation's allowance even if the keyboard was closed. Session credentials are encrypted with an Android Keystore AES-GCM key and bound to the backend scheme, host, and effective port. Changing to a different backend origin starts a new installation session. Upgrading discards the former credential entry whose origin was unspecified. The companion can revoke the current session and clear local preferences separately.

## Validation

Use a dedicated emulator or test device with no private data. The Android instrumentation checks exercise sensitive editor classification, actions and grapheme boundaries.

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.kothaMock=true
```

The journey tests switch the dedicated emulator's default IME and temporarily disable Wi-Fi and mobile data. The preview and session tests expect a local mock backend on port 4000. To run instrumentation directly with Android platform tools, use the installed emulator serial.

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -e kothaMock true \
  com.kothakeys.keyboard.test/androidx.test.runner.AndroidJUnitRunner
```

See [Verification results](VERIFICATION.md) for device and workflow coverage.

## Opt-in real-provider smoke test

The regular Android test suite uses the mock backend. The optional `-PkothaLiveSmoke=true` build adds a separate `com.kothakeys.keyboard.livesmoke` package and a synthetic test. Use it on an isolated emulator with an explicitly authorized, budgeted local service. Set `kothaBackendUrl=http://127.0.0.1:4012` for the live APK and use `adb reverse tcp:4012 tcp:4012`. Verify that the mapped health route responds before the run.

The smoke method requires both `kothaLiveSmoke=true` and `kothaSmokeProvider=live` as instrumentation arguments and uses a single Generate action. It reads the synthetic host draft before acceptance and checks that Replace applies exactly the returned preview. The same scenario can be exercised against the mock by building with `http://10.0.2.2:4000` and passing `kothaSmokeProvider=mock`. Restore the regular mock IME and remove the temporary smoke package and 4012 mapping after a live run.

## Release build

Set an HTTPS base URL and build a release application bundle. A release build without the HTTPS property keeps typing available and disables the network client.

```sh
./gradlew :app:bundleRelease -PkothaBackendUrl=https://keyboard.example.org
```

Use Android Studio **Generate Signed Bundle / APK** with the authorized release or upload keystore to produce the signed AAB or APK. Keep keystore material and signing passwords outside the repository. The checked-in source does not contain signing credentials. Verify the signed APK with the SDK's `apksigner verify --print-certs` before distributing it.

## Source and licenses

The app packages the shared word overrides and the jsAvroPhonetic grammar, Kotlin adapter, original upstream script, license, and notice. See the [Avro notices](../../shared/avro/NOTICE.txt) and [MPL 1.1 license](../../shared/avro/LICENSE.txt).

FlorisBoard is Apache-2.0 and was considered as a native Android engine. The project uses Android's InputConnection for its original keyboard UI, avoiding a full keyboard fork. OpenBangla Keyboard is a GPL-3.0 desktop IME. OpenBangla Riti is an MPL-2.0 Rust Bengali input engine, which would add a native build and language bindings to each client. Poriborton is MIT-licensed ANSI/Unicode conversion code. The shipped phonetic parser uses the upstream jsAvroPhonetic grammar and has original common-spelling overrides. [FlorisBoard](https://github.com/florisboard/florisboard) · [OpenBangla Keyboard](https://github.com/OpenBangla/OpenBangla-Keyboard) · [Riti](https://github.com/OpenBangla/riti) · [Poriborton](https://github.com/OpenBangla/poriborton)
