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
package com.saulhdev.feeder.data.content

import com.saulhdev.feeder.manager.sync.greader.AccountTallyStore
import com.saulhdev.feeder.manager.sync.greader.GoogleReaderState
import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * The one sync account, if there is one.
 *
 * **One at a time, deliberately.** Multiple accounts would mean an `accountId`
 * on every feed and article, a migration, and a decision about what the feed
 * shows when two accounts disagree — for a case almost nobody has. One account
 * means the local database simply *is* that account's, and every sync is a
 * reconciliation between it and the server. If two ever turn out to be wanted,
 * that is a schema change made deliberately rather than one carried from the
 * start.
 *
 * Kept out of DataStore, which every other preference uses, because this holds
 * a credential. `EncryptedSharedPreferences` puts it behind a key in the
 * Android keystore, so it is not readable from a backup or an unlocked
 * bootloader in the way a plain preferences file is. The rest of the app's
 * settings are not secrets and do not need this.
 *
 * The token rather than the password is stored where the server allows it —
 * ClientLogin returns one that can be revoked server-side, which a password
 * cannot.
 */
class SyncAccount(context: Context) {

    private val appContext = context.applicationContext

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "whisper_account",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    /** Where the account lives — a FreshRSS instance, Miniflux, Inoreader. */
    var serverUrl: String
        get() = prefs.getString(KEY_SERVER, "").orEmpty()
        private set(value) = prefs.edit().putString(KEY_SERVER, value).apply()

    var username: String
        get() = prefs.getString(KEY_USER, "").orEmpty()
        private set(value) = prefs.edit().putString(KEY_USER, value).apply()

    /** The ClientLogin token. Revocable server-side, unlike a password. */
    var authToken: String
        get() = prefs.getString(KEY_TOKEN, "").orEmpty()
        private set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    /** When the last successful sync finished, for the status line. */
    var lastSync: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC, value).apply()

    val isSignedIn: Boolean get() = authToken.isNotEmpty() && serverUrl.isNotEmpty()

    fun signIn(server: String, user: String, token: String) {
        serverUrl = server.trimEnd('/')
        username = user
        authToken = token
        lastSync = 0L
        // Another account's memory of which feeds it had would decide what
        // happens to this one's. Starts empty, which is a first sync.
        GoogleReaderState.clear(appContext)
        AccountTallyStore.clear(appContext)
    }

    /**
     * Forgets the account. Subscriptions and articles stay.
     *
     * Signing out is not "throw away my reading" — the local database is the
     * source of truth and works perfectly well without a server, which is the
     * whole point of the local-first arrangement. Someone who wants the
     * articles gone can remove the sources.
     */
    fun signOut() {
        prefs.edit().clear().apply()
        GoogleReaderState.clear(appContext)
        AccountTallyStore.clear(appContext)
    }

    private companion object {
        const val KEY_SERVER = "server_url"
        const val KEY_USER = "username"
        const val KEY_TOKEN = "auth_token"
        const val KEY_LAST_SYNC = "last_sync"
    }
}
