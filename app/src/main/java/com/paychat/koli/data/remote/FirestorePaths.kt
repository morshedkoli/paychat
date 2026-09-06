package com.paychat.koli.data.remote

/**
 * Every Firestore collection and field name used by the app. Nothing else in
 * the codebase spells these out, so a rename is a single edit here and in
 * firebase/firestore.rules.
 */
object Collections {
    const val USERS = "users"
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

object PhoneIndexFields {
    const val UID = "uid"
}

object ThreadFields {
    const val MEMBERS = "members"
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
