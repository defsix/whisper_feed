# Prior art and reference implementations

Findings from reading the source of comparable projects, recorded so the
Milestone 1+ work can lift proven solutions instead of rediscovering them.

**Licence position:** every project below is **GPLv3**, the same licence as
Whisper. Code may be adapted directly, provided the upstream copyright and
origin are preserved in the adapted files and recorded in `ATTRIBUTION.md`.
Nothing here has been copied into the tree yet.

| Project | Licence | Read at | Stack |
|---|---|---|---|
| [Twine](https://github.com/msasikanth/twine) | GPLv3 | `bce23e5` | Compose Multiplatform, Ktor, SQLDelight, Ksoup |
| [ReadYou](https://github.com/ReadYouApp/ReadYou) | GPLv3 | `d2b979c` | Compose, OkHttp, Room, Hilt |
| [Feeder](https://github.com/spacecowboy/Feeder) | GPLv3 | not yet read | Compose, Room |

Twine is Kotlin Multiplatform on Ktor/SQLDelight, so its code cannot be
pasted into our Android/OkHttp/Room codebase — the *logic* transfers, the
files do not. ReadYou is plain Android and much closer to a drop-in.

---

## 1. Feed autodiscovery — Twine

Covers handoff §9.1 ("website URL autodiscovery"). Twine splits this in two,
which is a better decomposition than the handoff assumes.

### Generic HTML discovery
`core/network/.../fetcher/FeedFetcher.kt`

Fetches the URL, dispatches on `Content-Type`, and when it gets HTML back,
parses it and looks for the first `<link>` whose `type` is
`application/rss+xml` or `application/atom+xml`, resolving relative hrefs
against the original host. Worth copying wholesale:

- It sends a broad `Accept` header listing the feed types ahead of `*/*`, so servers content-negotiate toward the feed.
- It treats a **missing** `Content-Type` as "try XML anyway", which is common on small self-hosted feeds.
- Redirects are followed manually with a redirect counter, not by the HTTP client, so redirect loops terminate.

### Special-case resolution
`core/network/.../fetcher/FeedUrlResolver.kt`

A separate resolver for sources where autodiscovery *cannot* work, applied
before the generic path. This is hard-won knowledge and directly relevant,
since these are exactly the sources a personal feed tends to contain:

- **Reddit** — serves a script-only shell to non-browser clients; the resolver rewrites `/r/<sub>` and `/user/<u>` to `…/.rss`.
- **Mastodon handles** — `@user@instance` is not a URL at all; rewritten to `https://<instance>/@<user>.rss`.
- **YouTube** — channel pages, `@handles` and video URLs all need different treatment, because only the channel *id* keys the feed. Handles require scraping the channel id out of the page; video URLs go via the oEmbed endpoint.
- A nice detail: it targets the channel's long-form uploads playlist (`UULF…` rather than `UC…`) so **Shorts are excluded from the feed**.

**Recommendation:** adopt both, as a two-stage `resolve()` then `discover()`.
The special-case list is cheap to port and disproportionately improves the
"add feed" experience.

---

## 2. Sync abstraction — ReadYou

Covers handoff §15/§16.

### Correction to the plan's assumption
The handoff (and an earlier claim in this project) assumed a Feedly adapter
could be lifted from ReadYou. **It cannot: ReadYou has no Feedly
implementation.** `AccountType.Feedly` exists as an enum constant, its
settings branch is an empty `{}`, and `RssService.get()` routes both Feedly
and Inoreader to `localRssService`. Only two real providers exist on disk:
`infrastructure/rss/provider/fever/` and `…/greader/`.

What ReadYou *does* have is a complete **Google Reader API** client
(`GoogleReaderAPI.kt`, ~560 lines, plus DTOs). That protocol is the de-facto
standard spoken by FreshRSS, Inoreader, Miniflux and BazQux. Given the
handoff's own finding that Feedly's API is enterprise-gated, **Google Reader
protocol support is the better target** for live sync beyond OPML — it buys
several services for one implementation, and it is the one that actually
exists in readable form.

### The abstraction shape worth copying
`domain/service/AbstractRssRepository.kt` + `domain/service/RssService.kt`

The handoff sketches `SyncAdapter` as a bare `pull()`/`push()` pair. ReadYou's
shape is better and worth adopting instead:

- One abstract base class where **only `sync()` is `abstract`** and everything else (`subscribe`, `markAsRead`, `markAsStarred`, `renameFeed`, `moveFeed`, `deleteFeed`, `addGroup`, …) is `open` with a working **local** implementation.
- Remote providers subclass it and override *only* what the remote service actually supports.
- `LocalRssService` is therefore not a null-object stub but the real default, which matches our local-first rule: the app is fully functional with no adapter at all, and sync is genuinely additive.
- A small dispatcher (`RssService`) maps the current account type to a provider instance and exposes it as a flow, so the rest of the app never branches on account type.

This directly serves handoff §16's "Room is the running source of truth, cloud
sync is replication" — the local implementation *is* the base class.

---

## 3. Theming — Twine

Covers handoff §5 (Material You, dynamic colour).

`shared/.../ui/SeedColorExtractor.kt`, `DynamicColorState.kt`, `AppTheme.kt`

Twine derives its palette from the **currently visible article image**: it
extracts a seed colour via [`materialkolor`](https://github.com/jordond/MaterialKolor),
caches it in an LRU keyed by image URL (with in-flight request coalescing so
a fling doesn't launch duplicate extractions), and animates the scheme
between seed colours as you scroll.

Two takeaways:

1. **`materialkolor` is worth adopting regardless.** It generates a full M3
   scheme from *any* seed colour on any API level. Android's built-in
   `dynamicLightColorScheme()` needs API 31+ and only reads the wallpaper —
   our `minSdk` is 26, so on API 26–30 we would otherwise have no dynamic
   theming at all. This gives us a graceful fallback.
2. **Content-based theming is a different thing from Material You** and the
   handoff asks for the wallpaper-based kind. Twine's per-article recolouring
   is striking but would fight our "no excessive animation / don't delay
   reading" rule if applied to a fast-scrolling feed. Recommend: wallpaper
   dynamic colour as specified, and treat content-derived accent as an
   optional later flourish, if at all.

---

## 4. Where there is no prior art

Searching turned up nothing open-source matching two parts of the plan, so
these should be treated as unproven build-it-ourselves work rather than
assembly:

- **Transparent "More/Less like this" personalisation** (§12). No RSS reader found implements user-visible, resettable preference learning.
- **Multiple co-existing layout modes** (§8). Twine and ReadYou each commit to one opinionated layout; switching between Cards/Magazine/List/Mosaic over one domain model is ours to design.

Caveat: this is absence of evidence from a bounded search, not an exhaustive
survey.

---

## 5. Launcher-side prior art

Recorded in `UPSTREAM_NOTES.md` §3. In short, Lawnchair's hardcoded provider
whitelist in `FeedBridge.kt` doubles as a directory of everyone who has
shipped a minus-one feed provider: Neo Feed, HomeFeeder (`ua.itaysonlab`),
Librechair (`launcher.libre.dev`), Smartspacer, and
[AIDL Bridge](https://amirzaidi.github.io/bridge.html) (`amirz.aidlbridge`) —
the latter being the reference implementation of the `ILauncherOverlay`
protocol that the whole technique derives from.
