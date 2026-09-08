# Whisper — roadmap

Status against the milestones in the handoff, what is left, and the order it
should be done in.

Last reviewed against the tree, not from memory: every "done" below was checked
in the code.

---

## What this is now

Worth stating plainly, because the answer has changed and several decisions
below were made against the old one.

Whisper started as a replacement for Google Discover on Lawnchair's minus-one
page. That was the brief, and it is why the launcher work came first. **It is
no longer what the app is.** Four layouts, weighted ordering, breaking-news
clustering, read state, search, source management, categories, OPML both ways,
a reader with offline caching — that is a full RSS reader, and the launcher
page is now one surface it renders on rather than its reason for existing.

Five things follow, and they are not cosmetic:

- **The Lawnchair debug step stops being a blocker.** It was recorded as
  blocking real-world install, on the reasoning that a Discover replacement
  which cannot reach the minus-one page is useless. A reader whose launcher
  page is a bonus is perfectly usable without it. Getting whitelisted is still
  worth doing; it is no longer the gate.
- **Onboarding inverts.** The brief in `docs/ONBOARDING_PROMPT.md` argued the
  Lawnchair pane was the one to build even if the rest slipped. That was right
  under the old framing and wrong under this one: most people installing this
  will not have Lawnchair, and a first run that leads with a setup step for a
  launcher they do not use tells them they have the wrong app. One dismissible
  pane, offered late.
- **The audience is much larger, and so is the field.** Not "Lawnchair users
  who miss Discover" but anyone who wants an RSS reader — against Feedly,
  Inoreader, FreshRSS, Feeder and Read You. Being good is no longer enough on
  its own; being *distinct* matters.
- **What makes it distinct is the weighting, not the launcher.**
  `docs/REFERENCES.md` §4 found no open-source prior art for transparent,
  resettable preference learning in a feed reader, and §5 now ships it: an
  ordering that explains itself per article and can be disagreed with per
  source. Every competitor is chronological or opaque. That is the thing worth
  leading with — the minus-one page is the second sentence, not the first.
- **Getting subscriptions in becomes a competitive concern rather than a
  convenience.** §11 and §13 move up: a reader nobody can populate in five
  minutes loses to one they can, whatever the feed looks like afterwards.

One thing this does **not** change: the web version is still impossible (§12).
That was refused on CORS, which has nothing to do with what the app is for.

---

## Where the milestones stand

| # | Milestone | Status |
|---|---|---|
| 0 | Launcher feasibility | **Done** — builds, installs, minus-one works, mechanism documented in `UPSTREAM_NOTES.md` |
| 1 | Material shell | **Done** — identity, M3, dynamic colour, edge-to-edge, light/dark/black, scaffold, header, chips. Plus a shape scale, bundled Inter and a two-stage splash, none of which the milestone asked for |
| 2 | Cards layout | **Done** — cards, images, metadata, pull-to-refresh, save, read state, per-card overflow, source favicons, hide source, More/Less |
| 3 | Remaining layouts | **Done** — Cards, Magazine, List and Mosaic, chosen in Settings; Mosaic swaps the container for a staggered grid |
| 4 | Source management | **Done bar reorder** — add, autodiscovery, duplicate detection, edit, remove with undo, multi-select bulk editing, a category screen, search, sort, broken feeds surfaced, OPML in/out. Reorder deliberately deferred; see §2 |
| 5 | Personalisation | **Done** — weighting drives Cards and Mosaic, reads back More/Less and reading habits, two structural diversity rules, read-on-scroll with a tunable dwell, three read-visibility settings, bulk mark with undo, a per-article explanation and a transparency-and-reset screen |
| 6 | Sync | **Part** — Google Reader protocol done first (see §7): client, account, service abstraction and sign-in screen work; read-state mapping and background sync outstanding. Google Drive backup is wanted and specified in §7, not yet built |
| 7 | Glance row | **Done** — weather, sunrise/sunset, feed status. Calendar deferred, as the spec says |
| 8 | Reader / offline / polish | **Part** — reader and offline caching work; sync, filter and frame-path performance done. Missing: accessibility pass, battery profiling, motion polish |

The card **rhythm** (hero / card / compact) is what Cards does *within* one
layout; the four layouts are what the user chooses between. Both exist now.

---

## Reported bugs

