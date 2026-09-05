# Chat Ledger App — Specification & Build Plan

Native Android chat application with an embedded per-conversation money ledger.
WhatsApp-like interface. Currency: BDT only.

---

## 1. Product summary

Users chat one-to-one. Inside any conversation either party can record a money
transaction. Transactions settle into a running balance for that conversation.
The home screen shows every conversation with its balance, plus an aggregate
balance across all conversations.

Users may also record transactions against people who have not installed the
app, identified only by name and phone number. When that phone number later
registers, the historical records are attached to the new account for review.

---

## 2. Core rules

### 2.1 Identity
- Registration requires name, phone number, and password.
- The phone number must be verified by OTP before the account is usable.
- The phone number is the unique identifier used to find other users.
- Phone numbers are stored in E.164 form (`+8801XXXXXXXXX`). All lookup and
  matching normalises to this form.

### 2.2 Transaction directions
A transaction is always written from the perspective of its author.

| Author records | Meaning | Acceptance required |
| --- | --- | --- |
| `SENT` | "I gave money to you" | Yes — counterparty must accept |
| `RECEIVED` | "I got money from you" | No — applies immediately |

Rationale: a `SENT` claim increases what the counterparty owes, so it must be
confirmed. A `RECEIVED` claim only reduces what the counterparty owes, so it
carries no fraud risk and applies at once.

### 2.3 Transaction lifecycle

```
PENDING ──accept──> ACCEPTED
   │
   ├──reject──> REJECTED
   └──cancel (author only)──> CANCELLED
```

- `RECEIVED` transactions are created directly in `ACCEPTED`.
- `SENT` transactions are created in `PENDING`.
- Transactions against an unregistered contact are created in `ACCEPTED`
  regardless of direction, but carry `unconfirmed = true` (see 2.6).
- Only `ACCEPTED` transactions affect any balance.

### 2.4 Immutability and corrections
An `ACCEPTED` transaction is never edited or deleted. To correct one, the author
creates a reversal: a new transaction with the opposite direction, the same
amount, and `reversesId` pointing at the original. The reversal follows the
normal acceptance rules for its direction. The original stays visible in the
history, marked as reversed.

A `PENDING` transaction may be freely cancelled by its author until the
counterparty acts on it.

### 2.5 Balance
Balance is always derived, never stored as the authority.

```
balance(thread, viewer) =
    sum( +amount )  for ACCEPTED transactions where the viewer is the payer
  + sum( -amount )  for ACCEPTED transactions where the viewer is the payee
```

Sign convention, from the viewer's perspective:
- **positive** — the other person owes the viewer
- **negative** — the viewer owes the other person

Both parties see the same figure with opposite sign. A cached copy is kept per
thread per user for fast list rendering, but it is always recomputable from the
transaction rows and is treated as disposable.

The home screen shows three figures:
- **Net** — sum of all thread balances
- **You will get** — sum of positive thread balances
- **You will give** — sum of negative thread balances

Net alone is misleading (a net of zero can hide 50,000 owed in each direction),
so all three are always displayed.

### 2.6 Unregistered contacts and handover

A user may create a local contact with just a name and a phone number, and
record transactions against it. These are stored under the creator's account and
are visible only to them. No acceptance step exists because there is no other
party yet.

When that phone number registers:

1. Every local contact whose phone matches the new account is linked to it.
2. Each linked contact's thread is promoted to a real two-party thread.
3. All historical transactions in that thread become visible to the new user,
   flagged **unconfirmed**.
4. The new user sees a review screen listing the inherited history, with
   per-item accept/reject and a bulk "Accept all" action.
5. Unconfirmed transactions **do not** count toward the new user's balances
   until reviewed. They continue to count toward the original creator's balance
   (the creator recorded them in good faith), but the thread is flagged as
   "awaiting confirmation" on the creator's side.

This prevents anyone from pre-loading fabricated debt against a phone number
before its owner joins.

### 2.7 Amounts
Money is stored as an integer number of poisha (`amountMinor`, 1 BDT = 100
poisha). Floating point is never used for money anywhere in the stack.

---

## 3. Feature list

### v1
- Phone registration with OTP verification, password login
- Contact discovery from the device phone book
- Manually added unregistered contacts
- One-to-one chat: text, image, voice note
- Delivery and read receipts, typing indicator
- Transaction messages with amount, note, optional photo, optional due date
- Accept / reject / cancel flows with in-chat system messages
- Per-thread balance header, home screen balance summary
- Unregistered-to-registered handover with review screen
- Push notifications for messages, new transactions, and transaction decisions
- PDF statement export per thread and for all threads
- Search across chats and transactions
- Dark mode, app lock (biometric / PIN)

