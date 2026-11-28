"""Summarize TSV output from scripts/measure-prediction.sh (no third-party modules)."""
import csv
import json
import sys
from collections import defaultdict
from pathlib import Path


def summarize(path):
    groups = defaultdict(lambda: dict(cases=0, top_one=0, top_three=0,
                                      auto_correct=0, auto_wrong=0, changed_protected=0))
    timing = None
    with path.open(encoding="utf-8", newline="") as stream:
        for row in csv.reader(stream, delimiter="\t", quoting=csv.QUOTE_NONE):
            if row[0] == "TIMING":
                timing = dict(cold_ms=float(row[1]), queries=int(row[2]),
                              p50_ms=float(row[3]), p95_ms=float(row[4]))
                continue
            kind, _, literal, expected, first, hit, automatic = row
            if kind == "spelling" and " " in expected:
                kind = "segmentation"
            result = groups[kind]
            result["cases"] += 1
            norm = str if kind == "keep" else str.casefold
            result["top_one"] += norm(first) == norm(expected)
            result["top_three"] += hit.lower() == "true"
            if automatic:
                correct = norm(automatic) == norm(expected)
                result["auto_correct"] += correct
                result["auto_wrong"] += not correct
            if kind == "keep":
                result["changed_protected"] += literal != first
    if timing is None:
        raise ValueError(f"Measurement did not finish: {path}")
    return {"groups": dict(groups), "timing": timing}


if __name__ == "__main__":
    print(json.dumps({Path(arg).name: summarize(Path(arg)) for arg in sys.argv[1:]},
                     indent=2, ensure_ascii=False))
