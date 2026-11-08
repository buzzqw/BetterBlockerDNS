# BetterBlockerDNS (BBDNS)

**BetterBlockerDNS** is a free, open-source DNS ad blocker for Android. It blocks ads, trackers, malware, phishing and unwanted content system-wide through a local VPN — **no root, no privileged helpers and no data sent to external services**.

BBDNS is designed to be easy enough for everyday users, while still offering detailed controls for anyone who wants them.

[![Ko-fi](https://img.shields.io/badge/Ko--fi-Support%20BBDNS-FF5E5B?logo=ko-fi&logoColor=white)](https://ko-fi.com/buzzqw)
[![PayPal](https://img.shields.io/badge/PayPal-Donate-00457C?logo=paypal&logoColor=white)](https://www.paypal.com/donate/?business=azanzani%40gmail.com&item_name=BetterBlockerDNS)

---

## Features

- **VPN-only protection** — root mode, systemless hosts and privileged helper modes are not supported.
- **Native HaGeZi support** — the full catalogue is available in the app:
  - Multi LIGHT, NORMAL, PRO, PRO mini, PRO++, PRO++ mini, ULTIMATE, ULTIMATE mini
  - Threat Intelligence Feeds (full, medium, mini)
  - Fake, Pop-Up Ads, DynDNS, Badware Hoster, Most Abused TLDs
  - DoH / VPN / Tor / Proxy Bypass, URL Shortener, Anti-Piracy
  - Native Trackers (Apple, Samsung, Xiaomi, TikTok)
  - No Safesearch, NSFW, Gambling, Social Networks
- **Native StevenBlack support** — the unified hosts file plus the fakenews, gambling, porn and social extensions, including the pre-built combinations.
- **One-tap protection profiles** — Essential, Balanced, Pro (recommended), Aggressive and Maximum. Profiles only manage the HaGeZi Multi/TIF group, so your add-on selections are preserved.
- **Compatibility allowlist** — banking, payments, Italian public administration, app stores, push notifications and captive portals. The rule engine uses suffix matching, so `paypal.com` also covers `www.paypal.com`.
- **DNS over HTTPS (RFC 8484)** — encrypted DNS with a built-in provider catalogue: Cloudflare (standard/security/family), Google, Quad9 (standard/unsecured/ECS), AdGuard (standard/family), DNS.SB, Control D (standard/malware/ads/family), DNSBunker, DNS4EU (unfiltered/protective/child/noads/child+noads) and a custom endpoint. Responses are cached for 60 seconds and the configured plain DNS server is used only as a fallback. Disable Android Private DNS when DoH is active.
- **Many free plain DNS resolvers** — Google, Cloudflare (standard, malware, family), Quad9 (standard, unsecured, ECS), AdGuard (standard, family, non-filtering), OpenDNS (Home, FamilyShield), CleanBrowsing (Family, Adult, Security), DNS.SB, Control D (standard, malware, ads, family), DNS4EU (unfiltered/protective/child/adblock) and Yandex (Basic, Safe, Family).
- **Simple or detailed logs** — the log screen is readable by default; enable “Detailed logs” to see full hostnames, ports and source/destination endpoints.
- **Custom lists** — hosts, ABP, DNSMasq, wildcard, regex and plain domains.
- **Automatic updates** — from 6 hours to 1 week.
- **Per-app control** — Wi-Fi and cellular access, plus VPN bypass.
- **Quick Settings tile**, notifications, backup/restore and Material 3 theming.

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
- Go 1.21+ and Android NDK 27 (the native `nflog` libraries are built at compile time and are not committed)

From the command line:

```bash
./gradlew :app:assembleFdroidDebug
# or
./gradlew :app:assemblePlaystoreDebug
```

The APK is written to `app/build/outputs/apk/`.

Release builds are deterministic: the version lives in `version.properties`.
Before a release, run `./gradlew bumpVersion`, commit the new version, tag the
commit (for example `v1.0.25`) and build with `scripts/build_release.sh`. The
script sets `BBDNS_EMAIL_BUILD=1`, so the package is emailed to the owner;
external builders such as F-Droid call Gradle directly and never send mail.

Release signing reads `BBDNS_KEYSTORE`, `BBDNS_KEYSTORE_PASSWORD`,
`BBDNS_KEY_ALIAS` and `BBDNS_KEY_PASSWORD` from the environment.
`scripts/build_release.sh` loads them from `~/.config/bbdns/release.env`; keep
the keystore outside the repository and back it up. Without these variables
(for example on F-Droid) the build falls back to debug signing, which is fine
because F-Droid signs the APK with its own key.

---

## F-Droid

BBDNS is prepared for [F-Droid](https://f-droid.org/): the metadata lives in
`metadata/en-US/` and the build recipe template in `fdroid/`. See
`fdroid/README.md` for the submission steps.

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
