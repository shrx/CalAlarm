// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 github.com/shrx

package org.shrx.calalarm.data.calendar.models

/**
 * Data class representing metadata for a calendar available on the device.
 *
 * This model corresponds to data from Android's CalendarContract.Calendars table.
 * Used to display available calendars to the user for selection in settings.
 *
 * @property id Unique identifier for the calendar (from CalendarContract.Calendars._ID)
 * @property displayName User-visible name of the calendar (e.g., "Work Calendar", "Personal")
 * @property accountName Account this calendar belongs to (e.g., "user@gmail.com")
 */
data class CalendarInfo(
    val id: Long,
    val displayName: String,
    val accountName: String
)
