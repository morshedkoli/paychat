package com.paychat.paychat.data.profile

import android.net.Uri
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.paychat.paychat.data.auth.AuthRepository
import com.paychat.paychat.data.local.dao.UserDao
import com.paychat.paychat.data.local.entity.UserEntity
import com.paychat.paychat.data.media.MediaFiles
import com.paychat.paychat.data.media.MediaUploader
import com.paychat.paychat.data.remote.Collections
import com.paychat.paychat.data.remote.UserFields
import com.paychat.paychat.data.remote.getLongOrTimestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The signed in user's own profile: their name and picture.
 *
 * Reads come from Room so the settings screen draws immediately, and every
 * write updates both the server and the local copy. The phone number is not
 * here because it is fixed at registration and the security rules refuse to
 * change it.
 */
@Singleton
class ProfileRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val userDao: UserDao,
    private val uploader: MediaUploader,
    private val mediaFiles: MediaFiles,
    private val auth: AuthRepository,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(): Flow<UserEntity?> = flow {
        val uid = auth.currentUid
        if (uid == null) {
            emit(flowOf(null))
        } else {
            // The row may not be cached yet on a device that has only ever
            // written its profile, so it is fetched once before observing.
            refresh(uid)
            emit(userDao.observe(uid))
        }
    }.flatMapLatest { it }

    suspend fun setName(name: String): Result<Unit> = runCatching {
        val uid = auth.currentUid ?: error("not signed in")
        write(uid, mapOf(UserFields.NAME to name))
        userDao.byUid(uid)?.let { userDao.upsert(it.copy(name = name)) }
    }

    /**
     * Uploads a new profile picture and points the profile at it.
     *
     * The picture is copied into the app's own storage first: the gallery uri
     * the picker hands back is a temporary grant, and it can be revoked before
     * the upload finishes.
     */
    suspend fun setPhoto(source: Uri): Result<Unit> = runCatching {
        val uid = auth.currentUid ?: error("not signed in")
        val id = "avatar-" + UUID.randomUUID().toString()

        val file = mediaFiles.copyIn(source, id, "jpg")
            ?: error("that picture could not be read")
        val uploaded = uploader.upload(file, id, isVoice = false).getOrThrow()
        file.delete()

        write(uid, mapOf(UserFields.PHOTO_URL to uploaded.secureUrl))
        userDao.byUid(uid)?.let { userDao.upsert(it.copy(photoUrl = uploaded.secureUrl)) }
    }

    private suspend fun write(uid: String, fields: Map<String, Any?>) {
        firestore.collection(Collections.USERS).document(uid).set(
            fields + (UserFields.UPDATED_AT to System.currentTimeMillis()),
            SetOptions.merge(),
        ).await()
    }

    private suspend fun refresh(uid: String) {
        val document = runCatching {
            firestore.collection(Collections.USERS).document(uid).get().await()
        }.getOrNull() ?: return
        if (!document.exists()) return

        userDao.upsert(
            UserEntity(
                uid = uid,
                phone = document.getString(UserFields.PHONE).orEmpty(),
                name = document.getString(UserFields.NAME).orEmpty(),
                photoUrl = document.getString(UserFields.PHOTO_URL),
                updatedAt = document.getLongOrTimestamp(UserFields.UPDATED_AT) ?: 0L,
            )
        )
    }
}