### Deferred
- Group chats and split bills
- Multi-currency
- End-to-end encryption (incompatible with server-derived balances as designed)
- Recurring transactions, interest, reminders beyond due-date notification

---

## 4. Technical stack

### Android client
- Kotlin, Jetpack Compose, Material 3
- MVVM with a repository layer
- Room as the local source of truth for all UI reads
- Hilt for dependency injection
- WorkManager for outbound sync and media uploads
- Coil for images, ExoPlayer for voice playback, MediaRecorder for capture
- Firebase Auth, Cloud Firestore, FCM

### Backend — Firebase
- **Firebase Auth** — phone OTP for verification. Because the product also
  requires a password, the account is created with the Email/Password provider
  using a synthetic identifier derived from the phone number, and the phone
  credential is linked to the same account after OTP succeeds. This yields one
  account carrying both a verified phone number and a password.
- **Cloud Firestore** — all documents. Security rules enforce every invariant
  that matters (see section 6).
- **Cloud Functions** — required for: sending FCM notifications, the
  registration handover trigger, due-date reminder scheduling, and issuing
  Cloudinary upload signatures.
- **FCM** — push notifications.

> **Plan requirement:** Cloud Functions cannot be deployed on the free Spark
> plan. The Firebase project must be on the Blaze (pay-as-you-go) plan. Blaze
> keeps the same free quota tiers, so at low volume the bill is effectively zero,
> but a billing account must be attached. If that is unacceptable, notifications,
> the handover trigger, and upload signing must move to a small self-hosted
> worker instead.

### Media hosting
Chat images, transaction receipt photos, and voice notes are uploaded to
**Cloudinary** (free tier: 25 GB storage, 25 GB monthly bandwidth, hosts both
image and audio/video resources). ImgBB is not used because it does not host
audio.

Uploads are **signed**: the client requests a short-lived upload signature from a
Cloud Function, then uploads directly to Cloudinary. An unsigned upload preset is
deliberately avoided — it would let anyone holding the APK upload arbitrary files
to the account and exhaust the quota.

Only the returned secure URL and public ID are stored in Firestore.

---

## 5. Data model (Firestore)

```
users/{uid}
  phone            string   E.164, unique (enforced via phoneIndex)
  name             string
  photoUrl         string?
  createdAt        timestamp
  fcmTokens        map<string, timestamp>

phoneIndex/{e164}
  uid              string
  # write-once document; makes phone uniqueness and lookup enforceable in rules

threads/{threadId}
  # threadId = sorted pair "uidA_uidB", or "uidOwner_local_{contactId}"
  members          array<string>     one uid for local threads, two otherwise
  isLocal          bool
  localContact     { name, phone }?  present when isLocal
  lastMessage      { text, type, at, senderId }
  updatedAt        timestamp

threads/{threadId}/messages/{messageId}
  senderId         string
  type             "TEXT" | "IMAGE" | "VOICE" | "TXN" | "SYSTEM"
  text             string?
  mediaUrl         string?
  mediaPublicId    string?
  durationMs       number?           voice only
  txnId            string?           TXN only
  createdAt        timestamp
  deliveredTo      array<string>
  readBy           array<string>

threads/{threadId}/transactions/{txnId}
  createdBy        string
  direction        "SENT" | "RECEIVED"
  amountMinor      number            integer poisha, greater than zero
  note             string?
  photoUrl         string?
  photoPublicId    string?
  dueDate          timestamp?
  status           "PENDING" | "ACCEPTED" | "REJECTED" | "CANCELLED"
  unconfirmed      bool              inherited history awaiting review
  reversesId       string?
  reversedBy       string?
  createdAt        timestamp
  resolvedAt       timestamp?
  resolvedBy       string?

users/{uid}/threadBalances/{threadId}
  amountMinor      number            signed, viewer perspective
  updatedAt        timestamp
  # derived cache, recomputable from transactions

users/{uid}/localContacts/{contactId}
  name             string
  phone            string            E.164
  threadId         string
  linkedUid        string?           set when that phone registers
```

### Payer resolution

```
payer(txn) = txn.direction == "SENT" ? txn.createdBy : otherMember(thread, txn.createdBy)
payee(txn) = the other member of the thread
```

---

