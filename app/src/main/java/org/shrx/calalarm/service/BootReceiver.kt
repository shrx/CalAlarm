// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 github.com/shrx

package org.shrx.calalarm.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.shrx.calalarm.data.local.AppDatabase
import org.shrx.calalarm.data.local.entities.ScheduledAlarm

/**
 * BroadcastReceiver that handles device boot completion.
 *
 * This receiver will:
 * - Receive BOOT_COMPLETED broadcast
 * - Restore all scheduled alarms from database
 * - Reschedule alarms with AlarmManager
 *
 * Note: AlarmManager alarms are cleared on device reboot,
 * so we must reschedule them.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Use goAsync() since we need to do async database operations
            val pendingResult: PendingResult = goAsync()

            // Query database and reschedule alarms
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val database: AppDatabase = AppDatabase.getInstance()
                    val alarmScheduler: AlarmScheduler = AlarmScheduler(context)
                    val now: Long = System.currentTimeMillis()

                    // Get all alarms from database
                    val allAlarms: List<ScheduledAlarm> = database.alarmDao().getAllAlarmsList()

                    // Filter for future alarms and reschedule them
                    allAlarms
                        .filter { alarm ->
                            alarm.eventStartTime + alarm.snoozeOffset > now
                        }
                        .forEach { alarm ->
                            alarmScheduler.scheduleAlarm(alarm)
                        }
                } finally {
                    // Notify that the async work is complete
                    pendingResult.finish()
                }
            }
        }
    }
}
