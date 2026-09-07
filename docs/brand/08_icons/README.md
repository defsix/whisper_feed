# Glance chip icons

Supplied artwork for the status chips above the feed.

## Not yet in use — these need alpha

All three files are **8-bit RGB with no alpha channel** and a solid white
background, measured rather than assumed:

| File | Canvas | Content bounds | Alpha |
|---|---|---|---|
| `sunset.png` | 1254×1254 | 518×476, inset ~29% | none — RGB |
| `weather_partly_cloudy.png` | 1254×1254 | 648×502, inset ~24% | none — RGB |
| `articles_read.png` | 1254×1254 | 553×439, inset ~28% | none — RGB |

A chip icon sits on the chip's own surface colour, which changes with the theme.
Without transparency each of these would draw as a white square — obvious in
light mode, glaring in dark mode.

Keying the white out here is not safe to do mechanically: the artwork's own
pale fills are close to the background (`articles_read.png` in particular is
mostly very light blue-grey on white), so a threshold either eats the artwork or
leaves a white fringe on every antialiased edge. That needs to come from the
source, not from a guess.

**What would make them usable:** the same three as PNG with a real alpha
channel, trimmed to the artwork, or as SVG.

## Coverage

These cover two of the eight weather states the feed can report. Still needed
for a complete set:

`clear` · `cloudy` · `fog` · `drizzle` · `rain` · `snow` · `showers` · `storm`

Plus `location_on`, for the chip shown before a location has been chosen.

## What ships in the meantime

`res/drawable/ic_ms_*.xml` — Material Symbols Rounded, from
google/material-design-icons, Apache 2.0 (see
`docs/licenses/MaterialSymbols-Apache-2.0.txt`). Monochrome, so they take the
theme's colour and work in both light and dark. They are placeholders for these
files, not a decision against them.
