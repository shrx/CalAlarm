// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 github.com/shrx

package org.shrx.calalarm.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.shrx.calalarm.data.calendar.models.CalendarInfo
import org.shrx.calalarm.data.repository.CalendarRepository
import org.shrx.calalarm.data.repository.UserPreferences
import org.shrx.calalarm.data.repository.UserPreferencesRepository

/**
 * ViewModel for the Settings screen where users select which calendars to monitor.
 *
 * Manages the state of available calendars and the user's current selection.
 * Loads calendar data on initialization and provides methods to toggle selections
 * and persist them to SharedPreferences via the CalendarRepository.
 *
 * @property calendarRepository Repository for accessing calendar data and saving selections
 * @property userPreferencesRepository Repository exposing user-configurable alarm preferences
 */
class SettingsViewModel(
    private val calendarRepository: CalendarRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _calendars: MutableStateFlow<List<CalendarInfo>> = MutableStateFlow(emptyList())
    val calendars: StateFlow<List<CalendarInfo>> = _calendars

    private val _selectedIds: MutableStateFlow<Set<Long>> = MutableStateFlow(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds

    private val _userPreferences: MutableStateFlow<UserPreferences> =
        MutableStateFlow(userPreferencesRepository.readPreferences())
    val userPreferences: StateFlow<UserPreferences> = _userPreferences

    private val _snoozeDelayInput: MutableStateFlow<String> =
        MutableStateFlow(_userPreferences.value.snoozeDelayMinutes.toString())
    val snoozeDelayInput: StateFlow<String> = _snoozeDelayInput

    private val _snoozeDelayError: MutableStateFlow<String?> = MutableStateFlow(null)
    val snoozeDelayError: StateFlow<String?> = _snoozeDelayError

    init {
        android.util.Log.d("SettingsViewModel", "ViewModel created: ${this.hashCode()}")
        viewModelScope.launch {
            _calendars.value = calendarRepository.getAvailableCalendars()
            _selectedIds.value = calendarRepository.getSelectedCalendarIds().toSet()
        }

        viewModelScope.launch {
            userPreferencesRepository.preferencesFlow.collect { prefs ->
                _userPreferences.value = prefs
                _snoozeDelayInput.value = prefs.snoozeDelayMinutes.toString()
            }
        }
    }

    /**
     * Toggles the selection state of a calendar.
     *
     * If the calendar is currently selected, it will be removed from the selection.
     * If the calendar is not selected, it will be added to the selection.
     *
     * @param calendarId The ID of the calendar to toggle
     */
    fun toggleCalendarSelection(calendarId: Long) {
        _selectedIds.value = if (_selectedIds.value.contains(calendarId)) {
            _selectedIds.value - calendarId
        } else {
            _selectedIds.value + calendarId
        }
    }

    /**
     * Saves the current calendar selection to SharedPreferences.
     *
     * Persists the selected calendar IDs via the CalendarRepository.
     * Called immediately after toggling any calendar selection.
     */
    fun saveSelection() {
        calendarRepository.saveSelectedCalendarIds(_selectedIds.value.toList())
    }

    fun onSnoozeDelayInputChange(input: String) {
        val sanitized: String = input.filter { it.isDigit() }
        _snoozeDelayInput.value = sanitized
        val minutes: Long? = sanitized.toLongOrNull()
        if (minutes == null || minutes < 1) {
            _snoozeDelayError.value = "Enter at least 1 minute"
            return
        }
        _snoozeDelayError.value = null
        if (minutes != _userPreferences.value.snoozeDelayMinutes) {
            userPreferencesRepository.setSnoozeDelayMinutes(minutes)
        }
    }

    fun setNextAlarmNotificationEnabled(enabled: Boolean) {
        userPreferencesRepository.setNextAlarmNotificationEnabled(enabled)
    }
}
