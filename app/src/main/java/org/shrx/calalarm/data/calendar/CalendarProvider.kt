// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 github.com/shrx

package org.shrx.calalarm.data.calendar

import android.content.Context
import android.provider.CalendarContract
import org.shrx.calalarm.data.calendar.models.CalendarEvent
import org.shrx.calalarm.data.calendar.models.CalendarInfo

/**
 * Provider class for reading calendar data from Android's CalendarContract API.
 *
 * This class provides access to:
 * - Available calendars on the device (from any sync provider: Google, DAVx5, Outlook, etc.)
 * - Upcoming events from selected calendars
 *
 * All-day events are automatically filtered out as they have no specific time to trigger alarms.
 * Deleted events (DELETED == 1) are also filtered out.
 *
 * Requires READ_CALENDAR permission to be granted before use.
 *
 * @property context Android context for accessing ContentResolver
 */
class CalendarProvider(private val context: Context) {

    /**
     * Retrieves all calendars available on the device.
     *
     * Queries CalendarContract.Calendars to fetch calendar metadata including
     * display name, account information, and unique IDs. This data is used
     * to populate the calendar selection UI in settings.
     *
     * @return List of CalendarInfo objects representing available calendars.
     *         Returns empty list if no calendars found or permission denied.
     */
    fun getAvailableCalendars(): List<CalendarInfo> {
        val calendars: MutableList<CalendarInfo> = mutableListOf()
        val projection: Array<String> = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME
        )

        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id: Long = cursor.getLong(0)
                val displayName: String = cursor.getString(1)
                val accountName: String = cursor.getString(2)

                calendars.add(
                    CalendarInfo(
                        id = id,
                        displayName = displayName,
                        accountName = accountName
                    )
                )
            }
        }
        return calendars
    }

    /**
     * Retrieves all upcoming events from specified calendars.
     *
     * Queries CalendarContract.Events with the following filters:
     * - Calendar ID must be in the provided list
     * - Event start time >= fromTime (no upper limit)
     * - Event not marked as deleted (DELETED = 1)
     * - Event is not all-day (ALL_DAY = 1)
     *
     * Results are ordered by start time ascending (earliest first).
     *
     * @param calendarIds List of calendar IDs to fetch events from
     * @param fromTime Start of time range (Unix timestamp in millis). Defaults to current time
     * @return List of CalendarEvent objects representing all future events.
     *         Returns empty list if calendarIds is empty or no events found.
     */
    fun getUpcomingEvents(
        calendarIds: List<Long>,
        fromTime: Long = System.currentTimeMillis()
    ): List<CalendarEvent> {
        if (calendarIds.isEmpty()) return emptyList()

        val events: MutableList<CalendarEvent> = mutableListOf()
        val projection: Array<String> = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.ALL_DAY
        )

        val selection: String = """
            ${CalendarContract.Events.CALENDAR_ID} IN (${calendarIds.joinToString(",")}) AND
            ${CalendarContract.Events.DTSTART} >= ? AND
            ${CalendarContract.Events.DELETED} != 1
        """.trimIndent()

        val selectionArgs: Array<String> = arrayOf(fromTime.toString())

        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${CalendarContract.Events.DTSTART} ASC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id: Long = cursor.getLong(0)
                val title: String? = cursor.getString(1)
                val startTime: Long = cursor.getLong(2)
                val calendarId: Long = cursor.getLong(3)
                val isAllDay: Int = cursor.getInt(4)

                // Skip all-day events (no specific time to trigger alarm)
                if (isAllDay == 1) {
                    continue
                }

                events.add(
                    CalendarEvent(
                        id = id,
                        title = title ?: "Untitled Event",
                        startTime = startTime,
                        calendarId = calendarId
                    )
                )
            }
        }
        return events
    }
}
