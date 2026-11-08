#!/usr/bin/env bash
# Publish the signed release APK to the self-hosted F-Droid repository that
# lives on the gh-pages branch. Run it after scripts/build_release.sh.
#
# Requirements:
#   - fdroidserver installed (`fdroid` in PATH, or set FDROID_BIN)
#   - repository signing key in ~/.config/bbdns/fdroid-repo.env
#     (BBDNS_REPO_KEYSTORE, BBDNS_REPO_KEYSTORE_PASSWORD,
#      BBDNS_REPO_KEY_ALIAS, BBDNS_REPO_KEY_PASSWORD)
#   - push access to the GitHub repository
set -euo pipefail

cd "$(dirname "$0")/.."

REPO_ENV="${BBDNS_FDROID_REPO_ENV:-$HOME/.config/bbdns/fdroid-repo.env}"
if [[ ! -f "$REPO_ENV" ]]; then
  echo "BBDNS: repository key not configured: $REPO_ENV" >&2
  exit 1
fi
# shellcheck disable=SC1090
source "$REPO_ENV"

: "${BBDNS_REPO_KEYSTORE:?BBDNS_REPO_KEYSTORE is missing}"
: "${BBDNS_REPO_KEYSTORE_PASSWORD:?BBDNS_REPO_KEYSTORE_PASSWORD is missing}"
: "${BBDNS_REPO_KEY_ALIAS:?BBDNS_REPO_KEY_ALIAS is missing}"
: "${BBDNS_REPO_KEY_PASSWORD:?BBDNS_REPO_KEY_PASSWORD is missing}"

FDROID_BIN="${FDROID_BIN:-fdroid}"
if ! command -v "$FDROID_BIN" >/dev/null 2>&1; then
  echo "BBDNS: fdroidserver not found (set FDROID_BIN to the fdroid binary)" >&2
  exit 1
fi

APK="${1:-app/build/outputs/apk/fdroid/release/app-fdroid-release.apk}"
if [[ ! -f "$APK" ]]; then
  echo "BBDNS: release APK not found: $APK" >&2
  exit 1
fi

VERSION_CODE="$(sed -n 's/^versionCode=//p' version.properties | head -n 1)"
VERSION_NAME="$(sed -n 's/^versionName=//p' version.properties | head -n 1)"
if [[ ! "$VERSION_CODE" =~ ^[0-9]+$ ]] || [[ -z "$VERSION_NAME" ]]; then
  echo "BBDNS: invalid version.properties" >&2
  exit 1
fi

REPO_URL="${BBDNS_FDROID_REPO_URL:-https://buzzqw.github.io/BetterBlockerDNS/repo}"
PAGES_BRANCH="${BBDNS_FDROID_REPO_BRANCH:-gh-pages}"
PAGES_GIT="${BBDNS_FDROID_REPO_GIT:-https://github.com/buzzqw/BetterBlockerDNS.git}"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

mkdir -p "$WORK/build/repo" "$WORK/build/metadata/en-US/images"
cp metadata/en-US/title.txt "$WORK/build/metadata/en-US/"
cp metadata/en-US/short_description.txt "$WORK/build/metadata/en-US/"
cp metadata/en-US/full_description.txt "$WORK/build/metadata/en-US/"
cp metadata/en-US/images/icon.png "$WORK/build/icon.png"
cp metadata/en-US/images/icon.png "$WORK/build/metadata/en-US/images/icon.png"
cp "$APK" "$WORK/build/repo/com.buzzqw.bbdns_${VERSION_CODE}.apk"

cat > "$WORK/build/metadata/com.buzzqw.bbdns.yml" <<'EOF'
Categories:
  - Internet
  - Security
License: GPL-3.0-or-later
AuthorName: buzzqw
AuthorEmail: azanzani@gmail.com
SourceCode: https://github.com/buzzqw/BetterBlockerDNS
IssueTracker: https://github.com/buzzqw/BetterBlockerDNS/issues
Changelog: https://github.com/buzzqw/BetterBlockerDNS/blob/HEAD/CHANGELOG.md
Donate: https://ko-fi.com/buzzqw
AutoName: BetterBlockerDNS
EOF

{
  echo 'sdk_path: /home/azanzani/.local/android-sdk'
  echo "repo_url: $REPO_URL"
  echo 'repo_name: BetterBlockerDNS'
  echo 'repo_description: Free, open-source DNS ad blocker for Android. VPN-only, no root.'
  echo 'repo_icon: icon.png'
  echo "repo_keyalias: $BBDNS_REPO_KEY_ALIAS"
  echo "keystore: $BBDNS_REPO_KEYSTORE"
  echo "keystorepass: '$BBDNS_REPO_KEYSTORE_PASSWORD'"
  echo "keypass: '$BBDNS_REPO_KEY_PASSWORD'"
  echo 'keydname: CN=buzzqw, OU=BetterBlockerDNS, O=buzzqw, C=IT'
  echo "serverwebroot: $REPO_URL"
  echo 'archive_older: 1'
  echo 'make_current_version_link: false'
} > "$WORK/build/config.yml"

(
  cd "$WORK/build"
  "$FDROID_BIN" update --pretty --use-date-from-apk --clean
)
rm -rf "$WORK/build/repo/status"

git clone --quiet --depth=1 --branch "$PAGES_BRANCH" "$PAGES_GIT" "$WORK/pages"
rm -rf "$WORK/pages/repo"
cp -a "$WORK/build/repo" "$WORK/pages/repo"

cd "$WORK/pages"
git add -A
if git diff --cached --quiet; then
  echo "BBDNS: F-Droid repository already up to date"
  exit 0
fi
git -c user.name=buzzqw -c user.email=azanzani@gmail.com \
  commit -m "Aggiorna il repository F-Droid a $VERSION_NAME"
git push origin "$PAGES_BRANCH"
echo "BBDNS: published $VERSION_NAME to $REPO_URL"
