package com.paychat.paychat.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Uploads everything the user created while the app could not reach the
 * server.
 *
 * Each message carries a client-generated id and is written with `set`, so
 * retrying an upload that actually succeeded overwrites the same document
 * instead of producing a duplicate.
 */
@HiltWorker
class OutboxWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val outboxSyncer: OutboxSyncer,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val success = outboxSyncer.syncAll()
        return if (success) Result.success() else Result.retry()
    }
}
