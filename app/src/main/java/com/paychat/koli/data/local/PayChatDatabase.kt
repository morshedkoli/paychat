package com.paychat.koli.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.paychat.koli.data.local.dao.DeviceContactDao
import com.paychat.koli.data.local.dao.LocalContactDao
import com.paychat.koli.data.local.dao.MessageDao
import com.paychat.koli.data.local.dao.ThreadDao
import com.paychat.koli.data.local.dao.TransactionDao
import com.paychat.koli.data.local.dao.UserDao
import com.paychat.koli.data.local.entity.DeviceContactEntity
import com.paychat.koli.data.local.entity.LocalContactEntity
import com.paychat.koli.data.local.entity.MessageEntity
import com.paychat.koli.data.local.entity.ThreadEntity
import com.paychat.koli.data.local.entity.TransactionEntity
import com.paychat.koli.data.local.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        ThreadEntity::class,
        MessageEntity::class,
        TransactionEntity::class,
        LocalContactEntity::class,
        DeviceContactEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class PayChatDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun threadDao(): ThreadDao
    abstract fun messageDao(): MessageDao
    abstract fun transactionDao(): TransactionDao
    abstract fun localContactDao(): LocalContactDao
    abstract fun deviceContactDao(): DeviceContactDao

    companion object {
        const val NAME = "paychat.db"
    }
}
