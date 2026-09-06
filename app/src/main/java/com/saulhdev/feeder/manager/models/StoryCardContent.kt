/*
 * This file is part of Neo Feed
 * Copyright (c) 2025   Neo Feed Team
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

package com.saulhdev.feeder.manager.models

import android.os.Parcelable
import com.saulhdev.feeder.data.entity.FeedCategory
import kotlinx.parcelize.Parcelize

@Parcelize
data class StoryCardContent(
    val title: String,
    val text: String,
    val tag: String = "",
    val backgroundUrl: String,
    val link: String,
    val source: FeedCategory
) : Parcelable {
    override fun describeContents(): Int {
        return 0
    }
}