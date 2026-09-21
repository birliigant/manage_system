#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"

cd "$ROOT_DIR"

mkdir -p out

javac -encoding UTF-8 -cp lib/h2-2.2.224.jar -d out $(find src/main/java -name "*.java")

exec java -cp out:src/main/resources:lib/h2-2.2.224.jar org.example.Main
