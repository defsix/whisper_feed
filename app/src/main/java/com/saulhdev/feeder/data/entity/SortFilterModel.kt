package com.saulhdev.feeder.data.entity

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SortFilterModel(
    val sort: String = SORT_CHRONOLOGICAL,
    val sortAsc: Boolean = false,
    val sourcesFilter: Set<String> = emptySet(),
    val tagsFilter: Set<String> = emptySet()
) : Parcelable

const val SORT_CHRONOLOGICAL = "chronological"
const val SORT_TITLE = "title"
const val SORT_SOURCE = "source"