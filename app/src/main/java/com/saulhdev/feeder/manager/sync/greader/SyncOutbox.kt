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

import android.content.Context
import androidx.core.content.edit

/**
 * Changes made in Whisper that the server has not been told about yet.
 *
 * Reads, unreads, saves and unsaves, by local article id. Collected as they
 * happen and sent at the next sync, so a change made with no connection is
 * not lost, and one undone before the sync cancels out rather than going out
 * twice.
 *
 * The opposite change replaces the first: reading and then undoing leaves
 * nothing to send, which is what the reader did.
 */
data class Outbox(
    val read: Set<String> = emptySet(),
    val unread: Set<String> = emptySet(),
    val star: Set<String> = emptySet(),
    val unstar: Set<String> = emptySet(),
) {
    fun withRead(ids: Collection<String>, read: Boolean): Outbox =
        if (read) copy(read = this.read + ids, unread = unread - ids.toSet())
        else copy(read = this.read - ids.toSet(), unread = unread + ids)

    fun withStar(id: String, starred: Boolean): Outbox =
        if (starred) copy(star = star + id, unstar = unstar - id)
        else copy(star = star - id, unstar = unstar + id)

    /** Everything waiting, by local id. */
    val pending: Set<String> get() = read + unread + star + unstar

    val isEmpty: Boolean get() = pending.isEmpty()

    /** What is left once [sent] has gone out. */
    fun without(sent: Outbox): Outbox = Outbox(
        read = read - sent.read,
        unread = unread - sent.unread,
        star = star - sent.star,
        unstar = unstar - sent.unstar,
    )
}

/**
 * Which feeds to subscribe, unsubscribe and add, from the two lists and what
 * was known the last time they were compared.
 *
 * Keys are normalised feed addresses. The two memories are what make this a
 * sync rather than a copy:
 *
 * - [lastLocal]: Whisper's feeds at the end of the last sync. A feed the
 *   server has and Whisper does not was either removed here since, and goes
 *   from the server too, or is new on the server, and comes here.
 * - [everOnServer]: every feed the server has been seen with. A feed Whisper
 *   has and the server does not was either removed on the server, and is left
 *   alone here - removals from the server are never applied, because a partial
 *   answer or the wrong account would take somebody's feeds - or is new here,
 *   and goes to the server.
 *
 * The first sync has no memory, so everything each side lacks goes across:
 * signing in on a phone that already has a hundred feeds puts them on the
 * server, with no OPML file to carry by hand.
 */
data class SubscriptionPlan(
    val subscribe: Set<String>,
    val unsubscribe: Set<String>,
    val addLocal: Set<String>,
)

fun planSubscriptions(
    local: Set<String>,
    server: Set<String>,
    lastLocal: Set<String>,
    everOnServer: Set<String>,
): SubscriptionPlan = SubscriptionPlan(
    subscribe = (local - server) - everOnServer,
    unsubscribe = (server - local) intersect lastLocal,
    addLocal = (server - local) - lastLocal,
)

/**
 * What the memories become once a plan has been carried out.
 *
 * A feed that left Whisper is forgotten as having been on the server, so that
 * adding it back later sends it again rather than being taken for one the
 * server dropped. One whose removal from the server did not go through is
 * still remembered as Whisper's, so the removal is tried again next time
 * rather than the feed being taken for new on the server and added back.
 */
fun rememberAfter(
    plan: SubscriptionPlan,
    local: Set<String>,
    server: Set<String>,
    lastLocal: Set<String>,
    everOnServer: Set<String>,
    subscribed: Set<String>,
    unsubscribed: Set<String>,
): Pair<Set<String>, Set<String>> {
    val nowLocal = local + plan.addLocal + (plan.unsubscribe - unsubscribed)
    val leftLocally = lastLocal - local - (plan.unsubscribe - unsubscribed)
    val ever = (everOnServer + server + subscribed) - unsubscribed - leftLocally
    return nowLocal to ever
}

/**
 * What the server's unread list changes here: (to mark read, to mark unread).
 *
 * Over articles the server has claimed only, and never one still waiting in
 * the outbox. "Read" only from a [complete] list, and never from an empty
 * one; see GoogleReaderService.pullReadState.
 */
fun readChanges(
    mapped: List<com.saulhdev.feeder.data.db.models.MappedArticle>,
    serverUnread: Set<String>,
    complete: Boolean,
    waiting: Set<String>,
): Pair<List<String>, List<String>> {
    val settled = mapped.filter { it.uuid !in waiting }
    val toUnread = settled.filter { it.readAt != 0L && it.remoteId in serverUnread }.map { it.uuid }
    val toRead = if (complete && serverUnread.isNotEmpty()) {
        settled.filter { it.readAt == 0L && it.remoteId !in serverUnread }.map { it.uuid }
    } else {
        emptyList()
    }
    return toRead to toUnread
}

/**
 * The account's sync state on this phone: the outbox, the two subscription
 * memories and when articles were last matched to the server's.
 *
 * Cleared on signing in and out, so one account's memory never decides what
 * happens to another's feeds.
 */
object GoogleReaderState {
    private const val FILE = "greader_state"
    private const val READ = "read"
    private const val UNREAD = "unread"
    private const val STAR = "star"
    private const val UNSTAR = "unstar"
    private const val LAST_LOCAL = "last_local"
    private const val EVER_ON_SERVER = "ever_on_server"
    private const val MAPPED_AT = "mapped_at"

    private val lock = Any()

    fun outbox(context: Context): Outbox = with(prefs(context)) {
        Outbox(
            read = getStringSet(READ, null).orEmpty().toSet(),
            unread = getStringSet(UNREAD, null).orEmpty().toSet(),
            star = getStringSet(STAR, null).orEmpty().toSet(),
            unstar = getStringSet(UNSTAR, null).orEmpty().toSet(),
        )
    }

    fun updateOutbox(context: Context, change: (Outbox) -> Outbox) {
        runCatching {
            synchronized(lock) {
                val next = change(outbox(context))
                prefs(context).edit {
                    putStringSet(READ, next.read)
                    putStringSet(UNREAD, next.unread)
                    putStringSet(STAR, next.star)
                    putStringSet(UNSTAR, next.unstar)
                }
            }
        }
    }

    fun lastLocal(context: Context): Set<String> =
        prefs(context).getStringSet(LAST_LOCAL, null).orEmpty().toSet()

    fun everOnServer(context: Context): Set<String> =
        prefs(context).getStringSet(EVER_ON_SERVER, null).orEmpty().toSet()

    fun rememberSubscriptions(context: Context, lastLocal: Set<String>, everOnServer: Set<String>) {
        prefs(context).edit {
            putStringSet(LAST_LOCAL, lastLocal)
            putStringSet(EVER_ON_SERVER, everOnServer)
        }
    }

    /** When articles were last matched to the server's, or 0 for never. */
    fun mappedAt(context: Context): Long = prefs(context).getLong(MAPPED_AT, 0L)

    fun setMappedAt(context: Context, at: Long) {
        prefs(context).edit { putLong(MAPPED_AT, at) }
    }

    fun clear(context: Context) {
        runCatching { synchronized(lock) { prefs(context).edit { clear() } } }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
