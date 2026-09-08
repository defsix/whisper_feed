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
| 1 | Material shell | **Done** — identity, M3, dynamic colour, edge-to-edge, light/dark/black, scaffold, header, chips. Plus a shape scale, bundled Inter and a two-stage splash, none of which the milestone asked for |
| 2 | Cards layout | **Done** — cards, images, metadata, pull-to-refresh, save, read state, per-card overflow, source favicons, hide source, More/Less |
| 3 | Remaining layouts | **Done** — Cards, Magazine, List and Mosaic, chosen in Settings; Mosaic swaps the container for a staggered grid |
| 4 | Source management | **Done bar reorder** — add, autodiscovery, duplicate detection, edit, remove with undo, multi-select bulk editing, a category screen, search, sort, broken feeds surfaced, OPML in/out. Reorder deliberately deferred; see §2 |
| 5 | Personalisation | **Started** — article weighting drives the Mosaic tile sizes and is the first thing to read the More/Less scores back. Ordering, reading habits and the reset control still to build |
| 6 | Google Drive sync | **Not started** |
| 7 | Glance row | **Done** — weather, sunrise/sunset, feed status. Calendar deferred, as the spec says |
| 8 | Reader / offline / polish | **Part** — reader and offline caching work; sync, filter and frame-path performance done. Missing: accessibility pass, battery profiling, motion polish |

The card **rhythm** (hero / card / compact) is what Cards does *within* one
layout; the four layouts are what the user chooses between. Both exist now.

---

## Reported bugs

Found on device, so they take precedence over anything below when they are in
the way.

- ~~**The overflow menu opens over the status bar.**~~ Removed rather than
  repositioned. Reload is what pull-to-refresh is for and Restart was a
  development leftover, so the menu's only real entry was Settings — which is
  now a single button straight to it. No dropdown in this window means no
  dropdown to position in it.
- ~~**The in-app reader showed the feed's excerpt, not the article.**~~ Fixed:
  the readable page is fetched when the reader opens. The per-feed "fetch full
  articles" toggle still prefetches during sync; the reader no longer depends
  on it.
- ~~**Compact rows lost their save button whenever they had a thumbnail.**~~
- **Coming back from the browser lands on the home screen.** The in-app reader
  does *not* have this problem — it returns to the feed correctly — so the
  in-app mode is the answer and the browser is the path that cannot be fixed. Half of this is
  fixed — the workspace no longer shows *before* the browser — but the return
  cannot be fixed from this side, and the reason is in Lawnchair rather than
  here. A fullscreen browser stops the launcher;
  `LauncherClient.onStop` disconnects the overlay service, which reaches
  `OverlayCallbackImpl.onServiceStateChanged(false)` →
  `Launcher.setLauncherOverlay(null)` → `Workspace.setLauncherOverlay`, whose
  last line is `onOverlayScrollChanged(0)` — the workspace is snapped back to
  home. Re-attaching on resume runs the same line again. No message the
  overlay can send prevents it; pushing `overlayScrollChanged(1f)` afterwards
  would slide the panel open again, but only *after* the home screen had
  already appeared, which is worse than the problem.

  The way out is not to leave the launcher at all: render the article inside
  the overlay panel rather than starting an activity. Then nothing pauses,
  back returns to the feed, and the trip never happens. It only helps the
  in-app reading mode — a browser is someone else's activity by definition.
  ~~**and flashes the home screen on the way out**~~ Tapping an article closes the minus-one panel, shows
  the desktop for a frame, and only then opens the browser; pressing back from
  the browser returns to the home screen rather than to the feed. Both halves
  are the same cause: the overlay is a window on the launcher's token, not an
  activity with its own task, so the browser starts into the launcher's task
  and there is nothing for back to return to. Worth reading
  `OverlayView.closePanelIfNeeded` and the `pendingCloseOnResume` flag together
  with how the view intent is launched — the fix is likely to be starting the
  browser in its own task and closing the panel after, not before, it is up.

