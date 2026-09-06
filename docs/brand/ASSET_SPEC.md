# Whisper — brand asset specification

Everything the app and its supporting surfaces need, in one list.

Derived from the **Whisper Brand Toolkit** board, the production artwork
supplied separately, and the Android platform requirements for each surface.
Every row says what to export, at what size, in which light/dark variants, and
where it ends up.

- **Status** — `HAVE` = supplied and usable · `PARTIAL` = supplied but not in a
  usable form · `NEED` = not supplied
- **Priority** — `P0` blocks shipping · `P1` needed for planned milestones ·
  `P2` polish / store / web

---

## 0. Decisions

Six questions were open; all are answered. Recorded here because each one
changes what gets exported.

| # | Decision |
|---|---|
| D1 | **The three-bar symbol is the mark.** The curved leaf/wave form on the toolkit board is retired. The concept-board crops in `01_logos/` – `06_ui_mockups/` all show the retired design and are historical reference only. |
| D2 | **The board's "SUBTLE GRADIENT" is presentation-only.** Not a brand token, not used in-app. Available for store and marketing backgrounds; exact stops still to be captured if a Play feature graphic is ever made. |
| D3 | **Launcher icon = gradient symbol on near-white** (board concept "02 Light"). Background sampled from the board tile: `#FBFCFE`. |
| D4 | **Inter is bundled**, with an in-app font setting so the user can change it. |
| D5 | **"Knowledge travels further when it's quiet."** does not ship. Held as reserve copy — see §15. |
| D6 | **Distribution is staged:** GitHub Releases now → F-Droid next → Play Store long term. All three sets of assets are specified in §8. |

### Consequence of D3 worth stating plainly

The gradient symbol on a near-white background is the lowest-contrast of the
four board concepts, and the pale Sky-blue bottom bar is its weakest element.
This is a deliberate choice, recorded so it is not rediscovered as a bug. If it
proves illegible at 48 dp on a busy wallpaper, the fallback is board concept
"01 Default" — white symbol on Cobalt — which needs no new artwork.

### Not an asset problem, but blocks real-world install

