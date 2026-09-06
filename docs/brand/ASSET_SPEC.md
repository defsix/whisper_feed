# Whisper — brand asset specification

Everything the app and its supporting surfaces need, in one list.

Derived from the **Whisper Brand Toolkit** board plus the Android platform
requirements for each surface. Every row says what to export, at what size, in
which light/dark variants, and where it ends up.

- **Status** — `HAVE` = supplied and usable · `PARTIAL` = supplied but not in a
  usable form · `NEED` = not supplied
- **Priority** — `P0` blocks shipping · `P1` needed for planned milestones ·
  `P2` polish / store / web

---

## 0. Open questions — please answer before exporting

These change what gets exported, so they come first.

| # | Question | Why it matters |
|---|---|---|
| Q1 | **Which symbol is current** — the curved leaf/wave form on the toolkit board, or the three staggered tapered bars in the seven production PNGs supplied separately? | They are different marks. Every asset below descends from one of them. The repo currently ships the three-bar version. |
| Q2 | The board's **"SUBTLE GRADIENT"** swatch — what are its stops and angle? | It is the app icon background and the accent gradient. I will not guess the values. |
| Q3 | Is the app icon background the **gradient** or **flat Cobalt `#2563EB`**? | The board's "01 Default" tile reads as a gradient; the repo currently ships flat Cobalt. |
| Q4 | Bundle **Inter** (Bold/Medium/Regular/Light) as the app-wide typeface, replacing the system font? | Currently the app uses the device default. Inter is on Google Fonts, so nothing needs supplying — just a decision. |
| Q5 | Does the splash line **"Knowledge travels further when it's quiet."** ship, or is it board-only copy? | Affects `strings.xml`. |
| Q6 | Do you want a **Play Store listing** at all? | If not, section 8 is dead and can be skipped entirely. |

---

## 1. Universal rules

Apply to every raster deliverable below.

- **PNG-24 with a real alpha channel**, sRGB. No background matte, no white
  fill behind transparent areas, no palette/8-bit PNGs.
- **No pre-applied padding** unless a row explicitly asks for it. Android
  applies its own insets; padding baked into the file gets doubled.
- **No pre-applied corner rounding or shadows** on icon layers. The system
  masks and shadows adaptive icons itself.
- **Square assets must be exactly square**, centred on the true canvas centre.
- Name files exactly as the `Export as` column says. Lower case, underscores.
- Where a row lists `_light` / `_dark`, the suffix describes **the background
  the asset sits on**, not the colour of the asset:
  - `_light` = for use **on light backgrounds** → dark navy artwork
  - `_dark` = for use **on dark backgrounds** → white artwork
- **An SVG always beats a PNG.** Any row marked *(SVG covers this)* disappears
  entirely if the vector master exists.

### Suggested delivery structure

```
whisper_assets/
  vector/        symbol.svg, wordmark.svg, lockups/*.svg
  icon/          icon_foreground.png, icon_background.png, icon_monochrome.png
  logos/         logo_*.png, lockup_*.png, wordmark_*.png
  splash/        splash_icon.png, splash_branding_*.png
  onboarding/    onboarding_bg_*.png
  placeholders/  placeholder_article_*.png, empty_*.png
  store/         play_icon.png, feature_graphic.png
  web/           favicon_*.png
```

---

## 2. Vector masters — the highest-value items

One file here removes a dozen rows below.

| Priority | Status | Export as | Format | Variants | Notes |
|---|---|---|---|---|---|
| **P0** | NEED | `symbol.svg` | SVG, square viewBox, art centred | one file, `currentColor` or a single fill | **The single most useful file in this document.** Yields the launcher foreground, monochrome layer, notification icon, splash icon and every favicon size, with no rasterisation and no safe-zone guesswork. If gradients are baked in, also supply a **flat single-colour** version as `symbol_flat.svg`. |
| P1 | NEED | `wordmark.svg` | SVG, outlined text | one file | "Whisper" with the letterforms converted to paths, so no font dependency. |
| P1 | NEED | `lockup_horizontal.svg`, `lockup_stacked.svg` | SVG, outlined text | one file each | Symbol + wordmark, correctly spaced. Colours can be recoloured per variant. |

