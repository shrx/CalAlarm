// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 github.com/shrx

package org.shrx.calalarm.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.shrx.calalarm.data.local.entities.DisabledEventId
import org.shrx.calalarm.data.local.entities.ScheduledAlarm

/**
 * Data Access Object for [ScheduledAlarm] entities.
 */
@Dao
interface AlarmDao {

    /**
     * Retrieves all alarms as a reactive Flow, ordered by start time.
     *
     * @return Flow emitting list of all scheduled alarms
     */
    @Query("SELECT * FROM scheduled_alarms ORDER BY eventStartTime ASC")
    fun getAllAlarms(): Flow<List<ScheduledAlarm>>

    /**
     * Retrieves all alarms as a one-shot suspending query, ordered by start time.
     *
     * @return List of all scheduled alarms
     */
    @Query("SELECT * FROM scheduled_alarms ORDER BY eventStartTime ASC")
    suspend fun getAllAlarmsList(): List<ScheduledAlarm>

    /**
     * Retrieves a single alarm by event ID.
     *
     * @param eventId The event ID to search for
     * @return The alarm with the given event ID, or null if not found
     */
    @Query("SELECT * FROM scheduled_alarms WHERE eventId = :eventId LIMIT 1")
    suspend fun getAlarmById(eventId: Long): ScheduledAlarm?

    /**
     * Retrieves all event IDs for existing alarms.
     *
     * @return List of event IDs from all alarms in the database
     */
    @Query("SELECT eventId FROM scheduled_alarms")
    suspend fun getAllAlarmEventIds(): List<Long>

    /**
     * Inserts a new alarm, replacing any existing alarm with the same event ID.
     *
     * @param alarm The alarm to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarm(alarm: ScheduledAlarm)

    /**
     * Deletes an alarm by its event ID.
     *
     * @param eventId The event ID of the alarm to delete
     */
    @Query("DELETE FROM scheduled_alarms WHERE eventId = :eventId")
    suspend fun deleteAlarm(eventId: Long)

    /**
     * Updates the snooze offset for an alarm.
     *
     * @param eventId The event ID of the alarm to update
     * @param snoozeOffset The snooze offset in milliseconds (0 if not snoozed)
     */
    @Query("UPDATE scheduled_alarms SET snoozeOffset = :snoozeOffset WHERE eventId = :eventId")
    suspend fun updateSnoozeOffset(eventId: Long, snoozeOffset: Long)

    /**
     * Retrieves all disabled event IDs.
     *
     * @return List of disabled event IDs
     */
    @Query("SELECT eventId FROM disabled_event_ids")
    suspend fun getDisabledEventIds(): List<Long>

    /**
     * Adds an event ID to the disabled list.
     *
     * @param disabledEventId The disabled event ID to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDisabledEventId(disabledEventId: DisabledEventId)

    /**
     * Removes an event ID from the disabled list.
     *
     * @param eventId The event ID to remove from disabled list
     */
    @Query("DELETE FROM disabled_event_ids WHERE eventId = :eventId")
    suspend fun deleteDisabledEventId(eventId: Long)
}
