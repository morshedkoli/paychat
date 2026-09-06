package com.paychat.paychat.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs every migration against a real database file.
 *
 * The point is not that the schema ends up correct — Room checks that itself
 * — but that the rows survive. A migration that quietly emptied a table would
 * lose transactions that had not reached the server, and no unit test can
 * catch that.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PayChatDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun balancesMoveOutOfTheThreadRow() {
        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL(
                "INSERT INTO threads (threadId, peerUid, peerPhone, peerName, peerPhotoUrl, " +
                    "isLocal, awaitingConfirmation, balanceMinor, lastMessageText, " +
                    "lastMessageAt, unreadCount, updatedAt) VALUES " +
                    "('t1', 'u2', '+8801700000000', 'Rana', NULL, 0, 0, 2500, 'hi', 10, 0, 10)"
            )
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 3, true, *PAYCHAT_MIGRATIONS)

        migrated.query("SELECT amountMinor FROM thread_balances WHERE threadId = 't1'").use {
            assertTrue("the balance did not survive the move", it.moveToFirst())
            assertEquals(2500, it.getLong(0))
        }
        migrated.query("SELECT peerName FROM threads WHERE threadId = 't1'").use {
            assertTrue(it.moveToFirst())
            assertEquals("Rana", it.getString(0))
        }
    }

    @Test
    fun blockingFlagsStartOff() {
        helper.createDatabase(TEST_DB, 3).use { db ->
            db.execSQL(
                "INSERT INTO threads (threadId, peerUid, peerPhone, peerName, peerPhotoUrl, " +
                    "isLocal, awaitingConfirmation, lastMessageText, lastMessageAt, " +
                    "unreadCount, updatedAt) VALUES " +
                    "('t1', 'u2', '+8801700000000', 'Rana', NULL, 0, 0, 'hi', 10, 0, 10)"
            )
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 4, true, *PAYCHAT_MIGRATIONS)

        migrated.query("SELECT blockedByMe, blockedByPeer FROM threads WHERE threadId = 't1'")
            .use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
                assertEquals(0, it.getInt(1))
            }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
