// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 github.com/shrx

package org.shrx.calalarm.ui.alarmlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.shrx.calalarm.data.local.entities.ScheduledAlarm
import org.shrx.calalarm.util.DateTimeFormatter

/**
 * Main screen displaying all scheduled alarms.
 *
 * Shows a list of alarms sorted by event start time (soonest first).
 * Users can delete individual alarms.
 * Displays appropriate states for empty list and loading.
 *
 * @param viewModel ViewModel managing alarm state and operations
 * @param onNavigateToSettings Callback to navigate to settings screen
 * @param onNavigateToInfo Callback to navigate to info screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmListScreen(
    viewModel: AlarmListViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToInfo: () -> Unit
) {
    android.util.Log.d("AlarmListScreen", "AlarmListScreen composing with ViewModel: ${viewModel.hashCode()}")
    val alarms: List<ScheduledAlarm> by viewModel.alarms.collectAsState(initial = emptyList())
    val hasSelectedCalendars: Boolean by viewModel.hasSelectedCalendars.collectAsState(initial = false)
    android.util.Log.d("AlarmListScreen", "Current alarms count: ${alarms.size}")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CalAlarm") },
                actions = {
                    IconButton(onClick = onNavigateToInfo) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info"
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (alarms.isEmpty()) {
                EmptyState(hasSelectedCalendars = hasSelectedCalendars)
            } else {
                AlarmList(
                    alarms = alarms,
                    onDeleteAlarm = { alarm -> viewModel.deleteAlarm(alarm) }
                )
            }
        }
    }
}

/**
 * Displays a message when no alarms are scheduled.
 *
 * Shows different messages depending on whether calendars are selected:
 * - No calendars selected: Prompts user to choose a calendar to monitor
 * - Calendars selected but no events: Prompts user to add events to calendar
 *
 * @param hasSelectedCalendars Whether any calendars are currently selected
 */
@Composable
private fun EmptyState(hasSelectedCalendars: Boolean) {
    val message: String = if (hasSelectedCalendars) {
        "No alarms scheduled. Add events to your calendar."
    } else {
        "No calendars selected. Choose a calendar to monitor."
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(16.dp)
        )
    }
}

/**
 * Displays the list of scheduled alarms.
 *
 * @param alarms List of alarms to display (already sorted by time)
 * @param onDeleteAlarm Callback when user deletes an alarm
 */
@Composable
private fun AlarmList(
    alarms: List<ScheduledAlarm>,
    onDeleteAlarm: (ScheduledAlarm) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(alarms) { alarm ->
            AlarmListItem(
                alarm = alarm,
                onDelete = { onDeleteAlarm(alarm) }
            )
        }
    }
}

/**
 * Individual alarm item in the list.
 *
 * @param alarm The alarm to display
 * @param onDelete Callback when delete button is clicked
 */
@Composable
private fun AlarmListItem(
    alarm: ScheduledAlarm,
    onDelete: () -> Unit
) {
    val timeText: String = buildAlarmTimeText(alarm)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = alarm.eventTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete alarm",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

internal fun buildAlarmTimeText(alarm: ScheduledAlarm): String {
    val isSnoozed: Boolean = alarm.snoozeOffset > 0
    val displayTime: Long = alarm.eventStartTime + alarm.snoozeOffset
    val formattedTime: String = formatRelativeDateTime(displayTime)
    return if (isSnoozed) {
        val snoozedTime: String = formattedTime.replaceFirstChar { character ->
            character.lowercase()
        }
        "Snoozed until $snoozedTime"
    } else {
        formattedTime
    }
}

internal fun formatRelativeDateTime(timestamp: Long): String {
    val currentTimeMillis: Long = System.currentTimeMillis()
    val zoneId: ZoneId = ZoneId.systemDefault()
    val targetDate: LocalDate = Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDate()
    val currentDate: LocalDate = Instant.ofEpochMilli(currentTimeMillis).atZone(zoneId).toLocalDate()
    return when {
        targetDate.isEqual(currentDate) ->
            "Today ${DateTimeFormatter.formatTime(timestamp)}"
        targetDate.isEqual(currentDate.plusDays(1)) ->
            "Tomorrow ${DateTimeFormatter.formatTime(timestamp)}"
        else -> DateTimeFormatter.formatDateTime(timestamp)
    }
}
