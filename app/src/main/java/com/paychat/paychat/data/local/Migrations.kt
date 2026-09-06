package com.paychat.paychat.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Every schema change since the first build that shipped a database.
//
// Room's destructive fallback is gone from here on. The local database is not
// only a cache: it holds messages and transactions that have not reached the
// server yet, and wiping it on an app update would lose money someone had
// already recorded.
//
// Version 1 was never exported, so a device still on it is dropped and rebuilt
// from the server. That version only ever existed on development machines.

/**
 * Balances moved out of the thread row into a table of their own.
 *
 * A balance belongs to the reader rather than to the conversation, and
 * keeping it separate means a balance that arrives before its thread is not
 * lost. The old column is carried across rather than recomputed: the
 * transactions to recompute from may not all be on this device.
 *
 * SQLite before 3.35 cannot drop a column, and Android 24 ships an older one,
 * so the table is rebuilt instead.
 */
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `thread_balances` (" +
                "`threadId` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`threadId`))"
        )
        db.execSQL(
            "INSERT OR REPLACE INTO `thread_balances` (`threadId`, `amountMinor`, `updatedAt`) " +
                "SELECT `threadId`, `balanceMinor`, `updatedAt` FROM `threads`"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `threads_new` (" +
                "`threadId` TEXT NOT NULL, `peerUid` TEXT, `peerPhone` TEXT NOT NULL, " +
                "`peerName` TEXT NOT NULL, `peerPhotoUrl` TEXT, `isLocal` INTEGER NOT NULL, " +
                "`awaitingConfirmation` INTEGER NOT NULL, `lastMessageText` TEXT, " +
                "`lastMessageAt` INTEGER NOT NULL, `unreadCount` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`threadId`))"
        )
        db.execSQL(
            "INSERT INTO `threads_new` SELECT `threadId`, `peerUid`, `peerPhone`, `peerName`, " +
                "`peerPhotoUrl`, `isLocal`, `awaitingConfirmation`, `lastMessageText`, " +
                "`lastMessageAt`, `unreadCount`, `updatedAt` FROM `threads`"
        )
        db.execSQL("DROP TABLE `threads`")
        db.execSQL("ALTER TABLE `threads_new` RENAME TO `threads`")

        recreateThreadIndices(db)
    }
}

/**
 * Blocking, which is two flags on the thread.
 *
 * Both default to 0: an existing conversation is not blocked, and the server
 * corrects any row where that is wrong on the next sync.
 */
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `threads` ADD COLUMN `blockedByMe` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `threads` ADD COLUMN `blockedByPeer` INTEGER NOT NULL DEFAULT 0")
    }
}

val PAYCHAT_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_2_3, MIGRATION_3_4)

/** Rebuilding a table drops its indices with it. */
private fun recreateThreadIndices(db: SupportSQLiteDatabase) {
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_threads_peerUid` ON `threads` (`peerUid`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_threads_peerPhone` ON `threads` (`peerPhone`)")
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_threads_lastMessageAt` ON `threads` (`lastMessageAt`)"
    )
}
