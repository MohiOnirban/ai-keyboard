"""Make repeatable synthetic English swipe paths. These do not measure human gesture accuracy."""
import csv
import math
import random
import sys

rng = random.Random(731901)
keys = {ch: (i + (10 - len(row)) / 2, y)
        for y, row in enumerate(("qwertyuiop", "asdfghjkl", "zxcvbnm"))
        for i, ch in enumerate(row)}
with open(sys.argv[1], encoding="utf-8") as source:
    words = [word for lang, word, _ in csv.reader(source, delimiter="\t", quoting=csv.QUOTE_NONE)
             if lang == "en" and 3 <= len(word) <= 15 and all(c in keys for c in word)]
writer = csv.writer(sys.stdout, delimiter="\t", lineterminator="\n")
for word in rng.sample(words[:4500], 160):
    # Perturb intended key centers and round corners. One trace per independent word.
    centers = [(keys[c][0] + rng.uniform(-0.33, 0.33), keys[c][1] + rng.uniform(-0.31, 0.31))
               for c in word]
    path = [centers[0]]
    for a, b in zip(centers, centers[1:]):
        steps = max(2, math.ceil(math.dist(a, b) * 2))
        for i in range(1, steps + 1):
            f = i / steps
            path.append((a[0] + f * (b[0] - a[0]), a[1] + f * (b[1] - a[1])))
    smooth = [path[0]]
    for a, b, c in zip(path, path[1:], path[2:]):
        smooth.append(tuple((a[k] + 2 * b[k] + c[k]) / 4 for k in range(2)))
    smooth.append(path[-1])
    writer.writerow([word, " ".join(f"{x:.3f},{y:.3f}" for x, y in smooth)])
# Negative controls: a tap, a sub-key drag, an off-board start and a wildly implausible loop.
for path in ["1,1", "1,1 1.1,1 1.2,1", "-4,-4 2,1 5,2", "0,0 0,15 9,15 9,0"]:
    writer.writerow(["-", path])
