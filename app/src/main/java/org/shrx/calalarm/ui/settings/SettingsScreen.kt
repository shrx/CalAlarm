// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 github.com/shrx

package org.shrx.calalarm.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.shrx.calalarm.data.calendar.models.CalendarInfo
import org.shrx.calalarm.data.repository.UserPreferences

/**
 * Settings screen for selecting which calendars to monitor for alarms.
 *
 * Displays a list of all calendars available on the device with checkboxes
 * allowing the user to select which calendars should trigger alarms.
 * Calendar selections are saved immediately when toggled.
 *
 * @param viewModel The ViewModel managing calendar data and selection state
 * @param onBack Callback invoked when the user taps the back button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val calendars: List<CalendarInfo> by viewModel.calendars.collectAsState()
    val selectedIds: Set<Long> by viewModel.selectedIds.collectAsState()
    val preferences: UserPreferences by viewModel.userPreferences.collectAsState()
    val snoozeInput: String by viewModel.snoozeDelayInput.collectAsState()
    val snoozeError: String? by viewModel.snoozeDelayError.collectAsState()
    val syncIntervalInput: String by viewModel.syncIntervalInput.collectAsState()
    val syncIntervalError: String? by viewModel.syncIntervalError.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
            item {
                SnoozeDelaySetting(
                    value = snoozeInput,
                    errorMessage = snoozeError,
                    onValueChange = viewModel::onSnoozeDelayInputChange
                )
            }
            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
            item {
                SyncIntervalSetting(
                    value = syncIntervalInput,
                    errorMessage = syncIntervalError,
                    onValueChange = viewModel::onSyncIntervalInputChange
                )
            }
            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
            item {
                NextAlarmNotificationSetting(
                    enabled = preferences.showNextAlarmNotification,
                    onToggle = viewModel::setNextAlarmNotificationEnabled
                )
            }
            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
            item {
                CalendarListHeader()
            }
            if (calendars.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No calendars found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(calendars) { calendar: CalendarInfo ->
                    CalendarItem(
                        calendar = calendar,
                        isSelected = selectedIds.contains(calendar.id),
                        onToggle = {
                            viewModel.toggleCalendarSelection(calendar.id)
                            viewModel.saveSelection()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SnoozeDelaySetting(
    value: String,
    errorMessage: String?,
    onValueChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "Snooze delay",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "Minutes to delay alarms after tapping Snooze.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = value,
            onValueChange = onValueChange,
            label = { Text("Minutes") },
            singleLine = true,
            isError = errorMessage != null,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            suffix = { Text("min") }
        )
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun SyncIntervalSetting(
    value: String,
    errorMessage: String?,
    onValueChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "Background sync interval",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "How often to check for new calendar events when the application is not running.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = value,
            onValueChange = onValueChange,
            label = { Text("Minutes") },
            singleLine = true,
            isError = errorMessage != null,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            suffix = { Text("min") }
        )
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun NextAlarmNotificationSetting(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Text(
                text = "Next alarm notification",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Keep a persistent status notification for the next upcoming alarm.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = { onToggle(it) }
        )
    }
}

/**
 * A single calendar item in the list.
 *
 * Displays the calendar's display name, account name, and a checkbox
 * indicating whether the calendar is selected for monitoring.
 *
 * @param calendar The calendar information to display
 * @param isSelected Whether the calendar is currently selected
 * @param onToggle Callback invoked when the checkbox is toggled
 */
@Composable
private fun CalendarItem(
    calendar: CalendarInfo,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() }
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        ) {
            Text(
                text = calendar.displayName,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = calendar.accountName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CalendarListHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "Calendars",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "Choose which calendars CalAlarm should monitor.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
