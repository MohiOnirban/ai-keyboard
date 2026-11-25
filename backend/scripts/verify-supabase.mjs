import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { readConfig } from '../src/config.ts';
import { buildApp } from '../src/app.ts';

const config = readConfig();
assert.equal(config.provider,'mock','This check uses only the mock provider.');
assert.ok(config.databaseUrl && config.databaseTls,'Specify a TLS PostgreSQL connection.');
const app = await buildApp(config);
try {
  const session = await app.inject({ method:'POST', url:'/v1/sessions', payload:{} });
  assert.equal(session.statusCode,201);
  const authorization = 'Bearer ' + session.json().token;
  try {
    const request = { method:'POST', url:'/v1/generate',
      headers:{ authorization, 'idempotency-key':randomUUID() },
      payload:{ mode:'rewrite', text:'I like you.', language:'en', style:'funny' } };
    const generated = await app.inject(request);
    assert.equal(generated.statusCode,200);
    assert.ok(generated.json().suggestion);
    assert.equal(generated.json().used,1);
    assert.equal((await app.inject(request)).statusCode,409);
  } finally {
    const revoked = await app.inject({method:'DELETE',url:'/v1/session',headers:{authorization}});
    assert.equal(revoked.statusCode,204);
  }
  assert.equal((await app.inject({url:'/v1/usage',headers:{authorization}})).statusCode,401);
  console.log('TLS PostgreSQL integration passed session creation, generation, accounting, duplicate protection and revocation.');
} finally {
  await app.close();
}
