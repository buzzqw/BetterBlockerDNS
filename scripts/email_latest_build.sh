#!/usr/bin/env bash
# Send the release APK only if it comes from the build that just finished.
# The email carries a ZIP attachment plus a link served by the local HTTP
# server, because some mail clients do not download .apk attachments intact.
# Used by the Gradle hook after :app:assembleRelease.
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

LINK_FILE=$(mktemp)
bash scripts/serve_latest_build.sh > "$LINK_FILE"
LINK=$(cat "$LINK_FILE")
rm -f "$LINK_FILE"
echo "BBDNS: download link $LINK"

exec env BBDNS_DOWNLOAD_URL="$LINK" bash scripts/send_build_email.sh "$ZIP"
