# Privacy

**Short version: Whisper has no servers. Nothing you do in it reaches us,
because there is nowhere for it to arrive.**

This document describes what the app actually does, not what it intends to do.
Everything in it was checked against the source, and the source is here for you
to check it against too.

Last reviewed: September 2026, for Whisper 1.0.0.

---

## What we collect

Nothing.

There is no account with us, no server we operate, no database we hold, and no
analytics, crash-reporting, advertising or tracking library in the app. Not one
— that is a statement about the dependency list, which you can read.

We could not tell you how many people use Whisper, which feeds are popular, or
whether anyone opened it today. That is not restraint; there is simply no
mechanism.

## What stays on your phone

All of it:

- The feeds you subscribe to, and the categories you file them under
- Articles fetched from those feeds, including their text, and the full page
  text where you have asked for that
- Which articles you have read, saved and pinned
- Which sources you read most often, which the app uses to decide what to show
  larger — a count per source, nothing about what you read in them
- Sources you have hidden, and words you have blocked
- Every setting

This lives in the app's private storage. Uninstalling removes it. So does
Android's "clear storage".

## What leaves your phone, and where it goes

Whisper makes network requests. It has to — a reader that never fetches
anything is a blank screen. Here is the complete list.

### 1. The feed servers you chose

When Whisper fetches a feed, that server sees what any web server sees: your IP
address, the time, and a `User-Agent` header identifying the app and version.
These are servers **you** added. We are not in the middle of that request and
never see it.

### 2. Article pages, if you turn on full-text fetching

Off by default, per-feed or globally. When on, Whisper fetches the article's own
page so it can show the whole piece rather than a summary. That publisher's
server sees the same things a browser would. No cookies and no account are sent,
so anything behind a paywall stays behind it.

### 3. Images inside articles

Loaded from wherever the article points at them, which is usually the
publisher's own servers or their CDN.

### 4. Open-Meteo, only if you turn on the glance row

The weather and sunrise strip is optional. When it is on, Whisper asks
[open-meteo.com](https://open-meteo.com) for a forecast.

Whisper **does not request location permission and cannot read your device's
location.** You type a place name. That name is sent to Open-Meteo's geocoding
service to turn it into coordinates, and the coordinates are then **rounded to
two decimal places — about a kilometre — before any forecast is requested.**
Open-Meteo sees an approximate area and your IP address, and nothing else.

Turn the glance row off and no request is ever made.

### 5. A sync server, only if you set one up

If you connect a Google Reader–compatible server — FreshRSS, Miniflux, or
similar — Whisper signs in with the username and password you give it and
syncs read state and subscriptions with **your** server. Plaintext HTTP is
refused outright, so those credentials cannot cross the network unencrypted.
The credentials are stored encrypted on the device, behind a key held in
Android's hardware-backed keystore.

### 6. A Mastodon instance, only if you connect one

Standard OAuth against the instance you name. The token is stored the same way.

### 7. Your own backup destination

If you turn on backups, the OPML and settings files are written to a folder
**you** pick with Android's document picker — local storage, an SD card, or a
cloud folder if that is what you choose. Whisper writes the file; where that
folder actually lives is between you and whoever provides it.

### 8. Android's own backup, only if you ask for it

Off by default, and asked separately for cloud backup and for phone-to-phone
transfer, because those are different questions. Your saved credentials are
excluded from both routes regardless of what you choose.

## What Whisper refuses to do

- **Plaintext HTTP is refused**, everywhere. A feed served over `http://` will
  fail to fetch and appear under Broken feeds. This is deliberate: article HTML
  is rendered in a WebView, and anyone on the network can rewrite a page in
  transit.
- **Private and local network addresses are refused.** A feed or an article link
  pointing at `192.168.x.x`, `10.x.x.x`, `localhost` or similar is not fetched,
  on every redirect hop rather than only the first. A hostile feed cannot use
  your phone to reach inside your own network.
- **The in-app browser opens `http` and `https` only.** Not `file:`, not
  `content:`, not `javascript:`.

## Permissions, and why

| Permission | Why |
|---|---|
| `INTERNET` | Fetching feeds. |
| `ACCESS_NETWORK_STATE` | Knowing whether to sync now or wait for wifi. |
| `SYSTEM_ALERT_WINDOW` | Not used for drawing. Holding it exempts the app from Android's background-activity-launch restriction, which is what lets a tap in the launcher panel open an article. Without it, the panel renders and taps do nothing. |
| `READ/WRITE_EXTERNAL_STORAGE` | Android 9 and older only, capped in the manifest. Newer versions use the document picker, which grants access to one folder you choose. |

There is no location permission, no contacts, no camera, no microphone, and no
"query all packages".

## Your rights

The GDPR gives you rights over personal data a controller holds about you. We
hold none, so there is nothing for us to disclose, correct, export or delete.

What you can do yourself:

- **Export**: OPML for your subscriptions, a JSON file for your settings, both
  from the Backup screen.
- **Delete**: uninstall the app, or clear its storage in Android settings.
- **Selective deletion**: remove a source and its articles go with it; clear a
  source's articles and keep the source.

If you want data removed from a *feed server* or *sync server*, that is a
request to whoever runs it, not to us.

## Children

Whisper shows whatever feeds it is given. It has no content rating, no
moderation, and no filtering beyond the blocked-words list, because it has no
view of what a feed will contain. It is not designed or directed at children.

## Changes

This file lives in the repository. Its history is the change log — every
revision is a commit, with a date and a diff.

## Contact

Through the project's repository. Whisper collects nothing, so there is no
"data protection request" address to write to; questions and bug reports go to
the same place.
