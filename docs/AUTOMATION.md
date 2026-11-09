# Automation

BBDNS can be controlled by automation apps and scripts through Android
broadcasts. This page is for advanced users: normal use does not require any
automation. The Home screen, the Quick Settings tile and the home screen widget
already start, stop and resume protection with one tap.

Typical uses:

- start protection when you join your home Wi-Fi;
- stop it when you leave;
- pause the DNS filter for 30 minutes while you use a banking app or make a
  payment;
- control protection from Home Assistant, Tasker, MacroDroid or a personal
  script.

## Requirements

- Protection must have been started at least once manually, so Android has
  granted the VPN permission.
- Send the broadcast explicitly to the `com.buzzqw.bbdns` package, as shown in
  the examples below.
- Android 12 and later can delay or refuse a background start if the system
  does not allow BBDNS to start its foreground service.

## Actions

| Action | Effect |
|---|---|
| `com.buzzqw.bbdns.action.START_PROTECTION` | Start protection. Requires the VPN permission already granted. |
| `com.buzzqw.bbdns.action.STOP_PROTECTION` | Stop protection. |
| `com.buzzqw.bbdns.action.TOGGLE_PROTECTION` | Stop if running, start otherwise. |
| `com.buzzqw.bbdns.action.PAUSE_PROTECTION` | Pause DNS blocking. Add the `minutes` integer extra (default 15, max 1440). The VPN stays up. |
| `com.buzzqw.bbdns.action.RESUME_PROTECTION` | End a pause now. |

## Pause

A pause only suspends DNS blocking: the VPN connection remains active, so
Android does not show the VPN as disconnected. If the system restarts the VPN
service during a pause, protection comes back still paused for the remaining
time instead of blocking again early.

The default pause is 15 minutes when the extra is missing or invalid. Values
outside 1–1440 minutes are clamped to that range.

## Examples with adb

```bash
adb shell am broadcast -a com.buzzqw.bbdns.action.START_PROTECTION -p com.buzzqw.bbdns
adb shell am broadcast -a com.buzzqw.bbdns.action.PAUSE_PROTECTION --ei minutes 30 -p com.buzzqw.bbdns
adb shell am broadcast -a com.buzzqw.bbdns.action.RESUME_PROTECTION -p com.buzzqw.bbdns
```

## Automation apps

In Tasker, MacroDroid or a similar app use a “send broadcast” or “send intent”
action with:

- **Action**: one of the action strings in the table above.
- **Package**: `com.buzzqw.bbdns`.
- **Extra** (only for pause): integer `minutes`.

## Privacy and security

The receiver only changes the protection state. It never exposes logs,
settings, blocked domains or other app data. Any app on the device can send
these intents, so automation is a convenience feature and not an
authentication mechanism.
