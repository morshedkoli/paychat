# Phone-First Authentication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the two separate signed-out front doors with one phone-number entry point that routes a known number to a password step and an unknown number, after confirmation, to registration and OTP.

**Architecture:** A new `phone` start destination asks for the number and calls a new unauthenticated `phoneLookup` Cloud Function that returns `{ exists }` and nothing else. `LOGIN` and `REGISTER` become `login/{phone}` and `register/{phone}` steps that take the number from the route instead of asking for it. Everything from `PendingRegistration` and the OTP screen down is untouched.

**Tech Stack:** Kotlin, Jetpack Compose, Navigation-Compose, Hilt, Firebase Auth / Firestore / Functions (callables, region `asia-south1`), TypeScript Cloud Functions (Node 22), JUnit for unit tests, `@firebase/rules-unit-testing` for rules tests.

**Spec:** `docs/superpowers/specs/2026-09-08-phone-first-auth-design.md`

## Global Constraints

- Every phone number in the system is E.164, produced only by `PhoneNumbers.toE164`. Default region is `"BD"`.
- Phone numbers in route strings carry no leading `+` (a raw `+` is ambiguous in a URI path segment). Strip it when building a route, restore it when reading one — as `Routes.otp` and `OtpViewModel` already do.
- No Firebase exception type may reach a ViewModel. The data layer maps everything to `AuthError` through `runCatchingAuth`.
- Reuse the existing `AuthError.TooManyRequests` ("Too many attempts. Try again later.") for a rate-limited lookup. Do **not** add a new error case — the spec's `TooManyAttempts` name predates checking `AuthError.kt`; `TooManyRequests` already exists and already has a message.
- Every Cloud Function handler uses `region: "asia-south1"` and `enforceAppCheck: false`, matching every existing handler. The client's `FirebaseModule.FUNCTIONS_REGION` is already `asia-south1`.
- `firebase/firestore.rules` must not be loosened. `phoneIndex` stays `allow read: if signedIn()`.
- The lookup is a UX hint and never a security decision. `completeRegistration` and `signIn` keep failing closed.
- Unit tests: `./gradlew testDebugUnitTest`. Rules tests: `npm test` in `firebase/tests`. Build and install: `./gradlew installDebug`.
- `adb` on this machine is `D:/android-sdk/platform-tools/adb.exe`. Under Git Bash, prefix `adb shell`/`adb pull` commands that contain device paths with `MSYS_NO_PATHCONV=1`.

---

## File Structure

**Create:**
- `app/src/main/java/com/paychat/paychat/feature/auth/phone/PhoneLookupOutcome.kt` — pure mapping from a lookup result to the branch the entry screen takes.
- `app/src/main/java/com/paychat/paychat/feature/auth/phone/PhoneEntryViewModel.kt` — the entry screen's state and the lookup call.
- `app/src/main/java/com/paychat/paychat/feature/auth/phone/PhoneEntryScreen.kt` — the number field, Continue, and the confirm dialog.
- `app/src/test/java/com/paychat/paychat/feature/auth/phone/PhoneLookupOutcomeTest.kt`
- `firebase/functions/src/phoneLookup.ts` — the callable.

**Modify:**
- `app/src/main/java/com/paychat/paychat/core/validation/Validators.kt` — add `passwordsMatch`.
- `app/src/test/java/com/paychat/paychat/core/validation/ValidatorsTest.kt` — cover it.
- `app/src/main/java/com/paychat/paychat/data/auth/AuthRepository.kt` — add `phoneExists`.
- `app/src/main/java/com/paychat/paychat/ui/nav/Routes.kt` — `PHONE`, parameterised `LOGIN`/`REGISTER`, builders, `AUTH_ROUTES`.
- `app/src/main/java/com/paychat/paychat/ui/nav/PayChatNavHost.kt` — new destination, new signed-out start, rewired callbacks.
- `app/src/main/java/com/paychat/paychat/feature/auth/login/LoginViewModel.kt` and `LoginScreen.kt` — password-only step.
- `app/src/main/java/com/paychat/paychat/feature/auth/register/RegisterViewModel.kt` and `RegisterScreen.kt` — name, password, confirm password.
- `firebase/functions/src/index.ts` — export the callable.
- `firebase/tests/rules.test.mjs` — assert `phoneIndex` stays closed to unauthenticated reads.

---

### Task 1: The lookup outcome mapper

The one piece of entry-screen logic worth testing without a ViewModel: given what came back, which branch does the screen take?

**Files:**
- Create: `app/src/main/java/com/paychat/paychat/feature/auth/phone/PhoneLookupOutcome.kt`
- Test: `app/src/test/java/com/paychat/paychat/feature/auth/phone/PhoneLookupOutcomeTest.kt`

