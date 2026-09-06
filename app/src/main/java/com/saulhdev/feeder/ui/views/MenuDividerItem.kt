/*
 * This file is part of Neo Feed
 * Copyright (c) 2024   NeoApplications Team
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

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.saulhdev.feeder.data.entity.DividerStyle

class MenuDividerItem(private val style: DividerStyle) : RecyclerView.ItemDecoration() {
    private val f2974a = Paint()
    private val b = Rect()

    init {
        this.f2974a.color = style.dividerColor
        this.f2974a.isAntiAlias = false
        this.f2974a.isDither = false
    }

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        if (a(parent, view)) {
            outRect.bottom = style.dividerHeight
        }
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        super.onDraw(c, parent, state)
        if (parent.childCount != 0) {
            val paddingLeft = parent.paddingLeft
            val measuredWidth = parent.measuredWidth - parent.paddingRight

            for (i in 0 until parent.childCount) {
                val childAt = parent.getChildAt(i)
                if (a(parent, childAt)) {
                    val bottom =
                        childAt.bottom + ((this.style.dividerHeight / 2) - (this.style.dividerSize / 2))
                    this.b.set(paddingLeft, bottom, measuredWidth, this.style.dividerSize + bottom)
                    c.drawRect(this.b, this.f2974a)
                }
            }
        }
    }

    companion object {
        fun a(recyclerView: RecyclerView, view: View): Boolean {
            val cap = recyclerView.getChildAdapterPosition(view)
            if (cap == -1) return false
            val adapter = recyclerView.adapter as MenuAdapter
            if (cap == adapter.itemCount - 1) return false
            if (adapter.getAdapterItem(cap).group != adapter.getAdapterItem(cap + 1).group) return true
            return false
        }
    }
}