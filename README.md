# PayChat

Native Android chat app with a per-conversation money ledger. Kotlin, Jetpack
Compose, Firebase. Currency: BDT.

Full specification and build plan: [docs/SPEC.md](docs/SPEC.md).

## Status

Phase 9 — settings, app lock, blocking, and the rough edges. Every screen in
the specification is now built. Settings carries the profile, the colour
scheme, the lock, and the export; the app can be locked behind the phone's own
screen lock; a conversation can be blocked or reported without touching what
it records; failures speak in one voice and empty screens have one shape.

The typing indicator listed in the specification is still not built. It needs
a presence write on every keystroke, which is a cost worth measuring against
the abuse limits in phase 10 rather than adding blind.

What phase 10 still owes: real Room migrations in place of the destructive
one, rules hardening and abuse limits, data export and account deletion, and
the Play Store release build.

Implemented and unit tested:

- `core/money/Money.kt` — integer poisha, exact parsing and formatting
- `core/ledger/BalanceCalculator.kt` — direction, acceptance, and sign rules
- `core/phone/PhoneNumbers.kt` — E.164 normalisation
- `core/model/ThreadIds.kt` — derived, order-independent thread ids
- `core/validation/Validators.kt` — name, password, and OTP rules
- `data/auth/` — registration, sign in, password reset
- `data/contacts/` — address book sync, account discovery, local contacts
- `data/chat/` — messages, threads, receipts
- `core/ledger/TransactionRules.kt` — who may accept, reject, cancel, correct
- `core/ledger/LedgerStatement.kt` — running balance and closing balance
- `data/media/` — photo and voice capture, signed Cloudinary upload
- `data/transactions/` — recording, settling, correcting, balance recomputation
- `data/sync/` — offline outbox
- `data/notifications/` — push tokens, data-only pushes, deep link to the chat
- `data/export/` — on-device PDF statements, per thread and across all threads
- `data/search/` — search over people, messages, and transaction notes
- `data/settings/` — theme and lock preferences, and the lock itself
- `data/moderation/` — blocking a conversation, and reporting one
- `core/errors/UserMessage.kt` — one sentence per failure, never a status code
- `firebase/firestore.rules` — every ledger invariant from the spec

## How the ledger works

A transaction is written from its author's point of view. `SENT` ("I gave you
money") raises what the other person owes, so it starts `PENDING` and they have
to accept it. `RECEIVED` ("I got money from you") only lowers what they owe, so
it applies immediately — there is nothing to protect them from. On a one-sided
thread with someone who has not registered, both directions apply at once and
are marked unconfirmed until that person joins.

An accepted transaction is never edited or deleted. Correcting one adds an
opposite entry pointing back at the original, which stays visible marked as
corrected, so the history remains auditable and both people can see what
changed.

The balance is never the authority. It is recomputed from the transaction rows
after every change and cached only so the lists do not have to load every
transaction of every conversation; it is always derivable again. `BalanceCalculator.viewerIsPayer` is the single place that
decides who handed money over, so the interface can say "you gave" on one
screen and "you received" on the other without the two disagreeing.

`TransactionRules` holds the same rules the security rules enforce, so the
interface can grey out an action instead of letting the user tap it and get a
permission error. The server stays the authority; that copy is for the
interface, never for trust.

The client re-uploads a transaction in full on every retry, so the rules allow
a write that changes nothing. Without that, a retry after a write that
succeeded but whose response never arrived would be denied for ever.

A balance is stored in its own table rather than on the conversation, because
it belongs to the reader and not to the conversation: the same thread is a
positive figure for one person and a negative one for the other. Keeping it
separate also means a balance arriving from the server before the thread it
belongs to is not thrown away.

`LedgerStatement` builds the statement: each line carries the balance as it
stood at that point. The running total is accumulated over every entry
whatever the screen filters out, so hiding a rejected row cannot change the
arithmetic of the rows around it, and the closing balance is taken from the
accumulated total rather than from the first or last line, so reversing the
display order cannot change the answer. The screen and the PDF export both
read it, so the two can never disagree about what the ledger says.

## How attachments work

The app never holds the Cloudinary API secret. `signMediaUpload`, a Cloud
Function, signs each upload and the app sends the file straight to Cloudinary,
so the file never passes through Firebase and does not count against its quota.

The signature covers the public id as well as the timestamp, and the public id
always begins with the caller's own uid, so a signature cannot be reused to
overwrite somebody else's file. An unsigned upload preset would have avoided
the function entirely, and is not used: it lets anyone holding the APK upload
arbitrary files to the account and exhaust the free quota.

A picked photo is copied into the app's own storage before it is queued,
because the picker's permission on the original lasts only as long as the
activity result while an outbox entry may outlive it by days. The outbox
uploads the file before the message document, so a message is never visible to
the other person pointing at a file that is not there yet, and stores the
resulting URL first, so a retry after a failed document write does not upload
the file twice.

Voice notes are AAC in an MP4 container, uploaded as a Cloudinary `video`
resource, since Cloudinary has no audio type. The conversation shares one
ExoPlayer rather than one per bubble: each player holds a hardware codec, and a
screen full of them would exhaust the device's decoders.

## How messaging works

Room is the source of truth for what the screen shows, so a chat opens
instantly from cache and behaves the same with or without a connection.

Sending writes the message to Room with a client-generated id and queues
`OutboxWorker`. The upload uses `set` with that id, so retrying an upload that
actually succeeded overwrites the same document rather than sending a
duplicate. Receiving is a Firestore snapshot listener that emits mapped rows;
the collector, which is a coroutine, does the database write, because a
snapshot listener cannot suspend.