**Interfaces:**
- Consumes: `com.paychat.paychat.data.auth.AuthError`, `com.paychat.paychat.feature.auth.message()`
- Produces: `sealed interface PhoneLookupOutcome` with `PhoneLookupOutcome.Registered`, `PhoneLookupOutcome.Unregistered`, `PhoneLookupOutcome.Failed(val message: String)`; and `fun phoneLookupOutcome(result: Result<Boolean>): PhoneLookupOutcome`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/paychat/paychat/feature/auth/phone/PhoneLookupOutcomeTest.kt`:

```kotlin
package com.paychat.paychat.feature.auth.phone

import com.paychat.paychat.data.auth.AuthError
import com.paychat.paychat.data.auth.AuthException
import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneLookupOutcomeTest {

    @Test
    fun `a registered number goes to the password step`() {
        assertEquals(
            PhoneLookupOutcome.Registered,
            phoneLookupOutcome(Result.success(true)),
        )
    }

    @Test
    fun `an unregistered number offers registration`() {
        assertEquals(
            PhoneLookupOutcome.Unregistered,
            phoneLookupOutcome(Result.success(false)),
        )
    }

    @Test
    fun `a rate limited lookup reports too many attempts`() {
        val outcome = phoneLookupOutcome(
            Result.failure(AuthException(AuthError.TooManyRequests))
        )
        assertEquals(
            PhoneLookupOutcome.Failed("Too many attempts. Try again later."),
            outcome,
        )
    }

    @Test
    fun `a lookup with no connection says so`() {
        val outcome = phoneLookupOutcome(Result.failure(AuthException(AuthError.Network)))
        assertEquals(
            PhoneLookupOutcome.Failed("No connection. Check your internet and try again."),
            outcome,
        )
    }

    @Test
    fun `a failure that is not an AuthException still produces a message`() {
        val outcome = phoneLookupOutcome(Result.failure(IllegalStateException("boom")))
        assertEquals(PhoneLookupOutcome.Failed("boom"), outcome)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "*PhoneLookupOutcomeTest*"`
Expected: FAIL — compilation error, `PhoneLookupOutcome` and `phoneLookupOutcome` are unresolved.

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/java/com/paychat/paychat/feature/auth/phone/PhoneLookupOutcome.kt`:

```kotlin
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "*PhoneLookupOutcomeTest*"`
Expected: PASS, 5 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/paychat/paychat/feature/auth/phone/PhoneLookupOutcome.kt app/src/test/java/com/paychat/paychat/feature/auth/phone/PhoneLookupOutcomeTest.kt
git commit -m "Decide the phone entry branch in one testable place"
```

---

### Task 2: Confirm-password rule

**Files:**
- Modify: `app/src/main/java/com/paychat/paychat/core/validation/Validators.kt`
- Test: `app/src/test/java/com/paychat/paychat/core/validation/ValidatorsTest.kt`

**Interfaces:**
- Produces: `Validators.passwordsMatch(password: String, confirmation: String): Boolean`

- [ ] **Step 1: Write the failing test**

Append inside the existing `ValidatorsTest` class body in `app/src/test/java/com/paychat/paychat/core/validation/ValidatorsTest.kt`:

```kotlin
    @Test
    fun `matching passwords are accepted`() {
        assertTrue(Validators.passwordsMatch("correct horse", "correct horse"))
    }

    @Test
    fun `a mismatched confirmation is rejected`() {
        assertFalse(Validators.passwordsMatch("correct horse", "correct horst"))
    }

    @Test
    fun `an empty confirmation is rejected`() {
        assertFalse(Validators.passwordsMatch("correct horse", ""))
    }
```

If `assertTrue` or `assertFalse` is not already imported in that file, add
`import org.junit.Assert.assertTrue` and `import org.junit.Assert.assertFalse`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "*ValidatorsTest*"`
Expected: FAIL — compilation error, `passwordsMatch` is unresolved.

- [ ] **Step 3: Write minimal implementation**

In `Validators.kt`, after `validatePassword`, add:

```kotlin
    /**
     * Whether the confirmation field matches. Kept next to the password rules
     * so the registration screen has one place to ask about passwords.
     */
    fun passwordsMatch(password: String, confirmation: String): Boolean =
        confirmation.isNotEmpty() && password == confirmation
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "*ValidatorsTest*"`
Expected: PASS, including the three new tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/paychat/paychat/core/validation/Validators.kt app/src/test/java/com/paychat/paychat/core/validation/ValidatorsTest.kt
git commit -m "Add the confirm-password rule to Validators"
```

---

### Task 3: The `phoneLookup` callable

**Files:**
- Create: `firebase/functions/src/phoneLookup.ts`
- Modify: `firebase/functions/src/index.ts`
- Modify: `firebase/functions/src/limits.ts` (documentation only — see Step 3)
- Test: `firebase/tests/rules.test.mjs`

**Interfaces:**
- Consumes: `enforceRateLimit` from `./limits`.
- Produces: callable named `phoneLookup`, input `{ phone: string }`, output `{ exists: boolean }`.

- [ ] **Step 1: Write the failing rules test**

The callable's whole reason to exist is that clients cannot read `phoneIndex`
while signed out. Pin that down first. In `firebase/tests/rules.test.mjs`, add
to the `describe("identity", ...)` block:

```javascript
  it("a signed out client cannot read the phone index", async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "phoneIndex", "+8801700000001"), { uid: ALICE });
    });

    await assertFails(
      getDoc(doc(testEnv.unauthenticatedContext().firestore(), "phoneIndex", "+8801700000001"))
    );
  });