Found on device, so they take precedence over anything below when they are in
the way.

- ~~**The feed was empty after a restart.**~~ Not a display fault and nothing
  was being deleted — cleanup was reporting `deleting=0` throughout. The app
  was drowning in its own sync. A device log showed the heap pinned at 244MB
  of 256MB, **520 blocking collections**, the main thread stalled for over a
  second at a time and 131 frames skipped, so the feed could not draw. Two
  causes, compounding:

  `maxFeedItemCount` was passed into `syncFeed` and used only for the cleanup
  afterwards, never to trim what was built — so a feed offering 135 entries
  had 135 articles and 135 content bodies materialised and written, and the
  "items per feed" setting capped nothing. And every feed synced at once, so
  with forty-five sources every one of those payloads was live in memory
  simultaneously. Now trimmed before anything is built, and four feeds at a
  time, which makes the peak the largest feed rather than the whole list.
- ~~**The search bar's two buttons did nothing.**~~ Reported by a tester with
  both circled. They were not broken: the bar was drawn *under* the status
  bar, so the back arrow and the clear button sat where the system takes the
  taps. `FeedSearchBar` replaces a `TopAppBar`, which insets itself, and the
  app's call site swapped one for the other without adding the inset back. It
  now insets by default, so the same mistake cannot be made again; the
  launcher overlay, which measures its own, passes zero. The clear icon was
  also `SubtractSquare` — a minus in a box, which at 22dp reads as a copy
  button, and was reported as one. It is a cross now.
- ~~**An empty feed said nothing at all.**~~ No message, no spinner, no
  artwork — the header, the glance row, the chips, and then a void. Every
  cause looked identical, so an empty feed could not be diagnosed even from a
  screenshot. It now says which of four things happened: no sources, a sync in
  progress, a sync that returned nothing, or a filter excluding everything.
  They are told apart because the right response differs completely, and
  because "nothing here" is not information.
- ~~**Sliders had no value on them.**~~ Every `FloatPref` has carried a
  `specialOutputs` lambda saying how to write its value down since the class
  was written, and nothing ever read it. The dwell slider and the overlay
  transparency slider were both a bare track with no number anywhere.
- ~~**The feed jumped while being scrolled.**~~ With read-on-scroll enabled,
  marking an article read subtracted from its weight, which dropped it a size,
  which shrank the card under the reader's finger and shunted everything below
  it up the screen — once per article, continuously, for the whole scroll.
  An article's size is now settled for as long as the feed is open: whatever
  it was first given, it keeps. Read state was the visible cause and not the
  only one, since marking read also rewrites the reading-habit counts and
  re-runs the clustering, so all three inputs change at once.

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

**What to build next: §14, the Drive backup**, ahead of finishing §7. It is the
one thing here that reduces a risk rather than adding a feature. Today a
subscription list built over years exists in exactly one place, and the only
protection is an OPML export behind a file picker that nobody remembers to
use; a lost phone loses the lot. §14 needs nothing that is unfinished, has no
reconciliation or id mapping to design, and is mostly a destination for bytes
that are already produced correctly.

Nothing is left half-shown by pausing §7 either — its account screen syncs
subscriptions and says so, and the read-state mapping it is missing was never
visible.

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

Cards now reads the same weight. `FeedEmphasis` is one scale shared by both
layouts — Cards draws Large as a full-bleed hero, Mosaic as a tile across both
columns — so the two promote the same articles and differ only in how the
promotion looks. The old positional rule gave the hero to whatever happened to
be eighth.

**Scrolling past as a read mark — built.** Two things kept it off by default,
and both still hold:

- **This is a Discover-shaped surface, not an inbox.** People scroll it idly
  and come back to something they glimpsed. A reader who has not asked for
  this and finds forty articles marked has no way to tell that is what
  happened.
- **It interacts with the weighting.** Read articles carry a −1.5 term, so
  scroll-to-read shrinks everything already seen on the next pass. That is
  arguably correct and it is certainly strong, and it wants seeing on a real
  feed before it is anyone's default.

**Dwell, not velocity — built.** The instinct was right, that a fling past
forty headlines is not reading, but scroll speed is the wrong way to measure
it: velocity swings inside a single fling, so the same gesture would mark some
articles and not others depending on where in the deceleration curve they
landed. Time on screen asks the question directly.

