import { readFile } from "node:fs/promises";
import { Pool } from "pg";
import type { Config } from "./config.ts";

export interface Queryable {
  query<Row extends Record<string, unknown>>(sql: string, params?: unknown[]): Promise<{ rows: Row[] }>;
}

interface Database extends Queryable {
  transaction<T>(work: (tx: Queryable) => Promise<T>): Promise<T>;
  close(): Promise<void>;
}

export async function connect(config: Config): Promise<Database> {
  if (config.databaseUrl) {
    const ca = config.databaseCaFile ? await readFile(config.databaseCaFile, "utf8") : undefined;
    const pool = new Pool({
      connectionString: config.databaseUrl,
      ssl: config.databaseTls ? { ca, rejectUnauthorized: true } : false,
      connectionTimeoutMillis: 15000,
      statement_timeout: 5000,
      idle_in_transaction_session_timeout: 5000,
    });
    // A pooled idle socket can fail without a query waiting to handle its error.
    pool.on("error", () => console.error("A PostgreSQL connection closed unexpectedly."));
    return {
      query: <Row extends Record<string, unknown>>(sql: string, params?: unknown[]) => pool.query<Row>(sql, params),
      async transaction(work) {
        const client = await pool.connect();
        let connectionFailure: Error | undefined;
        try {
          await client.query("begin");
          const value = await work(client);
          await client.query("commit");
          return value;
        } catch (error) {
          try {
            await client.query("rollback");
          } catch {
            connectionFailure = new Error("PostgreSQL could not roll back");
          }
          throw error;
        } finally {
          client.release(connectionFailure);
        }
      },
      close: () => pool.end(),
    };
  }

  const { PGlite } = await import("@electric-sql/pglite");
  const db = await PGlite.create(config.localDatabase);
  const migration = new URL(
    "../../infrastructure/supabase/migrations/20261125204106_private_usage.sql",
    import.meta.url,
  );
  try {
    await db.exec(await readFile(migration, "utf8"));
  } catch (error) {
    await db.close();
    throw error;
  }

  return {
    query: db.query.bind(db),
    transaction: (work) => db.transaction(work),
    close: () => db.close(),
  };
}

export type { Database };
