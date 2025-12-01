// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 github.com/shrx

package org.shrx.calalarm.data.repository

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for CalendarRepository's selectedCalendarIdsFlow property.
 *
 * These tests verify the reactive Flow that emits selected calendar IDs from
 * SharedPreferences. The selectedCalendarIdsFlow (lines 60-79) is a callbackFlow that:
 * 1. Emits immediately with current selection when first collected
 * 2. Emits again when SharedPreferences change
 * 3. Properly unregisters listener when Flow is cancelled
 *
 * ## Testing Strategy
 *
 * Uses MockK with pure JUnit (not AndroidJUnit4) since we're testing Flow behavior
 * that doesn't require Android framework. This makes tests fast and runnable on JVM.
 *
 * SharedPreferences is mocked to control preference values and simulate changes.
 * The OnSharedPreferenceChangeListener is captured to allow manual triggering of
 * preference change events.
 *
 * ## Test Coverage
 *
 * 1. **Immediate Emission**: Verifies Flow emits current selection immediately when collected
 * 2. **Reactive Updates**: Verifies Flow emits new value when preferences change
 * 3. **Multiple Emissions**: Verifies Flow emits multiple times as selection changes
 * 4. **Empty List**: Verifies Flow emits empty list when no calendars selected
 * 5. **Listener Cleanup**: Verifies listener is unregistered when Flow is cancelled
 *
 * ## Implementation Notes
 *
 * Tests use kotlinx-coroutines-test's runTest for proper coroutine testing.
 * We capture the OnSharedPreferenceChangeListener to manually trigger preference
 * changes, simulating what happens when saveSelectedCalendarIds() is called.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarRepositoryTest {

    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var sharedPreferencesEditor: SharedPreferences.Editor
    private lateinit var calendarRepository: CalendarRepository

    // Captured listener for simulating preference changes
    private val listenerSlot = slot<SharedPreferences.OnSharedPreferenceChangeListener>()

    @Before
    fun setup() {
        // Mock android.util.Log to prevent "Method d in android.util.Log not mocked" error
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0

        // Create mocks
        context = mockk(relaxed = true)
        sharedPreferences = mockk(relaxed = true)
        sharedPreferencesEditor = mockk(relaxed = true)

        // Setup SharedPreferences mock chain
        every { sharedPreferences.edit() } returns sharedPreferencesEditor
        every { sharedPreferencesEditor.putStringSet(any(), any()) } returns sharedPreferencesEditor
        every { sharedPreferencesEditor.apply() } returns Unit

        // Capture listener when registered
        every {
            sharedPreferences.registerOnSharedPreferenceChangeListener(capture(listenerSlot))
        } returns Unit

        every {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(any())
        } returns Unit

        // Create repository instance
        calendarRepository = CalendarRepository(context, sharedPreferences)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * Test: Selected Calendar IDs Flow Emits Immediately
     *
     * Given: SharedPreferences contains selected calendar IDs [1L, 2L, 3L]
     * When: Flow is first collected
     * Then: Flow emits the current selection immediately [1L, 2L, 3L]
     *
     * Verifies that the Flow provides the current value on collection without
     * waiting for preference changes. This is essential for UI to show correct
     * state immediately when ViewModel collects the Flow.
     */
    @Test
    fun testSelectedCalendarIdsFlow_emitsImmediately() = runTest {
        // Given: SharedPreferences contains selected calendar IDs
        val selectedIds: Set<String> = setOf("1", "2", "3")
        every {
            sharedPreferences.getStringSet("selected_calendars", emptySet())
        } returns selectedIds

        // When: Flow is collected
        val firstEmission: List<Long> = calendarRepository.selectedCalendarIdsFlow.first()

        // Then: Flow emits current selection immediately
        assertEquals(listOf(1L, 2L, 3L), firstEmission)

        // Verify listener was registered
        verify { sharedPreferences.registerOnSharedPreferenceChangeListener(any()) }
    }

    /**
     * Test: Selected Calendar IDs Flow Emits On Preference Change
     *
     * Given: SharedPreferences initially contains [1L, 2L]
     * And: Flow is being collected
     * When: saveSelectedCalendarIds() is called with [3L, 4L, 5L]
     * Then: Flow emits again with new value [3L, 4L, 5L]
     *
     * Verifies that the Flow reactively emits when SharedPreferences change,
     * allowing UI to update immediately when user changes calendar selection
     * in settings.
     */
    @Test
    fun testSelectedCalendarIdsFlow_emitsOnPreferenceChange() = runTest {
        // Given: Initial selection [1L, 2L]
        val initialIds: Set<String> = setOf("1", "2")
        every {
            sharedPreferences.getStringSet("selected_calendars", emptySet())
        } returns initialIds

        val emissions = mutableListOf<List<Long>>()

        // Start collecting in a separate coroutine
        val collectJob = launch {
            calendarRepository.selectedCalendarIdsFlow
                .take(2) // Take initial emission + one change
                .toList(emissions)
        }

        // Wait for initial emission and listener registration
        advanceUntilIdle()

        // Verify initial emission
        assertEquals(1, emissions.size)
        assertEquals(listOf(1L, 2L), emissions[0])

        // When: Preferences change to [3L, 4L, 5L]
        val newIds: Set<String> = setOf("3", "4", "5")
        every {
            sharedPreferences.getStringSet("selected_calendars", emptySet())
        } returns newIds

        // Simulate preference change notification
        listenerSlot.captured.onSharedPreferenceChanged(sharedPreferences, "selected_calendars")

        // Wait for the change emission
        advanceUntilIdle()

        // Then: Flow emits new value
        assertEquals(2, emissions.size)
        assertEquals(listOf(3L, 4L, 5L), emissions[1])

        collectJob.cancel()
    }

    /**
     * Test: Selected Calendar IDs Flow Emits Multiple Times
     *
     * Given: SharedPreferences initially contains [1L]
     * When: Selection changes multiple times: [] -> [2L, 3L] -> [4L]
     * Then: Flow emits each change: [1L], [], [2L, 3L], [4L]
     *
     * Verifies that the Flow continues to emit for every preference change,
     * not just the first one. This ensures UI stays synchronized with settings
     * throughout the app lifecycle.
     */
    @Test
    fun testSelectedCalendarIdsFlow_multipleEmissions() = runTest {
        // Given: Initial selection [1L]
        every {
            sharedPreferences.getStringSet("selected_calendars", emptySet())
        } returns setOf("1")

        val emissions = mutableListOf<List<Long>>()

        // Start collecting
        val collectJob = launch {
            calendarRepository.selectedCalendarIdsFlow
                .take(4) // Initial + 3 changes
                .toList(emissions)
        }

        // Wait for initial emission
        advanceUntilIdle()
        assertEquals(1, emissions.size)
        assertEquals(listOf(1L), emissions[0])

        // Change 1: Empty selection
        every {
            sharedPreferences.getStringSet("selected_calendars", emptySet())
        } returns emptySet()
        listenerSlot.captured.onSharedPreferenceChanged(sharedPreferences, "selected_calendars")
        advanceUntilIdle()

        assertEquals(2, emissions.size)
        assertEquals(emptyList<Long>(), emissions[1])

        // Change 2: Selection [2L, 3L]
        every {
            sharedPreferences.getStringSet("selected_calendars", emptySet())
        } returns setOf("2", "3")
        listenerSlot.captured.onSharedPreferenceChanged(sharedPreferences, "selected_calendars")
        advanceUntilIdle()

        assertEquals(3, emissions.size)
        assertEquals(listOf(2L, 3L), emissions[2])

        // Change 3: Selection [4L]
        every {
            sharedPreferences.getStringSet("selected_calendars", emptySet())
        } returns setOf("4")
        listenerSlot.captured.onSharedPreferenceChanged(sharedPreferences, "selected_calendars")
        advanceUntilIdle()

        assertEquals(4, emissions.size)
        assertEquals(listOf(4L), emissions[3])

        collectJob.cancel()
    }

    /**
     * Test: Selected Calendar IDs Flow Emits Empty List
     *
     * Given: SharedPreferences contains no selected calendars (null or empty)
     * When: Flow is collected
     * Then: Flow emits empty list []
     *
     * Verifies that the Flow correctly handles the case where no calendars
     * are selected. This is important for showing "no calendars selected"
     * empty state in UI.
     */
    @Test
    fun testSelectedCalendarIdsFlow_emitsEmptyList() = runTest {
        // Given: No calendars selected (null StringSet)
        every {
            sharedPreferences.getStringSet("selected_calendars", emptySet())
        } returns null

        // When: Flow is collected
        val emission: List<Long> = calendarRepository.selectedCalendarIdsFlow.first()

        // Then: Flow emits empty list
        assertTrue(emission.isEmpty())
        assertEquals(emptyList<Long>(), emission)
    }

    /**
     * Test: Selected Calendar IDs Flow Unregisters Listener On Cancellation
     *
     * Given: Flow is being collected
     * When: Collection is cancelled (Flow scope closed)
     * Then: OnSharedPreferenceChangeListener is unregistered
     *
     * Verifies that the Flow properly cleans up by unregistering the listener
     * when it's no longer needed. This prevents memory leaks and ensures
     * preference change notifications don't fire for inactive collectors.
     */
    @Test
    fun testSelectedCalendarIdsFlow_unregistersListenerOnCancellation() = runTest {
        // Given: Flow is being collected
        every {
            sharedPreferences.getStringSet("selected_calendars", emptySet())
        } returns setOf("1")

        val collectJob = launch {
            calendarRepository.selectedCalendarIdsFlow.first()
        }

        // Wait for collection and listener registration
        advanceUntilIdle()

        // Verify listener was registered
        verify { sharedPreferences.registerOnSharedPreferenceChangeListener(any()) }

        // When: Collection is cancelled
        collectJob.cancel()
        advanceUntilIdle()

        // Then: Listener is unregistered
        verify { sharedPreferences.unregisterOnSharedPreferenceChangeListener(listenerSlot.captured) }
    }

    /**
     * Test: Selected Calendar IDs Flow Ignores Unrelated Preference Changes
     *
     * Given: Flow is collecting
     * When: A different preference key changes (not "selected_calendars")
     * Then: Flow does not emit
     *
     * Verifies that the Flow only reacts to changes to the specific
     * "selected_calendars" preference, not all preference changes.
     */
    @Test
    fun testSelectedCalendarIdsFlow_ignoresUnrelatedPreferenceChanges() = runTest {
        // Given: Initial selection [1L]
        every {
            sharedPreferences.getStringSet("selected_calendars", emptySet())
        } returns setOf("1")

        val emissions = mutableListOf<List<Long>>()

        // Start collecting
        val collectJob = launch {
            calendarRepository.selectedCalendarIdsFlow
                .take(2) // Wait for potential unwanted emission
                .toList(emissions)
        }

        // Wait for initial emission
        advanceUntilIdle()
        assertEquals(1, emissions.size)
        assertEquals(listOf(1L), emissions[0])

        // When: Unrelated preference changes
        listenerSlot.captured.onSharedPreferenceChanged(sharedPreferences, "some_other_key")
        advanceUntilIdle()

        // Then: Flow does not emit again (still only 1 emission)
        assertEquals(1, emissions.size)

        // Change the actual key to verify listener is still working
        every {
            sharedPreferences.getStringSet("selected_calendars", emptySet())
        } returns setOf("2")
        listenerSlot.captured.onSharedPreferenceChanged(sharedPreferences, "selected_calendars")
        advanceUntilIdle()

        // Now we should have 2 emissions
        assertEquals(2, emissions.size)
        assertEquals(listOf(2L), emissions[1])

        collectJob.cancel()
    }
}
