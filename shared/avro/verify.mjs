import { createHash } from 'node:crypto';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
const file = name => fs.readFileSync(new URL(name,import.meta.url),'utf8');
const context = {};
vm.runInNewContext(file('avro-lib.js'),context);
const engine = context.OmicronLab.Avro.Phonetic;
const grammar = JSON.parse(file('grammar.json'));
assert.equal(JSON.stringify(engine.data),JSON.stringify(grammar));
const words = JSON.parse(file('../phonetic.json')).words;
for (const [roman,bangla] of Object.entries(words)) {
  assert.ok(/^[a-z-]+$/i.test(roman));
  assert.ok(/[\u0980-\u09ff]/u.test(bangla));
}
console.log(grammar.patterns.length,'Avro patterns match upstream;',Object.keys(words).length,'word overrides');

// Both native-adapter and upstream results use sorted UTF-8 input/output pairs.
const fixture = JSON.parse(file('../tests/avro-parity.json'));
const inputs = new Set();
for (const pattern of grammar.patterns) {
  for (const prefix of fixture.prefixes) {
    for (const suffix of fixture.suffixes) inputs.add(prefix + pattern.find + suffix);
  }
}
const digest = createHash('sha256');
for (const input of [...inputs].sort()) {
  digest.update(input + '\t' + engine.parse(input) + '\n');
}
assert.equal(inputs.size, fixture.uniqueInputs);
assert.equal(digest.digest('hex'), fixture.sha256);
console.log(inputs.size, 'upstream phonetic outputs match the Android parity fixture');
