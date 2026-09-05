package com.paychat.koli

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PayChatApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
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
