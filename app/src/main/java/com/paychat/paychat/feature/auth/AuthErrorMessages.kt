package com.paychat.paychat.feature.auth

import com.paychat.paychat.core.validation.NameError
import com.paychat.paychat.core.validation.PasswordError
import com.paychat.paychat.core.validation.Validators
import com.paychat.paychat.data.auth.AuthError

/**
 * Turns errors into what the user reads. Kept out of the ViewModels so the
 * wording of a failure is in one place.
 */
fun AuthError.message(): String = when (this) {
    AuthError.InvalidPhoneNumber -> "That phone number does not look right."
    AuthError.PhoneAlreadyRegistered -> "This number is already registered. Log in instead."
    AuthError.PhoneNotRegistered -> "No account uses this number. Create one first."
    AuthError.InvalidOtp -> "That code is not correct."
    AuthError.OtpExpired -> "That code has expired. Ask for a new one."
    AuthError.WrongPassword -> "Wrong password."
    AuthError.WeakPassword -> "Choose a longer password."
    AuthError.TooManyRequests -> "Too many attempts. Try again later."
    AuthError.Network -> "No connection. Check your internet and try again."
    AuthError.SessionReplaced -> "You were signed in on another device."
    is AuthError.Unknown -> detail ?: "Something went wrong. Try again."
}

fun NameError.message(): String = when (this) {
    NameError.EMPTY -> "Enter your name."
    NameError.TOO_LONG -> "That name is too long."
}

fun PasswordError.message(): String = when (this) {
    PasswordError.EMPTY -> "Enter a password."
    PasswordError.TOO_SHORT ->
        "Use at least ${Validators.MIN_PASSWORD_LENGTH} characters."
    PasswordError.DIGITS_ONLY -> "Use letters as well as numbers."
    PasswordError.TOO_COMMON -> "That password is too easy to guess."
}
