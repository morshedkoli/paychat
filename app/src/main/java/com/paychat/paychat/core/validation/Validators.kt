package com.paychat.paychat.core.validation

/**
 * Input rules shared by the registration, login, and password reset screens,
 * so that all three agree on what is acceptable.
 */
object Validators {

    const val MIN_PASSWORD_LENGTH = 8
    const val MAX_NAME_LENGTH = 60
    const val OTP_LENGTH = 6

    fun validateName(raw: String): NameError? {
        val name = raw.trim()
        return when {
            name.isEmpty() -> NameError.EMPTY
            name.length > MAX_NAME_LENGTH -> NameError.TOO_LONG
            else -> null
        }
    }

    /**
     * The synthetic email address means an attacker who knows a phone number
     * also knows the sign-in identifier, so the password is the only secret
     * protecting the account. A minimum length is enforced here and the most
     * obvious weak choices are rejected outright.
     */
    fun validatePassword(password: String): PasswordError? = when {
        password.isEmpty() -> PasswordError.EMPTY
        password.length < MIN_PASSWORD_LENGTH -> PasswordError.TOO_SHORT
        password.all { it.isDigit() } -> PasswordError.DIGITS_ONLY
        password in COMMON_PASSWORDS -> PasswordError.TOO_COMMON
        else -> null
    }

    fun isValidOtp(code: String): Boolean =
        code.length == OTP_LENGTH && code.all { it.isDigit() }

    private val COMMON_PASSWORDS = setOf(
        "password", "password1", "12345678", "123456789", "1234567890",
        "qwertyui", "iloveyou", "abc12345", "passw0rd", "11111111",
    )
}

enum class NameError { EMPTY, TOO_LONG }

enum class PasswordError { EMPTY, TOO_SHORT, DIGITS_ONLY, TOO_COMMON }
