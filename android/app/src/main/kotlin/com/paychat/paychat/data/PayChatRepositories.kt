package com.paychat.paychat.data

import android.content.ContentResolver
import android.net.Uri
import android.webkit.MimeTypeMap
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.paychat.paychat.model.AppMessage
import com.paychat.paychat.model.AppTransaction
import com.paychat.paychat.model.AppUser
import com.paychat.paychat.model.ChatThread
import com.paychat.paychat.model.MessageContentType
import com.paychat.paychat.model.TransactionStatus
import com.paychat.paychat.model.TransactionType
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class UserRepository(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
) {
    private val users = firestore.collection("users")
    private val userDirectory = firestore.collection("user_directory")

    fun watchUserProfile(uid: String): Flow<AppUser?> = callbackFlow {
        val registration = users.document(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }

            val profile = snapshot?.data?.let(AppUser::fromMap)
            trySend(profile)
        }

        awaitClose { registration.remove() }
    }

    suspend fun fetchUserProfile(uid: String): AppUser? {
        val snapshot = users.document(uid).get().await()
        return snapshot.data?.let(AppUser::fromMap)
    }

    suspend fun ensureUserProfile(firebaseUser: FirebaseUser): AppUser {
        val existing = fetchUserProfile(firebaseUser.uid)
        val now = Date()

        if (existing == null) {
            val created = AppUser(
                uid = firebaseUser.uid,
                phoneNumber = firebaseUser.phoneNumber.orEmpty(),
                fullName = firebaseUser.displayName?.trim().orEmpty(),
                photoUrl = firebaseUser.photoUrl?.toString().orEmpty(),
                email = firebaseUser.email.orEmpty(),
                address = "",
                createdAt = now,
                updatedAt = now,
                lastLoginAt = now,
                isProfileComplete = !firebaseUser.displayName.isNullOrBlank(),
                isBlocked = false,
                role = "user",
                deviceTokens = emptyList(),
            )

            users.document(firebaseUser.uid).set(created.toMap()).await()
            syncUserDirectory(created)
            return created
        }

        val merged = existing.copy(
            phoneNumber = firebaseUser.phoneNumber ?: existing.phoneNumber,
            email = existing.email.ifBlank { firebaseUser.email.orEmpty() },
            photoUrl = existing.photoUrl.ifBlank { firebaseUser.photoUrl?.toString().orEmpty() },
            updatedAt = now,
            lastLoginAt = now,
        )

        users.document(firebaseUser.uid).set(merged.toMap(), SetOptions.merge()).await()
        syncUserDirectory(merged)
        return merged
    }

    suspend fun updateProfile(
        uid: String,
        fullName: String,
        email: String,
        address: String,
        imageUri: Uri?,
        contentResolver: ContentResolver,
    ): AppUser {
        val existing = fetchUserProfile(uid) ?: error("User profile not found.")
        val photoUrl = if (imageUri != null) {
            uploadProfilePhoto(uid = uid, imageUri = imageUri, contentResolver = contentResolver)
        } else {
            existing.photoUrl
        }

        val updated = existing.copy(
            fullName = fullName.trim(),
            email = email.trim(),
            address = address.trim(),
            photoUrl = photoUrl,
            updatedAt = Date(),
            isProfileComplete = fullName.trim().isNotEmpty(),
        )

        users.document(uid).set(updated.toMap(), SetOptions.merge()).await()
        syncUserDirectory(updated)
        return updated
    }

    suspend fun findUserByPhone(phoneNumber: String): AppUser? {
        val searchTokens = phoneSearchTokens(phoneNumber)
        if (searchTokens.isEmpty()) return null

        val directorySnapshot = userDirectory
            .whereArrayContainsAny("phoneSearchTokens", searchTokens.take(10))
            .get()
            .await()

        val uid = directorySnapshot.documents
            .firstNotNullOfOrNull { it.getString("uid")?.takeIf(String::isNotBlank) }
            ?: return null

        return fetchUserProfile(uid)
    }

    private suspend fun uploadProfilePhoto(
        uid: String,
        imageUri: Uri,
        contentResolver: ContentResolver,
    ): String {
        val contentType = contentResolver.getType(imageUri) ?: "image/jpeg"
        val extension = MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(contentType)
            ?.lowercase()
            ?: "jpg"
        val reference = storage.reference.child("profile_photos/$uid/avatar.$extension")
        val metadata = StorageMetadata.Builder()
            .setContentType(contentType)
            .build()

        reference.putFile(imageUri, metadata).await()
        return reference.downloadUrl.await().toString()
    }

    private suspend fun syncUserDirectory(user: AppUser) {
        if (user.phoneNumber.isBlank()) return

        val payload = mapOf(
            "uid" to user.uid,
            "phoneNumber" to user.phoneNumber,
            "fullName" to user.fullName,
            "photoUrl" to user.photoUrl,
            "phoneSearchTokens" to phoneSearchTokens(user.phoneNumber),
            "updatedAt" to Timestamp(user.updatedAt),
        )

        userDirectory.document(user.uid).set(payload, SetOptions.merge()).await()
    }
}

