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

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Volume keys as a page-turner, for reading one-handed.
 *
 * The alternative asked for was a wrist flick, read off the accelerometer.
 * That wants a sensor listening the whole time the feed is open, which is the
 * opposite of what the battery work is for, and it cannot tell a deliberate
 * flick from taking the phone out of a pocket — every false positive moves the
 * page under the reader. The volume keys are already under the thumb, cost
 * nothing when unused, and are the convention e-readers settled on decades ago.
 *
 * Off by default: taking over the volume keys is a surprising thing for an app
 * to do, and it should be the reader's decision.
 *
 * A process-wide channel rather than a parameter, because the key arrives at
 * the Activity and the thing that scrolls is several composables deep, with no
 * useful relationship between them to thread it through.
 *
 * **Only works in the app.** The launcher overlay is a window owned by the
 * launcher's process, so key events never reach us there — nothing to fix,
 * that is what an overlay is.
 */
object VolumeScroll {

    /** +1 for down the feed, -1 for back up it. */
    private val _events = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    /** How much of the screen one press moves, leaving an overlap to read across. */
    const val PAGE_FRACTION = 0.85f

    fun scroll(direction: Int) {
        _events.tryEmit(direction)
    }
}
