package com.paychat.paychat.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.persistentCacheSettings
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.ktx.messaging
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideAuth(): FirebaseAuth = Firebase.auth

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = Firebase.firestore.apply {
        // Room is the source of truth for the UI, but Firestore keeps its own
        // cache so that listeners resolve offline and writes are queued.
        firestoreSettings = firestoreSettings {
            setLocalCacheSettings(persistentCacheSettings {})
        }
    }

    /**
     * Pinned to the region the functions are actually deployed in.
     *
     * The default is us-central1, and a callable looked up in the wrong region
     * fails as if it did not exist — which is exactly what happened to every
     * upload signature, report, and account deletion until this was set.
     */
    @Provides
    @Singleton
    fun provideFunctions(): FirebaseFunctions = Firebase.functions(FUNCTIONS_REGION)

    /** Matches the `region` on every handler in firebase/functions/src. */
    private const val FUNCTIONS_REGION = "asia-south1"

    @Provides
    @Singleton
    fun provideMessaging(): FirebaseMessaging = Firebase.messaging
}