The threshold is the reader's, not a constant: 1, 2, 3 or 5 seconds, off by
default. There is no right number — it depends on how fast someone reads and
how they scroll — so guessing one and hard-coding it would only have produced
a number to argue with. A card is on screen for roughly 300-500ms during a
fling, so every option sits above that floor.

A card also has to be at least 60% visible before its clock starts, or the two
items straddling the edges of the viewport accrue time as fast as the one being
looked at. Dwell accumulates while the list is still as well as while it moves:
an article held on screen while it is read has been read, and requiring
movement would mean the one card you stopped on was the one that never counted.


**Whether a read article then disappears is a separate setting, and the default
is no.** Today `readAt` does exactly two things: it subtracts 1.5 from the
article's weight, so a read article gets a smaller shape next time, and it
feeds the unread count on the glance row. It changes nothing else and hides
nothing. Three levels are worth offering, because they suit genuinely
different readers:

| Setting | Behaviour |
|---|---|
| **Keep** (default) | Read articles stay, at reduced weight — smaller, lower down, still there |
| **Dim** | Also visibly marked as read — the traditional reader's greyed row |
| **Hide** | Removed from the stream entirely |

**All three are built**, ahead of the rest of this section, since they stand on
their own: opening an article is already the one thing that marks it read, so
the setting does something real today and does not wait on the scroll trigger.
Saved and pinned articles are never hidden by it, whatever it is set to.

Hide must not be the default and probably should not be reachable without
scroll-to-read being on first: on a surface people scroll idly, an article that
vanishes because it was on screen for two seconds is indistinguishable from a
bug, and it is gone before the reader knows to look for it.

Two interactions that had to be right, and are:

- **Do not count the same thing twice.** The weight already shrinks read
  articles. Fade on top of that is fine; hide makes the weight term irrelevant
  for those articles rather than compounding with it.
- **Hiding needs a way back** — a "show articles you have read" switch in the
  filter sheet, not buried in Settings. The one place a reader looks for
  something that has vanished is the filter that made it vanish. It is staged
  until Apply like every other control in that sheet, and turning read
  articles back on restores Keep rather than Fade: Fade is a choice made
  deliberately in Settings, and a switch labelled "show" should not quietly
  pick a different way of showing.

#### Still open in this corner

- **"Mark all as read."** The bulk escape hatch every traditional reader has,
  and the obvious companion to a feed that now marks things on its own.
- **An undo window on a scroll-produced batch.** A single mark is recoverable
  because the reader saw it happen; forty in one scroll is not, and the
  scroll trigger can produce forty. Nothing else in the app performs a bulk
  change without an undo.
- **A debug readout of accrued dwell**, showing what each card banked as it
  passed. Worth building only if the four thresholds turn out not to cover
  it — the point is to choose the number from what actually happens rather
  than infer it from behaviour, and that is only worth the screen if the
  behaviour is in question.

**"Why is this here?" — built.** In the per-article menu, listing the signals
that decided the article's size, largest first, with what each contributed.
Derived from the same terms the weight sums rather than computed alongside
them: an explanation written separately from the thing it explains drifts the
first time either is edited, and then confidently says the wrong thing.

It is at the article rather than only in Settings because by the time someone
has walked to a settings screen they have stopped wondering about the card
that prompted the question.

**Reading habits as a weight term — built, and it did not need accounts.** The
plan was to gate this on sign-in and count usage server-side. It did not have
to wait: `readAt` is already on every article, so per-source read counts, opens
per week and time-since-last-read can all be derived on device today, from data
that is already there. What an account adds is *carrying those counts to
another phone* — which is §7's job, not a prerequisite for the feature. Build
it locally, sync it later.

The term itself is a small one on purpose. A source read often gets a nudge, not
a promotion: enough to break a tie between two similar articles, never enough
to outrank a fresh story from somewhere else.

**The diversity constraint is not optional, and it is the hard half — built as
two structural rules.** Left alone, "favour what they read" converges on one
site: it gets shown more, so it
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

**The reset control is a screen, not a button.** `docs/REFERENCES.md` §4 found
no open-source prior art for transparent, resettable preference learning in a
feed reader, and a bare reset is only the resettable half. "What Whisper has
learned" lists every source with its More/Less score, how many of its articles
were read in the last thirty days, and the resulting weight — the number the
feed actually uses, computed by the same arithmetic rather than an
approximation of it. A score nobody can audit is one nobody can correct.

