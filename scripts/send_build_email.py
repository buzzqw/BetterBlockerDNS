#!/usr/bin/env python3
"""Send a BBDNS build by email.

SMTP configuration is read from environment variables or from
~/.config/bbdns/mail.env (KEY=VALUE lines). No credentials are stored in the
repository.

  BBDNS_SMTP_HOST   SMTP server hostname (required)
  BBDNS_SMTP_PORT   SMTP port (default: 587)
  BBDNS_SMTP_USER   SMTP username
  BBDNS_SMTP_PASS   SMTP password / app password
  BBDNS_SMTP_MODE   starttls | ssl | plain (default: starttls)
  BBDNS_MAIL_FROM   sender address (default: BBDNS_SMTP_USER)
  BBDNS_IMAP_HOST   IMAP host for delivery checks (default: derived from SMTP)
  BBDNS_DOWNLOAD_URL  optional HTTP link added to the message body
  BBDNS_DRIVE_DIR   local Google Drive (or other synced) folder that receives the ZIP
  BBDNS_DRIVE_REMOTE  optional rclone destination, e.g. "gdrive:BBDNS"
  BBDNS_GDRIVE_BIN  optional gdrive binary (default: gdrive on PATH)

Gmail often answers 552 for APK attachments while still delivering them to the
owner's inbox: in that case the script verifies the message over IMAP and
compares the received attachment SHA-256 with the local file.

Usage:
  send_build_email.py <apk-or-other-file>
  send_build_email.py <https-url>
  send_build_email.py --check
"""

import hashlib
import imaplib
import os
import shutil
import smtplib
import ssl
import subprocess
import sys
import time
from email import message_from_bytes
from email.message import EmailMessage
from email.utils import make_msgid
from pathlib import Path

RECIPIENT = "azanzani@gmail.com"
DEFAULT_ENV_FILE = "~/.config/bbdns/mail.env"
PASSWORD_PLACEHOLDER = "SOSTITUISCI_CON_APP_PASSWORD"
# Gmail rejects messages larger than 25 MB, attachment encoding included.
GMAIL_MAX_MESSAGE = 25 * 1024 * 1024


def load_env_file(path: Path) -> None:
    """Load KEY=VALUE lines without overriding variables already set."""
    if not path.is_file():
        return
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[len("export "):].lstrip()
        key, sep, value = line.partition("=")
        if not sep:
            continue
        key = key.strip()
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        if key:
            os.environ.setdefault(key, value)


def connect(host: str, port: int, mode: str):
    """Open an SMTP connection according to the configured security mode."""
    if mode == "ssl":
        return smtplib.SMTP_SSL(
            host, port, context=ssl.create_default_context(), timeout=60
        )
    smtp = smtplib.SMTP(host, port, timeout=60)
    if mode == "starttls":
        smtp.starttls(context=ssl.create_default_context())
    return smtp


def imap_host() -> str:
    """IMAP host used to verify delivery; derived from the SMTP host."""
    configured = os.environ.get("BBDNS_IMAP_HOST", "").strip()
    if configured:
        return configured
    smtp = os.environ.get("BBDNS_SMTP_HOST", "").strip()
    if smtp.startswith("smtp."):
        return "imap." + smtp[len("smtp."):]
    return smtp


