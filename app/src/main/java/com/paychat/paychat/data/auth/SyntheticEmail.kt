package com.paychat.paychat.data.auth

/**
 * Firebase Auth has no password support for the phone provider, and no phone
 * support for the password provider. PayChat needs both on one account, so the
 * account carries a linked Email/Password credential whose address is derived
 * from the phone number. No mail is ever sent to it.
 *
 * The address is deterministic, so signing in only needs the phone number the
 * user types. It is also guessable by anyone who knows the phone number, which
 * is why [com.paychat.paychat.core.validation.Validators.validatePassword]
 * enforces a real password and why App Check should be enabled on the project.
 */
object SyntheticEmail {

    /** Reserved by RFC 6761, so it can never resolve to a real mail host. */
    const val DOMAIN = "phone.paychat.invalid"

    /**
     * @param e164 a phone number already normalised to E.164, e.g. "+8801712345678"
     */
    fun forPhone(e164: String): String {
        require(e164.startsWith("+")) { "expected an E.164 number, got: $e164" }
        val digits = e164.removePrefix("+")
        require(digits.isNotEmpty() && digits.all { it.isDigit() }) {
            "expected an E.164 number, got: $e164"
        }
        return "p$digits@$DOMAIN"
    }
}
