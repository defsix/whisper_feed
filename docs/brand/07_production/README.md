# Production assets

These are the **production-ready** files supplied directly (not the crops in
the sibling directories, which came out of the concept-board ZIP). Where any
two sources disagree, **these win**.

The symbol has been through three designs. This directory holds the third and
current one: **three curved wave bands**, cyan through indigo with a slate
third band. It replaced a straight-tapered-bar mark, which had itself replaced
the concept board's single leaf/"S" form. Both earlier marks survive only in
git history and in the concept-board crops in the sibling directories.

All files are 8-bit RGBA with a real alpha channel and no background matte.

`_light` and `_dark` name **the background the file sits on**, not the colour of
the artwork: `_light` files carry navy type for light backgrounds, `_dark`
files carry white type for dark ones.

| File | Size | What it is |
|---|---|---|
| `symbol_gradient.png` | 1254×1254 | Symbol, full-colour gradient — the master |
| `symbol_gradient_alt.png` | 1254×1254 | Alternate gradient cut |
| `symbol_navy_flat.png` | 1254×1254 | Symbol as one flat navy shape |
| `symbol_white_flat.png` | 1254×1254 | Symbol as one flat white shape |
| `wordmark_light.png` | 2172×724 | "Whisper" + tagline, navy type |
| `wordmark_dark.png` | 2172×724 | "Whisper" + tagline, white type |
| `lockup_horizontal_light.png` | 2172×724 | Symbol + wordmark, navy type |
| `lockup_horizontal_dark.png` | 2172×724 | Symbol + wordmark, white type |
| `lockup_stacked_light.png` | 1122×1402 | Symbol above wordmark + tagline, navy type |
| `lockup_stacked_dark.png` | 1122×1402 | Same, white type |

Two files were renamed on the way in, because the supplied names did not match
the artwork: the pack's `07_…gradient_plus_wordmark` carries **white** type, not
navy, and its `09_logo_horizontal_with_tagline` is a **stacked** lockup, not a
horizontal one.

Every file is 8-bit RGBA, antialias band ~1.5 px, no interior translucency and
no isolated stray pixels — checked, not assumed. The flat variants are true
single shapes, so they tint cleanly.

## What the app uses

- **Launcher icon** — `symbol_gradient.png` is the source of
  `res/mipmap-*/ic_launcher_foreground.png`, and `symbol_navy_flat.png` of
  `ic_launcher_monochrome.png`. Both are used unmodified apart from being
  cropped to their alpha bounds, scaled and centred on a transparent
  108×108-unit canvas so that every opaque pixel falls inside the adaptive
  icon's recommended 66-unit safe circle (the mark lands at ~47×54 units). The
  background layer is flat `#FBFCFE`.
- **Notification icon** — `symbol_white_flat.png` is the source of
  `res/drawable-*/ic_notification.png` at 24 dp. Android renders the alpha
  channel only and tints it, which is why the flat variant is used rather than
  the gradient. It is legible at 24 dp but the gaps between the bands are down
  to about a pixel; a small-size variant with wider gaps would hold up better.
- **Everything else is unused so far.** The obvious homes are the splash
  screen and the About screen (`lockup_stacked_*.png` /
  `lockup_horizontal_*.png`).

## Still missing for a complete set

Vector masters. Everything here is raster, so the launcher icon is a
downscaled bitmap rather than a `VectorDrawable`, and there is no clean source
for a notification icon at small sizes or for favicons. An SVG of the symbol
would fix all three.
