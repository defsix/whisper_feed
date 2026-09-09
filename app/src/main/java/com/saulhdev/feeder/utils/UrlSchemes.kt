/*
 * This file is part of Whisper
 * Copyright (c) 2026   Whisper contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.saulhdev.feeder.utils

/**
 * What scheme an address names, decided without `android.net.Uri`.
 *
 * Two reasons for not using the platform parser. It is a stub in a unit test —
 * every method throws "not mocked" — so a check written with it can only be
 * verified on a device, and these two checks are the ones standing between a
 * feed's contents and an intent leaving the app. And `Uri.parse` is lenient
 * where this needs to be strict: it will hand back a scheme for strings no
 * caller should be treating as addressable at all.
 *
 * RFC 3986: a scheme is a letter followed by letters, digits, `+`, `-` or `.`,
 * up to the first colon. Anything else — a leading space, a leading digit, no
 * colon, a colon inside a path — has no scheme, and is refused rather than
 * guessed at.
 */
fun schemeOf(url: String): String? {
    val colon = url.indexOf(':')
    if (colon <= 0) return null
    val scheme = url.substring(0, colon)
    if (!scheme[0].isLetter()) return null
    if (!scheme.all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' }) return null
    return scheme.lowercase()
}

/**
 * Schemes an address reached from content may be opened with.
 *
 * Every caller is handing over a string somebody else wrote: an `<a href>`
 * inside an article body, a link on a page in the in-app browser, a URL out of
 * a feed. Firing ACTION_VIEW at whatever they put there makes the app a
 * general-purpose launcher for other people's URIs — a `content://` pointed at
 * a provider, a `file://` inside the app's own storage, an `android-app://`
 * aimed at another app's deep links.
 *
 * An allowlist rather than a list of things to block, because the interesting
 * schemes are always the ones nobody thought of.
 */
private val VIEWABLE_SCHEMES = setOf(
    "http", "https", "mailto", "tel", "sms", "geo", "market",
)

/** Whether [launchView][com.saulhdev.feeder.utils.extensions.launchView] will open this. */
fun isViewable(url: String): Boolean = schemeOf(url) in VIEWABLE_SCHEMES

/**
 * Whether the in-app browser will load an address.
 *
 * Only http and https, which is stricter than [isViewable] on purpose: those
 * open in whichever app handles them, this one opens *inside Whisper*, in a
 * WebView with JavaScript enabled. The screen is reachable by an explicit
 * intent from any app on the device — MainActivity is exported, as a launcher
 * activity must be, and `nf-navigation://androidx.navigation/webview/{url}` is
 * a registered deep link — so the address arriving there is not necessarily
 * one of ours.
 */
fun isBrowsable(url: String): Boolean = schemeOf(url).let { it == "http" || it == "https" }
