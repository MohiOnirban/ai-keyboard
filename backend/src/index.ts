import { buildApp } from "./app.ts";
import { readConfig } from "./config.ts";

async function main() {
  const config = readConfig();
  const app = await buildApp(config);
  try {
    await app.listen({ port: config.port, host: config.host });
  } catch (error) {
    await app.close();
    throw error;
  }
  console.log(`Keyboard backend listening on ${config.host}:${config.port} (${config.aiEnabled ? config.provider : "AI disabled"})`);

  for (const signal of ["SIGINT", "SIGTERM"] as const) {
    process.once(signal, () => {
      void app.close().catch(() => { process.exitCode = 1; });
    });
  }
}

main().catch(() => {
  console.error("Keyboard backend could not start. Check its configuration, database connection and migrations.");
  process.exitCode = 1;
});
