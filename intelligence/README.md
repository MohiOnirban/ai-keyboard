# Communication engine

The backend calls `buildPrompt(request)` once per explicit generation. It receives a system instruction string and user-role JSON. Fixed directions specify the mode and style. The full request, including free-form preferences, stays in the user role. Retrieval searches the authored corpus by output language and mode, then scores style, source-word overlap and relationship. A single fictional example illustrates the technique. All three modes support the language, relationship, intensity and personalization fields in the shared API contract.

The deterministic mock supplies suggestions for development without provider access. It can replay a matching authored example, demonstrate all 18 styles for a simple expression of affection, answer common incoming messages, and make limited wording and perspective changes. Its fallback for an unfamiliar draft can preserve the original wording. Free-form personalization, translation and open-ended creative generation depend on the live provider. The engine uses no network, storage, analytics, or previous request history.

## Corpus

`examples.ts` contains 100 fictional source messages, each paired with five separately written responses in different labeled styles. That produces 500 authored input/output pairs. The sources include 34 Bangla, 33 Latin-script Banglish and 33 English messages. Together they cover all three modes, 18 styles and seven relationships. The custom examples show a preference for short everyday language. These original examples were written for this project without private chats, scraped social posts or attributed quotations.

`scenarios.json` contains 114 separate evaluation requests with a review criterion for each. There are 36 source situations per language plus six cross-language requests. Cases exercise the three intensities, all styles, pronouns and forms of address, audience-appropriate humor, negation, permission, financial amounts, ambiguous dates and optional personalization. The application imports only the example corpus. The evaluation set has been used during development and is suitable for regression tracking.

## Run locally

From the repository root with Node.js 22.18 or newer:

```sh
node --test intelligence/prompt.test.ts
node intelligence/evaluate.ts
```

The evaluation JSON reports required concepts with acceptable variants, prohibited wording, output validity, script, recognizable planning phrases, repeated outputs, unchanged drafts and length in Unicode code points. Script checks distinguish Bengali from Latin and cannot independently judge English versus Banglish. Wording checks can miss paraphrases or flag legitimate text. Review each case's criterion alongside its output.

To assess a previously captured run of the fictional scenarios:

```sh
node intelligence/evaluate.ts /absolute/path/to/fictional-observations.json
```

Supply a JSON array with exactly one object per scenario. Each object has `id` and `suggestion` plus optional `latencyMs` and `costUsd` measured by the caller. Missing measurements stay unknown. This command never initiates generation or sends observations to a service.

## Recorded mock run

The 114-scenario run in `mock-results.json` produced 93 distinct suggestions. The most frequent output occurred five times. The median was 42 code points and the 95th percentile was 58. Of the 114 outputs, 96 passed every mechanical check. Fifteen retained phrasing associated with planning, two missed the requested script and one missed a required expression of the boundary in English. Thirty-six rewrites came back unchanged. Local latency covers prompt preparation and mock generation within the Node process. The snapshot records the measured percentiles. Provider spend was USD 0.

An engineering-agent editorial review rated 18 selected outputs on four five-point scales. Each reviewed output, its scores and its rationale appear in `mock-review.json`. The benchmark checks that scores still refer to the exact output reviewed. The reviewer's sample includes ordinary cases and known weaknesses.

| Dimension | A score of 1 | A score of 3 | A score of 5 | Sample mean |
| --- | --- | --- | --- | ---: |
| Naturalness | Wrong language or hard to send | Understandable with awkward phrasing | Sounds idiomatic and ready to send | 4.11 |
| Creativity | Copies the input or misses the requested tone | Some purposeful rephrasing | A fresh and fitting expression of the same intent | 1.89 |
| Intention | Changes or misses the point | Preserves only part of the intended action | Keeps meaning, facts, consent and perspective | 4.28 |
| Cultural fit | Jarring register or stereotype | Broadly appropriate but stiff or generic | Appropriate register, script and social familiarity | 4.17 |

A model-generated editorial assessment has limited independence. Native Bangladeshi speaker review and a separately authorized live GPT-6 Luna benchmark remain outstanding. The recorded timing and cost apply only to this offline run.
