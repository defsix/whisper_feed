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
 * What one sync did: how many feeds were due, how many of those failed, and
 * the error that stopped the whole run, if one did.
 *
 * This was a Boolean, and false meant two unrelated things - the run broke,
 * or no feed was due because every one had been fetched minutes earlier. The
 * history then called a panel sync two minutes after a scheduled one
 * "failed", 0s, when it had simply had nothing to do.
 */
data class SyncResult(
    val due: Int,
    val failed: Int = 0,
    /** The kind of error that ended the run - a class name, never a message. */
    val error: String? = null,
    /**
     * False for an account sync, which says only whether it worked. Its line
     * then reads plain "ok" rather than a feed count made up to fill it.
     */
    val counted: Boolean = true,
) {
    /** Not broken. Nothing due is fine; some feeds failing is still a sync. */
    val ok: Boolean get() = error == null

    companion object {
        fun broken(error: Throwable): SyncResult = SyncResult(due = 0, error = errorKind(error))

        /** An account sync that worked; see [counted]. */
        val uncounted = SyncResult(due = 0, counted = false)
    }
}

/**
 * The name of what went wrong, safe for a report that is sent to the
 * developer: the class, with no message. A message can carry an address.
 */
fun errorKind(error: Throwable): String = error::class.java.simpleName.ifBlank { "error" }

/**
 * The history's outcome for a finished run.
 *
 * "ok" and "failed" still lead, so the lines read as they did; what follows
 * in brackets is what the Boolean could not say. [notes] are the worker's own
 * ("queued", "foreground"), kept at the end.
 */
fun syncOutcome(result: SyncResult, notes: List<String> = emptyList()): String {
    val (head, details) = when {
        result.error != null -> "failed: ${result.error}" to emptyList()
        !result.counted -> "ok" to emptyList()
        result.due == 0 -> "nothing due" to emptyList()
        else -> "ok" to listOfNotNull(
            "${result.due} feeds",
            if (result.failed > 0) "${result.failed} failed" else null,
        )
    }
    val all = details + notes
    return if (all.isEmpty()) head else "$head (${all.joinToString(", ")})"
}