class TransactionRepository {
    private val transactionsByUser = mutableMapOf<String, MutableStateFlow<List<AppTransaction>>>()

    fun observeTransactions(userId: String): StateFlow<List<AppTransaction>> = flowFor(userId)

    fun currentTransactions(userId: String): List<AppTransaction> = flowFor(userId).value

    fun getTransactionById(userId: String, transactionId: String): AppTransaction? {
        return currentTransactions(userId).firstOrNull { it.transactionId == transactionId }
    }

    suspend fun addTransaction(
        userId: String,
        contactName: String,
        contactPhone: String,
        type: TransactionType,
        amount: Double,
        note: String,
    ) {
        val flow = flowFor(userId)
        val now = Date()
        val transaction = AppTransaction(
            transactionId = UUID.randomUUID().toString(),
            userId = userId,
            contactName = contactName.trim(),
            contactPhone = contactPhone.trim(),
            type = type,
            amount = amount,
            note = note.trim(),
            createdAt = now,
            updatedAt = now,
            status = TransactionStatus.COMPLETED,
        )

        flow.value = listOf(transaction) + flow.value
    }

    private fun flowFor(userId: String): MutableStateFlow<List<AppTransaction>> {
        return transactionsByUser.getOrPut(userId) {
            MutableStateFlow(seedTransactions(userId))
        }
    }

    private fun seedTransactions(userId: String): List<AppTransaction> {
        val now = Date()
        return listOf(
            AppTransaction(
                transactionId = UUID.randomUUID().toString(),
                userId = userId,
                contactName = "Nafisa Rahman",
                contactPhone = "+8801712345678",
                type = TransactionType.RECEIVED,
                amount = 3200.0,
                note = "Monthly repayment",
                createdAt = Date(now.time - 5 * 60 * 60 * 1000L),
                updatedAt = Date(now.time - 5 * 60 * 60 * 1000L),
                status = TransactionStatus.COMPLETED,
            ),
            AppTransaction(
                transactionId = UUID.randomUUID().toString(),
                userId = userId,
                contactName = "Arif Store",
                contactPhone = "+8801911223344",
                type = TransactionType.PAID,
                amount = 860.0,
                note = "Inventory restock",
                createdAt = Date(now.time - 24 * 60 * 60 * 1000L),
                updatedAt = Date(now.time - 24 * 60 * 60 * 1000L),
                status = TransactionStatus.COMPLETED,
            ),
            AppTransaction(
                transactionId = UUID.randomUUID().toString(),
                userId = userId,
                contactName = "Mitu Akter",
                contactPhone = "+8801811002200",
                type = TransactionType.DUE,
                amount = 1450.0,
                note = "Pending collection",
                createdAt = Date(now.time - 2 * 24 * 60 * 60 * 1000L),
                updatedAt = Date(now.time - 2 * 24 * 60 * 60 * 1000L),
                status = TransactionStatus.PENDING,
            ),
        )
    }
}

