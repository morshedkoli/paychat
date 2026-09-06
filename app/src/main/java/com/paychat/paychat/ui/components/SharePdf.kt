package com.paychat.paychat.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Hands a generated statement to whatever the user wants to do with it.
 *
 * The read permission is granted on the intent rather than by making the file
 * world readable, so only the app the user picks can open it, and only until
 * they are done.
 */
fun shareStatement(context: Context, uri: Uri, subject: String) {
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, subject)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(share, "Share statement"))
}