def upload_to_drive(path: Path) -> str | None:
    """Copy a build to Google Drive and return a shareable link when possible.

    Supports, in order:
      * BBDNS_DRIVE_DIR: a local folder synced by Google Drive/pCloud/etc.
      * BBDNS_DRIVE_REMOTE: an rclone destination (``rclone link`` gives the URL).
      * gdrive (old CLI) found on PATH or in BBDNS_GDRIVE_BIN.

    The function never fails the email: it prints a note and returns None when
    no Drive target is configured.
    """
    # 1. A local synced folder is the simplest and works without any API.
    drive_dir = os.environ.get("BBDNS_DRIVE_DIR", "").strip()
    if drive_dir:
        target_dir = Path(drive_dir).expanduser()
        try:
            target_dir.mkdir(parents=True, exist_ok=True)
            destination = target_dir / path.name
            shutil.copy2(path, destination)
            print(f"Drive: copied {path.name} to {destination}")
            return None
        except OSError as exc:
            print(f"Drive: cannot copy to {target_dir}: {exc}", file=sys.stderr)

    # 2. rclone remote.
    remote = os.environ.get("BBDNS_DRIVE_REMOTE", "").strip()
    if remote and shutil.which("rclone"):
        try:
            subprocess.run(
                ["rclone", "copy", str(path), remote],
                check=True,
                capture_output=True,
                text=True,
                timeout=300,
            )
            link = subprocess.run(
                ["rclone", "link", f"{remote.rstrip('/')}/{path.name}"],
                check=True,
                capture_output=True,
                text=True,
                timeout=60,
            ).stdout.strip().splitlines()
            url = link[0] if link else None
            print(f"Drive: uploaded {path.name} with rclone" + (f" ({url})" if url else ""))
            return url
        except (subprocess.SubprocessError, OSError) as exc:
            print(f"Drive: rclone upload failed: {exc}", file=sys.stderr)

    # 3. gdrive CLI.
    binary = os.environ.get("BBDNS_GDRIVE_BIN", "").strip() or shutil.which("gdrive")
    if not binary:
        print(
            "Drive: no upload target configured (set BBDNS_DRIVE_DIR, "
            "BBDNS_DRIVE_REMOTE or BBDNS_GDRIVE_BIN).",
            file=sys.stderr,
        )
        return None
    parent = os.environ.get("BBDNS_DRIVE_FOLDER_ID", "").strip()
    command = [binary, "upload"]
    if parent:
        command += ["--parent", parent]
    command.append(str(path))
    try:
        result = subprocess.run(
            command, check=True, capture_output=True, text=True, timeout=300
        )
        output = (result.stdout or "") + (result.stderr or "")
        print(output.strip())
        # Old gdrive prints "Uploaded <id> at <speed>" and newer ones print the id.
        file_id = None
        for line in output.splitlines():
            line = line.strip()
            if line.lower().startswith("uploaded "):
                parts = line.split()
                if len(parts) >= 2:
                    file_id = parts[1]
                    break
            if line and "://" not in line and len(line) > 10 and " " not in line:
                file_id = line
        if file_id:
            return f"https://drive.google.com/file/d/{file_id}/view"
    except (subprocess.SubprocessError, OSError) as exc:
        print(f"Drive: gdrive upload failed: {exc}", file=sys.stderr)
    return None


def verify_delivery(message_id: str, expected: Path | None, user: str, password: str) -> bool:
    """Check that Gmail delivered the message, attachment included.

    Gmail may answer 552 ("content presents a potential security issue") and
    still deliver to the owner's inbox. Poll IMAP for the Message-ID and
    compare the received attachment with the local file.
    """
    expected_digest = None
    if expected is not None:
        expected_digest = hashlib.sha256(expected.read_bytes()).hexdigest()

    host = imap_host()
    if not host:
        return False

    for _ in range(20):
        try:
            with imaplib.IMAP4_SSL(host, 993, timeout=30) as im:
                im.login(user, password)
                im.select("INBOX", readonly=True)
                typ, data = im.search(None, f'HEADER Message-ID "{message_id}"')
                ids = data[0].split()
                if ids:
                    typ, raw = im.fetch(ids[-1], "(RFC822)")
                    message = message_from_bytes(raw[0][1])
                    if expected_digest is None:
                        return True
                    for part in message.walk():
                        if not part.get_filename():
                            continue
                        payload = part.get_payload(decode=True) or b""
                        return hashlib.sha256(payload).hexdigest() == expected_digest
                    return False
        except (imaplib.IMAP4.error, OSError):
            pass
        time.sleep(3)
    return False


