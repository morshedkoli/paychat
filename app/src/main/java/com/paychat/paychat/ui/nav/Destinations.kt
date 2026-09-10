package com.paychat.paychat.ui.nav

/**
 * Every navigable destination. Route strings live here only, so no screen ever
 * hand-builds a route.
 */
object Routes {
    const val SPLASH = "splash"
    const val PHONE = "phone"
    const val REGISTER = "register/{phone}"
    const val OTP = "otp/{phone}/{purpose}"
    const val LOGIN = "login/{phone}"

    /** The signed in landing destination: a tab shell, not a screen. */
    const val MAIN = "main"

    // Tab roots. These live in the shell's own graph, never the outer one, so
    // the bottom bar cannot appear over a full screen destination.
    const val CHATS = "chats"
    const val TRANSACTIONS = "transactions"
    const val PROFILE = "profile"

    const val CONTACTS = "contacts"
    const val ADD_CONTACT = "contacts/add"
    const val CHAT = "chat/{threadId}"
    const val ADD_TRANSACTION =
        "chat/{threadId}/transaction/new?direction={direction}&amount={amount}&note={note}&category={category}"
    const val TRANSACTION_DETAIL = "transaction/{txnId}"
    const val LEDGER = "chat/{threadId}/ledger"
    const val INHERITED_REVIEW = "inherited/{threadId}"
    const val SEARCH = "search"

    /**
     * The leading plus of the E.164 number is dropped from the route, because
     * a raw "+" in a path segment is ambiguous once the route is parsed as a
     * URI. The receiving screen puts it back. This holds for every route
     * below that carries a number.
     *
     * @param purpose the name of an [com.paychat.paychat.feature.auth.otp.OtpPurpose]
     */
    fun otp(phoneE164: String, purpose: String) =
        "otp/${phoneE164.removePrefix("+")}/$purpose"

    fun login(phoneE164: String) = "login/${phoneE164.removePrefix("+")}"

    fun register(phoneE164: String) = "register/${phoneE164.removePrefix("+")}"

    fun chat(threadId: String) = "chat/$threadId"

    fun addTransaction(
        threadId: String,
        direction: String? = null,
        amount: String? = null,
        note: String? = null,
        category: String? = null,
    ): String {
        val params = mutableListOf<String>()
        if (direction != null) params += "direction=$direction"
        if (amount != null) params += "amount=$amount"
        if (note != null) params += "note=$note"
        if (category != null) params += "category=$category"
        return if (params.isEmpty()) "chat/$threadId/transaction/new"
        else "chat/$threadId/transaction/new?${params.joinToString("&")}"
    }

    fun transactionDetail(txnId: String) = "transaction/$txnId"
    fun ledger(threadId: String) = "chat/$threadId/ledger"
    fun inheritedReview(threadId: String) = "inherited/$threadId"

    /** Destinations that make up the signed out flow. */
    val AUTH_ROUTES = setOf(PHONE, REGISTER, LOGIN, OTP)
}

object NavArgs {
    const val PHONE = "phone"
    const val PURPOSE = "purpose"
    const val THREAD_ID = "threadId"
    const val TXN_ID = "txnId"
    const val DIRECTION = "direction"
    const val AMOUNT = "amount"
    const val NOTE = "note"
    const val CATEGORY = "category"
}
