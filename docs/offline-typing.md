# Offline Bangla typing

The keyboard offers English QWERTY, raw Banglish, Bangla phonetic and direct Bangla characters. In phonetic mode, type “Ami tomake bhalobashi” and commit each word with Space. The tested result is “আমি তোমাকে ভালোবাসি”. A suggestion can replace the composing word. Typing the next word starts a new composition. Ordinary keystrokes and suggestions stay on the device.

## Engine decision

We evaluated Riti at e1fd5a809b0c3bf9c36e6f076ef6d558f9663fba. Its C API exposes pre-edit text, ranked candidates, backspace and selection. With an isolated Rust toolchain we ran 43 upstream tests, measured host performance, and cross-linked four mobile targets. Standalone programs returned আমি on the iOS simulator and Android emulator. Riti placed the requested spellings of the three example words at ranks 1, 2 and 2. Its larger dictionary offers suffix and spelling alternatives. Integrating it into the IMEs would require bindings and a decision about candidate-history persistence. Its core is MPL 2.0. [Full Riti evaluation, timing and licensing](riti-evaluation.md)

We chose jsAvroPhonetic for this implementation. The standalone parser has 289 ordered patterns and context-sensitive rules for vowel signs, consonant clusters, and case distinctions. It fits the existing native composition interfaces without a new Rust toolchain. iOS runs the original bundled parser in JavaScriptCore. Android indexes the same grammar by first character and interprets its rules in Kotlin. There is no web view, network dependency, remote code or provider call in conversion. Distribution includes source and the upstream MPL 1.1 notices. [Avro source](https://github.com/torifat/jsAvroPhonetic/tree/8d869e796c965d48e058c44e296e21233eb0d69e)

Avro alone produces “তমাকে” and “ভালবাশি” for the spellings in the example. The project dictionary supplies “তোমাকে” and “ভালোবাসি” along with common Bangladeshi texting variants. Free-form spellings remain ambiguous. Uppercase letters such as T and N can select a different Avro consonant. Keep the raw Banglish layout available for verbatim text.

## Reproducible checks

From the repository root run these on macOS with Xcode installed.

```sh
node shared/avro/verify.mjs
mkdir -p artifacts
xcrun swiftc -O apps/ios/Common/Phonetic.swift apps/ios/Tests/PhoneticChecks.swift -o artifacts/phonetic-checks
artifacts/phonetic-checks shared/phonetic.json
```

The Swift adapter passed 26 cases, 150 word overrides and the requested sentence. A Mac-hosted optimized run of 1,150 incremental renders took 177 ms total. The benchmark includes substrings, lookup and conversion. It excludes keyboard UI and does not establish physical-device latency. Android tests and simulator evidence are reported with the builds.

A differential check used the Kotlin class compiled by the Android debug build and the unmodified upstream parser as the oracle. For each of 289 patterns we combined prefixes (empty, a, k, o, r, exclamation mark) with suffixes (empty, e, i, sh, exclamation mark). All 8,472 unique input strings matched byte for byte. The shipped upstream parser and Android unit test each recompute the same SHA-256 fingerprint over sorted UTF-8 input/output pairs. Run `node shared/avro/verify.mjs` at the repository root and `./gradlew :app:testDebugUnitTest` in apps/android to repeat both checks. The Kotlin loop including comparison took 82 ms on the host JVM. This establishes parser parity on those inputs. Linguistic accuracy and suggestion ranking require separate examples.

Android uses the host InputConnection composing span. On iOS the keyboard displays the pending phonetic word in its own candidate toolbar because host editors can ignore third-party marked-text updates. Committing a candidate inserts it into the checked document target in a single operation.