---

## Suggested order

The numbers are labels, not a schedule — they stay put so they can be referred
to. The working order set by the user is 1, 3, 4, 2, then the rest.

### 1. Adding a feed should be forgiving

The whole "add source" flow is the roughest edge left, and it is what a new
user meets first.

- **Feed autodiscovery from a site URL.** Paste `https://www.howtogeek.com/`
  and get `https://www.howtogeek.com/feed`. Two stages, and both are needed:
  first try the site's own HTML for a `<link rel="alternate">` pointing at the
  feed, which is the correct answer whenever it exists; then fall back to
  probing the handful of conventional paths — `/feed`, `/rss`, `/feed.xml`,
  `/rss.xml`, `/atom.xml`, `/index.xml`. Guessing paths first would be wrong
  often enough to matter, so it is the fallback, not the opener.

  `docs/REFERENCES.md` §1 has the research: Twine's `FeedFetcher` for the
  HTML stage, and its `FeedUrlResolver` for the sites where discovery cannot
  work at all — Reddit serves a script shell to non-browsers, Mastodon handles
  are not URLs, and YouTube needs the channel id rather than the page.

- **Duplicate detection while adding.** Adding a feed already in the list should
  say so rather than silently creating a second copy. Match on the resolved feed
  URL after redirects, not the string typed — `example.com/feed` and
  `https://www.example.com/feed/` are the same subscription. An OPML import
  should merge against the same rule instead of doubling everything.

- **Undo remove** — deleting a source with dozens of articles is currently
  irreversible and instant.

### 2. Source management worth the name

Everything above is about one feed at a time. Once someone imports an OPML with
a hundred sources, one at a time is the wrong unit.

- **Multi-select in the source list**, with mass actions: add a tag, remove a
  tag, replace tags, enable, disable, delete.
- **Category management as its own screen** — rename a category everywhere it is
  used, merge two, delete one and choose what happens to its feeds. Categories
  are currently a free-text field on each source, so a typo creates a category
  and nothing can rename it.
- ~~**Sort and search the source list**~~ — done: by name, category, least
  recently updated, and recently added.
- **Surface broken feeds.** A source that has failed to fetch for days looks
  identical to one that is simply quiet. `lastSync` is already stored; nothing
  reads it back to the user.
- **Reorder — deferred, with a reason.** A persisted manual order fights the
  sort selector built alongside it: with Name, Category and Least-recently-
  updated on offer, what a hand-made order *means* while sorted by name has no
  good answer, and the usual fix — making manual a fourth sort option — leaves
  a drag handle that silently does nothing in the other three. It also wants a
  column and a migration. Worth deciding as a design question before it is
  built, rather than bolting it on.
- **Tags should be picked, not typed.** The source editor's Tags field is free
  text, so a category is created by spelling it right and lost by spelling it
  wrong — and the field reads as one value even though the column has always
  held a comma-separated list. Every tag in use should offer itself as a chip
  to select, with typing reserved for making a new one, and several selectable
  on one feed. Note this is a UI change and a tag registry, not a schema change:
  `Feeds.tag` already stores a list and `Feed.tags` already reads it.

That set is Milestone 4's "complete source management", read literally.

### 3. Finish the card

- **Per-card overflow menu** — the four entries the mockups show, in order:
  More like this, Less like this, Hide source, Share. The first two are the
  visible half of Milestone 5 and should be built to record a signal even
  before anything reads it back, so the menu is not a lie in the meantime.
  Hide source and Share work on their own from day one. Every article currently
  offers only save and share.
- **One symbol for saving.** A heart on the card saves an article and a
  bookmark ribbon in the header shows the saved ones — two glyphs for one idea,
  neither of which is ours. Both become a single mark derived from the app
  symbol: it is three blades, so a three-stroke form is already in the
  artwork. Vector rather than a bitmap, so it tints with the theme and carries
  a filled state for saved against an outline for not.

  Decided against, so it does not come back: a **Saved chip in the category
  row**. That row is topics — All, Misc, News, Tech — and Saved is a state, so
  the row would answer two questions at once and raise one it cannot (is Saved
  additive with News, or exclusive?). One route in, through the header toggle,
  which already shows when it is on.
