# Whisper — roadmap

Status against the milestones in the handoff, what is left, and the order it
should be done in.

Last reviewed against the tree, not from memory: every "done" below was checked
in the code.

---

## Where the milestones stand

| # | Milestone | Status |
|---|---|---|
| 0 | Launcher feasibility | **Done** — builds, installs, minus-one works, mechanism documented in `UPSTREAM_NOTES.md` |
| 1 | Material shell | **Done** — identity, M3, dynamic colour, edge-to-edge, light/dark/black, scaffold, header, chips. Plus a shape scale and bundled Inter, which the milestone did not ask for |
| 2 | Cards layout | **Most of it** — cards, images, metadata, pull-to-refresh, save, read state. Missing: per-card overflow menu, hide, More/Less controls |
| 3 | Remaining layouts | **Not started** — Magazine, List, Adaptive Mosaic |
| 4 | Source management | **Half** — add, edit, remove, categories, OPML in/out. Missing: autodiscovery, undo remove, category management, reorder |
| 5 | Personalisation | **Not started** |
| 6 | Google Drive sync | **Not started** |
| 7 | Glance row | **Done** — weather, sunrise/sunset, feed status. Calendar deferred, as the spec says |
| 8 | Reader / offline / polish | **Part** — reader and offline caching work, sync and filter performance done. Missing: accessibility pass, battery profiling, motion polish |

One thing to be honest about: the card **rhythm** (hero / card / compact) is not
the same as Milestone 3. The rhythm varies weight *within* one layout; Milestone
3 is four layouts the user chooses between. M3 is genuinely untouched.

---

## Suggested order

### 1. Finish what is half-built

Ordered by how visible the gap is.

- **Per-card overflow menu** — hide source, hide topic, not interested, share.
  Milestone 2 asked for it; the concept board shows it; every article currently
  offers only save and share. It is also the surface Milestone 5 hangs off.
- **The multi-tag filter bug.** `getFeedItemsByTagsSimple` matches with
  `Feeds.tag IN (:tags)`, but tags are stored comma-separated — so a feed tagged
  `Tech,News` does not match a `Tech` chip. Single-tag feeds work, which is why
  it looks fine. Known, reported, not yet fixed.
- **Feed autodiscovery** — paste a site URL rather than a feed URL. The approach
  is already researched in `docs/REFERENCES.md` §1; Twine's two-stage
  resolve-then-discover, including the special cases for Reddit, YouTube and
  Mastodon handles.
- **Undo remove**, category management, source reorder — the rest of Milestone 4.

### 2. Layouts (Milestone 3)

Magazine, List, Adaptive Mosaic, over the same domain model. The rhythm work
already split the card into three shapes with a shared item API, so this is less
of a jump than it was.

### 3. Personalisation (Milestone 5)

More/Less like this, source affinity, hide source and topic, reset,
chronological and smart ordering. `readAt` exists now, which is the first piece
of article state; the rest of the signal model is still to design.

Worth restating: `docs/REFERENCES.md` §4 found **no open-source prior art** for
transparent, resettable preference learning in a feed reader. This is
build-it-ourselves rather than assembly, and should be budgeted that way.

### 4. Sync and backup (Milestone 6)

What exists today: OPML import and export, bookmark import and export, both
manual, both through the file picker. That is a working backup story, just not
an automatic one.

What is missing: any account, any cloud, any cross-device state.

Two candidates, and they are not alternatives:

- **Google Drive app-data sync**, as the handoff specifies. Private per-app
  folder, no scopes over the user's own files, sources and article state and
  preferences replicated. Needs Play Services, which is a dependency the project
  has avoided so far — worth a decision before it is written.
- **Google Reader protocol sync**, the correction recorded in
  `docs/REFERENCES.md` §2. Feedly's API turned out to be enterprise-gated, but
  the Google Reader protocol is spoken by FreshRSS, Inoreader, Miniflux and
  BazQux — one implementation, several services, and no Google account. ReadYou
  has a complete client to work from.

