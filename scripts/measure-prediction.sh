#!/bin/zsh
set -euo pipefail
project=${0:A:h:h}
cd "$project"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
dataset=${2:-shared/tests/typing-evaluation.tsv}
case ${1:-} in
  ios)
    xcrun swiftc -O -module-cache-path "$work/cache" apps/ios/Common/Predictor.swift \
      shared/tests/prediction/Measure.swift -o "$work/measure"
    /usr/bin/time -l "$work/measure" shared/predict.tsv "$dataset" shared/wordfreq.tsv
    ;;
  android)
    gradle_modules=${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1
    compiler_jars=(
      "$gradle_modules"/org.jetbrains.kotlin/kotlin-compiler-embeddable/2.2.10/*/*.jar
      "$gradle_modules"/org.jetbrains.kotlin/kotlin-stdlib/2.2.10/*/*.jar
      "$gradle_modules"/org.jetbrains.kotlin/kotlin-script-runtime/2.2.10/*/*.jar
      "$gradle_modules"/org.jetbrains.kotlin/kotlin-reflect/1.6.10/*/*.jar
      "$gradle_modules"/org.jetbrains.kotlin/kotlin-daemon-embeddable/2.2.10/*/*.jar
      "$gradle_modules"/org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm/1.8.0/*/*.jar
      "$gradle_modules"/org.jetbrains/annotations/13.0/*/*.jar
    )
    runtime_jars=(
      "$gradle_modules"/org.jetbrains.kotlin/kotlin-stdlib/2.2.10/*/*.jar
      "$gradle_modules"/org.json/json/20250517/*/*.jar
    )
    android=apps/android/app/src/main/java/com/kothakeys/keyboard
    java -cp "${(j.:.)compiler_jars}" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
      -jvm-target 17 -no-stdlib -no-reflect -cp "${(j.:.)runtime_jars}" -d "$work/measure.jar" \
      "$android/OfflinePrediction.kt" "$android/TypingModel.kt" shared/android/Avro.kt shared/tests/prediction/Measure.kt
    /usr/bin/time -l java -cp "$work/measure.jar:${(j.:.)runtime_jars}" com.kothakeys.keyboard.MeasureKt \
      shared/predict.tsv "$dataset" shared/wordfreq.tsv
    ;;
  *)
    printf '%s\n' 'Usage: zsh scripts/measure-prediction.sh {ios|android} [dataset.tsv]' >&2
    exit 2
    ;;
esac
