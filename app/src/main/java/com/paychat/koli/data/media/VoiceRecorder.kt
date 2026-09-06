package com.paychat.koli.data.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records a voice message as AAC in an MP4 container, which every Android
 * version in range plays back and Cloudinary accepts as a video resource.
 */
@Singleton
class VoiceRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class Recording(val file: File, val durationMs: Long)

    private var recorder: MediaRecorder? = null
    private var target: File? = null
    private var startedAt: Long = 0L

    val isRecording: Boolean get() = recorder != null

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun start(file: File): Result<Unit> = runCatching {
        check(recorder == null) { "already recording" }

        val instance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        instance.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(BIT_RATE)
            setAudioSamplingRate(SAMPLE_RATE)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }

        recorder = instance
        target = file
        startedAt = System.currentTimeMillis()
    }.onFailure { release() }

    /**
     * @return the recording, or null when it was too short to be a message or
     *   the recorder failed. A failed stop still leaves no file behind.
     */
    fun stop(): Recording? {
        val instance = recorder ?: return null
        val file = target

        val stopped = runCatching { instance.stop() }.isSuccess
        release()

        if (!stopped || file == null || !file.exists()) {
            file?.delete()
            return null
        }

        val duration = System.currentTimeMillis() - startedAt
        if (duration < MIN_DURATION_MS) {
            file.delete()
            return null
        }
        return Recording(file, duration)
    }

    /** Abandons the recording and deletes the partial file. */
    fun cancel() {
        runCatching { recorder?.stop() }
        release()
        target?.delete()
        target = null
    }

    private fun release() {
        runCatching { recorder?.release() }
        recorder = null
    }

    companion object {
        const val MIN_DURATION_MS = 700L
        private const val BIT_RATE = 64_000
        private const val SAMPLE_RATE = 44_100
    }
}