class ChatRepository(
    private val firestore: FirebaseFirestore,
) {
    private val threads = firestore.collection("threads")
    private val threadsByMemberKey = mutableMapOf<String, MutableStateFlow<List<ChatThread>>>()
    private val messagesByThread = mutableMapOf<String, MutableStateFlow<List<AppMessage>>>()
    private val threadRegistrations = mutableMapOf<String, MutableList<ListenerRegistration>>()
    private val messageRegistrations = mutableMapOf<String, ListenerRegistration>()

    fun observeThreads(currentUser: AppUser): StateFlow<List<ChatThread>> {
        val observerKey = currentUser.uid
        startThreadObserver(currentUser)
        return threadsByMemberKey.getOrPut(observerKey) { MutableStateFlow(emptyList()) }
    }

    fun observeMessages(threadId: String): StateFlow<List<AppMessage>> {
        startMessageObserver(threadId)
        return messagesByThread.getOrPut(threadId) { MutableStateFlow(emptyList()) }
    }

    fun currentThreads(currentUser: AppUser): List<ChatThread> {
        return threadsByMemberKey[currentUser.uid]?.value.orEmpty()
    }

    fun getThreadById(currentUser: AppUser, threadId: String): ChatThread? {
        return currentThreads(currentUser).firstOrNull { it.id == threadId }
    }

    suspend fun createThread(
        currentUser: AppUser,
        memberDisplayName: String,
        memberPhone: String,
        memberUser: AppUser?,
    ): ChatThread {
        startThreadObserver(currentUser)

        val resolvedMemberPhone = memberUser?.phoneNumber?.takeIf { it.isNotBlank() } ?: memberPhone
        val existing = findExistingThread(currentUser.phoneNumber, resolvedMemberPhone)
        if (existing != null) {
            return existing
        }

        val now = Date()
        val threadId = UUID.randomUUID().toString()
        val participantIds = listOfNotNull(currentUser.uid, memberUser?.uid)
        val participantPhones = listOfNotNull(
            currentUser.phoneNumber.takeIf { it.isNotBlank() },
            resolvedMemberPhone.takeIf { it.isNotBlank() },
        ).distinct()
        val participantNames = mapOf(
            currentUser.phoneNumber to currentUser.displayName,
            resolvedMemberPhone to memberDisplayName.trim().ifBlank { memberUser?.displayName ?: resolvedMemberPhone },
        )
        val participantPhotoUrls = mapOf(
            currentUser.phoneNumber to currentUser.photoUrl,
            resolvedMemberPhone to (memberUser?.photoUrl ?: ""),
        )
        val initialMessage = "${currentUser.displayName} started a chat."
        val threadPayload = mapOf(
            "participantIds" to participantIds,
            "participantPhones" to participantPhones,
            "participantNames" to participantNames,
            "participantPhotoUrls" to participantPhotoUrls,
            "lastMessagePreview" to initialMessage,
            "updatedAt" to Timestamp(now),
            "createdAt" to Timestamp(now),
            "createdBy" to currentUser.uid,
        )

        threads.document(threadId).set(threadPayload).await()
        threads.document(threadId)
            .collection("messages")
            .document()
            .set(
                mapOf(
                    "senderId" to currentUser.uid,
                    "text" to initialMessage,
                    "createdAt" to Timestamp(now),
                    "type" to MessageContentType.TEXT.name,
                    "amount" to null,
                    "isRead" to true,
                ),
            )
            .await()

        return ChatThread(
            id = threadId,
            participantIds = participantIds.ifEmpty { listOf(currentUser.uid) },
            title = participantNames[resolvedMemberPhone].orEmpty(),
            avatarUrl = memberUser?.photoUrl.orEmpty(),
            lastMessagePreview = initialMessage,
            updatedAt = now,
            unreadCount = 0,
        )
    }

    suspend fun sendMessage(
        userId: String,
        threadId: String,
        senderId: String,
        text: String,
    ) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val message = mapOf(
            "senderId" to senderId,
            "text" to trimmed,
            "createdAt" to Timestamp(Date()),
            "type" to MessageContentType.TEXT.name,
            "amount" to null,
            "isRead" to true,
        )

        appendMessage(threadId, message)
        updateThreadActivity(threadId = threadId, preview = trimmed)
    }
    suspend fun sendTransactionMessage(
        userId: String,
        threadId: String,
        senderId: String,
        text: String,
        amount: Double,
    ) {
        val summary = text.trim()
        if (summary.isEmpty() || amount <= 0) return

        val message = mapOf(
            "senderId" to senderId,
            "text" to summary,
            "createdAt" to Timestamp(Date()),
            "type" to MessageContentType.TRANSACTION.name,
            "amount" to amount,
            "isRead" to true,
        )

        appendMessage(threadId, message)
        updateThreadActivity(threadId = threadId, preview = summary)
    }
    private suspend fun appendMessage(
        threadId: String,
        payload: Map<String, Any?>,
    ) {
        threads.document(threadId)
            .collection("messages")
            .document()
            .set(payload.filterValues { true })
            .await()
    }

    private suspend fun updateThreadActivity(
        threadId: String,
        preview: String,
    ) {
        threads.document(threadId).set(
            mapOf(
                "lastMessagePreview" to preview,
                "updatedAt" to Timestamp(Date()),
            ),
            SetOptions.merge(),
        ).await()
    }

    private suspend fun findExistingThread(
        currentUserPhone: String,
        otherUserPhone: String,
    ): ChatThread? {
        val snapshot = threads
            .whereArrayContains("participantPhones", currentUserPhone)
            .get()
            .await()

        val match = snapshot.documents.firstOrNull { document ->
            val participantPhones = (document.get("participantPhones") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
            participantPhones.size == 2 && participantPhones.contains(otherUserPhone)
        } ?: return null

        return match.toChatThread(
            currentUserPhone = currentUserPhone,
            currentUserId = null,
        )
    }

    private fun startThreadObserver(currentUser: AppUser) {
        val observerKey = currentUser.uid
        if (threadRegistrations.containsKey(observerKey)) return

        val flow = threadsByMemberKey.getOrPut(observerKey) { MutableStateFlow(emptyList()) }
        val registrations = mutableListOf<ListenerRegistration>()
        val currentItems = linkedMapOf<String, ChatThread>()

        fun publish() {
            flow.value = currentItems.values.sortedByDescending { it.updatedAt.time }
        }

        registrations += threads
            .whereArrayContains("participantIds", currentUser.uid)
            .addSnapshotListener { snapshot, _ ->
                    snapshot?.documents?.forEach { document ->
                    document.toChatThread(
                        currentUserPhone = currentUser.phoneNumber,
                        currentUserId = currentUser.uid,
                    )?.let { currentItems[it.id] = it }
                }
                publish()
            }

        if (currentUser.phoneNumber.isNotBlank()) {
            registrations += threads
                .whereArrayContains("participantPhones", currentUser.phoneNumber)
                .addSnapshotListener { snapshot, _ ->
                    snapshot?.documents?.forEach { document ->
                        document.toChatThread(
                            currentUserPhone = currentUser.phoneNumber,
                            currentUserId = currentUser.uid,
                        )?.let { currentItems[it.id] = it }
                    }
                    publish()
                }
        }

        threadRegistrations[observerKey] = registrations
    }

    private fun startMessageObserver(threadId: String) {
        if (messageRegistrations.containsKey(threadId)) return

        val flow = messagesByThread.getOrPut(threadId) { MutableStateFlow(emptyList()) }
        val registration = threads.document(threadId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                flow.value = snapshot?.documents?.mapNotNull { it.toAppMessage(threadId) }.orEmpty()
            }

        messageRegistrations[threadId] = registration
    }
}

