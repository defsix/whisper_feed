/*
 * This file is part of Neo Feed
 * Copyright (c) 2025   NeoApplications Team
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

package com.saulhdev.feeder.ui.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.cardview.widget.CardView
import com.google.android.material.textview.MaterialTextView
import com.saulhdev.feeder.R

class ExpandableCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : CardView(context, attrs, defStyleAttr) {

    private val headerLayout: LinearLayout
    private val iconImageView: ImageView
    private val titleTextView: MaterialTextView
    private val expandIcon: ImageView
    private val contentContainer: FrameLayout
    private var isExpanded: Boolean = false

    init {
        LayoutInflater.from(context).inflate(R.layout.card_expandable, this, true)

        headerLayout = findViewById(R.id.headerLayout)
        iconImageView = findViewById(R.id.iconImageView)
        titleTextView = findViewById(R.id.titleTextView)
        expandIcon = findViewById(R.id.expandIcon)
        contentContainer = findViewById(R.id.contentContainer)

        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.ExpandableCardView,
            defStyleAttr,
            0
        ).apply {
            try {
                val iconResId = getResourceId(R.styleable.ExpandableCardView_sectionIcon, 0)
                if (iconResId != 0) {
                    iconImageView.setImageResource(iconResId)
                }
                val title = getString(R.styleable.ExpandableCardView_sectionTitle)
                titleTextView.text = title
                val iconContentDesc =
                    getString(R.styleable.ExpandableCardView_iconContentDescription)
                if (!iconContentDesc.isNullOrEmpty()) {
                    iconImageView.contentDescription = iconContentDesc
                }
            } finally {
                recycle()
            }
        }

        headerLayout.setOnClickListener {
            toggleExpansion()
        }
    }

    fun setContentView(contentView: View) {
        contentContainer.removeAllViews()
        contentContainer.addView(contentView)
    }

    private fun toggleExpansion() {
        isExpanded = !isExpanded
        contentContainer.visibility = if (isExpanded) VISIBLE else GONE
        expandIcon.setImageResource(
            if (isExpanded) R.drawable.ic_caret_up else R.drawable.ic_caret_down
        )
        expandIcon.contentDescription = context.getString(
            if (isExpanded) R.string.collapse_section_description
            else R.string.expand_section_description
        )
    }
}