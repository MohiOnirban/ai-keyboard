import { randomUUID } from "node:crypto";
import assert from "node:assert/strict";
import { test } from "node:test";
import { buildApp } from "../src/app.ts";
import { readConfig } from "../src/config.ts";
import type { Result } from "../src/provider.ts";

function generation(token: string, options: unknown = { mode: "rewrite", text: "আজ বাড়ি যাব।" }, id: string = randomUUID()) {
  return {
    method: "POST" as const,
    url: "/v1/generate",
    headers: { authorization: `Bearer ${token}`, "idempotency-key": id },
    payload: options as object,
  };
}

test("mock API covers session lifecycle, validation, privacy and quota", async (t) => {
  const app = await buildApp(readConfig({ AI_DAILY_LIMIT: "1" }));
  t.after(() => app.close());
  const created = await app.inject({ method: "POST", url: "/v1/sessions", payload: {} });
  assert.equal(created.statusCode, 201);
  assert.equal(created.headers["cache-control"], "no-store");
  const { token } = created.json<{ token: string }>();
  assert.match(token, /^[A-Za-z0-9_-]{43}$/);
  const authorization = { authorization: `Bearer ${token}` };
  const usage = () => app.inject({ method: "GET", url: "/v1/usage", headers: authorization });
  assert.deepEqual((await usage()).json(), { used: 0, limit: 1 });
  assert.equal((await app.inject({ method: "GET", url: "/v1/usage" })).statusCode, 401);

  const secret = "private string must not appear in error";
  for (const input of [
    { mode: "rewrite", text: " " },
    { mode: "wrong", text: secret },
    { mode: "rewrite", text: secret, extra: true },
    { mode: "rewrite", text: 1 },
    { mode: "rewrite", text: "🙂".repeat(801) },
    { mode: "rewrite", text: "\ud800" },
    { mode: "rewrite", text: "x\u0000" },
    { mode: "rewrite", text: secret, preferences: { emoji: 1 } },
  ]) {
    const invalid = await app.inject(generation(token, input));
    assert.equal(invalid.statusCode, 400, JSON.stringify(input));
    assert.equal(invalid.body.includes(secret), false);
  }

  const id = randomUUID();
  const response = await app.inject(generation(token, { mode: "express", text: "aj mon valo" }, id));
  assert.equal(response.statusCode, 200, response.body);
  assert.equal(typeof response.json().suggestion, "string");
  assert.deepEqual((await usage()).json(), { used: 1, limit: 1 });
  assert.equal((await app.inject(generation(token, undefined, id.toUpperCase()))).statusCode, 409);
  const quota = await app.inject(generation(token));
  assert.equal(quota.statusCode, 429);
  assert.equal(quota.headers["kotha-error-code"], "quota");
  assert.equal((await app.inject({ method: "DELETE", url: "/v1/session", headers: authorization })).statusCode, 204);
  assert.equal((await usage()).statusCode, 401);
  assert.equal((await app.inject(generation(token))).statusCode, 401);
});

test("concurrent API retries call the provider once and preserve failure allowances", async (t) => {
  const started = Promise.withResolvers<void>();
  const completion = Promise.withResolvers<Result>();
  let calls = 0;
  const app = await buildApp(readConfig({ AI_DAILY_LIMIT: "2" }), async () => {
    calls += 1;
    started.resolve();
    return completion.promise;
  });
  t.after(() => app.close());
  const session = await app.inject({ method: "POST", url: "/v1/sessions", payload: {} });
  const { token } = session.json<{ token: string }>();
  const id = randomUUID();
  const first = app.inject(generation(token, undefined, id));
  const result = Promise.resolve(first);
  await started.promise;
  assert.equal((await app.inject(generation(token, undefined, id))).statusCode, 409);
  assert.equal((await app.inject(generation(token))).statusCode, 429);
  assert.equal(calls, 1);
  completion.resolve({ kind: "refusal", billedMicros: 0, inputTokens: null, outputTokens: null });
  assert.equal((await result).statusCode, 422);
  assert.deepEqual((await app.inject({
    method: "GET", url: "/v1/usage", headers: { authorization: `Bearer ${token}` },
  })).json(), { used: 0, limit: 2 });
});

test("session creation throttles the socket address and shutdown denies generation", async (t) => {
  const app = await buildApp(readConfig({ AI_ENABLED: "false", SESSION_CREATIONS_PER_IP_MINUTE: "1" }));
  t.after(() => app.close());
  const first = await app.inject({ method: "POST", url: "/v1/sessions", payload: {} });
  assert.equal(first.statusCode, 201);
  const second = await app.inject({
    method: "POST", url: "/v1/sessions", payload: {}, headers: { "x-forwarded-for": "192.0.2.1" },
  });
  assert.equal(second.statusCode, 429);
  assert.equal(second.headers["retry-after"], "60");
  assert.equal((await app.inject(generation(first.json().token))).statusCode, 503);
});

test("a disconnected client keeps its allowance and can explicitly generate after reconnecting", { timeout: 30_000 }, async (t) => {
  const started = Promise.withResolvers<void>();
  const disconnected = Promise.withResolvers<void>();
  const resume = Promise.withResolvers<void>();
  const originalId = randomUUID();
  let calls = 0;
  const app = await buildApp(readConfig({ AI_DAILY_LIMIT: "1" }), async () => {
    calls++;
    if (calls === 1) {
      started.resolve();
      await resume.promise;
    }
    return { kind: "suggestion", text: "A synthetic suggestion.", billedMicros: 0, inputTokens: 1, outputTokens: 1 };
  });
  t.after(() => app.close());
  app.addHook("onRequest", async (request, reply) => {
    if (request.headers["idempotency-key"] === originalId) reply.raw.once("close", disconnected.resolve);
  });
  const address = await app.listen({ host: "127.0.0.1", port: 0 });
  const created = await app.inject({ method: "POST", url: "/v1/sessions", payload: {} });
  const { token } = created.json<{token: string}>();
  const original = generation(token, undefined, originalId);
  const controller = new AbortController();
  const response = fetch(address + original.url, {
    method: original.method,
    headers: { ...original.headers, "content-type": "application/json" },
    body: JSON.stringify(original.payload),
    signal: controller.signal,
  });
  try {
    await started.promise;
    controller.abort();
    await assert.rejects(response, { name: "AbortError" });
    await disconnected.promise;
  } finally {
    resume.resolve();
  }
  let next;
  const newAction = generation(token);
  for (let attempt = 0; attempt < 12; attempt++) {
    next = await app.inject(newAction);
    if (next.statusCode !== 429) break;
    await new Promise(resolve => setTimeout(resolve, 50));
  }
  assert.equal(next?.statusCode, 200);
  assert.equal(next.json().used, 1);
  assert.equal((await app.inject(original)).statusCode, 409);
  assert.equal(calls, 2);
});

test("provider failures preserve the API error shape and add a fixed diagnostic code", async (t) => {
  const app = await buildApp(readConfig({}), async () => ({
    kind: "error", reason: "provider_restricted", inputTokens: null, outputTokens: null, billedMicros: null,
  }));
  t.after(() => app.close());
  const session = await app.inject({ method: "POST", url: "/v1/sessions", payload: {} });
  const failed = await app.inject(generation(session.json().token));
  assert.equal(failed.statusCode, 503);
  assert.equal(failed.headers["kotha-error-code"], "provider_restricted");
  assert.deepEqual(failed.json(), { error: "AI is temporarily unavailable." });
});
