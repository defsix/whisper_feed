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
package com.saulhdev.feeder.viewmodels

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.saulhdev.feeder.manager.sync.service.toSyncResult
import com.saulhdev.feeder.utils.SyncLog
import com.saulhdev.feeder.utils.bytesSince
import com.saulhdev.feeder.utils.receivedBytes
import com.saulhdev.feeder.utils.syncOutcome
import kotlin.coroutines.cancellation.CancellationException
import com.saulhdev.feeder.data.content.SyncAccount
import com.saulhdev.feeder.manager.sync.greader.GoogleReaderApi
import com.saulhdev.feeder.manager.sync.service.RssServiceDispatcher
import com.saulhdev.feeder.manager.sync.service.SyncOutcome
import com.saulhdev.feeder.utils.extensions.NeoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import com.saulhdev.feeder.manager.sync.greader.AccountProblem
import com.saulhdev.feeder.manager.sync.greader.problemFrom
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

/** What the account screen is doing, and what it has to say about it. */
data class AccountState(
    val signedIn: Boolean = false,
    val serverUrl: String = "",
    val username: String = "",
    val lastSync: Long = 0L,
    val busy: Boolean = false,
    /**
     * Set when something went wrong, cleared when the reader changes anything.
     *
     * A problem rather than a sentence, so the wording and its translations
     * stay on the screen that shows them. [detail] is whatever the server or
     * the exception said, kept for the cases with nothing better to offer.
     */
    val error: AccountProblem? = null,
    val detail: String? = null,
)

class AccountViewModel(
    /** The application, never a screen: this outlives the account screen's own. */
    private val app: Application,
    private val account: SyncAccount,
    private val dispatcher: RssServiceDispatcher,
) : NeoViewModel() {
    private val ioScope = viewModelScope.plus(Dispatchers.IO)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<AccountState> = _state.asStateFlow()

    private fun read() = AccountState(
        signedIn = account.isSignedIn,
        serverUrl = account.serverUrl,
        username = account.username,
        lastSync = account.lastSync,
    )

    fun clearError() {
        _state.value = _state.value.copy(error = null, detail = null)
    }

    /**
     * Signs in, then immediately syncs.
     *
     * One action rather than two: somebody who has just typed a server address
     * and a password wants their feeds, and a screen that accepts the
     * credential and then sits there is asking them to guess what happens next.
     */
    fun signIn(server: String, username: String, password: String) {
        _state.value = _state.value.copy(busy = true, error = null)
        ioScope.launch {
            when (val result = GoogleReaderApi(server).signIn(username, password)) {
                is GoogleReaderApi.AuthResult.Success -> {
                    account.signIn(server, username, result.token)
                    _state.value = read().copy(busy = true)
                    syncNow()
                }

                is GoogleReaderApi.AuthResult.Failed ->
                    _state.value = _state.value.copy(
                        busy = false,
                        error = result.problem,
                        detail = result.detail,
                    )
            }
        }
    }

    fun signOut() {
        account.signOut()
        _state.value = read()
    }

    fun syncNow() {
        _state.value = _state.value.copy(busy = true, error = null)
        ioScope.launch {
            // Written to the sync history like every other sync. It ran
            // straight from this screen rather than through the worker, and
            // so left no line: two syncs on the night the account was first
            // tried were visible only in what they had fetched.
            val run = SyncLog.started(app, SyncLog.ORIGIN_ACCOUNT)
            val receivedBefore = receivedBytes()
            // "Sync now" is a request, so everything is fetched.
            val outcome = try {
                dispatcher.current().sync(forceNetwork = true)
            } catch (e: CancellationException) {
                SyncLog.finished(app, run, "stopped: left the screen")
                throw e
            }
            SyncLog.finished(
                app,
                run,
                syncOutcome(outcome.toSyncResult().copy(bytes = bytesSince(receivedBefore))),
            )
            _state.value = when (outcome) {
                is SyncOutcome.Success -> read()
                SyncOutcome.SignedOut -> {
                    account.signOut()
                    read().copy(error = AccountProblem.SIGNED_OUT)
                }

                // The same translation as a failed sign-in, because it is the
                // same set of causes: a certificate, a name, a port, a server
                // that has gone away since the credential was stored.
                is SyncOutcome.Failed -> read().copy(
                    error = problemFrom(outcome.cause),
                    detail = outcome.cause?.message,
                )
            }
        }
    }
}
