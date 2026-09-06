package com.paychat.paychat.data.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.paychat.paychat.MainActivity
import com.paychat.paychat.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives push messages.
 *
 * The payload is data-only rather than a notification payload, so the app
 * builds every notification itself. That is what lets a message arriving for a
 * conversation already on screen be suppressed, and lets the notification open
 * the right chat.
 */
@AndroidEntryPoint
class PayChatMessagingService : FirebaseMessagingService() {

    @Inject lateinit var pushTokens: PushTokens
    @Inject lateinit var visibleThread: VisibleThread

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch { pushTokens.onTokenRefreshed(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val threadId = data[KEY_THREAD_ID] ?: return
        val title = data[KEY_TITLE].orEmpty()
        val body = data[KEY_BODY].orEmpty()
        val channel = data[KEY_CHANNEL] ?: getString(R.string.channel_messages)

        // Nothing is more irritating than being notified about the
        // conversation you are already reading.
        if (visibleThread.current == threadId) return

        show(threadId = threadId, title = title, body = body, channelId = channel)
    }

    private fun show(threadId: String, title: String, body: String, channelId: String) {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_THREAD_ID, threadId)
        }
        val pending = PendingIntent.getActivity(
            this,
            threadId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .build()

        // One notification per conversation, so a busy chat does not bury
        // everything else in the tray.
        NotificationManagerCompat.from(this).notify(threadId.hashCode(), notification)
    }

    companion object {
        const val EXTRA_THREAD_ID = "threadId"

        private const val KEY_THREAD_ID = "threadId"
        private const val KEY_TITLE = "title"
        private const val KEY_BODY = "body"
        private const val KEY_CHANNEL = "channel"
    }
}
