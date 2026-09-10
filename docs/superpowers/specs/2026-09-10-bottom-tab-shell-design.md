# Bottom tab shell: Chats, Transactions, Profile

Date: 2026-09-10
Status: approved for planning

## Goal

Replace the single Home screen with a three-tab shell. Chats keeps the
conversation list. Transactions is a new global feed of every transaction the
user is party to. Profile is a new tab that absorbs the current Settings
screen. Direction B of the two mockups was chosen: a saturated balance hero,
direction badges in the feed, centred profile identity — rendered in both
light and dark.

## Decisions taken during brainstorming

| Question | Decision |
| --- | --- |
| What is the Transactions tab? | A global feed of every transaction, newest first, filterable. Not a per-person balance list. |
| Profile versus Settings | Profile absorbs Settings. The `SETTINGS` route is retired. |
| What stays on Chats | Thread list, search, new-chat FAB, inherited banner. The balance summary and statement export leave. |
| Bottom bar on deep screens | Hidden. Chat, transaction detail and ledger stay full screen. Each tab keeps its own back stack. Back from Chats exits; back from another tab returns to Chats first. |
| Visual direction | B, bold balance. |
| Theme | Both light and dark, through existing tokens. The user's existing `ThemeChoice` setting keeps working. |

## Architecture

### Navigation

`PayChatNavHost` stays a single outer graph. A new route `Routes.MAIN` becomes
the signed-in landing destination, replacing `Routes.HOME` as the target of
`toTopLevel`. `MAIN` hosts `MainShell`, which owns a `Scaffold` with a
`NavigationBar` and a second, inner `NavHostController` for the three tab
roots.

```
outer graph                     inner graph (inside MainShell)
  splash                          chats
  phone / register / otp / login  transactions
  main  ──────────────────────▶   profile
  chat/{threadId}
  chat/{threadId}/transaction/new
  chat/{threadId}/ledger
  transaction/{txnId}
  inherited/{threadId}
  contacts, contacts/add
  search
```

Consequences of this shape, all of them wanted:

- The bar cannot appear over a deep screen, because deep screens are not in
  the inner graph.
- Each tab keeps its own back stack and scroll position with no extra code.
- The notification deep link (`openThreadId`) still navigates on the outer
  controller and needs no change.

`MainShell` receives one callback, `onOpenOuter: (String) -> Unit`, and passes
it to each tab so tab content can push outer destinations. Tab switching uses
`popUpTo(inner start) { saveState = true }`, `restoreState = true`,
`launchSingleTop = true`.

### Route changes

- Add `Routes.MAIN`, `Routes.CHATS`, `Routes.TRANSACTIONS`, `Routes.PROFILE`.
- Remove `Routes.HOME` and `Routes.SETTINGS`.
- Everything else is untouched.

### File moves and new files

| Path | Change |
| --- | --- |
| `ui/nav/MainShell.kt` | New. Scaffold, NavigationBar, inner NavHost. |
| `feature/chats/ChatsScreen.kt` | Moved from `feature/home/HomeScreen.kt`. Balance card, export overflow and settings action removed. |
| `feature/chats/ChatsViewModel.kt` | Moved from `feature/home/HomeViewModel.kt`. Summary and export state removed. |
| `feature/transactions/TransactionsScreen.kt` | New. |
| `feature/transactions/TransactionsViewModel.kt` | New. |
| `feature/profile/ProfileScreen.kt` | New. Hosts the identity block and the setting sections. |
| `feature/settings/SettingsSections.kt` | New. Stateless section composables extracted from `SettingsScreen.kt`. |
| `feature/settings/SettingsScreen.kt` | Deleted once its content lives in the sections file. `SettingsViewModel` is kept as is. |
| `feature/home/` | Deleted. |

## The Transactions tab

### Data

`TransactionDao` has no cross-thread query today. Add one:

```kotlin
@Query("SELECT * FROM transactions ORDER BY createdAt DESC LIMIT :limit")
fun observeRecent(limit: Int): Flow<List<TransactionEntity>>
```

No Paging library. The screen starts at 100 rows and raises the limit by 100
when the list reaches its end, which matches the flow-based pattern used
everywhere else in the app and is sufficient for this data volume.

`TransactionRepository` gains a thin wrapper around it. Peer display names are
not joined in SQL; the ViewModel combines the transaction flow with
`ThreadsRepository.observeThreads()` and matches on `threadId`, reusing the
existing display-name handling for local-only contacts.

