# Changelog

All notable changes to **BetterBlockerDNS (BBDNS)** are documented in this file.

Every commit gets an entry, written in English for end users: it explains what
changed and how it affects the app, without implementation details. Entries are
listed newest first.

Entries dated before 2026-11-07 are kept from the project's earlier history.

## 2026-11-24

### Lower processor use while connections are open
- The tunnel no longer repeats address work that was never used while checking
  open connections. Protection behaves exactly as before, but leaves more
  processor time to the rest of the phone, which helps battery life.

## 2026-11-22

### Release 1.0.53
- Version 1.0.53 adds an export button to the log: the entries can be saved as
  a CSV file and checked outside the app, which is what makes it possible to
  see which names were let through and which were blocked, and by which app.
  The battery diagnostic also reports how many different domains the blocked
  requests were for.

### Export the log
- The log screen can now save its entries as a CSV file through the system file
  picker: time, app, destination, port, protocol and whether the request was
  allowed or blocked. It is the list of names the filter saw, so it can be
  checked, sorted or shared with support instead of read one row at a time.
  The file goes where you choose; BBDNS has no server and uploads nothing.
- The battery diagnostic now also says how many different domains the blocked
  requests were for (`dblock=`), so a high block count can be told apart from
  many different sites.

### Release 1.0.52
- Version 1.0.52 makes the protection profiles load StevenBlack Unified:
  Balanced and the stricter profiles now download the malware and advertising
  base themselves, instead of depending on what the installation already had.
  The list is named in the download dialog, and a copy that is already enabled
  is not added twice. Essential keeps its two HaGeZi lists.

### Profiles load StevenBlack Unified
- Choosing Balanced, Pro, Aggressive or Maximum now downloads StevenBlack
  Unified as part of the profile, so the malware and advertising base is loaded
  by the profile itself instead of depending on what the installation had
  before. Essential keeps its two HaGeZi lists. The download dialog names the
  list, and a copy that is already enabled is not added twice.

### Release 1.0.51
- Version 1.0.51 blocks trackers that hide behind the site's own name, can show
  a page on the phone that explains a block and lets a site through for ten
  minutes, and stops a dead DoH endpoint from adding a timeout to every lookup.
  Opening the blocklist screen no longer downloads every list again.
  Everything still happens on the device: nothing is decrypted.

### Blocklist updates no longer restart on every visit
- Opening the blocklist screen used to cancel the pending automatic update and
  queue a new one, which ran a full download of every enabled list and reloaded
  the rules: on a phone that showed up as repeated updates a few seconds apart
  and a heavy CPU spike. Visiting the screen now only makes sure the periodic
  update exists; a download happens when it is due or when you change the
  interval.

### Trackers hidden behind a site's own name
- A site can no longer serve a tracker from its own domain: when the answer to
  an allowed name points (CNAME) to a blocked one, BBDNS blocks the answer and
  counts it in the log for the site you visited. This closes the most common
  way around a DNS filter.
- The check runs on the answer, so it needs DoH, which is what lets the app
  read the answer. With plain DNS the tunnel forwards the resolver's answer
  unchanged, as before.

### A page that explains the block
- New **Notice page for blocked sites** switch in *DNS resolver*. When it is
  on, a blocked site opened over plain HTTP shows a page served by the phone
  instead of the browser's generic error: it names the list that blocked the
  site and offers to let it through for ten minutes.
- The page works with no network, follows the system light and dark mode and
  needs no permission. HTTPS sites are still judged by name only and never
  decrypted, so they show the browser's own error; the switch description says
  so.
- The page is off by default, applies without restarting protection, and is
  saved in backups with the other DNS settings.

### A dead DoH endpoint no longer slows every lookup
- After three failures in a row the app sets the endpoint aside for a minute
  and asks the plain resolver straight away, instead of paying a timeout on
  every query. One test request is let through, and normal DoH use resumes as
  soon as it answers.

### Tests, CI and security paperwork
- New unit tests cover the DNS answers the app builds, the CNAME check, the
  DoH breaker, the temporary allow list and the notice page itself, including
  the signed "continue" link and the refusal to relay private addresses.
- Every push now runs the unit tests and Android Lint in CI, and a new
  security workflow scans the full history for committed secrets and reviews
  the dependencies a pull request adds.
- The project now ships [SECURITY.md](SECURITY.md) (supported versions, what
  the app connects to, private reporting), [ACCESSIBILITY.md](ACCESSIBILITY.md)
  (screen reader, large text and contrast, and the known gaps) and
  [CITATION.cff](CITATION.cff) for citing the app in research.

