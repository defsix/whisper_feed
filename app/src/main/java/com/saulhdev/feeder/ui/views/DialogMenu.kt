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

import android.annotation.SuppressLint
import android.content.res.Resources
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.UiThread
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.entity.MenuItem
import com.saulhdev.feeder.utils.PopupWindowImpl
import kotlin.math.ceil

@UiThread
class DialogMenu(private val d: View) {
    private var b: (() -> Unit)? = null
    private var popupWindowImpl: PopupWindowImpl? = null
    private fun a(): Boolean = this.popupWindowImpl != null

    private fun intToDp(dp: Int): Int {
        return ceil((dp * Resources.getSystem().displayMetrics.density).toDouble()).toInt()
    }

    fun d(view: View): Rect {
        val iArr = IntArray(2)
        view.getLocationOnScreen(iArr)
        return Rect(iArr[0], iArr[1], iArr[0] + view.measuredWidth, iArr[1] + view.measuredHeight)
    }

    @SuppressLint("InflateParams")
    fun show(
        list: List<MenuItem>,
        setListViewParams: ((Pair<View, MenuListView>) -> Unit)? = null,
        onClick: (MenuItem) -> Unit
    ) {
        if (!a()) {
            val context = this.d.context
            val inflate = LayoutInflater.from(context).inflate(R.layout.menu_list, null, false)
            val dialogActionsListView =
                inflate.findViewById<MenuListView>(R.id.dialog_actions_list_content)
            setListViewParams?.invoke(
                Pair(
                    inflate.findViewById(R.id.dialog_actions_list_container),
                    dialogActionsListView
                )
            )
            dialogActionsListView.setActions(list)
            dialogActionsListView.setActionClickListener(onClick)

            inflate.measure(
                View.MeasureSpec.makeMeasureSpec(
                    this.d.rootView.measuredWidth,
                    View.MeasureSpec.AT_MOST
                ),
                View.MeasureSpec.makeMeasureSpec(
                    this.d.rootView.measuredHeight - intToDp(64),
                    View.MeasureSpec.AT_MOST
                )
            )
            inflate.layout(0, 0, inflate.measuredWidth, inflate.measuredHeight)

            val d2 = d(this.d)
            val measuredWidth = (d2.right - inflate.measuredWidth) + intToDp(8)
            val b2 = d2.top - intToDp(8)
            val rect = Rect(
                measuredWidth,
                b2,
                inflate.measuredWidth + measuredWidth,
                inflate.measuredHeight + b2
            )

            popupWindowImpl = PopupWindowImpl(context).apply {
                contentView = inflate
                width = rect.width()
                height = rect.height()
                setOnDismissListener { popupWindowImpl = null }
                showAtLocation(d, 0, rect.left, rect.top)
            }
        }
    }

    fun dismiss() {
        if (a()) {
            b?.invoke()
            popupWindowImpl?.dismiss()
        }
    }
}