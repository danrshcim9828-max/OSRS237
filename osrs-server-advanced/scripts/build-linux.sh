#!/usr/bin/env bash
set -euo pipefail

TASK="${1:-build}"

if ! command -v java >/dev/null 2>&1; then
  echo "Java not found. Install JDK 21 and retry." >&2
  exit 1
fi

if ! command -v gradle >/dev/null 2>&1; then
  echo "Gradle not found. Install Gradle 8.7+ and retry." >&2
  exit 1
fi

echo "Detected Java:"
java -version

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$PROJECT_DIR"
gradle --no-daemon clean "$TASK"