Resetting is per source as well as wholesale, so one mistaken Less can be
undone without discarding a year of signal. Read state is deliberately not
cleared by it: that is a record of what happened rather than an opinion about
it, and "forget what you have learned" does not mean "mark a year of articles
unread" to anyone who presses it.

Worth restating: `docs/REFERENCES.md` §4 found **no open-source prior art** for
transparent, resettable preference learning in a feed reader. This is
build-it-ourselves rather than assembly, and should be budgeted that way.

### 6. Breaking news, sticky and pinned

Three related asks, in increasing order of difficulty.

**Pinning — built.** A reader pins an article they are following and it holds
the top of the feed until unpinned, whichever sort is active. No inference,
nothing to get wrong, and it is the manual escape hatch for whenever the
automatic detection below gets it wrong.

The boolean on `Article` was already there, and that turned out to be the
problem rather than the head start: `bookmarkArticle` set `pinned` to the same
value as `bookmarked`, so saving an article pinned it and unsaving released it.
Pinning could not mean anything on its own, and every saved article was
quietly collecting both weight bonuses. The two are different things — saving
is "I want to find this later", pinning is "I am following this, keep it in
front of me" — and they are now separate switches.

Since pinning was never reachable from the interface, every pinned row in an
existing database is a saved article rather than a pinned one. Migration 13→14
clears the column, so upgrading does not put every article the reader has ever
saved at the top of their feed.

Opening a pinned article no longer releases it either. That was right when a
pin was a side effect of saving; it is wrong when a pin means the reader is
following a story, since reading today's report is not a signal that they have
stopped.

**Breaking news detection — built, on title similarity.** The signal is the
right one: when several sources publish about the same thing inside a few
hours, that is a story, and a single-source post is not. The hard part was
"the same thing", and the cheapest option turned out to work.

What makes plain word overlap usable is weighting words by how *rare* they are
in the current feed. A token in two or three titles out of four hundred is a
proper noun identifying an event; one in fifty is a topic. Two articles sharing
two rare tokens are almost always the same story; two sharing four common ones
almost never are. An inverted index over the rare tokens only keeps it well
short of comparing every pair.

Ten tests cover it, including the two ways it can fail in public: inventing a
story out of headlines that merely share a topic, and being fooled by a feed
that appends its own name to every title.

The options considered, and the honest cost of each, kept for whoever revisits
this:

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
its own topic is not breaking news. The cluster must span **distinct sources**
— three of them — and everything in it must fall inside a twelve-hour window,
or a chatty feed would hold the hero slot all day. Both are tested.

Two things it deliberately does not do. It does not collapse the cluster: every
member stays in the feed at whatever size it earned, and only the lead is
promoted, because hiding four reports means choosing which four the reader does
not see and no automatic rule here is good enough for that. And it is off by
default — this is the one part of the weighting that infers rather than counts,
and a feed that hands the biggest slot to the wrong article for reasons the
reader never asked for is worse than one that never tries.

**Pinning and sticky are what remain.**

**Sticky until scrolled past — built.** A setting, off by default.

The one ordering rule those three needed turned out to be dull, which is the
point: **whatever the ordering already put first, if it is being held on
purpose.** Pinning sorts an article first; the breaking-news term weights a
cluster lead heavily enough that it lands first. Both arrive at the top
through machinery that already existed, and sticky only asks whether the
article now at the top got there for a reason worth holding. Nothing
competes, and only one thing can be held, because only one article can be
first — the reader's own pin winning, since it sorts above everything.

`stickyHeader` alone gives the wrong behaviour: a header stays pinned while
its section is on screen, and with one header over the whole list that means
for ever. Sticky would become permanent, and the reader could not get rid of a
story by scrolling, which is the first thing they will try. Two sections
instead — the held article heads one holding the next five articles, then an
empty, zero-height header takes the sticky slot and pushes it off. What the
reader sees is the card sliding away once a few articles have gone by, with no
animation written.

Mosaic does not take part: a staggered grid has no sticky slot, and the lead
already crosses both columns there, which is that layout's way of saying the
same thing.

