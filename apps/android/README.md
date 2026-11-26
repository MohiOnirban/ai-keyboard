# KothaKey for Android

This Android project contains a native Kotlin input method and a Jetpack Compose companion. Four layouts work offline: English QWERTY, Bangla characters, Avro-style Bangla phonetic, and raw Banglish. KothaKey displays up to three offline phonetic, raw Banglish or English candidates when available. The keyboard also supports grapheme deletion, two symbol pages, a dedicated keypad, emoji, clipboard tools, system appearance, and three height preferences.

## Build and install

Use JDK 17 or newer, Android SDK Platform 36, Build Tools 36.0.0 and Platform Tools. The checked-in wrapper uses Gradle 9.3.1. Initialize `ANDROID_HOME` to your Android SDK directory.

From this directory, build the APK and run the JVM tests.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Start KothaKey. Use **Enable keyboard** to open Android's input method settings, allow KothaKey, and return to the app. Use **Choose keyboard** to select it. Tap **Try typing here**. Tap **文** to cycle layouts or hold it to choose a layout. Open **More options → Switch system keyboard** for Android's input method picker. In phonetic mode try `ami bhalo achi`. Use Shift in Bangla character mode to reveal additional letters.

The debug APK defaults to `http://10.0.2.2:4000`, the emulator alias for the host. Start the repository backend in mock mode from the monorepo root.

```sh
cd backend
AI_PROVIDER=mock npm run dev
```

For a USB-connected Android device, enable USB debugging, authorize the Mac on the phone, and confirm the device appears in `adb devices`. Build against device loopback, run the backend in mock mode on the Mac, and reverse the port.

```sh
./gradlew :app:assembleDebug -PkothaBackendUrl=http://127.0.0.1:4000
adb reverse tcp:4000 tcp:4000
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The emulator APK uses the emulator-only host alias. Install the loopback APK on a USB-connected phone. For an untethered phone, build against a reachable HTTPS endpoint and enable KothaKey in Android keyboard settings.

## AI and editor compatibility

The toolbar opens **Fix Grammar**, **Improve & Tone**, **Ask AI** and **Translation**. On a narrow screen, **More options** makes hidden toolbar actions available. When typing candidates occupy the toolbar slot, tap **Show keyboard toolbar** to reach AI without changing the draft.

The four AI panels share the typing area and retain the selected keyboard height. Grammar checks a selection or a short draft. Improve & Tone provides tone presets and keeps recent alternatives from explicit generation. Ask AI supports Rewrite, Reply and Express with optional writing preferences and custom instructions. Translation has separate source and target choices for Bangla, Banglish and English. Offline phonetic typing still converts typed Banglish into Bangla without sending it to AI.

Rewrite-style actions use an active selection or, on Android 14 and newer, a short draft ending at the cursor in a compatible editor. You can paste or type input explicitly when draft context is unavailable. A result becomes host text only after **Replace Text** or **Insert**. If the editor changes, you can copy the result or insert it without replacing existing text. Password, address and numeric fields suppress the AI actions. Android apps can withhold context or decline edits.

Selecting a toolbar action opens the panel immediately. Only **Generate**, **Check grammar**, **Translate** and **Regenerate** send text to the service. Closing the keyboard or changing focused fields invalidates the preview and cancels the client request. A provider request already dispatched may still finish for billing. Session credentials are encrypted with an Android Keystore AES-GCM key and bound to the backend scheme, host, and effective port. Changing to a different backend origin starts a new installation session. Upgrading discards the former credential entry whose origin was unspecified. The companion can revoke the current session and clear local preferences separately.

Voice input uses an enabled Android voice input subtype where the system exposes one. If there is no voice subtype, the toolbar explains how to enable one and opens the Android keyboard picker.

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
