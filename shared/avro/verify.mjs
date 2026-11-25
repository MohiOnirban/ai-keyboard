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
