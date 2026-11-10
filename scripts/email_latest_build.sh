#!/usr/bin/env bash
# Send the release APK only if it comes from the build that just finished.
# The email carries a ZIP attachment: mail clients handle it more reliably
# than a raw .apk. Used by the Gradle hook after :app:assembleRelease.
set -euo pipefail

cd "$(dirname "$0")/.."

APK="app/build/outputs/apk/release/app-release.apk"
ZIP="app/build/outputs/apk/release/BetterBlockerDNS-release.zip"

if [[ ! -f "$APK" ]]; then
  echo "BBDNS: release APK missing, email skipped" >&2
  exit 0
fi

# A failed build leaves the previous APK in place: do not email it again.
if [[ "${BBDNS_FORCE_SEND:-0}" != "1" ]] &&
   [[ -z "$(find "$APK" -newermt '-10 minutes' -print -quit)" ]]; then
  echo "BBDNS: release APK is stale, email skipped (BBDNS_FORCE_SEND=1 to override)" >&2
  exit 0
fi

# ZIP attachment: mail clients handle it more reliably than a raw .apk.
(cd "$(dirname "$APK")" && rm -f "$(basename "$ZIP")" &&
  zip -q -9 "$(basename "$ZIP")" "$(basename "$APK")")

exec bash scripts/send_build_email.sh "$ZIP"
