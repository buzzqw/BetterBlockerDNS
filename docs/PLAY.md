# Publishing on Google Play

This document covers everything that has to be done outside the repository to
publish BetterBlockerDNS (BBDNS) on Google Play, and the few changes the
project needs to pass Play review. The build itself is already handled by
`scripts/build_play_bundle.sh` and by `.github/workflows/release.yml`.

## Why Play at all

An APK installed from a browser is checked by Play Protect every time, because
Google does not know the package. An app installed from Play is not: that is
the only supported way to stop the "scan this app" prompt. Publishing on Play
also makes the app recognisable to Play Protect, which is what already happens
for similar apps distributed both on GitHub and on Play.

## Before the first upload: keep the project key

By default Play generates a new signing key for a new app, and the APKs on
GitHub would then have a different identity: users could not update across the
two channels.

1. Create the app in Play Console (do **not** publish anything yet).
2. Open **Protected with Play → Play Store protection → Manage Play app
   signing → Change key**.
3. Choose **Provide a copy of your app signing key** and follow the
   instructions to upload `~/.config/bbdns/release/bbdns-release.jks`
   (alias `bbdns`, RSA 4096). Play shows a `pepk.jar` command with a per-app
   encryption key: run it with `--keystore=~/.config/bbdns/release/bbdns-release.jks
   --alias=bbdns`, then upload the generated zip.
4. Check that the **App signing key certificate SHA-256** shown in Play Console
   is `b6de8e179abd20d705d0084af4a6d667c5ce2962aa649758cc2e29f08ccc7eba`.

This must be done **before the first release on the open testing or production
track**: after that the app signing key can no longer be replaced with the
project one.

The same keystore is the upload key, so every bundle must be signed with it.
Play recommends a separate upload key; using the same one is allowed, but the
keystore then also identifies the developer account when uploading, so keep it
private and backed up. Losing the upload key is recoverable (Play can reset
it); the app signing key that Play holds is stored on Google's side.

## Build and upload

```bash
scripts/build_play_bundle.sh
# app/build/outputs/bundle/release/app-release.aab
```

The script sources `~/.config/bbdns/release.env`, builds the bundle and refuses
to hand it over unless it is signed with the project certificate. The Release
workflow builds the same bundle on a tag and attaches
`BetterBlockerDNS-<version>.aab` to the GitHub release.

Upload the `.aab` in Play Console under **Test and release**. Play builds the
per-device APKs and signs them with the app signing key configured above.

Always bump `version.properties` before a release: Play refuses a `versionCode`
that is not higher than the previous one.

## Play-only restrictions

These changes are already in the project; keep them when editing the manifest:

- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` was removed. The app now opens the
  system battery optimisation list instead of asking for the exemption
  directly, because Play restricts that permission.
- `FOREGROUND_SERVICE_DATA_SYNC` was removed: no service uses that type.
- `USE_FINGERPRINT` stays on purpose, for the app lock on Android 8 and 8.1;
  `USE_BIOMETRIC` alone covers Android 9 and newer.
- `QUERY_ALL_PACKAGES` stays, because per-app rules need the list of installed
  apps. It must be declared in the Play Console (see below).

## Declarations in Play Console

### Sensitive permissions — `QUERY_ALL_PACKAGES`

**App content → Sensitive app permissions.** Justification: the app lists the
installed apps so the user can create per-app filtering rules; the list is used
on the device only and never leaves it. A short screen recording of the
per-app rules screen helps.

### Foreground service — `specialUse`

**App content → Foreground service permissions.** Subtype from the manifest:
"Firewall and network filtering service", plus "Network speed monitoring" for
the optional speed notification.

### VpnService declaration

**App content → VPN service.** This is the review step that matters most,
see [VpnService policy][vpn-policy].

Recommended answers:

1. *Is providing a VPN the core functionality of your application?* — **No**.
   BBDNS does not connect to any VPN server: the tunnel starts and ends on the
   phone and carries DNS traffic only.
2. *Which permitted functionality does your app provide?* — **Device security
   (including anti-virus and firewall)**.
3. Video of the app being opened and the VPN started (90 seconds or less).
4. *What data does your VPN service collect or share?* — **No**.
5. The second video (prominent disclosure) is only required when data is
   collected. The in-app disclosure already exists in the permission dialog.

Be explicit in the free-text fields that the tunnel has no remote endpoint and
that no traffic, domain or app name is transmitted anywhere.

### Data safety

**App content → Data safety**, all answers "no":

- No data collected, no data shared, no data sold.
- No data is transmitted off the device by the developer.
- Data is not processed ephemerally by any third party on the developer's
  behalf; the resolver the user chooses is the user's own configuration.

Mention in the description that DNS queries go to the resolver the user picks,
which is not the developer.

### Other forms

- **Ads:** the app contains no ads.
- **App access:** all features are available without an account or login.
- **Content rating (IARC):** utility/tools category, no user interaction, no
  user-generated content, no sharing.
- **Target audience:** 13+ or 18+; there is no child-directed content.
- **Privacy policy URL:** `https://github.com/buzzqw/BetterBlockerDNS/blob/main/PRIVACY.md`
  (the file `PRIVACY.md` in this repository; GitHub Pages works too).