---

## 3. Launcher icon — Android adaptive icon

Board section: **APP ICON CONCEPTS**.

The board shows four finished tiles (01 Default, 02 Light, 03 Dark, 04
Material You). **Android cannot use finished tiles.** It composes three
independent square layers and applies its own mask, which differs per launcher
(circle, squircle, rounded square, teardrop). So concepts 02, 03 and 04 are
generated automatically from the layers — do not export them.

### Geometry, stated once

Each layer is a **108 × 108 dp** square. Of that:

- the outer **18 dp on every edge is always cropped** — it exists only for
  parallax and masking;
- the central **72 × 72 dp** is what a mask can show;
- the recommended **safe zone is a 66 dp diameter circle** at the centre —
  keep all artwork inside it.

In percentages of the exported square: **artwork must fit within the centre
61%**, measured as a circle.

| Priority | Status | Export as | Size | Variants | Style rules |
|---|---|---|---|---|---|
| **P0** | PARTIAL | `icon_foreground.png` | 1024×1024 px, RGBA (I downscale to 432/324/216/162/108) | one | Symbol only on full transparency. Artwork inside the centre-61% circle. **No** background, **no** rounded corners, **no** shadow, **no** padding beyond the safe-zone rule. Colour: white if the background is Cobalt/gradient. *(SVG covers this)* |
| **P0** | NEED | `icon_background.png` | 1024×1024 px, **RGB — no alpha** | one | Full-bleed to all four edges. Flat colour or the gradient from Q2/Q3. **No** rounded corners, **no** vignette, **no** artwork. If it is a flat colour or a simple linear gradient, send hex values instead of a file and I will define it in XML. |
| **P0** | NEED | `icon_monochrome.png` | 1024×1024 px, **alpha-only** (any RGB, ignored) | one | **Flat solid silhouette.** No gradient, no tonal shading, no soft inner edges. Android discards all colour and tints the alpha channel, so anything with tone renders as a smudge. Same centre-61% circle. Board concept "Favicon 03 Monochrome" is the right shape. *(SVG covers this, if a flat variant exists)* |

Legacy square/round PNG icons are **not** required — `minSdk` is 26, so the
adaptive icon is used on every supported release.

---

## 4. Notification icon

Not on the board. The app currently ships upstream Neo Feed's **Phosphor bell**
— this is the most visibly wrong asset in the project.

| Priority | Status | Export as | Size | Variants | Style rules |
|---|---|---|---|---|---|
| **P0** | NEED | `icon_notification.svg` | 24 × 24 dp viewBox | one | **Pure white, completely flat, on transparency.** Android renders the alpha channel only and tints it — colour and gradient are discarded. Must stay legible at 24 dp; the symbol will likely need thickened strokes and increased separation between elements at this size, so a small-size optical variant is expected rather than a straight scale-down. |
| P0 | NEED | `icon_notification.png` ×5 | 24, 36, 48, 72, 96 px | one | Raster fallback, only if no SVG. mdpi → xxxhdpi. |

---

## 5. Logos and lockups

Board sections: **PRIMARY LOGO**, **SYMBOL MARK**, **ALTERNATE LOGO LOCKUPS**.

Every lockup needs **both** variants — the app has light and dark themes and
follows the system setting.

