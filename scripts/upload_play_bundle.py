#!/usr/bin/env python3
"""Upload a signed app bundle to a Google Play track.

Called by scripts/upload_play_bundle.sh, which checks the bundle signature and
picks the changelog. Uses the Google Play Android Developer API with a service
account JSON key; no other dependency than Python 3 and `cryptography` (or the
`openssl` command as a fallback to sign the JWT).

The service account must be invited in Play Console (Users and permissions)
with release permission for the app, and the Google Play Android Developer API
must be enabled in its Google Cloud project. See docs/PLAY.md.
"""
import argparse
import base64
import json
import os
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

API = "https://androidpublisher.googleapis.com/androidpublisher/v3"
TOKEN_URL = "https://oauth2.googleapis.com/token"
SCOPE = "https://www.googleapis.com/auth/androidpublisher"
TIMEOUT = 300


def fail(message):
    print("BBDNS: %s" % message, file=sys.stderr)
    sys.exit(1)


def b64url(data):
    return base64.urlsafe_b64encode(data).rstrip(b"=")


def sign_rs256(message, pem):
    """Sign bytes with the service account private key."""
    try:
        from cryptography.hazmat.primitives import hashes, serialization
        from cryptography.hazmat.primitives.asymmetric import padding
    except ImportError:
        return sign_with_openssl(message, pem)
    key = serialization.load_pem_private_key(pem.encode(), password=None)
    return key.sign(message, padding.PKCS1v15(), hashes.SHA256())


def sign_with_openssl(message, pem):
    """Fallback for systems without the cryptography module."""
    handle, path = tempfile.mkstemp(suffix=".pem")
    try:
        with os.fdopen(handle, "w") as key_file:
            key_file.write(pem)
        result = subprocess.run(
            ["openssl", "dgst", "-sha256", "-sign", path],
            input=message,
            capture_output=True,
        )
        if result.returncode != 0:
            fail("openssl could not sign the token: %s"
                 % result.stderr.decode(errors="replace").strip())
        return result.stdout
    finally:
        try:
            os.unlink(path)
        except OSError:
            pass


def load_service_account(path):
    if not os.path.isfile(path):
        fail("service account key not found: %s\n"
             "Create one in Google Cloud (Google Play Android Developer API) and save it there." % path)
    try:
        with open(path, encoding="utf-8") as handle:
            info = json.load(handle)
    except (OSError, ValueError) as error:
        fail("cannot read %s: %s" % (path, error))
    for field in ("client_email", "private_key"):
        if not info.get(field):
            fail("%s is not a service account key (missing %s)" % (path, field))
    return info


def access_token(service_account):
    now = int(time.time())
    header = {"alg": "RS256", "typ": "JWT"}
    claims = {
        "iss": service_account["client_email"],
        "scope": SCOPE,
        "aud": TOKEN_URL,
        "iat": now,
        "exp": now + 3600,
    }
    unsigned = b".".join([
        b64url(json.dumps(header, separators=(",", ":")).encode()),
        b64url(json.dumps(claims, separators=(",", ":")).encode()),
    ])
    signature = sign_rs256(unsigned, service_account["private_key"])
    assertion = (unsigned + b"." + b64url(signature)).decode()

    body = urllib.parse.urlencode({
        "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
        "assertion": assertion,
    }).encode()
    request = urllib.request.Request(
        TOKEN_URL,
        data=body,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
    )
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            payload = json.load(response)
    except urllib.error.HTTPError as error:
        detail = error.read().decode(errors="replace")
        fail("Google refused the service account credentials (%s): %s\n"
             "Check that the key is active, the API is enabled and the account was "
             "invited in Play Console." % (error.code, detail.strip()))
    except urllib.error.URLError as error:
        fail("cannot reach Google: %s" % error.reason)
    token = payload.get("access_token")
    if not token:
        fail("Google returned no access token: %s" % payload)
    return token


def call(method, url, token, body=None, content_type="application/json"):
    data = None
    if body is not None:
        data = body if isinstance(body, bytes) else json.dumps(body).encode()
    request = urllib.request.Request(url, data=data, method=method)
    request.add_header("Authorization", "Bearer %s" % token)
    if data is not None:
        request.add_header("Content-Type", content_type)
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
            raw = response.read()
    except urllib.error.HTTPError as error:
        detail = error.read().decode(errors="replace")
        fail("%s %s failed (%s): %s" % (method, url, error.code, detail.strip()))
    except urllib.error.URLError as error:
        fail("%s %s failed: %s" % (method, url, error.reason))
    return json.loads(raw) if raw.strip() else {}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--service-account", required=True)
    parser.add_argument("--bundle", required=True)
    parser.add_argument("--package", required=True)
    parser.add_argument("--track", default="internal")
    parser.add_argument("--status", default="completed")
    parser.add_argument("--version-code", required=True, type=int)
    parser.add_argument("--release-notes-file")
    parser.add_argument("--language", default="en-US")
    args = parser.parse_args()

    if not os.path.isfile(args.bundle):
        fail("bundle not found: %s" % args.bundle)
    if args.status not in ("draft", "inProgress", "halted", "completed"):
        fail("invalid status: %s" % args.status)

    notes = ""
    if args.release_notes_file:
        try:
            with open(args.release_notes_file, encoding="utf-8") as handle:
                notes = handle.read().strip()
        except OSError as error:
            fail("cannot read release notes: %s" % error)

    service_account = load_service_account(args.service_account)
    token = access_token(service_account)

    base = "%s/applications/%s" % (API, args.package)
    edit = call("POST", "%s/edits" % base, token)["id"]
    print("BBDNS: edit %s opened for %s" % (edit, args.package))

    with open(args.bundle, "rb") as handle:
        bundle_bytes = handle.read()
    upload_url = "%s/edits/%s/bundles?uploadType=media" % (base, edit)
    uploaded = call("POST", upload_url, token, bundle_bytes,
                    content_type="application/octet-stream")
    print("BBDNS: uploaded %s (%.1f MB) as versionCode %s"
          % (os.path.basename(args.bundle), len(bundle_bytes) / 1048576.0,
             uploaded.get("versionCode", args.version_code)))

    release = {
        "versionCodes": [str(args.version_code)],
        "status": args.status,
    }
    if notes:
        release["releaseNotes"] = [{"language": args.language, "text": notes}]

    track_url = "%s/edits/%s/tracks/%s" % (base, edit, args.track)
    track = call("PUT", track_url, token, {"releases": [release]})
    print("BBDNS: track %s now has %s"
          % (args.track, ", ".join(
              "%s (%s)" % (",".join(r.get("versionCodes", [])), r.get("status"))
              for r in track.get("releases", []))))

    call("POST", "%s/edits/%s:commit" % (base, edit), token, {})
    print("BBDNS: edit committed on the %s track" % args.track)


if __name__ == "__main__":
    main()
