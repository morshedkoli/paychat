package com.paychat.paychat.core.errors

import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.functions.FirebaseFunctionsException
import java.io.IOException

/**
 * Turns a thrown thing into a sentence a person can act on.
 *
 * Firebase exception messages are written for the developer reading a log:
 * they name status codes and rule paths, and showing one to the user is both
 * frightening and useless. Everything unrecognised falls back to the caller's
 * own wording for the operation that failed, which is always more specific
 * than anything that could be written here.
 */
fun Throwable.userMessage(fallback: String): String = when (this) {
    is FirebaseFirestoreException -> when (code) {
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> OFFLINE

        // The rules refused it. Either the app asked for something it is not
        // allowed, or two people acted on the same transaction at once.
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> REFUSED

        FirebaseFirestoreException.Code.NOT_FOUND -> "That is no longer there."
        else -> fallback
    }

    is FirebaseFunctionsException -> when (code) {
        FirebaseFunctionsException.Code.UNAVAILABLE,
        FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> OFFLINE

        FirebaseFunctionsException.Code.PERMISSION_DENIED,
        FirebaseFunctionsException.Code.UNAUTHENTICATED -> REFUSED

        else -> fallback
    }

    is IOException -> OFFLINE

    // An error raised by the app itself with error(...) already reads as a
    // sentence, so it is shown as written.
    else -> message?.takeIf { it.isNotBlank() && it.firstOrNull()?.isUpperCase() == true }
        ?: fallback
}

private const val OFFLINE =
    "You are offline. This is saved on your phone and sent when you reconnect."

private const val REFUSED =
    "The server would not accept that. Someone may have acted on it already."
