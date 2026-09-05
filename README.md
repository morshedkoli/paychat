# PayChat

Native Android chat app with a per-conversation money ledger. Kotlin, Jetpack
Compose, Firebase. Currency: BDT.

Full specification and build plan: [docs/SPEC.md](docs/SPEC.md).

## Status

Phase 0 — project scaffold. Navigation, theme, Room schema, dependency
injection, and the ledger arithmetic are in place. Screens are placeholders that
name the phase which replaces them.

Implemented and unit tested already:

- `core/money/Money.kt` — integer poisha, exact parsing and formatting
- `core/ledger/BalanceCalculator.kt` — direction, acceptance, and sign rules
- `firebase/firestore.rules` — every ledger invariant from the spec

## Opening the project

The project has no Gradle wrapper checked in yet, because it was scaffolded
without an Android SDK present.

1. Open the folder in Android Studio (Ladybug or newer). Android Studio will
   offer to generate the wrapper and sync.
   Alternatively, with a local Gradle 8.9+ installed, run `gradle wrapper`.
2. Copy `local.properties.sample` to `local.properties` and set `sdk.dir`.
3. Create the Firebase project (see below) and drop `google-services.json` into
   `app/`. The build will not sync without it.

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
./gradlew test
```

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
