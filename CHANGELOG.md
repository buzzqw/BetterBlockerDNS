# Changelog

All notable changes to **BetterBlockerDNS (BBDNS)** are documented in this file.

Every commit gets an entry, written in English for end users: it explains what
changed and how it affects the app, without implementation details. Entries are
listed newest first.

Entries dated before 2026-11-07 are kept from the project's earlier history.

## 2026-11-08

### Release 1.0.27
- Published the interface and translation improvements listed below.
- No changes to DNS blocking, DNS over HTTPS or the blocklist catalogues.

### Interface improvements
- Removed the non-working email button from About and corrected the remaining
  links, including the developer link to GitHub.
- Logs are now enabled by default, and the protection method appears above the
  log options.
- Clearer descriptions for the permanent notification and for blocking port 80.
- AMOLED colours are enabled automatically on first launch when the device
  reports an OLED screen.
- Added Simplified Chinese and Brazilian Portuguese. The language list now
  follows the translation files shipped with the app, so new languages appear
  automatically.
- The About logo is now animated.
- The protection profile can be selected directly from a drop-down in
  Blocklists.
- Removed the redundant "Manage blocklists" entry from the Blocked domains
  screen.

### Continuous builds
- Removed the nightly pre-release channel. Every commit is now built and
  checked by CI, without publishing a new package.
- Stable versions continue to be published only as tagged releases with their
  changelog.
- No change to the app's behaviour.

### Code scanning
- Code security scanning now covers the Android app's Kotlin code in addition
  to the native, Go and script sources.
- Scans run on every update to the main branch and on a weekly schedule.
- The Go helper and the native code are built during the scan, so they are
  analysed together with the app.
- No change to the app's behaviour.

### Release 1.0.26
- Added a security policy with clear instructions for reporting problems
  privately.
- Automatic Android backups no longer include the app-lock passcode/pattern or
  the DNS query history.
- Release packages now verify the integrity of the build tools they download,
  and the build pipeline uses reviewed, pinned tool versions.
- Removed unused code and a bundled library that the app did not need, reducing
  the attack surface and the download size.
- No changes to DNS blocking, DNS over HTTPS, the blocklist catalogues or the
  app's interface.

### Repository cleanup
- Removed the temporary self-hosted F-Droid repository and its publishing
  script; the source repository now has only the `main` branch.
- The official F-Droid submission remains in approval.
- No change to the app's behaviour.

### Self-hosted F-Droid repository
- Added a signed F-Droid repository on GitHub Pages, so BBDNS can be installed
  and updated from F-Droid while official inclusion is pending.
- Added a script to publish new releases to that repository.
- No change to the app's behaviour.

### README cleanup
- Removed a redundant note about the GitHub Sponsor button from the README.
- No change to the app's behaviour.

### F-Droid recipe validation
- Validated the F-Droid build metadata with `fdroid lint`; the donation field
  and changelog URL now follow F-Droid conventions.
- No change to the app's behaviour.

### Donation links
- Added PayPal and Ko-fi links to the README, the GitHub Sponsor button and the
  repository homepage.
- No change to the app's behaviour.

### Release 1.0.25
- Release APKs are now signed with the project's release key, so GitHub
  builds install and update consistently.
- The F-Droid recipe and changelog point to version 1.0.25.
- No visible changes.

### Release 1.0.24
- Published the F-Droid-ready build. No visible changes: this release contains
  the release tooling, metadata and repository cleanup.
- The F-Droid recipe now points to the 1.0.24 release commit.

### Repository cleanup
- Removed an unused local-tool entry from the project ignore rules.
- No change to the app's behaviour.

### F-Droid recipe alignment
- The F-Droid recipe now points to the commit that contains the 1.0.23 release
  metadata.
- No change to the app's behaviour.

### F-Droid build preparation
- Version information now lives in the repository, so local, CI and F-Droid
  builds produce the same version name and code.
- Added an F-Droid build recipe and submission notes.
- Added scripts to advance the version and to build the release package.
- Release builds send email only when explicitly requested, so external
  builders such as F-Droid never try to send mail.
- Added the F-Droid changelog for version 1.0.23.
- No change to the app's behaviour.

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

### Smarter default protection
- Malware and ad protection are now enabled by default for new installations.
- Lists already covered by a broader list are skipped, so BBDNS does not
  download or process the same domains twice.

### Profile and DNS at a glance
- The Home screen shows the active protection profile and the DNS resolver in
  use.
- Default blocklist selection is better balanced between protection and
  compatibility.

### Faster blocked-domain analysis
- The app now finds the reason a domain was blocked much faster, especially
  with large blocklists.

### Clearer blocklist management
- Blocked domains, protection profiles and the compatibility allowlist now have
  separate, easier to find screens.
- Added an **Active lists** screen that shows which lists are currently enabled.

### Completed dashboard, statistics and blocklist management
- Completed the Home dashboard and the statistics screen.
- Added full management for blocklists.
- Removed in-app purchases and premium gating: every feature is available to
  everyone.
- Expanded Italian, German, French and Spanish translations.

## 2026-11-07

### DNS over HTTPS (DoH)
- Added encrypted DNS over HTTPS (RFC 8484) with a built-in catalogue of
  providers.
- DoH responses are cached for 60 seconds; the configured plain DNS server is
  used only as a fallback.

### Native StevenBlack lists and more DNS resolvers
- Added native StevenBlack lists, including the fakenews, gambling, porn and
  social extensions and their pre-built combinations.
- Added many free plain DNS resolvers: Google, Cloudflare, Quad9, AdGuard,
  OpenDNS, CleanBrowsing, DNS.SB, Control D, DNS4EU and Yandex.
- Removed Shizuku and other privileged helper modes: protection is VPN-only.
- Various quality fixes and translation updates.

### TLD blocklists
- Added support for top-level-domain lists, including HaGeZi Most Abused TLDs,
  to block entire risky domain extensions.

### BetterBlockerDNS identity
- Introduced the BetterBlockerDNS (BBDNS) name, package and branding, with a new
  shield logo.
- Refreshed the store description and screenshots.
- Updated translations and version metadata for the new project identity.

## 2026-01-26

### Download link update
- Updated the download link to version 1.8.

## 2026-01-12

### Nightly builds and translations
- Added the nightly build workflow.
- Updated several interface translations.

### New release preparation
- Prepared a new release with updated build settings, store metadata and
  screenshots.
