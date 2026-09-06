package com.paychat.paychat.data.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.paychat.paychat.data.auth.AuthRepository
import com.paychat.paychat.data.local.dao.LocalContactDao
import com.paychat.paychat.data.local.dao.MessageDao
import com.paychat.paychat.data.local.dao.ThreadDao
import com.paychat.paychat.data.local.dao.TransactionDao
import com.paychat.paychat.data.local.dao.UserDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes everything the app holds about this user as one JSON file.
 *
 * Play's policy requires that a user can take their data with them, and the
 * PDF statement is not that: it is a summary, formatted for reading, and it
 * leaves out messages entirely. This is the raw rows, in a format a person
 * can open in any text editor and a program can parse.
 *
 * It is built from Room rather than from the server, because Room already
 * holds everything the user is allowed to see and the export then works
 * offline. Media is referenced by url rather than embedded: the files are
 * large, and the links stay valid.
 */
@Singleton
class DataExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userDao: UserDao,
    private val threadDao: ThreadDao,
    private val messageDao: MessageDao,
    private val transactionDao: TransactionDao,
    private val localContactDao: LocalContactDao,
    private val auth: AuthRepository,
) {

    suspend fun export(): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = auth.currentUid ?: error("You are not signed in.")
            val document = build(uid)

            val file = File(exportDir(), fileName())
            file.parentFile?.mkdirs()
            file.writeText(document.toString(2))

            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
    }

    private suspend fun build(uid: String): JSONObject {
        val profile = userDao.byUid(uid)
        val threads = threadDao.all()
        val contacts = localContactDao.observeAll().first()

        return JSONObject().apply {
            put("exportedAt", System.currentTimeMillis())
            put("format", FORMAT_VERSION)
            put(
                "profile",
                JSONObject().apply {
                    put("uid", uid)
                    put("phone", profile?.phone)
                    put("name", profile?.name)
                    put("photoUrl", profile?.photoUrl)
                },
            )
            put(
                "localContacts",
                JSONArray().apply {
                    contacts.forEach { contact ->
                        put(
                            JSONObject().apply {
                                put("name", contact.name)
                                put("phone", contact.phone)
                                put("threadId", contact.threadId)
                            }
                        )
                    }
                },
            )
            put(
                "conversations",
                JSONArray().apply {
                    threads.forEach { thread -> put(conversation(thread.threadId)) }
                },
            )
        }
    }

    private suspend fun conversation(threadId: String): JSONObject {
        val thread = threadDao.byId(threadId) ?: return JSONObject()

        return JSONObject().apply {
            put("threadId", thread.threadId)
            put("counterparty", thread.peerName)
            put("phone", thread.peerPhone)
            put("isLocal", thread.isLocal)
            put(
                "messages",
                JSONArray().apply {
                    // Oldest first, which is how a conversation reads outside
                    // the app even though the screen draws it the other way.
                    messageDao.observeThread(threadId).first().asReversed().forEach { message ->
                        put(
                            JSONObject().apply {
                                put("id", message.messageId)
                                put("from", message.senderId)
                                put("type", message.type.name)
                                put("text", message.text)
                                put("mediaUrl", message.mediaUrl)
                                put("durationMs", message.durationMs)
                                put("txnId", message.txnId)
                                put("at", message.createdAt)
                            }
                        )
                    }
                },
            )
            put(
                "transactions",
                JSONArray().apply {
                    transactionDao.forThread(threadId).forEach { txn ->
                        put(
                            JSONObject().apply {
                                put("id", txn.txnId)
                                put("recordedBy", txn.createdBy)
                                put("direction", txn.direction.name)
                                put("amountMinor", txn.amountMinor)
                                put("currency", "BDT")
                                put("note", txn.note)
                                put("photoUrl", txn.photoUrl)
                                put("dueDate", txn.dueDate)
                                put("status", txn.status.name)
                                put("unconfirmed", txn.unconfirmed)
                                put("reversesId", txn.reversesId)
                                put("reversedBy", txn.reversedBy)
                                put("at", txn.createdAt)
                                put("resolvedAt", txn.resolvedAt)
                            }
                        )
                    }
                },
            )
        }
    }

    /** Cleared each time, so a share sheet cannot offer a stale export. */
    private fun exportDir(): File =
        File(context.cacheDir, "exports").apply {
            deleteRecursively()
            mkdirs()
        }

    private fun fileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        return "paychat-data-$stamp.json"
    }

    private companion object {
        /** Bumped whenever the shape of the file changes. */
        const val FORMAT_VERSION = 1
    }
}
