// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 github.com/shrx

package org.shrx.calalarm.data.repository

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserPreferencesRepositoryTest {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var repository: UserPreferencesRepository
    private var storedSnooze: Long = 10L
    private var storedNotification: Boolean = false
    private val listenerSlot = slot<SharedPreferences.OnSharedPreferenceChangeListener>()

    @Before
    fun setup() {
        sharedPreferences = mockk(relaxed = true)
        editor = mockk(relaxed = true)

        every { sharedPreferences.edit() } returns editor
        every { editor.putLong(any(), any()) } answers {
            storedSnooze = arg(1)
            editor
        }
        every { editor.putBoolean(any(), any()) } answers {
            storedNotification = arg(1)
            editor
        }
        every { editor.apply() } returns Unit

        every { sharedPreferences.getLong(any(), any()) } answers { storedSnooze }
        every { sharedPreferences.getBoolean(any(), any()) } answers { storedNotification }

        every {
            sharedPreferences.registerOnSharedPreferenceChangeListener(capture(listenerSlot))
        } returns Unit
        every { sharedPreferences.unregisterOnSharedPreferenceChangeListener(any()) } returns Unit

        repository = UserPreferencesRepository(sharedPreferences)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun getSnoozeDelayMinutes_enforcesMinimumValue() {
        storedSnooze = 0L

        val result: Long = repository.getSnoozeDelayMinutes()

        assertEquals(1L, result)
    }

    @Test
    fun setSnoozeDelayMinutes_coercesValueBeforeSaving() {
        repository.setSnoozeDelayMinutes(0L)

        verify {
            editor.putLong("snooze_delay_minutes", 1L)
        }
    }

    @Test
    fun preferencesFlow_emitsUpdatesForRelevantKeys() = runTest {
        val emissions = mutableListOf<UserPreferences>()

        val collectJob = launch {
            repository.preferencesFlow
                .take(3)
                .toList(emissions)
        }

        advanceUntilIdle()

        assertEquals(1, emissions.size)
        assertEquals(10L, emissions[0].snoozeDelayMinutes)
        assertEquals(false, emissions[0].showNextAlarmNotification)

        storedSnooze = 25L
        listenerSlot.captured.onSharedPreferenceChanged(sharedPreferences, "snooze_delay_minutes")
        advanceUntilIdle()

        storedNotification = true
        listenerSlot.captured.onSharedPreferenceChanged(sharedPreferences, "show_next_alarm_notification")
        advanceUntilIdle()

        assertEquals(3, emissions.size)
        assertEquals(25L, emissions[1].snoozeDelayMinutes)
        assertEquals(false, emissions[1].showNextAlarmNotification)
        assertEquals(25L, emissions[2].snoozeDelayMinutes)
        assertEquals(true, emissions[2].showNextAlarmNotification)

        collectJob.cancel()
    }
}
