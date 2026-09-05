package com.paychat.koli.ui.nav

/**
 * Every navigable destination. Route strings live here only, so no screen ever
 * hand-builds a route.
 */
object Routes {
    const val SPLASH = "splash"
    const val REGISTER = "register"
    const val OTP = "otp/{phone}/{purpose}"
    const val LOGIN = "login"

    const val HOME = "home"
    const val CONTACTS = "contacts"
    const val ADD_CONTACT = "contacts/add"
    const val CHAT = "chat/{threadId}"
    const val ADD_TRANSACTION = "chat/{threadId}/transaction/new"
    const val TRANSACTION_DETAIL = "transaction/{txnId}"
    const val LEDGER = "chat/{threadId}/ledger"
    const val INHERITED_REVIEW = "inherited/{threadId}"
    const val SEARCH = "search"
    const val SETTINGS = "settings"

    /**
     * The leading plus of the E.164 number is dropped from the route, because
     * a raw "+" in a path segment is ambiguous once the route is parsed as a
     * URI. The OTP screen puts it back.
     *
     * @param purpose the name of an [com.paychat.koli.feature.auth.otp.OtpPurpose]
     */
    fun otp(phoneE164: String, purpose: String) =
        "otp/${phoneE164.removePrefix("+")}/$purpose"

    fun chat(threadId: String) = "chat/$threadId"
    fun addTransaction(threadId: String) = "chat/$threadId/transaction/new"
    fun transactionDetail(txnId: String) = "transaction/$txnId"
    fun ledger(threadId: String) = "chat/$threadId/ledger"
    fun inheritedReview(threadId: String) = "inherited/$threadId"

    /** Destinations that make up the signed out flow. */
    val AUTH_ROUTES = setOf(SPLASH, REGISTER, LOGIN, OTP)
}

object NavArgs {
    const val PHONE = "phone"
    const val PURPOSE = "purpose"
    const val THREAD_ID = "threadId"
    const val TXN_ID = "txnId"
}
