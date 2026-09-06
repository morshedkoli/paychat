package com.paychat.koli.di

import android.content.Context
import androidx.room.Room
import com.paychat.koli.data.local.PayChatDatabase
import com.paychat.koli.data.local.dao.LocalContactDao
import com.paychat.koli.data.local.dao.MessageDao
import com.paychat.koli.data.local.dao.ThreadDao
import com.paychat.koli.data.local.dao.TransactionDao
import com.paychat.koli.data.local.dao.UserDao
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
            // Pre-release only. Room holds a cache of Firestore plus anything
            // still waiting to sync, so wiping it would discard unsent
            // messages and transactions. Replace with real migrations before
            // the first Play Store build (phase 10).
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides fun provideUserDao(db: PayChatDatabase): UserDao = db.userDao()
    @Provides fun provideThreadDao(db: PayChatDatabase): ThreadDao = db.threadDao()
    @Provides fun provideMessageDao(db: PayChatDatabase): MessageDao = db.messageDao()
    @Provides fun provideTransactionDao(db: PayChatDatabase): TransactionDao = db.transactionDao()
    @Provides fun provideLocalContactDao(db: PayChatDatabase): LocalContactDao = db.localContactDao()
}
