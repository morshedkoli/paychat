package com.paychat.paychat.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.paychat.paychat.core.network.NetworkMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages draining of the outbox for both online immediate delivery
 * and offline persistent queuing.
 */
@Singleton
class OutboxScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val outboxSyncer: OutboxSyncer,
    private val networkMonitor: NetworkMonitor,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Whenever network transitions to online, immediately flush the outbox
        scope.launch {
            networkMonitor.isOnline.collect { online ->
                if (online) {
                    flush()
                }
            }
        }
    }

    /**
     * Enqueues durable background work with WorkManager to empty the outbox
     * as soon as network is available.
     */
    fun schedule() {
        val request = OneTimeWorkRequestBuilder<OutboxWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Triggers an immediate in-process flush of the outbox if online.
     */
    fun flush() {
        scope.launch {
            if (networkMonitor.isCurrentlyOnline()) {
                outboxSyncer.syncAll()
            }
        }
    }

    /**
     * Schedules persistent background work AND immediately triggers an
     * in-process flush if network is available right now.
     */
    fun scheduleAndFlush() {
        schedule()
        flush()
    }

    /** Drops any pending outbox uploads, e.g. when signing out. */
    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private companion object {
        const val WORK_NAME = "paychat-outbox"
    }
}
