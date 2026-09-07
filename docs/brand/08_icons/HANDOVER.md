# Glance chip icons — handover

Everything needed to produce the icon set for Whisper's glance row.

The glance row is the strip of three status chips directly above the category
filters, on both the app's feed and the launcher minus-one surface. Each chip
carries one icon on its right-hand side.

---

## 1. Delivery spec

Applies to every icon in the list.

| | |
|---|---|
| **Format** | **SVG preferred.** Otherwise PNG-24 with a real alpha channel. |
| **Size (PNG)** | **512 × 512 px**, square canvas |
| **Alpha** | **Required.** Transparent background, right up to the artwork edge. No white or coloured backing. |
| **Trim** | Artwork centred and **trimmed to its own bounds** — no built-in padding. The app applies its own inset. |
| **Colour** | Full colour, one version. No separate light/dark files. |
| **Colour space** | sRGB |

### Why alpha is non-negotiable

The icon sits directly on the chip's surface, and that surface colour changes
with the theme — light grey in light mode, near-black in dark mode. Any icon
with a baked-in background draws as a visible square. This is the single reason
the three already supplied are not in use.

### Why 512 px

The icon renders at roughly 28 dp, which is 112 px on an xxxhdpi screen. 512 px
gives headroom for larger surfaces later without a re-export, and downscales
cleanly. Anything above 512 is wasted file size. An SVG makes the question moot.

### Palette and style

Match the three already supplied — soft gradients, fully rounded terminals, no
outlines, no drop shadows. The brand palette is the reference:

| Name | Hex |
|---|---|
| Cobalt | `#2563EB` |
| Indigo | `#4F46E5` |
| Sky | `#7DD3FC` |
| Slate | `#475569` |
| Soft | `#E5E7EB` |

Keep the pale end of the palette above roughly 25% luminance — anything paler
starts to disappear against a light chip surface.

### Optical sizing

These are read at about 28 dp on a phone. Detail that survives at 512 px can
disappear entirely at that size. Weight the strokes so each icon stays
identifiable in a 28 dp square, and keep the number of distinct elements low —
the supplied "partly cloudy" is about the right level of detail; the supplied
"articles read" is at the busy end and may need simplifying.

---

## 2. The set — 11 icons

### 2a. Weather (8)

These fill the first chip. Exactly one shows at a time, chosen from the
condition code the weather service returns. Every branch below is reachable, so
all eight are needed for the chip never to fall back.

| # | Filename | Depicts | Shows when the condition is | Status |
|---|---|---|---|---|
| 1 | `weather_clear.png` | Full sun, rays all round, no cloud | Clear sky | **needed** |
| 2 | `weather_partly_cloudy.png` | Sun behind a cloud, sun upper-left | Partly cloudy, mainly clear, overcast-with-breaks | ✅ supplied — needs alpha |
| 3 | `weather_cloudy.png` | Cloud alone, no sun | Overcast | **needed** |
| 4 | `weather_fog.png` | Cloud with horizontal bars beneath it, suggesting haze | Fog, depositing rime fog | **needed** |
| 5 | `weather_rain.png` | Cloud with falling drops | Drizzle, rain, and rain showers — one icon covers all three | **needed** |
| 6 | `weather_snow.png` | Cloud with falling flakes | Snow and snow showers — one icon covers both | **needed** |
| 7 | `weather_storm.png` | Cloud with a lightning bolt | Thunderstorm, with or without hail | **needed** |
| 8 | `weather_unknown.png` | Thermometer | Fallback for any condition code not recognised. Rare, but it is a real branch and will show if the service adds a code | **needed** |

### 2b. Chip icons (3)

| # | Filename | Depicts | Shows when | Status |
|---|---|---|---|---|
| 9 | `sunset.png` | Sun half-set behind a horizon line | Always — second chip, next to today's sunset time | ✅ supplied — needs alpha |
| 10 | `articles_read.png` | Article or newspaper with a tick | Always — third chip, next to the count of articles read today | ✅ supplied — needs alpha |
| 11 | `location.png` | Map pin | First chip, **before a location has been set**. Replaces the weather and sunset chips and is tappable to open the location picker | **needed** |

**Total: 7 new files, plus alpha-channel versions of the 3 already supplied.**

---

## 3. Optional — only if wanted

Neither is needed for the set to be complete.

| Filename | Depicts | Why |
|---|---|---|
| `weather_clear_night.png` | Crescent moon and stars | A clear night currently shows a sun. The weather service returns a day/night flag, so a moon variant is cheap to wire up. |
| `weather_partly_cloudy_night.png` | Moon behind a cloud | Same reason. |
| `weather_drizzle.png`, `weather_showers.png` | Lighter and heavier rain | Icon 5 currently covers drizzle, rain and showers. The chip's text label already distinguishes them, so this is polish. |
| `weather_snow_showers.png` | Intermittent snow | Same, against icon 6. |

---

## 4. What is wrong with the three already supplied

Measured, not assumed:

| File | Canvas | Artwork bounds | Alpha |
|---|---|---|---|
| `sunset.png` | 1254 × 1254 | 518 × 476, inset ~29% | **none — RGB** |
| `weather_partly_cloudy.png` | 1254 × 1254 | 648 × 502, inset ~24% | **none — RGB** |
| `articles_read.png` | 1254 × 1254 | 553 × 439, inset ~28% | **none — RGB** |

All three are 8-bit RGB on a solid white background. Re-export with alpha and
they can go straight in.

Removing the white here mechanically is not safe: the artwork's own pale fills
sit close to white — `articles_read.png` is largely very light blue-grey on
white — so any threshold either eats parts of the artwork or leaves a white
fringe along every antialiased edge. It has to come from the source file.

---

## 5. Naming and where they go

Lower case, underscores, exactly the filenames in the tables above. Drop them in
`docs/brand/08_icons/`; the build pulls from there.

---

## 6. What ships until then

`app/src/main/res/drawable/ic_ms_*.xml` — Material Symbols Rounded, from
google/material-design-icons, Apache 2.0, attributed in
`docs/licenses/MaterialSymbols-Apache-2.0.txt`. Monochrome, so they take the
theme's colour and work in both light and dark.

They are placeholders for this set, not a decision against it.

One consequence when the colour icons land: the icon currently sits inside a
tinted circular badge, which suits a monochrome symbol but will fight a
full-colour illustration. The badge comes off at that point and the icon sits
directly on the chip — closer to the original concept mockups anyway.
