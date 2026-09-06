package com.paychat.paychat.data.notifications

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which conversation is on screen, so a push for it can be suppressed.
 *
 * The messaging service runs on its own thread and reads this while the UI
 * thread writes it, hence the volatile field. Only the chat screen writes,
 * and it writes on resume and pause rather than on creation, so a chat left
 * open in the background still notifies.
 */
@Singleton
class VisibleThread @Inject constructor() {

    @Volatile
    var current: String? = null
        private set

    fun opened(threadId: String) {
        current = threadId
    }

    fun closed(threadId: String) {
        if (current == threadId) current = null
    }
}
