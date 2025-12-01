// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 github.com/shrx

package org.shrx.calalarm.domain

import android.content.ContentResolver
import android.content.Context
import android.os.Handler
import android.os.Looper
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.Runs
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.shrx.calalarm.data.calendar.models.CalendarEvent
import org.shrx.calalarm.data.local.AlarmDao
import org.shrx.calalarm.data.local.entities.ScheduledAlarm
import org.shrx.calalarm.data.repository.CalendarRepository
import org.shrx.calalarm.service.AlarmScheduler

/**
 * Unit tests for EventSyncService.
 *
 * Verifies alarm synchronization logic including scheduling, idempotency,
 * cleanup, and disabled event handling.
 *
 * Note: Tests currently failing due to MockK mocking issues with CalendarRepository.
 */
class EventSyncServiceUnitTest {

    private lateinit var context: Context
    private lateinit var alarmDao: AlarmDao
    private lateinit var alarmScheduler: AlarmScheduler
    private lateinit var calendarRepository: CalendarRepository
    private lateinit var eventSyncService: EventSyncService

    @Before
    fun setup() {
        // Create mocks directly
        context = mockk(relaxed = true)
        alarmDao = mockk(relaxed = true)
        alarmScheduler = mockk(relaxed = true)
        calendarRepository = mockk(relaxed = true)

        // Mock Android components to avoid Looper and ContentResolver dependencies
        mockkStatic(Looper::class)
        val mockLooper: Looper = mockk(relaxed = true)
        every { Looper.getMainLooper() } returns mockLooper

        val mockContentResolver: ContentResolver = mockk(relaxed = true)
        every { context.contentResolver } returns mockContentResolver

        // Mock android.util.Log for unit tests
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0

        // Create service instance with mocked dependencies
        eventSyncService = EventSyncService(
            context = context,
            alarmDao = alarmDao,
            alarmScheduler = alarmScheduler,
            calendarRepository = calendarRepository
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * Verifies that new calendar events result in alarm creation.
     */
    @Test
    fun syncAndScheduleAlarms_newEvents_schedulesAlarms() = runBlocking {
        // Given: Calendar returns 2 events
        val event1: CalendarEvent = CalendarEvent(
            id = 1L,
            title = "Meeting",
            startTime = System.currentTimeMillis() + 3600000, // 1 hour from now
            calendarId = 100L
        )
        val event2: CalendarEvent = CalendarEvent(
            id = 2L,
            title = "Lunch",
            startTime = System.currentTimeMillis() + 7200000, // 2 hours from now
            calendarId = 100L
        )

        // Setup all mocks BEFORE calling sync
        coEvery {
            calendarRepository.getUpcomingEventsFromSelectedCalendars(any())
        } returns listOf(event1, event2)

        // And: Database has no existing alarms
        coEvery { alarmDao.getAllAlarmEventIds() } returns emptyList()
        coEvery { alarmDao.getDisabledEventIds() } returns emptyList()
        coEvery { alarmDao.getAllAlarmsList() } returns emptyList()

        // And: Mock insert and schedule operations
        coEvery { alarmDao.insertAlarm(any()) } just Runs

        // When: Sync is triggered
        eventSyncService.syncAndScheduleAlarms()

        // Then: Both events are inserted into database
        coVerify(exactly = 2) { alarmDao.insertAlarm(any()) }

        // And: Both alarms are scheduled (alarmScheduler is relaxed, no need to verify)
        coVerify(atLeast = 2) { alarmScheduler.scheduleAlarm(any()) }
    }

    /**
     * Verifies that sync is idempotent and doesn't create duplicate alarms.
     */
    @Test
    fun syncAndScheduleAlarms_alreadyScheduled_idempotent() = runBlocking {
        // Given: Calendar returns 2 events
        val event1: CalendarEvent = CalendarEvent(
            id = 1L,
            title = "Meeting",
            startTime = System.currentTimeMillis() + 3600000,
            calendarId = 100L
        )
        val event2: CalendarEvent = CalendarEvent(
            id = 2L,
            title = "Lunch",
            startTime = System.currentTimeMillis() + 7200000,
            calendarId = 100L
        )
        coEvery { calendarRepository.getUpcomingEventsFromSelectedCalendars(any()) } returns listOf(event1, event2)

        // And: Database already has alarms for both events
        coEvery { alarmDao.getAllAlarmEventIds() } returns listOf(1L, 2L)
        coEvery { alarmDao.getDisabledEventIds() } returns emptyList()

        val alarm1: ScheduledAlarm = ScheduledAlarm(
            eventId = 1L,
            eventTitle = "Meeting",
            eventStartTime = event1.startTime,
            calendarId = 100L
        )
        val alarm2: ScheduledAlarm = ScheduledAlarm(
            eventId = 2L,
            eventTitle = "Lunch",
            eventStartTime = event2.startTime,
            calendarId = 100L
        )
        coEvery { alarmDao.getAllAlarmsList() } returns listOf(alarm1, alarm2)

        // When: Sync is triggered
        eventSyncService.syncAndScheduleAlarms()

        // Then: No new alarms are inserted (idempotent)
        coVerify(exactly = 0) { alarmDao.insertAlarm(any()) }

        // And: No alarms are scheduled (already exist)
        coVerify(exactly = 0) { alarmScheduler.scheduleAlarm(any()) }

        // And: No alarms are canceled (both still valid)
        coVerify(exactly = 0) { alarmScheduler.cancelAlarm(any()) }
    }

    /**
     * Verifies that alarms are removed when calendar events are deleted.
     */
    @Test
    fun syncAndScheduleAlarms_deletedEvents_cancelsAndRemovesAlarms() = runBlocking {
        // Given: Calendar returns only 1 event
        val event1: CalendarEvent = CalendarEvent(
            id = 1L,
            title = "Meeting",
            startTime = System.currentTimeMillis() + 3600000,
            calendarId = 100L
        )
        coEvery { calendarRepository.getUpcomingEventsFromSelectedCalendars(any()) } returns listOf(event1)

        // And: Database has 2 alarms (event 2L was deleted from calendar)
        coEvery { alarmDao.getAllAlarmEventIds() } returns listOf(1L, 2L)
        coEvery { alarmDao.getDisabledEventIds() } returns emptyList()

        val alarm1: ScheduledAlarm = ScheduledAlarm(
            eventId = 1L,
            eventTitle = "Meeting",
            eventStartTime = event1.startTime,
            calendarId = 100L
        )
        val alarm2: ScheduledAlarm = ScheduledAlarm(
            eventId = 2L,
            eventTitle = "Deleted Event",
            eventStartTime = System.currentTimeMillis() + 7200000,
            calendarId = 100L
        )
        coEvery { alarmDao.getAllAlarmsList() } returns listOf(alarm1, alarm2)

        // And: Mock cancel and delete operations
        every { alarmScheduler.cancelAlarm(any()) } just Runs
        coEvery { alarmDao.deleteAlarm(any()) } just Runs

        // When: Sync is triggered
        eventSyncService.syncAndScheduleAlarms()

        // Then: Alarm for deleted event 2L is canceled
        coVerify(exactly = 1) { alarmScheduler.cancelAlarm(alarm2) }

        // And: Alarm for deleted event 2L is deleted from database
        coVerify(exactly = 1) { alarmDao.deleteAlarm(2L) }

        // And: Alarm for existing event 1L is not touched
        coVerify(exactly = 0) { alarmScheduler.cancelAlarm(alarm1) }
        coVerify(exactly = 0) { alarmDao.deleteAlarm(1L) }
    }

    @Test
    fun syncAndScheduleAlarms_eventTimeChanged_reschedulesNonSnoozedAlarm() = runBlocking {
        val originalTime: Long = System.currentTimeMillis() + 3_600_000
        val updatedTime: Long = originalTime + 1_800_000
        val calendarEvent: CalendarEvent = CalendarEvent(
            id = 1L,
            title = "Moved Meeting",
            startTime = updatedTime,
            calendarId = 100L
        )
        coEvery { calendarRepository.getUpcomingEventsFromSelectedCalendars(any()) } returns listOf(calendarEvent)
        coEvery { alarmDao.getAllAlarmEventIds() } returns listOf(1L)
        coEvery { alarmDao.getDisabledEventIds() } returns emptyList()
        val existingAlarm: ScheduledAlarm = ScheduledAlarm(
            eventId = 1L,
            eventTitle = "Moved Meeting",
            eventStartTime = originalTime,
            calendarId = 100L,
            snoozeOffset = 0L
        )
        coEvery { alarmDao.getAllAlarmsList() } returns listOf(existingAlarm)
        coEvery { alarmDao.insertAlarm(any()) } just Runs
        every { alarmScheduler.cancelAlarm(any()) } just Runs
        every { alarmScheduler.scheduleAlarm(any()) } just Runs

        eventSyncService.syncAndScheduleAlarms()

        coVerify(exactly = 1) { alarmScheduler.cancelAlarm(existingAlarm) }
        coVerify {
            alarmDao.insertAlarm(match { it.eventId == 1L && it.eventStartTime == updatedTime })
        }
        coVerify {
            alarmScheduler.scheduleAlarm(match { it.eventId == 1L && it.eventStartTime == updatedTime })
        }
        coVerify(exactly = 0) { alarmDao.deleteAlarm(any()) }
    }

    @Test
    fun syncAndScheduleAlarms_eventTimeChanged_doesNotRescheduleSnoozedAlarm() = runBlocking {
        val originalTime: Long = System.currentTimeMillis() + 3_600_000
        val updatedTime: Long = originalTime + 1_800_000
        val calendarEvent: CalendarEvent = CalendarEvent(
            id = 1L,
            title = "Snoozed Meeting",
            startTime = updatedTime,
            calendarId = 100L
        )
        coEvery { calendarRepository.getUpcomingEventsFromSelectedCalendars(any()) } returns listOf(calendarEvent)
        coEvery { alarmDao.getAllAlarmEventIds() } returns listOf(1L)
        coEvery { alarmDao.getDisabledEventIds() } returns emptyList()
        val snoozedAlarm: ScheduledAlarm = ScheduledAlarm(
            eventId = 1L,
            eventTitle = "Snoozed Meeting",
            eventStartTime = originalTime,
            calendarId = 100L,
            snoozeOffset = 600_000L
        )
        coEvery { alarmDao.getAllAlarmsList() } returns listOf(snoozedAlarm)

        eventSyncService.syncAndScheduleAlarms()

        coVerify(exactly = 0) { alarmScheduler.cancelAlarm(any()) }
        coVerify(exactly = 0) { alarmDao.insertAlarm(any()) }
        coVerify(exactly = 0) { alarmScheduler.scheduleAlarm(any()) }
    }

    /**
     * Verifies that past alarms are cleaned up even if the event still exists.
     */
    @Test
    fun syncAndScheduleAlarms_pastEvents_cancelsAndRemovesAlarms() = runBlocking {
        // Given: Calendar returns 1 future event
        val now: Long = System.currentTimeMillis()
        val futureEvent: CalendarEvent = CalendarEvent(
            id = 1L,
            title = "Future Meeting",
            startTime = now + 3600000, // 1 hour from now
            calendarId = 100L
        )
        coEvery { calendarRepository.getUpcomingEventsFromSelectedCalendars(any()) } returns listOf(futureEvent)

        // And: Database has 1 past alarm
        coEvery { alarmDao.getAllAlarmEventIds() } returns listOf(1L, 2L)
        coEvery { alarmDao.getDisabledEventIds() } returns emptyList()

        val futureAlarm: ScheduledAlarm = ScheduledAlarm(
            eventId = 1L,
            eventTitle = "Future Meeting",
            eventStartTime = futureEvent.startTime,
            calendarId = 100L
        )
        val pastAlarm: ScheduledAlarm = ScheduledAlarm(
            eventId = 2L,
            eventTitle = "Past Meeting",
            eventStartTime = now - 3600000, // 1 hour ago (past)
            calendarId = 100L
        )
        coEvery { alarmDao.getAllAlarmsList() } returns listOf(futureAlarm, pastAlarm)

        // And: Mock cancel and delete operations
        every { alarmScheduler.cancelAlarm(any()) } just Runs
        coEvery { alarmDao.deleteAlarm(any()) } just Runs

        // When: Sync is triggered
        eventSyncService.syncAndScheduleAlarms()

        // Then: Past alarm is canceled
        coVerify(exactly = 1) { alarmScheduler.cancelAlarm(pastAlarm) }

        // And: Past alarm is deleted from database
        coVerify(exactly = 1) { alarmDao.deleteAlarm(2L) }

        // And: Future alarm is not touched
        coVerify(exactly = 0) { alarmScheduler.cancelAlarm(futureAlarm) }
        coVerify(exactly = 0) { alarmDao.deleteAlarm(1L) }
    }

    /**
     * Verifies that disabled events are not automatically rescheduled.
     */
    @Test
    fun syncAndScheduleAlarms_disabledEventsBlacklist_skipsDisabledEvents() = runBlocking {
        // Given: Calendar returns 2 events
        val event1: CalendarEvent = CalendarEvent(
            id = 1L,
            title = "Meeting",
            startTime = System.currentTimeMillis() + 3600000,
            calendarId = 100L
        )
        val event2: CalendarEvent = CalendarEvent(
            id = 2L,
            title = "Lunch (user disabled)",
            startTime = System.currentTimeMillis() + 7200000,
            calendarId = 100L
        )
        coEvery { calendarRepository.getUpcomingEventsFromSelectedCalendars(any()) } returns listOf(event1, event2)

        // And: No existing alarms
        coEvery { alarmDao.getAllAlarmEventIds() } returns emptyList()

        // And: Event 2L is disabled (blacklisted)
        coEvery { alarmDao.getDisabledEventIds() } returns listOf(2L)

        coEvery { alarmDao.getAllAlarmsList() } returns emptyList()

        // And: Mock insert and schedule operations
        coEvery { alarmDao.insertAlarm(any()) } just Runs
        every { alarmScheduler.scheduleAlarm(any()) } just Runs

        // When: Sync is triggered
        eventSyncService.syncAndScheduleAlarms()

        // Then: Only 1 alarm is inserted (event 1L, not event 2L)
        coVerify(exactly = 1) { alarmDao.insertAlarm(any()) }

        // And: Only 1 alarm is scheduled (event 1L, not event 2L)
        coVerify(exactly = 1) { alarmScheduler.scheduleAlarm(any()) }

        // And: Verify the scheduled alarm is for event 1L (not event 2L)
        coVerify {
            alarmDao.insertAlarm(match { alarm: ScheduledAlarm ->
                alarm.eventId == 1L
            })
        }
    }

    /**
     * Verifies that stale blacklist entries are automatically cleaned up.
     */
    @Test
    fun syncAndScheduleAlarms_cleanupDisabledIds_removesStaleBlacklistEntries() = runBlocking {
        // Given: Calendar returns 1 event
        val event1: CalendarEvent = CalendarEvent(
            id = 1L,
            title = "Meeting",
            startTime = System.currentTimeMillis() + 3600000,
            calendarId = 100L
        )
        coEvery { calendarRepository.getUpcomingEventsFromSelectedCalendars(any()) } returns listOf(event1)

        // And: Event 1L already has an alarm
        coEvery { alarmDao.getAllAlarmEventIds() } returns listOf(1L)

        // And: Disabled events contains non-existent event 99L
        coEvery { alarmDao.getDisabledEventIds() } returns listOf(99L)

        val alarm1: ScheduledAlarm = ScheduledAlarm(
            eventId = 1L,
            eventTitle = "Meeting",
            eventStartTime = event1.startTime,
            calendarId = 100L
        )
        coEvery { alarmDao.getAllAlarmsList() } returns listOf(alarm1)

        // And: Mock delete operation
        coEvery { alarmDao.deleteDisabledEventId(any()) } just Runs

        // When: Sync is triggered
        eventSyncService.syncAndScheduleAlarms()

        // Then: Stale disabled event ID 99L is deleted from blacklist
        coVerify(exactly = 1) { alarmDao.deleteDisabledEventId(99L) }
    }

    /**
     * Verifies that sync correctly handles both additions and removals simultaneously.
     */
    @Test
    fun syncAndScheduleAlarms_mixedChanges_handlesAdditionsAndDeletions() = runBlocking {
        // Given: Calendar returns 2 new events
        val event3: CalendarEvent = CalendarEvent(
            id = 3L,
            title = "New Meeting 1",
            startTime = System.currentTimeMillis() + 3600000,
            calendarId = 100L
        )
        val event4: CalendarEvent = CalendarEvent(
            id = 4L,
            title = "New Meeting 2",
            startTime = System.currentTimeMillis() + 7200000,
            calendarId = 100L
        )
        coEvery { calendarRepository.getUpcomingEventsFromSelectedCalendars(any()) } returns listOf(event3, event4)

        // And: Database has 2 old alarms (for events no longer in calendar)
        coEvery { alarmDao.getAllAlarmEventIds() } returns listOf(1L, 2L)
        coEvery { alarmDao.getDisabledEventIds() } returns emptyList()

        val alarm1: ScheduledAlarm = ScheduledAlarm(
            eventId = 1L,
            eventTitle = "Old Meeting 1",
            eventStartTime = System.currentTimeMillis() + 3600000,
            calendarId = 100L
        )
        val alarm2: ScheduledAlarm = ScheduledAlarm(
            eventId = 2L,
            eventTitle = "Old Meeting 2",
            eventStartTime = System.currentTimeMillis() + 7200000,
            calendarId = 100L
        )
        coEvery { alarmDao.getAllAlarmsList() } returns listOf(alarm1, alarm2)

        // And: Mock operations
        coEvery { alarmDao.insertAlarm(any()) } just Runs
        every { alarmScheduler.scheduleAlarm(any()) } just Runs
        every { alarmScheduler.cancelAlarm(any()) } just Runs
        coEvery { alarmDao.deleteAlarm(any()) } just Runs

        // When: Sync is triggered
        eventSyncService.syncAndScheduleAlarms()

        // Then: 2 new alarms are inserted
        coVerify(exactly = 2) { alarmDao.insertAlarm(any()) }

        // And: 2 new alarms are scheduled
        coVerify(exactly = 2) { alarmScheduler.scheduleAlarm(any()) }

        // And: 2 old alarms are canceled
        coVerify(exactly = 2) { alarmScheduler.cancelAlarm(any()) }

        // And: 2 old alarms are deleted
        coVerify(exactly = 2) { alarmDao.deleteAlarm(any()) }

        // And: Verify correct event IDs are deleted
        coVerify { alarmDao.deleteAlarm(1L) }
        coVerify { alarmDao.deleteAlarm(2L) }
    }

    /**
     * Verifies that all alarms are canceled when no calendar events exist.
     */
    @Test
    fun syncAndScheduleAlarms_noEventsInCalendar_cancelsAllAlarms() = runBlocking {
        // Given: Calendar returns no events
        coEvery { calendarRepository.getUpcomingEventsFromSelectedCalendars(any()) } returns emptyList()

        // And: Database has 2 existing alarms
        coEvery { alarmDao.getAllAlarmEventIds() } returns listOf(1L, 2L)
        coEvery { alarmDao.getDisabledEventIds() } returns emptyList()

        val alarm1: ScheduledAlarm = ScheduledAlarm(
            eventId = 1L,
            eventTitle = "Meeting",
            eventStartTime = System.currentTimeMillis() + 3600000,
            calendarId = 100L
        )
        val alarm2: ScheduledAlarm = ScheduledAlarm(
            eventId = 2L,
            eventTitle = "Lunch",
            eventStartTime = System.currentTimeMillis() + 7200000,
            calendarId = 100L
        )
        coEvery { alarmDao.getAllAlarmsList() } returns listOf(alarm1, alarm2)

        // And: Mock cancel and delete operations
        every { alarmScheduler.cancelAlarm(any()) } just Runs
        coEvery { alarmDao.deleteAlarm(any()) } just Runs

        // When: Sync is triggered
        eventSyncService.syncAndScheduleAlarms()

        // Then: Both alarms are canceled
        coVerify(exactly = 2) { alarmScheduler.cancelAlarm(any()) }

        // And: Both alarms are deleted
        coVerify(exactly = 2) { alarmDao.deleteAlarm(any()) }

        // And: No new alarms are scheduled
        coVerify(exactly = 0) { alarmScheduler.scheduleAlarm(any()) }
    }

    /**
     * Verifies that syncAndScheduleAlarms() respects cancellation during repository query.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun syncAndScheduleAlarms_cancelledDuringRepositoryQuery_stopsExecution() = runTest {
        // Given: Repository query is slow
        val testEvent: CalendarEvent = CalendarEvent(
            id = 1L,
            title = "Test",
            startTime = System.currentTimeMillis() + 3600000,
            calendarId = 100L
        )
        coEvery {
            calendarRepository.getUpcomingEventsFromSelectedCalendars(any())
        } coAnswers {
            delay(1000)
            listOf(testEvent)
        }
        coEvery { alarmDao.getAllAlarmEventIds() } returns emptyList()
        coEvery { alarmDao.getDisabledEventIds() } returns emptyList()
        coEvery { alarmDao.getAllAlarmsList() } returns emptyList()

        // When: Start sync and cancel mid-execution
        val job: Job = launch {
            eventSyncService.syncAndScheduleAlarms()
        }
        advanceTimeBy(500) // Repository query in progress
        job.cancel()
        job.join()

        // Then: Sync was cancelled before scheduling any alarms
        coVerify(exactly = 0) { alarmDao.insertAlarm(any()) }
        coVerify(exactly = 0) { alarmScheduler.scheduleAlarm(any()) }
    }

    /**
     * Verifies that syncAndScheduleAlarms() respects cancellation during alarm insertion loop.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun syncAndScheduleAlarms_cancelledDuringAlarmInsertion_stopsScheduling() = runTest {
        // Given: Repository returns multiple events
        val events: List<CalendarEvent> = List(10) { i ->
            CalendarEvent(
                id = i.toLong(),
                title = "Event $i",
                startTime = System.currentTimeMillis() + 3600000,
                calendarId = 100L
            )
        }
        coEvery { calendarRepository.getUpcomingEventsFromSelectedCalendars(any()) } returns events
        coEvery { alarmDao.getAllAlarmEventIds() } returns emptyList()
        coEvery { alarmDao.getDisabledEventIds() } returns emptyList()
        coEvery { alarmDao.getAllAlarmsList() } returns emptyList()

        // And: Inserting alarms is slow
        coEvery { alarmDao.insertAlarm(any()) } coAnswers {
            delay(100)
        }

        // When: Start sync and cancel after first insertion
        val job: Job = launch {
            eventSyncService.syncAndScheduleAlarms()
        }
        advanceTimeBy(150) // First alarm inserted
        job.cancel()
        job.join()

        // Then: Not all alarms were scheduled (cancelled mid-loop)
        coVerify(atMost = 2) { alarmDao.insertAlarm(any()) }
    }

    /**
     * Verifies that cancel-previous pattern works: multiple rapid syncs, only last completes.
     *
     * Simulates the CalendarObserver callback behavior by manually implementing the
     * cancel-previous pattern: each new sync cancels the previous Job before starting.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun syncAndScheduleAlarms_cancelPreviousPattern_onlyLastCompletes() = runTest {
        // Given: Syncs take time to complete
        val event: CalendarEvent = CalendarEvent(
            id = 1L,
            title = "Test",
            startTime = System.currentTimeMillis() + 3600000,
            calendarId = 100L
        )
        coEvery {
            calendarRepository.getUpcomingEventsFromSelectedCalendars(any())
        } coAnswers {
            delay(1000)
            listOf(event)
        }
        coEvery { alarmDao.getAllAlarmEventIds() } returns emptyList()
        coEvery { alarmDao.getDisabledEventIds() } returns emptyList()
        coEvery { alarmDao.getAllAlarmsList() } returns emptyList()
        coEvery { alarmDao.insertAlarm(any()) } just Runs

        // When: Start 3 syncs with cancel-previous pattern (simulating observer behavior)
        var currentJob: Job? = null

        // First sync
        currentJob?.cancel()
        currentJob = launch { eventSyncService.syncAndScheduleAlarms() }
        advanceTimeBy(100)

        // Second sync cancels first
        currentJob.cancel()
        currentJob = launch { eventSyncService.syncAndScheduleAlarms() }
        advanceTimeBy(100)

        // Third sync cancels second
        currentJob.cancel()
        currentJob = launch { eventSyncService.syncAndScheduleAlarms() }
        advanceUntilIdle() // Let last one complete

        // Then: Only 1 alarm was inserted (from the last sync)
        coVerify(exactly = 1) { alarmDao.insertAlarm(any()) }
    }
}
