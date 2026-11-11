#!/usr/bin/env bash
# Build the signed app bundle for the Google Play Console.
# The bundle is signed with the same key as the APKs, so Play delivers updates
# that install over the GitHub build once the key is registered in Play Console
# (see docs/PLAY.md). Nothing is uploaded: the file is only built and checked.
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

BBDNS_SKIP_EMAIL=1 ./gradlew :app:bundleRelease "$@"

bundle="app/build/outputs/bundle/release/app-release.aab"
if [[ ! -f "$bundle" ]]; then
  echo "BBDNS: app bundle not found at $bundle" >&2
  exit 1
fi

# Refuse to hand over a bundle signed with anything but the project key.
expected="b6de8e179abd20d705d0084af4a6d667c5ce2962aa649758cc2e29f08ccc7eba"
actual="$(keytool -printcert -jarfile "$bundle" \
  | sed -n 's/^[[:space:]]*SHA256: //p' | head -n 1 \
  | tr -d ':' | tr 'A-Z' 'a-z')"
if [[ "$actual" != "$expected" ]]; then
  echo "BBDNS: bundle signed with an unexpected certificate ($actual)" >&2
  exit 1
fi

echo "BBDNS: bundle ready: $bundle"
echo "BBDNS: certificate SHA-256: $actual"
