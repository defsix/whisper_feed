# The bundled feed library

Whisper ships a directory of feeds so that somebody with an empty reader has
somewhere to start. Every other route in — paste an address, import OPML, scan
bookmarks, wait a week for it to notice what your reading links to — assumes
you already know the answer.

## Where it comes from

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
ambiguous rather than merely wrong — see below.

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

## Refreshing it

Re-run the fetch and verification against the upstream repository, then
regenerate `app/src/main/assets/library/`. The manifest is `index.json`: slug,
display name, `topic` or `country`, and a feed count. Verification is the part
worth repeating — a directory ages in exactly the way the 62 dropped addresses
show.
