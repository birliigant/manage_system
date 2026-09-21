#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"

cd "$ROOT_DIR"

mkdir -p out

javac -encoding UTF-8 -cp lib/h2-2.2.224.jar -d out $(find src/main/java -name "*.java")

case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*)
    CLASSPATH_SEPARATOR=';'
    ;;
  *)
    CLASSPATH_SEPARATOR=':'
    ;;
esac

CLASSPATH="out${CLASSPATH_SEPARATOR}src/main/resources${CLASSPATH_SEPARATOR}lib/h2-2.2.224.jar"

exec java -cp "$CLASSPATH" org.example.Main
