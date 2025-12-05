// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 github.com/shrx

package org.shrx.calalarm.util

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Utility object for formatting dates and times consistently across the app.
 *
 * Provides standard formatting functions for displaying timestamps to users.
 */
object DateTimeFormatter {
    private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * Formats a Unix timestamp into a readable date and time string.
     *
     * @param timestamp Unix timestamp in milliseconds
     * @return Formatted string in ISO format (e.g., "2025-01-15 14:30")
     */
    fun formatDateTime(timestamp: Long): String {
        val zonedDateTime: ZonedDateTime = Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
        return dateTimeFormatter.format(zonedDateTime)
    }

    /**
     * Formats a Unix timestamp into a time-only string.
     *
     * @param timestamp Unix timestamp in milliseconds
     * @return Formatted time string (e.g., "14:30")
     */
    fun formatTime(timestamp: Long): String {
        val zonedDateTime: ZonedDateTime = Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
        return timeFormatter.format(zonedDateTime)
    }

    /**
     * Gets the current time formatted as HH:mm.
     *
     * @return Current time string (e.g., "14:30")
     */
    fun getCurrentTime(): String {
        val zonedDateTime: ZonedDateTime = ZonedDateTime.now()
        return timeFormatter.format(zonedDateTime)
    }
}
