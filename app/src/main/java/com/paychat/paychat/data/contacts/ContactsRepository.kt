package com.paychat.paychat.data.contacts

import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.paychat.paychat.core.model.ThreadIds
import com.paychat.paychat.core.phone.PhoneNumbers
import com.paychat.paychat.data.auth.AuthRepository
import com.paychat.paychat.data.local.dao.DeviceContactDao
import com.paychat.paychat.data.local.dao.LocalContactDao
import com.paychat.paychat.data.local.dao.ThreadDao
import com.paychat.paychat.data.local.dao.UserDao
import com.paychat.paychat.data.local.entity.DeviceContactEntity
import com.paychat.paychat.data.local.entity.LocalContactEntity
import com.paychat.paychat.data.local.entity.ThreadEntity
import com.paychat.paychat.data.local.entity.UserEntity
import com.paychat.paychat.data.remote.Collections
import com.paychat.paychat.data.remote.LocalContactFields
import com.paychat.paychat.data.remote.PhoneIndexFields
import com.paychat.paychat.data.remote.ThreadFields
import com.paychat.paychat.data.remote.UserFields
import com.paychat.paychat.data.session.SessionStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Who the user can record money with: people from the address book who already
 * use PayChat, and people added by hand who do not.
 */
@Singleton
class ContactsRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val phoneNumbers: PhoneNumbers,
    private val reader: DeviceContactsReader,
    private val deviceContactDao: DeviceContactDao,
    private val localContactDao: LocalContactDao,
    private val threadDao: ThreadDao,
    private val userDao: UserDao,
    private val session: SessionStore,
    private val auth: AuthRepository,
) {

    fun observeRegistered(): Flow<List<DeviceContactEntity>> = deviceContactDao.observeRegistered()
    fun observeUnregistered(): Flow<List<DeviceContactEntity>> = deviceContactDao.observeUnregistered()
    fun observeLocalContacts(): Flow<List<LocalContactEntity>> = localContactDao.observeAll()

    fun hasContactsPermission(): Boolean = reader.hasPermission()

    /**
     * Reads the address book, works out which numbers have accounts, and
     * stores the result locally.
     *
     * @return how many of the user's contacts are on PayChat
     */
    suspend fun syncDeviceContacts(): Result<Int> = runCatching {
        val ownPhone = session.phone.first()
        val candidates = ContactNormaliser.normalise(
            raw = reader.read(),
            phoneNumbers = phoneNumbers,
            ownUserPhone = ownPhone,
        )
        if (candidates.isEmpty()) return@runCatching 0

        val registered = lookupRegistered(candidates.map { it.phoneE164 })
        val now = System.currentTimeMillis()

        deviceContactDao.upsertAll(
            candidates.map { candidate ->
                DeviceContactEntity(
                    phone = candidate.phoneE164,
                    displayName = candidate.displayName,
                    linkedUid = registered[candidate.phoneE164],
                    resolvedAt = now,
                )
            }
        )
        // Anything not touched by this pass has left the address book.
        deviceContactDao.deleteResolvedBefore(now)

        cacheProfiles(registered.values.toList())
        registered.size
    }

    /**
     * Looks up which of [phones] belong to accounts.
     *
     * Only the numbers travel, and only as document ids in a read of
     * `phoneIndex`. Nothing about the address book is written to the server.
     */
    private suspend fun lookupRegistered(phones: List<String>): Map<String, String> {
        val found = mutableMapOf<String, String>()
        for (batch in ContactNormaliser.batched(phones)) {
            val snapshot = firestore.collection(Collections.PHONE_INDEX)
                .whereIn(FieldPath.documentId(), batch)
                .get()
                .await()
            for (document in snapshot.documents) {
                val uid = document.getString(PhoneIndexFields.UID) ?: continue
                found[document.id] = uid
            }
        }
        return found
    }

    /** Stores names and photos so contact lists render without a round trip. */
    private suspend fun cacheProfiles(uids: List<String>) {
        if (uids.isEmpty()) return
        val users = mutableListOf<UserEntity>()
        for (batch in ContactNormaliser.batched(uids)) {
            val snapshot = firestore.collection(Collections.USERS)
                .whereIn(FieldPath.documentId(), batch)
                .get()
                .await()
            for (document in snapshot.documents) {
                users += UserEntity(
                    uid = document.id,
                    phone = document.getString(UserFields.PHONE).orEmpty(),
                    name = document.getString(UserFields.NAME).orEmpty(),
                    photoUrl = document.getString(UserFields.PHOTO_URL),
                    updatedAt = document.getLong(UserFields.UPDATED_AT) ?: 0L,
                )
            }
        }
        userDao.upsertAll(users)
    }

    /**
     * Finds the account using [rawPhone], if there is one.
     *
     * @return the uid, or null when nobody has registered that number
     */
    suspend fun findAccountByPhone(rawPhone: String): Result<String?> = runCatching {
        val e164 = phoneNumbers.toE164(rawPhone)
            ?: throw IllegalArgumentException("not a valid phone number")
        val document = firestore.collection(Collections.PHONE_INDEX).document(e164).get().await()
        document.getString(PhoneIndexFields.UID)
    }

    /**
     * Opens, or creates, the conversation to use for [rawPhone].
     *
     * A number with an account gets a real two-party thread. A number without
     * one gets a local contact and a one-sided thread, which is promoted to a
     * real thread when that person registers.
     *
     * @return the thread id to navigate to
     */
    suspend fun openOrCreateThread(rawPhone: String, name: String): Result<String> = runCatching {
        val ownUid = auth.currentUid ?: error("not signed in")
        val e164 = phoneNumbers.toE164(rawPhone)
            ?: throw IllegalArgumentException("not a valid phone number")
        val ownPhone = session.phone.first()
        require(e164 != ownPhone) { "you cannot record money with yourself" }

        // An existing thread for this number wins, whichever kind it is, so a
        // second attempt never produces a duplicate conversation.
        threadDao.byPhone(e164)?.let { return@runCatching it.threadId }

        val peerUid = findAccountByPhone(e164).getOrThrow()
        val now = System.currentTimeMillis()

        if (peerUid != null) {
            val threadId = ThreadIds.direct(ownUid, peerUid)
            val peer = firestore.collection(Collections.USERS).document(peerUid).get().await()
            val peerName = peer.getString(UserFields.NAME).orEmpty().ifEmpty { name }

            // Merged, not replaced: the other person may have created this
            // thread first, and a plain set would wipe its last message.
            firestore.collection(Collections.THREADS).document(threadId).set(
                mapOf(
                    ThreadFields.MEMBERS to listOf(ownUid, peerUid).sorted(),
                    ThreadFields.IS_LOCAL to false,
                    ThreadFields.UPDATED_AT to now,
                ),
                SetOptions.merge(),
            ).await()

            threadDao.upsert(
                ThreadEntity(
                    threadId = threadId,
                    peerUid = peerUid,
                    peerPhone = e164,
                    peerName = peerName,
                    peerPhotoUrl = peer.getString(UserFields.PHOTO_URL),
                    isLocal = false,
                    updatedAt = now,
                )
            )
            threadId
        } else {
            val contactId = UUID.randomUUID().toString()
            val threadId = ThreadIds.local(ownUid, contactId)

            firestore.runBatch { batch ->
                batch.set(
                    firestore.collection(Collections.USERS).document(ownUid)
                        .collection(Collections.LOCAL_CONTACTS).document(contactId),
                    mapOf(
                        LocalContactFields.NAME to name,
                        LocalContactFields.PHONE to e164,
                        LocalContactFields.THREAD_ID to threadId,
                        LocalContactFields.CREATED_AT to now,
                    )
                )
                batch.set(
                    firestore.collection(Collections.THREADS).document(threadId),
                    mapOf(
                        ThreadFields.MEMBERS to listOf(ownUid),
                        ThreadFields.IS_LOCAL to true,
                        ThreadFields.LOCAL_CONTACT to mapOf(
                            LocalContactFields.NAME to name,
                            LocalContactFields.PHONE to e164,
                        ),
                        ThreadFields.UPDATED_AT to now,
                    )
                )
            }.await()

            localContactDao.upsert(
                LocalContactEntity(
                    contactId = contactId,
                    name = name,
                    phone = e164,
                    threadId = threadId,
                    createdAt = now,
                )
            )
            threadDao.upsert(
                ThreadEntity(
                    threadId = threadId,
                    peerUid = null,
                    peerPhone = e164,
                    peerName = name,
                    isLocal = true,
                    updatedAt = now,
                )
            )
            threadId
        }
    }
}
