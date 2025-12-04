// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 github.com/shrx

package org.shrx.calalarm.ui.alarm

import android.app.KeyguardManager
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import org.shrx.calalarm.data.local.AlarmDao
import org.shrx.calalarm.data.local.AppDatabase
import org.shrx.calalarm.data.local.entities.ScheduledAlarm
import org.shrx.calalarm.data.repository.UserPreferencesRepository
import org.shrx.calalarm.service.EXTRA_EVENT_ID
import org.shrx.calalarm.service.EXTRA_EVENT_TITLE
import org.shrx.calalarm.util.DateTimeFormatter

/**
 * Full-screen alarm activity that displays when an alarm is triggered.
 *
 * This activity:
 * - Shows over lock screen
 * - Plays alarm sound (system default alarm)
 * - Vibrates device with repeating pattern
 * - Displays event title
 * - Provides DISMISS button to stop alarm
 *
 * Intent extras:
 * - EVENT_ID (Long): The calendar event ID
 * - EVENT_TITLE (String): The event title to display
 */
class AlarmActivity : ComponentActivity() {
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var vibrator: Vibrator
    private lateinit var alarmDao: AlarmDao
    private lateinit var userPreferencesRepository: UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lock screen
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val keyguardManager: KeyguardManager = getSystemService(KeyguardManager::class.java)
        keyguardManager.requestDismissKeyguard(this, null)

        // Extract intent data
        val eventId: Long = intent.getLongExtra(EXTRA_EVENT_ID, 0L)
        val eventTitle: String = intent.getStringExtra(EXTRA_EVENT_TITLE)!!

        // Initialize DAO
        alarmDao = AppDatabase.getInstance().alarmDao()
        userPreferencesRepository = UserPreferencesRepository(
            getSharedPreferences(UserPreferencesRepository.PREFERENCES_FILE_NAME, MODE_PRIVATE)
        )

        // Start alarm sound and vibration
        startAlarmSound()
        startVibration()

        setContent {
            AlarmActivityTheme {
                AlarmScreenContent(
                    eventTitle = eventTitle,
                    onSnooze = { snoozeAlarm(eventId) },
                    onDismiss = { dismissAlarm(eventId) }
                )
            }
        }
    }

    /**
     * Starts playing the system default alarm sound in a loop.
     */
    private fun startAlarmSound() {
        val alarmUri: android.net.Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        mediaPlayer = MediaPlayer().apply {
            setDataSource(this@AlarmActivity, alarmUri)
            isLooping = true
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            prepare()
            start()
        }
    }

    /**
     * Starts vibration with a repeating pattern.
     */
    private fun startVibration() {
        vibrator = getSystemService(Vibrator::class.java)
        val pattern: LongArray = longArrayOf(0, 1000, 500, 1000, 500)
        val effect: VibrationEffect = VibrationEffect.createWaveform(pattern, 0)
        vibrator.vibrate(effect)
    }

    /**
     * Snoozes the alarm by stopping sound/vibration, calculating snooze offset (10 minutes from now), and rescheduling.
     * If alarm is deleted from database (race condition), it is recreated using intent data.
     * Notification remains visible to show snoozed alarm.
     */
    private fun snoozeAlarm(eventId: Long) {
        stopSoundAndVibration()

        val eventTitle: String = intent.getStringExtra(EXTRA_EVENT_TITLE)!!
        val notificationManager: NotificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.cancel(eventId.toInt())

        lifecycleScope.launch {
            // Get alarm from database, or recreate if deleted (race condition)
            val alarm: ScheduledAlarm = alarmDao.getAlarmById(eventId) ?: ScheduledAlarm(
                eventId = eventId,
                eventTitle = eventTitle,
                eventStartTime = System.currentTimeMillis(),
                calendarId = 0L,
                snoozeOffset = 0L
            )

            // Calculate snooze offset using user-configured delay
            val snoozeDelayMinutes: Long = userPreferencesRepository.getSnoozeDelayMinutes()
            val snoozeDurationMs: Long = TimeUnit.MINUTES.toMillis(snoozeDelayMinutes)
            val currentOffset: Long = System.currentTimeMillis() - alarm.eventStartTime
            val newOffset: Long = currentOffset + snoozeDurationMs
            val snoozedAlarm: ScheduledAlarm = alarm.copy(snoozeOffset = newOffset)

            // Save to database (insert or replace)
            alarmDao.insertAlarm(snoozedAlarm)

            // Reschedule alarm
            val alarmScheduler: org.shrx.calalarm.service.AlarmScheduler =
                org.shrx.calalarm.service.AlarmScheduler(this@AlarmActivity)
            alarmScheduler.scheduleAlarm(snoozedAlarm)

            finish()
        }
    }

    /**
     * Dismisses the alarm by stopping sound/vibration, removing notification, deleting from database, and finishing.
     */
    private fun dismissAlarm(eventId: Long) {
        stopSoundAndVibration()

        // Remove notification
        val notificationManager: NotificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.cancel(eventId.toInt())

        lifecycleScope.launch {
            alarmDao.deleteAlarm(eventId)
        }

        finish()
    }

    /**
     * Stops alarm sound and vibration.
     */
    private fun stopSoundAndVibration() {
        if (::mediaPlayer.isInitialized) {
            mediaPlayer.release()
        }
        if (::vibrator.isInitialized) {
            vibrator.cancel()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSoundAndVibration()
    }

    /**
     * Prevents dismissing with back button - user must press DISMISS.
     */
    @Deprecated("Deprecated in Java")
    @Suppress("MissingSuperCall")
    override fun onBackPressed() {
        // Do nothing - user must press DISMISS button
    }
}

/**
 * Composable UI for the alarm screen.
 *
 * Shows:
 * - Red full-screen background
 * - Current time (large, bold)
 * - Bell icon
 * - Event title (max 3 lines)
 * - SNOOZE button (secondary)
 * - DISMISS button (primary)
 */
@Composable
fun AlarmScreenContent(
    eventTitle: String,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AlarmTheme.colors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Current time display
            var currentTime: String by remember { mutableStateOf(DateTimeFormatter.getCurrentTime()) }

            LaunchedEffect(Unit) {
                while (true) {
                    delay(1000)
                    currentTime = DateTimeFormatter.getCurrentTime()
                }
            }

            Text(
                text = currentTime,
                color = AlarmTheme.colors.onBackground,
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Alarm icon
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                tint = AlarmTheme.colors.onBackground,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Event title
            Text(
                text = eventTitle,
                color = AlarmTheme.colors.onBackground,
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(48.dp))

            // SNOOZE button
            Button(
                onClick = onSnooze,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AlarmTheme.colors.buttonSecondary,
                    contentColor = AlarmTheme.colors.onButton
                )
            ) {
                Text(
                    text = "SNOOZE",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // DISMISS button
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AlarmTheme.colors.buttonPrimary,
                    contentColor = AlarmTheme.colors.onButton
                )
            ) {
                Text(
                    text = "DISMISS",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
