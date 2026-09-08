package com.paychat.paychat.feature.auth.phone

import com.paychat.paychat.data.auth.AuthError
import com.paychat.paychat.data.auth.AuthException
import com.paychat.paychat.feature.auth.message

/**
 * What the phone entry screen does next, once the lookup has answered.
 *
 * A failed lookup is its own outcome rather than a default to either branch.
 * Guessing "unregistered" would text a code to somebody who already has an
 * account, and guessing "registered" would ask for the password of an account
 * that does not exist.
 */
sealed interface PhoneLookupOutcome {
    /** The number has an account: ask for the password. */
    data object Registered : PhoneLookupOutcome

    /** The number has no account: offer to create one. */
    data object Unregistered : PhoneLookupOutcome

    /** Nothing was learned. [message] is ready to show under the field. */
    data class Failed(val message: String) : PhoneLookupOutcome
}

fun phoneLookupOutcome(result: Result<Boolean>): PhoneLookupOutcome = result.fold(
    onSuccess = { exists ->
        if (exists) PhoneLookupOutcome.Registered else PhoneLookupOutcome.Unregistered
    },
    onFailure = { throwable ->
        val error = (throwable as? AuthException)?.error
            ?: AuthError.Unknown(throwable.message)
        PhoneLookupOutcome.Failed(error.message())
    },
)
