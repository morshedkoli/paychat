package com.paychat.paychat.di

import android.content.Context
import androidx.room.Room
import com.paychat.paychat.data.local.PAYCHAT_MIGRATIONS
import com.paychat.paychat.data.local.PayChatDatabase
import com.paychat.paychat.data.local.dao.DeviceContactDao
import com.paychat.paychat.data.local.dao.LocalContactDao
import com.paychat.paychat.data.local.dao.MessageDao
import com.paychat.paychat.data.local.dao.ThreadBalanceDao
import com.paychat.paychat.data.local.dao.ThreadDao
import com.paychat.paychat.data.local.dao.TransactionDao
import com.paychat.paychat.data.local.dao.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PayChatDatabase =
        Room.databaseBuilder(context, PayChatDatabase::class.java, PayChatDatabase.NAME)
            .addMigrations(*PAYCHAT_MIGRATIONS)
            // Version 1 was never exported and only ever existed on
            // development machines, so it is the one version rebuilt from
            // scratch. Every later version migrates: the database holds
            // messages and transactions that have not reached the server yet,
            // and wiping it on an update would lose money already recorded.
            .fallbackToDestructiveMigrationFrom(1)
            .build()

    @Provides fun provideUserDao(db: PayChatDatabase): UserDao = db.userDao()
    @Provides fun provideThreadDao(db: PayChatDatabase): ThreadDao = db.threadDao()
    @Provides fun provideThreadBalanceDao(db: PayChatDatabase): ThreadBalanceDao = db.threadBalanceDao()
    @Provides fun provideMessageDao(db: PayChatDatabase): MessageDao = db.messageDao()
    @Provides fun provideTransactionDao(db: PayChatDatabase): TransactionDao = db.transactionDao()
    @Provides fun provideLocalContactDao(db: PayChatDatabase): LocalContactDao = db.localContactDao()
    @Provides fun provideDeviceContactDao(db: PayChatDatabase): DeviceContactDao = db.deviceContactDao()
}
