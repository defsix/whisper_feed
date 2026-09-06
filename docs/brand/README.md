# Whisper Brand Assets

This ZIP contains individually cropped assets from the selected **first brand toolkit board** for **Whisper**.

## Included
- Primary logo and alternate logo lockups
- Symbol mark
- App icon concepts
- Favicon concepts
- Color palette crop
- Typography/reference crops
- UI mockup crops
- Original source board

## Important note
These are **cropped raster assets** taken from the approved concept board.
They are useful as:
- design reference
- implementation reference
- handoff material for Codex/design work

They are **not final vector master assets**.

For production-ready use, the final logo/icon set should be recreated cleanly as:
- SVG logo files
- adaptive Android icon assets
- monochrome icon asset
- favicon PNG/ICO exports
- notification icon
- splash screen assets

## Brand direction
- **Name:** Whisper
- **Descriptor:** RSS Reader
- **Tagline:** Your feeds, your focus.
- **Typeface reference:** Inter
- **Theme direction:** Android / Material You

## Color palette
- Cobalt — `#2563EB`
- Indigo — `#4F46E5`
- Sky — `#7DD3FC`
- Slate — `#475569`
- Soft — `#E5E7EB`

---

## How these are used in the app

- **Palette** — Cobalt `#2563EB` is the seed the app's Material 3 scheme is
  generated from (`OverlayTheme.WhisperSeed`), rather than the roles being
  hand-picked. The full palette is also in `res/values/colors.xml`.
- **Launcher icon** — `res/drawable/ic_launcher_foreground.xml` is a **vector
  reconstruction** of the symbol mark: three round-capped slanted strokes, sized
  to the adaptive-icon safe zone, with a matching monochrome layer for themed
  icons. It is drawn from these rasters, not exported from the master artwork,
  so it will not be pixel-identical. Replace it with paths derived from the real
  SVG when that exists.
- **Not yet used** — the wordmark lockups, favicons and splash/onboarding
  mockups. The splash screen and About screen are the obvious next places for
  them.
