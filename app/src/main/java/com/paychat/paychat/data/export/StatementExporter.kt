package com.paychat.paychat.data.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.paychat.paychat.core.ledger.LedgerStatement
import com.paychat.paychat.core.model.TxnStatus
import com.paychat.paychat.data.auth.AuthRepository
import com.paychat.paychat.data.local.dao.ThreadDao
import com.paychat.paychat.data.local.dao.TransactionDao
import com.paychat.paychat.data.local.entity.ThreadEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns what the ledger says into a PDF the user can share.
 *
 * A statement lists accepted transactions only. Pending, rejected and
 * cancelled entries carry no money, and a statement that showed them would
 * invite someone to read a figure that was never owed.
 *
 * Files are written to the cache directory: a statement is a snapshot the
 * user is about to send somewhere, not something the app needs to keep, and
 * anything left behind is reclaimed by the system.
 */
@Singleton
class StatementExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val threadDao: ThreadDao,
    private val transactionDao: TransactionDao,
    private val writer: StatementPdfWriter,
    private val auth: AuthRepository,
) {

    /** One conversation. */
    suspend fun exportThread(threadId: String): Result<Uri> = export {
        val thread = threadDao.byId(threadId) ?: error("no such conversation")
        val name = thread.displayName()
        StatementDocument(
            title = "Statement — $name",
            generatedAt = System.currentTimeMillis(),
            sections = listOf(sectionFor(thread)),
        ) to fileName("statement", name)
    }

    /** Every conversation that has any accepted money in it. */
    suspend fun exportAll(): Result<Uri> = export {
        val sections = threadDao.all()
            .map { sectionFor(it) }
            .filter { it.lines.isNotEmpty() }
            .sortedBy { it.counterparty.lowercase(Locale.US) }

        StatementDocument(
            title = "Statement — all conversations",
            generatedAt = System.currentTimeMillis(),
            sections = sections,
        ) to fileName("statement", "all")
    }

    private suspend fun sectionFor(thread: ThreadEntity): StatementSection {
        val viewerUid = auth.currentUid.orEmpty()
        val accepted = transactionDao.forThread(thread.threadId)
            .filter { it.status == TxnStatus.ACCEPTED }

        return StatementSection(
            counterparty = thread.displayName(),
            phone = thread.peerPhone,
            lines = LedgerStatement.build(accepted, viewerUid),
            closing = LedgerStatement.closingBalance(accepted, viewerUid),
        )
    }

    private suspend fun export(build: suspend () -> Pair<StatementDocument, String>): Result<Uri> =
        withContext(Dispatchers.IO) {
            runCatching {
                val (document, name) = build()
                if (document.isEmpty) error("There is nothing to export yet.")

                val file = writer.write(document, File(exportDir(), name))
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }
        }

    /**
     * Old statements are cleared each time, so a share sheet cannot offer a
     * file from last week and the cache cannot grow without bound.
     */
    private fun exportDir(): File =
        File(context.cacheDir, "statements").apply {
            deleteRecursively()
            mkdirs()
        }

    private fun fileName(prefix: String, subject: String): String {
        val safe = subject.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').ifBlank { "statement" }
        val stamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        return "$prefix-$safe-$stamp.pdf"
    }
}

private fun ThreadEntity.displayName(): String = peerName.ifBlank { peerPhone }
