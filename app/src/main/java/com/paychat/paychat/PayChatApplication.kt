package com.paychat.paychat

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PayChatApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var messageDao: com.paychat.paychat.data.local.dao.MessageDao
    @Inject lateinit var transactionDao: com.paychat.paychat.data.local.dao.TransactionDao
    @Inject lateinit var outboxScheduler: com.paychat.paychat.data.sync.OutboxScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        recoverAndSyncOutbox()
    }

    private fun recoverAndSyncOutbox() {
        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
        ).launch {
            messageDao.resetUploadingToPending()
            transactionDao.resetUploadingToPending()
            outboxScheduler.flush()
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        listOf(
            Triple(
                getString(R.string.channel_messages),
                getString(R.string.channel_messages_name),
                NotificationManager.IMPORTANCE_HIGH
            ),
            Triple(
                getString(R.string.channel_transactions),
                getString(R.string.channel_transactions_name),
                NotificationManager.IMPORTANCE_HIGH
            ),
            Triple(
                getString(R.string.channel_reminders),
                getString(R.string.channel_reminders_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ),
        ).forEach { (id, name, importance) ->
            manager.createNotificationChannel(NotificationChannel(id, name, importance))
        }
    }
}
