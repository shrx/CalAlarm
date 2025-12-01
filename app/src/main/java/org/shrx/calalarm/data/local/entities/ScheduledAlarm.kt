// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 github.com/shrx

package org.shrx.calalarm.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a scheduled alarm for a calendar event.
 *
 * @property eventId Calendar event ID (from CalendarContract.Events._ID)
 * @property eventTitle Event title
 * @property eventStartTime Event start time as Unix timestamp in milliseconds
 * @property calendarId Calendar ID (from CalendarContract.Events.CALENDAR_ID)
 * @property snoozeOffset Snooze offset in milliseconds to add to eventStartTime (0 if not snoozed)
 */
@Entity(tableName = "scheduled_alarms")
data class ScheduledAlarm(
    @PrimaryKey val eventId: Long,
    val eventTitle: String,
    val eventStartTime: Long,
    val calendarId: Long,
    val snoozeOffset: Long = 0
)

/**
 * Room entity tracking calendar events for which the user has manually disabled alarms.
 *
 * @property eventId Calendar event ID that the user has chosen not to alarm for
 */
@Entity(tableName = "disabled_event_ids")
data class DisabledEventId(
    @PrimaryKey val eventId: Long
)
