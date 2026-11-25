import { buildPrompt, mockSuggestion } from "../../intelligence/prompt.ts";
import type { GenerationRequest } from "../../intelligence/prompt.ts";
import type { Config } from "./config.ts";

const ENDPOINT = "https://openrouter.ai/api/v1/responses";
const MAX_RESPONSE_BYTES = 128 * 1024;

type Usage = {
  billedMicros: number | null;
  inputTokens: number | null;
  outputTokens: number | null;
};

export type Result = (
  | { kind: "suggestion"; text: string }
  | { kind: "error" | "refusal" }
) & Usage;

const noUsage: Usage = { billedMicros: null, inputTokens: null, outputTokens: null };
const maxSuggestionLength = 1600;

export function prepare(request: GenerationRequest, config: Config) {
  const { instructions, input } = buildPrompt(request);
  const textBytes = Buffer.byteLength(instructions) + Buffer.byteLength(input);
  // UTF-8 bytes conservatively bound OpenAI text tokens, with room for the message/schema framing.
  if (textBytes + 512 > config.maxInputTokens) return null;
  return {
    model: config.model,
    instructions,
    input: [{ role: "user", content: input }],
    reasoning: { effort: "none" },
    store: false,
    stream: false,
    max_output_tokens: config.maxOutputTokens,
    provider: {
      allow_fallbacks: false,
      require_parameters: true,
      data_collection: "deny",
      zdr: true,
      max_price: { prompt: config.inputPrice, completion: config.outputPrice, request: 0 },
    },
    text: {
      format: {
        type: "json_schema",
        name: "keyboard_suggestion",
        strict: true,
        schema: {
          type: "object",
          properties: { suggestion: { type: "string" } },
          required: ["suggestion"],
          additionalProperties: false,
        },
      },
    },
  } as const;
}

export type Prepared = NonNullable<ReturnType<typeof prepare>>;
export type Generate = (request: GenerationRequest, body: Prepared) => Promise<Result>;

export function makeProvider(config: Config, fetcher = fetch): Generate {
  return async (request, body) => {
    if (config.provider === "mock") {
      const text = mockSuggestion(request);
      return validSuggestion(text)
        ? { kind: "suggestion", text: text.trim(), ...noUsage, billedMicros: 0 }
        : { kind: "error", ...noUsage, billedMicros: 0 };
    }

    try {
      const response = await fetcher(ENDPOINT, {
        method: "POST",
        headers: { authorization: `Bearer ${config.openRouterKey}`, "content-type": "application/json" },
        signal: AbortSignal.timeout(config.timeoutMs),
        redirect: "error",
        body: JSON.stringify(body),
      });

      const value = await readJson(response);
      const usage = readUsage(value, config);
      if (record(value) && (value.error_type === "refusal" || value.error_type === "content_policy_violation")) {
        return { kind: "refusal", ...usage };
      }
      if (!response.ok || !record(value) || value.status !== "completed" || value.error != null) {
        return { kind: "error", ...usage };
      }
      return readSuggestion(value, usage);
    } catch {
      // A timed-out or unreadable response may have accrued charges.
      return { kind: "error", ...noUsage };
    }
  };
}

async function readJson(response: Response): Promise<unknown> {
  if (!response.body) return null;

  const reader = response.body.getReader();
  const decoder = new TextDecoder("utf-8", { fatal: true });
  let size = 0;
  let body = "";
  try {
    for (;;) {
      const { value, done } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > MAX_RESPONSE_BYTES) return null;
      body += decoder.decode(value, { stream: true });
    }
    return JSON.parse(body + decoder.decode());
  } finally {
    // Cancel on oversized or malformed streams. On EOF, cancel is a no-op.
    void reader.cancel().catch(() => {});
  }
}

function readUsage(value: unknown, config: Config): Usage {
  if (!record(value) || !record(value.usage)) return noUsage;
  const { input_tokens, output_tokens, cost } = value.usage;
  const inputTokens = tokenCount(input_tokens);
  const outputTokens = tokenCount(output_tokens);
  // Missing cost falls back to the maximum configured rates when the counts are known.
  let billedMicros = null;
  if (typeof cost === "number" && Number.isFinite(cost) && cost >= 0 && cost <= 1_000_000) {
    billedMicros = Math.ceil(cost * 1_000_000);
  } else if (inputTokens !== null && outputTokens !== null) {
    billedMicros = Math.ceil(inputTokens * config.inputPrice + outputTokens * config.outputPrice);
  }
  return { billedMicros, inputTokens, outputTokens };
}

function readSuggestion(value: Record<string, unknown>, usage: Usage): Result {
  if (!Array.isArray(value.output)) return { kind: "error", ...usage };
  const content = value.output
    .filter((item) => record(item) && item.type === "message" && item.role === "assistant")
    .flatMap((item) => Array.isArray(item.content) ? item.content : []);
  if (content.some((item) => record(item) && item.type === "refusal")) {
    return { kind: "refusal", ...usage };
  }
  if (content.length !== 1 || !record(content[0]) || content[0].type !== "output_text"
    || typeof content[0].text !== "string") {
    return { kind: "error", ...usage };
  }
  try {
    const parsed: unknown = JSON.parse(content[0].text);
    if (record(parsed) && Object.keys(parsed).length === 1 && validSuggestion(parsed.suggestion)) {
      return { kind: "suggestion", text: parsed.suggestion.trim(), ...usage };
    }
  } catch {
    // Parsing errors can contain provider output, so only return a fixed error.
  }
  return { kind: "error", ...usage };
}

function validSuggestion(value: unknown): value is string {
  return typeof value === "string" && value.length <= maxSuggestionLength && !!value.trim()
    && value.isWellFormed() && !/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/u.test(value);
}

function tokenCount(value: unknown): number | null {
  return typeof value === "number" && Number.isInteger(value) && value >= 0 && value <= 2_147_483_647
    ? value : null;
}

function record(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}
