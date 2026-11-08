# IzzyOnDroid inclusion request — com.buzzqw.bbdns

Paste this text into a new issue at
<https://codeberg.org/IzzyOnDroid/repodata/issues> after checking the current
issue template and labels.

---

**Title:** New app: BetterBlockerDNS (com.buzzqw.bbdns)

## App details

- **Name:** BetterBlockerDNS (BBDNS)
- **Package:** `com.buzzqw.bbdns`
- **Version:** 1.0.29 (`versionCode` 1000029)
- **Tag:** `v1.0.29`
- **APK:** <https://github.com/buzzqw/BetterBlockerDNS/releases/download/v1.0.29/app-fdroid-release.apk>
- **APK size:** about 6.7 MB
- **Source:** <https://github.com/buzzqw/BetterBlockerDNS>
- **License:** GPL-3.0-or-later
- **Issue tracker:** <https://github.com/buzzqw/BetterBlockerDNS/issues>
- **Min SDK:** 24 · **Target/compile SDK:** 36
- **Categories:** Internet, Security
- **Fastlane entry point:** `/metadata/en-US/` (short and full description,
  512×512 icon, 1024×500 feature graphic, six phone screenshots and
  `changelogs/1000029.txt`)

## What it does

BBDNS is a local-VPN DNS filter for Android. It blocks ads, trackers, malware
and phishing system-wide using DNS filtering, with native HaGeZi and
StevenBlack catalogues, one-tap protection profiles, per-app Wi-Fi/cellular
rules, DNS-over-HTTPS, custom blocklists and local request logs. Everything
runs on the device: no account, no analytics, no telemetry.

## Fork attribution

BBDNS includes GPL-3.0 code derived from DNSNet
(<https://github.com/t895/DNSNet>), DNS66 and NetGuard. The original copyright
notices are kept in the source files. The BBDNS name, icon, feature graphic,
screenshots, description and feature set are specific to this project.

## Policy checklist

- [x] FOSS license (GPL-3.0-or-later); source publicly available on GitHub
- [x] No trackers, ads, analytics or telemetry
- [x] No proprietary components or Google Play Services
- [x] No `android:usesCleartextTraffic`; HTTPS only for blocklists and DoH
- [x] APK signed with the release key, no `android:debuggable`/`android:testOnly`
- [x] APK attached to the GitHub release, smaller than 30 MB
- [x] Release tags match `versionName` (`v1.0.29` → 1.0.29)
- [x] Unique package name, app name, icon, feature graphic and screenshots
- [x] Fastlane metadata present in the repository
- [x] Intention to maintain the app for the foreseeable future

## Reviewer notes

- `QUERY_ALL_PACKAGES` is required only to list installed apps for the per-app
  firewall. Package names never leave the device.
- The app is VPN-only: no root, no Shizuku, no privileged helpers.
- The release signer certificate SHA-256 is
  `b6de8e179abd20d705d0084af4a6d667c5ce2962aa649758cc2e29f08ccc7eba`.
- The previous F-Droid submission was closed because the project had not been
  maintained for more than three months and was not considered to offer unique
  features. BBDNS is now developed continuously and has its own branding,
  feature set and release process.
