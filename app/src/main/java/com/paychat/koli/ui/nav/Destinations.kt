package com.paychat.koli.ui.nav

/**
 * Every navigable destination. Route strings live here only, so no screen ever
 * hand-builds a route.
 */
object Routes {
    const val SPLASH = "splash"
    const val REGISTER = "register"
    const val OTP = "otp/{phone}"
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

    fun otp(phone: String) = "otp/$phone"
    fun chat(threadId: String) = "chat/$threadId"
    fun addTransaction(threadId: String) = "chat/$threadId/transaction/new"
    fun transactionDetail(txnId: String) = "transaction/$txnId"
    fun ledger(threadId: String) = "chat/$threadId/ledger"
    fun inheritedReview(threadId: String) = "inherited/$threadId"
}

object NavArgs {
    const val PHONE = "phone"
    const val THREAD_ID = "threadId"
    const val TXN_ID = "txnId"
}