### Translations
- The new switch is translated into German, Spanish, French, Brazilian
  Portuguese and Chinese, together with the DNS bypass and SafeSearch
  switches added in the previous version, which were English-only.

## 2026-11-19

### Release 1.0.50
- Version 1.0.50 tells you when protection was stopped in the background and
  could not be restarted automatically, and stops the periodic restart check
  when you turn protection off.

### Clearer blocklist update reports
- A blocklist update now records how it ended: whether it was automatic or
  started by hand, how many lists were refreshed, how many failed and whether
  anything was actually downloaded. A repeated update that changes nothing is
  no longer indistinguishable from a real download.
- The battery report header explains that the traffic counters also include
  everything forwarded through the tunnel.

### Protection alerts
- If Android stops the protection in the background and BBDNS cannot bring it
  back by itself, the app now shows a notification asking you to open it again.
  The warning disappears as soon as protection is running again.
- The periodic check that can restart the protection after a system stop now
  runs only while protection is on, so it no longer wakes the app when you have
  turned protection off.
- The battery diagnostic report now records whether that check found protection
  down, so an interrupted protection is easier to recognise.

## 2026-11-18

### Release 1.0.49
- Version 1.0.49 adds optional protection against encrypted DNS that bypasses
  the filter, and optional SafeSearch for Google, Bing and YouTube. Both
  switches are off by default, apply without restarting protection and are
  saved in backups.

### DNS bypass protection
- A new **Block DNS bypass** switch in *DNS resolver* closes the encrypted
  resolvers that could skip the filter: DNS-over-TLS on port 853, the known
  DNS-over-HTTPS endpoints, the Firefox canary domain and the public DoH
  resolvers that are configured by IP address. Browsers and apps fall back to
  the filtered DNS. Android Private DNS must stay off while it is on.
- The block follows a pause of the protection: encrypted DNS works again while
  protection is paused and is closed again when it resumes, without losing the
  saved setting.

### SafeSearch without decryption
- A new **SafeSearch** switch answers Google, Bing and YouTube with the
  addresses that force SafeSearch and YouTube restricted mode. The redirect
  happens in DNS, with no certificate and no traffic decryption, and only for
  A/AAAA queries, so mail, certificates and service discovery keep working.
- If the SafeSearch address cannot be resolved, the original query is resolved
  normally instead of failing: search keeps working even when the upstream
  resolver is having trouble.

### DNS rules under test
- The DNS helpers, the blocklist parser and the compatibility allowlist now
  have unit tests, so the rules that keep sites working cannot regress
  silently. The *why was this blocked?* screen also names the DNS bypass
  protection when it is the reason.

## 2026-11-17

### Release 1.0.48
- Version 1.0.48 brings the app onto current Android components without
  changing how protection, blocklists and settings work on the Android
  versions the app already supports.

### One component update held back
- The newest release of one core Android support component needs the app to be
  built against a newer Android API level than it uses today. It stays on the
  previous release for now, so the app keeps building and running exactly as
  before; it will follow when the project moves to the newer API level.

### Current Android libraries on board
- The app now uses the latest versions of the Android components it relies on
  for saved settings, background work, the local database and core system
  support. Protection, blocklists and settings keep working exactly as before,
  with the fixes and efficiency gains those components bring.
- No new permission or setting appears, and existing data is kept as it is.
- The automated check that builds every change now runs on current build
  tooling, so future versions keep being verified reliably.

## 2026-11-17

### Release 1.0.46
- Version 1.0.46 keeps protection steady and quiet in the situations that used
  to heat the phone and drain the battery: long downloads, connections that
  the other side closes, and frequent network switches.

### A connection that closes no longer slows the phone down
- When a connection was closed or failed at the other end, the tunnel could
  keep the processor busy for many minutes, which made the phone warm and ate
  battery until the connection timed out. The tunnel now closes such a
  connection immediately, and pauses instead of working in vain if a
  connection stops moving data.
- Long downloads and streaming are not affected: traffic keeps flowing as
  before.

### One reset per network change
- Android reports the same network switch more than once, so the tunnel reset
  the open connections twice and the battery log filled with duplicate lines.
  The repeated report is now ignored.

## 2026-11-17

### Release 1.0.45
- Version 1.0.45 lists the AdGuard DNS filter among the individual lists, so
  the list that a protection profile downloaded is visible and marked as
  enabled.

