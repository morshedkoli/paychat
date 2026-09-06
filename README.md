# PayChat

Native Android chat app with a per-conversation money ledger. Kotlin, Jetpack
Compose, Firebase. Currency: BDT.

Full specification and build plan: [docs/SPEC.md](docs/SPEC.md).

## Status

Phase 2 — contacts. Authentication is complete, and the app can now find which
of the user's contacts have accounts, add people who do not, and open the
conversation for either. Remaining screens are placeholders that name the phase
which replaces them.

Implemented and unit tested:

- `core/money/Money.kt` — integer poisha, exact parsing and formatting
- `core/ledger/BalanceCalculator.kt` — direction, acceptance, and sign rules
- `core/phone/PhoneNumbers.kt` — E.164 normalisation
- `core/model/ThreadIds.kt` — derived, order-independent thread ids
- `core/validation/Validators.kt` — name, password, and OTP rules
- `data/auth/` — registration, sign in, password reset
- `data/contacts/` — address book sync, account discovery, local contacts
- `firebase/firestore.rules` — every ledger invariant from the spec

## Contacts and privacy

The address book is never uploaded. Contact discovery reads the numbers on the
device, normalises them to E.164, and reads `phoneIndex` for those ids in
batches of 30 — the Firestore `whereIn` limit. The result is cached in Room.
Numbers that leave the address book are dropped on the next sync.

A number without an account can still be added by hand. That creates a local
contact and a one-sided thread owned by the user alone, which becomes a real
two-party thread when the number registers (phase 7).

## How an account works

Firebase Auth has no password on the phone provider and no phone on the
password provider, but PayChat needs both. An account is therefore a phone
credential plus a linked Email/Password credential whose address is derived
from the number by `SyntheticEmail` (`p8801712345678@phone.paychat.invalid`).
No mail is ever sent there.

Registration: verify OTP, sign in with the phone credential, link the password
credential, then write `users/{uid}` and `phoneIndex/{e164}` in one batch. The
account is only created after the number is verified, and if linking the
password fails the half-built account is deleted rather than left behind.

Login needs no SMS: the synthetic address is recomputed from the typed number.
Password reset verifies by OTP and then sets the new password.

**Because the sign-in identifier is derivable from a phone number, the password
is the only secret protecting an account.** `Validators` enforces a minimum
length and rejects digit-only and common passwords. Enable **App Check** and
Firebase Auth's **email enumeration protection** on the project before release.

One device at a time: signing in writes a new `activeSessionId` on the user
document. Every other device watches that field and signs itself out when the
value stops matching its own.

## Opening the project

1. Copy `local.properties.sample` to `local.properties` and set `sdk.dir` to
   your Android SDK. Use forward slashes (`D:/android-sdk`) — a backslash is an
   escape character in a properties file and silently mangles the path.
2. Create the Firebase project (see below) and put the real
   `google-services.json` in `app/`. The build will not configure without one.
3. Open in Android Studio, or build from the command line:

```bash
./gradlew assembleDebug
```

Requires JDK 17, Android SDK platform 35 and build-tools 35.0.0. The Gradle
wrapper (8.11.1) is checked in.

> A placeholder `google-services.json` is enough to compile, but the app cannot
> reach Firebase with it. `app/google-services.json` is gitignored, so it never
> reaches the repository either way.

## Firebase setup

1. Create a Firebase project and add an Android app with package name
   `com.paychat.koli`. Add a second app for `com.paychat.koli.debug` — the
   debug build uses that suffix.
2. Register the SHA-1 and SHA-256 of your debug and release signing keys.
   Phone authentication will not work without them.
3. Enable **Authentication** providers: Phone, and Email/Password.
4. Create a **Cloud Firestore** database in production mode.
5. Upgrade the project to the **Blaze** plan. Cloud Functions cannot be deployed
   on Spark, and Functions are required for push notifications, the
   registration handover trigger, due-date reminders, and Cloudinary upload
   signing. Blaze keeps the free quota tiers, so low volume costs nothing, but a
   billing account must be attached.
6. Deploy the rules and indexes:

```bash
firebase deploy --only firestore:rules,firestore:indexes
```

## Cloudinary setup

1. Create a free Cloudinary account. Note the cloud name.
2. Put the cloud name in `local.properties` as `cloudinary.cloudName`.
3. The API key and secret go into Cloud Functions configuration only. They must
   never be placed in the app or in an unsigned upload preset — an unsigned
   preset lets anyone holding the APK upload arbitrary files and exhaust the
   quota.

## Running the tests

```bash
./gradlew testDebugUnitTest
```

44 unit tests cover the money arithmetic, the balance rules, phone
normalisation, thread ids, input validation, and address book normalisation.

## Layout

```
app/src/main/java/com/paychat/koli/
  core/          pure logic, no Android or Firebase types
    money/       Money value class
    ledger/      balance arithmetic
    model/       domain enums
    phone/       E.164 normalisation
  data/local/    Room database, entities, DAOs
  di/            Hilt modules
  ui/            theme, navigation, screens
firebase/        security rules and indexes
docs/            specification
```
