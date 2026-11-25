#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
npm --prefix backend run build
npm --prefix backend test
npm --prefix intelligence test
node shared/avro/verify.mjs
node intelligence/evaluate.ts > /dev/null
if [ "$(uname -s)" = Darwin ]; then
  mkdir -p artifacts
  xcrun swiftc -O -module-cache-path artifacts/swift-cache apps/ios/Common/Phonetic.swift apps/ios/Tests/PhoneticChecks.swift -o artifacts/phonetic-checks
  artifacts/phonetic-checks shared/phonetic.json
fi
