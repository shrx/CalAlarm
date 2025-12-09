// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 github.com/shrx

package org.shrx.calalarm

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.shrx.calalarm.data.local.AlarmDao
import org.shrx.calalarm.data.local.AppDatabase
import org.shrx.calalarm.data.repository.CalendarRepository
import org.shrx.calalarm.data.repository.UserPreferencesRepository
import org.shrx.calalarm.domain.EventSyncService
import org.shrx.calalarm.service.AlarmScheduler
import org.shrx.calalarm.service.NextAlarmNotifier
import org.shrx.calalarm.service.SyncWorker
import java.util.concurrent.TimeUnit

/**
 * Application class for CalAlarm.
 *
 * Initializes application-wide singletons and ensures background sync
 * monitoring is started whenever calendar permission is available.
 */
class CalAlarmApplication : Application() {

    lateinit var alarmDao: AlarmDao
        private set

    lateinit var userPreferencesRepository: UserPreferencesRepository
        private set

    lateinit var calendarRepository: CalendarRepository
        private set

    lateinit var eventSyncService: EventSyncService
        private set

    lateinit var alarmScheduler: AlarmScheduler
        private set

    private lateinit var nextAlarmNotifier: NextAlarmNotifier
    private val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        // Initialize Room database
        AppDatabase.initialize(this)
        alarmDao = AppDatabase.getInstance().alarmDao()

        val sharedPreferences: SharedPreferences = getSharedPreferences(
            UserPreferencesRepository.PREFERENCES_FILE_NAME,
            MODE_PRIVATE
        )

        userPreferencesRepository = UserPreferencesRepository(sharedPreferences)
        calendarRepository = CalendarRepository(applicationContext, sharedPreferences)
        alarmScheduler = AlarmScheduler(applicationContext)
        eventSyncService = EventSyncService(
            applicationContext,
            alarmDao,
            alarmScheduler,
            calendarRepository
        )

        val notificationManager: NotificationManager = getSystemService(NotificationManager::class.java)

        val alarmChannel: NotificationChannel = NotificationChannel(
            "alarm_channel",
            "Alarms",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Calendar event alarms"
            setSound(null, null)
            enableVibration(false)
        }
        notificationManager.createNotificationChannel(alarmChannel)

        val nextAlarmChannel: NotificationChannel = NotificationChannel(
            "next_alarm_channel",
            "Next Alarm",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent notification for the next upcoming alarm"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(nextAlarmChannel)

        nextAlarmNotifier = NextAlarmNotifier(applicationContext, alarmDao, userPreferencesRepository)
        nextAlarmNotifier.start()

        ensureEventSyncMonitoring()
        schedulePeriodicSync()

        applicationScope.launch {
            userPreferencesRepository.preferencesFlow
                .map { it.syncIntervalMinutes }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    schedulePeriodicSync()
                }
        }
    }

    /**
     * Starts calendar monitoring if the READ_CALENDAR permission is granted.
     *
     * Safe to call multiple times; monitoring is started only once.
     */
    fun ensureEventSyncMonitoring() {
        val hasCalendarPermission: Boolean = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        if (hasCalendarPermission) {
            eventSyncService.startMonitoring()
        }
    }

    /**
     * Schedules periodic background sync using WorkManager.
     * Reschedules when called again with updated interval.
     *
     * Note: WorkManager enforces a minimum interval of 15 minutes for periodic work.
     * Shorter intervals will be delayed to the 15-minute mark by the platform.
     */
    private fun schedulePeriodicSync() {
        val intervalMinutes: Long = userPreferencesRepository.getSyncIntervalMinutes()

        val workRequest: androidx.work.PeriodicWorkRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            intervalMinutes,
            TimeUnit.MINUTES
        )
            .setInitialDelay(intervalMinutes, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "periodic_calendar_sync",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }
}
