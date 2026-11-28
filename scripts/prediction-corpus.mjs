import { readFileSync, writeFileSync } from 'node:fs';
import { examples } from '../intelligence/examples.ts';

// Only authored training examples are eligible. Evaluation cases and user input are excluded.
const rows = new Set();
for (const { language, text, variants } of examples) {
  for (const sample of [text, ...Object.values(variants)]) {
    for (const sentence of sample.normalize('NFC').split(/[.!?।]+/u)) {
      const clean = sentence.trim().replace(/\s+/gu, ' ');
      if (clean) rows.add(language + '\t' + clean);
    }
  }
}
const dialogue = new URL('../intelligence/typing-dialogue.tsv', import.meta.url);
for (const row of readFileSync(dialogue, 'utf8').trim().split('\n')) {
  if (!/^(?:en|bn|banglish)\t[^\t]+$/u.test(row)) throw Error('Invalid dialogue row');
  rows.add(row.normalize('NFC'));
}
const output = Array.from(rows).join('\n') + '\n';
const path = new URL('../shared/predict.tsv', import.meta.url);
if (process.argv.includes('--check')) {
  if (readFileSync(path, 'utf8') !== output) throw Error('Regenerate shared/predict.tsv');
} else {
  writeFileSync(path, output);
}
console.log(rows.size + ' offline prediction sentences');
