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
package com.saulhdev.feeder.manager.sync.greader

/**
 * The address to sign in to, from whatever the reader typed.
 *
 * Every other client in this app rewrites `http` to `https` on its way to the
 * socket, and this one deliberately does not. Not because upgrading a
 * credentialed address is dangerous — it is the same host either way, checked
 * against the system trust store, so nobody without a real certificate for
 * that name can be on the other end — but because a password is the wrong
 * thing to be quietly routed somewhere the reader did not agree to. They
 * should see what happened.
 *
 * So the correction is made here instead: at sign-in, in the field, before the
 * password is sent. The reader watches `http://` become `https://` and the
 * stored address is the corrected one, so it is right the next time too.
 *
 * Doing nothing was not an option worth keeping. Cleartext is refused at the
 * platform level, so an `http://` address never connects at all — it fails
 * with a network error that says nothing about the scheme, and the reader is
 * left to guess between a typo, a firewall, and a server that is down.
 *
 * A missing scheme is filled in for the same reason. `freshrss.example.com/…`
 * is what people paste out of their own browser's address bar.
 */
fun normalisedServerUrl(typed: String): String {
    val trimmed = typed.trim()
    if (trimmed.isEmpty()) return ""

    val scheme = trimmed.substringBefore("://", missingDelimiterValue = "")
    return when {
        scheme.equals("https", ignoreCase = true) ->
            "https://" + trimmed.substringAfter("://")

        scheme.equals("http", ignoreCase = true) ->
            "https://" + trimmed.substringAfter("://")

        // Anything else with a scheme is left exactly as typed. `ftp://` is
        // not a sync server and pretending it might be, by rewriting it into
        // one, would turn a clear mistake into a confusing one.
        scheme.isNotEmpty() -> trimmed

        else -> "https://$trimmed"
    }
}
