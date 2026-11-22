# Accessibility

**BetterBlockerDNS (BBDNS)** is a filter that runs in the background, but every
decision it makes is shown in an app someone has to read: which app is blocked,
why a domain was blocked, how much was blocked today. This document describes
what the app does for people who use a screen reader, large text or a switch,
how to check it, and where the gaps still are.

Accessibility bugs are regular bugs. If something below is not true on your
device, report it as an issue with the screen, the Android version and the
assistive technology you use.

## What the app relies on

BBDNS is built with Jetpack Compose, so most of its accessibility comes from
Compose semantics rather than from layout XML:

- **Screen readers (TalkBack).** Icon-only controls carry a content description;
  icons that only decorate a label are hidden from the reader, so the label is
  not read twice. Lists expose their rows one at a time, with the state of a
  switch included in the announcement.
- **Large text and display size.** Screens are built with `sp` text and
  wrapping rows: labels grow instead of being clipped, and settings rows put the
  value on a second line when it does not fit. The font scale set in Android is
  respected, up to the largest step the system offers.
- **Contrast and colour.** The theme follows the system light/dark choice and,
  on Android 12 and newer, the system dynamic colour. Text and controls use
  theme colours rather than hard-coded ones, so a high-contrast theme applied by
  the system carries through. The optional AMOLED black and custom accent
  colours keep the same text contrast.
- **Touch targets.** Interactive rows are at least 48 dp high, which is the
  Android minimum, including the small switches at the end of a settings row.
- **No time limits.** Onboarding, dialogs and the "why was this blocked?"
  scan wait for the user; nothing closes on a timer.
- **Motion.** The app does not use looping animations, parallax or auto-playing
  video. Transitions are short and follow the system animation scale: turning
  animations off in Android removes them.

## The notice page

The local page that explains a blocked site is plain HTML with no JavaScript,
no web fonts and no images:

- it declares `lang="en"` and a viewport, so it scales and reflows;
- it supports light and dark mode through `prefers-color-scheme`;
- body text is 16 px with a 1.5 line height, and every action is a full-width
  link rather than a small hit area;
- it sends `Content-Security-Policy: default-src 'none'`, so nothing else can
  load and change what is announced.

The page is served by the phone itself, so it works with no network.

## How to check a change

Before releasing a UI change, walk through this by hand:

1. **TalkBack** (Settings → Accessibility → TalkBack): open the home screen,
   the log, the blocklist screen and a settings sub-screen. Every control is
   reachable, announced by name, and its state is spoken ("on", "off",
   "checked").
2. **Font size at maximum** (Settings → Display → Font size): no label is cut
   off, no switch overlaps its text, and no button becomes unreachable.
3. **Display size at maximum**: the same, with the layout in its narrowest
   form.
4. **Dark theme and AMOLED black**: text remains readable on every screen,
   including dialogs and the paused-protection banner.
5. **Switch access or Voice Access**, if available: the main actions
   (start, stop, pause, open the log) can be reached without precise dragging.

## Known gaps

These are real and known, not aspirational:

- The traffic and statistics charts describe their data in text next to the
  chart, but the chart itself is not exposed as a single summarised element.
- The domain lists are long: TalkBack can read them, but there is no
  "jump to letter" navigation.
- Some third-party dialogs (the system file picker for backups, the system
  Private DNS screen) are outside the app and follow Android's own behaviour.
- The app is translated into seven languages; a few newer strings are still
  English-only, which a screen reader will read with the English voice.

## Contributing

Accessibility fixes are welcome and do not need a design discussion: a clearer
content description, a larger touch target or a missing heading is a bug fix.
Include what you changed and how you verified it with the checklist above.
