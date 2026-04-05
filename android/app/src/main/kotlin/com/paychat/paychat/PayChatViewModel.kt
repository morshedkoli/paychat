package com.paychat.paychat

import android.app.Application
import android.app.Activity
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.FirebaseException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.FirebaseStorage
import com.paychat.paychat.data.ChatRepository
import com.paychat.paychat.data.TransactionRepository
import com.paychat.paychat.model.AuthEntryState
import com.paychat.paychat.model.AuthEntryStep
import com.paychat.paychat.data.UserRepository
import com.paychat.paychat.model.AppMessage
import com.paychat.paychat.model.AppTransaction
import com.paychat.paychat.model.AppUser
import com.paychat.paychat.model.ChatThread
import com.paychat.paychat.model.SessionState
import com.paychat.paychat.model.TransactionType
import java.util.Date
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class PayChatViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val userRepository = UserRepository(
        firestore = firestore,
        storage = FirebaseStorage.getInstance(),
    )
    private val transactionRepository = TransactionRepository()
    private val chatRepository = ChatRepository(firestore = firestore)

    private val _session = MutableStateFlow(SessionState.loading())
    val session: StateFlow<SessionState> = _session.asStateFlow()

    private val _threads = MutableStateFlow<List<ChatThread>>(emptyList())
    val threads: StateFlow<List<ChatThread>> = _threads.asStateFlow()

    private val _transactions = MutableStateFlow<List<AppTransaction>>(emptyList())
    val transactions: StateFlow<List<AppTransaction>> = _transactions.asStateFlow()

    private val _authEntry = MutableStateFlow(AuthEntryState())
    val authEntry: StateFlow<AuthEntryState> = _authEntry.asStateFlow()

    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null

    private var profileJob: Job? = null
    private var threadsJob: Job? = null
    private var transactionsJob: Job? = null

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        handleAuthUser(firebaseAuth.currentUser)
    }

    init {
        auth.useAppLanguage()
        auth.addAuthStateListener(authListener)
        handleAuthUser(auth.currentUser)
    }

    override fun onCleared() {
        auth.removeAuthStateListener(authListener)
        profileJob?.cancel()
        threadsJob?.cancel()
        transactionsJob?.cancel()
        super.onCleared()
    }

    fun retry() {
        handleAuthUser(auth.currentUser)
    }

    fun currentUser(): AppUser? = _session.value.user

    fun thread(threadId: String): ChatThread? {
        val user = currentUser() ?: return null
        return chatRepository.getThreadById(user, threadId)
    }

    fun transaction(transactionId: String): AppTransaction? {
        val userId = currentUser()?.uid ?: return null
        return transactionRepository.getTransactionById(userId, transactionId)
    }

    fun messages(threadId: String): StateFlow<List<AppMessage>> = chatRepository.observeMessages(threadId)

    fun signOut() {
        auth.signOut()
        clearDemoData()
        _session.value = SessionState.unauthenticated()
        resetAuthEntry()
    }

    fun startPhoneNumberVerification(
        activity: Activity,
        countryIso: String,
        countryDialCode: String,
        rawNationalNumber: String,
    ) {
        val nationalNumber = rawNationalNumber.filter(Char::isDigit)
        val phoneNumber = formatE164PhoneNumber(
            countryDialCode = countryDialCode,
            nationalNumber = nationalNumber,
        )

        if (nationalNumber.isEmpty()) {
            _authEntry.value = _authEntry.value.copy(
                errorMessage = "Enter your phone number first.",
            )
            return
        }
        if (phoneNumber == null) {
            _authEntry.value = _authEntry.value.copy(
                errorMessage = "Enter a valid phone number with country code.",
            )
            return
        }

        _authEntry.value = _authEntry.value.copy(
            step = AuthEntryStep.PHONE,
            countryIso = countryIso,
            countryDialCode = countryDialCode,
            nationalNumber = nationalNumber,
            phoneNumberE164 = phoneNumber,
            isSendingCode = true,
            errorMessage = null,
        )

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                _authEntry.value = _authEntry.value.copy(
                    isSendingCode = false,
                    isVerifyingCode = true,
                    errorMessage = null,
                )
                signInWithPhoneCredential(credential)
            }

            override fun onVerificationFailed(exception: FirebaseException) {
                _authEntry.value = _authEntry.value.copy(
                    step = AuthEntryStep.PHONE,
                    isSendingCode = false,
                    isVerifyingCode = false,
                    errorMessage = friendlyPhoneAuthThrowable(exception),
                )
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken,
            ) {
                resendToken = token
                _authEntry.value = _authEntry.value.copy(
                    step = AuthEntryStep.OTP,
                    verificationId = verificationId,
                    otpSentTo = phoneNumber,
                    phoneNumberE164 = phoneNumber,
                    isSendingCode = false,
                    isVerifyingCode = false,
                    errorMessage = null,
                )
            }

            override fun onCodeAutoRetrievalTimeOut(verificationId: String) {
                _authEntry.value = _authEntry.value.copy(
                    verificationId = verificationId,
                    isSendingCode = false,
                )
            }
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()

        if (BuildConfig.DEBUG && isProbablyEmulator()) {
            auth.firebaseAuthSettings.forceRecaptchaFlowForTesting(true)
        }

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun verifyOtp(otpCode: String) {
        val verificationId = _authEntry.value.verificationId
        val code = otpCode.trim()
        if (verificationId.isNullOrBlank()) {
            _authEntry.value = _authEntry.value.copy(
                errorMessage = "Request a fresh OTP code and try again.",
            )
            return
        }
        if (code.length != 6) {
            _authEntry.value = _authEntry.value.copy(
                errorMessage = "Please enter the 6-digit OTP.",
            )
            return
        }

        _authEntry.value = _authEntry.value.copy(
            isVerifyingCode = true,
            errorMessage = null,
        )
        val credential = PhoneAuthProvider.getCredential(verificationId, code)
        signInWithPhoneCredential(credential)
    }

    fun editPhoneNumber() {
        _authEntry.value = _authEntry.value.copy(
            step = AuthEntryStep.PHONE,
            verificationId = null,
            otpSentTo = null,
            phoneNumberE164 = "",
            isSendingCode = false,
            isVerifyingCode = false,
            errorMessage = null,
        )
    }

    fun clearAuthError() {
        _authEntry.value = _authEntry.value.copy(errorMessage = null)
    }

    suspend fun saveProfile(
        fullName: String,
        email: String,
        address: String,
        imageUri: Uri?,
    ): Result<Unit> {
        val user = currentUser() ?: return Result.failure(
            IllegalStateException("No active user profile is available."),
        )

        return try {
            val updated = userRepository.updateProfile(
                uid = user.uid,
                fullName = fullName,
                email = email,
                address = address,
                imageUri = imageUri,
                contentResolver = getApplication<Application>().contentResolver,
            )
            applySession(updated)
            Result.success(Unit)
        } catch (error: Exception) {
            Result.failure(IllegalStateException(error.message ?: "Could not save your profile.", error))
        }
    }

    suspend fun sendMessage(threadId: String, text: String): Result<Unit> {
        val user = currentUser() ?: return Result.failure(
            IllegalStateException("You must be signed in to send a message."),
        )

        return try {
            chatRepository.sendMessage(
                userId = user.uid,
                threadId = threadId,
                senderId = user.uid,
                text = text,
            )
            Result.success(Unit)
        } catch (error: Exception) {
            Result.failure(IllegalStateException(error.message ?: "Could not send the message.", error))
        }
    }

    suspend fun createChatMember(
        memberName: String,
        memberPhone: String,
    ): Result<ChatThread> {
        val user = currentUser() ?: return Result.failure(
            IllegalStateException("You must be signed in to add a chat member."),
        )

        val trimmedName = memberName.trim()
        val trimmedPhone = memberPhone.trim()
        val normalizedPhone = normalizeChatPhoneNumber(trimmedPhone)
        if (trimmedName.isEmpty()) {
            return Result.failure(IllegalStateException("Member name is required."))
        }
        if (trimmedPhone.isEmpty()) {
            return Result.failure(IllegalStateException("Member phone number is required."))
        }
        if (normalizedPhone == null) {
            return Result.failure(
                IllegalStateException("Enter a valid phone number. Use +country code or a valid local number."),
            )
        }

        return try {
            val memberUser = userRepository.findUserByPhone(normalizedPhone)
            if (memberUser?.uid == user.uid || normalizedPhone == user.phoneNumber) {
                return Result.failure(
                    IllegalStateException("Use a different phone number to start a new chat."),
                )
            }
            val thread = chatRepository.createThread(
                currentUser = user,
                memberDisplayName = trimmedName,
                memberPhone = normalizedPhone,
                memberUser = memberUser,
            )
            Result.success(thread)
        } catch (error: Exception) {
            Result.failure(
                IllegalStateException(error.message ?: "Could not create the chat member.", error),
            )
        }
    }

    suspend fun addConversationTransaction(
        threadId: String,
        type: TransactionType,
        amount: Double,
        note: String,
    ): Result<Unit> {
        val user = currentUser() ?: return Result.failure(
            IllegalStateException("You must be signed in to add a transaction."),
        )
        val thread = chatRepository.getThreadById(user, threadId) ?: return Result.failure(
            IllegalStateException("Conversation not found."),
        )

        return try {
            transactionRepository.addTransaction(
                userId = user.uid,
                contactName = thread.title,
                contactPhone = "",
                type = type,
                amount = amount,
                note = note,
            )

            val summary = note.trim().ifBlank { "${type.displayLabel()} $amount" }

            chatRepository.sendTransactionMessage(
                userId = user.uid,
                threadId = threadId,
                senderId = user.uid,
                text = summary,
                amount = amount,
            )
            Result.success(Unit)
        } catch (error: Exception) {
            Result.failure(IllegalStateException(error.message ?: "Could not add the transaction.", error))
        }
    }

    private fun handleAuthUser(firebaseUser: FirebaseUser?) {
        profileJob?.cancel()

        if (firebaseUser == null) {
            clearDemoData()
            _session.value = SessionState.unauthenticated()
            resetAuthEntry()
            return
        }

        _session.value = SessionState.loading()
        profileJob = viewModelScope.launch {
            try {
                val ensured = userRepository.ensureUserProfile(firebaseUser)
                applySession(ensured)
                userRepository.watchUserProfile(firebaseUser.uid).collect { profile ->
                    val resolved = profile ?: userRepository.ensureUserProfile(firebaseUser)
                    applySession(resolved)
                }
            } catch (error: FirebaseFirestoreException) {
                if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    applySession(firebaseUser.toFallbackProfile())
                } else {
                    clearDemoData()
                    _session.value = SessionState.unauthenticated(
                        error.message ?: "Could not load your workspace.",
                    )
                }
            } catch (error: Exception) {
                clearDemoData()
                _session.value = SessionState.unauthenticated(
                    error.message ?: "Could not load your workspace.",
                )
            }
        }
    }

    private fun signInWithPhoneCredential(credential: PhoneAuthCredential) {
        viewModelScope.launch {
            try {
                auth.signInWithCredential(credential).await()
                resetAuthEntry()
            } catch (error: Exception) {
                _authEntry.value = _authEntry.value.copy(
                    isSendingCode = false,
                    isVerifyingCode = false,
                    errorMessage = friendlyPhoneAuthThrowable(error),
                )
            }
        }
    }

    private fun applySession(profile: AppUser) {
        _session.value = when {
            profile.isBlocked -> SessionState.blocked(profile)
            !profile.isProfileComplete -> SessionState.profileIncomplete(profile)
            else -> SessionState.authenticated(profile)
        }
        resetAuthEntry()
        observeDemoData(profile.uid)
    }

    private fun observeDemoData(userId: String) {
        threadsJob?.cancel()
        transactionsJob?.cancel()
        val user = currentUser() ?: return

        threadsJob = viewModelScope.launch {
            chatRepository.observeThreads(user).collect { _threads.value = it }
        }
        transactionsJob = viewModelScope.launch {
            transactionRepository.observeTransactions(userId).collect { _transactions.value = it }
        }
    }

    private fun clearDemoData() {
        threadsJob?.cancel()
        transactionsJob?.cancel()
        _threads.value = emptyList()
        _transactions.value = emptyList()
    }

    private fun resetAuthEntry() {
        resendToken = null
        _authEntry.value = AuthEntryState()
    }

    private fun formatE164PhoneNumber(
        countryDialCode: String,
        nationalNumber: String,
    ): String? {
        val dialDigits = countryDialCode.filter { it.isDigit() }
        val localDigits = nationalNumber.trimStart('0')
        if (dialDigits.isBlank() || localDigits.isBlank()) {
            return null
        }

        val e164 = "+$dialDigits$localDigits"
        return if (e164.length in 8..16) e164 else null
    }

    private fun isProbablyEmulator(): Boolean {
        return Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
            Build.FINGERPRINT.contains("emulator", ignoreCase = true) ||
            Build.MODEL.contains("Emulator", ignoreCase = true) ||
            Build.MODEL.contains("Android SDK built for", ignoreCase = true) ||
            Build.MANUFACTURER.contains("Genymotion", ignoreCase = true) ||
            Build.BRAND.startsWith("generic", ignoreCase = true) && Build.DEVICE.startsWith("generic", ignoreCase = true) ||
            Build.PRODUCT.contains("sdk", ignoreCase = true) ||
            Build.HARDWARE.contains("goldfish", ignoreCase = true) ||
            Build.HARDWARE.contains("ranchu", ignoreCase = true)
    }

    private fun normalizeChatPhoneNumber(rawPhoneNumber: String): String? {
        val trimmed = rawPhoneNumber.trim()
        if (trimmed.isBlank()) return null

        if (trimmed.startsWith("+")) {
            val digits = trimmed.drop(1).filter(Char::isDigit)
            return if (digits.length in 8..15) "+$digits" else null
        }

        val digits = trimmed.filter(Char::isDigit)
        if (digits.isBlank()) return null

        return when {
            digits.startsWith("880") && digits.length in 10..13 -> "+$digits"
            digits.startsWith("0") && digits.length == 11 -> "+880${digits.drop(1)}"
            digits.length == 10 -> "+880$digits"
            digits.length in 8..15 -> "+$digits"
            else -> null
        }
    }

    private fun friendlyPhoneAuthThrowable(error: Exception): String {
        val exception = error as? FirebaseAuthException
        val rawMessage = error.message.orEmpty()
        if (
            rawMessage.contains("valid app identifier", ignoreCase = true) ||
            rawMessage.contains("play integrity", ignoreCase = true)
        ) {
            return "Phone verification could not confirm this Android app. Make sure the app package name and SHA-1/SHA-256 fingerprints registered in Firebase match this installed build, then try again."
        }
        if (rawMessage.contains("region enabled by the app developer", ignoreCase = true)) {
            return "SMS is blocked for this country/region in your Firebase Auth settings. Enable the region in Firebase Console > Authentication > Settings > SMS region policy."
        }
        if (rawMessage.contains("sign-in provider is disabled", ignoreCase = true)) {
            return "Phone Authentication is disabled in your Firebase project. Enable Phone in Firebase Console > Authentication > Sign-in method."
        }
        return if (exception != null) {
            friendlyPhoneAuthMessage(exception)
        } else {
            error.message ?: "Phone verification failed. Please try again."
        }
    }

    private fun friendlyPhoneAuthMessage(exception: FirebaseAuthException): String {
        return when (exception.errorCode.lowercase()) {
            "invalid-phone-number" ->
                "The phone number format is invalid. Use the full number with country code."
            "missing-phone-number" ->
                "Enter your phone number first."
            "quota-exceeded" ->
                "OTP quota exceeded for now. Please wait a bit and try again."
            "too-many-requests" ->
                "Too many verification attempts. Please wait and try again."
            "captcha-check-failed" ->
                "Security verification failed. Please try again."
            "session-expired" ->
                "The OTP session expired. Request a new code."
            "invalid-verification-code" ->
                "The OTP code is invalid. Please try again."
            "invalid-verification-id" ->
                "The verification session is invalid. Request a new OTP."
            "network-request-failed" ->
                "Network error while contacting Firebase. Check your internet connection and try again."
            "operation-not-allowed" ->
                "Phone sign-in is not enabled in Firebase Authentication. Enable Phone in Firebase Console > Authentication > Sign-in method."
            else -> exception.message ?: "Phone verification failed. Please try again."
        }
    }
}

private fun FirebaseUser.toFallbackProfile(): AppUser {
    val now = Date()
    val displayName = displayName?.trim().orEmpty()
    val emailAddress = email?.trim().orEmpty()
    val fallbackName = displayName.ifBlank {
        emailAddress.substringBefore('@', "PayChat User")
    }

    return AppUser(
        uid = uid,
        phoneNumber = phoneNumber.orEmpty(),
        fullName = fallbackName,
        photoUrl = photoUrl?.toString().orEmpty(),
        email = emailAddress,
        address = "",
        createdAt = now,
        updatedAt = now,
        lastLoginAt = now,
        isProfileComplete = true,
        isBlocked = false,
        role = "user",
        deviceTokens = emptyList(),
    )
}

private fun TransactionType.displayLabel(): String {
    return when (this) {
        TransactionType.SENT -> "Sent"
        TransactionType.RECEIVED -> "Received"
        TransactionType.PAID -> "Paid"
        TransactionType.DUE -> "Due"
    }
}
