import Fastify from "fastify";
import type { FastifyReply, FastifyRequest } from "fastify";
import type { GenerationRequest } from "../../intelligence/prompt.ts";
import type { Config } from "./config.ts";
import { connect } from "./database.ts";
import { Ledger } from "./ledger.ts";
import { makeProvider, prepare } from "./provider.ts";
import type { Generate, Result } from "./provider.ts";

const EMPTY_OBJECT = { type: "object", properties: {}, additionalProperties: false } as const;
const option = { type: "string", maxLength: 80 } as const;
const bodySchema = {
  type: "object",
  additionalProperties: false,
  required: ["mode", "text"],
  properties: {
    text: { type: "string", minLength: 1, maxLength: 1600 },
    mode: { type: "string", enum: ["rewrite", "reply", "express"] },
    style: option,
    intensity: { type: "string", enum: ["subtle", "balanced", "bold"] },
    relationship: option,
    language: { type: "string", enum: ["auto", "bn", "banglish", "en"] },
    preferences: {
      type: "object",
      additionalProperties: false,
      properties: {
        length: option,
        humor: option,
        formality: option,
        emoji: option,
        custom: { type: "string", maxLength: 500 },
      },
    },
  },
} as const;

const publicErrors = {
  invalid: [400, "Check the submitted text and options."],
  unauthorized: [401, "The anonymous session is missing, expired or revoked."],
  duplicate: [409, "This generation request was already submitted."],
  refusal: [422, "A suggestion could not be generated for this text."],
  rate: [429, "Too many requests. Try again in a minute."],
  quota: [429, "The daily AI allowance has been reached."],
  busy: [429, "AI is busy. Try again in a moment."],
  unavailable: [503, "AI is temporarily unavailable."],
} as const;

function respond(reply: FastifyReply, error: keyof typeof publicErrors) {
  const [code, message] = publicErrors[error];
  if (error === "rate") reply.header("retry-after", "60");
  return reply.code(code).send({ error: message });
}

const bearerPattern = /^Bearer ([A-Za-z0-9_-]{43})$/i;
const uuidPattern = /^[\da-f]{8}-[\da-f]{4}-[1-8][\da-f]{3}-[89ab][\da-f]{3}-[\da-f]{12}$/i;

export async function buildApp(config: Config, generate: Generate = makeProvider(config)) {
  const app = Fastify({
    logger: false,
    trustProxy: config.trustProxy,
    bodyLimit: 24 * 1024,
    requestTimeout: 15_000,
    connectionTimeout: 180_000,
    ajv: { customOptions: { coerceTypes: false, removeAdditional: false, useDefaults: false } },
  });
  const db = await connect(config);
  const ledger = new Ledger(db, config);
  try {
    await ledger.prune();
  } catch (error) {
    await db.close();
    throw error;
  }
  const cleanup = setInterval(() => {
    void ledger.prune().catch(() => console.error("Usage ledger cleanup failed."));
  }, 60 * 60_000);
  cleanup.unref();

  app.addHook("onClose", async () => {
    clearInterval(cleanup);
    await db.close();
  });
  app.addHook("onRequest", async (_request, reply) => {
    reply.header("cache-control", "no-store");
  });
  app.setErrorHandler((error, _request, reply) => {
    const statusCode = (error as { statusCode?: number }).statusCode;
    if (statusCode === 413) return reply.code(413).send({ error: "The submitted request is too large." });
    if (statusCode === 415) return reply.code(415).send({ error: "Send a JSON request." });
    if (statusCode && statusCode < 500) return respond(reply, "invalid");
    console.error("A request failed inside the keyboard service.");
    return respond(reply, "unavailable");
  });
  app.setNotFoundHandler((_request, reply) => reply.code(404).send({ error: "Route not found." }));

  app.get("/health", async () => {
    await db.query("select 1");
    return { status: "ok" };
  });
  app.post<{ Body: Record<string, never> }>("/v1/sessions", {
    schema: { body: EMPTY_OBJECT },
  }, async (request, reply) => {
    const token = await ledger.createSession(request.ip);
    if (!token) return respond(reply, "rate");
    return reply.code(201).send({ token });
  });
  app.get("/v1/usage", async (request, reply) => {
    const token = bearer(request);
    if (!token) return respond(reply, "unauthorized");
    const used = await ledger.usage(token);
    if (used === null) return respond(reply, "unauthorized");
    return { used, limit: config.dailyLimit };
  });
  app.delete("/v1/session", async (request, reply) => {
    const token = bearer(request);
    if (!token || !await ledger.revoke(token)) return respond(reply, "unauthorized");
    return reply.code(204).send();
  });
  app.post<{ Body: GenerationRequest }>("/v1/generate", {
    schema: { body: bodySchema },
  }, async (request, reply) => {
    const token = bearer(request);
    if (!token) return respond(reply, "unauthorized");
    const header = request.headers["idempotency-key"];
    if (typeof header !== "string" || !uuidPattern.test(header) || !validateStringValues(request.body)) {
      return respond(reply, "invalid");
    }
    const prepared = prepare(request.body, config);
    if (!prepared) return reply.code(400).send({ error: "The selected text and preferences are too long together." });

    let disconnected = reply.raw.destroyed;
    const close = () => { disconnected = true; };
    reply.raw.once("close", close);
    try {
      const admission = await ledger.reserve(token, header.toLowerCase(), request.ip);
      if (admission.status !== "accepted") return respond(reply, admission.status);

      const started = performance.now();
      let result: Result = { kind: "error", billedMicros: 0, inputTokens: null, outputTokens: null };
      if (!disconnected) {
        try {
          result = await generate(request.body, prepared);
        } catch {
          result = { kind: "error", billedMicros: null, inputTokens: null, outputTokens: null };
        }
      }

      const withinLimits = (result.inputTokens ?? 0) <= config.maxInputTokens
        && (result.outputTokens ?? 0) <= config.maxOutputTokens
        && (result.billedMicros ?? 0) <= config.reserveMicros;
      if (!withinLimits) console.error("Provider-reported usage exceeded the reserved bound.");

      const outcome = await ledger.finish(admission.reservation, {
        successful: result.kind === "suggestion" && !disconnected && withinLimits,
        billedMicros: result.billedMicros,
        inputTokens: result.inputTokens,
        outputTokens: result.outputTokens,
        latencyMs: Math.round(performance.now() - started),
      });

      if (disconnected) return;
      if (!outcome.authorized) return respond(reply, "unauthorized");
      if (result.kind === "refusal") return respond(reply, "refusal");
      if (result.kind !== "suggestion" || !outcome.successful) return respond(reply, "unavailable");
      return { suggestion: result.text, used: outcome.used, limit: config.dailyLimit };
    } finally {
      reply.raw.off("close", close);
    }
  });

  return app;
}

function bearer(request: FastifyRequest): string | undefined {
  return bearerPattern.exec(request.headers.authorization ?? "")?.[1];
}

function validateStringValues(request: GenerationRequest): boolean {
  const valid = (value: string, maximum: number) => value.length <= maximum && value.isWellFormed()
    && !/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/u.test(value);

  return !!request.text.trim() && valid(request.text, 1600)
    && [request.style, request.relationship, request.preferences?.length,
      request.preferences?.humor, request.preferences?.formality, request.preferences?.emoji]
      .every((value) => value === undefined || valid(value, 80))
    && (request.preferences?.custom === undefined || valid(request.preferences.custom, 500));
}
