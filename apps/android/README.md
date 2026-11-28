# KothaKey for Android

This Android project contains a native Kotlin input method and a Jetpack Compose companion. Four layouts work offline: English QWERTY, Bangla characters, Avro-style Bangla phonetic, and raw Banglish. KothaKey displays up to three offline completions, spelling suggestions or next words beneath the toolbar. The keyboard also supports grapheme deletion, two symbol pages, a dedicated keypad, emoji, clipboard tools, system appearance, and three height preferences.

## Build and install

Use JDK 17 or newer, Android SDK Platform 36, Build Tools 36.0.0 and Platform Tools. The checked-in wrapper uses Gradle 9.3.1. Initialize `ANDROID_HOME` to your Android SDK directory.

From this directory, build the APK and run the JVM tests.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Start KothaKey. Use **Enable keyboard** to open Android's input method settings, allow KothaKey, and return to the app. Use **Choose keyboard** to select it. Tap **Try typing here**. Hold the spacebar on the Latin letter layout or open **More options → Typing language** to choose a layout. Open **More options → Switch system keyboard** for Android's input method picker. In phonetic mode try `ami bhalo achi`. Use Shift in Bangla character mode to reveal additional letters.

The debug APK defaults to `http://10.0.2.2:4000`, the emulator alias for the host. Start the repository backend in mock mode from the monorepo root.

```sh
cd backend
AI_PROVIDER=mock npm run dev
```

### Connect a physical Android phone

- **Untethered phone.** Use the HTTPS origin of a KothaKey backend you control or trust. Its certificate must be trusted by Android and the phone must be able to reach it. In a debug APK, open **Settings → AI connection**, enter the origin in **HTTPS service address** and choose **Save address**. For a release APK, build with the same HTTPS origin using the property in [Release build](#release-build).
- **USB development.** Enable USB debugging, authorize this computer and check `adb devices`. With the mock backend running and the normal debug APK installed, run `adb reverse tcp:4000 tcp:4000`. Open **Settings → AI connection** and choose **Use USB development address**. Use synthetic text with the local development service.

Choose **Check connection** to reach the health route without a draft, credential or generation. **Refresh allowance** checks the anonymous session. **Use build address** clears a debug override. The default emulator address is unavailable on physical phones. If the matching HTTPS origin or USB development service is unreachable, offline typing still works. Failures may be inspected on the device through the `KothaKeyAI` logcat tag, which records only the stage, fixed diagnostic code, HTTP status and elapsed milliseconds.

## AI and editor compatibility

The toolbar opens **Fix Grammar**, **Improve & Tone**, **Ask AI** and **Translation**. On a narrow screen, **More options** makes hidden toolbar actions available. The toolbar stays visible above the suggestion row, with a microphone for Android voice input.

The four AI panels share the typing area and retain the selected keyboard height. Grammar checks a selection or a short draft. Improve & Tone runs the selected preset and keeps recent alternatives for the same input. Ask AI supports Rewrite, Reply and Express with optional writing preferences and custom instructions. Translation has separate source and target choices for Bangla, Banglish and English. Offline phonetic typing still converts typed Banglish into Bangla without sending it to AI.

Rewrite-style actions use an active selection or, on Android 14 and newer, a short draft ending at the cursor in a compatible editor. You can paste or type input explicitly when draft context is unavailable. A result becomes host text only after **Replace Text** or **Insert**. If the editor changes, you can copy the result or insert it without replacing existing text. Password, address and numeric fields suppress AI actions and suggestions. Android apps can withhold context or decline edits.

Grammar and tone choices ask for consent before their first request to the configured service. With consent, opening Grammar on an eligible draft or choosing a tone sends the displayed draft and writing preferences. Ask AI and Translation use their own generation buttons. The keyboard cancels the active client request when the user closes it, changes the input target or chooses another tone. A provider request already dispatched may still finish for billing. Session credentials are encrypted with an Android Keystore AES-GCM key and bound to the backend scheme, host, and effective port. Changing to a different backend origin starts a new installation session. Upgrading discards the former credential entry whose origin was unspecified. The companion can revoke the current session and clear local preferences separately.

Tap a suggestion to finish a word. In a supported editor, tap inside a previously typed word to see alternatives for the whole word. Tapping one preserves the surrounding punctuation and spaces. With English autocorrection enabled, Space or punctuation can fix a confident spelling mistake or add missing spaces. Backspace immediately reverses an automatic correction. The Undo chip also restores recent keyboard replacements, cuts and deletions when the editor can verify the affected range.

Glide across letter keys and release to insert a word. The strip shows available alternatives. Drag the spacebar horizontally to move the cursor. Settings controls glide, its trail, prediction, cursor suggestions, spacing, capitalization and on-device learning. The personal dictionary works even with learning off. Frequency and context data are bundled for offline use.

Open **Tools → Clipboard** to copy, cut or paste. To keep items, enable **Settings → Clipboard → Keep clips I save** and tap **Save clipboard item** in the keyboard. Pin, unpin or delete an item there. Unpinned clips expire after the chosen interval when the shelf is next accessed. Turning history off clears the shelf.

Open **Voice input** to choose English, Bangla or, on Android 14 and later, Mixed. Tap **Allow microphone** for the system permission request, return to the keyboard, then tap **Dictate** to start. Android supplies recognition, available languages and punctuation. KothaKey prefers Android's on-device recognizer when available. The panel identifies when the configured service may process audio online. Partial words appear in the panel. A final result is inserted only while the original editor and cursor range still match. **Start voice input** switches to an enabled system voice keyboard as an alternative.

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

On API 33 or newer, the optional `syntheticVoice` instrumented check exercises Android's on-device recognizer with a generated English fixture. Provide signed 16-bit little-endian PCM, mono, 16 kHz, containing “The parcel arrives tomorrow.” Push the fixture to `/sdcard/Android/data/com.kothakeys.keyboard/files/advanced/synthetic-voice.pcm` and add `-e syntheticVoice true` to the instrumentation invocation. The result file reports whether Android supplied a matching transcript or an error code. After testing, restore a previously denied microphone permission with `adb shell pm revoke com.kothakeys.keyboard android.permission.RECORD_AUDIO`.

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