### AdGuard DNS filter among the lists
- The filter was applied by Balanced and the stronger profiles but had no row
  in the list of sources: the profiles lit no switch and "Show individual
  lists" did not mention it. It now appears under *Other catalogues*, with its
  own switch that reads the same state the profile sets, so an enabled filter
  shows up as enabled and can be turned off from there.

## 2026-11-17

### Release 1.0.44
- Version 1.0.44 puts the AdGuard DNS filter switch in Blocklists, where the
  other protection switches are.

### AdGuard DNS filter switch
- The AdGuard DNS filter was only reachable from the presets of the "add a
  list" dialog, which is not where anyone looks for it. It now has its own
  switch in Blocklists, next to Extra ads and the other protection switches,
  with the same download dialog and the same rollback when the download fails.
- The switch and the protection profiles use the same list, so turning the
  profile to Balanced or higher and flipping this switch do not stack it twice.

## 2026-11-16

### Release 1.0.43
- Version 1.0.43 stops rules written for browsers from blocking whole sites,
  and brings the AdGuard DNS filter to the protection profiles.

### Browser filter rules no longer block whole sites
- A rule written for a browser, such as `example.com##.ad-banner`, used to be
  read as a plain domain and blocked the entire site; the element hiding
  exceptions (`example.com#@#.ad`) did the same. EasyList alone hit about 1,700
  sites this way, banks included. Those page rules are now skipped, because DNS
  only sees hostnames, and a browser list (EasyList, uBlock Origin, AdGuard)
  contributes exactly the `||domain^` rules it contains. Wildcards such as
  `||youtube.*^` are accepted too.

### AdGuard DNS filter in the profiles and as a preset
- The AdGuard DNS filter, built for DNS blockers with about 190,000 hosts, is
  available as a one-tap preset in the custom lists and comes with every
  protection profile from Balanced upwards. Nearly 110,000 of its domains are
  not in HaGeZi PRO, the Threat Intelligence Feed or StevenBlack.
- Profiles enabled before this version keep working and still show as active;
  tap the profile again to download the filter, about 4 MB. Switching to a
  weaker profile removes it with the rest of the profile.
- The Extra ads list now also covers DoubleClick and Google's ad server
  (`doubleclick.net`, `adservice.google.com`): the default lists leave them
  out, so apps kept showing banners that came from there.

### YouTube ads, honestly
- The troubleshooting page now says what does work, beyond explaining the
  limit: uBlock Origin in Firefox for Android with `youtube.com` allowed in the
  extension, or a client that does not play ads. It also points out that the
  Secure DNS option of Chrome, Brave and Firefox bypasses BBDNS, like Android's
  Private DNS.

## 2026-11-15

### Release 1.0.42
### Optional "Extra ads" list
- Added an optional **Extra ads — mobile networks** switch in Blocklists. It
  adds mobile ad networks and tracking endpoints that the default lists do not
  cover, and it works offline because the list ships with the app.
- It does not remove YouTube video ads: those are served from the same servers
  as the videos, and no DNS list can separate them. The README now explains
  this limitation.

## 2026-11-12

### Release 1.0.41
### First Google Play build
- BBDNS is now built for Google Play as well. The app is signed with the same
  key as the GitHub releases, so updates install over each other from either
  source. There are no functional changes compared with 1.0.40.

### Release 1.0.40
### Resource usage shows BBDNS only
- The "Resource usage" card on Home now shows only the CPU used by BBDNS.
- The battery value was removed: Android does not let an app measure its own
  battery use, and the phone-wide figures looked as if BBDNS had used them.

### Battery diagnostic log
- The analysis of an exported battery log no longer reports normal network
  activity as a problem; it warns only when the connection handling really
  loops without work.

## 2026-11-11

### Release 1.0.39
### Clearer wording
- The app screens and the store description now introduce BBDNS as a DNS
  firewall, describing what it protects you from (malware, phishing, tracking)
  rather than advertising alone.
- The store screenshots and the featured image are rebuilt with the same
  wording.
- Nothing changed in how filtering works.

## 2026-11-11

### Release 1.0.38
### Google Play
- First release prepared for Google Play, where BBDNS will be published soon.
- Opening the battery settings from BBDNS now shows the list of apps instead of
  the direct exemption request; the resulting setting is the same.
- An unused permission was removed. No feature changed.

## 2026-11-11

### Release emails
- The release email now carries the link to the GitHub release and its notes
  instead of an attached build, so every installed APK is the one GitHub
  built and signed.

### Release 1.0.37
- Version 1.0.37 adds the optional battery diagnostic log below.