- **Source favicon on the card** — the meta row names the source in text only.
  Discover puts the site's mark beside it, and it is what makes a source
  recognisable at a glance in a mixed feed.

  Where the image comes from, cheapest first: the feed's own `<image>` or
  `<icon>` element (many carry one and it is already parsed past), then
  `/favicon.ico`, then the `<link rel="icon">` set in the site's HTML head.
  Resolve **once per source, not per article** — store the URL on `Feed` and
  the bytes in the existing blob directory, refreshed only when a feed is
  edited or fails to load. Falling back to a tinted monogram of the source's
  first letter keeps the row from collapsing when a site has nothing usable,
  which is common enough on small blogs to be the default case rather than an
  edge one.

  Note the privacy cost and keep it honest: fetching a favicon is a request to
  the site's own server, no third-party favicon service. Google's
  `s2/favicons` endpoint is the easy path and it tells Google every source the
  user reads — it should not be used.

### 4. Layouts (Milestone 3)

Magazine, List, Adaptive Mosaic, over the same domain model. The rhythm work
already split the card into three shapes with a shared item API, so this is less
of a jump than it was.

### 5. Personalisation (Milestone 5)

More/Less like this, source affinity, hide source and topic, reset,
chronological and smart ordering. `readAt` exists now, which is the first piece
of article state; the rest of the signal model is still to design.

**First consumer shipped: article weighting in Mosaic.** `FeedWeight.kt` scores
each article — image, freshness, source affinity, headline length, whether a
summary exists, read state, saved, pinned — and the score picks the tile size.
It is the first thing that reads the affinity scores back rather than only
writing them, and it is deliberately a display decision rather than a filter:
a low weight makes an article small, never absent, so a bad score cannot hide
anything. The constants are in one object and want tuning against a real feed.

Still open here: the same weight should drive the Cards rhythm (currently
positional), a visible "why is this big" affordance, and the reset control.

**Reading habits as a weight term — and it does not need accounts.** The plan
was to gate this on sign-in and count usage server-side. It does not have to
wait: `readAt` is already on every article, so per-source read counts, opens
per week and time-since-last-read can all be derived on device today, from data
that is already there. What an account adds is *carrying those counts to
another phone* — which is §7's job, not a prerequisite for the feature. Build
it locally, sync it later.

The term itself is a small one on purpose. A source read often gets a nudge, not
a promotion: enough to break a tie between two similar articles, never enough
to outrank a fresh story from somewhere else.

**The diversity constraint is not optional, and it is the hard half.** Left
alone, "favour what they read" converges on one site: it gets shown more, so it
is read more, so it is weighted higher. The counter has to be structural rather
than a smaller coefficient, because any positive coefficient runs away
eventually. Two rules, both cheap:

- **No source may hold more than one of the top slots.** Whatever the weights
  say, the large tiles and the hero slot go to distinct sources.
- **A cap on consecutive items from one source** in the ordered list, with the
  overflow displaced rather than dropped — it moves down, it never disappears.

Both are display rules, which keeps the property the weighting already has: a
low score changes how big something is and where it sits, never whether it is
there at all. Nothing in Whisper should be able to hide an article the user
subscribed to.

Worth restating: `docs/REFERENCES.md` §4 found **no open-source prior art** for
transparent, resettable preference learning in a feed reader. This is
build-it-ourselves rather than assembly, and should be budgeted that way.

### 6. Breaking news, sticky and pinned

Three related asks, in increasing order of difficulty.

