#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
npm --prefix backend run build
npm --prefix backend test
npm --prefix intelligence test
node scripts/prediction-corpus.mjs --check
node shared/avro/verify.mjs
node intelligence/evaluate.ts > /dev/null
if [ "$(uname -s)" = Darwin ]; then
  mkdir -p artifacts
  xcrun swiftc -O -module-cache-path artifacts/swift-cache apps/ios/Common/Phonetic.swift apps/ios/Tests/PhoneticChecks.swift -o artifacts/phonetic-checks
  artifacts/phonetic-checks shared/phonetic.json
  xcrun swiftc -O -module-cache-path artifacts/swift-cache apps/ios/Common/Predictor.swift apps/ios/Tests/PredictionChecks.swift -o artifacts/prediction-checks
  artifacts/prediction-checks
fi
