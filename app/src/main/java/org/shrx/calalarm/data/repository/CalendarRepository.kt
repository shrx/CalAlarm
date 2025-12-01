// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 github.com/shrx

package org.shrx.calalarm.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import org.shrx.calalarm.data.calendar.CalendarProvider
import org.shrx.calalarm.data.calendar.models.CalendarEvent
import org.shrx.calalarm.data.calendar.models.CalendarInfo

/**
 * Repository for calendar-related data operations.
 *
 * @property context Android context for accessing ContentResolver
 * @property sharedPreferences SharedPreferences for persisting calendar selections
 */
open class CalendarRepository(
    private val context: Context,
    private val sharedPreferences: SharedPreferences
) {

    init {
        android.util.Log.d("CalendarRepository", "Repository created: ${this.hashCode()}")
    }

    private val calendarProvider: CalendarProvider = CalendarProvider(context)

    companion object {
        private const val PREF_SELECTED_CALENDARS: String = "selected_calendars"
    }

    /**
     * Flow that emits the list of selected calendar IDs whenever they change.
     */
    val selectedCalendarIdsFlow: Flow<List<Long>> = callbackFlow {
        // Emit current value immediately
        trySend(getSelectedCalendarIds())

        // Create listener for preference changes
        val listener: SharedPreferences.OnSharedPreferenceChangeListener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == PREF_SELECTED_CALENDARS) {
                    trySend(getSelectedCalendarIds())
                }
            }

        // Register listener
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)

        // Unregister listener when Flow is cancelled
        awaitClose {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    /**
     * Retrieves all calendars available on the device.
     *
     * @return List of CalendarInfo objects representing available calendars
     */
    open suspend fun getAvailableCalendars(): List<CalendarInfo> {
        return withContext(Dispatchers.IO) {
            calendarProvider.getAvailableCalendars()
        }
    }

    /**
     * Retrieves all upcoming events from the currently selected calendars.
     *
     * @param fromTime Start of time range (Unix timestamp in millis). Defaults to current time
     * @return List of CalendarEvent objects from selected calendars
     */
    open suspend fun getUpcomingEventsFromSelectedCalendars(
        fromTime: Long = System.currentTimeMillis()
    ): List<CalendarEvent> {
        return withContext(Dispatchers.IO) {
            val selectedCalendarIds: List<Long> = getSelectedCalendarIds()
            android.util.Log.d("CalendarRepository", "Selected calendar IDs: $selectedCalendarIds")
            if (selectedCalendarIds.isEmpty()) {
                android.util.Log.w("CalendarRepository", "No calendars selected!")
                return@withContext emptyList()
            }
            val events: List<CalendarEvent> = calendarProvider.getUpcomingEvents(selectedCalendarIds, fromTime)
            android.util.Log.d("CalendarRepository", "Found ${events.size} events")
            events.forEach { event ->
                android.util.Log.d("CalendarRepository", "Event: id=${event.id}, title=${event.title}, startTime=${event.startTime}")
            }
            events
        }
    }

    /**
     * Retrieves the list of calendar IDs selected by the user in settings.
     *
     * @return List of selected calendar IDs
     */
    fun getSelectedCalendarIds(): List<Long> {
        return sharedPreferences.getStringSet(PREF_SELECTED_CALENDARS, emptySet())
            ?.map { it.toLong() } ?: emptyList()
    }

    /**
     * Saves the list of calendar IDs selected by the user in settings.
     *
     * @param calendarIds List of calendar IDs to save as selected
     */
    fun saveSelectedCalendarIds(calendarIds: List<Long>) {
        sharedPreferences.edit()
            .putStringSet(PREF_SELECTED_CALENDARS, calendarIds.map { it.toString() }.toSet())
            .apply()
    }
}
