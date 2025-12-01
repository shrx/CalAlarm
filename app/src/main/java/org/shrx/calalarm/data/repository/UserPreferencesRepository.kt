// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 github.com/shrx

package org.shrx.calalarm.data.repository

import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.max

data class UserPreferences(
    val snoozeDelayMinutes: Long,
    val showNextAlarmNotification: Boolean
)

/**
 * Repository exposing user-configurable settings stored in SharedPreferences.
 *
 * Currently supports two settings:
 * - Snooze delay length in minutes (must be >= 1 minute)
 * - Whether to show a persistent notification for the next upcoming alarm
 */
class UserPreferencesRepository(
    private val sharedPreferences: SharedPreferences
) {

    companion object {
        const val PREFERENCES_FILE_NAME: String = "calalarm_prefs"
        private const val KEY_SNOOZE_DELAY_MINUTES: String = "snooze_delay_minutes"
        private const val KEY_SHOW_NEXT_ALARM_NOTIFICATION: String = "show_next_alarm_notification"
        private const val DEFAULT_SNOOZE_MINUTES: Long = 10L
    }

    /**
     * Flow emitting the latest [UserPreferences] snapshot whenever SharedPreferences change.
     */
    val preferencesFlow: Flow<UserPreferences> = callbackFlow {
        trySend(readPreferences())

        val listener: SharedPreferences.OnSharedPreferenceChangeListener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == KEY_SNOOZE_DELAY_MINUTES || key == KEY_SHOW_NEXT_ALARM_NOTIFICATION) {
                    trySend(readPreferences())
                }
            }

        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)

        awaitClose {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    fun readPreferences(): UserPreferences {
        return UserPreferences(
            snoozeDelayMinutes = getSnoozeDelayMinutes(),
            showNextAlarmNotification = isNextAlarmNotificationEnabled()
        )
    }

    fun getSnoozeDelayMinutes(): Long {
        val storedValue: Long = sharedPreferences.getLong(KEY_SNOOZE_DELAY_MINUTES, DEFAULT_SNOOZE_MINUTES)
        return max(1L, storedValue)
    }

    fun setSnoozeDelayMinutes(minutes: Long) {
        val sanitized: Long = max(1L, minutes)
        sharedPreferences.edit()
            .putLong(KEY_SNOOZE_DELAY_MINUTES, sanitized)
            .apply()
    }

    fun isNextAlarmNotificationEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_SHOW_NEXT_ALARM_NOTIFICATION, false)
    }

    fun setNextAlarmNotificationEnabled(enabled: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_SHOW_NEXT_ALARM_NOTIFICATION, enabled)
            .apply()
    }
}