Receipts live in two array fields, `deliveredTo` and `readBy`, and are the only
fields a non-sender may change on a message. `MessageMapper` holds the rule
that makes them readable: on a message the user sent they describe what the
*other* person did, and on a message the user received `readAt` records when
the user themselves read it, which is what the unread badge counts. Every
message lists its own sender in both arrays, so the mapper ignores the sender's
own entry — otherwise every message would show as read the moment it was sent.

## Notifications and statements

Pushes are data-only, so the app draws every notification itself. A payload
the system drew for us could not be suppressed for the conversation already on
screen, and would lose the tap target that opens the right chat. The chat
screen reports itself as visible on resume and hidden on pause, so a chat left
open behind another app still notifies.

One device holds the account, so the user document holds one token. Signing
out deletes it, and a token the transport rejects is deleted rather than
retried, since it belongs to an app that is no longer installed. A daily job
scans a one day window for due dates and reminds both parties.

Statements are drawn on the device with Android's own PdfDocument, so an
export works offline and costs nothing to run. They list accepted
transactions only: pending, rejected and cancelled entries carry no money, and
printing them would invite someone to read a figure that was never owed.

Search is answered entirely from Room. The device already holds every message
and transaction it may see, so search works offline and needs no server-side
index — Firestore could not run the query without one.

## Locking, blocking, and reporting

The app lock is the phone's own: a fingerprint, a face, or the screen lock
PIN. PayChat stores no PIN of its own, so there is no second secret to forget
and nothing worth stealing from the app's storage. It arms after the app has
been away for half a minute, because taking a photo or answering a
notification is a detour rather than an absence; a cold start locks straight
away.

Blocking closes a conversation in both directions, and changes nothing else.
The history and the balance stay exactly as they were. The flag lives on the
thread document, because the security rules have to see it to refuse the other
side's writes, and a member may add or remove only their own uid.

Reports are create-only and unreadable by clients: nobody can enumerate who
has been reported, and nothing can be edited away once filed.

## Contacts and privacy

The address book is never uploaded. Contact discovery reads the numbers on the
device, normalises them to E.164, and reads `phoneIndex` for those ids in
batches of 30 — the Firestore `whereIn` limit. The result is cached in Room.
Numbers that leave the address book are dropped on the next sync.

A number without an account can still be added by hand. That creates a local
contact and a one-sided thread owned by the user alone.

## The handover

When that number finally registers, `attachHistoryOnRegistration` turns those
one-sided threads into real two-party conversations. It triggers on the
`phoneIndex` document rather than on the profile, because that is the write
that claims a number and the rules only allow it for a number Firebase itself
verified by OTP; triggering on the profile would fire before the number was
proven.

The transactions are not rewritten. They were created `unconfirmed` on a
one-sided thread, which is already the state the review screen looks for, and
rewriting them would mean touching an unbounded number of documents inside one
trigger.

Unconfirmed history counts for the person who wrote it and for nobody else, so
**no one can load debt onto a phone number before its owner joins**. The new
user reviews each entry: accepting brings it into their balance, rejecting
stops it counting for either side, because a dispute that removed it from only
one of them would leave the two permanently disagreeing. Until they decide,
the original author sees the conversation marked as awaiting confirmation.

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
   `com.paychat.paychat`. Debug and release share that application id, so one
   registration covers both. Adding an `applicationIdSuffix` to the debug build
   would need a second Firebase app registered for it, or phone auth stops
   working on debug builds.
2. Register the SHA-1 and SHA-256 of your debug and release signing keys.
   Phone authentication will not work without them.
3. Enable **Authentication** providers: Phone, and Email/Password.
4. Create a **Cloud Firestore** database in production mode.
5. Upgrade the project to the **Blaze** plan. Cloud Functions cannot be deployed
   on Spark, and Functions are required for push notifications, the
   registration handover trigger, due-date reminders, and Cloudinary upload
   signing. Blaze keeps the free quota tiers, so low volume costs nothing, but a
   billing account must be attached.
   Deploying the reminder job also enables Cloud Scheduler on the project the
   first time it runs.
6. Deploy the rules and indexes:

```bash
firebase deploy --only firestore:rules,firestore:indexes
```

## Cloudinary setup

Nothing about Cloudinary is configured in the app. The signing function returns
the cloud name along with every signature, so all three values live in Secret
Manager and there is no second copy to drift out of step.

1. Create a free Cloudinary account. Note the cloud name, API key and secret.
2. Set all three as Cloud Functions secrets (see below).

The API key and secret must never be placed in the app, and an unsigned upload
preset must never be used: it lets anyone holding the APK upload arbitrary
files and exhaust the quota.

## Running the tests

```bash
./gradlew testDebugUnitTest
```

90 unit tests cover the money arithmetic, the balance, statement, handover and
transaction rules,
phone
normalisation, thread ids, input validation, address book normalisation,
receipt mapping, upload request assembly, and timestamp formatting.

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

## Cloud Functions

```bash
cd firebase/functions && npm install
firebase functions:secrets:set CLOUDINARY_CLOUD_NAME
firebase functions:secrets:set CLOUDINARY_API_KEY
firebase functions:secrets:set CLOUDINARY_API_SECRET
firebase deploy --only functions
```

The secrets live in Secret Manager, never in the repository and never in the
app. Every function is deployed to `asia-south1`; change the region constant
in each source file under `firebase/functions/src/` if your users are
elsewhere.

The deployed functions are `signMediaUpload`, `attachHistoryOnRegistration`,
the three notification triggers, and `remindDueTransactions`, which runs at
18:00 Asia/Dhaka.
