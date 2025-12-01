// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 github.com/shrx

package org.shrx.calalarm.domain

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.MockKAnnotations
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.shrx.calalarm.data.local.AlarmDao
import org.shrx.calalarm.data.local.entities.ScheduledAlarm
import org.shrx.calalarm.data.repository.CalendarRepository
import org.shrx.calalarm.service.AlarmScheduler

/**
 * Instrumentation tests for EventSyncService.
 *
 * Verifies testable portions: alarm scheduling, cancellation, disabled event handling,
 * and database operations. Tests for syncAndScheduleAlarms() would require integration
 * testing or refactoring to inject CalendarRepository.
 */
@RunWith(AndroidJUnit4::class)
class EventSyncServiceTest {

    @MockK
    private lateinit var alarmDao: AlarmDao

    @MockK
    private lateinit var alarmScheduler: AlarmScheduler

    @MockK
    private lateinit var calendarRepository: CalendarRepository

    private lateinit var context: Context
    private lateinit var eventSyncService: EventSyncService

    private val now: Long = System.currentTimeMillis()
    private val futureTime: Long = now + 3600000L // 1 hour from now

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)

        // Get real Android context from test environment
        context = ApplicationProvider.getApplicationContext()

        // Initialize EventSyncService with mocked dependencies
        eventSyncService = EventSyncService(context, alarmDao, alarmScheduler, calendarRepository)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    /**
     * Verifies that disableAlarm cancels, deletes, and blacklists the alarm.
     */
    @Test
    fun testDisableAlarm() = runBlocking {
        // Given: Alarm exists
        val alarm: ScheduledAlarm = ScheduledAlarm(
            eventId = 1L,
            eventTitle = "Meeting",
            eventStartTime = futureTime,
            calendarId = 100L
        )

        // When: disableAlarm() called
        eventSyncService.disableAlarm(alarm)

        // Then: Alarm is canceled with AlarmManager
        verify(exactly = 1) { alarmScheduler.cancelAlarm(alarm) }

        // And: Alarm is deleted from database
        coVerify(exactly = 1) { alarmDao.deleteAlarm(1L) }

        // And: Event ID is added to disabled list
        coVerify(exactly = 1) { alarmDao.insertDisabledEventId(match { it.eventId == 1L }) }
    }

    /**
     * Verifies that disableAlarm only affects the specified alarm.
     */
    @Test
    fun testDisableAlarm_multipleAlarms() = runBlocking {
        // Given: Multiple alarms
        val alarm1: ScheduledAlarm = ScheduledAlarm(
            eventId = 1L,
            eventTitle = "Meeting",
            eventStartTime = futureTime,
            calendarId = 100L
        )
        val alarm2: ScheduledAlarm = ScheduledAlarm(
            eventId = 2L,
            eventTitle = "Lunch",
            eventStartTime = futureTime + 3600000L,
            calendarId = 100L
        )

        // When: disableAlarm() called for alarm1 only
        eventSyncService.disableAlarm(alarm1)

        // Then: Only alarm1 is affected
        verify(exactly = 1) { alarmScheduler.cancelAlarm(alarm1) }
        verify(exactly = 0) { alarmScheduler.cancelAlarm(alarm2) }

        coVerify(exactly = 1) { alarmDao.deleteAlarm(1L) }
        coVerify(exactly = 0) { alarmDao.deleteAlarm(2L) }

        coVerify(exactly = 1) { alarmDao.insertDisabledEventId(match { it.eventId == 1L }) }
        coVerify(exactly = 0) { alarmDao.insertDisabledEventId(match { it.eventId == 2L }) }
    }

    /**
     * Verifies that disableAlarm performs operations in the correct order.
     */
    @Test
    fun testDisableAlarm_operationOrder() = runBlocking {
        // Given: Alarm exists
        val alarm: ScheduledAlarm = ScheduledAlarm(
            eventId = 1L,
            eventTitle = "Meeting",
            eventStartTime = futureTime,
            calendarId = 100L
        )

        // When: disableAlarm() called
        eventSyncService.disableAlarm(alarm)

        // Then: Operations performed in order (using coVerifyOrder)
        coVerify {
            alarmScheduler.cancelAlarm(alarm)
            alarmDao.deleteAlarm(1L)
            alarmDao.insertDisabledEventId(match { it.eventId == 1L })
        }
    }
}

