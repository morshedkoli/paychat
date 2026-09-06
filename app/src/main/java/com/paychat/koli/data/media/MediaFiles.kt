package com.paychat.koli.data.media

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where attachments live on the device.
 *
 * A picked photo is copied into the app's own storage before it is queued.
 * The picker only grants access to the original for as long as the activity
 * result lives, so a message waiting in the outbox would otherwise fail to
 * upload once the app restarted.
 */
@Singleton
class MediaFiles @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val outgoing: File
        get() = File(context.filesDir, OUTGOING_DIR).apply { mkdirs() }

    /** A file the camera can write into, shared through the file provider. */
    fun newCameraFile(messageId: String): File = File(outgoing, "$messageId.jpg")

    fun newVoiceFile(messageId: String): File = File(outgoing, "$messageId.m4a")

    /**
     * Copies [source] into app storage.
     *
     * @return the copied file, or null when the content could not be read
     */
    suspend fun copyIn(source: Uri, messageId: String, extension: String): File? =
        withContext(Dispatchers.IO) {
            val target = File(outgoing, "$messageId.$extension")
            runCatching {
                context.contentResolver.openInputStream(source)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: return@runCatching null
                target
            }.getOrNull()
        }

    /** Removes the local copy once the upload has succeeded. */
    fun discard(path: String?) {
        if (path.isNullOrEmpty()) return
        runCatching { File(path).delete() }
    }

    private companion object {
        const val OUTGOING_DIR = "media/outgoing"
    }
}