### Sign, wording and filtering

Direction is stored from the author's point of view, so the reader's side is
derived, never stored. Use the existing
`BalanceCalculator.viewerIsPayer(entry, viewerUid)` for every row:

- viewer is payer: `+`, credit colour, "you gave".
- otherwise: `−`, debit colour, "you got".

Filter chips (All / You gave / You got) filter in memory on the same
predicate, because the sign is not a column.

Unconfirmed rows — history recorded against the user's number before they
registered — render muted and are excluded from the hero, matching the rule
already in `BalanceCalculator.effectOn`.

### Layout

Balance hero at the top: net balance large, then get / give / people. `people`
is the count of threads with a non-zero balance. The hero reuses
`BalanceSummary` and the combine that `HomeViewModel` builds today from
`transactions.observeBalances()`; that code moves rather than being rewritten.

Below it: filter chips, then the feed grouped under local-calendar day headers
derived from `createdAt` via `ui/components/Timestamps.kt`. A row taps through
to the outer `TRANSACTION_DETAIL` route.

Out of scope on purpose: in-tab search (the existing `SEARCH` screen already
covers messages and transactions), date-range pickers, per-filter export.

## The Profile tab

`SettingsScreen` is 388 lines and carries its own `Scaffold`, top bar and back
arrow, none of which can survive inside a tab. Split it:

- `SettingsSections.kt` holds stateless composables — `AccountSection`,
  `AppearanceSection`, `DataSection`, `DangerSection` — plus the existing
  private helpers (`ThemeRow`, `SectionHeading`, `NameDialog`) made internal.
- `ProfileScreen.kt` renders the centred identity block (avatar, name, phone,
  edit) from direction B, then those sections in a scrolling column, with its
  own snackbar host and the confirm dialogs for sign out and delete.
- `SettingsViewModel` is unchanged and is the Profile screen's view model.
- "Export all statements", which lives in the Chats overflow menu today, is
  reachable here only. `SettingsViewModel.exportEverything` already implements
  it, so the Chats copy is deleted rather than moved.

Sign out still calls `authGateViewModel::signOut`, which `MainShell` forwards
from the outer graph.

## Theme

Nothing forced. `ThemeChoice` (SYSTEM / LIGHT / DARK) already exists in
`SettingsUiState` and keeps working. Direction B's new surfaces become tokens
on the existing `LedgerColors`, which already carries `credit`, `debit`,
`pending`, `outgoingBubble`, `incomingBubble`:

- `heroStart`, `heroEnd` — the balance hero gradient.
- `creditContainer`, `debitContainer` — the direction badge fills behind the
  arrows in the feed.

Both get a light and a dark value in `Color.kt`, wired in `Theme.kt` next to
the existing pairs. No colour is hard-coded in a screen.

## Testing

Unit tests, which is where the existing 135 tests live:

- `TransactionsViewModel`: sign and wording per row for both parties of the
  same transaction; each filter chip; unconfirmed rows excluded from the hero
  but present in the list; day grouping across a midnight boundary; the peer
  name falling back correctly when a thread is local-only.
- Room: a query test for `observeRecent` ordering and limit.

Instrumented tests: none added. The migration tests are unaffected, since no
schema change is involved — the new DAO query reads existing columns.

Manual check on device: tab switching preserves scroll, the bar is absent in
chat and transaction detail, and back from each tab root exits.

## Risks

- `SettingsScreen` is a large working file and the split is the highest-risk
  edit in this change. It is behaviour-preserving; the sections keep the same
  view model calls.
- Moving `feature/home` renames the public entry point used by
  `PayChatNavHost`. It is a compile-time break, so nothing can silently drift.
- The in-memory filter and grouping run on every emission of a 100+ row list.
  If that shows up, the fix is to move grouping behind `derivedStateOf` or into
  the flow with `distinctUntilChanged`, not to add Paging.

## Amendment, 2026-09-10

The original decision said back from any tab root exits the app. The
implementation keeps the inner back stack instead, so back from Transactions or
Profile returns to Chats and only back from Chats exits. Two reasons to prefer
what was built: it is the Material pattern users already expect from a bottom
bar, and it is what keeps the Chats navigation entry — and therefore
`ChatsViewModel`, the only driver of `syncThreads()` and `syncBalances()` —
alive across tab switches. Popping tab roots inclusively would restart both
sync loops on every switch.
