#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p out
find src test -name '*.java' -print > out/sources.txt
javac --release 17 -Xlint:all -d out @out/sources.txt
java -cp out Main
java -cp out facebook.RulesTest
