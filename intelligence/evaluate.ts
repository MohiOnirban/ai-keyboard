// Offline benchmark. A captured run must contain only fictional scenarios from scenarios.json.
import { readFileSync } from "node:fs";
import { performance } from "node:perf_hooks";
import { examples } from "./examples.ts";
import { buildPrompt, mockSuggestion, type GenerationRequest } from "./prompt.ts";

type Scenario = {
  id: string; request: GenerationRequest; expectedLanguage: "en" | "bn" | "banglish";
  required: string[][]; forbidden: string[]; review: string;
};
type Observation = { id: string; suggestion: string; latencyMs?: number; costUsd?: number };
const scenarios: Scenario[] = JSON.parse(readFileSync(new URL("./scenarios.json", import.meta.url), "utf8"));
const suppliedFile = process.argv[2];
const supplied: Observation[] | null = suppliedFile ? JSON.parse(readFileSync(suppliedFile, "utf8")) : null;
if (supplied !== null && (!Array.isArray(supplied) || supplied.length !== scenarios.length)) {
  throw new Error("Supply exactly one observation per holdout scenario.");
}
const byId = new Map(supplied?.map(item => [item.id, item]));
const normalize = (s: string) => s.normalize("NFC").toLowerCase().trim().replace(/\s+/g, " ");
const corpusTexts = new Set(examples.map(group => normalize(group.text)));
if (new Set(scenarios.map(s => normalize(s.request.text))).size !== scenarios.length
    || scenarios.some(s => corpusTexts.has(normalize(s.request.text)))) throw new Error("Holdout text overlap.");
function percentile(numbers: number[], p: number) {
  if (!numbers.length) return null;
  const sorted = [...numbers].sort((a,b) => a - b);
  return Number(sorted[Math.ceil(sorted.length * p) - 1].toFixed(4));
}
const scriptMatches = (text: string, language: string) => {
  const bn = /\p{Script=Bengali}/u.test(text);
  return language === "bn" ? bn : !bn && /[a-z]/i.test(text);
};
const reviewedAxes = ["naturalness", "creativity", "intention", "culturalFit"] as const;
type Review = { id: string; basis: string; scores: Record<typeof reviewedAxes[number], number>; output: string };
const reviews: Review[] = !supplied ? JSON.parse(readFileSync(new URL("./mock-review.json", import.meta.url), "utf8")) : [];
const rows = scenarios.map(s => {
  let suggestion: string;
  let latencyMs: number | undefined;
  let costUsd: number | undefined;
  if (supplied !== null) {
    const observed = byId.get(s.id);
    if (!observed || typeof observed.suggestion !== "string") throw new Error("Missing suggestion for " + s.id);
    ({suggestion, latencyMs, costUsd} = observed);
    for (const value of [latencyMs, costUsd]) {
      if (value !== undefined && (!Number.isFinite(value) || value < 0)) throw new Error("Invalid measurement for " + s.id);
    }
  } else {
    const start = performance.now();
    buildPrompt(s.request);
    suggestion = mockSuggestion(s.request);
    latencyMs = performance.now() - start;
    costUsd = 0;
  }
  const result = normalize(suggestion);
  const missing = s.required.filter(group => !group.some(term => result.includes(normalize(term))));
  const prohibited = s.forbidden.filter(term => result.includes(normalize(term)));
  const reasons: string[] = [];
  if (!result || suggestion.length > 1600 || !suggestion.isWellFormed()) reasons.push("format");
  if (!scriptMatches(suggestion,s.expectedLanguage)) reasons.push("script");
  if (missing.length) reasons.push("anchors");
  if (prohibited.length) reasons.push("prohibited phrase");
  const instructionLike = /(?:জানতে চাই|চাইতে হবে|সরি বলব|অনুরোধ করব|হালকা খোঁচা|কে (?:জানাই|বুঝাই|বলি)|করতে অনুরোধ|ছোট ভাইকে|মজা করতে চাই|বলতে চাই যে|jiggesh korbo|ke bolbo|ke boli|sorry bolbo|group e moja korbo|niye roast|korte boli|^(?:tease|make a light joke|ask (?:them|if|where)|tell (?:him|her|them)))/iu;
  // Bengali marks have no ASCII word boundary, so the end condition is intentionally unanchored.
  if (s.request.mode === "express" && instructionLike.test(suggestion)) reasons.push("planning language");
  return {id:s.id, mode:s.request.mode, expectedLanguage:s.expectedLanguage, suggestion, missing, prohibited, reasons, latencyMs, costUsd};
});
const unchangedByMode = { rewrite: 0, reply: 0, express: 0 };
for (const [i, row] of rows.entries()) {
  if (normalize(row.suggestion) === normalize(scenarios[i].request.text)) unchangedByMode[row.mode]++;
}
const byOutput = new Map<string, number>();
for (const row of rows) {
  const key = normalize(row.suggestion);
  byOutput.set(key, (byOutput.get(key) ?? 0) + 1);
}
const failed = rows.filter(row => row.reasons.length);
const reviewTotals = Object.fromEntries(reviewedAxes.map(axis => [axis,0]));
for (const review of reviews) {
  const obs = rows.find(row => row.id === review.id);
  if (obs?.suggestion !== review.output || !review.basis) throw new Error("Reassess changed sample " + review.id);
  for (const axis of reviewedAxes) {
    const score = review.scores[axis];
    if (!Number.isInteger(score) || score < 1 || score > 5) throw new Error("Invalid review score");
    reviewTotals[axis] += score;
  }
}
const latency = rows.flatMap(row => row.latencyMs === undefined ? [] : [row.latencyMs]);
const costs = rows.flatMap(row => row.costUsd === undefined ? [] : [row.costUsd]);
const checks = ["format","script","anchors","prohibited phrase","planning language"];
const lengths = rows.map(row => [...row.suggestion].length);
const report = {
  source: supplied ? "captured fictional outputs" : "local deterministic mock",
  exampleFamilies: examples.length, authoredExamples: examples.reduce((sum,f) => sum + Object.keys(f.variants).length,0),
  scenarios: rows.length, passAllMechanicalChecks: rows.length - failed.length,
  checkFailures: Object.fromEntries(checks.map(check => [check,rows.filter(r => r.reasons.includes(check)).length])),
  unchangedByMode, distinctOutputs: byOutput.size, mostRepeatedOutputCount: Math.max(...byOutput.values()),
  medianCharacters: percentile(lengths,0.5), p95Characters:percentile(lengths,0.95),
  latencyMs: { measured:latency.length, median:percentile(latency,0.5), p95:percentile(latency,0.95) },
  costUsd: costs.length === rows.length ? costs.reduce((sum,amount) => sum+amount,0) : null,
  scoredSample: reviews.length,
  editorialSampleMeans: reviews.length ? Object.fromEntries(reviewedAxes.map(axis => [axis,Number((reviewTotals[axis]/reviews.length).toFixed(2))])) : null,
  failed: failed.map(({id,reasons,missing,prohibited}) => ({id,reasons,missing,prohibited})),
};
console.log(JSON.stringify(report,null,2));
