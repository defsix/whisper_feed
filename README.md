# Whisper

**Your feeds, your focus.**

**A Pixel-quality, Lawnchair-native RSS feed with user-controlled sources, multiple layouts, transparent personalisation, and private cross-device sync.**

Whisper occupies Lawnchair's left-most **minus-one** page — the slot the Pixel
Launcher reserves for Google Discover. Swipe right from Home and you get a
continuously scrolling, personalised feed built from RSS/Atom sources you chose
yourself.

It is a genuine native launcher overlay surface, **not** a home-screen widget.

```
Home screen → swipe right → Whisper
```

## Status

Early development. `ROADMAP.md` has the milestone-by-milestone position and
what comes next; `UPSTREAM_NOTES.md` has the launcher-integration findings the
project is built on.

- **Milestone 0 — launcher feasibility: complete.** Upstream builds, the
  overlay provider mechanism is documented, and minus-one replacement is
  confirmed working on-device against Lawnchair.
- **Milestone 1 — Material shell: in progress.** App identity is renamed to
  `io.zero76.whisper` / "Whisper"; the UI is still upstream's.

## Principles

- Pixel/Material 3 quality, with Material You dynamic colour and light/dark/system themes.
- Local-first: Room is the source of truth; the app works fully offline and without any account.
- You own your sources — add, remove, edit, categorise, reorder, mute, import and export freely.
- Transparent personalisation. "More like this" / "Less like this" signals you can inspect and reset, never a mandatory opaque algorithm.
- Chronological ordering is always available and is the default.
- Feedly-compatible via OPML, never Feedly-dependent.
- No ads, no sponsored stories, no analytics or telemetry by default.

## Explicit non-goals

Whisper does not ingest, scrape, or synchronise a user's actual Google
Discover stream. There is no supported public API for that, and the
alternatives (scraping the Google app, accessibility hacks, reverse-engineering
private endpoints) are brittle and inappropriate. The goal is to reproduce the
*quality of the experience* with sources you control.

## Building

Requires JDK 17+ and an Android SDK with platform 37 and build-tools 36.

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

### Getting Lawnchair to use it

Lawnchair only accepts feed providers on a hardcoded package whitelist, and
`io.zero76.whisper` is not on it. To use a local build, unlock Lawnchair's debug
menu and turn the whitelist check off:

1. Open the App Drawer, tap the search field, and type `/lawnchairdebug`.
2. Open Lawnchair Settings — a build icon now appears in the overflow area — and go to **Debug menu**.
3. Enable **Ignore feed whitelist**.
4. In **Home screen settings → Feed provider**, select **Whisper**.

No root, LSPosed, or Shizuku is required. The mechanism and the reasoning
behind it are documented in `UPSTREAM_NOTES.md` §3.

### Granting "Display over other apps"

The app needs this permission to open articles you tap — the feed itself
renders without it, but taps do nothing. On a sideloaded build the toggle is
greyed out, because Android restricts sensitive permissions for apps not
installed from an app store. Unblock it via **Settings → Apps → Whisper → ⋮
→ Allow restricted settings**, then grant it. See `UPSTREAM_NOTES.md` §3b for
why the permission is needed.

## Brand

Brand assets and the palette live in [`docs/brand/`](docs/brand/). The
production-ready artwork is in
[`docs/brand/07_production/`](docs/brand/07_production/) and is what the app
ships; the other directories are concept-board crops kept for reference and
show an earlier version of the symbol.

## Licence and attribution

Whisper is licensed under the **GPLv3+** — see [`LICENSE`](LICENSE).

It is a fork of [Neo Feed](https://github.com/NeoApplications/Neo-Feed), whose
launcher-overlay implementation it retains. See
[`ATTRIBUTION.md`](ATTRIBUTION.md) for full upstream copyright and credits.
