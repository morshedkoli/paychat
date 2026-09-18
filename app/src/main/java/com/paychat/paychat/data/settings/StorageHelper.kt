package com.paychat.paychat.data.settings

import android.content.Context
import coil.Coil
import java.io.File
import java.text.DecimalFormat

/**
 * Calculates and clears temporary app cache (Coil image cache, temporary media, and system cache dirs).
 */
object StorageHelper {

    fun getDirectorySize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var total = 0L
        val files = dir.listFiles() ?: return 0L
        for (file in files) {
            total += if (file.isDirectory) getDirectorySize(file) else file.length()
        }
        return total
    }

    fun calculateCacheSizeBytes(context: Context): Long {
        val internalCache = getDirectorySize(context.cacheDir)
        val externalCache = getDirectorySize(context.externalCacheDir)
        return internalCache + externalCache
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    fun clearCache(context: Context): Long {
        val bytesBefore = calculateCacheSizeBytes(context)

        try {
            val imageLoader = Coil.imageLoader(context)
            imageLoader.memoryCache?.clear()
            imageLoader.diskCache?.clear()
        } catch (_: Throwable) {}

        deleteSubfiles(context.cacheDir)
        deleteSubfiles(context.externalCacheDir)

        return bytesBefore
    }

    private fun deleteSubfiles(dir: File?) {
        if (dir == null || !dir.exists()) return
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) deleteSubfiles(file)
            file.delete()
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 KB"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        val df = DecimalFormat("#.##")
        return when {
            gb >= 1.0 -> "${df.format(gb)} GB"
            mb >= 1.0 -> "${df.format(mb)} MB"
            else -> "${df.format(kb.coerceAtLeast(1.0))} KB"
        }
    }
}