Both settings sit with the personalisation switches, not in a category of
their own: "Highlight breaking news" and "Hold it there while you scroll", the
second directly under the first because it only modifies what the first
promotes.

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

#### What is built

- **`GoogleReaderApi`** — the protocol itself: ClientLogin, the separate write
  token, subscriptions, item ids, edit-tag, subscription/edit. Fourteen tests
  cover the parsing and the identifiers.
- **`GoogleReaderIds`** — the three shapes an id comes in, in one place. The
  long form is sixteen hex digits, unsigned, and larger than `Long.MAX_VALUE`
  for half its range; `toLong()` on it throws on exactly the ids whose top bit
  is set, which is how a client works for months and then falls over on one
  article. Tested at the boundary.
- **`RssService`** — the abstraction, with only `sync()` abstract. Everything
  else has a working local implementation, and `LocalRssService` is the real
  default rather than a stub.
- **`SyncAccount`** — one account, in `EncryptedSharedPreferences` rather than
  DataStore because it holds a credential.
- **The account screen** — Settings → Account. Sign in, sync, sign out.

#### The division of labour, which is the design decision

The remote service syncs the **subscription list** and **read state**. It does
not fetch articles; the local path still does that.

That is deliberate. Whisper's articles carry things the protocol has no field
for — extracted full text, the chosen image, the built summary — so taking
them from the server would mean losing those or fetching twice. Fetching
locally also keeps behaviour identical with and without an account, and keeps
the app working when the server is down. An account changes *which feeds* and
*what has been read*, not what an article is.

Google Drive is **not** a second version of this and is no longer described
here; see §14. It is a backup destination for the OPML, it does not depend on
any of this, and it should be built before the rest of it.

#### Deliberately unfinished, and named rather than hidden

- **Read state is pulled but not applied.** Matching the protocol's item ids to
  Whisper's own article uuids needs a mapping this version does not store, and
  guessing would mark the wrong articles read. The ids are fetched so the shape
  is proven against a real server. The mapping — a `remoteId` column on
  `Article`, written at sync time — is the next piece, and the same mapping is
  what lets read state be pushed back.
- **Removals are not applied.** A feed the server does not mention is left
  alone rather than deleted. Deleting somebody's subscriptions because of a
  partial response or the wrong account is unrecoverable, and those are exactly
  the failure modes a first version meets.
- **No background sync yet.** The account screen syncs on demand; hooking it
  into the existing `FeedSyncer` schedule comes with the id mapping.

### 8. Ship it

- **The Lawnchair whitelist PR** — see below. Worth doing, and no longer the
  gate it was recorded as: see "What this is now". The app is usable without
  ever touching a launcher.
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

### 13. Bookmarks in, feeds out

**Feasible, and cheaper than it looks — most of the engine is already built.**
`FeedParser.alternateFeedLinksAtUrl` already reads `<link rel="alternate">` out
of a page's head and filters on the RSS, Atom and JSON types.
`candidateFeedUrls` already probes eight common paths including the Blogger and
WordPress ones. `normalizeFeedUrl`/`isSameFeedUrl` already answer "is this the
same feed", and OPML import and export already exist. What is missing is a
bookmark parser, a queue, and a review screen — plumbing around code that
works.

The idea is a good one and the framing is the right one: *the user gives
Whisper the websites, Whisper finds the feeds.* Nobody should have to search
for "BBC RSS feed".

#### Three parts cannot be built, because they need a server

The proposal was written for something with a backend. Whisper has none, and
§12 records why it must not acquire one.

- **The shared discovery cache** — "if another Whisper user later imports the
  same website, the known feed can be tested first" — requires a service that
  sees which sites users are subscribing to. That is precisely the bargain
  declined for Discover and for the web version. A **local** cache, keyed by
  domain on the device, is worth having and costs nothing.
- **The standalone web tool** — same CORS wall as §12. A browser cannot fetch
  arbitrary third-party sites, so the tool would need a server proxying every
  scan, and that server would see every bookmark file uploaded to it.
- **The privacy section's framing.** It asks what happens to bookmark data
  "sent to the scanning service". Nothing is sent anywhere: the file is parsed
  on the phone and the phone does the fetching. That is a stronger answer than
  any policy could be, and the screen should say so plainly rather than
  reassure.

#### SSRF is still a real concern, for a different reason

