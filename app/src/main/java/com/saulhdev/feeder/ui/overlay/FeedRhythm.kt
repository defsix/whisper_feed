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
package com.saulhdev.feeder.ui.overlay

import com.saulhdev.feeder.utils.LAYOUT_CARDS
import com.saulhdev.feeder.utils.LAYOUT_LIST
import com.saulhdev.feeder.utils.LAYOUT_MAGAZINE
import com.saulhdev.feeder.utils.LAYOUT_MOSAIC

/** The shapes an article can take in the feed. */
enum class FeedCardShape {
    /** Full-bleed image with the headline laid over it. Anchors the feed. */
    Hero,

    /** Image above, headline and summary below. The relaxed middle weight. */
    Card,

    /** Headline left, thumbnail right. Dense, quick to scan. */
    Compact,

    /** Headline and source only. No image at any width. */
    Text,

    /** A grid tile: image on top, headline beneath, sized by its content. */
    Tile,
}

/**
 * Picks a shape for the article at [index], for the chosen [layout].
 *
 * The four layouts differ in what they are *for*, not in decoration:
 *
 *  - **Cards** varies the weight of consecutive items. A feed of identical
 *    cards reads as one undifferentiated column and gives the eye nothing to
 *    catch on, which is the thing a scrolling news surface most has to avoid.
 *    A hero opens, another lands every eighth item to reset the rhythm, a full
 *    card sits at a regular offset between, compact rows fill the rest.
 *  - **Magazine** commits to the image on every item. Fewer articles per
 *    screen, each one worth stopping at — for reading rather than triage.
 *  - **List** drops images entirely. Most articles per screen, for getting
 *    through a backlog.
 *  - **Mosaic** is a staggered grid of three tile sizes. Leaving it to the
 *    images' own aspect ratios was not enough variation in practice: news
 *    photography is overwhelmingly 16:9, so every tile came out the same
 *    height and the grid read as a table. The sizes are imposed the same way
 *    Cards imposes its rhythm, with the largest crossing both columns.
 *
 * The Cards pattern is positional rather than content-derived on purpose.
 * Deriving it from the article — image size, title length, source — would make
 * the layout jump around as a sync reorders the list, and an article would
 * change shape depending on what happened to load near it.
 *
 * In every layout except List, an article with no usable image falls back to a
 * shape that does not need one: the image-led shapes collapse into an oddly
 * padded blank without one.
 */
fun feedCardShape(
    index: Int,
    hasImage: Boolean,
    layout: String = LAYOUT_CARDS,
): FeedCardShape = when (layout) {
    LAYOUT_LIST -> FeedCardShape.Text

    // Tile even without an image: the tile already omits it, and a full-width
    // text row dropped into a half-width grid cell reads as a broken tile
    // rather than a deliberate one.
    LAYOUT_MOSAIC -> FeedCardShape.Tile

    LAYOUT_MAGAZINE -> if (hasImage) FeedCardShape.Card else FeedCardShape.Compact

    else -> when {
        !hasImage      -> FeedCardShape.Compact
        index == 0     -> FeedCardShape.Hero
        index % 8 == 0 -> FeedCardShape.Hero
        index % 4 == 2 -> FeedCardShape.Card
        else           -> FeedCardShape.Compact
    }
}

/** How much room a Mosaic tile takes. */
enum class MosaicTileSize {
    /** A short tile: image cropped wide, two lines of headline. */
    Small,

    /** One column, the image at its own aspect ratio. The default weight. */
    Medium,

    /** Both columns, with a summary. The thing the eye lands on. */
    Large,
}

/**
 * The size for the Mosaic tile at [index].
 *
 * Positional for the same reason the Cards rhythm is: a size derived from the
 * article would change as a sync reorders the feed, and the same article would
 * be large or small depending on what loaded near it.
 *
 * A large tile needs a picture to be worth the width it takes, so an article
 * without one is never large — it takes the small shape instead, which is the
 * one that does not pretend to have an image.
 */
fun mosaicTileSize(index: Int, hasImage: Boolean): MosaicTileSize = when {
    !hasImage         -> MosaicTileSize.Small
    index == 0        -> MosaicTileSize.Large
    index % 11 == 0   -> MosaicTileSize.Large
    index % 3 == 1    -> MosaicTileSize.Small
    else              -> MosaicTileSize.Medium
}

/**
 * Whether the tile at [index] crosses both columns.
 *
 * The grid needs this before it composes the item — a span is a property of the
 * slot, not of what goes in it — so it is asked separately rather than read
 * back off the tile.
 */
fun mosaicSpansFullLine(index: Int, hasImage: Boolean): Boolean =
    mosaicTileSize(index, hasImage) == MosaicTileSize.Large

/**
 * Whether a layout wants a staggered grid rather than a single column.
 *
 * Kept here beside the shape rules so the container and the shape it holds
 * cannot disagree — a Tile in a LazyColumn is a full-width card with a
 * grid tile's proportions, which looks like a mistake rather than a choice.
 */
fun feedLayoutIsGrid(layout: String): Boolean = layout == LAYOUT_MOSAIC
