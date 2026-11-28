"""Generate an independent, repeatable English typo and preservation evaluation from a frequency list."""
import csv
import random
import sys

rng = random.Random(195221)
with open(sys.argv[1], encoding="utf-8") as source:
    lexicon = [word for lang, word, _ in csv.reader(source, delimiter="\t", quoting=csv.QUOTE_NONE)
               if lang == "en" and word.isascii() and word.isalpha()]
known = set(lexicon)
pool = [w for w in lexicon[200:9000] if 4 <= len(w) <= 14]
keyboard = {ch: (x + (10 - len(row)) / 2, y)
            for y, row in enumerate(("qwertyuiop", "asdfghjkl", "zxcvbnm"))
            for x, ch in enumerate(row)}
writer = csv.writer(sys.stdout, delimiter="\t", lineterminator="\n")
writer.writerow(["kind", "language", "context", "input", "expected"])
for n, word in enumerate(rng.sample(pool, 360)):
    i = rng.randrange(len(word) - 1)
    a, b = word[:i], word[i:]
    x, y = keyboard[b[0]]
    near = sorted(c for c, (u, v) in keyboard.items()
                  if 0 < (u - x)**2 + (v - y)**2 <= 1.3)
    if n % 5 == 0:
        typo = a + b[1:]
    elif n % 5 == 1:
        typo = a + b[1] + b[0] + b[2:]
    elif n % 5 == 2:
        typo = a + rng.choice(near) + b[1:]
    elif n % 5 == 3:
        typo = a + b[0] + b
    else:
        typo = a + rng.choice(near) + b
    # A valid word is ambiguous without a sentence and should remain untouched.
    if typo not in known:
        writer.writerow(["spelling", "en", "", typo, word])
    writer.writerow(["keep", "en", "", word, word])
