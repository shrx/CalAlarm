// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 github.com/shrx

package org.shrx.calalarm.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.shrx.calalarm.CalAlarmApplication

/**
 * WorkManager Worker that performs periodic calendar sync.
 *
 * This worker runs periodically in the background to ensure new calendar events
 * are detected even when the app process is not running. It serves as a fallback
 * to the ContentObserver which only works when the process is alive.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            val application: CalAlarmApplication = applicationContext as CalAlarmApplication
            application.eventSyncService.requestSync()
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("SyncWorker", "Sync failed", e)
            Result.retry()
        }
    }
}
