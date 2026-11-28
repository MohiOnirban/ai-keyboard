import { createHash, createHmac, randomBytes, randomUUID } from "node:crypto";
import type { Config } from "./config.ts";
import type { Database, Queryable } from "./database.ts";

const MINUTE = 60_000;
const DAY = 24 * 60 * MINUTE;
const SESSION_LIFETIME = 90 * DAY;
const GENERATION_RETENTION = 45 * DAY;

export type Reservation = { sessionId: string; requestId: string };
export type Admission =
  | { status: "accepted"; reservation: Reservation }
  | { status: "unauthorized" | "duplicate" | "rate" | "quota" | "busy" }
  | { status: "unavailable"; budget?: true };

export interface Outcome {
  successful: boolean;
  billedMicros: number | null;
  inputTokens: number | null;
  outputTokens: number | null;
  latencyMs: number;
}

type Finished = { successful: boolean; authorized: boolean; used: number };

export class Ledger {
  private db: Database;
  private config: Config;

  constructor(db: Database, config: Config) {
    this.db = db;
    this.config = config;
  }

  async createSession(address: string): Promise<string | null> {
    return this.db.transaction(async (tx) => {
      const now = await clock(tx);
      const minute = Math.floor(now.getTime() / MINUTE);
      const key = this.addressKey(address, minute);
      if (!await incrementRate(tx, "create", key, minute, this.config.sessionRate)) return null;

      const token = randomBytes(32).toString("base64url");
      await tx.query(
        `insert into keyboard_private.sessions (id, token_digest, expires_at) values ($1, $2, $3)`,
        [randomUUID(), digest(token), new Date(now.getTime() + SESSION_LIFETIME)],
      );
      return token;
    });
  }

  async revoke(token: string): Promise<boolean> {
    return this.db.transaction(async (tx) => {
      await lock(tx);
      const { rows } = await tx.query(
        `update keyboard_private.sessions set revoked_at = clock_timestamp()
         where token_digest = $1 and revoked_at is null and expires_at > clock_timestamp()
         returning id`,
        [digest(token)],
      );
      return rows.length === 1;
    });
  }

  async usage(token: string): Promise<number | null> {
    const { rows } = await this.db.query<{ used: number }>(
      `select count(g.request_id)::int as used
       from keyboard_private.sessions s
       left join keyboard_private.generations g on g.session_id = s.id and g.state = 'succeeded'
           and g.started_at >= date_trunc('day', statement_timestamp(), 'UTC')
       where s.token_digest = $1 and s.revoked_at is null and s.expires_at > statement_timestamp()
       group by s.id`,
      [digest(token)],
    );
    return rows[0]?.used ?? null;
  }

