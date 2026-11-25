import { randomBytes } from "node:crypto";
import type { FastifyServerOptions } from "fastify";

export function readConfig(env: NodeJS.ProcessEnv = process.env) {
  const provider = env.AI_PROVIDER ?? "mock";
  if (provider !== "mock" && provider !== "openrouter") {
    throw new Error("AI_PROVIDER must be mock or openrouter");
  }

  const aiEnabled = boolean(env, "AI_ENABLED", true);
  const production = env.NODE_ENV === "production";
  const databaseUrl = env.DATABASE_URL || undefined;
  const databaseCaFile = env.DATABASE_CA_FILE || undefined;
  const databaseTls = boolean(env, "DATABASE_TLS", true);
  const dailyBudgetMicros = usd(env, "AI_DAILY_BUDGET_USD", 0);
  const monthlyBudgetMicros = usd(env, "AI_MONTHLY_BUDGET_USD", 0);
  const inputPrice = number(env, "AI_INPUT_USD_PER_MILLION", 0, 0, 1000);
  const outputPrice = number(env, "AI_OUTPUT_USD_PER_MILLION", 0, 0, 1000);
  const maxInputTokens = integer(env, "AI_MAX_INPUT_TOKENS", 12288, 512, 32768);
  const maxOutputTokens = integer(env, "AI_MAX_OUTPUT_TOKENS", 320, 32, 1024);
  const reserveMicros = Math.ceil(inputPrice * maxInputTokens + outputPrice * maxOutputTokens);
  const rateSecret = env.RATE_LIMIT_SECRET || randomBytes(32).toString("hex");

  if (production && (!databaseUrl || !databaseTls)) {
    throw new Error("Production requires a TLS PostgreSQL connection");
  }
  if (production && (!env.RATE_LIMIT_SECRET || Buffer.byteLength(rateSecret) < 32)) {
    throw new Error("Production requires a RATE_LIMIT_SECRET of at least 32 bytes");
  }
  if (databaseUrl) {
    const url = new URL(databaseUrl);
    if (url.protocol !== "postgres:" && url.protocol !== "postgresql:") {
      throw new Error("DATABASE_URL must be a PostgreSQL connection string");
    }
    if ([...url.searchParams.keys()].some((key) => key.toLowerCase().startsWith("ssl"))) {
      throw new Error("Use DATABASE_TLS and DATABASE_CA_FILE for database TLS configuration");
    }
  }
  if (provider === "openrouter" && aiEnabled) {
    if (!databaseUrl || !databaseTls || !env.OPENROUTER_API_KEY?.trim()) {
      throw new Error("Live generation requires TLS PostgreSQL and OPENROUTER_API_KEY");
    }
    if (!inputPrice || !outputPrice || reserveMicros > Math.min(dailyBudgetMicros, monthlyBudgetMicros)) {
      throw new Error("Live generation requires explicit prices and sufficient daily and monthly budgets");
    }
  }

  // Default to socket addresses. Trust only explicitly configured ingress addresses.
  const trustProxy: FastifyServerOptions["trustProxy"] = env.TRUST_PROXY_CIDRS
    ? env.TRUST_PROXY_CIDRS.split(",").map((address) => address.trim())
    : false;

  return {
    provider,
    aiEnabled,
    production,
    databaseUrl,
    databaseTls,
    databaseCaFile,
    localDatabase: env.MOCK_DATABASE_DIR || "memory://",
    rateSecret,
    trustProxy,
    host: env.HOST || "127.0.0.1",
    port: integer(env, "PORT", 4000, 1, 65535),
    openRouterKey: env.OPENROUTER_API_KEY,
    model: env.AI_MODEL?.trim() || "openai/gpt-6-luna",
    maxInputTokens,
    maxOutputTokens,
    inputPrice,
    outputPrice,
    dailyBudgetMicros,
    monthlyBudgetMicros,
    reserveMicros: provider === "openrouter" ? reserveMicros : 0,
    dailyLimit: integer(env, "AI_DAILY_LIMIT", 20, 1, 1000),
    installRate: integer(env, "AI_INSTALL_REQUESTS_PER_MINUTE", 12, 1, 1000),
    ipRate: integer(env, "AI_IP_REQUESTS_PER_MINUTE", 120, 1, 10000),
    sessionRate: integer(env, "SESSION_CREATIONS_PER_IP_MINUTE", 20, 1, 1000),
    installConcurrency: integer(env, "AI_INSTALL_CONCURRENCY", 1, 1, 100),
    globalConcurrency: integer(env, "AI_GLOBAL_CONCURRENCY", 20, 1, 1000),
    timeoutMs: integer(env, "AI_TIMEOUT_MS", 15000, 100, 120000),
  } as const;
}

export type Config = ReturnType<typeof readConfig>;

function boolean(env: NodeJS.ProcessEnv, name: string, fallback: boolean) {
  if (env[name] === undefined || env[name] === "") return fallback;
  if (env[name] !== "true" && env[name] !== "false") throw new Error(`${name} must be true or false`);
  return env[name] === "true";
}

function number(env: NodeJS.ProcessEnv, name: string, fallback: number, min: number, max: number) {
  const value = env[name] === undefined || env[name] === "" ? fallback : Number(env[name]);
  if (!Number.isFinite(value) || value < min || value > max) throw new Error(`Invalid ${name}`);
  return value;
}

function integer(env: NodeJS.ProcessEnv, name: string, fallback: number, min: number, max: number) {
  const value = number(env, name, fallback, min, max);
  if (!Number.isInteger(value)) throw new Error(`${name} must be an integer`);
  return value;
}

function usd(env: NodeJS.ProcessEnv, name: string, fallback: number) {
  const value = number(env, name, fallback, 0, 1_000_000);
  return Math.floor(value * 1_000_000);
}
