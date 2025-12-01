// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 github.com/shrx

package org.shrx.calalarm.ui.alarmlist

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.shrx.calalarm.data.local.AlarmDao
import org.shrx.calalarm.data.repository.CalendarRepository
import org.shrx.calalarm.domain.EventSyncService

/**
 * Unit tests for AlarmListViewModel's hasSelectedCalendars Flow property.
 *
 * These tests verify the reactive Flow that maps CalendarRepository's selectedCalendarIdsFlow
 * to a Boolean indicating whether any calendars are selected. The hasSelectedCalendars Flow
 * (AlarmListViewModel lines 55-57) is a simple map transformation that:
 * 1. Observes CalendarRepository.selectedCalendarIdsFlow
 * 2. Maps List<Long> to Boolean using isEmpty() check
 * 3. Returns true if at least one calendar is selected, false otherwise
 *
 * ## Testing Strategy
 *
 * Uses MockK with pure JUnit (not AndroidJUnit4) since we're testing Flow behavior
 * that doesn't require Android framework. This makes tests fast and runnable on JVM.
 *
 * All dependencies (AlarmDao, EventSyncService, SharedPreferences) are mocked.
 * CalendarRepository is created as a real instance with mocked dependencies since
 * selectedCalendarIdsFlow is a property (not mockable). We control Flow emissions
 * by mocking SharedPreferences and triggering the OnSharedPreferenceChangeListener.
 *
 * ## Test Coverage
 *
 * 1. **Returns True When Calendars Selected**: Verifies returns true when list is not empty
 * 2. **Returns False When No Calendars Selected**: Verifies returns false when list is empty
 * 3. **Updates Reactively**: Verifies Flow emits when repository Flow emits new values
 * 4. **Maps From Repository Flow**: Verifies correct transformation from List<Long> to Boolean
 *
 * ## Implementation Notes
 *
 * Tests use kotlinx-coroutines-test's runTest for proper coroutine testing.
 * SharedPreferences is mocked to control CalendarRepository's selectedCalendarIdsFlow
 * emissions. The OnSharedPreferenceChangeListener is captured to simulate preference
 * changes and verify reactive behavior.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlarmListViewModelTest {

    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var sharedPreferencesEditor: SharedPreferences.Editor
    private lateinit var alarmDao: AlarmDao
    private lateinit var eventSyncService: EventSyncService
    private lateinit var calendarRepository: CalendarRepository
    private lateinit var viewModel: AlarmListViewModel

    // Captured listener for simulating preference changes
    private val listenerSlot = slot<SharedPreferences.OnSharedPreferenceChangeListener>()

    @Before
    fun setup() {
        // Mock android.util.Log to prevent "Method d in android.util.Log not mocked" error
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0

        // Create mocks for dependencies
        context = mockk(relaxed = true)
        sharedPreferences = mockk(relaxed = true)
        sharedPreferencesEditor = mockk(relaxed = true)
        alarmDao = mockk(relaxed = true)
        eventSyncService = mockk(relaxed = true)

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

        // Setup default mock for getAllAlarms() to prevent Flow collection errors
        every { alarmDao.getAllAlarms() } returns flowOf(emptyList())

        // Create real CalendarRepository with mocked dependencies
        // (We need a real instance because selectedCalendarIdsFlow is a property, not mockable)
        calendarRepository = CalendarRepository(context, sharedPreferences)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * Test: Has Selected Calendars Returns True When Calendars Selected
     *
     * Given: CalendarRepository.selectedCalendarIdsFlow emits [1L, 2L]
     * When: hasSelectedCalendars Flow is collected
     * Then: Flow emits true (list is not empty)
     *
     * Verifies that hasSelectedCalendars correctly maps a non-empty list of calendar IDs
     * to true. This is used by the UI to determine whether to show the "no calendars selected"
     * empty state or the "no upcoming events" empty state.
     */
    @Test
    fun testHasSelectedCalendars_returnsTrueWhenCalendarsSelected() = runTest {
        // Given: SharedPreferences contains selected calendar IDs [1L, 2L]
        val selectedIds: Set<String> = setOf("1", "2")
        every {
            sharedPreferences.getStringSet("selected_calendars", emptySet())
        } returns selectedIds

        // When: ViewModel is created and hasSelectedCalendars Flow is collected
        viewModel = AlarmListViewModel(alarmDao, eventSyncService, calendarRepository)
        val result: Boolean = viewModel.hasSelectedCalendars.first()

        // Then: Flow emits true (calendars are selected)
        assertTrue(result)
    }

    /**
     * Test: Has Selected Calendars Returns False When No Calendars Selected
     *
     * Given: CalendarRepository.selectedCalendarIdsFlow emits empty list []
     * When: hasSelectedCalendars Flow is collected
     * Then: Flow emits false (list is empty)
     *
     * Verifies that hasSelectedCalendars correctly maps an empty list to false.
     * This allows the UI to show a specific message prompting the user to select
     * calendars in settings.
     */
    @Test
    fun testHasSelectedCalendars_returnsFalseWhenNoCalendarsSelected() = runTest {
        // Given: SharedPreferences contains no selected calendars (null)
        every {
            sharedPreferences.getStringSet("selected_calendars", emptySet())
        } returns null

        // When: ViewModel is created and hasSelectedCalendars Flow is collected
        viewModel = AlarmListViewModel(alarmDao, eventSyncService, calendarRepository)
        val result: Boolean = viewModel.hasSelectedCalendars.first()

        // Then: Flow emits false (no calendars selected)
        assertFalse(result)
    }

    /**
     * Test: Has Selected Calendars Updates Reactively
     *
     * Given: CalendarRepository.selectedCalendarIdsFlow initially emits [1L]
     * And: hasSelectedCalendars Flow is being collected
     * When: Repository Flow emits new values: [] then [2L, 3L]
     * Then: hasSelectedCalendars emits: true, false, true
     *
     * Verifies that hasSelectedCalendars reactively updates when the underlying
     * selectedCalendarIdsFlow emits new values. This ensures the UI updates immediately
     * when the user changes calendar selection in settings, without needing to manually
     * refresh or restart the app.
     */
    @Test
    fun testHasSelectedCalendars_updatesReactively() = runTest {
        // Given: SharedPreferences initially contains [1L]
        every {
            sharedPreferences.getStringSet("selected_calendars", emptySet())
        } returns setOf("1")

        // Create ViewModel
        viewModel = AlarmListViewModel(alarmDao, eventSyncService, calendarRepository)

        val emissions = mutableListOf<Boolean>()

        // Start collecting hasSelectedCalendars
        val collectJob = launch {
            viewModel.hasSelectedCalendars
                .take(3) // Initial emission + 2 changes
                .toList(emissions)
        }

        // Wait for initial emission
        advanceUntilIdle()

        // Verify initial emission: true (list [1L] is not empty)
        assertEquals(1, emissions.size)
        assertTrue(emissions[0])

        // When: Preferences change to empty list
        every {
            sharedPreferences.getStringSet("selected_calendars", emptySet())
        } returns emptySet()
        listenerSlot.captured.onSharedPreferenceChanged(sharedPreferences, "selected_calendars")
        advanceUntilIdle()

        // Then: hasSelectedCalendars emits false
        assertEquals(2, emissions.size)
        assertFalse(emissions[1])

        // When: Preferences change to [2L, 3L]
        every {
            sharedPreferences.getStringSet("selected_calendars", emptySet())
        } returns setOf("2", "3")
        listenerSlot.captured.onSharedPreferenceChanged(sharedPreferences, "selected_calendars")
        advanceUntilIdle()

        // Then: hasSelectedCalendars emits true
        assertEquals(3, emissions.size)
        assertTrue(emissions[2])

        collectJob.cancel()
    }

    /**
     * Test: Has Selected Calendars Maps From Repository Flow
     *
     * Given: CalendarRepository.selectedCalendarIdsFlow emits various list sizes
     * When: hasSelectedCalendars Flow is collected for each emission
     * Then: Correctly maps to Boolean based on isEmpty() check
     *
     * Verifies the mapping logic from List<Long> to Boolean is correct for all cases:
     * - Empty list [] -> false
     * - Single element [1L] -> true
     * - Multiple elements [1L, 2L, 3L] -> true
     *
     * This test comprehensively validates the core mapping transformation logic.
     */
    @Test
    fun testHasSelectedCalendars_mapsFromRepositoryFlow() = runTest {
        // Test Case 1: Empty list -> false
        every {
            sharedPreferences.getStringSet("selected_calendars", emptySet())
        } returns emptySet()

        viewModel = AlarmListViewModel(alarmDao, eventSyncService, calendarRepository)
        val emptyResult: Boolean = viewModel.hasSelectedCalendars.first()
        assertFalse("Empty list should map to false", emptyResult)

        // Test Case 2: Single element [1L] -> true
        every {
            sharedPreferences.getStringSet("selected_calendars", emptySet())
        } returns setOf("1")

        // Create new repository and viewModel for clean state
        val calendarRepository2: CalendarRepository = CalendarRepository(context, sharedPreferences)
        val viewModel2: AlarmListViewModel = AlarmListViewModel(alarmDao, eventSyncService, calendarRepository2)

        val singleResult: Boolean = viewModel2.hasSelectedCalendars.first()
        assertTrue("Single element list should map to true", singleResult)

        // Test Case 3: Multiple elements [1L, 2L, 3L] -> true
        every {
            sharedPreferences.getStringSet("selected_calendars", emptySet())
        } returns setOf("1", "2", "3")

        // Create new repository and viewModel for clean state
        val calendarRepository3: CalendarRepository = CalendarRepository(context, sharedPreferences)
        val viewModel3: AlarmListViewModel = AlarmListViewModel(alarmDao, eventSyncService, calendarRepository3)

        val multipleResult: Boolean = viewModel3.hasSelectedCalendars.first()
        assertTrue("Multiple element list should map to true", multipleResult)
    }
}
