# WireGuard support: analysis and decision

Date: 2026-11-18

## Short answer

WireGuard chaining would be the single most valuable feature BBDNS does not
have: it is the only real answer to "another VPN took over". It is **not**
implemented in this round because it cannot be added to the current datapath
without replacing it. The work is comparable to a second engine, not to a
setting.

## Why the obvious integration does not work

`com.wireguard.android:tunnel` (the official GoBackend) is a complete VPN
solution: it owns its own `VpnService`, its own TUN device and its own routing
table. Android allows one VPN at a time, so starting that backend stops BBDNS
and its DNS filter. There is no public API to read its TUN, inject packets
into it, or hand it a TUN that BBDNS already owns.

The direction we would need is the opposite:

```
apps → BBDNS VpnService (one TUN)
              │
              ├─ DNS queries ──────► BBDNS filter
              │
              └─ all other flows ──► userspace TCP/IP stack ──► WireGuard
```

The missing block is the middle one. BBDNS is a NetGuard-derived C engine: it
reads IP packets from the TUN and opens normal host sockets, protected with
`VpnService.protect()`. To send those flows through WireGuard it would need a
userspace TCP/IP stack (gVisor, lwIP) plus a userspace WireGuard device
(wireguard-go with a channel TUN, or lwIP + boringtun). That is exactly the
~25k lines of Go that blockads-android carries, with gVisor netstack and
wireguard-go, on top of its Kotlin app. Its `tunnel.aar` is a 19 MB fat AAR and
the per-ABI APKs are 25 MB, against BBDNS's 3.9 MB universal APK.

## Options

| Option | What it means | Effort | Risk |
|---|---|---|---|
| A. Official GoBackend | Replace BBDNS's VpnService with the WireGuard one | Low | No DNS filtering: the feature would remove the product's reason to exist |
| B. New Go datapath | gVisor netstack + wireguard-go, keep Kotlin UI and rules | Very high | New language, new build, 20 MB+ per ABI, rewrite of filtering and logging |
| C. SOCKS-over-WireGuard | Local SOCKS5 server over wireguard-go, C engine forwards TCP to it (NetGuard already supports a SOCKS5 address) | High | TCP only; UDP/QUIC, calls and games bypass the tunnel or break |
| D. Import only | Parse `.conf` and show it, but no tunnel | Low | Fake feature: users would rightly complain |

Option C is the only incremental one, and its UDP gap is unacceptable for a
system-wide VPN: DNS could be routed, but QUIC and calls would not.

## Recommendation

Do not ship WireGuard until there is a decision to move the datapath to Go
(option B). If that decision is taken, the work splits into:

1. a Go module with `wireguard-go`, a channel TUN and `gomobile` packaging;
2. a userspace TCP/IP stack for the flows the C engine used to dial;
3. porting rule evaluation, per-app UID resolution and connection logging to
   the Go path, keeping the Kotlin UI unchanged;
4. a per-app split-tunnel UI (which apps use WireGuard, which use the normal
   network) and LAN/private-range exceptions;
5. battery and throughput benchmarks against the current engine, because the
   C engine is the reason the APK is 3.9 MB and the idle drain is low.

Until then, the product stays honest: BBDNS uses the VPN slot and says so.

## What was implemented instead

The cheap, real wins from the same competitive review:

- **Block DNS bypass**: DoT on 853, known DoH endpoints, the Firefox canary
  domain and hardcoded public DoH IPs. This closes the leak that makes users
  think the blocker does not work.
- **SafeSearch**: DNS-level redirect to `forcesafesearch.google.com`,
  `strict.bing.com` and `restrict.youtube.com`, with no MITM and no CA.
- Unit tests for the DNS helpers, the blocklist parser and the compatibility
  allowlist.
