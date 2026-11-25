// Exercise the normal API with only the checked-in fictional scenarios.
import { open, readFile } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';
import { buildApp } from '../src/app.ts';
import { readConfig } from '../src/config.ts';
import { makeProvider } from '../src/provider.ts';

const [outputPath, liveOption] = process.argv.slice(2);
if (!outputPath || (liveOption && liveOption !== '--live')) {
  throw new Error('Usage: node scripts/benchmark.mjs NEW_OUTPUT.json [--live]');
}
const fixtures = JSON.parse(await readFile(new URL('../../intelligence/scenarios.json', import.meta.url), 'utf8'));
const config = readConfig();
if ((config.provider === 'openrouter') !== (liveOption === '--live')) {
  throw new Error('Match the provider to the explicit --live option, or run in mock mode.');
}
if (Math.min(config.dailyLimit, config.installRate, config.ipRate) < fixtures.length) {
  throw new Error('Use a dedicated benchmark profile with daily and per-minute limits covering the fixture count.');
}
if (liveOption) {
  const approved = Number(process.env.BENCHMARK_MAX_USD);
  if (!Number.isFinite(approved) || approved <= 0 || config.reserveMicros * fixtures.length > approved * 1e6) {
    throw new Error('The worst-case sum of reservations exceeds the explicit BENCHMARK_MAX_USD.');
  }
}

const file = await open(outputPath, 'wx', 0o600);
const observations = [];
let providerMs;
let app;
let authorization;
let usage;
try {
  const provider = makeProvider(config);
  app = await buildApp(config, async (request, body) => {
    const started = performance.now();
    usage = await provider(request, body);
    providerMs = performance.now() - started;
    return usage;
  });
  const session = await app.inject({method:'POST',url:'/v1/sessions',payload:{}});
  if (session.statusCode !== 201) throw new Error('Could not start benchmark session.');
  authorization = 'Bearer ' + session.json().token;
  for (const fixture of fixtures) {
    const started = performance.now();
    usage = undefined;
    const result = await app.inject({
      method:'POST',url:'/v1/generate',payload:fixture.request,
      headers:{authorization,'idempotency-key':randomUUID()},
    });
    const latencyMs = performance.now() - started;
    if (result.statusCode !== 200) throw new Error('Stopped at ' + fixture.id + ', HTTP ' + result.statusCode);
    observations.push({
      id:fixture.id, suggestion:result.json().suggestion, latencyMs, providerMs,
      inputTokens:usage?.inputTokens, outputTokens:usage?.outputTokens,
      ...(usage?.billedMicros == null ? {} : { costUsd:usage.billedMicros / 1e6 }),
    });
    if (observations.length % 10 === 0) console.log('Completed',observations.length,'of',fixtures.length);
  }
} finally {
  try {
    await file.writeFile(JSON.stringify(observations,null,2) + '\n');
  } finally {
    await file.close();
    if (authorization) await app.inject({method:'DELETE',url:'/v1/session',headers:{authorization}});
    if (app) await app.close();
  }
}
console.log('Captured', observations.length, 'synthetic API observations using', config.provider);
