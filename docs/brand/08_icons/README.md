# Glance chip icons

The icon set for the status chips above the feed. **Complete and in use.**

`docs/brand/08_icons/*.png` are the masters, 1254×1254 RGBA. The build exports
them to `app/src/main/res/drawable-*/ic_glance_*.png` at five densities.

| File | Shows when |
|---|---|
| `weather_clear.png` | clear sky |
| `weather_partly_cloudy.png` | partly cloudy, mainly clear |
| `weather_cloudy.png` | overcast |
| `weather_fog.png` | fog, rime fog |
| `weather_rain.png` | drizzle, rain, showers |
| `weather_snow.png` | snow, snow showers |
| `weather_storm.png` | thunderstorm |
| `weather_unknown.png` | any unrecognised condition code |
| `sunset.png` | always — sunset chip |
| `articles_read.png` | always — read-today chip |
| `location.png` | before a location has been chosen |
| `weather_clear_night.png` | clear sky, after dark |
| `weather_partly_cloudy_night.png` | partly cloudy, after dark |
| `weather_rain_night.png` | drizzle, rain, showers, after dark |
| `sunrise.png` | the second chip after dark, when it shows the next sunrise |
| `sunset_waves_alt.png` | **not in use** — alternate sunset, see below |

All sixteen were checked before use: RGBA with real alpha, transparent
backgrounds, 68–85% clear, and no white fringe on the antialiased edges (mean
edge colour is the artwork's own, not near-white). This is what the first batch
was missing.

## How they are exported

Rendered at **32 dp** on the chip, so 32/48/64/96/128 px across mdpi to
xxxhdpi.

Every icon is cropped to **one shared box** — the union of all eleven content
bounds, squared off, which works out at 81% of the master canvas. Cropping each
icon to its own bounds instead would rescale them against each other: the
thermometer is 430 px wide against the cloud's 970 px, and per-icon cropping
would blow it up to the same width. The shared box removes only the margin that
is empty in every file, so the relative sizing stays as drawn.

## Why sunset, not sunset-waves

Two sunsets were supplied. `sunset.png` — yellow sun over blue horizon bars —
is the one that ships: the yellow reads unmistakably as a sunset at 32 dp and
sets the chip apart from the all-blue weather icons beside it.
`sunset_waves_alt.png` is closer to the brand mark but is blue-on-blue at that
size and can read as water rather than a sunset. Kept in case that call should
go the other way.

## Rendering

Drawn as artwork, not tinted. The tinted circular badge that held the earlier
monochrome symbols is gone — it existed to give a single-colour glyph presence
and a theme colour, and both would fight a full-colour illustration.

## Day and night

Only the three conditions whose day artwork contains a sun have a night
counterpart. Overcast, fog, snow and storm are drawn without one, so a separate
night version would be the same picture.

Whether it is day is derived at read time from the next sunrise and the next
sunset — daylight if the sunset comes first — rather than from the service's
`is_day` flag. The response is cached for half an hour, and a cached flag would
be wrong for up to that long either side of dusk. Comparing the two upcoming
events also settles it without needing the date, so it is right at 03:00 as
well as at 21:00.

The second chip follows the same rule: after dark, today's sunset is behind us
and repeating it is stale, so the chip becomes the next sunrise — artwork and
label together.
