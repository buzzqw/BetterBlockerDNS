#!/usr/bin/env bash
# Email the owner a link to a published GitHub release, without attachments:
# GitHub builds and signs the APK (.github/workflows/release.yml), so there is
# no need to build it locally. Run it after the Release workflow has finished.
#
# Usage: scripts/email_release_link.sh [vX.Y.Z]   (default: version.properties)
set -euo pipefail

cd "$(dirname "$0")/.."

repo="buzzqw/BetterBlockerDNS"
tag="${1:-v$(sed -n 's/^versionName=//p' version.properties | head -n 1)}"
url="https://github.com/$repo/releases/tag/$tag"

notes=""
if command -v gh >/dev/null 2>&1; then
  if ! notes="$(gh release view "$tag" --repo "$repo" --json body -q .body 2>/dev/null)"; then
    echo "BBDNS: release $tag not found on GitHub yet (is the Release workflow done?)" >&2
    exit 1
  fi
fi

BBDNS_RELEASE_VERSION="${tag#v}" BBDNS_RELEASE_NOTES="$notes" \
  exec python3 scripts/send_build_email.py "$url"
