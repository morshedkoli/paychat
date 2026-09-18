package com.paychat.paychat.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StorageHelperTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun formatFileSize_formatsCorrectUnits() {
        assertEquals("0 KB", StorageHelper.formatFileSize(0L))
        assertEquals("1 KB", StorageHelper.formatFileSize(500L))
        assertEquals("2.5 KB", StorageHelper.formatFileSize((2.5 * 1024).toLong()))
        assertEquals("10.5 MB", StorageHelper.formatFileSize((10.5 * 1024 * 1024).toLong()))
        assertEquals("1.25 GB", StorageHelper.formatFileSize((1.25 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun getDirectorySize_calculatesCorrectTotal() {
        val root = tempFolder.newFolder("cache")
        val file1 = File(root, "file1.txt").apply { writeBytes(ByteArray(1024)) }
        val sub = File(root, "sub").apply { mkdir() }
        val file2 = File(sub, "file2.txt").apply { writeBytes(ByteArray(2048)) }

        val size = StorageHelper.getDirectorySize(root)
        assertEquals(3072L, size)
    }

    @Test
    fun getDirectorySize_nonExistentDirectory_returnsZero() {
        val missing = File(tempFolder.root, "missing_dir")
        assertEquals(0L, StorageHelper.getDirectorySize(missing))
        assertEquals(0L, StorageHelper.getDirectorySize(null))
    }
}
