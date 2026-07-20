// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 github.com/shrx

package org.shrx.calalarm.domain

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.shrx.calalarm.data.calendar.CalendarObserver
import org.shrx.calalarm.data.calendar.models.CalendarEvent
import org.shrx.calalarm.data.local.AlarmDao
import org.shrx.calalarm.data.local.entities.DisabledEventId
import org.shrx.calalarm.data.local.entities.ScheduledAlarm
import org.shrx.calalarm.data.repository.CalendarRepository
import org.shrx.calalarm.service.AlarmScheduler

/**
 * Business logic service for synchronizing calendar events with scheduled alarms.
 *
 * @property context Android context for CalendarObserver
 * @property alarmDao DAO for database operations on alarms
 * @property alarmScheduler Wrapper for scheduling/canceling AlarmManager alarms
 * @property calendarRepository Repository for fetching calendar data
 */
class EventSyncService(
    private val context: Context,
    private val alarmDao: AlarmDao,
    private val alarmScheduler: AlarmScheduler,
    private val calendarRepository: CalendarRepository
) {
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val alarmResyncChannel: Channel<Unit> = Channel(Channel.CONFLATED)
    private val monitoringStarted: AtomicBoolean = AtomicBoolean(false)
    private val syncMutex: Mutex = Mutex()

    private val calendarObserver: CalendarObserver = CalendarObserver(context) {
        alarmResyncChannel.trySend(Unit)
    }

    /**
     * Synchronizes calendar events with scheduled alarms.
     *
     * Performs a full sync: schedules new alarms, cancels deleted/past alarms.
     * Serialized with a mutex: SyncWorker and the monitoring consumer are
     * independent entry points that must not interleave this read-modify-write.
     */
    suspend fun syncAndScheduleAlarms() = syncMutex.withLock {
        // 1. Get upcoming events from selected calendars
        val events: List<CalendarEvent> = calendarRepository.getUpcomingEventsFromSelectedCalendars()

        // 2. Get existing alarm event IDs and disabled event IDs from database
        val existingAlarmEventIds: List<Long> = alarmDao.getAllAlarmEventIds()
        val disabledEventIds: List<Long> = alarmDao.getDisabledEventIds()

        // 3. Find new events (not in database and not manually disabled by user)
        val newEvents: List<CalendarEvent> = events.filter { event ->
            event.id !in existingAlarmEventIds && event.id !in disabledEventIds
        }

        // 4. Schedule alarms for new events
        android.util.Log.d("EventSyncService", "Scheduling ${newEvents.size} new alarms")
        val eventMap: Map<Long, CalendarEvent> = events.associateBy { it.id }
        newEvents.forEach { event ->
            val alarm: ScheduledAlarm = ScheduledAlarm(
                eventId = event.id,
                eventTitle = event.title,
                eventStartTime = event.startTime,
                calendarId = event.calendarId
            )

            android.util.Log.d("EventSyncService", "Inserting alarm: eventId=${event.id}, title=${event.title}")
            // Save to database
            alarmDao.insertAlarm(alarm)

            // Schedule with AlarmManager
            android.util.Log.d("EventSyncService", "Scheduling alarm with AlarmManager: eventId=${event.id}")
            alarmScheduler.scheduleAlarm(alarm)
        }

        // 5. Cancel and delete alarms for events that no longer exist or are in the past
        val currentEventIds: Set<Long> = eventMap.keys
        val now: Long = System.currentTimeMillis()

        alarmDao.getAllAlarmsList().forEach { alarm ->
            val matchingEvent: CalendarEvent? = eventMap[alarm.eventId]
            if (alarm.snoozeOffset > 0) {
                // Snoozed alarm - only delete if alarm time is past
                if (alarm.eventStartTime + alarm.snoozeOffset < now) {
                    alarmScheduler.cancelAlarm(alarm)
                    alarmDao.deleteAlarm(alarm.eventId)
                }
            } else if (matchingEvent == null || matchingEvent.startTime < now) {
                // Non-snoozed alarm - event gone or event starts in the past
                alarmScheduler.cancelAlarm(alarm)
                alarmDao.deleteAlarm(alarm.eventId)
            } else if (matchingEvent.startTime != alarm.eventStartTime) {
                // Event still exists with a future start time - follow it, even if the
                // alarm's stored time already passed (event postponed after it started)
                val updatedAlarm: ScheduledAlarm = alarm.copy(eventStartTime = matchingEvent.startTime)
                alarmScheduler.cancelAlarm(alarm)
                alarmDao.insertAlarm(updatedAlarm)
                alarmScheduler.scheduleAlarm(updatedAlarm)
            }
        }

        // 6. Clean up disabled event IDs for events that no longer exist or are in the past
        disabledEventIds.forEach { disabledEventId ->
            if (disabledEventId !in currentEventIds) {
                alarmDao.deleteDisabledEventId(disabledEventId)
            }
        }
    }

    /**
     * Disables an alarm by canceling it and blacklisting its event ID.
     *
     * @param alarm The alarm to disable
     */
    suspend fun disableAlarm(alarm: ScheduledAlarm) = syncMutex.withLock {
        // Cancel the alarm
        alarmScheduler.cancelAlarm(alarm)

        // Delete from database
        alarmDao.deleteAlarm(alarm.eventId)

        // Add to blacklist
        alarmDao.insertDisabledEventId(DisabledEventId(alarm.eventId))
    }

    /**
     * Starts monitoring for calendar changes and automatically syncs alarms when changes occur.
     *
     * Monitors two sources:
     * - Calendar database (new/deleted events)
     * - Calendar selection (user enables/disables calendars in settings)
     *
     * When either source changes, a sync is triggered. If multiple changes occur rapidly,
     * only the most recent triggers a sync (conflated channel). A running sync is never
     * cancelled: interrupting between the DB insert and AlarmManager scheduling would
     * strand an alarm that never fires. A pending request simply runs the sync again
     * with fresh data once the current one finishes.
     *
     * Should be called once after permissions are granted. The initial call triggers the first sync.
     */
    fun startMonitoring() {
        if (!monitoringStarted.compareAndSet(false, true)) {
            return
        }
        // Monitor calendar database changes
        calendarObserver.startObserving()

        // Monitor calendar selection changes (initial emission triggers first sync)
        coroutineScope.launch {
            calendarRepository.selectedCalendarIdsFlow
                .distinctUntilChanged()
                .collect {
                    alarmResyncChannel.trySend(Unit)
                }
        }

        // Single consumer coroutine that processes sync requests serially,
        // letting each sync run to completion
        coroutineScope.launch {
            for (request in alarmResyncChannel) {
                try {
                    syncAndScheduleAlarms()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("EventSyncService", "Sync failed", e)
                }
            }
        }
    }

    /**
     * Requests a sync to be performed.
     * Can be called from anywhere to trigger a calendar sync.
     */
    fun requestSync() {
        alarmResyncChannel.trySend(Unit)
    }
}
