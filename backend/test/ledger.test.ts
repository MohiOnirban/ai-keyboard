import { randomUUID } from "node:crypto";
import assert from "node:assert/strict";
import { test } from "node:test";
import { readConfig } from "../src/config.ts";
import { connect } from "../src/database.ts";
import { Ledger } from "../src/ledger.ts";
import type { Outcome } from "../src/ledger.ts";

const unknownFailure: Outcome = {
  successful: false, billedMicros: null, inputTokens: null, outputTokens: null, latencyMs: 2,
};
const success = { ...unknownFailure, successful: true, billedMicros: 0 };

test("private PostgreSQL ledger enforces duplicate, allowance, revocation and spend accounting", async (t) => {
  const config = readConfig({ AI_DAILY_LIMIT: "1" });
  const db = await connect(config);
  t.after(() => db.close());

  // Switch out of the embedded database owner to exercise the hosted role's table grants and RLS.
  await db.query("set role keyboard_api");
  await assert.rejects(db.query("select 1 from pg_authid"));
  const ledger = new Ledger(db, config);
  const token = await ledger.createSession("test");
  assert.ok(token);
  const { rows: [session] } = await db.query<{ token_digest: string }>(
    "select token_digest from keyboard_private.sessions",
  );
  assert.notEqual(session.token_digest, token);
  assert.match(session.token_digest, /^[a-f0-9]{64}$/);
  assert.equal(await ledger.usage(token), 0);

  const id = randomUUID();
  const claims = await Promise.all(Array.from({ length: 6 }, () => ledger.reserve(token, id, "test")));
  assert.equal(claims.filter((value) => value.status === "accepted").length, 1);
  assert.equal(claims.filter((value) => value.status === "duplicate").length, 5);
  const accepted = claims.find((value) => value.status === "accepted");
  assert.ok(accepted);
  assert.equal((await ledger.reserve(token, randomUUID(), "test")).status, "quota");
  assert.deepEqual(await ledger.finish(accepted.reservation, success), { successful: true, authorized: true, used: 1 });
  assert.equal(await ledger.usage(token), 1);
  assert.equal(await ledger.revoke(token), true);
  assert.equal(await ledger.usage(token), null);

  await t.test("known and uncertain provider costs are retained independently of allowances", async () => {
    const billable = new Ledger(db, {
      ...config, reserveMicros: 100, dailyBudgetMicros: 160, monthlyBudgetMicros: 200,
    });
    const one = await billable.createSession("one");
    const two = await billable.createSession("two");
    assert.ok(one && two);
    const attempts = await Promise.all([
      billable.reserve(one, randomUUID(), "one"),
      billable.reserve(two, randomUUID(), "two"),
    ]);
    const reservation = attempts.find((value) => value.status === "accepted");
    assert.ok(reservation);
    assert.equal(attempts.filter((value) => value.status === "unavailable").length, 1);
    const failure = { ...unknownFailure, billedMicros: 50, inputTokens: 20, outputTokens: 0 };
    assert.equal((await billable.finish(reservation.reservation, failure)).used, 0);
    const next = await billable.reserve(one, randomUUID(), "one");
    assert.equal(next.status, "accepted");
    if (next.status !== "accepted") return;
    assert.equal((await billable.finish(next.reservation, unknownFailure)).used, 0);
    assert.equal((await billable.reserve(two, randomUUID(), "two")).status, "unavailable");
    const { rows: [row] } = await db.query<{ spent: string | bigint }>(
      "select sum(coalesce(billed_micros, reserved_micros)) as spent from keyboard_private.generations",
    );
    assert.equal(BigInt(row.spent), 150n);
  });

  await t.test("a revoked mid-flight request releases its allowance but records reported spend", async () => {
    const fresh = await ledger.createSession("revoked");
    assert.ok(fresh);
    const started = await ledger.reserve(fresh, randomUUID(), "revoked");
    assert.equal(started.status, "accepted");
    if (started.status !== "accepted") return;
    assert.equal(await ledger.revoke(fresh), true);
    assert.deepEqual(await ledger.finish(started.reservation, { ...success, billedMicros: 2 }), {
      successful: false, authorized: false, used: 0,
    });
    const { rows: [row] } = await db.query<{ billed_micros: string | bigint }>(
      "select billed_micros from keyboard_private.generations where session_id = $1",
      [started.reservation.sessionId],
    );
    assert.equal(BigInt(row.billed_micros), 2n);
  });
});

test("expired in-flight leases release concurrency while preserving deduplication and spending", async (t) => {
  const config = readConfig({ AI_GLOBAL_CONCURRENCY: "1" });
  const db = await connect(config);
  t.after(() => db.close());
  const ledger = new Ledger(db, { ...config, reserveMicros: 100, dailyBudgetMicros: 250, monthlyBudgetMicros: 250 });
  const one = await ledger.createSession("one");
  const two = await ledger.createSession("two");
  assert.ok(one && two);
  const id = randomUUID();
  const first = await ledger.reserve(one, id, "one");
  assert.equal(first.status, "accepted");
  assert.equal((await ledger.reserve(two, randomUUID(), "two")).status, "busy");
  await db.query(`
    update keyboard_private.generations
    set started_at = clock_timestamp() - interval '2 minutes', lease_until = clock_timestamp() - interval '1 minute'
    where request_id = $1
  `, [id]);
  assert.equal((await ledger.reserve(one, id, "one")).status, "duplicate");
  const second = await ledger.reserve(two, randomUUID(), "two");
  assert.equal(second.status, "accepted");
  if (second.status !== "accepted") return;
  await ledger.finish(second.reservation, unknownFailure);
  assert.equal((await ledger.reserve(two, randomUUID(), "two")).status, "unavailable");
});

test("UTC day and month limits use admission time", async (t) => {
  const config = readConfig({ AI_DAILY_LIMIT: "1" });
  const db = await connect(config);
  t.after(() => db.close());
  const ledger = new Ledger(db, { ...config, reserveMicros: 100, dailyBudgetMicros: 100, monthlyBudgetMicros: 100 });
  const token = await ledger.createSession("one");
  assert.ok(token);
  const id = randomUUID();
  const initial = await ledger.reserve(token, id, "one");
  assert.equal(initial.status, "accepted");
  if (initial.status !== "accepted") return;
  await ledger.finish(initial.reservation, { ...success, billedMicros: 100 });

  await db.query(`
    update keyboard_private.generations set
      started_at = date_trunc('day', clock_timestamp(), 'UTC') - interval '1 minute',
      lease_until = date_trunc('day', clock_timestamp(), 'UTC')
    where request_id = $1
  `, [id]);
  assert.equal(await ledger.usage(token), 0);
  const { rows: [date] } = await db.query<{ day: number }>(
    "select extract(day from timezone('UTC', clock_timestamp()))::int as day",
  );
  assert.equal((await ledger.reserve(token, randomUUID(), "one")).status, date.day === 1 ? "accepted" : "unavailable");

  await db.query(`
    update keyboard_private.generations set
      started_at = date_trunc('month', clock_timestamp(), 'UTC') - interval '1 minute',
      lease_until = date_trunc('month', clock_timestamp(), 'UTC')
  `);
  assert.equal((await ledger.reserve(token, randomUUID(), "one")).status, "accepted");
});
