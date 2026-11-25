# Offline Bangla typing

The keyboard offers English QWERTY, raw Banglish, Bangla phonetic and direct Bangla characters. In phonetic mode, type “Ami tomake bhalobashi” and commit each word with Space. The visible result should be “আমি তোমাকে ভালোবাসি”. A suggestion can replace the composing word. Typing the next word starts a new composition. Ordinary keystrokes and suggestions stay on the device.

## Engine decision

We inspected Riti at e1fd5a809b0c3bf9c36e6f076ef6d558f9663fba. Its Rust static library and C API expose pre-edit text, candidates, backspace and selection. Its source implements dictionary-based prediction, suffix handling and autocorrection. These features are valuable for ambiguous Roman spellings. The Xcode project lists iOS device and simulator targets. Upstream CI exercises desktop targets. Android integration would need NDK binaries and a JNI adapter. The build requires Rust 1.75 or newer, which was absent from this Mac. The repository includes about 3.9 MiB of dictionary JSON and 136 KiB of autocorrections. Riti has benchmark cases for cold dictionary lookups. We did not run them or measure Riti on either mobile device. Those data sizes alone do not establish latency or memory use. This revision uses the upodesh FST for phonetic dictionary lookup and embeds the suffix and autocorrect JSON. The project is MPL 2.0. Distribution would also require a dependency and data-notice review. [Riti source](https://github.com/OpenBangla/riti/tree/e1fd5a809b0c3bf9c36e6f076ef6d558f9663fba)

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

The Swift adapter passed 19 cases, 148 word overrides and the requested sentence. A Mac-hosted optimized run of 1,150 incremental renders took 177 ms total. The benchmark includes substrings, lookup and conversion. It excludes keyboard UI and does not establish physical-device latency. Android tests and simulator evidence are reported with the builds.

A differential check used the Kotlin class compiled by the Android debug build and the unmodified upstream parser as the oracle. For each of 289 patterns we combined prefixes (empty, a, k, o, r, exclamation mark) with suffixes (empty, e, i, sh, exclamation mark). All 8,472 unique input strings matched byte for byte. The Kotlin loop including comparison took 82 ms on the host JVM. This establishes parser parity on those inputs. Linguistic accuracy and suggestion ranking require separate examples.
