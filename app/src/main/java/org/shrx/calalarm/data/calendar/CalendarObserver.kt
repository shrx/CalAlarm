// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 github.com/shrx

package org.shrx.calalarm.data.calendar

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract

/**
 * ContentObserver that monitors calendar changes in real-time.
 *
 * Watches CalendarContract.Events.CONTENT_URI for any changes to calendar events
 * and triggers a callback when changes are detected. Runs continuously for the
 * application lifetime; Android automatically cleans up when the process is killed.
 */
class CalendarObserver(
    private val context: Context,
    private val onCalendarChanged: () -> Unit
) : ContentObserver(Handler(Looper.getMainLooper())) {

    /**
     * Starts observing calendar changes by registering this ContentObserver
     * with the ContentResolver. Once started, the observer runs continuously
     * until the application process is terminated.
     */
    fun startObserving() {
        context.contentResolver.registerContentObserver(
            CalendarContract.Events.CONTENT_URI,
            true,  // notifyForDescendants - watch subdirectories too
            this
        )
    }

    /**
     * Called when calendar data changes.
     * Triggers the callback to initiate calendar sync.
     */
    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)
        onCalendarChanged()
    }
}
