# What Whisper still needs from you

A shopping list, checked against the repo rather than from memory.
`ASSET_SPEC.md` is the full reference; this is only what is missing.

**Nothing here blocks a build.** Everything below is either a placeholder the
app is currently making do without, or a screen not yet built.

---

## One correction first

I said the symbol needed a light colourway. It does not — both are already in
`07_production/`:

- `symbol_white_flat.png` — the mark for dark grounds
- `symbol_navy_flat.png` — the mark for light grounds

Nothing to export. Ignore that request if you had it on a list.

---

## Universal rules

Apply to everything below.

- **PNG-24, sRGB, real alpha channel.** No white matte behind transparent
  areas, no 8-bit/palette PNGs.
- **No baked-in padding**, no pre-rounded corners, no drop shadows. Android
  applies its own insets and masks; anything baked in gets applied twice.
- `_light` / `_dark` describes **the background it sits on**, not the colour of
  the artwork. `_dark` = artwork for a dark background.
- One file at the size given. I generate the density buckets.

---

## 1. Article placeholder — the one I would do first

Seen on every card whose article has no image, which on a real feed is
constant. Currently a flat grey rectangle.

| File | Size | Notes |
|---|---|---|
| `placeholder_article_light.png` | 1280 × 720 (16:9) | For light theme |
| `placeholder_article_dark.png` | 1280 × 720 (16:9) | For dark theme |

It gets cropped to several aspect ratios — 16:9 on small mosaic tiles, 2:1 on
large ones, 4:3 on the hero — so **keep anything meaningful inside the middle
60% both ways**. It repeats down a scrolling list, so it wants to be quiet:
a texture or a soft wave field reads better than the mark repeated forty times.

---

## 2. Horizontal lockup without the tagline

The lockups in `07_production/` carry "Your feeds, your focus." At app-bar
height that line renders at about 5 px and turns to mush.

| File | Size | Notes |
|---|---|---|
| `lockup_horizontal_notag_light.png` | ~1200 px wide, height to suit | Symbol + wordmark only |
| `lockup_horizontal_notag_dark.png` | ~1200 px wide, height to suit | Same, for dark grounds |

Trim tight to the ink — no surrounding transparent margin.

---

## 3. Onboarding backgrounds

For the onboarding screen, which is not built yet. Flagged in the spec as **the
only asset with no derivable source** — everything else can be reconstructed
from the mark, this cannot.

| File | Size | Notes |
|---|---|---|
| `onboarding_bg_dark.png` | 1080 × 2400, RGB | The navy wave/mesh gradient |
| `onboarding_bg_light.png` | 1080 × 2400, RGB | Light equivalent; the board's header band is close |

Text sits over the **upper third**, so keep that area calm and low-contrast.
A layered SVG or mesh gradient would be better than a flat PNG if it is easy
for you — it would scale to any screen without a second export.

---

## 4. Empty states — lower priority

Small illustrations for two screens that currently show text alone.

| File | Size | Shown when |
|---|---|---|
| `empty_feed_light.png` / `_dark.png` | 600 × 600, RGBA | No articles match the current filter |
| `empty_bookmarks_light.png` / `_dark.png` | 600 × 600, RGBA | Nothing saved yet |

---

## 5. Vector masters — whenever, if ever

Nothing needs them. They would remove every rasterisation step and fix the
small-size cases properly.

| File | Notes |
|---|---|
| `symbol.svg` | Outlined paths, no live text, no embedded rasters |
| `wordmark.svg` | Same |

---

## Delivery

Anywhere is fine — a zip, or dropped into `docs/brand/07_production/`. Names as
written above; I will place them and generate the densities.
