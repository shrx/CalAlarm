// SPDX-License-Identifier: GPL-3.0-or-later
package org.shrx.calalarm.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.shrx.calalarm.MainActivity
import org.shrx.calalarm.data.local.AlarmDao
import org.shrx.calalarm.data.local.entities.ScheduledAlarm
import org.shrx.calalarm.data.repository.UserPreferences
import org.shrx.calalarm.data.repository.UserPreferencesRepository

private const val NEXT_ALARM_CHANNEL_ID: String = "next_alarm_channel"

class NextAlarmNotifier(
    private val context: Context,
    private val alarmDao: AlarmDao,
    private val userPreferencesRepository: UserPreferencesRepository
) {

    private var scope: CoroutineScope? = null
    private var monitoringJob: Job? = null
    private val notificationManager: NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    fun start() {
        if (monitoringJob != null) {
            return
        }
        val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = coroutineScope
        monitoringJob = coroutineScope.launch {
            combine(
                alarmDao.getAllAlarms(),
                userPreferencesRepository.preferencesFlow
            ) { alarms: List<ScheduledAlarm>, prefs: UserPreferences ->
                alarms to prefs
            }.collect { (alarms: List<ScheduledAlarm>, prefs: UserPreferences) ->
                handleUpdate(alarms, prefs)
            }
        }
    }

    fun stop() {
        monitoringJob?.cancel()
        monitoringJob = null
        scope?.cancel()
        scope = null
    }

    private fun handleUpdate(
        alarms: List<ScheduledAlarm>,
        prefs: UserPreferences
    ) {
        if (!prefs.showNextAlarmNotification) {
            notificationManager.cancel(NEXT_ALARM_NOTIFICATION_ID)
            return
        }
        val snoozedAlarm: ScheduledAlarm? = findSnoozedAlarm(alarms)
        if (snoozedAlarm != null) {
            showNotification(snoozedAlarm, isSnoozed = true)
            return
        }
        val nextAlarm: ScheduledAlarm? = findNextAlarm(alarms)
        if (nextAlarm != null) {
            showNotification(nextAlarm, isSnoozed = false)
        } else {
            notificationManager.cancel(NEXT_ALARM_NOTIFICATION_ID)
        }
    }

    private fun findSnoozedAlarm(alarms: List<ScheduledAlarm>): ScheduledAlarm? {
        val now: Long = System.currentTimeMillis()
        return alarms
            .filter { alarm -> alarm.snoozeOffset > 0 }
            .map { alarm -> alarm to (alarm.eventStartTime + alarm.snoozeOffset) }
            .filter { (_, triggerTime) -> triggerTime > now }
            .minByOrNull { it.second }
            ?.first
    }

    private fun findNextAlarm(alarms: List<ScheduledAlarm>): ScheduledAlarm? {
        val now: Long = System.currentTimeMillis()
        return alarms
            .filter { alarm -> alarm.snoozeOffset == 0L && alarm.eventStartTime > now }
            .minByOrNull { alarm -> alarm.eventStartTime }
    }

    private fun showNotification(alarm: ScheduledAlarm, isSnoozed: Boolean) {
        val displayTime: Long = alarm.eventStartTime + alarm.snoozeOffset
        val title: String = if (isSnoozed) "Snoozed alarm" else "Next alarm"

        val intent: Intent = Intent(context, MainActivity::class.java)
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            NEXT_ALARM_PENDING_INTENT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: android.app.Notification = NotificationCompat.Builder(context, NEXT_ALARM_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(alarm.eventTitle)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setShowWhen(true)
            .setWhen(displayTime)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        notificationManager.notify(NEXT_ALARM_NOTIFICATION_ID, notification)
    }
}
