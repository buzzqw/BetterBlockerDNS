#!/usr/bin/env bash
# Upload the signed app bundle of the current version to a Google Play track.
#
# The bundle is built by scripts/build_play_bundle.sh when it is missing, its
# certificate is checked against the project key, and the short changelog in
# metadata/en-US/changelogs/<versionCode>.txt is used as release notes.
#
# One-time setup (see docs/PLAY.md): create a service account in Google Cloud,
# enable the Google Play Android Developer API, invite the service account in
# Play Console with release permission and save its JSON key outside the
# repository, by default ~/.config/bbdns/play-service-account.json.
#
# Usage: scripts/upload_play_bundle.sh [--track internal|alpha|beta|production]
#                                     [--status completed|draft] [--notes FILE]
set -euo pipefail

cd "$(dirname "$0")/.."

release_env="${BBDNS_RELEASE_ENV:-$HOME/.config/bbdns/release.env}"
if [[ -f "$release_env" ]]; then
  # shellcheck disable=SC1090
  source "$release_env"
fi

track="internal"
status="completed"
notes=""
bundle="app/build/outputs/bundle/release/app-release.aab"
package="com.buzzqw.bbdns"

usage() {
  awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --track) track="${2:?missing value}"; shift 2 ;;
    --status) status="${2:?missing value}"; shift 2 ;;
    --notes) notes="${2:?missing value}"; shift 2 ;;
    --bundle) bundle="${2:?missing value}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "BBDNS: unknown argument: $1" >&2; usage >&2; exit 1 ;;
  esac
done

key="${BBDNS_PLAY_SERVICE_ACCOUNT:-$HOME/.config/bbdns/play-service-account.json}"
if [[ ! -f "$key" ]]; then
  echo "BBDNS: Play service account key missing: $key" >&2
  echo "BBDNS: see docs/PLAY.md (Upload from the command line). Nothing was uploaded." >&2
  exit 1
fi

if [[ ! -f "$bundle" ]]; then
  echo "BBDNS: bundle missing, building it first"
  bash scripts/build_play_bundle.sh
fi
if [[ ! -f "$bundle" ]]; then
  echo "BBDNS: bundle still missing at $bundle" >&2
  exit 1
fi

# Tightening: never upload something signed with another key.
expected="b6de8e179abd20d705d0084af4a6d667c5ce2962aa649758cc2e29f08ccc7eba"
actual="$(keytool -printcert -jarfile "$bundle" \
  | sed -n 's/^[[:space:]]*SHA256: //p' | head -n 1 \
  | tr -d ':' | tr 'A-Z' 'a-z')"
if [[ "$actual" != "$expected" ]]; then
  echo "BBDNS: bundle signed with an unexpected certificate ($actual)" >&2
  exit 1
fi

version_code="$(sed -n 's/^versionCode=//p' version.properties | head -n 1)"
version_name="$(sed -n 's/^versionName=//p' version.properties | head -n 1)"
if [[ -z "$version_code" ]]; then
  echo "BBDNS: versionCode missing in version.properties" >&2
  exit 1
fi

if [[ -z "$notes" ]]; then
  notes="metadata/en-US/changelogs/${version_code}.txt"
fi
if [[ ! -f "$notes" ]]; then
  echo "BBDNS: release notes missing: $notes" >&2
  exit 1
fi

echo "BBDNS: uploading $version_name ($version_code) to the $track track as $status"
exec python3 scripts/upload_play_bundle.py \
  --service-account "$key" \
  --bundle "$bundle" \
  --package "$package" \
  --track "$track" \
  --status "$status" \
  --version-code "$version_code" \
  --release-notes-file "$notes"
