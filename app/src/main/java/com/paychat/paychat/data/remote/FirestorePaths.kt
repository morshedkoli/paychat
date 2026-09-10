package com.paychat.paychat.data.remote

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

fun DocumentSnapshot.getLongOrTimestamp(field: String): Long? {
    return when (val value = get(field)) {
        is Number -> value.toLong()
        is Timestamp -> value.toDate().time
        is String -> value.toLongOrNull()
        else -> null
    }
}

/**
 * Every Firestore collection and field name used by the app. Nothing else in
 * the codebase spells these out, so a rename is a single edit here and in
 * firebase/firestore.rules.
 */
object Collections {
    const val USERS = "users"
    const val SESSIONS = "sessions"
    const val PHONE_INDEX = "phoneIndex"
    const val THREADS = "threads"
    const val MESSAGES = "messages"
    const val TRANSACTIONS = "transactions"
    const val THREAD_BALANCES = "threadBalances"
    const val LOCAL_CONTACTS = "localContacts"
}

object UserFields {
    const val PHONE = "phone"
    const val NAME = "name"
    const val PHOTO_URL = "photoUrl"
    const val CREATED_AT = "createdAt"
    const val UPDATED_AT = "updatedAt"
    const val FCM_TOKEN = "fcmToken"

    /**
     * Identifies the one device currently allowed to use this account. A device
     * whose stored session id stops matching this value signs itself out.
     */
    const val ACTIVE_SESSION_ID = "activeSessionId"
}

object SessionFields {
    const val ACTIVE_SESSION_ID = "activeSessionId"
    const val UPDATED_AT = "updatedAt"
}

object PhoneIndexFields {
    const val UID = "uid"
}

object ThreadFields {
    const val MEMBERS = "members"

    /** Uids who have blocked the other party in this thread. */
    const val BLOCKED_BY = "blockedBy"

    /**
     * Uid to the moment they last said they were typing, in epoch
     * milliseconds. A member may only write their own key.
     */
    const val TYPING = "typing"

    /** Uids whose account has been deleted. Written by the delete function. */
    const val DEPARTED = "departed"
    const val IS_LOCAL = "isLocal"
    const val LOCAL_CONTACT = "localContact"
    const val LAST_MESSAGE = "lastMessage"
    const val UPDATED_AT = "updatedAt"
}

object MessageFields {
    const val SENDER_ID = "senderId"
    const val TYPE = "type"
    const val TEXT = "text"
    const val MEDIA_URL = "mediaUrl"
    const val MEDIA_PUBLIC_ID = "mediaPublicId"
    const val DURATION_MS = "durationMs"
    const val TXN_ID = "txnId"
    const val CREATED_AT = "createdAt"

    /** Uids that have received the message. */
    const val DELIVERED_TO = "deliveredTo"

    /** Uids that have opened the conversation since the message arrived. */
    const val READ_BY = "readBy"
}

object TransactionFields {
    const val CREATED_BY = "createdBy"
    const val DIRECTION = "direction"
    const val AMOUNT_MINOR = "amountMinor"
    const val NOTE = "note"
    const val PHOTO_URL = "photoUrl"
    const val PHOTO_PUBLIC_ID = "photoPublicId"
    const val DUE_DATE = "dueDate"
    const val STATUS = "status"
    const val UNCONFIRMED = "unconfirmed"
    const val REVERSES_ID = "reversesId"
    const val REVERSED_BY = "reversedBy"
    const val CREATED_AT = "createdAt"
    const val RESOLVED_AT = "resolvedAt"
    const val RESOLVED_BY = "resolvedBy"
}

object ThreadBalanceFields {
    const val AMOUNT_MINOR = "amountMinor"
    const val UPDATED_AT = "updatedAt"
}

object LastMessageFields {
    const val TEXT = "text"
    const val TYPE = "type"
    const val AT = "at"
    const val SENDER_ID = "senderId"
}

object LocalContactFields {
    const val NAME = "name"
    const val PHONE = "phone"
    const val THREAD_ID = "threadId"
    const val LINKED_UID = "linkedUid"
    const val CREATED_AT = "createdAt"
}