Blocking private address ranges matters here too, but not because a server is
being tricked into reaching its own network — because a *phone* would be
reaching the user's own LAN. A bookmark, or a redirect from one, pointing at
`192.168.1.1` would have Whisper probing the household router, printers and
NAS for `/feed.xml`. Block the private ranges and `localhost`, check the
destination again after every redirect, and allow only `http` and `https`.
The proposal's list is right; only the reasoning changes.

#### Pick folders, not a file — this is the design, not an option

The proposal treats folder preservation as a checkbox near the end. It is the
whole feature.

Importing an entire bookmark collection has a yield problem: bookmarks are a
junk drawer, not a reading list. Amazon products, Stack Overflow answers, a
router admin page, forty half-read docs pages. Most of it publishes no feed,
and a good share of what does is a site someone bookmarked once for one thing.
Reviewing four hundred results to keep thirty is not obviously less work than
adding thirty by hand.

**A folder is not a junk drawer.** Someone who keeps a News folder and a Tech
folder has already done the curation, by hand, over years — and did it because
they read those sites. Scanning only the chosen folders takes the yield from
"a fraction of what is found is wanted" to "nearly all of it".

It also changes what has to be built. Forty sites is not eleven hundred: it is
under a minute, and it needs no background job, no Wi-Fi-only default and no
resumable queue. Those become a later concern for whoever does want the whole
file, not a prerequisite.

And the folder names are the categories. Whisper already has categories, so a
News folder becomes the News category — which is exactly what the breaking-news
detection in §6 reads, since it only considers sources filed under news.

**That is more than a convenience, and it is the argument for building this
sooner rather than later.** Clustering needs several sources covering the same
story, filed under news. Someone who has added eight feeds by hand has neither
the sources nor the categories, so the feature that most distinguishes the app
is switched off by default and would do nothing if it were switched on.
Importing a News folder produces precisely the input it needs — a dozen news
sources, already categorised, in one action. The importer is not only how
people get started; it is what makes the clustering fire at all.

The same holds for the weighting generally. Reading habits, source affinity and
the diversity rules all want a feed with breadth in it, and all of them are
inert on a handful of hand-added sources.

So the first screen is not "choose a file" but, after choosing one, a folder
tree with checkboxes and a count beside each:

    ☑ News          14 bookmarks
    ☑ Tech          22 bookmarks
    ☐ Shopping      88 bookmarks
    ☐ Work         310 bookmarks

#### Scale is the actual engineering problem, once the whole file is in scope

The spec's worked example is 1,482 bookmarks and 1,126 unique URLs. Costed
honestly on a phone:

- 1,126 page fetches for autodiscovery, at perhaps 100 KB each, is **~110 MB**
  before any probing.
- Suppose 40% advertise a feed. The other ~676 fall through to probing, and
  `candidateFeedUrls` currently generates up to 16 candidates per URL —
  **~10,800 further requests**, nearly all of them 404s.
- At a dozen concurrent connections that is **twenty minutes to an hour** of
  continuous radio, and a meaningful bite out of a mobile data allowance.

Three things make it tractable, and the first is by far the biggest:

1. **Group by domain before probing.** Fifteen BBC bookmarks are one site to
   probe. This collapses the long tail more than any other change.
2. **Probe the origin, not the path.** `candidateFeedUrls` tries both because
   the Add Feed screen handles one URL at a time and can afford to; a scan of a
   thousand cannot.
3. **Wi-Fi by default, as a resumable WorkManager job.** The scan has to
   survive the screen going off and the app being swapped out — an hour is
   long enough that requiring the user to sit and watch it is not an option.

#### Cut from the first version

- **CMS-specific rules.** WordPress, Ghost, Substack, Blogger and Medium all
  advertise their feeds in `<head>`; autodiscovery already catches them. This
  is a page of code for cases that are handled.
- **Three-level confidence.** Two states carry the whole distinction that
  matters: the site *said* this is its feed, or Whisper *guessed*. A middle
  band between them adds a word to the UI and nothing to the decision.

#### The best idea in the document is buried at §46

**Broken-feed recovery.** When a subscribed feed starts failing, run discovery
against its site and offer the replacement. Whisper already surfaces broken
feeds, so this is one screen and a use of an engine built for something else —
and it fixes a problem every RSS reader has and none of them solve. It may be
worth building *before* the bookmark importer: it needs only the engine that
exists today, and it earns its keep on a feed list of forty as much as on a
bookmark file of two thousand.

