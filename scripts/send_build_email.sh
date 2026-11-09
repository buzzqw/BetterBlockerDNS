#!/usr/bin/env bash
# Send the compact BBDNS release APK (or a download URL) by email.
# SMTP credentials are read from the environment or from
# ~/.config/bbdns/mail.env; see scripts/send_build_email.py for the variables.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
RELEASE_APK="$REPO_ROOT/app/build/outputs/apk/release/app-release.apk"

# --check and other options go straight to the Python sender.
if [[ $# -gt 0 && "$1" == -* ]]; then
  exec python3 "$SCRIPT_DIR/send_build_email.py" "$@"
fi

if [[ $# -gt 0 ]]; then
  TARGET="$1"
else
  TARGET="$RELEASE_APK"
  if [[ ! -f "$TARGET" ]]; then
    echo "Release APK not found, building :app:assembleRelease" >&2
    (cd "$REPO_ROOT" && ./gradlew :app:assembleRelease --console=plain)
  fi
fi

# URLs are passed through; files must exist.
if [[ "$TARGET" != http://* && "$TARGET" != https://* && ! -f "$TARGET" ]]; then
  echo "APK not found: $TARGET" >&2
  exit 2
fi

# Always send the package as ZIP: mail providers and Drive handle it better
# than a raw .apk. The Python sender uploads the ZIP to Drive when
# BBDNS_DRIVE_DIR / BBDNS_DRIVE_REMOTE / BBDNS_GDRIVE_BIN is configured.
if [[ "$TARGET" == *.apk ]]; then
  ZIP="${TARGET%.apk}.zip"
  rm -f "$ZIP"
  (cd "$(dirname "$TARGET")" && zip -q -9 "$(basename "$ZIP")" "$(basename "$TARGET")")
  TARGET="$ZIP"
fi

exec python3 "$SCRIPT_DIR/send_build_email.py" "$TARGET"