**Pinning is the easy one and should be built first.** A user pins an article
they are following; it holds the top of the feed until unpinned. It needs one
boolean on `Article`, an entry in the per-card overflow menu (§3), and a rule
that pinned items sort above everything regardless of the active sort. No
inference, no clustering, nothing to get wrong. It also happens to be the
manual escape hatch for whenever the automatic detection below gets it wrong,
so it is worth having in place before the automatic version ships.

**Breaking news detection — clustering.** The signal is the right one: when
five sources publish about the same thing inside an hour, that is a story, and
a single-source post is not. The hard part is "the same thing". Options, and
the honest cost of each:

- *Title similarity* — normalise, strip the source suffix, then compare on
  token overlap or trigram Jaccard. Runs locally, no network, no model, a few
  milliseconds over a few hundred articles. Misses stories that are worded
  differently and joins ones that merely share a proper noun. Cheap enough to
  try first and see how it reads on a real feed.
- *Shared outbound links* — articles about one event tend to cite the same
  source document. Precise when it fires, silent when it does not.
- *Embeddings* — accurate, and a model in the APK plus per-article inference
  on a phone, for a feed of a few hundred items. Not for a first pass.

Whichever is used, the cluster is what gets promoted, not the article: one
representative gets the hero slot, and the others become a "N sources" line
under it. That also answers a question the current rhythm cannot — which of
five near-identical articles to show.

**This is not a second mechanism beside the weighting in §5 — it is the
weighting's largest term.** Both answer the same question, "how much of the
screen has this earned", and building them as two systems would mean two
things competing for the hero slot with no rule between them. A cluster of
distinct sources publishing inside an hour is simply worth a lot of weight,
which is what makes it beat a merely-fresh article for the same slot. It also
inherits the diversity rules for free.

Scope the burst to where it means something: a spike across five news sources
is a story, the same spike across five review sites is a product launch and a
release-day rush from one topic is neither. Restrict it to sources the user has
categorised as news, at least for the first pass. The categories already exist
and are already the user's own judgement, so the app is reading a decision the
user made rather than guessing at one.

Guard against the obvious failure: one prolific feed posting six times about
its own topic is not breaking news. Require the cluster to span **distinct
sources**, and require recency, or a chatty feed will hold the hero slot all
day.

**Sticky until scrolled past.** A user setting, off by default. The promoted
cluster holds the top of the viewport until the user scrolls past it, then
releases and behaves like any other card. In Compose this is a sticky header
in the `LazyColumn` rather than a separate overlay, so it costs little — but
it interacts with the rhythm in §4 and with pinning above, and those three
need one ordering rule between them, not three competing ones. Decide that
rule when the layouts land.

Settings this adds: highlight breaking news (on/off), keep it at the top until
scrolled past (on/off). Both belong with the personalisation switches in §5,
not in a category of their own.

### 7. Sync and backup (Milestone 6)

What exists today: OPML import and export, bookmark import and export, both
manual, both through the file picker. That is a working backup story, just not
an automatic one.

What is missing: any account, any cloud, any cross-device state.

**Decided: Google Reader protocol.** Recorded in `docs/REFERENCES.md` §2 —
Feedly's own API turned out to be enterprise-gated, but the Google Reader
protocol is spoken by FreshRSS, Inoreader, Miniflux and BazQux, so one
implementation buys several services and needs no Google account and no Play
Services. ReadYou has a complete client (`GoogleReaderAPI.kt`, around 560 lines
plus DTOs) to work from, under a compatible licence.

The shape to copy with it is ReadYou's `AbstractRssRepository`: one base class
where only `sync()` is abstract and everything else has a working *local*
implementation that remote providers override selectively. That matches the
local-first rule exactly — the app stays fully functional with no account, and
sync is genuinely additive rather than a mode.

Google Drive app-data sync is **not** being pursued. It syncs your own devices
rather than your reading, and it would put a Play Services dependency into an
app that has avoided one everywhere else.

### 8. Ship it

- **The Lawnchair whitelist PR** — see below. This is the single highest-value
  item for anyone other than us using the app.
