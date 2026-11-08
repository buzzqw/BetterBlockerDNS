# Security Policy

BetterBlockerDNS (BBDNS) is a local-VPN DNS filter: it handles every DNS query
of the device and inspects raw packets in native code, so security reports are
taken seriously.

## Supported versions

| Version | Supported |
| ------- | --------- |
| Latest stable release (1.0.x) | :white_check_mark: |
| `nightly` pre-release | :white_check_mark: |
| Older releases | :x: (please reproduce on the latest version first) |

F-Droid builds are built and signed by F-Droid from the same source. A report
that only applies to a build from an unofficial source may not be reproducible.

## Reporting a vulnerability

**Please do not open a public issue for a security problem.**

Use GitHub's private vulnerability reporting:

1. Open <https://github.com/buzzqw/BetterBlockerDNS/security/advisories/new>
2. Describe the issue, the affected version/build and the impact.
3. Include reproduction steps or a proof of concept if possible.

If you cannot use GitHub, email **azanzani@gmail.com** with `[BBDNS SECURITY]`
in the subject. If you want to encrypt the report, ask for a PGP key first.

### What to include

- Affected version (from *Settings → About*, or the APK file name) and flavor
  (`playstore` or `fdroid`).
- Android version and device/ABI, if relevant.
- Steps to reproduce, expected vs. actual behavior.
- Impact assessment (for example: firewall bypass, DNS leak, data exposure,
  code execution).
- Any suggested fix, if you have one.

### What to expect

- Acknowledgement within **3 working days**.
- An initial assessment and severity classification within **10 working days**.
- A fix or mitigation plan for confirmed issues, and credit in the release
  notes if you want it.
- **Coordinated disclosure**: please give us **90 days** (or until a fixed
  release is available, whichever comes first) before publishing details.

This is a volunteer project: there is **no bug bounty**, but valid reports are
always credited unless you prefer to stay anonymous.

## Scope

In scope:

- The Android app (Kotlin/Compose, JNI/C in `app/src/main/cpp`, Go helpers).
- Packet filtering, DNS blocking, DNS-over-HTTPS and the VPN service.
- Local data handling: settings, passcode/pattern lock, logs, backup/restore.
- Build and release tooling (`scripts/`, Gradle configuration, signing).
- GitHub Actions workflows and the F-Droid recipe in `fdroid/`.

Out of scope (or lower priority):

- Reports that require a rooted device, an unlocked bootloader or a malicious
  app with device-administrator/accessibility privileges, unless they show a
  privilege escalation from a normal app.
- Denial of service caused by a blocklist *you* configured yourself, or by an
  upstream blocklist provider serving malicious content.
- Missing hardening that has no concrete attack path (we still welcome it as a
  normal issue or pull request).
- Spam, social engineering, and reports about the *effectiveness* of a
  particular blocklist.

## Dependency and code-scanning alerts

Runtime dependencies are kept up to date through Dependabot. Dependabot also
reports transitive libraries that belong to the **Gradle build toolchain**
(for example Netty, Bouncy Castle, Guava and protobuf pulled in by the Android
Gradle Plugin). Those libraries are executed by the build, not packaged in the
APK, so they are out of the app's attack surface; alerts that cannot be fixed
without an incompatible Android Gradle Plugin upgrade are dismissed with that
justification and reviewed when the toolchain is upgraded.

CodeQL code scanning runs on every push to `main` and on a weekly schedule.
Real findings are fixed in code; a dismissal always carries a written reason.

---

## Security model (short version)

- Blocklists and DoH endpoints are fetched over HTTPS with the platform trust
  store. There is no certificate pinning.
- DNS-over-HTTPS falls back to the configured plain DNS server when the
  encrypted resolver is unreachable; that fallback is unencrypted by design.
- Settings and downloaded lists live in the app's private storage. The
  passcode/pattern lock protects the UI only; it is not a disk-encryption
  mechanism.
- The VPN service and the quick-settings tile are exported only with
  system-level permissions (`BIND_VPN_SERVICE`, `BIND_QUICK_SETTINGS_TILE`).
- Release APKs are signed with the project key; nightly builds are signed with
  the same key from `main`. Debug builds are signed with the debug key and
  must never be treated as official.
