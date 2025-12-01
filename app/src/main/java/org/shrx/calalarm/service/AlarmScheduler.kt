// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 github.com/shrx

package org.shrx.calalarm.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.shrx.calalarm.data.local.entities.ScheduledAlarm

/**
 * Wrapper for Android's AlarmManager to schedule and cancel alarms for calendar events.
 *
 * @property context Android context for accessing AlarmManager and creating Intents
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    /**
     * Schedules an exact alarm for the given scheduled alarm.
     * Trigger time = eventStartTime + snoozeOffset.
     * For non-snoozed alarms: snoozeOffset = 0, so triggers at eventStartTime.
     * For snoozed alarms: snoozeOffset = calculated offset to achieve 10min delay from snooze button press.
     *
     * @param alarm The ScheduledAlarm containing event details and trigger time
     */
    fun scheduleAlarm(alarm: ScheduledAlarm) {
        val intent: Intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_EVENT_ID, alarm.eventId)
            putExtra(EXTRA_EVENT_TITLE, alarm.eventTitle)
            putExtra(EXTRA_IS_SNOOZED, alarm.snoozeOffset > 0L)
        }

        val pendingIntent: PendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.eventId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            alarm.eventStartTime + alarm.snoozeOffset,
            pendingIntent
        )
    }

    /**
     * Cancels an existing alarm for the given scheduled alarm.
     *
     * @param alarm The ScheduledAlarm to cancel
     */
    fun cancelAlarm(alarm: ScheduledAlarm) {
        val intent: Intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent: PendingIntent? = PendingIntent.getBroadcast(
            context,
            alarm.eventId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )

        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }
}
