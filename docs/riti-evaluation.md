# Riti evaluation for offline Bangla typing

Keep the current jsAvroPhonetic adapters and Banglish word overrides for Kotha Keys. Riti can run on both mobile platforms and provides a larger dictionary, suffix suggestions, and remembered selections. Its default first candidate missed two words in the requested sentence and demoted several exact conjunct spellings. The current Avro adapters produce **আমি তোমাকে ভালোবাসি** as the first choices for **Ami tomake bhalobashi**.

## Accuracy

The comparison used [Riti e1fd5a8](https://github.com/OpenBangla/riti/tree/e1fd5a809b0c3bf9c36e6f076ef6d558f9663fba) in `avro_phonetic` mode with an empty temporary user directory, Unicode output, and the default exclusion of raw English candidates. Each word was entered through the key-event API with its original case and a fresh composing buffer. The Avro comparison used [the pinned upstream parser](https://github.com/torifat/jsAvroPhonetic/tree/8d869e796c965d48e058c44e296e21233eb0d69e) in macOS JavaScriptCore and the same [Kotlin parser](../shared/android/Avro.kt) and suggestion code used by Android, run on a host JVM.

These 25 cases cover the requested sentence, existing project regressions, and a few Banglish variants. A match requires the specified spelling. Forms such as ভালবাসি and ভালোবাসি can both represent the intended word. The sample measures behavior on these inputs, including ones already tuned by the project dictionary. It does not estimate accuracy over Bangladeshi texting generally.

| Configuration | Correct first choice | Correct in first three |
| --- | ---: | ---: |
| Riti with dictionary suggestions | 10/25 | 16/25 |
| Riti direct transliteration | 15/25 | 15/25 |
| Upstream jsAvroPhonetic alone | 15/25 | 15/25 |
| Kotha Keys Avro with 148 overrides, initial Swift and Kotlin checks | 23/25 | 23/25 |
| Kotha Keys Avro with 150 overrides, Swift and Kotlin regression | 25/25 | 25/25 |

The two direct transliterators gave identical outputs on the 25 inputs. The initial Kotha Keys misses were `bhalobasi` → ভালবাসি and `kamon` → কামন. Two project overrides supply ভালোবাসি and কেমন. The 25/25 result verifies those targeted corrections against the same cases.

| Typed | Expected | Riti dictionary first choice | Rank of expected |
| --- | --- | --- | ---: |
| `Ami` | আমি | আমি | 1 |
| `tomake` | তোমাকে | তমাকে | 2 |
| `bhalobashi` | ভালোবাসি | ভালবাসি | 2 |
| `ami` | আমি | আমি | 1 |
| `Bangladesh` | বাংলাদেশ | বাংলাদেশ | 1 |
| `krri` | কৃ | কররই | 5 |
| `kkhe` | ক্ষে | ক্ষয়ে | 6 |
| `ou` | ঔ | অউ | Absent |
| `chaa` | চা | ছা | 2 |
| `strii` | স্ত্রী | স্ত্রীই | Absent |
| `bhaat` | ভাত | 🌾 | 3 |
| `123` | ১২৩ | ১২৩ | 1 |
| `spondon` | স্পন্দন | স্পন্দন | 1 |
| `shobdo` | শব্দ | শব্দ | 1 |
| `groho` | গ্রহ | গ্রহ | 1 |
| `shilpo` | শিল্প | শিল্প | 1 |
| `montro` | মন্ত্র | মন্ত্র | 1 |
| `kSh` | ক্ষ | কষ | 5 |
| `bry` | ব্র্য | ব্রয় | 8 |
| `Ng` | ঙ | ঙ | 1 |
| `ShT` | ষ্ট | 💯 | 11 |
| `bhalobasi` | ভালোবাসি | ভালবাসি | 2 |
| `valobashi` | ভালোবাসি | ভালবাসি | 2 |
| `valo` | ভালো | ভাল | 5 |
| `kamon` | কেমন | ক্যামন | Absent |

Riti offers corrected or alternate spellings for more inputs than direct conversion. It assigns priority to autocorrections, uses edit distance for dictionary candidates, adds emoji candidates, and retains the phonetic spelling as a fallback. This ordering explains why dictionary mode can recover the intended spelling while making it harder to commit an exact conjunct with Space. The upstream release test suite passed all 43 tests. A separate event check confirmed that backspacing `spondon` restored the Roman buffer to `spondo` and ending the session cleared it. [Riti ranking](https://github.com/OpenBangla/riti/blob/e1fd5a809b0c3bf9c36e6f076ef6d558f9663fba/src/phonetic/suggestion.rs) · [Riti input method](https://github.com/OpenBangla/riti/blob/e1fd5a809b0c3bf9c36e6f076ef6d558f9663fba/src/phonetic/method.rs)

## Performance

An Apple Silicon Mac ran the pinned Riti tree in release mode using Rust 1.100.0 and a temporary Cargo and Rustup installation. Cargo resolved `okkhor 0.10.0`, `upodesh 0.4.0`, and `m17n-im 0.2.2`. The upstream build regenerated its Khipro Rust tables from the pinned submodule. The comparison used Swift `-O` and the project Kotlin classes with a warmed host JVM.

The workload entered each prefix of `spondon`, `shilpo`, `krri`, and `montro`. These are 23 events per pass. Each of seven trials created a fresh context, timed its first pass, then repeated the workload 50 times on that context. The Riti harness used `get_suggestion_for_key` and cleared the composing buffer between words. The Swift and Kotlin harnesses called the app adapters with each prefix. Times are the medians from one host observation, in milliseconds.

| Implementation | Context initialization | First 23 events | Next 1,150 events |
| --- | ---: | ---: | ---: |
| Riti with dictionary suggestions | 8.186 | 0.903 | 2.684 |
| Riti direct transliteration | 6.617 | 0.017 | 0.419 |
| Kotha Keys Swift and JavaScriptCore | 2.968 | 2.321 | 246.927 |
| Kotha Keys Kotlin on the JVM | 1.196 | 0.123 | 18.616 |

Riti was fast in this host workload. Repeated prefixes warm its dictionary cache, and the Swift and Kotlin adapters rebuild candidates from the Roman prefix. The Swift warm-pass median also varied across host runs, including 56.227 ms before the two overrides. These timings exclude mobile UI, real-keypress scheduling, C and JNI bridge overhead, packaging, and OS-level cold launch. Device memory use and typing latency remain unmeasured. Riti stores a dictionary entry for each new prefix in an uncapped context cache, so longer-session memory needs separate measurement. [Riti cache implementation](https://github.com/OpenBangla/riti/blob/e1fd5a809b0c3bf9c36e6f076ef6d558f9663fba/src/phonetic/suggestion.rs)

The repository's 3.9 MiB dictionary JSON is present in the source tree. For this revision, the phonetic search calls `upodesh`, which embeds a 581,151-byte word index and 775,287 bytes of preprocessed pattern JSON, among other data. Riti embeds its suffix and autocorrection tables. These files are inputs to the build and do not measure memory use. [Riti embedded tables](https://github.com/OpenBangla/riti/blob/e1fd5a809b0c3bf9c36e6f076ef6d558f9663fba/src/data.rs) · [upodesh 0.4.0 source](https://docs.rs/crate/upodesh/0.4.0/source/)

## Mobile compatibility

| Target | Verification |
| --- | --- |
| iOS arm64 device, deployment target 17.0 | Release archive compiled. A C consumer linked against the iPhoneOS SDK. |
| iOS arm64 simulator, deployment target 17.0 | Release archive compiled and a C consumer linked against the iPhoneSimulator SDK. The booted iOS 27.2 simulator ran the binary and returned আমি for `Ami`. |
| Android arm64-v8a, API 24 | Release archive compiled and the C consumer linked with NDK 28.2.13676358. The booted Android 15 ARM64 emulator ran the binary and returned আমি for `Ami`. |
| Android x86_64, API 24 | Release archive compiled and the C consumer linked with the same NDK. |

The C smoke program used the public header, enabled phonetic candidates, pressed Shift+A, M, and I, then read and freed the pre-edit string at index 0. Linked C executables were about 2.6 MiB on iOS and 3.2–3.4 MiB after stripping on Android. These are standalone build sizes. The production extension and APK footprint would depend on linking and packaging.

App integration would require Swift bindings for the C API and a JNI wrapper on Android. Both integrations would need to bind the engine to the existing composing-text lifecycle, validate candidate indices at the boundary, and map case-sensitive Roman key codes. Physical devices and the shipped IMEs were outside the Riti smoke test. Upstream declares Rust 1.75 as its minimum in the manifest. This evaluation verifies the selected newer toolchain and dependency resolution. [Riti manifest](https://github.com/OpenBangla/riti/blob/e1fd5a809b0c3bf9c36e6f076ef6d558f9663fba/Cargo.toml) · [C header](https://github.com/OpenBangla/riti/blob/e1fd5a809b0c3bf9c36e6f076ef6d558f9663fba/include/riti.h) · [Rust iOS targets](https://doc.rust-lang.org/rustc/platform-support/apple-ios.html) · [Rust Android targets](https://doc.rust-lang.org/rustc/platform-support/android.html)

## Licensing and local data

Riti declares MPL 2.0. That license permits static linking in a larger application. Distribution of the linked binary requires letting recipients obtain the covered source, including any modifications to covered files, and preserving the applicable notices. The pinned Khipro submodule carries MIT terms, including on the layout used to generate embedded Rust tables. The host Cargo dependency metadata includes permissive licenses such as MIT, Apache-2.0, and Unicode-3.0. A shipping review would need the exact mobile dependency graph, upstream dictionary and autocorrection provenance, and the corresponding source and notices. The current Avro integration separately carries the upstream MPL 1.1 text, parser source, grammar, and Kotlin adapter attribution. [Riti license](https://github.com/OpenBangla/riti/blob/e1fd5a809b0c3bf9c36e6f076ef6d558f9663fba/LICENSE) · [Mozilla MPL 2.0 FAQ](https://www.mozilla.org/en-US/MPL/2.0/FAQ/) · [Khipro license](https://github.com/rank-coder/khipro-m17n/blob/f4ef2771e3913a4994ffabdb589a65d3ec592623/LICENSE) · [Kotha Keys Avro notice](../shared/avro/NOTICE.txt)

Riti can write a user-selected candidate to `phonetic-candidate-selection.json`. That file holds Roman input and chosen Bengali spellings. Its mobile adapter would need an explicit decision about persisting selection history, a private app or extension container for any approved history, and handling for missing or unwritable storage. The current project adapters use static overrides and compute suggestions in memory. [Riti candidate persistence](https://github.com/OpenBangla/riti/blob/e1fd5a809b0c3bf9c36e6f076ef6d558f9663fba/src/phonetic/method.rs)

## Reproduce the host evaluation

Use a temporary checkout of the revision named above, initialize its pinned submodule and copy [the benchmark](../scripts/riti/kotha_eval.rs) into its examples directory. Create the empty temporary user-data directory referenced by the benchmark. Run `cargo test --release` and `cargo run --release --example kotha_eval` in that checkout. The report used the median of seven runs for each timing. The [C ABI smoke source](../scripts/riti/mobile-smoke.c) can be linked against a target-specific release static library with the corresponding SDK. Neither benchmark is linked into the shipping keyboards.