- Release signing key, then GitHub Releases → F-Droid → Play, per the staged
  plan in `docs/brand/ASSET_SPEC.md` §8.

### 9. Search, and a filter that explains itself

- ~~**Search the feed** from the header.~~ Done, in both surfaces. Over the
  headline, the source and the summary — all already in the database, so it
  needs no network, no account and no index. The article body is deliberately
  not covered: it lives in a file per article, so searching it would mean
  reading every one off disk on each keystroke, or building an index. Worth
  revisiting if the shorter fields prove too thin.
- **The filter is confusing as it stands.** Half of this is done: the overlay
  showed a View-based XML sheet while the app showed a Compose one, and the
  two now share the app's. What is left is the sheet's own design — what it is
  filtering *by* should be visible without opening it, and it should read as
  narrowing the feed rather than configuring it.

### 10. Glance and header, once the rest is in

Small, and none of it blocks anything.

- ~~**Chance of precipitation on the weather chip.**~~ Done. The note here
  claimed the Open-Meteo call already returned it; it did not — the request
  asked only for temperature and weather code, and now asks for
  precipitation_probability_max as well. Shown beside the temperature with the
  supplied rain artwork at 16dp, and only above 20%, so a dry day's chip is
  unchanged.
- **Account avatar, top right.** Discover puts the signed-in user there and it
  is where a hand goes looking. It waits on §7: there is no account to show
  until the Google Reader client exists, and a silhouette that opens nothing
  would be worse than the space it fills.

---

### 11. Finding sources the user does not have

Discover's most-liked trick is putting a site in front of you that you never
subscribed to. The assumption was that a pure RSS reader cannot do this,
because there is no recommendation service behind it. That is half right: what
it cannot do is *rank the whole web*. What it can do is notice things it is
already holding.

Three mechanisms, all local, none needing an account, a server, or a model.
Listed cheapest first — the first is the strong one and the other two are
garnish.

**1. Outbound links in articles that were actually read.** Every article
already fetched carries links, and the reader's own `readAt` says which
articles were read rather than merely delivered. Count the outbound domains
across read articles over a few weeks, discard ones already subscribed, and
for a domain that recurs, do the autodiscovery pass §1 already implements
against its home page. If it publishes a feed, that is a suggestion with a
reason attached: *"You have followed six links to The Verge this month."*
No inference about taste, no profile, no third party. The reason is legible
and always true, and if the user disagrees they can see exactly what produced
it — which is the thing §5's `docs/REFERENCES.md` note says nobody else does.

**2. Feeds the sources themselves point at.** Blogrolls, `<link
rel="related">`, OPML files published on a site's own links page. A small
number of sites still do this and it costs one fetch of a page already being
fetched. Low yield, near-zero cost.

**3. Curated OPML collections.** Public topic lists that a user opts into and
imports. Honest, but it is a directory rather than a discovery, and it puts
whoever curates the list in charge of what gets seen. Worth it only if the
list is chosen explicitly by the user, never fetched in the background.

What is deliberately *not* here: sending the subscription list anywhere to be
matched against other users' lists. That is the mechanism that makes Discover
work and it is the one thing this reader should not do — the whole premise is
that nobody else learns what is read.

Constraints when this is built: a suggestion is a suggestion. It appears in one
place the user can go to and dismiss from, never injected into the feed itself
as an unsubscribed article — the moment articles from sites the user did not
choose start appearing in the feed, the reader stops being theirs. And every
suggestion states its evidence.

This sits after §7 in any sensible order: it depends on read history being
worth something, which depends on the reader having been used for a while.

---

### 12. A desktop reader — last, and only as an app

Bottom of the pile deliberately. Recorded so the reasoning is not re-derived.

