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
import org.shrx.calalarm.data.local.AlarmDao
import org.shrx.calalarm.data.local.AppDatabase
import org.shrx.calalarm.data.repository.CalendarRepository
import org.shrx.calalarm.data.repository.UserPreferencesRepository
import org.shrx.calalarm.domain.EventSyncService
import org.shrx.calalarm.service.AlarmScheduler
import org.shrx.calalarm.service.NextAlarmNotifier

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
}