```

If `getDoc` is not already imported at the top of that file, add it to the
existing `firebase/firestore` import list.

- [ ] **Step 2: Run the rules test**

Run: `cd firebase/tests && npm test`
Expected: PASS. This test documents existing behaviour rather than driving new
behaviour — if it *fails*, the rules have already been loosened and that must
be fixed before going further.

- [ ] **Step 3: Write the callable**

Create `firebase/functions/src/phoneLookup.ts`:

```typescript
import { getFirestore } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { enforceRateLimit } from "./limits";

/** E.164: a plus, a non-zero country digit, then 7 to 14 more digits. */
const E164 = /^\+[1-9]\d{7,14}$/;

const HOUR_MS = 60 * 60 * 1000;

/**
 * Answers whether a phone number already has an account.
 *
 * Deliberately unauthenticated: the whole point is to answer this before
 * anybody can sign in, so that a returning user is asked for a password
 * instead of an SMS, and a new user is never shown a wrong-password error.
 *
 * `phoneIndex` itself stays readable only to signed in clients, because making
 * it public would let anyone enumerate which numbers use PayChat. This handler
 * reads it with admin credentials and returns one bit and nothing else: no
 * name, no uid, no timestamps.
 *
 * The one bit is still worth rationing, so it is rated twice — per caller,
 * which bounds bulk enumeration from one source, and per number, which bounds
 * attention on a single number even from many sources.
 */
export const phoneLookup = onCall(
  {
    region: "asia-south1",
    enforceAppCheck: false, // Turn on once App Check is configured.
  },
  async (request) => {
    const phone = String(request.data?.phone ?? "");
    if (!E164.test(phone)) {
      throw new HttpsError("invalid-argument", "That is not a phone number.");
    }

    const ip = request.rawRequest.ip ?? "unknown";
    await enforceRateLimit({
      uid: `ip:${ip}`,
      action: "phoneLookup",
      limit: 30,
      windowMs: HOUR_MS,
      message: "Too many attempts. Try again in a while.",
    });
    await enforceRateLimit({
      uid: `phone:${phone}`,
      action: "phoneLookup",
      limit: 6,
      windowMs: HOUR_MS,
      message: "Too many attempts. Try again in a while.",
    });

    const snapshot = await getFirestore().collection("phoneIndex").doc(phone).get();
    return { exists: snapshot.exists };
  }
);
```

In `firebase/functions/src/index.ts`, add the export alongside the others:

```typescript
export { phoneLookup } from "./phoneLookup";
```

In `firebase/functions/src/limits.ts`, the `uid` parameter is now also used for
non-uid counter keys. Amend its doc comment so the next reader is not misled —
replace the line `* uid: string;`'s surrounding docblock sentence about uids by
adding this sentence to the end of the existing block comment above
`enforceRateLimit`:

```
 * The `uid` is just the counter key. Signed out callers have no uid, so
 * `phoneLookup` passes `ip:<address>` and `phone:<e164>` instead.
```

- [ ] **Step 4: Verify it compiles**

Run: `cd firebase/functions && npm run build`
Expected: `tsc` exits 0, `lib/phoneLookup.js` exists.

- [ ] **Step 5: Commit**

```bash
git add firebase/functions/src/phoneLookup.ts firebase/functions/src/index.ts firebase/functions/src/limits.ts firebase/tests/rules.test.mjs
git commit -m "Answer whether a number is registered, without opening the index"
```

Note for the executor: the function still has to be deployed
(`cd firebase/functions && npm run deploy`) before the app can use it. Deploying
is a live change to the user's Firebase project — ask before running it, and do
not run it unprompted.

---

### Task 4: `AuthRepository.phoneExists`

**Files:**
- Modify: `app/src/main/java/com/paychat/paychat/data/auth/AuthRepository.kt`

**Interfaces:**
- Consumes: `com.google.firebase.functions.FirebaseFunctions` (already provided by `FirebaseModule`, pinned to `asia-south1`).
- Produces: `suspend fun AuthRepository.phoneExists(phoneE164: String): Result<Boolean>`

- [ ] **Step 1: Add the dependency and the method**

In the constructor of `AuthRepository`, add the functions client:

```kotlin
    private val functions: FirebaseFunctions,
