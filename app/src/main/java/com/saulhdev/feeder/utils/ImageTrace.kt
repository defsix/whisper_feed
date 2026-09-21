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

import coil.EventListener
import coil.decode.DataSource
import coil.decode.DecodeResult
import coil.decode.Decoder
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.ImageRequest
import coil.request.Options
import coil.request.SuccessResult

/**
 * Times image decodes and records what format they were.
 *
 * A scroll's logcat showed six `c2.android.av1-dav1d.decoder` instances being
 * created — a full MediaCodec each, with its own native buffer pools. Nothing
 * in this app plays video. They are AVIF images: Android decodes AVIF and HEIF
 * through MediaCodec rather than through the ordinary bitmap path, so a
 * publisher serving AVIF silently turns each picture into a codec
 * instantiation.
 *
 * Worse than it first looks, and the codec's name is the reason. `c2.android.*`
 * are the AOSP *software* codecs; a hardware AV1 decoder would carry the
 * vendor's prefix. So these frames are being decoded on the CPU, one image at
 * a time, on a phone that has a hardware decoder it is not being handed.
 *
 * Which leaves a question worth answering before anything is changed: whether
 * it costs enough to matter. The obvious remedy is to stop advertising AVIF in
 * the Accept header, so a content-negotiating CDN sends WebP or JPEG instead —
 * but that trades a smaller download for a cheaper decode, and which way that
 * trade falls depends on numbers nobody has yet. Six decoder instantiations in
 * a minute might be forty milliseconds each or four.
 *
 * So this measures it: how many images were served, where from, how many
 * needed decoding at all, and how long each format took. The cache figures
 * come along for free and answer the other open question — whether the memory
 * cache is big enough, or whether scrolling back re-decodes everything.
 *
 * One instance per request, which is what makes the timing simple: the start
 * time is an ordinary field rather than an entry in a map that has to be
 * cleaned up on every path a request can fail down.
 *
 * **Formats and durations only, never addresses.** A URL names a publisher and
 * often an article; these lines go into a file the reader sends to a stranger.
 * The mime type is the whole of what is needed.
 */
class ImageTrace private constructor() : EventListener {

    private var decodeStartedAt = 0L
    private var mimeType: String? = null

    override fun fetchEnd(
        request: ImageRequest,
        fetcher: Fetcher,
        options: Options,
        result: FetchResult?,
    ) {
        mimeType = (result as? SourceResult)?.mimeType
    }

    override fun decodeStart(request: ImageRequest, decoder: Decoder, options: Options) {
        decodeStartedAt = System.nanoTime()
    }

    override fun decodeEnd(
        request: ImageRequest,
        decoder: Decoder,
        options: Options,
        result: DecodeResult?,
    ) {
        if (decodeStartedAt == 0L) return
        FeedTrace.imageDecoded(
            format = shortFormat(mimeType),
            micros = (System.nanoTime() - decodeStartedAt) / 1_000,
        )
        decodeStartedAt = 0L
    }

    /**
     * Where the picture came from, decoded or not.
     *
     * A memory-cache hit never reaches [decodeEnd], so counting only decodes
     * would say nothing about how often one was avoided — which is the whole
     * question about the cache's size.
     */
    override fun onSuccess(request: ImageRequest, result: SuccessResult) {
        FeedTrace.imageServed(result.dataSource)
    }

    object Factory : EventListener.Factory {
        override fun create(request: ImageRequest): EventListener = ImageTrace()
    }

    companion object {
        /**
         * The part of a mime type worth counting.
         *
         * `image/avif` is the one this exists for, but bucketing everything
         * keeps the comparison honest: "avif is slow" means nothing without
         * the jpeg figure from the same scroll beside it.
         */
        internal fun shortFormat(mimeType: String?): String = when {
            mimeType.isNullOrBlank() -> "unknown"
            else -> mimeType.substringAfter('/', mimeType)
                .substringBefore(';')
                .trim()
                .lowercase()
                .removePrefix("x-")
                .ifEmpty { "unknown" }
        }
    }
}

/** Short names for [DataSource], for the trace line. */
internal fun DataSource.shortName(): String = when (this) {
    DataSource.MEMORY_CACHE, DataSource.MEMORY -> "mem"
    DataSource.DISK -> "disk"
    DataSource.NETWORK -> "net"
}
