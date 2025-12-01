// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 github.com/shrx

package org.shrx.calalarm.data.calendar.models

/**
 * Data class representing a calendar event retrieved from the device's calendar database.
 *
 * This model corresponds to data from Android's CalendarContract.Events table.
 * Events are used to schedule alarms at their start time. All-day events are filtered out
 * during retrieval as they have no specific time to trigger an alarm.
 *
 * @property id Unique identifier for the event (from CalendarContract.Events._ID)
 * @property title Display name/summary of the event. Defaults to "Untitled Event" if null
 * @property startTime Event start time as Unix timestamp in milliseconds (alarm triggers at this time)
 * @property calendarId Calendar this event belongs to (from CalendarContract.Events.CALENDAR_ID)
 */
data class CalendarEvent(
    val id: Long,
    val title: String,
    val startTime: Long,
    val calendarId: Long
)
