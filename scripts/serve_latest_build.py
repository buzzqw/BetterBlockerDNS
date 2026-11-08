#!/usr/bin/env python3
"""Serve the latest BBDNS release APK (and its ZIP) over HTTPS.

TLS is enabled when BBDNS_TLS_CERT and BBDNS_TLS_KEY point at a certificate
and key (scripts/serve_latest_build.sh creates a self-signed pair outside the
repository); without them the server falls back to plain HTTP.

The server reads the files on every request, so new builds are served without
restarting it. Used by scripts/serve_latest_build.sh, which is started by the
Gradle email hook after a release build.

It listens on 127.0.0.1 by default; set BBDNS_HTTP_HOST to bind another
interface (scripts/serve_latest_build.sh does this to publish the LAN URL).
"""

import os
import re
import ssl
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parent.parent
APK = ROOT / "app/build/outputs/apk/fdroid/release/app-fdroid-release.apk"
ZIP = ROOT / "app/build/outputs/apk/fdroid/release/BetterBlockerDNS-release.zip"
PORT = int(os.environ.get("BBDNS_HTTP_PORT", "8099"))
# The helper has no authentication: expose it on the loopback interface by
# default. Set BBDNS_HTTP_HOST=0.0.0.0 only when a tunnel or reverse proxy is
# intentionally publishing it.
HOST = os.environ.get("BBDNS_HTTP_HOST", "127.0.0.1")

# A requested name is echoed in the Content-Disposition header. Strip anything
# that could break out of the header or the quoted string (CR/LF included).
SAFE_FILENAME = re.compile(r"[^A-Za-z0-9._-]+")


def safe_filename(name: str, fallback: str) -> str:
    cleaned = SAFE_FILENAME.sub("_", Path(name).name).strip("._")
    return cleaned if cleaned else fallback


class Handler(BaseHTTPRequestHandler):
    def _serve(self, file: Path, content_type: str, filename: str) -> None:
        if not file.is_file():
            self.send_error(404, "Not found")
            return
        data = file.read_bytes()
        self.send_response(200)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Content-Disposition", f'attachment; filename="{filename}"')
        # New builds must never be masked by a cached copy in the browser.
        self.send_header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
        self.send_header("Pragma", "no-cache")
        self.send_header("Expires", "0")
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(data)

    def _route(self):
        path = unquote(self.path.split("?", 1)[0])
        name = Path(path).name
        if path in ("/", "") or name.endswith(".apk"):
            # Versioned names keep each build out of the browser cache.
            filename = safe_filename(name, "BetterBlockerDNS.apk")
            self._serve(APK, "application/vnd.android.package-archive", filename)
        elif name.endswith(".zip"):
            self._serve(ZIP, "application/zip", safe_filename(name, "BetterBlockerDNS.zip"))
        else:
            self.send_error(404, "Not found")

    def do_GET(self):
        self._route()

    def do_HEAD(self):
        self._route()

    def log_message(self, fmt, *args):
        print(fmt % args, flush=True)


def main() -> int:
    server = ThreadingHTTPServer((HOST, PORT), Handler)
    cert = os.environ.get("BBDNS_TLS_CERT")
    key = os.environ.get("BBDNS_TLS_KEY")
    scheme = "http"
    if cert and key:
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.minimum_version = ssl.TLSVersion.TLSv1_2
        context.load_cert_chain(cert, key)
        server.socket = context.wrap_socket(server.socket, server_side=True)
        scheme = "https"
    print(f"BBDNS file server on {scheme}://{HOST}:{PORT}", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    return 0


if __name__ == "__main__":
    sys.exit(main())