private fun DocumentSnapshot.toChatThread(
    currentUserPhone: String,
    currentUserId: String?,
): ChatThread? {
    val participantIds = (get("participantIds") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
    val participantPhones = (get("participantPhones") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
    val matchesByUid = currentUserId != null && participantIds.contains(currentUserId)
    val matchesByPhone = currentUserPhone.isNotBlank() && participantPhones.contains(currentUserPhone)
    if (!matchesByUid && !matchesByPhone) return null

    val participantNames = get("participantNames") as? Map<*, *>
    val participantPhotoUrls = get("participantPhotoUrls") as? Map<*, *>
    val otherPhone = participantPhones.firstOrNull { it != currentUserPhone }
    val title = (otherPhone?.let { participantNames?.get(it) as? String })
        .orEmpty()
        .ifBlank { "PayChat User" }
    val avatarUrl = otherPhone?.let { participantPhotoUrls?.get(it) as? String }.orEmpty()

    return ChatThread(
        id = id,
        participantIds = participantIds.ifEmpty { participantPhones },
        title = title,
        avatarUrl = avatarUrl,
        lastMessagePreview = getString("lastMessagePreview").orEmpty(),
        updatedAt = get("updatedAt").toRepositoryDateOrNow(),
        unreadCount = 0,
    )
}

private fun DocumentSnapshot.toAppMessage(threadId: String): AppMessage? {
    return AppMessage(
        id = id,
        threadId = threadId,
        senderId = getString("senderId").orEmpty(),
        text = getString("text").orEmpty(),
        createdAt = get("createdAt").toRepositoryDateOrNow(),
        type = runCatching {
            MessageContentType.valueOf(getString("type").orEmpty())
        }.getOrDefault(MessageContentType.TEXT),
        amount = getDouble("amount"),
        isRead = getBoolean("isRead") ?: true,
    )
}

private fun phoneSearchTokens(phoneNumber: String): List<String> {
    val digits = phoneNumber.filter(Char::isDigit)
    if (digits.isBlank()) return emptyList()

    return buildSet {
        add(digits)
        if (digits.length > 7) add(digits.takeLast(7))
        if (digits.length > 10) add(digits.takeLast(10))
        if (digits.length > 11) add(digits.takeLast(11))
        if (digits.startsWith("0")) add(digits.trimStart('0'))
    }.filter { it.isNotBlank() }.toList()
}

private fun Any?.toRepositoryDateOrNow(): Date {
    return when (this) {
        is Timestamp -> toDate()
        is Date -> this
        is Long -> Date(this)
        else -> Date()
    }
}
