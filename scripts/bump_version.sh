#!/usr/bin/env bash
# Advance version.properties to the next 1.0.<n> release.
# Run it before the release commit, then tag that commit.
set -euo pipefail

cd "$(dirname "$0")/.."

file="version.properties"
if [[ ! -f "$file" ]]; then
  echo "version.properties not found" >&2
  exit 1
fi

current_code="$(sed -n 's/^versionCode=//p' "$file" | head -n 1)"
if [[ ! "$current_code" =~ ^[0-9]+$ ]]; then
  echo "invalid versionCode in $file: $current_code" >&2
  exit 1
fi

next_code=$((current_code + 1))
next_name="1.0.$((next_code - 1000000))"
printf 'versionCode=%s\nversionName=%s\n' "$next_code" "$next_name" > "$file"

echo "BBDNS: version bumped to $next_name ($next_code)"
