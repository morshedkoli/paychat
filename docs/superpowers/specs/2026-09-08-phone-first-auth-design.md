# Phone-first authentication

Date: 2026-09-08

## Problem

The signed-out flow has two independent front doors. `LoginScreen` asks for a
phone number and a password; `RegisterScreen` asks for a name, a phone number
and a password. Someone arriving at the app has to know which of the two they
belong to before they can type anything, and a returning user who taps
"Create an account" only discovers their number is taken after an SMS has
already been sent.

The number is the identity in PayChat — the account is a verified phone
credential, and `phoneIndex` maps a number to exactly one account. So the
number is the one thing worth asking for first.

## The flow

One entry point. The number decides which branch follows.

```
PHONE  ── enter number ──▶ phoneLookup callable
                              │
              exists = true   │   exists = false
                              │
        ┌─────────────────────┴──────────────────────┐
        ▼                                            ▼
  login/{phone}                        confirm dialog: "Create one?"
  password only                                      │ Create
  ├─ Log in ──▶ signed in                             ▼
  └─ Forgot password? ──▶ otp(phone,              register/{phone}
        RESET_PASSWORD) ──▶ new password          name + password + confirm
                                                       │
                                                       ▼
                                                  otp(phone, REGISTER)
                                                       │
                                                       ▼
                                                   signed in
```

Everything below the OTP screen is unchanged: `PendingRegistration`,
`OtpScreen`, `OtpPurpose`, and every `AuthRepository` method keep their current
signatures. This design reshuffles the screens above them and adds one
server-side lookup.

## Components

### `PhoneEntryScreen` / `PhoneEntryViewModel`

New, at route `phone`, and the start destination of the signed-out graph.

A single `PhoneField` and a Continue button, on the existing `AuthBackdrop`
with `AuthHero`, so it reads as the same flow as the screens it replaces.

Continue:

1. Normalises the input with `PhoneNumbers.toE164`. A null result sets the
   field error "Enter a valid phone number." and stops.
2. Calls `AuthRepository.phoneExists(e164)` (below) with the button in a
   loading state.
3. On `true`, navigates to `login/{phone}`.
4. On `false`, raises an in-screen `AlertDialog`:
   "No PayChat account for `+8801…`. Create one?" with Cancel and
   Create account. Create navigates to `register/{phone}`; Cancel returns to
   the field with the number still in it, which is what catches a typo.
5. On failure, shows the mapped message and re-enables Continue. It must not
   fall through to either branch — see Error handling.

### `LoginScreen` / `LoginViewModel`

Becomes the password step. Route `login/{phone}`.

- The phone field is gone. The number arrives as a route argument and is shown
  as read-only text with a Change action that pops back to `phone`.
- `LoginUiState` loses `phone` and `phoneError` and gains `phoneE164`, set once
  from the route.
- `submit()` no longer normalises anything; it validates that the password is
  non-empty and calls `authRepository.signIn(phoneE164, password)`.
- `startPasswordReset(newPassword)` keeps its behaviour but takes the number
  from state rather than re-parsing a field, so it can no longer fail for want
  of a valid number.

### `RegisterScreen` / `RegisterViewModel`

Becomes the details step. Route `register/{phone}`.

- The phone field is gone; the number arrives as a route argument and is shown
  read-only, as on the password step.
- A confirm-password field is added. `RegisterUiState` gains `confirmPassword`
  and `confirmPasswordError`.
- `submit()` validates name (`Validators.validateName`), password
  (`Validators.validatePassword`), and that confirm matches password —
  "Passwords do not match." on mismatch — then puts the details in
  `PendingRegistration` and proceeds to `otp(phone, REGISTER)` exactly as now.

### Routes

```kotlin
const val PHONE = "phone"
const val LOGIN = "login/{phone}"
const val REGISTER = "register/{phone}"

fun login(phoneE164: String) = "login/${phoneE164.removePrefix("+")}"
fun register(phoneE164: String) = "register/${phoneE164.removePrefix("+")}"
```

The leading `+` is stripped in the route and restored by the screen, matching
the convention `Routes.otp` already uses and for the same reason: a raw `+` in
a path segment is ambiguous once the route is parsed as a URI.

`AUTH_ROUTES` gains `PHONE`. The signed-out start destination changes from
`LOGIN` to `PHONE`.