### Battery diagnostic log
- New optional "Battery diagnostic log" in Settings > Behavior. When turned
  on, BBDNS records once a minute, while protection runs, the battery level
  and what the app did (CPU time, network wake-ups, packets, sessions). It
  saves no domains, addresses or app names, never wakes the phone on its own,
  and can be exported as a text file to help find what drains the battery.
- The tunnel skips some unused work for every packet it sends back to apps.

## 2026-11-10

### Build delivery
- The email sent with each release now points to the latest published version
  on GitHub instead of a link that only worked on the local network.

## 2026-11-10

### Release 1.0.36
- Version 1.0.36 adds the changelog link on the About screen and keeps the
  reliability fixes of 1.0.35.

### About screen
- The About screen now has a "Changelog" entry right under the version, so the
  list of what changed in each release opens with one tap.

## 2026-11-10

### Release 1.0.35
- Version 1.0.35 ships the build and documentation fixes below; the app itself
  is unchanged from 1.0.34.

### Build checks
- The build, release and security jobs now run on a fixed Ubuntu image, so an
  automatic runner upgrade cannot break them.
- The automatic security analysis of the source code no longer fails when the
  build cache skips compilation, so the weekly check completes reliably.

### Documentation
- The README now explains how to get started, how BBDNS works, which
  permissions it uses and why, and how to fix the most common problems.

## 2026-11-10

### Automatic releases
- Pushing a version tag now makes GitHub build the signed APK and publish the
  release, so every official download comes from the same verified build.

### Release 1.0.34
- Version 1.0.34 bundles the battery, performance and usability changes
  below.

### Lower CPU, memory and battery use
- The tunnel now checks only the first packet of each connection against the
  firewall rules (DNS queries are still checked one by one), so streaming,
  calls, games and QUIC traffic use far less CPU and battery.
- Changing an app's Wi-Fi/mobile access, the blocked IP list, or turning the
  screen off with "block when screen off" enabled now ends the open
  connections, so the new rules apply to them right away.
- Custom regex domains are prepared once instead of on every DNS query, and
  the blocked IP list is looked up instantly instead of scanned.
- The network speed notification pauses while the screen is off and stops
  redrawing when there is no traffic; the protection notification is no
  longer refreshed with the screen off.
- The home screen stops sampling CPU, battery and history while the app is in
  the background.
- Release builds no longer write a system log line for every blocked domain.
- Fixed a rare crash in the tunnel when the open connections were reset while
  traffic was being processed.

### Easier to use
- The home shows the recently blocked domains right below the key numbers,
  and the resource usage card is collapsed until you open it.
- Allowing a domain from the home can be undone from the confirmation that
  appears at the bottom of the screen.
- The first time you block an app while protection is on, BBDNS explains that
  its open connections were closed so the rule applies immediately.
- The Wi-Fi and mobile data buttons of each app, and the "add IP" button, are
  now announced correctly by screen readers.
- Tapping the Quick Settings tile opens the app again on Android 14 and newer.
- The protection notification shows compact numbers (for example 12K).
- Italian, German, Spanish, French, Brazilian Portuguese and Simplified
  Chinese are now fully translated.

## 2026-11-09

### Builder compatibility
- Kept the previous build command working for external app stores while the
  single-build transition completes.

### Single build
- BBDNS now ships one APK instead of two identical builds, so releases and
  downloads are simpler.

### Release 1.0.33
- Protection keeps the VPN running when the network changes, and the firewall
  now talks to the running service: rule changes and session resets apply
  immediately.
- Turning protection off now always closes the tunnel and releases the TUN
  interface, even if the start had not completed.
- Large blocklists load faster, and domains are matched with a 64-bit hash so
  an unrelated domain can no longer be blocked by a hash collision.
- The logs screen now shows the newest entries first and stays fast when the
  stored history is full.
- The unused native nflog helper and the unused Retrofit/Gson libraries are
  gone, so the APK is smaller and the HTTP client is declared directly.
- Building from source no longer needs the Go toolchain: only the Android NDK
  is required.
- Both store builds now share the same app startup code, and the last traces of
  the old project name are gone from the theme and splash screen.

### Release 1.0.32
- BBDNS now resolves and records each connection only once, so background
  traffic no longer keeps the processor, the radio and the database busy.
- Reverse DNS lookups are cached: the same address is no longer resolved again
  for every connection.
- Packet processing is lighter: the native engine reuses its callbacks and
  buffers, and established TCP streams are forwarded with less work.
- Automatic blocklist updates follow the saved interval after a restart and
  skip the full list reload when nothing changed, saving processor time on
  every run.
