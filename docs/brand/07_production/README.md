# Production assets

These are the **production-ready** files supplied directly (not the crops in
the sibling directories, which came out of the concept-board ZIP). Where the
two disagree, **these win**: the ZIP crops carry an earlier symbol design (a
curved leaf/"S" form); the mark below — three staggered, tapered bars — is the
current one.

All files are 8-bit RGBA with a real alpha channel and no background matte.

| File | Size | What it is |
|---|---|---|
| `symbol_white.png` | 1254×1254 | Symbol, solid white on transparent |
| `symbol_gradient_01.png` | 1254×1254 | Symbol, blue→indigo→sky gradient on transparent |
| `symbol_gradient_02.png` | 1254×1254 | As above, slightly tighter bar spacing |
| `lockup_horizontal_dark.png` | 2172×724 | Symbol + "Whisper" + tagline, navy type — for light backgrounds |
| `lockup_horizontal_light.png` | 2172×724 | Same lockup, white type — for dark backgrounds |
| `wordmark_dark.png` | 2172×724 | "Whisper" + tagline, no symbol, navy type |
| `lockup_stacked_dark.png` | 1448×1086 | Symbol above wordmark + tagline, navy type |

## What the app uses

- **Launcher icon** — `symbol_white.png` is the source of
  `res/mipmap-*/ic_launcher_foreground.png`. It is used unmodified apart from
  being cropped to its alpha bounds, scaled, and centred on a transparent
  108×108-unit canvas so that every opaque pixel falls inside the adaptive
  icon's recommended 66-unit safe circle (the mark ends up ~39×55 units). The
  same file backs the `<monochrome>` layer, so themed icons get the real mark
  rather than a silhouette of something else. The background layer is flat
  Cobalt `#2563EB` from the palette.
- **Everything else is unused so far.** The obvious homes are the splash
  screen and the About screen (`lockup_stacked_dark.png` /
  `lockup_horizontal_*.png`), and a notification icon (which needs a solid
  white, single-colour asset — `symbol_white.png` works).

## Still missing for a complete set

Vector masters. Everything here is raster, so the launcher icon is a
downscaled bitmap rather than a `VectorDrawable`, and there is no clean source
for a notification icon at small sizes or for favicons. An SVG of the symbol
would fix all three.