- **News app, financial features, health, government:** not applicable.

## Store listing

The listing must describe the product honestly and must not read as an app
built to strip ads from other apps: the VpnService policy states that
"any manipulation of ads that can impact monetization will result in
rejection", and ad-blocking VPNs have been removed from Play for this reason.

A wording that still describes the product, without putting ad blocking first:

**Short description** (80 characters maximum)

> Local DNS firewall: filter malware, phishing and tracking domains. No root.

**Full description (opening)**

> BetterBlockerDNS (BBDNS) is a free, open-source DNS firewall for Android. It
> filters domain lookups system-wide through a tunnel that never leaves your
> phone — no root, no account, no BBDNS server.
>
> Filtering categories include malware, phishing, scams, cryptojacking,
> telemetry, and advertising or tracking domains. Protection profiles let you
> choose how strict the filter is, and per-app rules decide which apps are
> filtered.

The repository files `metadata/en-US/short_description.txt` and
`metadata/en-US/full_description.txt` already contain this text, and the in-app
English strings were reworded the same way: the app describes what it protects
from, not "ad blocking". Keep that rule when adding features:

- Do not use "ad blocker", "block ads" or "ad-free" as the promise of the app
  (the only exception is "no ads", which is a declaration about the app itself).
- Third-party list and resolver names stay as they are, because they are proper
  names ("Ads Extreme", "Pop-Up Ads", "AdGuard").

Close with the privacy section: filtering is on the device, no analytics, no
crash reporting, logs stay on the phone.

Assets already in the repository meet the Play sizes:

- icon 512×512: `metadata/en-US/images/icon.png`
- feature graphic 1024×500: `metadata/en-US/images/featureGraphic.png`
- phone screenshots 1080×2160: `metadata/en-US/images/phoneScreenshots/`
  (2:1, the widest ratio Play accepts; crop to 9:16 if the console complains)

## Review risk

The VpnService policy is enforced by a human review. If the app is rejected:

1. Read the rejection reason: it is either the VpnService declaration or the
   listing text.
2. Reply in the appeal form explaining that the tunnel is local, carries DNS
   only, has no endpoint and collects nothing, and point at `PRIVACY.md`.
3. If the rejection is about ad blocking, the choice is between rewording the
   listing and keeping the app off Play. Do not resubmit the same text: repeated
   violations can affect the developer account.

While an app is in review or rejected, the GitHub releases keep working as they
do today.

## After publication

- Play Protect stops asking to scan the app for users who install it from Play.
  Users who keep installing the GitHub APK still see the prompt.
- Keep the same app signing key for every release. The GitHub Release workflow
  already checks the certificate before publishing.
- Play requires the target API level of the year: the project targets 36, which
  is accepted through 2026.

[vpn-policy]: https://support.google.com/googleplay/android-developer/answer/12564964
