# Security policy

**BetterBlockerDNS (BBDNS)** filters DNS on the phone, which means it sits in
the path of every lookup the device makes. A bug here is not cosmetic: it can
let traffic bypass the filter or break name resolution. This document says
which versions get fixes, what the app is supposed to do on the network, how
to report a problem privately, and what is out of scope.

## Supported versions

| Version | Supported |
|---|---|
| Latest release (see [Releases](https://github.com/buzzqw/BetterBlockerDNS/releases/latest)) | ✅ Fixes are released here |
| `main` branch, latest commit | ✅ Development, fixes land here first |
| Older releases | ⚠️ Update first: fixes ship only in the newest release |
| Forks and modified builds | ❌ Not supported |

Builds that do not come from the official release page may contain anything.
The app never phones home for an update check, so nothing will warn you that a
build is old: check the release page yourself.

## Official distribution

BBDNS is distributed only through:

- [GitHub Releases](https://github.com/buzzqw/BetterBlockerDNS/releases) — the
  signed APK attached to each release.

No other site, store or APK mirror is operated by this project. Builds from
elsewhere are unofficial: uninstall them and install an official build.

### Verifying a download

Every release APK is signed with the project key. To check a file you already
have:

```bash
# Android SDK build-tools, on any machine
apksigner verify --print-certs BetterBlockerDNS-<version>.apk
```

The digest is the identity of the signing key: it stays the same across
releases. Note it the first time and compare it after every update. If it
changes, do not install the file: install the previous release from the same
official page instead.

You can also rebuild from source and compare: the release process is documented
in the README in this repository, and a rebuild that behaves differently from
the released APK is a security report worth sending.

## What the app does on the network

BBDNS has no backend. There is no account, no analytics, no crash reporting and
no telemetry of any kind.

While protection is running, the app:

- **Creates a local VPN tunnel.** The tunnel starts and ends on the phone. No
  traffic is sent to a BBDNS server, because there is no BBDNS server. Non-DNS
  packets are forwarded to their destination unchanged.
- **Forwards allowed DNS queries** to the resolver you selected: a plain DNS
  server, or a DNS-over-HTTPS endpoint when you turn DoH on. That resolver sees
  the names you look up, exactly as it would without BBDNS.
- **Downloads the blocklists you enabled** from their public sources (HaGeZi,
  StevenBlack, AdGuard, and the rest of the catalogue).
- **Never decrypts TLS.** Blocking decisions are made from the queried name
  (and, with DoH on, from the CNAME aliases in the answer). There is no
  certificate, no interception proxy and no man-in-the-middle.
- **Keeps logs on the device.** Blocked domains, counters and the optional
  detailed log live in the app's private storage. They are never uploaded and
  you can clear them at any time.

The optional **notice page** is a small HTTP server bound to `127.0.0.1` on an
ephemeral port. It is reachable by other apps on the phone (every loopback
server is), so it is built to be useless to them:

- it only explains sites that are currently blocked, and it never relays a site
  that is not blocked or was not allowed from its own page;
- "continue" links are signed with a per-process key, so one app cannot lift
  another app's blocks;
- it refuses loopback, link-local and private upstream addresses, so a
  blocklist entry cannot turn it into a way to reach device services;
- it handles plain HTTP only, never `CONNECT`, and drops every request larger
  than 16 KiB of headers.

See [PRIVACY.md](PRIVACY.md) for the full data-handling statement.

## Reporting a vulnerability

**Do not open a public issue** for a security problem: it would expose every
installation before a fix exists.

Report privately through one of these:

1. **GitHub Security Advisories** — on the repository, *Security* →
   *Advisories* → *Report a vulnerability*. This keeps the report, the
   discussion and the fix private until the advisory is published.
2. **Email** — `azanzani@gmail.com`, with `BBDNS security` in the subject.

Please include, as far as you can:

- the app version (Settings → About) and Android version;
- whether DoH, the notice page, per-app rules or a proxy were enabled;
- what you expected and what happened, with the steps in between;
- any logs from *Detailed logs*, with the domains you consider private removed.

Expect an acknowledgement within a few days. Fixes are released in the newest
version and described in the changelog without exploit details. Credit is given
in the advisory unless you ask otherwise. There is no bug bounty: this is a
free project without revenue.

## In scope

- Bypassing the filter: a blocked name that resolves, a DNS path that escapes
  the tunnel, DoH/DoT leakage while *Block DNS bypass* is on.
- Wrong or dangerous answers built by the app: malformed DNS replies, cache
  poisoning, a forged reply accepted from a non-configured resolver.
- The local notice page: reaching a site through it without a signed link,
  SSRF into the phone or the local network, request smuggling, header
  injection.
- Privilege issues: escaping the VPN sandbox, reading another app's data,
  writing outside app storage.
- Backup and restore accepting malicious files that later execute or redirect
  traffic.
- Anything that sends data off the device without the user asking.

## Out of scope

- A blocklist that blocks something you need. Check *Why was this blocked?*
  and add an allow rule; if the rule is wrong for everyone, the list's own
  repository is the place to report it.
- Sites that stop working because they depend on a blocked tracker. That is the
  product doing its job; use the allowlist.
- The upstream resolver answering incorrectly.
- An attacker who already controls the phone (root, ADB, a malicious
  accessibility service) — at that point the tunnel can be removed as well.
- Missing hardening that has no demonstrated impact, and reports produced only
  by an automated scanner without a working proof of concept.

## Supported platforms

BBDNS runs on Android 7.0 (API 24) and newer. Devices older than that, or
custom ROMs that do not implement `VpnService` correctly, are not supported.
