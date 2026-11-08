#!/usr/bin/env bash
# Build the signed F-Droid release package and email it to the owner.
# F-Droid and other external builders call Gradle directly, so they never
# trigger the email hook.
set -euo pipefail

cd "$(dirname "$0")/.."

release_env="${BBDNS_RELEASE_ENV:-$HOME/.config/bbdns/release.env}"
if [[ -f "$release_env" ]]; then
  # shellcheck disable=SC1090
  source "$release_env"
fi

if [[ -z "${BBDNS_KEYSTORE:-}" ]]; then
  echo "BBDNS: release keystore not configured (set BBDNS_KEYSTORE or create $release_env)" >&2
  exit 1
fi
if [[ ! -f "$BBDNS_KEYSTORE" ]]; then
  echo "BBDNS: release keystore not found: $BBDNS_KEYSTORE" >&2
  exit 1
fi

BBDNS_EMAIL_BUILD=1 exec ./gradlew :app:assembleFdroidRelease "$@"