## 6. Security rules — invariants to enforce

1. A user may only read a thread where their uid is in `members`.
2. A user may only write messages where `senderId == request.auth.uid`.
3. `amountMinor` must be an integer greater than zero.
4. On create, `direction == "SENT"` forces `status == "PENDING"` and
   `direction == "RECEIVED"` forces `status == "ACCEPTED"` — except on local
   threads, where both are created as `ACCEPTED`.
5. Only the counterparty, never the author, may move `PENDING` to `ACCEPTED` or
   `REJECTED`.
6. Only the author may move `PENDING` to `CANCELLED`.
7. No transition out of `ACCEPTED`, `REJECTED`, or `CANCELLED` is permitted.
8. `createdBy`, `direction`, `amountMinor`, and `createdAt` are immutable after
   creation.
9. `unconfirmed` may only be cleared by the inheriting user.
10. `phoneIndex/{e164}` is create-only, and only by the account claiming it.
11. `threadBalances` is written only by the owning user or by a Cloud Function.

---

## 7. Notable implementation decisions

**Offline first.** Every screen reads from Room. Firestore offline persistence is
also enabled, but Room stays the UI source of truth so that pending outbound
items, upload progress, and local-only contacts all render consistently.
Outbound messages and transactions are queued in Room with a `syncState` and
flushed by WorkManager.

**PDF export.** Generated on-device with Android's `PdfDocument`, so no Cloud
Function invocation and no server round trip is needed. A statement lists every
accepted transaction in a thread with date, direction, amount, note, and running
balance, followed by a closing balance summary.

**Due dates.** Stored on the transaction. A daily scheduled Cloud Function scans
for transactions due the next day and pushes a reminder to both parties.

**Voice notes.** Recorded as AAC in an MP4 container via `MediaRecorder`,
uploaded to Cloudinary as a `video` resource type, played back with ExoPlayer
behind a waveform-style progress bar.

**Balance recomputation.** The client recomputes a thread balance locally from
its Room transaction rows on every change, keeping the UI instant and correct
even offline. The Firestore cache document exists so the home screen does not
have to load every transaction of every thread; it is written after each local
recompute and treated as advisory.

---

## 8. Screens

1. Splash / auth gate
2. Registration — name, phone, password
3. OTP verification
4. Login
5. Home — balance summary card, thread list with per-thread balance
6. Contacts — registered contacts, invite, add unregistered contact
7. Chat — message list, composer, attachment sheet, balance header
8. Add transaction — direction toggle, amount pad, note, photo, due date
9. Transaction detail — status, history, accept / reject / cancel / reverse
10. Thread ledger — transactions only, running balance, export button
11. Inherited history review — for newly registered users
12. Search
13. Settings — profile, notifications, app lock, theme, export all

---

## 9. Build phases

| Phase | Scope |
| --- | --- |
| 0 | Project scaffold, Firebase wiring, theme, navigation skeleton, Room setup |
| 1 | Registration, OTP, password login, session, profile, `phoneIndex` |
| 2 | Contact sync, user discovery by phone, local contact creation |
| 3 | Chat: text messaging, realtime listeners, offline queue, receipts |
| 4 | Media: image and voice capture, Cloudinary signed upload, playback |
| 5 | Transactions: create, accept / reject / cancel, in-chat rendering, balance |
| 6 | Home summary, thread ledger screen, running balance |
| 7 | Handover: registration trigger, linking, inherited history review |
| 8 | Notifications, due-date reminders, PDF export, search |
| 9 | App lock, dark mode, settings, block & report, error handling, empty states |
| 10 | Security rules hardening, abuse limits, data export, account deletion, tests, Play Store release |

---

## 10. Decisions

- **Application id** — `com.paychat.koli`. **App name** — PayChat.
- **Password reset** — OTP based. The user enters their phone number, verifies
  by OTP, then sets a new password. Handled in phase 1.
- **Multi-device** — one active device per account. Signing in on a new device
  signs the previous one out. The active device's FCM token is the only one
  kept in `users/{uid}.fcmTokens`, and a session token mismatch forces a sign
  out on the old device.
- **Blocking and reporting** — included. A blocked user cannot send messages or
  create transactions in the shared thread; existing history and balances stay
  visible and unchanged, because blocking someone must not erase what is owed.
  Reporting sends the thread id and a reason to a moderation collection.
  Scheduled in phase 9.
- **Data export and account deletion** — required by Play Store policy.
  Scheduled in phase 10.
