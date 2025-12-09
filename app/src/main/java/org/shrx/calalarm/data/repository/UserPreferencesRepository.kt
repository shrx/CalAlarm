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
    val showNextAlarmNotification: Boolean,
    val syncIntervalMinutes: Long
)

/**
 * Repository exposing user-configurable settings stored in SharedPreferences.
 *
 * Currently supports three settings:
 * - Snooze delay length in minutes (must be >= 1 minute)
 * - Whether to show a persistent notification for the next upcoming alarm
 * - Periodic sync interval in minutes (must be >= 15 minutes per WorkManager constraint)
 */
class UserPreferencesRepository(
    private val sharedPreferences: SharedPreferences
) {

    companion object {
        const val PREFERENCES_FILE_NAME: String = "calalarm_prefs"
        private const val KEY_SNOOZE_DELAY_MINUTES: String = "snooze_delay_minutes"
        private const val KEY_SHOW_NEXT_ALARM_NOTIFICATION: String = "show_next_alarm_notification"
        private const val KEY_SYNC_INTERVAL_MINUTES: String = "sync_interval_minutes"
        private const val DEFAULT_SNOOZE_MINUTES: Long = 10L
        private const val DEFAULT_SYNC_INTERVAL_MINUTES: Long = 15L
    }

    /**
     * Flow emitting the latest [UserPreferences] snapshot whenever SharedPreferences change.
     */
    val preferencesFlow: Flow<UserPreferences> = callbackFlow {
        trySend(readPreferences())

        val listener: SharedPreferences.OnSharedPreferenceChangeListener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == KEY_SNOOZE_DELAY_MINUTES || key == KEY_SHOW_NEXT_ALARM_NOTIFICATION || key == KEY_SYNC_INTERVAL_MINUTES) {
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
            showNextAlarmNotification = isNextAlarmNotificationEnabled(),
            syncIntervalMinutes = getSyncIntervalMinutes()
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

    /**
     * Gets the sync interval in minutes for periodic background sync.
     * Minimum 15 minutes enforced by WorkManager platform constraint.
     */
    fun getSyncIntervalMinutes(): Long {
        val storedValue: Long = sharedPreferences.getLong(KEY_SYNC_INTERVAL_MINUTES, DEFAULT_SYNC_INTERVAL_MINUTES)
        return max(15L, storedValue)
    }

    /**
     * Sets the sync interval in minutes for periodic background sync.
     * Minimum 15 minutes enforced by WorkManager platform constraint.
     */
    fun setSyncIntervalMinutes(minutes: Long) {
        val sanitized: Long = max(15L, minutes)
        sharedPreferences.edit()
            .putLong(KEY_SYNC_INTERVAL_MINUTES, sanitized)
            .apply()
    }
}