| Priority | Status | Board label | Export as | Size | Variants | Used for |
|---|---|---|---|---|---|---|
| P1 | HAVE | Symbol Mark | `symbol_gradient.png` | 1254×1254 RGBA | gradient | in-app mark, About page |
| P1 | HAVE | Symbol Mark | `symbol_white.png` | 1254×1254 RGBA | white | launcher foreground source, splash |
| P1 | NEED | Symbol Mark | `symbol_navy.png` | 1024×1024 RGBA | navy | mark on light backgrounds where gradient is too loud |
| P1 | PARTIAL | Primary Logo | `logo_primary_light.png` / `logo_primary_dark.png` | ≥2400 px wide, RGBA | both | README, About, docs. *Have the dark-type version; need the white-type one.* |
| P1 | HAVE | Alt lockup 1 — horizontal | `lockup_horizontal_light.png` / `_dark.png` | 2172×724 RGBA | both | app bar, splash branding |
| P1 | PARTIAL | Alt lockup 2 — stacked | `lockup_stacked_light.png` / `_dark.png` | ≥1600 px wide, RGBA | both | onboarding, About. *Have dark-type; need white-type.* |
| P1 | PARTIAL | Alt lockup 3 — wordmark only | `wordmark_light.png` / `wordmark_dark.png` | 2172×724 RGBA | both | docs, footers. *Have dark-type; need white-type.* |
| — | n/a | Alt lockup 4 — dark card | — | — | — | **Not needed.** It is lockup 1 composited on a navy card; I can produce that from the parts. |

Tagline handling: the board shows lockups both with and without
"YOUR FEEDS, YOUR FOCUS." Please export **with-tagline and without-tagline**
versions of the primary logo and the horizontal lockup — the app bar needs the
short one, the About page the long one.

---

## 6. Splash screen

Board mockup: **SPLASH SCREEN**. Not yet implemented in the app.

Uses the Android 12+ `SplashScreen` API, which has its own geometry, unrelated
to the launcher icon.

| Priority | Status | Export as | Size | Variants | Style rules |
|---|---|---|---|---|---|
| P1 | NEED | `splash_icon.png` | 288 × 288 dp canvas → **1152×1152 px**, RGBA | one (white) | Artwork must fit inside a **192 dp diameter circle** at the centre of the 288 dp canvas. If an icon background colour is also set, the limit tightens to a **160 dp circle**. Transparent outside the artwork. *(SVG covers this)* |
| P1 | NEED | `splash_branding_light.png` / `_dark.png` | max 200 × 80 dp → **800×320 px**, RGBA | both | The wordmark or horizontal lockup that sits pinned at the bottom of the splash. Must fit the box without cropping. |
| P1 | NEED | background colour | two hex values | light + dark | Single flat colour per theme. The mockup reads as Cobalt; confirm the dark-theme value. |
| P1 | NEED | copy | text | — | Board shows "Whisper" / "Your feeds, your focus." / "Knowledge travels further when it's quiet." — see Q5. |

---

## 7. Onboarding

Board mockup: **ONBOARDING**. Not yet implemented.

| Priority | Status | Export as | Size | Variants | Style rules |
|---|---|---|---|---|---|
| P1 | NEED | `onboarding_bg_dark.png` | 1080 × 2400 px, RGB | dark | The navy wave/mesh gradient. **This is the only asset in this document I cannot derive from anything else** — it is not geometric and has no vector equivalent I can reconstruct. If it exists as a mesh gradient or layered SVG, that is better still. |
| P1 | NEED | `onboarding_bg_light.png` | 1080 × 2400 px, RGB | light | Light-theme equivalent. The board's header band is close to the right treatment. |
| P2 | NEED | `wave_header.png` | 2400 × 800 px, RGBA | light + dark | The decorative wave from the board header, if you want it reused on any in-app header. Transparent above the wave. |
| P1 | NEED | copy | text | — | "Curate. Read. Breathe." / "A calmer, more focused way to follow the world." / "Get Started" |

---

## 8. Store listing — only if Q6 is yes

| Priority | Status | Export as | Size | Variants | Style rules |
|---|---|---|---|---|---|
| P2 | NEED | `play_icon.png` | **512 × 512 px**, 32-bit PNG | one | Full-bleed square, artwork composed for the square. **No rounded corners, no shadow** — Google Play applies its own mask. This is a flattened composite of the icon foreground over the icon background, so it can be generated once section 3 exists. |
| P2 | NEED | `feature_graphic.png` | **1024 × 500 px**, RGB, no alpha | one | Play listing banner. Text must survive being cropped on small screens. |
| P2 | NEED | screenshots | 1080 × 2400 px, ≥2 and ≤8 | light + dark | Real device captures, not mockups. |