```

with `import com.google.firebase.functions.FirebaseFunctions` and
`import com.google.firebase.functions.FirebaseFunctionsException` at the top.

Add the method above `signOut`:

```kotlin
    /**
     * Whether [phoneE164] already has an account.
     *
     * `phoneIndex` is not readable while signed out — making it public would
     * leak which numbers use the app — so the question goes to a callable that
     * reads it with admin credentials and answers with one bit.
     *
     * This is a hint for the signed out flow, never a decision: whichever
     * branch it sends someone down, [completeRegistration] still refuses a
     * number that is taken and [signIn] still refuses one that is not.
     */
    suspend fun phoneExists(phoneE164: String): Result<Boolean> = runCatchingAuth {
        val response = functions.getHttpsCallable(LOOKUP_FUNCTION)
            .call(mapOf("phone" to phoneE164))
            .await()

        @Suppress("UNCHECKED_CAST")
        val data = response.data as? Map<String, Any?>
            ?: error("phoneLookup returned no data")
        data["exists"] as? Boolean ?: error("phoneLookup returned no exists flag")
    }
```

Add the companion object at the end of the class body:

```kotlin
    private companion object {
        const val LOOKUP_FUNCTION = "phoneLookup"
    }
```

- [ ] **Step 2: Map the callable's rate-limit error**

`runCatchingAuth` currently ends at a generic `Exception` catch, which would
turn a rate limit into `Unknown`. Add a case for it immediately **before** the
`catch (e: IOException)` clause in `runCatchingAuth`:

```kotlin
} catch (e: FirebaseFunctionsException) {
    // A callable reports "too many" as RESOURCE_EXHAUSTED; everything else it
    // can fail with is either a bug or the network.
    val error = when (e.code) {
        FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> AuthError.TooManyRequests
        FirebaseFunctionsException.Code.UNAVAILABLE,
        FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> AuthError.Network
        else -> AuthError.Unknown(e.message)
    }
    Result.failure(AuthException(error))
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run the unit tests**

Run: `./gradlew testDebugUnitTest`
Expected: PASS — nothing here changes existing behaviour.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/paychat/paychat/data/auth/AuthRepository.kt
git commit -m "Ask the server whether a number is already registered"
```

---

### Task 5: Routes for the phone-first flow

**Files:**
- Modify: `app/src/main/java/com/paychat/paychat/ui/nav/Routes.kt`

**Interfaces:**
- Produces: `Routes.PHONE`, `Routes.LOGIN` = `"login/{phone}"`, `Routes.REGISTER` = `"register/{phone}"`, `Routes.login(phoneE164)`, `Routes.register(phoneE164)`; `Routes.AUTH_ROUTES` containing all four auth routes.

- [ ] **Step 1: Change the route constants**

Replace the three auth constants at the top of `Routes` with:

```kotlin
    const val SPLASH = "splash"
    const val PHONE = "phone"
    const val REGISTER = "register/{phone}"
    const val OTP = "otp/{phone}/{purpose}"
    const val LOGIN = "login/{phone}"
```

- [ ] **Step 2: Add the builders**

Beside the existing `otp` builder, whose comment about the dropped `+` now
covers all three, add:

```kotlin
    fun login(phoneE164: String) = "login/${phoneE164.removePrefix("+")}"

    fun register(phoneE164: String) = "register/${phoneE164.removePrefix("+")}"
```

- [ ] **Step 3: Widen the signed-out set**

```kotlin
    /** Destinations that make up the signed out flow. */
    val AUTH_ROUTES = setOf(PHONE, REGISTER, LOGIN, OTP)
```

- [ ] **Step 4: Verify the state of the build**

Run: `./gradlew compileDebugKotlin`
Expected: FAIL, and only in `PayChatNavHost.kt`, where `Routes.LOGIN` and
`Routes.REGISTER` are still used as complete routes. Task 8 fixes those call
sites. Read the errors and confirm they are all in that one file; anything
else means a route was used somewhere this plan has not accounted for.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/paychat/paychat/ui/nav/Routes.kt
git commit -m "Carry the phone number into the login and register routes"
```

---

### Task 6: The password step

**Files:**
- Modify: `app/src/main/java/com/paychat/paychat/feature/auth/login/LoginViewModel.kt`
- Modify: `app/src/main/java/com/paychat/paychat/feature/auth/login/LoginScreen.kt`

**Interfaces:**
- Consumes: `Routes.LOGIN`, `NavArgs.PHONE`, `PhoneNumbers.formatForDisplay`.
- Produces: `LoginUiState(phoneE164, phoneDisplay, password, error, submitting, signedIn)`; `LoginViewModel.startPasswordReset(newPassword: String): String`; `LoginScreen(onSignedIn: () -> Unit, onChangeNumber: () -> Unit, onResetRequested: (String) -> Unit, viewModel: LoginViewModel)`.

- [ ] **Step 1: Rewrite the ViewModel**

Replace the state and the class body of `LoginViewModel.kt` with:

```kotlin
data class LoginUiState(
    val phoneE164: String = "",
    val phoneDisplay: String = "",
    val password: String = "",
    val error: String? = null,
    val submitting: Boolean = false,
    val signedIn: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    phoneNumbers: PhoneNumbers,
    private val authRepository: AuthRepository,
    private val pending: PendingRegistration,
) : ViewModel() {

    // The route carries the number without its leading plus. See Routes.login.
    private val phoneE164 = "+" + savedStateHandle.get<String>(NavArgs.PHONE).orEmpty()

    private val _state = MutableStateFlow(
        LoginUiState(
            phoneE164 = phoneE164,
            phoneDisplay = phoneNumbers.formatForDisplay(phoneE164),
        )
    )
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onPasswordChange(value: String) =
        _state.update { it.copy(password = value, error = null) }

    fun submit() {
        val current = _state.value
        if (current.submitting) return

        if (current.password.isEmpty()) {
            _state.update { it.copy(error = "Enter your password.") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }
            authRepository.signIn(phoneE164, current.password).fold(
                onSuccess = { _state.update { it.copy(submitting = false, signedIn = true) } },
                onFailure = { throwable ->
                    val error = (throwable as? AuthException)?.error
                        ?: AuthError.Unknown(throwable.message)
                    _state.update { it.copy(submitting = false, error = error.message()) }
                },
            )
        }
    }

    /**
     * Password reset reuses the OTP screen, which reads the new password from
     * [PendingRegistration]. The name is irrelevant here and is left blank.
     *
     * @return the E.164 number to verify
     */
    fun startPasswordReset(newPassword: String): String {
        pending.put(phoneE164 = phoneE164, name = "", password = newPassword)
        return phoneE164
    }
}
```

Imports: add `androidx.lifecycle.SavedStateHandle` and
`com.paychat.paychat.ui.nav.NavArgs`. `PhoneNumbers` stays imported but is no
longer a `private val` — it is only used in the initialiser.

- [ ] **Step 2: Rewrite the screen's signature and header**

In `LoginScreen.kt`, change the function signature to:

```kotlin
@Composable
fun LoginScreen(
    onSignedIn: () -> Unit,
    onChangeNumber: () -> Unit,
    onResetRequested: (String) -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
```

`onRegisterInstead` is gone: from the password step, the way to registration is
back through the number.

- [ ] **Step 3: Replace the phone field with the number and a Change action**

Inside the card `Column`, replace the `PhoneField(...)` call with:

```kotlin
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        state.phoneDisplay,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(
                                        onClick = onChangeNumber,
                                        enabled = !state.submitting,
                                    ) { Text("Change") }
                                }
```

Imports: add `androidx.compose.foundation.layout.Row` and
`androidx.compose.ui.Alignment` if absent; remove the `PhoneField` import.

- [ ] **Step 4: Fix the reset dialog and the footer**

The reset callback no longer returns null, so simplify it:

```kotlin
    if (showReset) {
        ResetPasswordDialog(
            onDismiss = { showReset = false },
            onConfirm = { newPassword ->
                showReset = false
                onResetRequested(viewModel.startPasswordReset(newPassword))
            },
        )
    }
```

Replace the `FooterLink(...)` block at the bottom of the column with:

```kotlin
                StaggeredEntrance(delayMillis = 280, modifier = Modifier.fillMaxWidth()) {
                    FooterLink(
                        lead = "Not your number?",
                        action = "Use another",
                        onClick = onChangeNumber,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
```

- [ ] **Step 5: Verify the state of the build**

Run: `./gradlew compileDebugKotlin`
Expected: FAIL only in `PayChatNavHost.kt` (routes from Task 5, and the changed
`LoginScreen` parameters). Task 8 fixes it.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/paychat/paychat/feature/auth/login/
git commit -m "Make login the password step of a number-first flow"
```

---

### Task 7: The registration details step

**Files:**
- Modify: `app/src/main/java/com/paychat/paychat/feature/auth/register/RegisterViewModel.kt`
- Modify: `app/src/main/java/com/paychat/paychat/feature/auth/register/RegisterScreen.kt`

**Interfaces:**
- Consumes: `Routes.REGISTER`, `NavArgs.PHONE`, `PhoneNumbers.formatForDisplay`, `Validators.passwordsMatch` (Task 2).
- Produces: `RegisterUiState(phoneE164, phoneDisplay, name, password, confirmPassword, nameError, passwordError, confirmPasswordError, submitting, proceedToOtp)`; `RegisterScreen(onOtpRequired: (String) -> Unit, onChangeNumber: () -> Unit, viewModel: RegisterViewModel)`.

- [ ] **Step 1: Rewrite the ViewModel**

Replace the state and class body of `RegisterViewModel.kt` with:

```kotlin
data class RegisterUiState(
    val phoneE164: String = "",
    val phoneDisplay: String = "",
    val name: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val nameError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val submitting: Boolean = false,
    /** Set when the form is valid and the OTP step should open for this number. */
    val proceedToOtp: String? = null,
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    phoneNumbers: PhoneNumbers,
    private val pending: PendingRegistration,
) : ViewModel() {

    // The route carries the number without its leading plus. See Routes.register.
    private val phoneE164 = "+" + savedStateHandle.get<String>(NavArgs.PHONE).orEmpty()

    private val _state = MutableStateFlow(
        RegisterUiState(
            phoneE164 = phoneE164,
            phoneDisplay = phoneNumbers.formatForDisplay(phoneE164),
        )
    )
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    fun onNameChange(value: String) = _state.update { it.copy(name = value, nameError = null) }

    fun onPasswordChange(value: String) = _state.update {
        it.copy(password = value, passwordError = null, confirmPasswordError = null)
    }

    fun onConfirmPasswordChange(value: String) = _state.update {
        it.copy(confirmPassword = value, confirmPasswordError = null)
    }

    /**
     * Validates the form and hands the details to [PendingRegistration]. The
     * account itself is not created until the OTP is verified, so an
     * unverified number never produces an account.
     */
    fun submit() {
        val current = _state.value
        if (current.submitting) return

        val nameError = Validators.validateName(current.name)?.message()
        val passwordError = Validators.validatePassword(current.password)?.message()
        val confirmError =
            if (passwordError == null &&
                !Validators.passwordsMatch(current.password, current.confirmPassword)
            ) "Passwords do not match." else null

        if (nameError != null || passwordError != null || confirmError != null) {
            _state.update {
                it.copy(
                    nameError = nameError,
                    passwordError = passwordError,
                    confirmPasswordError = confirmError,
                )
            }
            return
        }

        pending.put(
            phoneE164 = phoneE164,
            name = current.name.trim(),
            password = current.password,
        )
        _state.update { it.copy(proceedToOtp = phoneE164) }
    }

    fun onNavigated() = _state.update { it.copy(proceedToOtp = null) }
}
```

Imports: add `androidx.lifecycle.SavedStateHandle` and
`com.paychat.paychat.ui.nav.NavArgs`. `viewModelScope`, `launch` and
`PhoneNumbers`-as-a-field are no longer needed — `submit` has nothing to
suspend on now, so drop the `viewModelScope.launch` wrapper and its imports if
they become unused.

- [ ] **Step 2: Rewrite the screen's signature**

```kotlin
@Composable
fun RegisterScreen(
    onOtpRequired: (phoneE164: String) -> Unit,
    onChangeNumber: () -> Unit,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
```

- [ ] **Step 3: Replace the phone field, add the confirmation**

In the card `Column`, replace the `PhoneField(...)` call with the same
number-and-Change row as the password step:

```kotlin
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        state.phoneDisplay,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(
                                        onClick = onChangeNumber,
                                        enabled = !state.submitting,
                                    ) { Text("Change") }
                                }
```

Change the password field's IME action to `ImeAction.Next` and add the
confirmation field directly after it:

```kotlin
                                PasswordField(
                                    value = state.password,
                                    onValueChange = viewModel::onPasswordChange,
                                    error = state.passwordError,
                                    enabled = !state.submitting,
                                    imeAction = ImeAction.Next,
                                )
                                PasswordField(
                                    value = state.confirmPassword,
                                    onValueChange = viewModel::onConfirmPasswordChange,
                                    label = "Confirm password",
                                    error = state.confirmPasswordError,
                                    enabled = !state.submitting,
                                    imeAction = ImeAction.Done,
                                )
```

Imports: add `androidx.compose.foundation.layout.Row` and
`androidx.compose.ui.Alignment` if absent; remove the `PhoneField` import.

- [ ] **Step 4: Fix the header and footer**

The number is already known here, so the subtitle should not explain why it is
being asked for. Change the `AuthHero` call to:

```kotlin
                    AuthHero(
                        headline = "Let's get you set up",
                        subtitle = "Pick a name people will recognise, and a password for this account.",
                    )
```

Replace the `FooterLink(...)` block with:

```kotlin
                StaggeredEntrance(delayMillis = 280, modifier = Modifier.fillMaxWidth()) {
                    FooterLink(
                        lead = "Wrong number?",
                        action = "Change it",
                        onClick = onChangeNumber,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
```

- [ ] **Step 5: Verify the state of the build**

Run: `./gradlew compileDebugKotlin`
Expected: FAIL only in `PayChatNavHost.kt`. Task 8 fixes it.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/paychat/paychat/feature/auth/register/
git commit -m "Ask only for the details registration still needs"
```

---

### Task 8: The phone entry screen, and wiring the graph

This is the task that makes the tree compile again, so it carries both the new
screen and every call site the earlier tasks broke.

**Files:**
- Create: `app/src/main/java/com/paychat/paychat/feature/auth/phone/PhoneEntryViewModel.kt`
- Create: `app/src/main/java/com/paychat/paychat/feature/auth/phone/PhoneEntryScreen.kt`
- Modify: `app/src/main/java/com/paychat/paychat/ui/nav/PayChatNavHost.kt`

**Interfaces:**
- Consumes: `PhoneLookupOutcome` and `phoneLookupOutcome` (Task 1), `AuthRepository.phoneExists` (Task 4), `Routes.PHONE`/`login`/`register` (Task 5), the `LoginScreen` and `RegisterScreen` signatures (Tasks 6 and 7).
- Produces: `PhoneEntryScreen(onKnownNumber: (String) -> Unit, onNewNumber: (String) -> Unit, viewModel: PhoneEntryViewModel)`.

- [ ] **Step 1: Write the ViewModel**

Create `app/src/main/java/com/paychat/paychat/feature/auth/phone/PhoneEntryViewModel.kt`:

```kotlin
package com.paychat.paychat.feature.auth.phone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paychat.paychat.core.phone.PhoneNumbers
import com.paychat.paychat.data.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PhoneEntryUiState(
    val phone: String = "",
    val phoneError: String? = null,
    val checking: Boolean = false,
    /** Set when the number is known and the password step should open. */
    val knownNumber: String? = null,
    /** Set when the number is unknown, to ask before starting registration. */
    val offerRegistration: String? = null,
    /** The same number formatted for the confirmation dialog to show. */
    val offerRegistrationDisplay: String? = null,
)

@HiltViewModel
class PhoneEntryViewModel @Inject constructor(
    private val phoneNumbers: PhoneNumbers,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PhoneEntryUiState())
    val state: StateFlow<PhoneEntryUiState> = _state.asStateFlow()

    fun onPhoneChange(value: String) =
        _state.update { it.copy(phone = value, phoneError = null) }

    fun submit() {
        val current = _state.value
        if (current.checking) return

        val e164 = phoneNumbers.toE164(current.phone)
        if (e164 == null) {
            _state.update { it.copy(phoneError = "Enter a valid phone number.") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(checking = true, phoneError = null) }
            when (val outcome = phoneLookupOutcome(authRepository.phoneExists(e164))) {
                PhoneLookupOutcome.Registered ->
                    _state.update { it.copy(checking = false, knownNumber = e164) }
                PhoneLookupOutcome.Unregistered ->
                    _state.update {
                        it.copy(
                            checking = false,
                            offerRegistration = e164,
                            offerRegistrationDisplay = phoneNumbers.formatForDisplay(e164),
                        )
                    }
                is PhoneLookupOutcome.Failed ->
                    _state.update { it.copy(checking = false, phoneError = outcome.message) }
            }
        }
    }

    /** The confirmation was dismissed: keep the number on screen for a second look. */
    fun onRegistrationDeclined() = _state.update {
        it.copy(offerRegistration = null, offerRegistrationDisplay = null)
    }

    fun onNavigated() = _state.update {
        it.copy(knownNumber = null, offerRegistration = null, offerRegistrationDisplay = null)
    }
}
```

- [ ] **Step 2: Write the screen**

Create `app/src/main/java/com/paychat/paychat/feature/auth/phone/PhoneEntryScreen.kt`:

```kotlin
package com.paychat.paychat.feature.auth.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paychat.paychat.feature.auth.AuthBackdrop
import com.paychat.paychat.feature.auth.AuthHero
import com.paychat.paychat.feature.auth.LightCardScheme
import com.paychat.paychat.feature.auth.StaggeredEntrance
import com.paychat.paychat.ui.components.PhoneField
import com.paychat.paychat.ui.components.PrimaryButton

/**
 * The one front door of the signed out flow.
 *
 * The number is the identity in PayChat, so it is the only thing asked for
 * here; whether an account exists decides what comes next, rather than the
 * user having to know which of two screens they belong on.
 */
@Composable
fun PhoneEntryScreen(
    onKnownNumber: (phoneE164: String) -> Unit,
    onNewNumber: (phoneE164: String) -> Unit,
    viewModel: PhoneEntryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.knownNumber) {
        state.knownNumber?.let {
            onKnownNumber(it)
            viewModel.onNavigated()
        }
    }

    state.offerRegistration?.let { e164 ->
        val display = state.offerRegistrationDisplay ?: e164
        AlertDialog(
            onDismissRequest = viewModel::onRegistrationDeclined,
            title = { Text("Create an account?") },
            text = {
                Text(
                    "No PayChat account uses $display yet. Create one for this number?",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onNewNumber(e164)
                    viewModel.onNavigated()
                }) { Text("Create account") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onRegistrationDeclined) { Text("Cancel") }
            },
        )
    }

    Scaffold(containerColor = Color.Transparent) { inner ->
        AuthBackdrop {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .imePadding()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                StaggeredEntrance(delayMillis = 0) {
                    AuthHero(
                        headline = "Chat and settle up",
                        subtitle = "Enter your phone number to get started.",
                    )
                }

                StaggeredEntrance(delayMillis = 120) {
                    LightCardScheme {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp,
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                PhoneField(
                                    value = state.phone,
                                    onValueChange = viewModel::onPhoneChange,
                                    error = state.phoneError,
                                    enabled = !state.checking,
                                    imeAction = ImeAction.Done,
                                )
                            }
                        }
                    }
                }

                StaggeredEntrance(delayMillis = 200) {
                    PrimaryButton(
                        text = "Continue",
                        onClick = viewModel::submit,
                        loading = state.checking,
                    )
                }

                Spacer(Modifier.weight(1f))
            }
        }
    }
}
```

The dialog reads its formatted number from state rather than constructing a
`PhoneNumbers` of its own, which would be a second instance of a `@Singleton`.
So the screen needs no `PhoneNumbers` import — remove
`import com.paychat.paychat.core.phone.PhoneNumbers` from the block above if
you pasted it.

- [ ] **Step 3: Wire the graph**

In `PayChatNavHost.kt`:

The signed-out redirect becomes:

```kotlin
            AuthGate.SIGNED_OUT ->
                if (navController.currentDestination?.route !in Routes.AUTH_ROUTES) {
                    navController.toTopLevel(Routes.PHONE)
                }
```

Add the entry destination before the register one:

```kotlin
            composable(Routes.PHONE) {
                PhoneEntryScreen(
                    onKnownNumber = { phone -> navController.navigate(Routes.login(phone)) },
                    onNewNumber = { phone -> navController.navigate(Routes.register(phone)) },
                )
            }
```

Replace the register destination with:

```kotlin
            composable(
                Routes.REGISTER,
                arguments = listOf(navArgument(NavArgs.PHONE) { type = NavType.StringType }),
            ) {
                RegisterScreen(
                    onOtpRequired = { phone ->
                        navController.navigate(Routes.otp(phone, OtpPurpose.REGISTER.name))
                    },
                    onChangeNumber = { navController.popBackStack(Routes.PHONE, false) },
                )
            }
```

Replace the login destination with:

```kotlin
            composable(
                Routes.LOGIN,
                arguments = listOf(navArgument(NavArgs.PHONE) { type = NavType.StringType }),
            ) {
                LoginScreen(
                    onSignedIn = { authGateViewModel.onSignedIn() },
                    onChangeNumber = { navController.popBackStack(Routes.PHONE, false) },
                    onResetRequested = { phone ->
                        navController.navigate(
                            Routes.otp(phone, OtpPurpose.RESET_PASSWORD.name)
                        )
                    },
                )
            }
```

Add `import com.paychat.paychat.feature.auth.phone.PhoneEntryScreen`.

- [ ] **Step 4: Build and run every unit test**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL, all tests pass. Fix any remaining reference to a
removed parameter (`onRegisterInstead`, `onLoginInstead`, `state.phone` on the
login or register screens) until it is clean.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/paychat/paychat/feature/auth/phone/ app/src/main/java/com/paychat/paychat/ui/nav/PayChatNavHost.kt
git commit -m "Ask for the number first and let it choose the branch"
```

---

### Task 9: On-device verification

The lookup cannot answer until the function is deployed, so this task starts
by asking for that.

**Files:** none — verification only.

- [ ] **Step 1: Ask before deploying**

Deploying changes the user's live Firebase project. Ask for explicit approval,
then run:

```bash
cd firebase/functions && npm run deploy
```

If approval is withheld, stop here and report that the flow cannot be verified
end to end until `phoneLookup` is deployed.

- [ ] **Step 2: Install on the connected device**

Run: `./gradlew installDebug`
Expected: "Installed on 1 device."

- [ ] **Step 3: Check the crash buffer is clear, then launch**

```bash
D:/android-sdk/platform-tools/adb.exe logcat -b crash -c
D:/android-sdk/platform-tools/adb.exe shell input keyevent KEYCODE_WAKEUP
D:/android-sdk/platform-tools/adb.exe shell monkey -p com.paychat.paychat -c android.intent.category.LAUNCHER 1
```

Then screenshot:

```bash
MSYS_NO_PATHCONV=1 D:/android-sdk/platform-tools/adb.exe shell screencap -p /sdcard/s.png
MSYS_NO_PATHCONV=1 D:/android-sdk/platform-tools/adb.exe pull /sdcard/s.png ./phone-entry.png
```

Expected: the phone entry screen, one field and Continue.

- [ ] **Step 4: Walk the three branches**

For each, screenshot and check `adb logcat -b crash -d` is empty afterwards:

1. **Known number** — enter a number that has an account. Expect the password
   step showing that number formatted, with Change and Forgot password?.
   Sign in and reach Home.
2. **Unknown number** — enter a number with no account. Expect the confirm
   dialog naming the number. Cancel returns to the field with the number
   intact; Create account reaches the details step with name, password and
   confirm password. A deliberate mismatch must show "Passwords do not match."
3. **Forgot password** — from the password step of a known number, set a new
   password and verify the OTP reaches Home.

- [ ] **Step 5: Report**

Report each branch as passed or failed with the decisive log line or
screenshot. Do not claim a branch passed without having seen it.

---

## Notes for the executor

- Tasks 5, 6 and 7 deliberately leave the tree not compiling; Task 8 closes it.
  Do not "fix" `PayChatNavHost.kt` early — the compiler errors there are the
  checklist Task 8 works through.
- If a step's code does not match what is actually in the file (someone has
  edited it since), stop and report rather than guessing. The plan quotes real
  current code, so a mismatch means the assumption behind the task changed.
