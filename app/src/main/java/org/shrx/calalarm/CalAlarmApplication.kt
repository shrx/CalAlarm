// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 github.com/shrx

package org.shrx.calalarm

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.SharedPreferences
import org.shrx.calalarm.data.local.AlarmDao
import org.shrx.calalarm.data.local.AppDatabase
import org.shrx.calalarm.data.repository.UserPreferencesRepository
import org.shrx.calalarm.service.NextAlarmNotifier

/**
 * Application class for CalAlarm.
 *
 * Initializes the Room database. Calendar monitoring and sync are started
 * in MainActivity after READ_CALENDAR permission is granted.
 */
class CalAlarmApplication : Application() {

    private lateinit var nextAlarmNotifier: NextAlarmNotifier

    override fun onCreate() {
        super.onCreate()

        // Initialize Room database
        AppDatabase.initialize(this)

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

        val alarmDao: AlarmDao = AppDatabase.getInstance().alarmDao()
        val sharedPreferences: SharedPreferences = getSharedPreferences(
            UserPreferencesRepository.PREFERENCES_FILE_NAME,
            MODE_PRIVATE
        )
        val userPreferencesRepository: UserPreferencesRepository = UserPreferencesRepository(sharedPreferences)
        nextAlarmNotifier = NextAlarmNotifier(applicationContext, alarmDao, userPreferencesRepository)
        nextAlarmNotifier.start()
    }
}
