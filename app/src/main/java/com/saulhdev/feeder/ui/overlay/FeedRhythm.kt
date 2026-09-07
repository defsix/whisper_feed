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

/** The three shapes an article can take in the feed. */
enum class FeedCardShape {
    /** Full-bleed image with the headline laid over it. Anchors the feed. */
    Hero,

    /** Image above, headline and summary below. The relaxed middle weight. */
    Card,

    /** Headline left, thumbnail right. Dense, quick to scan. */
    Compact,
}

/**
 * Picks a shape for the article at [index].
 *
 * A feed of identical cards reads as one undifferentiated column and gives the
 * eye nothing to catch on, which is the thing a scrolling news surface most has
 * to avoid. Discover solves it by varying the weight of consecutive items, so
 * this does the same: a hero to open, another every eighth item to reset the
 * rhythm, a full card at a regular offset in between, and compact rows for the
 * rest.
 *
 * The pattern is positional rather than content-derived on purpose. Deriving it
 * from the article — image size, title length, source — would make the layout
 * jump around as a sync reorders the list, and an article would change shape
 * depending on what happened to load near it.
 *
 * An article with no usable image can only ever be [FeedCardShape.Compact]: the
 * other two shapes are built around an image and collapse into an oddly padded
 * blank without one.
 */
fun feedCardShape(index: Int, hasImage: Boolean): FeedCardShape = when {
    !hasImage       -> FeedCardShape.Compact
    index == 0      -> FeedCardShape.Hero
    index % 8 == 0  -> FeedCardShape.Hero
    index % 4 == 2  -> FeedCardShape.Card
    else            -> FeedCardShape.Compact
}
