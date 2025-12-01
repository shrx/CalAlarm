// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 github.com/shrx

package org.shrx.calalarm.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.shrx.calalarm.data.local.AppDatabase
import org.shrx.calalarm.ui.alarm.AlarmActivity

/**
 * BroadcastReceiver triggered by AlarmManager to present the ringing alarm UI.
 *
 * Confirms the calendar event still exists and routes the user into AlarmActivity via
 * a full-screen notification while also keeping the status-area notification updated.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Extract event ID from intent extras
        val eventId: Long = intent.getLongExtra(EXTRA_EVENT_ID, 0L)
        if (eventId == 0L) return

        // Validate that the event still exists in the calendar
        if (!checkEventExists(context, eventId)) {
            // Event was deleted - clean up alarm from database
            val database: AppDatabase = AppDatabase.getInstance()
            CoroutineScope(Dispatchers.IO).launch {
                database.alarmDao().deleteAlarm(eventId)
            }
            return
        }

        // Create PendingIntent for full-screen intent
        val eventTitle: String = intent.getStringExtra(EXTRA_EVENT_TITLE)!!

        val alarmActivityIntent: Intent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_EVENT_ID, eventId)
            putExtra(EXTRA_EVENT_TITLE, eventTitle)
        }

        val alarmActivityPendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            eventId.toInt(),
            alarmActivityIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager: NotificationManager = context.getSystemService(NotificationManager::class.java)
        // Hide the passive status notification while the ringing alarm is active.
        notificationManager.cancel(NEXT_ALARM_NOTIFICATION_ID)

        val notificationTitle: String = if (intent.getBooleanExtra(EXTRA_IS_SNOOZED, false)) {
            "Snoozed alarm"
        } else {
            "Alarm"
        }

        // Build the ringing alarm notification that supplies a full-screen intent.
        val notification: Notification = NotificationCompat.Builder(context, "alarm_channel")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(notificationTitle)
            .setContentText(eventTitle)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(alarmActivityPendingIntent, true)
            .setContentIntent(alarmActivityPendingIntent)
            .setAutoCancel(false)
            .setWhen(System.currentTimeMillis())
            .build()

        notificationManager.notify(eventId.toInt(), notification)
    }

    private fun checkEventExists(context: Context, eventId: Long): Boolean {
        val projection: Array<String> = arrayOf(CalendarContract.Events._ID)
        val selection: String = "${CalendarContract.Events._ID} = $eventId AND ${CalendarContract.Events.DELETED} != 1"

        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            null,
            null
        )?.use { cursor ->
            return cursor.count > 0
        }
        return false
    }
}
