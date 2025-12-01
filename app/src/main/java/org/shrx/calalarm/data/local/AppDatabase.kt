// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 github.com/shrx

package org.shrx.calalarm.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import org.shrx.calalarm.data.local.entities.DisabledEventId
import org.shrx.calalarm.data.local.entities.ScheduledAlarm

/**
 * Room database for CalAlarm application.
 *
 * This is the main database class that provides access to DAOs and manages
 * the database lifecycle. It uses the singleton pattern to ensure only one
 * database instance exists throughout the application lifecycle.
 *
 * Database Schema:
 * - [ScheduledAlarm]: Stores all scheduled alarms linked to calendar events
 * - [DisabledEventId]: Tracks events for which user has manually disabled alarms
 *
 * @property alarmDao Provides access to alarm-related database operations
 */
@Database(
    entities = [ScheduledAlarm::class, DisabledEventId::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    /**
     * Returns the DAO for alarm-related database operations.
     *
     * @return [AlarmDao] instance for querying and modifying alarm data
     */
    abstract fun alarmDao(): AlarmDao

    companion object {
        /**
         * Late-initialized database instance.
         * Must be initialized via [initialize] before calling [getInstance].
         */
        private lateinit var INSTANCE: AppDatabase

        /**
         * Initializes the database instance.
         *
         * Must be called once from Application.onCreate() before any database access.
         * The database is created with the name "calalarm_database" and stored
         * in the app's internal storage.
         *
         * @param context Application context used to build the database
         */
        fun initialize(context: Context) {
            INSTANCE = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "calalarm_database"
            ).build()
        }

        /**
         * Gets the singleton database instance.
         *
         * @return Singleton [AppDatabase] instance
         * @throws UninitializedPropertyAccessException if [initialize] hasn't been called
         */
        fun getInstance(): AppDatabase {
            return INSTANCE
        }
    }
}
