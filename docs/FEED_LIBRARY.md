# The bundled feed library

Whisper ships a directory of feeds so that somebody with an empty reader has
somewhere to start. Every other route in — paste an address, import OPML, scan
bookmarks, wait a week for it to notice what your reading links to — assumes
you already know the answer.

## Two tranches

The library was built twice. The first came wholesale from an upstream
directory; the second was assembled by hand to fill in the countries that
directory had never covered. They were verified the same way, and the second
is described under **The country tranche** below.

## Where the first tranche comes from

[plenaryapp/awesome-rss-feeds](https://github.com/plenaryapp/awesome-rss-feeds),
under **CC0 1.0** — public domain, no attribution required, no friction with
GPL-3.0. It is the directory a different Android reader uses for the same
purpose, which is some evidence it is maintained for the job.

Bundled, never fetched. A directory served over the network would mean asking
somewhere what feeds exist, and a query like "photography" or "Irish politics"
says a good deal about a person. `PRIVACY.md` lists exactly one
developer-initiated third-party call, and a catalogue is not worth making it
two.

## What was checked, and what was removed

Every one of the 720 addresses was fetched and its response inspected before
anything shipped.

| | |
|---|---|
| Verified: returned a parseable feed | 600 |
| Kept, unverifiable from the build machine | 58 |
| Dropped as dead | 62 |

**Dropped** means one of: HTTP 404 or 410; a 200 that served HTML rather than
a feed, which is a site that has removed or moved its feed; or a DNS, TLS or
connection failure. The last of those matters more here than it looks — the
app refuses cleartext, so a host with no working certificate cannot be read at
all.

**Unverifiable** means the build machine could not reach it: a 403 from a bot
wall, a 429 from the checker's own parallelism, or an egress policy in the
container. Those almost certainly work on a phone, and dropping a feed on the
strength of a proxy's opinion would be worse than shipping one that needs a
retry. They ship.

Two packs did not survive at all. **Iran**: nothing in it verified. **Russia**:
the source file has unescaped double quotes inside quoted attributes, which is
ambiguous rather than merely wrong — see below. Russia has since been added
back from hand-picked addresses rather than repaired; Iran has not.

## The ampersands

Thirteen of the twenty-four country files are not well-formed XML. They carry
bare `&` in attribute values — "Breaking news, showbiz & celebrity photos",
"World & Nation" — 479 of them across the set.

This was not only a problem for building the library. Whisper's OPML importer
uses SAX, which is strict and right to be, so a single unescaped ampersand two
hundred feeds into a file rejected the whole thing. Anything that writes OPML
by string concatenation produces this, which is most things that write OPML, so
the importer now escapes ampersands that do not begin an entity before parsing.

Only ampersands. Unclosed tags and stray quotes are ambiguous, and guessing at
somebody's subscription list is worse than declining it — which is why Russia
is absent rather than repaired.

## The country tranche

The upstream directory covers 22 countries, with nothing for Scandinavia, the
Low Countries, central Europe, Russia, or most of east and south-east Asia. A
reader in any of them opened the library, found their own country missing, and
learned the wrong thing about what the app is for.

So a second set was assembled by hand: national broadcasters and major papers,
164 addresses across 28 countries, probed exactly as the first tranche was.

| | |
|---|---|
| Verified: returned a parseable feed | 96 |
| Kept, unverifiable from the build machine | 15 |
| Dropped | 53 |

Two rounds of probing rather than one. The first lost 39, and roughly a
quarter of those were a publication that still has a feed at a different
address — an `arc/outboundfeeds` path, a `?outputType=xml`, a section id that
has moved on. Trying a second address for each recovered eight, which is worth
one extra round of anybody's time.

**Dropped** is the same three cases as before: 404 or 410; a 200 serving HTML,
which is a site that has retired its feed; or DNS, TLS or connection failure.
**Unverifiable** is a refusal to serve rather than an absence — fourteen 403s
from bot walls and one 451 — kept for the same reason the first tranche keeps
them, that a datacentre IP is not a phone.

Ireland, Japan and Poland already had packs and were merged into rather than
replaced. Ireland is the clearest case for why this tranche exists: it had six
feeds and none of RTÉ, the Irish Times or the Irish Independent, which is most
of what an Irish reader would look for first.

### One editorial decision, stated plainly

The Russia pack is Meduza, The Moscow Times, Kommersant and Interfax. RT and
Sputnik are not in it. Both are widely carried, so their absence is a choice
rather than an oversight: a pack labelled "Russia" in a reader's library reads
as a recommendation, and state outlets under EU sanction are not something to
recommend silently. Anybody who wants them can paste the address — the app
takes any feed, and this is a starting list rather than a permitted one.

## The cleartext sweep

Checking the new packs turned up 119 `http://` addresses in the *first*
tranche, carried over from upstream and never looked at. They matter more than
they look: Whisper tells Android to refuse cleartext outright, so an `http://`
feed does not merely travel in the clear — it cannot be fetched at all, and a
new reader picking one gets a subscription that fails on its first sync and
lands in Broken feeds.

Each was asked for over https: 97 answered with a feed and were rewritten, and
23 had no working https and were dropped, on the same reasoning that dropped
the dead ones. A feed the app is structurally incapable of reading is not a
feed to offer somebody on their first day.

## Five is the floor

The first pass left 28 of the 50 countries with fewer than five feeds — four
had two — while the United States had nine. That is a directory with an
opinion about whose news matters, arrived at by nobody deciding anything: it
is simply where the upstream list was thickest and where the first round of
probing happened to succeed.

So every pack now carries at least five, countries and topics alike. A third
tranche of 131 addresses was probed to get there, and the four countries still
short after it — Indonesia, China, Kenya, Vietnam — were given a round of
their own rather than left at four.

A pack of two is worse than no pack. Somebody opening "Denmark" and finding a
pair of feeds learns that the app does not really cover Denmark, which is a
more damaging thing to learn than that Denmark is not listed — and the reader
in a country the directory skimped on is the one who most needs it to work.

The floor is now a test rather than an intention: `FeedLibraryAssetTest` fails
the build if any pack drops below five. Because every address is verified
before it ships, that is a promise about five *working* feeds rather than five
names.

China was the hardest. Most Chinese outlets' English feeds have been retired,
and eight candidate addresses returned HTML or 404 before CGTN, ECNS, Nikkei
Asia and The Diplomat filled the pack out.

## The same publication twice

Filling the packs out to five put some publications in twice under two
addresses — Tagesschau, Hong Kong Free Press, Republika at `/rss` and `/rss/`,
the Japan Times. A pack of five with two rows for one masthead is really a
pack of four, so this undercuts the floor rather than merely looking untidy.

Eight were removed. A heuristic found them — same registrable domain, one
title containing the other — and **three of its eight were wrong**, which is
the useful part of the story:

- **Al-Ahram** alongside **Ahram Online** is the Arabic edition beside the
  English one. Two editions are two feeds. The language guard missed it
  because "Al-Ahram" names no language.
- **Philippine News Agency** and **Philippine Information Agency** are two
  different agencies that share a government domain and most of a name.
- For **Feld Thoughts** it kept the wrong one of the pair, a tag archive over
  the blog itself.

So the automated check that ships is the narrow one: the same address twice,
including the `/rss` versus `/rss/` case that let Republika through. Deciding
that two *publications* are the same needs a person, and the scan is a tool to
put candidates in front of one — `FeedLibraryAssetTest` does not attempt it,
because a matcher confident enough to decide it silently deleted three feeds
that belonged.

## Refreshing it

Re-run the fetch and verification against the upstream repository, then
regenerate `app/src/main/assets/library/`. The manifest is `index.json`: slug,
display name, `topic` or `country`, and a feed count. Verification is the part
worth repeating — a directory ages in exactly the way the 62 dropped addresses
show.