Drive syncs *your own devices*. Google Reader syncs *your reading with a
service*. A local-first reader probably wants both eventually; Reader protocol
is the more useful first because it needs no proprietary dependency.

### 5. Ship it

- **The Lawnchair whitelist PR** — see below. This is the single highest-value
  item for anyone other than us using the app.
- Release signing key, then GitHub Releases → F-Droid → Play, per the staged
  plan in `docs/brand/ASSET_SPEC.md` §8.

---

## Replacing Discover: what is actually possible

The short version: **the minus-one page belongs to the launcher, not to
Android.** No app can claim it. What an app can do is offer itself as a feed
*provider* to a launcher that supports choosing one.

| Launcher | Can Whisper be its feed? |
|---|---|
| **Pixel Launcher** | **No, and this will not change.** It is hardcoded to Google's app. There is no public API, no intent, no setting. Anything claiming otherwise is describing a different launcher. |
| **Lawnchair** | **Yes** — today via the debug toggle, and without it if we get whitelisted. See below. |
| **Lawnchair forks** (Neo Launcher, Librechair) | Likely, since they inherit the same `FeedBridge`. Unverified — nobody has tested it. |
| **Everything else** | Mostly no. Most launchers have no minus-one page at all, and those that do generally hardwire it. |

### Getting rid of the debug step

From `FeedBridge.kt`, recorded in `UPSTREAM_NOTES.md` §3: Lawnchair filters
providers through a hardcoded map of package name to signing-certificate hash. A
package that is not in it is rejected unless `pref_ignoreFeedWhitelist` is on.

So there is exactly one legitimate route to "install it and it works":

> **A pull request to Lawnchair** adding `io.zero76.whisper` and our release
> signing certificate hash to that map.

That needs a stable release signing key first — which we do not have yet, and
which is a prerequisite for every distribution channel anyway. It also needs
Lawnchair's maintainers to accept it, and then a Lawnchair release, and then
users on that release. Not instant, but it is a normal open-source contribution
and the whitelist already contains five third-party apps, so there is precedent.

### One shortcut that exists and should not be taken

Neo Feed's whitelist entry is not a fixed hash — it is computed at runtime from
whatever is installed under `com.saulhdev.neofeed`. Any app using that package
name is accepted, whoever signed it.

Shipping under Neo Feed's package ID would therefore work immediately, with no
PR and no debug toggle. It would also collide with every real Neo Feed install,
break their updates, and pass this app off as someone else's. It is worth
knowing the hole is there, and worth not climbing through it.

### If the launcher dependency is unacceptable

The only way to own the experience end to end is to **ship a launcher** — fork
Lawnchair, wire the feed in directly, distribute one app. That removes the
whitelist, the debug toggle and the "install Lawnchair first" step in one go.

It also turns a feed reader into a launcher project, with a home screen, an app
drawer, widget hosting, gestures, backup and every device quirk that comes with
being the thing that runs when someone presses Home. That is a different and
much larger product. Naming it as an option, not recommending it.

---

## Debt worth clearing

Small, and cheaper now than later.

- **Nothing here has been verified on a device by me.** Everything is reasoned
  from the code and measured where it could be measured — text widths against
  the real font, icon alpha, migration SQL. The on-device checks have all been
  yours. Emulator-based screenshot tests would change that.
- **Test coverage is 8 unit tests**, all on article age. The sync and filter
  performance work, the theme resolution and the day/night rule are all
  untested and all have the shape that benefits most from tests.
- **Dead code**: `NavigationSuite.kt` and `Pager.kt` went unreferenced when the
  bottom navigation was removed. `ArticleItem` and `BookmarkItem` predate the
  shared card.
- **Assets still open**: onboarding background, article placeholders, a
  tagline-free horizontal lockup for the app bar. Listed in
  `docs/brand/ASSET_SPEC.md`.
