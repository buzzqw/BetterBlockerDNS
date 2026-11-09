#!/usr/bin/env bash
# Start the BBDNS HTTPS file server if it is not already running.
# Prints the LAN HTTPS URL of the current release APK on stdout.
# The self-signed certificate lives outside the repository, in
# ~/.config/bbdns/tls, and is regenerated when the LAN IP changes.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PORT="${BBDNS_HTTPS_PORT:-8443}"
LOG="${BBDNS_HTTP_LOG:-/tmp/bbdns-file-server.log}"
META="$REPO_ROOT/app/build/outputs/apk/release/output-metadata.json"
TLS_DIR="${BBDNS_TLS_DIR:-$HOME/.config/bbdns/tls}"
CERT="$TLS_DIR/cert.pem"
KEY="$TLS_DIR/key.pem"

IP=$(ip -4 route get 1.1.1.1 2>/dev/null | awk '{for (i = 1; i <= NF; i++) if ($i == "src") { print $(i + 1); exit }}')
[[ -z "$IP" ]] && IP=127.0.0.1

# (Re)create the certificate when missing or issued for another IP.
if [[ ! -f "$CERT" || ! -f "$KEY" ]] ||
   ! openssl x509 -in "$CERT" -noout -ext subjectAltName 2>/dev/null | grep -q "IP Address:${IP}\$\|IP Address:${IP},"; then
  mkdir -p "$TLS_DIR"
  chmod 700 "$TLS_DIR"
  openssl req -x509 -newkey rsa:2048 -nodes -days 825 \
    -keyout "$KEY" -out "$CERT" -subj "/CN=BBDNS build server" \
    -addext "subjectAltName=IP:${IP},IP:127.0.0.1,DNS:localhost" \
    -addext "extendedKeyUsage=serverAuth" >> "$LOG" 2>&1
  chmod 600 "$KEY"
  # A running server still uses the old certificate: restart it.
  pkill -f "scripts/serve_latest_build.py" 2>/dev/null || true
  sleep 0.3
fi

if ! ss -ltn 2>/dev/null | grep -q ":${PORT} "; then
  # Redirect every descriptor of the detached server: it must never inherit
  # a caller's pipe, otherwise command substitution would wait forever.
  (
    cd "$REPO_ROOT"
    BBDNS_HTTP_HOST="$IP" BBDNS_HTTP_PORT="$PORT" BBDNS_TLS_CERT="$CERT" BBDNS_TLS_KEY="$KEY" \
      setsid python3 scripts/serve_latest_build.py \
      >> "$LOG" 2>&1 < /dev/null &
  ) >> "$LOG" 2>&1 < /dev/null
  sleep 0.7
fi

# Versioned file name: avoids stale copies in the browser/download cache.
VERSION=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["elements"][0]["versionName"])' "$META" 2>/dev/null || true)
[[ -z "$VERSION" ]] && VERSION=$(date +%s)

echo "https://${IP}:${PORT}/BetterBlockerDNS-${VERSION}.apk"
