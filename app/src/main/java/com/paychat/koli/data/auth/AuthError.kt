package com.paychat.koli.data.auth

/**
 * Every way authentication can fail, in terms the UI can turn into a message.
 * Firebase exception types never leave the data layer.
 */
sealed interface AuthError {
    data object InvalidPhoneNumber : AuthError
    data object PhoneAlreadyRegistered : AuthError
    data object PhoneNotRegistered : AuthError
    data object InvalidOtp : AuthError
    data object OtpExpired : AuthError
    data object WrongPassword : AuthError
    data object WeakPassword : AuthError
    data object TooManyRequests : AuthError
    data object Network : AuthError

    /** The account was signed in on another device. */
    data object SessionReplaced : AuthError

    data class Unknown(val detail: String?) : AuthError
}