Lawnchair whitelists feed providers by **package name and signing
certificate**. A Whisper installed from any store still needs the
`/lawnchairdebug` override until `io.zero76.whisper` is added to Lawnchair's
`FeedBridge.kt` upstream. That is a pull request, not an export.

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
  vector/        symbol.svg, symbol_flat.svg, wordmark.svg, lockups/*.svg
  icon/          icon_monochrome.png
  logos/         logo_*.png, lockup_*.png, wordmark_*.png
  splash/        splash_icon.png, splash_branding_*.png
  onboarding/    onboarding_bg_*.png
  placeholders/  placeholder_article_*.png, empty_*.png
  store/         feature_graphic.png, screenshots/
  web/           favicon_*.png
```

---

## 2. Vector masters — the highest-value items

One file here removes a dozen rows below.

| Priority | Status | Export as | Format | Variants | Notes |
|---|---|---|---|---|---|
| **P0** | NEED | `symbol_flat.svg` | SVG, square viewBox, art centred, **single flat fill** | one | **The single most useful file in this document.** Yields the monochrome icon layer, the notification icon and every favicon size, with no rasterisation and no safe-zone guesswork. Flat means one solid colour — no gradients, no overlapping translucency. |
| P1 | NEED | `symbol.svg` | SVG, gradients intact | one | The full-colour mark as vector. Replaces the 1254 px raster for the launcher foreground, splash and About page, and scales without softening. |
| P1 | NEED | `wordmark.svg` | SVG, outlined text | one | "Whisper" with letterforms converted to paths, so there is no font dependency. |
| P1 | NEED | `lockup_horizontal.svg`, `lockup_stacked.svg` | SVG, outlined text | one each | Symbol + wordmark, correctly spaced. Recolourable per variant. |

---

## 3. Launcher icon — Android adaptive icon

Board section: **APP ICON CONCEPTS**. Decision **D3**: concept "02 Light".

The board shows four finished tiles. **Android cannot use finished tiles.** It
composes three independent square layers and applies its own mask, which
differs per launcher (circle, squircle, rounded square, teardrop). Concepts 01,
03 and 04 are the same three layers under different system themes — do not
export them.

### Geometry, stated once

Each layer is a **108 × 108 dp** square. Of that:

- the outer **18 dp on every edge is always cropped** — it exists only for
  parallax and masking;
- the central **72 × 72 dp** is what a mask can show;
- the recommended **safe zone is a 66 dp diameter circle** at the centre —
  keep all artwork inside it.

In percentages of the exported square: **artwork must fit within the centre
61%**, measured as a circle.

| Priority | Status | Asset | Size | Variants | Style rules |
|---|---|---|---|---|---|
| P0 | **HAVE** | `symbol_gradient_01.png` → `ic_launcher_foreground` | 1254×1254 RGBA supplied; downscaled to 432/324/216/162/108 | one | The gradient symbol on full transparency. Cropped to alpha bounds and centred in the safe circle at build time — no manual padding wanted. Superseded by `symbol.svg` when that exists. |
| P0 | **HAVE** | background | — | one | Flat `#FBFCFE`, defined as a colour resource. **No file needed.** |
| **P0** | **NEED** | `icon_monochrome.png` | 1024×1024 px, **alpha-only** (RGB ignored) | one | **Flat solid silhouette.** No gradient, no tonal shading, no soft inner edges, no overlapping translucency between the three bars. Android discards all colour and tints the alpha channel, so anything with tone renders as a smudge. Same centre-61% circle. *(`symbol_flat.svg` covers this)* |

Legacy square/round PNG icons are **not** required — `minSdk` is 26, so the
adaptive icon is used on every supported release.

---

## 4. Notification icon

Not on the board. The app currently ships upstream Neo Feed's **Phosphor bell**
— the most visibly wrong asset in the project.

| Priority | Status | Export as | Size | Variants | Style rules |
|---|---|---|---|---|---|
| **P0** | NEED | `icon_notification.svg` | 24 × 24 dp viewBox | one | **Pure white, completely flat, on transparency.** Android renders the alpha channel only and tints it — colour and gradient are discarded. Must stay legible at 24 dp; the three bars will likely need thickening and wider separation at this size, so expect a small-size optical variant rather than a straight scale-down. *(`symbol_flat.svg` may cover this if the shape survives shrinking)* |
| P0 | NEED | `icon_notification.png` ×5 | 24, 36, 48, 72, 96 px | one | Raster fallback, only if no SVG. mdpi → xxxhdpi. |

---

## 5. Logos and lockups

Board sections: **PRIMARY LOGO**, **SYMBOL MARK**, **ALTERNATE LOGO LOCKUPS** —
all of which show the retired leaf mark (**D1**). The rows below are what the
three-bar mark needs.

Every lockup needs **both** variants — the app has light and dark themes and
follows the system setting.

| Priority | Status | Export as | Size | Variants | Used for |
|---|---|---|---|---|---|
| P1 | HAVE | `symbol_gradient_01.png` | 1254×1254 RGBA | gradient | launcher foreground, About page, splash |
| P1 | HAVE | `symbol_gradient_02.png` | 1254×1254 RGBA | gradient, tighter spacing | alternate |
| P1 | HAVE | `symbol_white.png` | 1254×1254 RGBA | white | use on Cobalt or dark surfaces |
| P2 | NEED | `symbol_navy.png` | 1024×1024 RGBA | navy | single-colour mark where the gradient is too loud |
| P1 | PARTIAL | `logo_primary_light.png` / `logo_primary_dark.png` | ≥2400 px wide, RGBA | both | README, About, docs. *Have the navy-type version; need the white-type one.* |
| P1 | HAVE | `lockup_horizontal_light.png` / `_dark.png` | 2172×724 RGBA | both | app bar, splash branding |
| P1 | PARTIAL | `lockup_stacked_light.png` / `_dark.png` | ≥1600 px wide, RGBA | both | onboarding, About. *Have navy-type; need white-type.* |
| P1 | PARTIAL | `wordmark_light.png` / `wordmark_dark.png` | 2172×724 RGBA | both | docs, footers. *Have navy-type; need white-type.* |

Tagline handling: export **with-tagline and without-tagline** versions of the
primary logo and the horizontal lockup — the app bar needs the short one, the
About page the long one.

---

## 6. Splash screen

Board mockup: **SPLASH SCREEN**. Not yet implemented.

Uses the Android 12+ `SplashScreen` API, which has its own geometry, unrelated
to the launcher icon.

| Priority | Status | Export as | Size | Variants | Style rules |
|---|---|---|---|---|---|
| P1 | NEED | `splash_icon.png` | 288 × 288 dp canvas → **1152×1152 px**, RGBA | one | Artwork must fit inside a **192 dp diameter circle** at the centre of the 288 dp canvas. If an icon background colour is also set, the limit tightens to a **160 dp circle**. Transparent outside the artwork. The board's splash shows the **white** symbol on Cobalt, so `symbol_white.png` is the likely source. *(`symbol.svg` covers this)* |
| P1 | NEED | `splash_branding_light.png` / `_dark.png` | max 200 × 80 dp → **800×320 px**, RGBA | both | The wordmark or horizontal lockup pinned at the bottom of the splash. Must fit the box without cropping. |
| P1 | NEED | background colour | two hex values | light + dark | Single flat colour per theme. The board reads as Cobalt; the dark-theme value is unconfirmed. |

Splash copy: "Whisper" / "Your feeds, your focus." Both already in
`strings.xml`. The third board line does not ship (**D5**).

---

## 7. Onboarding

Board mockup: **ONBOARDING**. Not yet implemented.

| Priority | Status | Export as | Size | Variants | Style rules |
|---|---|---|---|---|---|
| P1 | NEED | `onboarding_bg_dark.png` | 1080 × 2400 px, RGB | dark | The navy wave/mesh gradient. **The only asset in this document that cannot be derived from anything else** — not geometric, no vector equivalent to reconstruct. A mesh gradient or layered SVG would be better still. |
| P1 | NEED | `onboarding_bg_light.png` | 1080 × 2400 px, RGB | light | Light-theme equivalent. The board's header band is close to the right treatment. |
| P2 | NEED | `wave_header.png` | 2400 × 800 px, RGBA | light + dark | The decorative wave from the board header, if reused on any in-app header. Transparent above the wave. |

Onboarding copy, from the board: "Curate. Read. Breathe." /
"A calmer, more focused way to follow the world." / "Get Started"

---

## 8. Distribution — staged (D6)

### 8a. GitHub Releases — near term

| Priority | Status | Asset | Notes |
|---|---|---|---|
| P1 | — | signed APK | Already produced per build. Needs a release signing key, not a debug one. |
| P2 | NEED | `readme_banner.png` — 1280 × 320 px, RGBA | Repo header. Must read on both GitHub themes, so avoid pure white and pure black artwork. |

No store artwork required.

### 8b. F-Droid — intermediate

F-Droid builds from source and takes the launcher icon out of the APK, so
there is no separate store icon. Metadata follows the Fastlane layout at
`fastlane/metadata/android/en-US/`.

| Priority | Status | Asset | Size | Variants |
|---|---|---|---|---|
| P1 | NEED | `phoneScreenshots/1..8.png` | 1080 × 2400 px | **light and dark sets** — F-Droid shows them in order, so lead with the minus-one feed |
| P2 | NEED | `featureGraphic.png` | 1024 × 500 px, RGB no alpha | one |
| P1 | NEED | `short_description.txt` | ≤80 characters | — |
| P1 | NEED | `full_description.txt` | ≤4000 characters | — |
| P1 | — | `changelogs/<versionCode>.txt` | ≤500 characters each | generated per release |

Also required, not artwork: reproducible builds, no proprietary dependencies,
and a metadata pull request to `fdroiddata`.

### 8c. Play Store — long term

| Priority | Status | Asset | Size | Style rules |
|---|---|---|---|---|
| P2 | NEED | `play_icon.png` | **512 × 512 px**, 32-bit PNG | Full-bleed square. **No rounded corners, no shadow** — Play applies its own mask. A flattened composite of §3's foreground over `#FBFCFE`, so generatable once §3 is settled. |
| P2 | NEED | `feature_graphic.png` | **1024 × 500 px**, RGB, no alpha | Text must survive cropping on small screens. The D2 gradient is the intended background here. |
| P2 | NEED | screenshots | 1080 × 2400 px, 2–8 of them | Real device captures, not mockups. |

Also required, not artwork: a Play developer account, a hosted privacy policy,
target-API compliance, and a permissions declaration for
`SYSTEM_ALERT_WINDOW`.

---

## 9. Web / favicons

| Priority | Status | Export as | Size | Variants | Style rules |
|---|---|---|---|---|---|
| P2 | NEED | `favicon_light.png` / `favicon_dark.png` | 512 × 512 px, RGBA | both | Board section **FAVICON CONCEPTS** — "02 Browser" is the light variant, "01 App Bar" the dark. 16 / 32 / 48 / 180 (apple-touch) / 192 / 512 are generated from these. *(`symbol.svg` covers this entirely)* |

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
| Cobalt | `#2563EB` | Material 3 seed colour; splash background |
| Indigo | `#4F46E5` | secondary accent |
| Sky | `#7DD3FC` | tertiary accent |
| Slate | `#475569` | body text, muted UI |
| Soft | `#E5E7EB` | surfaces, dividers |
| *(icon ground)* | `#FBFCFE` | launcher icon background (**D3**, sampled from the board tile) |

The board's "SUBTLE GRADIENT" is presentation-only (**D2**) and is not a token.

---

## 12. Typography — bundled (D4)

Board section: **TYPOGRAPHY (GOOGLE FONTS)**.

| Family | Weights | Action |
|---|---|---|
| Inter | Bold, Medium, Regular, Light | **Bundled** in the APK (~400 KB). Nothing to export — Inter is on Google Fonts under the SIL Open Font License. |

Plus an in-app setting, `Appearance → Font`:

| Option | Notes |
|---|---|
| Inter | default |
| System default | inherits the device font and the user's font-family setting |
| A serif | for article reading; family to be chosen |

---

## 13. Priority summary

**If only three things get made, make these:**

1. **`symbol_flat.svg`** — a flat, single-colour vector of the three-bar mark.
   Clears the monochrome icon layer, the notification icon and all six favicon
   sizes in one file.
2. **`icon_notification.svg`** — or confirmation that the flat symbol reads at
   24 dp. The app is currently shipping another project's bell.
3. **`onboarding_bg_dark.png`** — the only asset with no derivable source.

**Everything currently blocking (P0):**

- `symbol_flat.svg` *or* a flat `icon_monochrome.png` at 1024²
- `icon_notification.svg` *or* the five raster sizes

The launcher foreground and background are settled — the artwork exists and the
background is a hex value.

---

## 14. What the repo holds today

In `docs/brand/07_production/` — supplied, 1254×1254 or larger, RGBA, usable:

| File | Covers |
|---|---|
| `symbol_gradient_01.png` | §3 launcher foreground (**D3**), §5 in-app mark |
| `symbol_gradient_02.png` | §5 alternate |
| `symbol_white.png` | §6 splash icon |
| `lockup_horizontal_dark.png`, `lockup_horizontal_light.png` | §5 |
| `lockup_stacked_dark.png` | §5 — white-type variant still needed |
| `wordmark_dark.png` | §5 — white-type variant still needed |

In `docs/brand/01_logos/` … `06_ui_mockups/` — crops from the concept board,
68–423 px, RGB with **no alpha**. **Historical reference only.** They show the
retired leaf mark (**D1**) and are too small and too opaque to ship regardless.

---

## 15. Reserve copy

Written for the brand but not shipping. Kept so it is not lost.

| Line | Origin |
|---|---|
| "Knowledge travels further when it's quiet." | board splash mockup (**D5**) |
| "Less noise. More signal. A calmer you." | board header |
| "Clean feeds. Clearer thinking. Brighter days." | board header |
| "An Android RSS reader for a brighter tomorrow" | board header |
| "A calmer, smarter way to stay informed" | board footer |
| "Android · RSS · Personal · Focused · Always yours" | board footer |
