# KothaKey 0.3.0 typing and editing

The Android IME and iOS keyboard extension use the bundled word frequencies and conversation corpus to correct typing, segment joined words and rank up to three candidates. These operations work offline. The cloud AI actions still use the separate Fastify backend.

## Everyday editing

- Type normally in English, Banglish, Bangla or phonetic Bangla. English spelling alternatives update while typing. Space or punctuation can commit a confident correction or a split. Revisit an existing word by placing the caret inside it, at its end, or by selecting it. Tap a candidate to change the whole word. The original stays in place until a candidate is chosen.
- Completing a new word can add a space. Typing punctuation immediately afterward replaces that automatic space. Correcting a word inside existing text preserves the surrounding text. Double-space period has its own setting.
- The existing suggestion area offers Undo for supported KothaKey edits. The keyboard checks the editor, selection and surrounding text. Moving the caret or changing the draft can invalidate an Undo. For a just-applied automatic correction, immediate Backspace also restores the original.
- Swipe across letter keys and lift to commit a sufficiently close match. Alternatives appear inside the keyboard. English is the main gesture vocabulary. Roman Banglish and phonetic use a smaller authored vocabulary. Direct Bangla uses tap typing. The user can turn off gesture typing and independently hide the trail.
- Open the clipboard tool to copy or cut a selection, preview and insert clipboard text, or manage saved clips. History is opt-in. Retention, pinning, individual deletion and clearing are local. On iOS, clipboard access requires Full Access and the host can request paste permission.
- The Android microphone opens explicit dictation controls. The installed speech service determines supported languages and whether it processes audio on the device. On iPhone, use the system keyboard and its Dictation control. A custom iOS keyboard extension cannot record microphone input. [Android recognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer) · [Apple custom keyboard guide](https://developer.apple.com/library/archive/documentation/General/Conceptual/ExtensibilityPG/CustomKeyboard.html)

## Implementation and language resources

| Component | Implementation and distribution |
| --- | --- |
| English and Bangla vocabulary | 20,000 English and 6,000 Bangla frequency entries. The 458,016-byte adapted FrequencyWords asset ships with the upstream attribution, revision and CC BY-SA 4.0 license. |
| Sentence context | 1,042 language-tagged project-authored examples (60,593 bytes), including informal Banglish. A word’s predecessors, its accessible successor, and frequencies help distinguish possible edits. The training generator reads project examples and authored dialogue separately from the evaluations. |
| Spelling | Native candidate generation handles insertions, deletions, nearby replacements, repeated letters, transpositions and apostrophes. A bounded second edit extends coverage for longer words. Display and automatic edit policies use separate confidence rules. |
| Missing spaces | A bounded search considers sequences of dictionary words and ranks them by frequency and observed transitions. Name, identifier, language and personal-vocabulary guards limit unwanted edits. |
| Personalization | Explicit personal words and opt-in bounded local history affect ranking. Reversal can reduce future automatic changes. Learned word pairs can supply next-word candidates. Settings permit turning learning off and clearing it. |
| Touch and glide | Native key views translate touches into keyboard-relative coordinates. Tap alternatives are transient. Glide resamples a path by distance and compares it to compressed word shapes with endpoint, length, usage-frequency and context scoring. No server call, downloaded gesture library or key geometry change is required. |
| Bangla phonetic | The existing Avro implementation and KothaKey overrides remain bundled. Bengali characters and conjuncts are handled by the existing platform text integration. |

The native implementation uses the dictionaries and indexes already present in the app. AOSP LatinIME and HeliBoard were considered in the previous evaluation. Their Android integration and separate dictionary licensing would still leave a second integration on iPhone. The gesture implementation compares word shapes locally using both native toolchains. No third-party recognizer binary or new runtime library ships. [Prior engine review](phase2-research.md) · [FrequencyWords attribution](../shared/wordfreq-NOTICE.txt) · [Avro notice and MPL source](../shared/avro/NOTICE.txt) · [Published work on word gestures](https://research.google/pubs/the-word-gesture-keyboard-reimagining-keyboard-interaction-cacm-research-highlight/)

## Reproduce the comparisons

Run these commands from the repository root on macOS with the Android Gradle dependencies cached and Xcode selected. The first command freezes the vocabulary used to choose held-out typo cases so that expanding the dictionary cannot change the test population.

```sh
git show 25bc059:shared/wordfreq.tsv > /tmp/kotha-020-wordfreq.tsv
python3 scripts/correction-holdout.py /tmp/kotha-020-wordfreq.tsv > /tmp/kotha-typos.tsv
node scripts/typing-holdout.mjs > /tmp/kotha-dialogue.tsv
zsh scripts/measure-prediction.sh android /tmp/kotha-typos.tsv > /tmp/kotha-android.tsv
zsh scripts/measure-prediction.sh ios /tmp/kotha-typos.tsv > /tmp/kotha-ios.tsv
python3 scripts/summarize-typing.py /tmp/kotha-android.tsv /tmp/kotha-ios.tsv
zsh scripts/measure-prediction.sh android shared/tests/advanced-evaluation.tsv
zsh scripts/measure-prediction.sh ios /tmp/kotha-dialogue.tsv
zsh scripts/measure-prediction.sh ios-glide
zsh scripts/measure-prediction.sh android-glide
```

The 70-case regression set includes examples from the brief. The 712-query lexical evaluation contains 352 perturbed spellings and 360 original dictionary entries. The 1,346-query dialogue holdout is independent of the training generator. The gesture test uses rounded and perturbed paths for 160 randomly sampled English words and four rejection controls. It measures decoder behavior on synthetic traces. The equivalent-input Gboard exercise uses real taps in the same emulator editor. None of these establishes population accuracy or battery life on physical phones.

## Measured results

| Evaluation | Android 0.2 → 0.3 | iOS 0.2 → 0.3 |
| --- | --- | --- |
| Correct automatic edit on 352 unseen synthetic typos | 138 → 246 | 66 → 253 |
| Wrong automatic edit on those same 352 typos | 3 → 4 | 4 → 5 |
| Intended spelling anywhere in the three suggestions | 342 → 340 | 343 → 340 |
| Unchanged words among 360 original word-list entries | 359 → 359 | 359 → 359 |
| Unchanged tokens in 198 name, Banglish and technical control cases | 196 → 198 | 198 → 198 |
| Correct automatic segmentation in 16 joined-word cases | 0 → 14 | 0 → 14 |
| Exact next word in the top three, 810 dialogue positions | 60 → 79 | 90 → 103 |
| Correct top-three completion, 536 dialogue positions | 271 → 274 | 250 → 258 |
| Intended personal word among three suggestions, three explicit dictionary cases | 3 → 3 | 3 → 3 |
| Intended personal word ranked first, same three cases | 1 → 3 | 3 → 3 |

Both native glide decoders ranked 145/160 synthetic paths first and 155/160 in their top three. Both rejected all four negative controls. Desktop warm-query p95 for gesture ranking was 0.77 ms under the JVM and 0.05 ms for optimized Swift. These traces use controlled noise around intended key centers. Higher automatic correction coverage also produced additional wrong automatic edits. Names and informal dialects outside the tests can behave differently, and broad next-word coverage remains limited. [Full measurements](advanced-typing-metrics.json)

The Stage A Android emulator harness measured p95 of 10.70 ms for suggestions, 8.15 ms for boundary correction and 2.45 ms for cursor range detection plus ranking (120 warm queries per category). Dictionary construction took 2.05 s. An optimized Swift process running on the iPhone 18 Pro simulator measured 0.54 ms, 0.15 ms and 0.47 ms respectively. Startup took 508 ms, including 359 ms of CPU. Resident process memory rose from 25.1 MB before loading to 41.1 MB after the query suite. The separately recorded Swift glide index construction took 42 ms. These are algorithm measurements. Actual key-to-display latency includes host editor and UI scheduling. Sustained battery use and the 20 ms target on affordable physical phones remain unmeasured.

The Android comparison used the same 1080-pixel emulator, multiline practice field and seven touch sequences. KothaKey's Stage A APK committed “the,” “receive,” “tomorrow,” “how are you” and “I want to go home” on Space. It preserved “improvse” and “kortesi.” Gboard offered “improvise” in its strip, preserved six inputs on Space, and produced an accented final letter in the long joined-word trial. The sample records installed configurations and observed results. It does not establish general superiority. [Per-input outcomes](advanced-typing-metrics.json)


## Mobile verification

| Final native engine measurement | Android API 35 emulator | iPhone 18 Pro simulator, iOS 27.2 |
| --- | ---: | ---: |
| Warm suggestion p95 | 11.49 ms | 0.37 ms |
| Boundary correction p95 | 7.78 ms | 0.11 ms |
| Cursor range and ranking p95 | 9.97 ms | 0.33 ms |
| Glide ranking p95 | 0.175 ms | 0.006 ms |
| Cold vocabulary construction | 728 ms | 550 ms |
| Cold glide index construction | 631 ms | 33 ms |

Android ran 120 warm queries per category inside the instrumented emulator process, whose proportional memory usage was 87,865 KiB. The optimized iOS timing harness ran 700 queries per text category and 100 glide queries. Its resident memory rose from 25.1 MB to 40.0 MB. Android PSS and iOS RSS measure different things. Both runs include the native model and exclude screen rendering and physical touchscreen latency.

- **Android editing.** The keyboard accepted an emulator finger path as “world,” offered alternatives and restored the previous draft with Undo. Native tests exercised editing a word at the cursor, joined-word correction, clipboard insertion, pinning and removal. In Chrome, center taps for `gopd` offered “good” as the first spelling alternative. A tap at the lower-left edge of P offered “gold.” Tapping each alternative inserted its displayed spelling. [Center taps](advanced-screenshots/android-touch-center.png) · [Edge tap](advanced-screenshots/android-touch-edge.png) [Mid-gesture trail](advanced-screenshots/advanced-glide-trail.png) · [Glide result](advanced-screenshots/advanced-glide-result.png) · [Clipboard](advanced-screenshots/advanced-clipboard.png) · [Cursor alternatives](advanced-screenshots/advanced-cursor.png)
- **iPhone editing.** In the playground, choosing “the” in “say teh ok” preserved both neighboring words. Undo restored the original. A comma replaced an automatically inserted space. Center and edge taps for “gopd” produced “good” and “gold,” respectively. An actual simulator drag inserted “wet,” and Undo cleared it. Space split “howareyou,” and Backspace reversed the split. [Cursor and Undo](advanced-screenshots/ios-cursor-undo.png) · [Spacing](advanced-screenshots/spacing.png) · [Edge tap](advanced-screenshots/touch-alternative.png) · [Glide candidates](advanced-screenshots/ios-glide.png)
- **Voice.** The Android journey reached the installed system voice IME. A separate synthetic English audio test reached the on-device recognizer, which disconnected and returned Android error 5. The iPhone simulation successfully switched to Apple's keyboard through the supported handoff. Successful acoustic transcription in English, Bangla or mixed speech remains unverified. [iOS handoff](advanced-screenshots/voice-handoff.png)
- **Equivalent editor comparison.** Both Android keyboards received the same taps in the same field and display configuration. The screenshots show the KothaKey segmentation candidate and the Gboard spelling suggestion. [KothaKey](advanced-screenshots/android-segmentation.png) · [Gboard](advanced-screenshots/gboard-candidates.png)
- **Shared checks.** Fourteen backend tests and eleven communication-intelligence tests passed locally. The phonetic checks matched 8,472 upstream Android parity outputs and covered 26 additional cases and 150 word overrides. All 41 Swift predictor and learning checks passed.

Across successive iOS builds, eight simulator journeys passed and the Messages test skipped. The final Safari smart-delete regression also confirmed that a cut preserved both neighboring words and offered no ambiguous Undo. The 28 files in the simulator ZIP matched the built app and extension byte for byte. The generic iPhone Release build compiled unsigned.

The final Android Gradle run passed all 34 JVM tests and reported zero lint errors. On Android API 35, instrumentation discovered 23 tests, executed 21 and skipped two opt-in network fixtures. The signed APK is 10,160,680 bytes, up 153,795 bytes from the prior 10,006,885-byte APK.

In Chrome on the Android emulator, the multiline field changed `teh` to `the` on Space. The email and URL fields preserved the literal spelling. Safari on the iPhone simulator also preserved `teh` in focused email and URL fields and suppressed keyboard suggestions. On an existing multiline selection Safari accepted a replacement, restored the original with Undo, and dismissed stale Undo after a tap moved the caret. [Chrome multiline](advanced-screenshots/chrome-multiline.png) · [Safari multiline](advanced-screenshots/safari-multiline.png) · [Safari email](advanced-screenshots/safari-email.png)

Native iOS text editing verified exact Cut and Paste Undo, pinning a saved clip, deleting it and disabling history. In WebKit, selecting a word and cutting it can also remove adjacent whitespace while the keyboard still sees the earlier cursor position. KothaKey hides the Undo action on ambiguous whitespace boundaries. The original cut text remains available through the clipboard. [iPhone clipboard](advanced-screenshots/ios-clipboard.png)

The Android emulator’s Google Messages composer corrected `teh` to `the` on Space in an unsent draft. The test cleared the synthetic draft after reading it. The Android image had no app registered for creating notes. Apple Messages launched on the iPhone simulator, but its setup did not expose a compose field. Apple Notes was unavailable in that image. [Unsent Android draft](advanced-screenshots/android-messages.png)

### Installing the builds

The GitHub release carries an installable, debug-signed Android package for Android 7.0 or later, version code 4. Its certificate matches the previous Phase 1 APK. Open the download on the phone, install it, open KothaKey and follow the keyboard activation steps. Offline typing needs no service address. Cloud AI on a phone requires a reachable, trusted HTTPS KothaKey backend configured in Settings. The default emulator URL only works inside an emulator. [Android activation](../apps/android/README.md) · [Version 0.3.0](https://github.com/MohiOnirban/ai-keyboard/releases/tag/v0.3.0)

The iOS simulator uses the app with the bundled keyboard extension. Installing on a physical iPhone requires Apple development or distribution signing for both targets. [iOS setup and signing](../apps/ios/README.md)

[All changed files from the pre-implementation checkpoint](https://github.com/MohiOnirban/ai-keyboard/compare/25bc05994f7f9ac0c6714fdeff4a33eefc46e90b...v0.3.0)
