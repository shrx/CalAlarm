// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 github.com/shrx

package org.shrx.calalarm.ui.alarmlist

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.shrx.calalarm.data.local.entities.ScheduledAlarm
import org.shrx.calalarm.util.DateTimeFormatter

class AlarmListScreenTest {
    private lateinit var previousLocale: Locale
    private lateinit var previousTimeZone: TimeZone

    @Before
    fun setUp() {
        previousLocale = Locale.getDefault()
        previousTimeZone = TimeZone.getDefault()
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        Locale.setDefault(previousLocale)
        TimeZone.setDefault(previousTimeZone)
    }

    @Test
    fun buildAlarmTimeText_returnsTodayLabelForSameDayEvents() {
        val eventTime: Long = eventTimeForDayOffset(dayOffset = 0)
        val alarm: ScheduledAlarm = buildAlarm(eventStartTime = eventTime)

        val result: String = buildAlarmTimeText(alarm)

        assertEquals("Today ${DateTimeFormatter.formatTime(eventTime)}", result)
    }

    @Test
    fun buildAlarmTimeText_returnsTomorrowLabelForNextDayEvents() {
        val eventTime: Long = eventTimeForDayOffset(dayOffset = 1)
        val alarm: ScheduledAlarm = buildAlarm(eventStartTime = eventTime)

        val result: String = buildAlarmTimeText(alarm)

        assertEquals("Tomorrow ${DateTimeFormatter.formatTime(eventTime)}", result)
    }

    @Test
    fun buildAlarmTimeText_fallsBackToAbsoluteDateAfterTomorrow() {
        val eventTime: Long = eventTimeForDayOffset(dayOffset = 2)
        val alarm: ScheduledAlarm = buildAlarm(eventStartTime = eventTime)

        val result: String = buildAlarmTimeText(alarm)

        assertEquals(DateTimeFormatter.formatDateTime(eventTime), result)
    }

    @Test
    fun buildAlarmTimeText_includesRelativeLabelForSnoozedAlarms() {
        val eventTime: Long = eventTimeForDayOffset(dayOffset = 0)
        val snoozedAlarm: ScheduledAlarm = buildAlarm(
            eventStartTime = eventTime,
            snoozeOffset = 600_000L
        )
        val displayTime: Long = eventTime + 600_000L

        val result: String = buildAlarmTimeText(snoozedAlarm)

        val relative: String = "Today ${DateTimeFormatter.formatTime(displayTime)}"
            .replaceFirstChar { character -> character.lowercase() }
        assertEquals("Snoozed until $relative", result)
    }

    private fun buildAlarm(
        eventStartTime: Long,
        snoozeOffset: Long = 0L
    ): ScheduledAlarm = ScheduledAlarm(
        eventId = 1L,
        eventTitle = "Event",
        eventStartTime = eventStartTime,
        calendarId = 42L,
        snoozeOffset = snoozeOffset
    )

    private fun eventTimeForDayOffset(dayOffset: Long): Long {
        val zone: ZoneId = ZoneId.systemDefault()
        val currentDate: LocalDate = Instant.ofEpochMilli(System.currentTimeMillis())
            .atZone(zone)
            .toLocalDate()
        val targetDate: LocalDate = currentDate.plusDays(dayOffset)
        val zonedDateTime: ZonedDateTime = targetDate
            .atTime(LocalTime.NOON)
            .atZone(zone)
        return zonedDateTime.toInstant().toEpochMilli()
    }
}
