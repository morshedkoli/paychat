package com.paychat.paychat.model

import com.google.firebase.Timestamp
import java.util.Date

enum class SessionStatus {
    LOADING,
    UNAUTHENTICATED,
    PROFILE_INCOMPLETE,
    AUTHENTICATED,
    BLOCKED,
}

data class SessionState(
    val status: SessionStatus,
    val user: AppUser? = null,
    val message: String? = null,
) {
    companion object {
        fun loading() = SessionState(status = SessionStatus.LOADING)

        fun unauthenticated(message: String? = null) = SessionState(
            status = SessionStatus.UNAUTHENTICATED,
            message = message,
        )

        fun profileIncomplete(user: AppUser) = SessionState(
            status = SessionStatus.PROFILE_INCOMPLETE,
            user = user,
        )

        fun authenticated(user: AppUser) = SessionState(
            status = SessionStatus.AUTHENTICATED,
            user = user,
        )

        fun blocked(user: AppUser) = SessionState(
            status = SessionStatus.BLOCKED,
            user = user,
        )
    }
}

enum class AuthEntryStep {
    PHONE,
    OTP,
}

data class AuthEntryState(
    val step: AuthEntryStep = AuthEntryStep.PHONE,
    val countryIso: String = "BD",
    val countryDialCode: String = "+880",
    val nationalNumber: String = "",
    val phoneNumberE164: String = "",
    val otpSentTo: String? = null,
    val verificationId: String? = null,
    val isSendingCode: Boolean = false,
    val isVerifyingCode: Boolean = false,
    val errorMessage: String? = null,
)

data class AppUser(
    val uid: String,
    val phoneNumber: String,
    val fullName: String,
    val photoUrl: String,
    val email: String,
    val address: String,
    val createdAt: Date,
    val updatedAt: Date,
    val lastLoginAt: Date,
    val isProfileComplete: Boolean,
    val isBlocked: Boolean,
    val role: String,
    val deviceTokens: List<String>,
) {
    val displayName: String
        get() = fullName.trim().ifBlank { phoneNumber.ifBlank { "PayChat User" } }

    fun toMap(): Map<String, Any> = mapOf(
        "uid" to uid,
        "phoneNumber" to phoneNumber,
        "fullName" to fullName,
        "photoUrl" to photoUrl,
        "email" to email,
        "address" to address,
        "createdAt" to Timestamp(createdAt),
        "updatedAt" to Timestamp(updatedAt),
        "lastLoginAt" to Timestamp(lastLoginAt),
        "isProfileComplete" to isProfileComplete,
        "isBlocked" to isBlocked,
        "role" to role,
        "deviceTokens" to deviceTokens,
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): AppUser = AppUser(
            uid = map["uid"] as? String ?: "",
            phoneNumber = map["phoneNumber"] as? String ?: "",
            fullName = map["fullName"] as? String ?: "",
            photoUrl = map["photoUrl"] as? String ?: "",
            email = map["email"] as? String ?: "",
            address = map["address"] as? String ?: "",
            createdAt = map["createdAt"].toDateOrNow(),
            updatedAt = map["updatedAt"].toDateOrNow(),
            lastLoginAt = map["lastLoginAt"].toDateOrNow(),
            isProfileComplete = map["isProfileComplete"] as? Boolean ?: false,
            isBlocked = map["isBlocked"] as? Boolean ?: false,
            role = map["role"] as? String ?: "user",
            deviceTokens = (map["deviceTokens"] as? List<*>)?.mapNotNull { it as? String }.orEmpty(),
        )
    }
}

enum class TransactionType {
    SENT,
    RECEIVED,
    PAID,
    DUE,
}

enum class TransactionStatus {
    PENDING,
    COMPLETED,
    CANCELLED,
}

data class AppTransaction(
    val transactionId: String,
    val userId: String,
    val contactName: String,
    val contactPhone: String,
    val type: TransactionType,
    val amount: Double,
    val note: String,
    val createdAt: Date,
    val updatedAt: Date,
    val status: TransactionStatus,
) {
    val isPositive: Boolean
        get() = type == TransactionType.RECEIVED || type == TransactionType.DUE
}

data class ChatThread(
    val id: String,
    val participantIds: List<String>,
    val title: String,
    val avatarUrl: String,
    val lastMessagePreview: String,
    val updatedAt: Date,
    val unreadCount: Int,
)

enum class MessageContentType {
    TEXT,
    TRANSACTION,
}

data class AppMessage(
    val id: String,
    val threadId: String,
    val senderId: String,
    val text: String,
    val createdAt: Date,
    val type: MessageContentType,
    val amount: Double? = null,
    val isRead: Boolean = true,
)

private fun Any?.toDateOrNow(): Date {
    return when (this) {
        is Timestamp -> toDate()
        is Date -> this
        is Long -> Date(this)
        else -> Date()
    }
}
