# Privacy policy

**BetterBlockerDNS (BBDNS)** — last updated 11 November 2026

BBDNS is a DNS filtering app for Android. It runs entirely on your phone and
has no servers of its own. This policy explains what the app does with your
data and why it asks for certain permissions.

## Data we collect

**None.** BBDNS has no user accounts, no analytics, no advertising SDKs, no
crash reporting and no telemetry. The developer does not receive any data from
your device, and there is nothing to send: the app has no backend.

## What the app does on your device

- **DNS filtering.** The app takes over the device's network traffic through a
  local VPN tunnel so it can inspect DNS lookups. Non-DNS packets are forwarded
  unchanged, and the tunnel starts and ends on your phone: no traffic is sent to
  a BBDNS server, because there is no BBDNS server.
- **DNS resolution.** When a domain is allowed, the query is forwarded to the
  DNS resolver you selected in the app (for example Cloudflare, Quad9 or
  Google). That resolver sees the domain you are resolving, exactly as it would
  without BBDNS. The choice is yours and can be changed at any time.
- **List downloads.** Blocklists are downloaded from the public sources you
  enable (for example HaGeZi or StevenBlack). Those servers see the download
  request, as with any file download from your phone.
- **Logs and statistics.** Blocked domains, recent activity and counters are
  stored only in the app's private storage on your phone. They are never
  uploaded. You can clear them at any time from the app, and the detailed log
  is optional.
- **Battery diagnostic log (optional, off by default).** When you turn it on,
  the app records once a minute the battery level and its own resource usage.
  It never records domains, IP addresses or app names. It is stored on the
  phone and only leaves it if you export and share the file yourself.

## Permissions and why they are needed

| Permission | Purpose |
|---|---|
| VPN service (asked by Android) | Create the local tunnel where DNS filtering happens. BBDNS never routes your traffic through an external server; the tunnel starts and ends on your phone. |
| Internet and network state | Forward allowed DNS queries, download the lists you enable, detect Wi-Fi and mobile networks. |
| Installed apps (`QUERY_ALL_PACKAGES`) | Show the list of apps so you can create per-app rules. The list never leaves the device. |
| Foreground service and notifications | Keep protection running and show its status. |
| Start at boot | Restore protection after the phone restarts, only if you asked for it. |
| Battery optimisation settings | Let you exclude BBDNS from Android's background restrictions so the tunnel is not killed. |
| Biometric | Optional app lock, checked by Android. BBDNS never sees or stores your fingerprint or face data. |

## Children

BBDNS is not directed at children and collects no data from anyone.

## Changes

If a future version starts sending data anywhere — for example a remote backup
or a synchronisation feature — this policy will be updated before that version
is released, and the change will appear in the app's changelog.

## Contact

The app is developed by **buzzqw** and distributed under the GPL-3.0 licence.
Questions about privacy: <azanzani@gmail.com> or open an issue at
<https://github.com/buzzqw/BetterBlockerDNS/issues>.
