# Screenshots the README is waiting for

The README has a gallery with these filenames in it. Until the files exist,
GitHub renders a broken-image icon for each, so the gallery is commented out —
uncomment it once the first three are here.

**These must be real screenshots from a real device.** There are UI mockups in
`docs/brand/06_ui_mockups/`, drawn before the interface existed, and they must
not be used here: a concept board presented as a screenshot is a claim about
what the app looks like that the app cannot keep.

## The shortlist, in order of usefulness

| File | What to capture |
|---|---|
| `01-feed-cards.png` | The feed in the **Cards** layout, scrolled so a hero card and two ordinary cards are visible. Light theme. This is the one that has to sell the app. |
| `02-overlay.png` | The **launcher panel** — swipe right from the Lawnchair home screen. Shows the thing no other reader does. |
| `03-feed-mosaic.png` | The **Mosaic** layout, so the gallery shows there is more than one. Dark theme, for contrast with the first. |
| `04-sources.png` | **Data sources** with a selection running, so the bulk actions are visible in the app bar. |
| `05-article.png` | An article in the **reader**. |
| `06-settings.png` | Settings, top of the list. |

## Practical notes

- **Portrait, one device, one theme per image.** A gallery of mixed devices
  looks like a gallery of different apps.
- **Use plausible real feeds** — the BBC, the Guardian, Ars Technica. A feed
  called "Test Feed 1" makes the app look unfinished.
- **Have a few articles read** so the dimming is visible, and one bookmarked.
- **Turn the glance row on** for at least the first shot; it is distinctive and
  it is off by default, so nobody discovers it from a screenshot without it.
- **No personal data.** Whatever is on screen is public forever once this repo
  is cloned. Check the place name in the glance row, and the notification
  shade.
- Trim the status bar clutter if you like, but do not fake it.

## Sizing

Straight off the device is fine — GitHub scales them. If they are enormous
(over ~1 MB each) run them through `oxipng` or `pngquant` first; a README that
takes ten seconds to load is a README nobody scrolls.
