package com.paychat.paychat.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.paychat.paychat.data.local.dao.DeviceContactDao
import com.paychat.paychat.data.local.dao.LocalContactDao
import com.paychat.paychat.data.local.dao.MessageDao
import com.paychat.paychat.data.local.dao.ThreadBalanceDao
import com.paychat.paychat.data.local.dao.ThreadDao
import com.paychat.paychat.data.local.dao.TransactionDao
import com.paychat.paychat.data.local.dao.UserDao
import com.paychat.paychat.data.local.entity.DeviceContactEntity
import com.paychat.paychat.data.local.entity.LocalContactEntity
import com.paychat.paychat.data.local.entity.MessageEntity
import com.paychat.paychat.data.local.entity.ThreadBalanceEntity
import com.paychat.paychat.data.local.entity.ThreadEntity
import com.paychat.paychat.data.local.entity.TransactionEntity
import com.paychat.paychat.data.local.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        ThreadEntity::class,
        ThreadBalanceEntity::class,
        MessageEntity::class,
        TransactionEntity::class,
        LocalContactEntity::class,
        DeviceContactEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class PayChatDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun threadDao(): ThreadDao
    abstract fun threadBalanceDao(): ThreadBalanceDao
    abstract fun messageDao(): MessageDao
    abstract fun transactionDao(): TransactionDao
    abstract fun localContactDao(): LocalContactDao
    abstract fun deviceContactDao(): DeviceContactDao

    companion object {
        const val NAME = "paychat.db"
    }
}
