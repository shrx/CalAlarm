// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 github.com/shrx

package org.shrx.calalarm

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.MutableStateFlow
import org.shrx.calalarm.data.local.AlarmDao
import org.shrx.calalarm.data.local.AppDatabase
import org.shrx.calalarm.data.repository.CalendarRepository
import org.shrx.calalarm.domain.EventSyncService
import org.shrx.calalarm.data.repository.UserPreferencesRepository
import org.shrx.calalarm.service.AlarmScheduler
import org.shrx.calalarm.ui.alarmlist.AlarmListScreen
import org.shrx.calalarm.ui.alarmlist.AlarmListViewModel
import org.shrx.calalarm.ui.info.InfoScreen
import org.shrx.calalarm.ui.permissions.PermissionRequiredScreen
import org.shrx.calalarm.ui.settings.SettingsScreen
import org.shrx.calalarm.ui.settings.SettingsViewModel
import org.shrx.calalarm.ui.theme.CalAlarmTheme

private const val REQUEST_CALENDAR_CODE: Int = 100

/**
 * Main entry point for the CalAlarm application.
 *
 * This activity handles:
 * - Permission checks (POST_NOTIFICATIONS, READ_CALENDAR, USE_FULL_SCREEN_INTENT)
 * - Showing permission request screens when permissions are missing
 * - Navigation to main app when all permissions are granted
 *
 * setContent() is called ONCE in onCreate(). All permission changes are handled
 * reactively through mutable state, preventing composition destruction.
 */
class MainActivity : ComponentActivity() {
    private val permissionStateFlow: MutableStateFlow<PermissionState> = MutableStateFlow(PermissionState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        // Check initial permissions
        updatePermissionState()

        // Set content ONCE - all permission changes handled reactively
        setContent {
            CalAlarmTheme {
                PermissionGate()
            }
        }

    }

    override fun onResume() {
        super.onResume()
        // Update permission state when activity resumes
        updatePermissionState()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Update permission state when permission result received
        updatePermissionState()
    }

    private fun updatePermissionState() {
        val notificationManager: NotificationManager = getSystemService(NotificationManager::class.java)
        val newState: PermissionState = PermissionState(
            hasPostNotifications = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
            hasCalendar = checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED,
            hasFullScreenIntent = notificationManager.canUseFullScreenIntent()
        )
        permissionStateFlow.value = newState
    }

    @Composable
    private fun PermissionGate() {
        val permissionState: PermissionState by permissionStateFlow.collectAsStateWithLifecycle()

        when {
            !permissionState.hasPostNotifications -> {
                PermissionRequiredScreen(
                    title = "Notification Permission Required",
                    description = "CalAlarm needs permission to show notifications for alarms.",
                    onButtonClick = {
                        requestPermissions(
                            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                            REQUEST_CALENDAR_CODE
                        )
                    }
                )
            }
            !permissionState.hasCalendar -> {
                PermissionRequiredScreen(
                    title = "Calendar Permission Required",
                    description = "CalAlarm needs access to your calendar to schedule alarms for events.",
                    onButtonClick = {
                        requestPermissions(
                            arrayOf(Manifest.permission.READ_CALENDAR),
                            REQUEST_CALENDAR_CODE
                        )
                    }
                )
            }
            !permissionState.hasFullScreenIntent -> {
                PermissionRequiredScreen(
                    title = "Full-Screen Alarm Permission Required",
                    description = "CalAlarm needs permission to show alarms over the lock screen. Please enable 'Display over other apps' in Settings.",
                    buttonText = "Open Settings",
                    onButtonClick = {
                        val intent: Intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                        startActivity(intent)
                    }
                )
            }
            else -> {
                MainAppContent()
            }
        }
    }

    @Composable
    private fun MainAppContent() {
        val navController: NavHostController = rememberNavController()

        // Create dependencies and ViewModels (remember to prevent recreation on recomposition)
        val alarmDao: AlarmDao = remember {
            AppDatabase.getInstance().alarmDao()
        }
        val alarmScheduler: AlarmScheduler = remember {
            AlarmScheduler(this)
        }
        val sharedPreferences: android.content.SharedPreferences = remember {
            getSharedPreferences(UserPreferencesRepository.PREFERENCES_FILE_NAME, Context.MODE_PRIVATE)
        }
        val userPreferencesRepository: UserPreferencesRepository = remember {
            UserPreferencesRepository(sharedPreferences)
        }
        val calendarRepository: CalendarRepository = remember {
            CalendarRepository(this, sharedPreferences)
        }
        val eventSyncService: EventSyncService = remember {
            EventSyncService(this, alarmDao, alarmScheduler, calendarRepository)
        }
        val alarmListViewModel: AlarmListViewModel = remember {
            AlarmListViewModel(alarmDao, eventSyncService, calendarRepository)
        }
        val settingsViewModel: SettingsViewModel = remember {
            SettingsViewModel(calendarRepository, userPreferencesRepository)
        }
        // Start observing calendar database and selection changes
        LaunchedEffect(Unit) {
            eventSyncService.startMonitoring()
        }

        NavHost(
            navController = navController,
            startDestination = "alarm_list",
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            composable("alarm_list") {
                AlarmListScreen(
                    viewModel = alarmListViewModel,
                    onNavigateToSettings = { navController.navigate("settings") },
                    onNavigateToInfo = { navController.navigate("info") }
                )
            }
            composable("settings") {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("info") {
                InfoScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }

    private data class PermissionState(
        val hasPostNotifications: Boolean = false,
        val hasCalendar: Boolean = false,
        val hasFullScreenIntent: Boolean = false
    )
}
