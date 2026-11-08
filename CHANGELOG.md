# Changelog

All notable changes to **BetterBlockerDNS (BBDNS)** are documented in this file.

Every commit gets an entry, written in English for end users: it explains what
changed and how it affects the app, without implementation details. Entries are
listed newest first.

## 2026-11-08

### Documentation: release process
- After each commit, BBDNS now asks whether to publish a new version on GitHub
  together with its changelog.
- Every new version is built and sent to the project owner.
- No change to the app's behaviour.

### Documentation: public changelog
- Added this changelog so every change to BBDNS is explained in plain English.
- No change to the app's behaviour.

### Support the project and check resource usage from Home
- Added a **Support BBDNS** card at the bottom of the Home screen with PayPal
  and Ko-fi buttons, so development can be supported directly from the app.
- Added a **Resource usage** card showing BBDNS CPU usage together with the
  device battery level and current. Values are read only while the Home screen
  is open: nothing is sampled in the background.
- The Home screen now shows **Recently blocked** domains only. The separate
  "most blocked domains" card was removed to keep the dashboard shorter and
  easier to read.

### Initial public release
- System-wide ad, tracker, malware and phishing blocking through a local VPN,
  without root or privileged helpers.
- Native HaGeZi and StevenBlack catalogues, one-tap protection profiles and a
  compatibility allowlist for banking, payments, public administration and
  app stores.
- DNS over HTTPS (RFC 8484) with a built-in provider catalogue, plus a wide
  choice of plain DNS resolvers.
- Simple or detailed logs, custom lists (hosts, ABP, DNSMasq, wildcard, regex,
  domains and TLDs), automatic updates, per-app Wi-Fi and cellular rules,
  backup/restore and Material 3 theming.
- English, Italian, German, French and Spanish translations.
