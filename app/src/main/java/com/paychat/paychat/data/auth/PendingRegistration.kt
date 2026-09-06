package com.paychat.paychat.data.auth

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the details entered on the registration or reset screen while the user
 * is away verifying the OTP.
 *
 * This is deliberately in memory only. The password must not travel in a
 * navigation route, where it would sit in the back stack, and it must not be
 * written to disk.
 */
@Singleton
class PendingRegistration @Inject constructor() {

    data class Details(
        val phoneE164: String,
        val name: String,
        val password: String,
    )

    @Volatile
    private var details: Details? = null

    fun put(phoneE164: String, name: String, password: String) {
        details = Details(phoneE164, name, password)
    }

    fun peek(): Details? = details

    /** Reads the details and forgets them, so a password is never held longer than one use. */
    fun take(): Details? = details.also { details = null }

    fun clear() {
        details = null
    }
}