---

## 9. Web / favicons

| Priority | Status | Export as | Size | Variants | Style rules |
|---|---|---|---|---|---|
| P2 | NEED | `favicon_light.png` / `favicon_dark.png` | 512 × 512 px, RGBA | both | Board section **FAVICON CONCEPTS** — "02 Browser" is the light variant, "01 App Bar" the dark. I generate 16 / 32 / 48 / 180 (apple-touch) / 192 / 512 from these. *(SVG covers this entirely)* |
| P2 | NEED | `readme_banner.png` | 1280 × 320 px, RGBA | light | GitHub repo header. Must read on both GitHub themes, so avoid pure white or pure black artwork. |

---

## 10. In-app imagery

Not on the board, but the app renders these today with upstream placeholders.

| Priority | Status | Export as | Size | Variants | Used for |
|---|---|---|---|---|---|
| P1 | NEED | `placeholder_article_light.png` / `_dark.png` | 1280 × 720 px (16:9) | both | Shown on every feed card whose article has no image — currently a flat grey rectangle, and it appears constantly on a real feed. |
| P2 | NEED | `empty_feed_light.png` / `_dark.png` | 600 × 600 px, RGBA | both | "No articles found" state |
| P2 | NEED | `empty_bookmarks_light.png` / `_dark.png` | 600 × 600 px, RGBA | both | "No bookmarks" state |

UI glyphs — search, settings, bookmark, refresh, the bottom-nav icons in the
board mockup — are **not** brand assets. The app uses the Phosphor icon set
already, matching upstream. Nothing to supply unless you want them replaced.

---

## 11. Colour — supplied, no action needed

Board section: **COLOR PALETTE**. Already in `app/src/main/res/values/colors.xml`
and used as the Material 3 seed.

| Name | Hex | Role in the app |
|---|---|---|
| Cobalt | `#2563EB` | Material 3 seed colour; launcher icon background |
| Indigo | `#4F46E5` | secondary accent |
| Sky | `#7DD3FC` | tertiary accent |
| Slate | `#475569` | body text, muted UI |
| Soft | `#E5E7EB` | surfaces, dividers |

**Outstanding:** the "SUBTLE GRADIENT" bar — see **Q2**.

---

## 12. Typography — supplied, decision needed

Board section: **TYPOGRAPHY (GOOGLE FONTS)**.

| Family | Weights shown on the board | Action |
|---|---|---|
| Inter | Bold, Medium, Regular, Light | Nothing to export — Inter is on Google Fonts and can be bundled or fetched via downloadable fonts. See **Q4**. |

---

## 13. Priority summary

**If only three things get made, make these:**

1. **`symbol.svg`** — a flat, single-colour vector of the current symbol.
   Eliminates the launcher foreground, the monochrome layer, the notification
   icon, the splash icon and all six favicon sizes in one file.
2. **The icon background** — either `icon_background.png` at 1024×1024, or just
   the gradient stops and angle as text.
3. **`onboarding_bg_dark.png`** — the only asset here with no derivable source.

**Everything currently blocking (P0):**

- `symbol.svg` *or* a corrected `icon_monochrome.png`
- `icon_background.png` *or* gradient values
- `icon_notification.svg` — the app is still shipping a bell that belongs to
  another project

---

## 14. What the repo holds today

In `docs/brand/07_production/` — supplied, 1254×1254 or larger, RGBA, usable:

| File | Covers |
|---|---|
| `symbol_white.png` | §3 foreground (in use), §6 splash icon |
| `symbol_gradient_01.png`, `symbol_gradient_02.png` | §5 in-app mark |
| `lockup_horizontal_dark.png`, `lockup_horizontal_light.png` | §5 |
| `lockup_stacked_dark.png` | §5 (light-type variant still needed) |
| `wordmark_dark.png` | §5 (light-type variant still needed) |

In `docs/brand/01_logos/` … `06_ui_mockups/` — crops from the concept board,
68–423 px, RGB with **no alpha**. **Reference only, not shippable.** They also
show the earlier symbol design — see **Q1**.
