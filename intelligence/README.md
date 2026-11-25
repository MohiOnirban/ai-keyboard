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

Native Bangladeshi speakers have yet to review the sample. The mock timing and zero cost apply to the local provider.

## Live GPT-6 Luna benchmark

On November 26, 2026 (Asia/Shanghai), the Fastify service submitted all 114 fictional evaluation requests to `openai/gpt-6-luna` through OpenRouter with reasoning disabled. It used the private hosted Supabase ledger and the prompt at Git revision `9161f15`. Each output language had 38 cases. The set included 38 Rewrite, 39 Reply and 37 Express requests. All 114 returned a structured result on the first attempt. Inputs, generated outputs and editorial scores are separate from private user traffic. The counts below describe this baseline revision.

| Measurement | Live result |
| --- | ---: |
| Passed all mechanical checks | 101 / 114 |
| Distinct normalized outputs | 114 / 114 |
| Unchanged suggestions | 0 |
| Median / p95 response length, Unicode code points | 60 / 106 |
| Median / p95 provider round trip | 2.42 s / 3.18 s |
| Median / p95 full API request | 11.66 s / 13.48 s |
| Input / output tokens | 38,610 / 3,854 |
| Accounted benchmark cost, USD | 0.005835 |

The full API timing includes admission and settlement across a remote database connection from the developer Mac. The provider timing covers inference and transport to OpenRouter. Mobile rendering and the device-to-API hop are outside these timings. Cost uses reported amounts or a token estimate at the configured price ceilings, rounded up per call to the next microdollar.

Six results used an unexpected script and nine missed a required term group. Two failed both checks. Some word checks missed valid paraphrases such as “leave me untagged.” Review also found a substantive error in h053, where the model affirmed a refund without evidence, and a perspective error in h059, where it adopted the sender’s experience. A passing keyword check cannot establish factual accuracy.

An engineering-agent review scored the same 18 outputs chosen for the mock. Out of five, the live sample averaged 4.44 for naturalness, 3.22 for creativity, 4.78 for preserving intention and 4.67 for cultural fit. The video and address privacy boundaries survived translation. Two jokes had awkward punchlines and one apology changed ownership of the item. [Scored outputs and rationale](live-review.json)

Run this command from the repository root to reproduce the baseline score without contacting a provider.

```sh
node intelligence/evaluate.ts intelligence/benchmarks/gpt-6-luna-baseline.json intelligence/live-review.json
```

[Full fictional capture](benchmarks/gpt-6-luna-baseline.json) · [Machine-readable results](benchmarks/gpt-6-luna-results.json) · [Authorized generation runner](../backend/README.md)

## Follow-up on language and factual replies

The baseline prompted two changes. Output script and language now have a dedicated trusted instruction. For automatic selection, the service uses Bengali script, common Banglish cues and English function words to identify clear cases. Reply mode explicitly treats the input as the other person's message and directs the recipient to check unknown information.

Two subsequent live runs used the same 18 scenarios, comprising 12 previously tested situations and six new fictional examples. In the first run the payment response correctly offered to check, while two English drafts still received Banglish replies. The final run exercised the improved automatic selection. All 18 final suggestions used the intended language on engineering review. Each of the 11 scenarios completed after resumption had a new result, and the seven earlier successful observations were preserved exactly. One HTTP 503 on h087 had reported token usage and was charged to the ledger. The separate retry succeeded.

The final h053 answer was “Eta ektu check kore apnake janacchi.” The model also offered to check the new courier and signed-roster examples. Two remaining limitations are visible. In h051 the reply to a teacher still shifts responsibility for a project choice back to the teacher. In r05 the question about finding another way across the water implies the sender wanted to take the ferry. These are principal engineering judgments on synthetic examples. This focused retest covers the changed behavior in 18 cases. The full-set metrics above apply to the recorded baseline revision.

| Live testing activity | Upstream attempts | USD accounted |
| --- | ---: | ---: |
| 114-case benchmark | 114 | 0.005835 |
| Initial service and two mobile smoke tests | 3 | 0.000138 |
| First focused run | 18 | 0.001028 |
| Final focused run, including one failed attempt | 19 | 0.001219 |
| **Total** | **154** | **0.008220** |

The final database reconciliation showed 153 successes and one failure, totaling 55,219 input and 5,269 output tokens. OpenRouter's read-only key endpoint separately showed USD 0.0080985 of key-wide daily usage at the final check. The project budget used its own rounded-up ledger amounts, including USD 0.000136 for the failed attempt. The authorized maximum was USD 1.00. The paid mobile listener was closed after both device smoke tests. [OpenRouter key usage endpoint](https://openrouter.ai/docs/api/api-reference/api-keys/get-current-api-key)

[Follow-up scenarios](reply-language-regression.json) · [First follow-up outputs](benchmarks/gpt-6-luna-focused-first.json) · [Final follow-up outputs](benchmarks/gpt-6-luna-focused-final.json)
