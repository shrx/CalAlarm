// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 github.com/shrx

package org.shrx.calalarm.ui.alarmlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.shrx.calalarm.data.local.AlarmDao
import org.shrx.calalarm.data.local.entities.ScheduledAlarm
import org.shrx.calalarm.data.repository.CalendarRepository
import org.shrx.calalarm.domain.EventSyncService

/**
 * ViewModel for the Alarm List screen.
 *
 * Manages the state of scheduled alarms and provides functions for
 * deleting alarms. Exposes alarm data as a Flow for reactive UI updates.
 *
 * @property alarmDao DAO for accessing alarm data from the database
 * @property eventSyncService Service for syncing calendar events with alarms
 * @property calendarRepository Repository for accessing calendar selection state
 */
class AlarmListViewModel(
    private val alarmDao: AlarmDao,
    private val eventSyncService: EventSyncService,
    private val calendarRepository: CalendarRepository
) : ViewModel() {

    init {
        android.util.Log.d("AlarmListViewModel", "ViewModel created: ${this.hashCode()}")
    }

    /**
     * Flow of all scheduled alarms, sorted by event start time (soonest first).
     *
     * This Flow emits a new list whenever the database changes, allowing the UI
     * to reactively update when alarms are added, deleted, or modified.
     */
    val alarms: Flow<List<ScheduledAlarm>> = alarmDao.getAllAlarms()

    /**
     * Flow indicating whether any calendars are selected.
     *
     * Emits true if at least one calendar is selected, false otherwise.
     * Used by the UI to determine which empty state message to display.
     *
     * This Flow reacts to changes in calendar selection (via SharedPreferences),
     * not just changes to the alarms database. This ensures the empty state message
     * updates immediately when the user selects/deselects calendars in settings,
     * even if no alarms exist yet.
     */
    val hasSelectedCalendars: Flow<Boolean> = calendarRepository.selectedCalendarIdsFlow.map { ids ->
        ids.isNotEmpty()
    }

    /**
     * Deletes an alarm and adds it to the disabled event blacklist.
     *
     * Calls [EventSyncService.disableAlarm] to cancel the alarm, remove it
     * from the database, and blacklist the event ID to prevent automatic
     * recreation during sync.
     *
     * @param alarm The alarm to delete
     */
    fun deleteAlarm(alarm: ScheduledAlarm) {
        viewModelScope.launch {
            eventSyncService.disableAlarm(alarm)
        }
    }
}
