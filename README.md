<p align="center">
  <img src="metadata/en-US/images/icon.png" alt="BetterBlockerDNS icon" width="96" height="96">
</p>

<h1 align="center">BetterBlockerDNS (BBDNS)</h1>

**BetterBlockerDNS** is a free, open-source DNS ad blocker for Android. It blocks ads, trackers, malware, phishing and unwanted content system-wide through a local VPN — **no root, no privileged helpers and no data sent to external services**.

BBDNS is designed to be easy enough for everyday users, while still offering detailed controls for anyone who wants them.

<p align="center">
  <img src="metadata/en-US/images/featureGraphic.png" alt="BetterBlockerDNS — DNS ad blocking, no root" width="800">
</p>

<p align="center">
  <a href="https://github.com/buzzqw/BetterBlockerDNS/releases/latest"><img src="https://img.shields.io/github/v/release/buzzqw/BetterBlockerDNS?label=release" alt="Latest release"></a>
  <a href="https://www.gnu.org/licenses/gpl-3.0.html"><img src="https://img.shields.io/badge/license-GPL--3.0-blue" alt="License: GPL-3.0"></a>
  <img src="https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 7.0+">
  <a href="https://ko-fi.com/buzzqw"><img src="https://img.shields.io/badge/Ko--fi-Support%20BBDNS-FF5E5B?logo=ko-fi&logoColor=white" alt="Ko-fi"></a>
  <a href="https://www.paypal.com/donate/?business=azanzani%40gmail.com&item_name=BetterBlockerDNS"><img src="https://img.shields.io/badge/PayPal-Donate-00457C?logo=paypal&logoColor=white" alt="PayPal"></a>
</p>

---

## Screenshots

<p align="center">
  <img src="metadata/en-US/images/phoneScreenshots/1.png" alt="Home dashboard with protection active" width="30%">
  <img src="metadata/en-US/images/phoneScreenshots/2.png" alt="Blocklists and protection presets" width="30%">
  <img src="metadata/en-US/images/phoneScreenshots/3.png" alt="Protection profiles" width="30%">
</p>
<p align="center">
  <img src="metadata/en-US/images/phoneScreenshots/4.png" alt="DNS resolver and DNS over HTTPS" width="30%">
  <img src="metadata/en-US/images/phoneScreenshots/5.png" alt="DNS server list" width="30%">
  <img src="metadata/en-US/images/phoneScreenshots/6.png" alt="Settings" width="30%">
</p>

---

## Install

