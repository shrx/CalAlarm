// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 github.com/shrx

package org.shrx.calalarm.util

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Unit tests for DateTimeFormatter utility functions.
 *
 * Tests formatting of timestamps into human-readable date and time strings.
 */
class DateFormattingTest {

    @Before
    fun setUp() {
        // Set a fixed timezone for consistent test results across different environments
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.US)
    }

    /**
     * Test formatDateTime with a known timestamp.
     *
     * Given: Timestamp 1704067200000L (2024-01-01 00:00 UTC)
     * When: formatDateTime() called
     * Then: Returns "2024-01-01 00:00" in UTC timezone
     */
    @Test
    fun testFormatDateTime_knownTimestamp() {
        // Given: Known timestamp (2024-01-01 00:00 UTC)
        val timestamp: Long = 1704067200000L

        // When: Format timestamp
        val result: String = DateTimeFormatter.formatDateTime(timestamp)

        // Then: Formatted correctly
        assertEquals("2024-01-01 00:00", result)
    }

    /**
     * Test formatDateTime with morning time.
     *
     * Given: Timestamp for morning (09:20)
     * When: formatDateTime() called
     * Then: Returns correct format with morning time
     */
    @Test
    fun testFormatDateTime_morningTime() {
        // Given: Morning timestamp (2024-03-15 09:20 UTC)
        val timestamp: Long = 1710494400000L // 2024-03-15 09:20 UTC

        // When: Format timestamp
        val result: String = DateTimeFormatter.formatDateTime(timestamp)

        // Then: Formatted correctly
        assertEquals("2024-03-15 09:20", result)
    }

    /**
     * Test formatDateTime with afternoon time.
     *
     * Given: Timestamp for afternoon (15:05)
     * When: formatDateTime() called
     * Then: Returns correct format with afternoon time
     */
    @Test
    fun testFormatDateTime_afternoonTime() {
        // Given: Afternoon timestamp (2024-06-20 15:05 UTC)
        val timestamp: Long = 1718895900000L

        // When: Format timestamp
        val result: String = DateTimeFormatter.formatDateTime(timestamp)

        // Then: Formatted correctly
        assertEquals("2024-06-20 15:05", result)
    }

    /**
     * Test formatDateTime with evening time.
     *
     * Given: Timestamp for evening (20:15)
     * When: formatDateTime() called
     * Then: Returns correct format with evening time
     */
    @Test
    fun testFormatDateTime_eveningTime() {
        // Given: Evening timestamp (2024-09-10 20:15 UTC)
        val timestamp: Long = 1725999300000L

        // When: Format timestamp
        val result: String = DateTimeFormatter.formatDateTime(timestamp)

        // Then: Formatted correctly
        assertEquals("2024-09-10 20:15", result)
    }

    /**
     * Test formatDateTime with midnight.
     *
     * Given: Timestamp for midnight (00:00)
     * When: formatDateTime() called
     * Then: Returns correct format with 00:00 time
     */
    @Test
    fun testFormatDateTime_midnight() {
        // Given: Midnight timestamp (2024-12-25 00:00 UTC)
        val timestamp: Long = 1735084800000L

        // When: Format timestamp
        val result: String = DateTimeFormatter.formatDateTime(timestamp)

        // Then: Formatted correctly
        assertEquals("2024-12-25 00:00", result)
    }

    /**
     * Test formatDateTime with epoch (zero timestamp).
     *
     * Given: 0L timestamp (Unix epoch)
     * When: formatDateTime() called
     * Then: Returns "1970-01-01 00:00" in UTC
     */
    @Test
    fun testFormatDateTime_zeroTimestamp() {
        // Given: Epoch timestamp
        val timestamp: Long = 0L

        // When: Format timestamp
        val result: String = DateTimeFormatter.formatDateTime(timestamp)

        // Then: Formatted correctly as epoch
        assertEquals("1970-01-01 00:00", result)
    }

    /**
     * Test formatDateTime returns consistent format structure.
     *
     * Given: Various timestamps
     * When: formatDateTime() called
     * Then: All results match the format pattern "yyyy-MM-dd HH:mm"
     */
    @Test
    fun testFormatDateTime_consistentFormat() {
        // Given: Various timestamps
        val timestamps: List<Long> = listOf(
            1704067200000L, // 2024-01-01 00:00
            1609459200000L, // 2021-01-01 00:00
            1735689600000L, // 2025-01-01 00:00
            1640995200000L  // 2022-01-01 00:00
        )

        // When: Format all timestamps
        val results: List<String> = timestamps.map { DateTimeFormatter.formatDateTime(it) }

        // Then: All match expected pattern
        val pattern: Regex = Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}")
        results.forEach { result ->
            assertTrue("Result '$result' should match pattern", pattern.matches(result))
        }
    }

    /**
     * Test formatTime with known timestamp.
     *
     * Given: Timestamp with specific time
     * When: formatTime() called
     * Then: Returns only "HH:mm" format
     */
    @Test
    fun testFormatTime_knownTimestamp() {
        // Given: Known timestamp (2024-01-01 14:30 UTC)
        val timestamp: Long = 1704119400000L

        // When: Format time
        val result: String = DateTimeFormatter.formatTime(timestamp)

        // Then: Returns only time portion
        assertEquals("14:30", result)
    }

    /**
     * Test formatTime with morning time.
     *
     * Given: Morning timestamp (08:05)
     * When: formatTime() called
     * Then: Returns "08:05"
     */
    @Test
    fun testFormatTime_morningTime() {
        // Given: Morning timestamp (2024-03-15 08:05 UTC)
        val timestamp: Long = 1710489900000L

        // When: Format time
        val result: String = DateTimeFormatter.formatTime(timestamp)

        // Then: Returns only time portion
        assertEquals("08:05", result)
    }

    /**
     * Test formatTime with evening time.
     *
     * Given: Evening timestamp (23:05)
     * When: formatTime() called
     * Then: Returns "23:05"
     */
    @Test
    fun testFormatTime_eveningTime() {
        // Given: Evening timestamp (2024-06-20 23:05 UTC)
        val timestamp: Long = 1718924700000L

        // When: Format time
        val result: String = DateTimeFormatter.formatTime(timestamp)

        // Then: Returns only time portion
        assertEquals("23:05", result)
    }

    /**
     * Test formatTime with midnight.
     *
     * Given: Midnight timestamp (00:00)
     * When: formatTime() called
     * Then: Returns "00:00"
     */
    @Test
    fun testFormatTime_midnight() {
        // Given: Midnight timestamp (2024-09-10 00:00 UTC)
        val timestamp: Long = 1725926400000L

        // When: Format time
        val result: String = DateTimeFormatter.formatTime(timestamp)

        // Then: Returns midnight time
        assertEquals("00:00", result)
    }

    /**
     * Test formatTime with noon.
     *
     * Given: Noon timestamp (12:00)
     * When: formatTime() called
     * Then: Returns "12:00"
     */
    @Test
    fun testFormatTime_noon() {
        // Given: Noon timestamp (2024-09-10 12:00 UTC)
        val timestamp: Long = 1725969600000L

        // When: Format time
        val result: String = DateTimeFormatter.formatTime(timestamp)

        // Then: Returns noon time
        assertEquals("12:00", result)
    }

    /**
     * Test formatTime returns consistent format structure.
     *
     * Given: Various timestamps
     * When: formatTime() called
     * Then: All results match the format pattern "HH:mm"
     */
    @Test
    fun testFormatTime_consistentFormat() {
        // Given: Various timestamps with different times
        val timestamps: List<Long> = listOf(
            1704067200000L, // 00:00
            1704078000000L, // 03:00
            1704110400000L, // 12:00
            1704139200000L, // 20:00
            1704153600000L  // 00:00
        )

        // When: Format all timestamps
        val results: List<String> = timestamps.map { DateTimeFormatter.formatTime(it) }

        // Then: All match expected pattern
        val pattern: Regex = Regex("\\d{2}:\\d{2}")
        results.forEach { result ->
            assertTrue("Result '$result' should match pattern", pattern.matches(result))
        }
    }

    /**
     * Test getCurrentTime returns valid time format.
     *
     * Given: Current moment
     * When: getCurrentTime() called
     * Then: Returns valid "HH:mm" format string
     */
    @Test
    fun testGetCurrentTime_returnsValidFormat() {
        // Given/When: Get current time
        val result: String = DateTimeFormatter.getCurrentTime()

        // Then: Matches HH:mm pattern
        val pattern: Regex = Regex("\\d{2}:\\d{2}")
        assertTrue("Result '$result' should match pattern HH:mm", pattern.matches(result))
    }

    /**
     * Test getCurrentTime returns reasonable values.
     *
     * Given: Current moment
     * When: getCurrentTime() called
     * Then: Returns time within valid hour (00-23) and minute (00-59) ranges
     */
    @Test
    fun testGetCurrentTime_returnsReasonableValues() {
        // Given/When: Get current time
        val result: String = DateTimeFormatter.getCurrentTime()

        // Then: Parse and verify valid time ranges
        val parts: List<String> = result.split(":")
        assertEquals(2, parts.size)

        val hour: Int = parts[0].toInt()
        val minute: Int = parts[1].toInt()

        assertTrue("Hour should be 0-23, got $hour", hour in 0..23)
        assertTrue("Minute should be 0-59, got $minute", minute in 0..59)
    }

    /**
     * Test formatDateTime and formatTime are consistent.
     *
     * Given: Same timestamp
     * When: Both formatDateTime() and formatTime() called
     * Then: formatTime() result matches time portion of formatDateTime() result
     */
    @Test
    fun testFormatDateTime_formatTime_consistency() {
        // Given: Known timestamp (2024-01-01 14:30 UTC)
        val timestamp: Long = 1704119400000L

        // When: Format with both methods
        val dateTimeResult: String = DateTimeFormatter.formatDateTime(timestamp)
        val timeResult: String = DateTimeFormatter.formatTime(timestamp)

        // Then: Time portion of dateTime matches time-only result
        val dateTimeParts: List<String> = dateTimeResult.split(" ")
        assertEquals(2, dateTimeParts.size)
        assertEquals(timeResult, dateTimeParts[1])
    }

    /**
     * Test formatDateTime with far future timestamp.
     *
     * Given: Future timestamp (2099-12-31 23:59)
     * When: formatDateTime() called
     * Then: Returns correct future date
     */
    @Test
    fun testFormatDateTime_farFuture() {
        // Given: Far future timestamp (2099-12-31 23:59 UTC)
        val timestamp: Long = 4102444740000L

        // When: Format timestamp
        val result: String = DateTimeFormatter.formatDateTime(timestamp)

        // Then: Formatted correctly
        assertEquals("2099-12-31 23:59", result)
    }

    /**
     * Test formatDateTime preserves minutes with leading zeros.
     *
     * Given: Timestamp with single-digit minutes
     * When: formatDateTime() called
     * Then: Returns minutes with leading zero (e.g., "09" not "9")
     */
    @Test
    fun testFormatDateTime_preservesLeadingZeros() {
        // Given: Timestamp with 05 minutes (2024-01-01 00:05 UTC)
        val timestamp: Long = 1704067500000L

        // When: Format timestamp
        val result: String = DateTimeFormatter.formatDateTime(timestamp)

        // Then: Leading zero is preserved
        assertEquals("2024-01-01 00:05", result)
        assertTrue("Should contain '00:05'", result.contains("00:05"))
    }
}
