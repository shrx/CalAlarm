// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 github.com/shrx

package org.shrx.calalarm.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utility object for formatting dates and times consistently across the app.
 *
 * Provides standard formatting functions for displaying timestamps to users.
 */
object DateTimeFormatter {
    /**
     * Formats a Unix timestamp into a readable date and time string.
     *
     * @param timestamp Unix timestamp in milliseconds
     * @return Formatted string in ISO format (e.g., "2025-01-15 14:30")
     */
    fun formatDateTime(timestamp: Long): String {
        val dateFormat: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }

    /**
     * Formats a Unix timestamp into a time-only string.
     *
     * @param timestamp Unix timestamp in milliseconds
     * @return Formatted time string (e.g., "14:30")
     */
    fun formatTime(timestamp: Long): String {
        val dateFormat: SimpleDateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }

    /**
     * Gets the current time formatted as HH:mm.
     *
     * @return Current time string (e.g., "14:30")
     */
    fun getCurrentTime(): String {
        val dateFormat: SimpleDateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        return dateFormat.format(Date())
    }
}