def main() -> int:
    load_env_file(Path(os.environ.get("BBDNS_MAIL_ENV", DEFAULT_ENV_FILE)).expanduser())

    check_only = "--check" in sys.argv[1:]
    args = [arg for arg in sys.argv[1:] if arg != "--check"]

    if not check_only and not args:
        print("usage: send_build_email.py <apk|url> | --check", file=sys.stderr)
        return 2

    target = args[0].strip() if args else ""
    is_url = target.startswith("http://") or target.startswith("https://")
    attachment = None
    drive_url = None
    if not check_only and not is_url:
        attachment = Path(target).expanduser().resolve()
        if not attachment.is_file():
            print(f"APK not found: {attachment}", file=sys.stderr)
            return 2
        # Put the build on Drive as ZIP when a target is configured. The email
        # always carries the link, and the attachment itself when it fits.
        drive_url = upload_to_drive(attachment)
        encoded_size = 4 * ((attachment.stat().st_size + 2) // 3)
        if encoded_size > GMAIL_MAX_MESSAGE:
            if drive_url:
                print(
                    f"File too large for Gmail ({encoded_size / (1024 * 1024):.1f} MB "
                    "once encoded); sending the Drive link only.",
                    file=sys.stderr,
                )
                target = drive_url
                is_url = True
                attachment = None
            else:
                print(
                    f"File too large for Gmail: {attachment.name} becomes ~"
                    f"{encoded_size / (1024 * 1024):.1f} MB once encoded "
                    f"(limit {GMAIL_MAX_MESSAGE // (1024 * 1024)} MB). "
                    "Configure BBDNS_DRIVE_DIR/BBDNS_DRIVE_REMOTE or send the "
                    "release APK instead.",
                    file=sys.stderr,
                )
                return 5

    host = os.environ.get("BBDNS_SMTP_HOST", "").strip()
    if not host:
        print(
            "BBDNS_SMTP_HOST is not configured; cannot send the build. "
            "Set the SMTP variables or write them to "
            f"{DEFAULT_ENV_FILE}.",
            file=sys.stderr,
        )
        return 3

    try:
        port = int(os.environ.get("BBDNS_SMTP_PORT", "587"))
    except ValueError:
        print("BBDNS_SMTP_PORT must be a number.", file=sys.stderr)
        return 3

    user = os.environ.get("BBDNS_SMTP_USER", "").strip()
    password = os.environ.get("BBDNS_SMTP_PASS", "")
    mode = os.environ.get("BBDNS_SMTP_MODE", "starttls").lower()
    sender = os.environ.get("BBDNS_MAIL_FROM", user or RECIPIENT)

    if mode not in {"starttls", "ssl", "plain"}:
        print(f"Unknown BBDNS_SMTP_MODE: {mode}", file=sys.stderr)
        return 3

    if password == PASSWORD_PLACEHOLDER:
        print(
            "BBDNS_SMTP_PASS still contains the placeholder. Put a Google app "
            f"password in {DEFAULT_ENV_FILE}.",
            file=sys.stderr,
        )
        return 3

    try:
        with connect(host, port, mode) as smtp:
            if user:
                smtp.login(user, password)
            if check_only:
                print(f"SMTP OK: {user or '(anonymous)'} @ {host}:{port} ({mode})")
                return 0

            msg = EmailMessage()
            msg["Message-ID"] = make_msgid(domain="bbdns.local")
            if is_url:
                msg["Subject"] = "BBDNS build download link"
                msg["From"] = sender
                msg["To"] = RECIPIENT
                msg.set_content(
                    "New BetterBlockerDNS build available at:\n\n"
                    f"{target}\n\n"
                    "The APK is signed with the debug key and is a universal "
                    "F-Droid release build.\n"
                )
            else:
                assert attachment is not None
                digest = hashlib.sha256(attachment.read_bytes()).hexdigest()
                msg["Subject"] = f"BBDNS build: {attachment.name}"
                msg["From"] = sender
                msg["To"] = RECIPIENT
                body = (
                    "New BetterBlockerDNS build attached.\n\n"
                    f"File: {attachment.name}\n"
                    f"Size: {attachment.stat().st_size / (1024 * 1024):.1f} MB\n"
                    f"SHA-256: {digest}\n"
                )
                if drive_url:
                    body += f"\nGoogle Drive: {drive_url}\n"
                link = os.environ.get("BBDNS_DOWNLOAD_URL", "").strip()
                if link:
                    body += f"\nDirect download (LAN): {link}\n"
                if attachment.suffix.lower() == ".zip":
                    body += (
                        "\nThe ZIP contains the APK: extract it on the phone and "
                        "install it.\n"
                    )
                if attachment.suffix.lower() == ".txt" or attachment.name.lower().endswith(".b64"):
                    body += (
                        "\nThis is a Base64 text attachment because some providers "
                        "reject APK attachments. Decode it with:\n"
                        f"  base64 -d {attachment.name} > BetterBlockerDNS.apk\n"
                    )
                msg.set_content(body)
                msg.add_attachment(
                    attachment.read_bytes(),
                    maintype="application",
                    subtype="vnd.android.package-archive"
                    if attachment.suffix.lower() == ".apk"
                    else "octet-stream",
                    filename=attachment.name,
                )

            smtp.send_message(msg)
    except smtplib.SMTPDataError as exc:
        # Gmail may flag APK attachments with a 552 but still deliver them to
        # the owner's inbox. Verify with IMAP before reporting a failure.
        if exc.smtp_code == 552 and user and password:
            print(
                "Gmail returned 552 (potential security issue); "
                "checking if the message was delivered anyway...",
                file=sys.stderr,
            )
            if verify_delivery(msg["Message-ID"], attachment, user, password):
                print(f"Build delivered to {RECIPIENT} despite the SMTP warning: {target}")
                return 0
        print(f"SMTP error: {exc}", file=sys.stderr)
        return 4
    except (smtplib.SMTPException, OSError) as exc:
        print(f"SMTP error: {exc}", file=sys.stderr)
        return 4

    print(f"Build emailed to {RECIPIENT}: {target}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