### `AuthRepository.phoneExists`

```kotlin
suspend fun phoneExists(phoneE164: String): Result<Boolean>
```

Calls the `phoneLookup` callable through the injected `FirebaseFunctions`, in
the same shape as `MediaUploader` and `ModerationRepository`, and maps
failures through the existing `runCatchingAuth` so no Firebase type reaches a
ViewModel. `AuthError` gains `TooManyAttempts` for the rate-limited case.

### `phoneLookup` callable

New `firebase/functions/src/phoneLookup.ts`, exported from `index.ts`,
`region: "asia-south1"` and `enforceAppCheck: false` to match every existing
handler.

Deliberately unauthenticated — the whole point is to answer the question
before anyone can sign in.

```
input:  { phone: string }   // E.164, ^\+[1-9]\d{7,14}$
output: { exists: boolean }
```

The handler validates the shape of the number, reads `phoneIndex/{e164}` with
admin credentials, and returns whether the document exists. It returns nothing
else — no name, no uid, no timestamps. A masked name on the password step was
considered and rejected: it would turn one bit of disclosure into a bit plus a
partial identity.

Two fixed-window limits via the existing `enforceRateLimit`, whose `uid`
parameter is used here as an opaque counter key:

| key                      | limit | window |
|--------------------------|-------|--------|
| `ip:<rawRequest.ip>`     | 30    | 1 hour |
| `phone:<e164>`           | 6     | 1 hour |

The first bounds bulk enumeration from one source, the second bounds attention
on a single number even from many sources. Exceeding either throws
`resource-exhausted`, which the client shows as "Too many attempts."

`firestore.rules` does not change. Admin credentials bypass rules, so
`phoneIndex` stays `allow read: if signedIn()` and the callable remains the
only way to ask the question while signed out.

## Data flow and privacy

The lookup discloses one bit — whether a number is registered — to an
unauthenticated caller. That is the cost of the flow, and it is bounded by the
two rate limits rather than removed. What it buys is that a returning user
never pays for an SMS and a new user never meets a wrong-password error.

The lookup is a UX hint and never a security decision. Both branches remain
safe if it is wrong or tampered with:

- Claiming `exists = false` for a registered number leads to the register
  step, where `completeRegistration` sees `isNewUser == false`, signs out
  again and fails with `PhoneAlreadyRegistered`.
- Claiming `exists = true` for an unregistered number leads to the password
  step, where `signIn` fails with `PhoneNotRegistered`.

## Error handling

| Case | Behaviour |
|------|-----------|
| Number does not normalise | Field error, no call made |
| `exists = true` | Navigate to the password step |
| `exists = false` | Confirm dialog, then the register step |
| Network failure | "You appear to be offline." under the field; Continue re-enabled |
| `resource-exhausted` | "Too many attempts. Try again in a while." |
| Any other failure | Existing `AuthError.Unknown` message |

A failed lookup must never be treated as either answer. Guessing "new" would
send an SMS to someone who already has an account; guessing "existing" would
show a password field for an account that does not exist.

## Testing

The repository has no ViewModel tests and no mocking or coroutine-test
dependency, so this work follows the existing pattern — pure logic in
`app/src/test`, rules in `firebase/tests/rules.test.mjs` — rather than
introducing a framework.

- `app/src/test/.../feature/auth/PhoneLookupOutcomeTest.kt` — a small pure
  mapper from lookup result or `AuthError` to the branch the screen takes
  (password step, confirm dialog, or an error message), so every row of the
  table above is asserted without a ViewModel.
- `app/src/test/.../core/validation/ValidatorsTest.kt` — extend for the
  confirm-password rule if the check lands in `Validators`; otherwise the
  mapper test covers it.
- `firebase/tests/rules.test.mjs` — assert an unauthenticated read of
  `phoneIndex/{e164}` is still denied, so the callable stays the only path.
- Manual run on the connected device, all three branches: a registered number
  reaching the password step and signing in; an unregistered number through
  confirm, name and password, OTP, to a created account; and forgot-password
  from the password step.

## Out of scope

- App Check, which is off across every existing callable and is its own task.
- Name hints, masked or otherwise, on the password step.
- Any change to `AuthRepository.completeRegistration`, `signIn`,
  `resetPassword`, `PendingRegistration`, or the OTP screen.