**Not as a website.** A browser cannot fetch a third-party feed: the
same-origin policy stops it and news sites do not serve CORS headers to
strangers. Importing an OPML file is fine — that is a local file — but the
forty feeds it names are all blocked, so a web version needs a server
proxying every fetch. That server then sees every feed every user subscribes
to and every article they open, which is the one thing this reader exists not
to do. It is also a permanent hosting bill and a liability the Android app
does not have.

This is why Feedly makes it look easy: **Feedly is the server.** It fetches
every feed centrally, for everyone, and the browser only ever talks to
Feedly. That is a different product with a different bargain, not a smaller
version of this one.

**As a desktop app, yes.** Checked against the actual dependency set rather
than assumed:

- Room 2.8.4 publishes a `standard-jvm` variant, so the database and every
  DAO port unchanged.
- OkHttp, Rome, Readability4J, Jsoup, TagSoup and Moshi are plain JVM.
- DataStore is multiplatform.

The work is three things: `androidx.compose` → Compose Multiplatform, which
brings the theme, shape scale, Inter and the whole card set across because the
Material 3 API is near-identical; Coil 2 → 3 for images; and replacing
WorkManager with a coroutine scheduler, which is simpler on desktop than on
Android. What does not come across: the launcher overlay, and dynamic colour,
since Material You is an Android API — desktop would use the static palette.

**What OPML does not carry.** It is the source list and its categories. No
read state, no bookmarks, no article state of any kind. A desktop reader seeded
from OPML opens with everything unread, including the hundreds already dealt
with on the phone, and the two drift apart from that moment. That makes it *a
separate reader that happens to have the same feeds*, not the same reader on a
bigger screen. Continuity needs §7; once §7 exists a desktop client inherits it.

**Order:** after §7, after v1 ships. The styling it would reuse is the part
still changing weekly, and adding a second platform while testers are being
recruited works against the testing. The cheap de-risking step, if it is
wanted earlier, is splitting `:core` from `:app` as a pure refactor with no
desktop module — nothing user-visible, but the seam exists while the code is
fresh.

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

### Shipping our own launcher — decided against

The only way to own the experience end to end would be to fork Lawnchair, wire
the feed in directly and distribute one app. That removes the whitelist, the
debug toggle and the "install Lawnchair first" step together.

It also turns a feed reader into a launcher project — home screen, app drawer,
widget hosting, gestures, backup, and every device quirk that comes with being
what runs when someone presses Home. **Off the table**, decided rather than
merely unattempted.

Which leaves Lawnchair's whitelist as the one route worth spending effort on,
and makes the standalone app the primary product with the minus-one surface as
a bonus for the people running a launcher that supports it.

---

## Debt worth clearing

Small, and cheaper now than later.

- **Nothing here has been verified on a device by me.** Everything is reasoned
  from the code and measured where it could be measured — text widths against
  the real font, icon alpha, migration SQL. The on-device checks have all been
  yours. Emulator-based screenshot tests would change that.
- **Test coverage is 14 unit tests**, on article age and tag splitting. The sync
  and filter performance work, the theme resolution and the day/night rule are
  all untested and all have the shape that benefits most from tests.
- ~~**Dead code**~~ — cleared. Nine unreferenced files and eight drawables
  removed, along with eight unused DAO methods, one of which had an
  `@Relation` without `@Transaction`: the same shape as the OPML crash fixed
  earlier, waiting to be called.
- ~~**Phosphor was unlicensed**~~ — the icon set was hand-transcribed into
  ImageVector sources with no licence recorded anywhere. `docs/licenses/
  Phosphor-MIT.txt` now carries it; new icons are parsed from the upstream
  SVG rather than retyped.
- **Assets still open**: onboarding background and article placeholders, listed
  in `docs/brand/ASSET_SPEC.md`. The horizontal lockup is no longer needed —
  the app bar composes the symbol and the wordmark itself, which keeps the two
  independently sizeable. Still missing if the cobalt splash is ever wanted
  back: a light colourway of the symbol, since the vivid gradient loses two of
  its three blades against `#2563EB`.
