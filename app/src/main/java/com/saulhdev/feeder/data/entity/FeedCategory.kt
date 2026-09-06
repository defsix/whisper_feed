package com.saulhdev.feeder.data.entity

import android.os.Parcelable
import androidx.annotation.ColorInt
import kotlinx.parcelize.Parcelize

@Parcelize
data class FeedCategory(
    val id: String,
    val title: String,
    @ColorInt val categoryColor: Int,
    val serverIcon: String?
) : Parcelable {
    override fun describeContents(): Int {
        return 0
    }
}