- The status notification refreshes less often, reducing background wake-ups.

### Release 1.0.31
- Added an "Update now" button to the blocklist update section and its interval
  dialog, so the lists can be refreshed immediately.
- The banking-app preset now matches ambiguous brand names only inside package
  names, keeping the extra banks without flagging unrelated apps.
- StevenBlack Unified and its "-only" extensions can be combined again, while
  profile and preset alternatives still replace each other.
- A restored backup now cleans up duplicate lists and redundant lower tiers.
- The last-update time follows the device's 12/24-hour setting.

### Release 1.0.30
- Home shows live CPU and battery usage while the screen is open, including the
  battery draw and the percentage of battery already consumed.
- The AMOLED option now really turns the dark theme black instead of leaving it
  almost unchanged.
- The logs screen is more compact, so more blocked requests fit on screen.
- The "Banking apps" quick exclusion recognises many more Italian and European
  banks and payment apps.
- Blocklist profiles and presets no longer keep two alternatives of the same
  list enabled, and redundant lower tiers are removed automatically.
- The Auto-update Lists section shows when the blocklists were last updated.

### Last blocklist update time
- The Auto-update Lists section now shows when the blocklists were last
  updated successfully, or "never" if no update has run yet. Manual updates
  are included.

### Banking app preset
- The "Banking apps" quick exclusion now recognises many more Italian and
  European banks and payment apps, so fewer of them are missed when the preset
  is applied.

### Compact logs layout
- The logs view is now more compact: app groups use a smaller header with the
  timestamp on the same line, and each entry has tighter spacing and smaller
  icons, so more blocked requests fit on screen.

### AMOLED colours
- The "AMOLED Colors" option now has a visible effect: with the dark theme it
  turns the background pure black and the other surfaces near-black, instead of
  leaving the normal dark colours almost unchanged.

### Live resource usage on Home
- The Home resource card now updates while it is open: it shows the CPU used by
  BBDNS and the current battery draw together with the percentage of battery
  already consumed.
- No background sampling: the values stop updating as soon as Home is closed.

## 2026-11-08

### Release 1.0.29
- Updated the Android build toolchain to Android Gradle Plugin 9.5 and
  Gradle 9.9, which removes the Kotlin metadata warnings from release builds.
- Updated the F-Droid build recipe to 1.0.29.
- No changes to the app's behaviour.

### Release 1.0.28
- Fixed the language picker: all bundled languages, including Simplified
  Chinese and Brazilian Portuguese, are listed again.
- Protection can be controlled from automation apps and scripts, including a
  pause duration.
- Added a home screen widget that shows the protection state and today's
  blocked count, with one-tap start, stop and resume.
- A temporary pause now survives a VPN service restart.
- The active blocklist screen shows when each list was last updated.
- DNS is more reliable: TCP retries use the configured resolver, the secondary
  resolver is used as a fallback, and DNS over HTTPS reuses connections and
  retries sooner.
- The Quick Settings tile can start protection when it is off.
- Fixed the Wi-Fi and mobile data actions in the "new app installed"
  notification.

### Automation
- Protection can now be started, stopped, paused and resumed from automation
  apps and scripts such as Tasker, MacroDroid, Home Assistant or adb. Pausing
  accepts a duration. The receiver only changes the protection state and never
  exposes logs, settings or blocked domains.

### Home screen widget
- New home screen widget: it shows whether protection is on, off or paused,
  how many requests were blocked today, and starts, stops or resumes
  protection with one tap.

### Protection pause
- A temporary pause now survives a service restart: if Android stops the VPN
  during the pause, protection comes back still paused for the remaining time
  instead of blocking again early.

### Blocklists
- The active blocklist screen now shows when each list was last updated, so it
  is easy to see whether automatic updates are working.

### DNS reliability
- DNS queries that the system retries over TCP now go to the configured
  resolver instead of failing. This fixes some apps and websites that could
  not load after a truncated answer.
- The secondary DNS resolver chosen in the settings is now actually used:
  Android can fall back to it when the primary does not answer.
- DNS over HTTPS is more reliable and faster: the app reuses the encrypted
  connection, retries once after an immediate failure and waits less before
  falling back to plain DNS.

### Quick Settings
- The BBDNS Quick Settings tile can now start protection when it is off,
  instead of only opening the app. The app still opens when the VPN permission
  has not been granted yet.

### Notification actions
- The Wi-Fi and mobile data buttons in the "new app installed" notification
  now change the permission they are labelled with.

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
