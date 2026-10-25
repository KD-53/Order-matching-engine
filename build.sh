#!/usr/bin/env bash
# Compiles the engine and tests with plain javac, then runs the tests.
set -euo pipefail
cd "$(dirname "$0")"

rm -rf out
javac -d out/main $(find src/main/java -name '*.java')
javac -d out/test -cp out/main $(find src/test/java -name '*.java')

java -cp "out/main$( [ "${OS:-}" = "Windows_NT" ] && echo ';' || echo ':' )out/test" engine.OrderBookTest
