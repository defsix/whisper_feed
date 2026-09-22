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

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * The characters an article's id may contain.
 *
 * Every id is a `UUID.randomUUID().toString()` — generated locally, never
 * taken from a feed, and not the publisher's guid, which is a string somebody
 * else wrote. That was traced through the sync and the Google Reader client
 * before this was added, and it holds today.
 *
 * It is enforced anyway, because the file name is built by interpolation and
 * nothing at that line said the id was safe to interpolate. An id containing
 * `../` would put an article's cached text outside `filesDir` — into the
 * databases directory, or over the shared preferences — and the invariant
 * that prevents it lives in a different file from the code that depends on
 * it. That is exactly the arrangement that stops being true without anyone
 * noticing.
 *
 * Hyphens and word characters cover a UUID with room to spare. Anything else
 * is a mistake worth hearing about rather than a case worth handling.
 */
private val SAFE_ID = Regex("[A-Za-z0-9_-]+")

/**
 * The id, checked before it becomes part of a path.
 *
 * Throws rather than sanitising. Stripping the offending characters would
 * turn two different ids into one file, so an article could silently be
 * served another article's text — which is a worse bug than the one being
 * prevented, and one this app has already had for different reasons.
 */
private fun safeId(itemId: String): String {
    require(SAFE_ID.matches(itemId)) { "Unsafe article id for a file name" }
    return itemId
}

fun blobFile(itemId: String, filesDir: File): File =
    File(filesDir, "${safeId(itemId)}.txt.gz")

@Throws(IOException::class)
fun blobInputStream(itemId: String, filesDir: File): InputStream =
    GZIPInputStream(blobFile(itemId = itemId, filesDir = filesDir).inputStream())

@Throws(IOException::class)
fun blobOutputStream(itemId: String, filesDir: File): OutputStream =
    GZIPOutputStream(blobFile(itemId = itemId, filesDir = filesDir).outputStream())

fun blobFullFile(itemId: String, filesDir: File): File =
    File(filesDir, "${safeId(itemId)}.full.html.gz")

@Throws(IOException::class)
fun blobFullInputStream(itemId: String, filesDir: File): InputStream =
    GZIPInputStream(blobFullFile(itemId = itemId, filesDir = filesDir).inputStream())

@Throws(IOException::class)
fun blobFullOutputStream(itemId: String, filesDir: File): OutputStream =
    GZIPOutputStream(blobFullFile(itemId = itemId, filesDir = filesDir).outputStream())