  async reserve(token: string, requestId: string, address: string): Promise<Admission> {
    return this.db.transaction(async (tx) => {
      await lock(tx);
      const now = await clock(tx);
      const sessions = await tx.query<{ id: string }>(
        `select id from keyboard_private.sessions
         where token_digest = $1 and revoked_at is null and expires_at > $2`,
        [digest(token), now],
      );
      const sessionId = sessions.rows[0]?.id;
      if (!sessionId) return { status: "unauthorized" };

      const previous = await tx.query(
        `select 1 from keyboard_private.generations where session_id = $1 and request_id = $2`,
        [sessionId, requestId],
      );
      if (previous.rows.length) return { status: "duplicate" };
      if (!this.config.aiEnabled) return { status: "unavailable" };

      const minute = Math.floor(now.getTime() / MINUTE);
      if (!await incrementRate(tx, "generate_ip", this.addressKey(address, minute), minute, this.config.ipRate)
        || !await incrementRate(tx, "generate_install", sessionId, minute, this.config.installRate)) {
        return { status: "rate" };
      }

      const dayStart = utcDay(now);
      const monthStart = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth()));
      const { rows: [installation] } = await tx.query<{ claimed: number; active: number }>(
        `select count(*) filter (
                    where started_at >= $3 and (state = 'succeeded' or (state = 'pending' and lease_until > $2))
                  )::int as claimed,
                count(*) filter (where state = 'pending' and lease_until > $2)::int as active
         from keyboard_private.generations
         where session_id = $1 and (started_at >= $3 or lease_until > $2)`,
        [sessionId, now, dayStart],
      );
      if (installation.claimed >= this.config.dailyLimit) return { status: "quota" };

      const { rows: [global] } = await tx.query<{ active: number }>(
        `select count(*)::int as active from keyboard_private.generations
         where state = 'pending' and lease_until > $1`,
        [now],
      );
      if (installation.active >= this.config.installConcurrency || global.active >= this.config.globalConcurrency) {
        return { status: "busy" };
      }

      if (this.config.reserveMicros > 0) {
        const { rows: [spend] } = await tx.query<{ daily: string | bigint; monthly: string | bigint }>(
          `select coalesce(sum(coalesce(billed_micros, reserved_micros)) filter (where started_at >= $2), 0) as daily,
                  coalesce(sum(coalesce(billed_micros, reserved_micros)), 0) as monthly
           from keyboard_private.generations where started_at >= $1`,
          [monthStart, dayStart],
        );
        const reserve = BigInt(this.config.reserveMicros);
        if (BigInt(spend.daily) + reserve > BigInt(this.config.dailyBudgetMicros)
          || BigInt(spend.monthly) + reserve > BigInt(this.config.monthlyBudgetMicros)) {
          return { status: "unavailable", budget: true };
        }
      }

      await tx.query(
        `insert into keyboard_private.generations
           (session_id, request_id, started_at, lease_until, state, reserved_micros)
         values ($1, $2, $3, $4, 'pending', $5)`,
        [sessionId, requestId, now, new Date(now.getTime() + this.config.timeoutMs + 30_000), this.config.reserveMicros],
      );
      return { status: "accepted", reservation: { sessionId, requestId } };
    });
  }

  async finish(reservation: Reservation, outcome: Outcome): Promise<Finished> {
    return this.db.transaction(async (tx) => {
      await lock(tx);
      const now = await clock(tx);
      const { rows: [result] } = await tx.query<{ authorized: boolean; state: string }>(
        `update keyboard_private.generations g set
             state = case when $3 and s.revoked_at is null and s.expires_at > $4 and g.lease_until > $4
                          then 'succeeded' else 'failed' end,
             billed_micros = $5, input_tokens = $6, output_tokens = $7, latency_ms = $8
         from keyboard_private.sessions s
         where g.session_id = $1 and g.request_id = $2 and g.state = 'pending' and s.id = g.session_id
         returning s.revoked_at is null and s.expires_at > $4 as authorized, g.state`,
        [
          reservation.sessionId, reservation.requestId, outcome.successful, now,
          outcome.billedMicros, outcome.inputTokens, outcome.outputTokens, outcome.latencyMs,
        ],
      );
      if (!result) throw new Error("No reservation to settle");
      const { rows: [usage] } = await tx.query<{ used: number }>(
        `select count(*)::int as used from keyboard_private.generations
         where session_id = $1 and started_at >= $2 and state = 'succeeded'`,
        [reservation.sessionId, utcDay(now)],
      );
      return { authorized: result.authorized, successful: result.state === "succeeded", used: usage.used };
    });
  }

  async prune(): Promise<void> {
    await this.db.transaction(async (tx) => {
      await lock(tx);
      const now = await clock(tx);
      await tx.query("delete from keyboard_private.rate_windows where minute_start < $1", [
        new Date(now.getTime() - 2 * MINUTE),
      ]);
      await tx.query("delete from keyboard_private.generations where started_at < $1", [
        new Date(now.getTime() - GENERATION_RETENTION),
      ]);
      await tx.query(
        `delete from keyboard_private.sessions s
         where (expires_at <= $1 or revoked_at <= $1)
           and not exists (select 1 from keyboard_private.generations g where g.session_id = s.id)`,
        [now],
      );
    });
  }

  private addressKey(address: string, minute: number): string {
    return createHmac("sha256", this.config.rateSecret).update(`${minute}:${address}`).digest("hex");
  }
}

async function lock(tx: Queryable) {
  const { rows } = await tx.query("select singleton from keyboard_private.ledger_lock where singleton for update");
  if (rows.length !== 1) throw new Error("Usage ledger singleton is missing");
}

async function clock(tx: Queryable): Promise<Date> {
  const { rows: [row] } = await tx.query<{ now: Date }>("select clock_timestamp() as now");
  return row.now;
}

function utcDay(date: Date): Date {
  return new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate()));
}

function digest(token: string): string {
  return createHash("sha256").update(token).digest("hex");
}

async function incrementRate(tx: Queryable, scope: string, key: string, minute: number, limit: number) {
  const { rows } = await tx.query(
    `insert into keyboard_private.rate_windows as w (scope, key, minute_start, count) values ($1, $2, $3, 1)
     on conflict (scope, key, minute_start)
     do update set count = w.count + 1 where w.count < $4
     returning count`,
    [scope, key, new Date(minute * MINUTE), limit],
  );
  return rows.length === 1;
}