- **GitHub Releases**: download the signed APK from the
  [latest release](https://github.com/buzzqw/BetterBlockerDNS/releases/latest).

BBDNS will be proposed to F-Droid after three months of maintenance.

---

## Features

- **VPN-only protection** — root mode, systemless hosts and privileged helper modes are not supported.
- **Native [HaGeZi](https://github.com/hagezi/dns-blocklists) support** — the full catalogue is available in the app:
  - Multi LIGHT, NORMAL, PRO, PRO mini, PRO++, PRO++ mini, ULTIMATE, ULTIMATE mini
  - Threat Intelligence Feeds (full, medium, mini)
  - Fake, Pop-Up Ads, DynDNS, Badware Hoster, Most Abused TLDs
  - DoH / VPN / Tor / Proxy Bypass, URL Shortener, Anti-Piracy
  - Native Trackers (Apple, Samsung, Xiaomi, TikTok)
  - No Safesearch, NSFW, Gambling, Social Networks
- **Native [StevenBlack](https://github.com/StevenBlack/hosts) support** — the unified hosts file plus the fakenews, gambling, porn and social extensions, including the pre-built combinations.
- **One-tap protection profiles** — Essential, Balanced, Pro (recommended), Aggressive and Maximum. Profiles only manage the [HaGeZi](https://github.com/hagezi/dns-blocklists) Multi/TIF group, so your add-on selections are preserved.
- **Compatibility allowlist** — banking, payments, public administration, app stores, push notifications and captive portals. The rule engine uses suffix matching, so `paypal.com` also covers `www.paypal.com`.
- **DNS over HTTPS (RFC 8484)** — encrypted DNS with a built-in provider catalogue: Cloudflare (standard/security/family), Google, Quad9 (standard/unsecured/ECS), AdGuard (standard/family), DNS.SB, Control D (standard/malware/ads/family), DNSBunker, DNS4EU (unfiltered/protective/child/noads/child+noads) and a custom endpoint. Responses are cached for 60 seconds and the configured plain DNS server is used only as a fallback. Disable Android Private DNS when DoH is active.
- **Many free plain DNS resolvers** — Google, Cloudflare (standard, malware, family), Quad9 (standard, unsecured, ECS), AdGuard (standard, family, non-filtering), OpenDNS (Home, FamilyShield), CleanBrowsing (Family, Adult, Security), DNS.SB, Control D (standard, malware, ads, family), DNS4EU (unfiltered/protective/child/adblock) and Yandex (Basic, Safe, Family).
- **Simple or detailed logs** — the log screen is readable by default; enable “Detailed logs” to see full hostnames, ports and source/destination endpoints.
- **Custom lists** — hosts, ABP, DNSMasq, wildcard, regex and plain domains.
- **Automatic updates** — from 6 hours to 1 week.
- **Per-app control** — Wi-Fi and cellular access, plus VPN bypass.
- **Quick Settings tile and home screen widget** — see the status and blocked requests at a glance, and start, stop or resume protection with one tap.
- **Automation intents** — let Tasker, MacroDroid, Home Assistant or `adb` control protection. See [Automation](docs/AUTOMATION.md).
- Notifications, backup/restore and Material 3 theming.

---

## Performance and battery life

BBDNS is optimized at every layer, from the native packet loop to the Compose
UI, so always-on protection does not drain the battery.

- **Native packet engine** — the VPN data path runs in C. The JNI class, method
  and direction lookups are cached once and a reusable buffer replaces the array
  allocated for every packet. Only new TCP connections reach the Kotlin rule
  engine, so established streams are forwarded without any JNI round-trip.
- **One lookup per connection** — the app owning a flow is resolved once per
  4-tuple and reused for 5 minutes, non-DNS traffic is logged once per
  connection, and reverse DNS answers are cached for 10 minutes, failures
  included. Background traffic no longer keeps the processor, the radio and the
  database busy.
- **In-memory blocklist index** — lists are compiled at load time into hash
  sets, suffix sets, TLD sets and pre-compiled regexes, so every match takes a
  fast path even with very large lists. The full reload is skipped when the
  downloaded files have not changed.
- **Respectful updates** — automatic blocklist updates run through WorkManager
  and follow the saved interval even after a restart.
- **DNS over HTTPS cache** — encrypted answers are reused for 60 seconds, so
  repeated queries avoid a new TLS connection.
- **Quiet in the background** — the status notification refreshes every 15
  seconds instead of every 5, and the live CPU, battery and traffic graphs are
  sampled only while their screen is visible: there is no polling when the
  screen is closed.
- **Small footprint** — no root services, privileged helpers, daemons or
  analytics; the app only runs its VPN service while protection is enabled.

Measured against the previous version on the same Android 16 test device
(debug build, 78,000 blocklist rules):

| Test | Result |
|---|---|
| CPU while moving 10 MB of traffic through the VPN | **about 40% lower** |
| CPU while idle with the screen off | **about 35% lower**, no wake-lock held |
| DNS queries blocked and recorded during the test | **200 out of 200** |

The measurements come from an emulator with a debug build, so the absolute
times are higher than on a phone; the percentages compare the same workload
before and after the optimization.

The Home screen shows live CPU and battery usage, so the real cost of protection
can be checked at any time.

---

## Project structure

```
app/src/main/java/com/buzzqw/bbdns/
├── core/                 # logging, utilities, constants
├── data/                 # database, cache, repositories, services
├── domain/               # models, use cases, managers
├── presentation/         # Compose UI, navigation, ViewModels
│   └── screens/settings/subSettings/dns/
│       ├── hosts/        # blocklist parser and rule database
│       └── lists/        # HaGeZi + StevenBlack catalogues, profiles, allowlist
└── service/              # VPN, firewall, tile, notifications
```

---

## Build

Requirements:

- Android Studio Ladybug or newer
- JDK 17+
- Android SDK 36 (minSdk 24)
- Go 1.25+ and Android NDK 27 (the native `nflog` libraries are built at compile time and are not committed)

From the command line:

```bash
./gradlew :app:assembleFdroidDebug
# or
./gradlew :app:assemblePlaystoreDebug
```

The APK is written to `app/build/outputs/apk/`.

Every commit is also built by the `Build` workflow. CI artifacts are for
verification only, are not signed with the release key and are never published
as a release.

Release builds are deterministic: the version lives in `version.properties`.
Before a release, run `./gradlew bumpVersion`, commit the new version, tag the
commit (for example `v1.0.29`) and build with `scripts/build_release.sh`. The
script sets `BBDNS_EMAIL_BUILD=1`, so the package is emailed to the owner;
external builders that call Gradle directly never send mail.

Release signing reads `BBDNS_KEYSTORE`, `BBDNS_KEYSTORE_PASSWORD`,
`BBDNS_KEY_ALIAS` and `BBDNS_KEY_PASSWORD` from the environment.
`scripts/build_release.sh` loads them from `~/.config/bbdns/release.env`; keep
the keystore outside the repository and back it up. Without these variables
the build falls back to debug signing.

---

## Security

Found a security issue? **Do not open a public issue.** Report it privately via
[GitHub Security Advisories](https://github.com/buzzqw/BetterBlockerDNS/security/advisories/new)
or email **azanzani@gmail.com** with `[BBDNS SECURITY]` in the subject. See
[SECURITY.md](.github/SECURITY.md) for the supported versions, scope and
coordinated-disclosure policy.

---

## Support the project

BBDNS is free and has no paywall. If you want to support development:

- **PayPal**: [Donate with PayPal](https://www.paypal.com/donate/?business=azanzani%40gmail.com&item_name=BetterBlockerDNS)
- **Ko-fi**: [ko-fi.com/buzzqw](https://ko-fi.com/buzzqw)

---

## License

BBDNS is distributed under the **GNU General Public License v3.0**. Copyright notices of the original authors are retained in the source files. The HaGeZi and StevenBlack lists keep their own licenses and attribution requirements.

- GPLv3: <https://www.gnu.org/licenses/gpl-3.0.html>
- HaGeZi DNS Blocklists: <https://github.com/hagezi/dns-blocklists>
- StevenBlack hosts: <https://github.com/StevenBlack/hosts>
