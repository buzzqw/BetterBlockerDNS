#!/usr/bin/env python3
"""Summarize a BBDNS battery diagnostic log (Settings > Behavior > Export).

Usage: scripts/analyze_battery_log.py bbdns_battery_YYYY-MM-DD_HH-MM.txt

Each "S" line covers the interval since the previous one, with a single
screen/charger state. The report splits the time by screen on / screen off
(charging excluded) and shows, per hour: battery drain, how long the CPU was
awake, BBDNS CPU time, event-loop wake-ups, packets and the busiest threads.
"""
import sys
from collections import Counter, defaultdict

NUMERIC = ("dt awake cpu loops rck idle tin tinb tout toutb sock jni zwin "
           "allow block doh dohc dohf rx tx").split()


def parse(path):
    header, samples, events = [], [], []
    with open(path, encoding="utf-8", errors="replace") as f:
        for raw in f:
            line = raw.rstrip("\n")
            if line.startswith("#"):
                header.append(line)
                continue
            parts = line.split(" ")
            if len(parts) < 3:
                continue
            ts, kind, rest = parts[0], parts[1], parts[2:]
            fields = {"ts": ts}
            for item in rest:
                if "=" in item:
                    k, v = item.split("=", 1)
                    fields[k] = v
            (samples if kind == "S" else events).append(fields)
    return header, samples, events


def num(fields, key):
    try:
        return int(fields.get(key, 0))
    except ValueError:
        return 0


def fmt_h(ms):
    return f"{ms / 3_600_000:.2f} h"


def summarize(name, rows):
    total = defaultdict(int)
    threads = Counter()
    drain_uah = 0
    drain_pct = 0
    prev = None
    for prev_row, row in rows:
        for k in NUMERIC:
            total[k] += num(row, k)
        thr = row.get("thr", "-")
        if thr != "-":
            for item in thr.split(","):
                if ":" in item:
                    t, ms = item.rsplit(":", 1)
                    threads[t] += int(ms) if ms.isdigit() else 0
        # Battery used in the interval: charge counter is the most precise.
        if prev_row is not None:
            a, b = num(prev_row, "uah"), num(row, "uah")
            if a > 0 and b > 0 and a >= b:
                drain_uah += a - b
            la, lb = num(prev_row, "bat"), num(row, "bat")
            if la >= lb >= 0:
                drain_pct += la - lb
    dt = total["dt"]
    print(f"\n== {name}: {len(rows)} intervals, {fmt_h(dt)}")
    if dt <= 0:
        return
    per_h = 3_600_000 / dt

    def rate(key):
        return total[key] * per_h

    print(f"  battery drain     : {drain_pct * per_h:.2f} %/h"
          + (f", {drain_uah * per_h / 1000:.1f} mAh/h (charge counter)" if drain_uah else ""))
    print(f"  CPU awake         : {100 * total['awake'] / dt:.1f} % of the time")
    print(f"  BBDNS CPU         : {rate('cpu') / 1000:.1f} s/h "
          f"({100 * total['cpu'] / max(total['awake'], 1):.2f} % of awake time)")
    print(f"  loop wake-ups     : {rate('loops'):.0f}/h  (short 100 ms rechecks {rate('rck'):.0f}/h, "
          f"timeouts {rate('idle'):.0f}/h)")
    print(f"  zero-window ACKs  : {rate('zwin'):.0f}/h")
    print(f"  TUN in / out      : {rate('tin'):.0f} / {rate('tout'):.0f} pkt/h, "
          f"{rate('tinb') / 1e6:.2f} / {rate('toutb') / 1e6:.2f} MB/h")
    print(f"  socket events     : {rate('sock'):.0f}/h, JNI rule calls {rate('jni'):.0f}/h")
    print(f"  DNS allowed/block : {rate('allow'):.0f} / {rate('block'):.0f} per h")
    print(f"  DoH net/cache/fail: {rate('doh'):.0f} / {rate('dohc'):.0f} / {rate('dohf'):.0f} per h")
    print(f"  app traffic rx/tx : {rate('rx') / 1e6:.2f} / {rate('tx') / 1e6:.2f} MB/h")
    if threads:
        print("  busiest threads   : " + ", ".join(
            f"{t} {ms * per_h / 1000:.1f}s/h" for t, ms in threads.most_common(8)))


def main():
    if len(sys.argv) != 2:
        print(__doc__)
        sys.exit(1)
    header, samples, events = parse(sys.argv[1])
    for line in header:
        print(line)
    if not samples:
        print("No samples in the log.")
        return
    print(f"\nFrom {samples[0]['ts']} to {samples[-1]['ts']}: {len(samples)} samples, {len(events)} events")

    groups = defaultdict(list)
    prev = None
    for row in samples:
        # A first sample after start/clear has no deltas.
        if "dt" in row:
            if row.get("chg") == "1":
                key = "charging"
            elif row.get("scr") == "1":
                key = "screen on"
            elif row.get("doze") == "1":
                key = "screen off, Doze"
            else:
                key = "screen off, not in Doze"
            groups[key].append((prev, row))
            if row.get("chg") != "1" and row.get("scr") != "1":
                groups["screen off (all)"].append((prev, row))
        prev = row
    for key in ("screen off (all)", "screen off, Doze", "screen off, not in Doze", "screen on", "charging"):
        if groups.get(key):
            summarize(key, groups[key])

    sessions = [(num(r, "tcp"), num(r, "udp")) for r in samples if "tcp" in r]
    if sessions:
        print(f"\nSessions: max TCP {max(s[0] for s in sessions)}, max UDP {max(s[1] for s in sessions)}")
    counts = Counter(e.get("event", "?") for e in events)
    print("Events: " + ", ".join(f"{k}={v}" for k, v in counts.most_common()))

    # Intervals where the event loop spun: many 100 ms waits that ended without
    # any event, or keep-alive ACKs for TCP flows with a full window. A high
    # "rck" alone is normal under traffic: the loop also uses the short wait
    # whenever it ran less than 100 ms after the previous session cleanup.
    spins = [r for r in samples if num(r, "idle") > 300 or num(r, "zwin") > 0]
    if spins:
        print(f"\nWARNING: {len(spins)} intervals where the event loop spun "
              "(>300 empty wake-ups or TCP flows with a full window):")
        for r in spins[:10]:
            print(f"  {r['ts']} scr={r.get('scr')} rck={r.get('rck')} idle={r.get('idle')} "
                  f"zwin={r.get('zwin')} tcp={r.get('tcp')}")


if __name__ == "__main__":
    main()