#### What the first version should be

One `discoverFeeds(url)` entry point, shared by the Add Feed screen (which
should stop having its own), bookmark import, bulk paste, and broken-feed
recovery — the proposal's §49 instinct is right and the reason to follow it is
that four half-implementations is how this goes wrong.

Then: parse Netscape `bookmarks.html`, show its folder tree and scan only what
is ticked. Drop non-http schemes, normalise, group by domain, scan with bounded
concurrency, validate that what came back parses as a feed and has items,
deduplicate several bookmarks onto one feed, and show a reviewable list with
the four states that matter — found, several found, none found, unreachable.
Each folder becomes a category on the feeds that came out of it.

Not deleting anything is the rule here as everywhere: a bookmark with no feed
is reported, never silently dropped.

**Where it goes:** the engine first, in the Add Feed screen and in broken-feed
recovery, where it is small and immediately useful and gets exercised daily.
Folder-scoped import lands on top of a proven engine and is an afternoon's
plumbing rather than a gamble. Whole-file import — with the job queue, the
data-use warnings and the domain grouping that scale needs — only if anyone
asks for it.

### 14. Google Drive — somewhere to put the OPML

Previously recorded here as not being pursued, on two objections. One was
wrong and the other is avoidable, so it is back.

**The framing was wrong.** "It syncs your devices rather than your reading" was
written as a dismissal and is actually the point. Google Reader sync needs a
server the reader chose and probably runs; plenty of people will never do that
and still stand to lose every subscription to a factory reset. For them a Drive
backup is not a lesser sync — it is the only thing standing between them and
starting again. The two features serve different people and should not be
weighed against each other.

**The Play Services objection is avoidable.** Google Sign-In needs Play
Services; the Drive REST API does not. `AppAuth` performs a standard OAuth2
flow in a browser tab and hands back a token the plain REST endpoints accept,
so the app can talk to Drive with no proprietary dependency and keep building
on F-Droid. F-Droid will mark it NonFreeNet, which is accurate and applies to
every network service.

**One objection does stand and should be said out loud in the interface.**
Uploading a backup means Google holds the reader's subscription list. That is
their own Drive and their own choice, and it is categorically different from a
recommendation service profiling them — but this app tells people nothing
leaves their phone, so the screen that offers this has to be equally plain that
turning it on is the exception.

**Scope is deliberately small: this is the existing export, sent somewhere
automatic.** Not continuous synchronisation — that is §7's job and the two
should not be confused. This is "my subscriptions are safe", nothing more, and
keeping it that narrow is what makes it a week rather than a milestone.

OPML export and bookmark export both work today and already produce exactly
the right bytes; all that is missing is a destination that is not a file
picker. In order:

1. **`appDataFolder`, not the visible Drive.** A private folder the app owns,
   invisible in the reader's file list, removed when the app is uninstalled.
   Nothing of theirs to tidy up, and no chance of a stray file being edited.
2. **The OPML, and the bookmarks.** Sources and their categories are the thing
   worth protecting — they are what took years to assemble and what cannot be
   reconstructed. Read state and preferences can follow later if they are
   missed; they are not what anyone means by "I lost my feeds".
3. **Restore on a fresh install** — the case the whole feature is for. Offered
   during onboarding, once, when a backup is found.
4. **Automatic, on a schedule, on Wi-Fi.** A backup nobody remembers to take is
   not a backup.

Because it is a file rather than a live connection, there is no reconciliation
to design, no conflict to resolve and no id mapping — the three things making
§7 the larger piece. Uploading a copy of a file that already exists is most of
the work.

**Where it goes:** it does *not* need to wait for §7, and it should not be
built on §7's `RssService` seam either — an earlier draft of this note said it
should, on the reasoning that Drive is a provider in the same sense. It is not.
That interface is about reconciling with a service that has opinions about
read state; this uploads a file. Forcing it through would mean implementing
`sync()` as "write the OPML" and leaving every other method empty, which is a
worse description of what is happening than a plain backup class.

Being independent of §7 also makes it the better thing to ship first: it is
smaller, it needs nothing that is unfinished, and it protects the thing people
would actually grieve.
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
