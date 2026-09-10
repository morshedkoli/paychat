# Bottom Tab Shell Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the single Home screen with a three-tab shell — Chats, a new global Transactions feed, and a Profile tab that absorbs Settings.

**Architecture:** `PayChatNavHost` keeps one outer graph. A new `main` route hosts `MainShell`, which owns a `Scaffold` + `NavigationBar` and a second inner `NavHostController` for the three tab roots. Deep screens (chat, transaction detail, ledger, search, contacts) stay on the outer graph, so the bar cannot appear over them and each tab keeps its own back stack for free. Feed row derivation is a pure object so it is unit-testable without coroutines.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Navigation Compose, Hilt, Room, Flow. Tests: JUnit 4, Turbine, MockK, kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-09-10-bottom-tab-shell-design.md`

## Global Constraints

- Currency is BDT; format money only through `Money.format()` / `formatSigned()` / `formatPlain()`. Symbol `৳` lives in `Money.SYMBOL` — never hard-code it in a screen.
- Balance sign convention is `BalanceCalculator`'s: positive means the other person owes the viewer. Never re-derive it; call `BalanceCalculator.viewerIsPayer(entry, viewerUid)` and `BalanceCalculator.effectOn`.
- No colour literals in feature code. New surfaces go on `LedgerColors` in `ui/theme/`, with a light and a dark value, read via `PayChatTheme.ledger`.
- No new Room schema version. The new DAO query reads existing columns only, so `app/schemas/` stays as it is and `MigrationTest` is untouched.
- No Paging library. Pagination is a `LIMIT` raised in steps of 100.
- Theme stays a user choice (`ThemeChoice.SYSTEM/LIGHT/DARK`). Nothing is forced dark.
- Every task ends green: `./gradlew testDebugUnitTest` passes before the commit.
- Commit messages: plain prose, present tense, no `feat:`/`fix:` prefixes — match the existing log (e.g. "Stop the verify screen waiting forever for an answer").

---

## File Structure

| Path | Responsibility |
| --- | --- |
| `ui/theme/Color.kt` | Modify. Add light/dark values for the hero gradient and the direction badge fills. |
| `ui/theme/LedgerColors.kt` | Modify. Add `heroStart`, `heroEnd`, `creditContainer`, `debitContainer`. |
| `ui/theme/Theme.kt` | Modify. Wire the new tokens into both schemes. |
| `feature/transactions/TransactionFeed.kt` | Create. Pure row derivation, filtering and day grouping. No Android types. |
| `feature/transactions/TransactionsViewModel.kt` | Create. Combines flows, holds the filter, raises the page limit. |
| `feature/transactions/TransactionsScreen.kt` | Create. Hero, filter chips, grouped feed. |
| `data/local/dao/Daos.kt` | Modify. Add `TransactionDao.observeRecent(limit)`. |
| `data/transactions/TransactionRepository.kt` | Modify. Add `observeRecent(limit)` and expose `viewerUid`. |
| `feature/chats/ChatsScreen.kt` | Create from `feature/home/HomeScreen.kt`, minus balance card, export overflow, settings action. |
| `feature/chats/ChatsViewModel.kt` | Create from `feature/home/HomeViewModel.kt`, minus summary and export. |
| `feature/settings/SettingsSections.kt` | Create. Stateless section composables extracted from `SettingsScreen.kt`. |
| `feature/profile/ProfileScreen.kt` | Create. Centred identity block + the sections + dialogs. |
| `ui/nav/MainShell.kt` | Create. Scaffold, NavigationBar, inner NavHost. |
| `ui/nav/Destinations.kt` | Modify. Add `MAIN`, `CHATS`, `TRANSACTIONS`, `PROFILE`; drop `HOME`, `SETTINGS`. |
| `ui/nav/PayChatNavHost.kt` | Modify. `MAIN` replaces `HOME` and `SETTINGS`. |
| `feature/home/`, `feature/settings/SettingsScreen.kt` | Delete at the end of Task 8. |

Task order is bottom-up so the app compiles and tests pass at every commit: tokens → pure logic → data → new tab → moved tabs → shell → wiring → deletion.

---

### Task 1: Theme tokens for the new surfaces

**Files:**
- Modify: `app/src/main/java/com/paychat/paychat/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/paychat/paychat/ui/theme/LedgerColors.kt`
- Modify: `app/src/main/java/com/paychat/paychat/ui/theme/Theme.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `LedgerColors.heroStart`, `.heroEnd`, `.creditContainer`, `.debitContainer` — all `androidx.compose.ui.graphics.Color`, read at a call site as `PayChatTheme.ledger.heroStart`.

- [ ] **Step 1: Read the three files to match the existing naming**

Run: `sed -n '1,60p' app/src/main/java/com/paychat/paychat/ui/theme/Color.kt` and the same for `LedgerColors.kt` and lines 40-70 of `Theme.kt`. Existing pairs are named `CreditLight`/`CreditDark`, `DebitLight`/`DebitDark`, `PendingLight`/`PendingDark`, `OutgoingBubbleLight`/`OutgoingBubbleDark`. Follow that convention exactly.

- [ ] **Step 2: Add the eight colour values to `Color.kt`**

Append next to the existing ledger colours:

```kotlin
// The Transactions balance hero. A gradient, so two stops per theme.
val HeroStartLight = Color(0xFFDCF0E4)
val HeroEndLight = Color(0xFFE4ECF4)
val HeroStartDark = Color(0xFF1F4D38)
val HeroEndDark = Color(0xFF20303F)

// Fills behind the direction arrow on a feed row.
val CreditContainerLight = Color(0xFFD3ECDD)
val DebitContainerLight = Color(0xFFF7DAD7)
val CreditContainerDark = Color(0xFF1F4D38)
val DebitContainerDark = Color(0xFF4D2320)
```

- [ ] **Step 3: Add the four fields to `LedgerColors`**

```kotlin
data class LedgerColors(
    val credit: Color,
    val debit: Color,
    val pending: Color,
    val outgoingBubble: Color,
    val incomingBubble: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val creditContainer: Color,
    val debitContainer: Color,
)
```

Then extend the `LocalLedgerColors` default in the same file with the four `*Light` values, or the build breaks on the missing arguments.

- [ ] **Step 4: Wire both schemes in `Theme.kt`**

The file already has `val ledgerColors = if (darkTheme) { ... } else { ... }` around line 52. Add the four arguments to each branch — `*Dark` in the dark branch, `*Light` in the light branch.

- [ ] **Step 5: Compile**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL. No test yet — these are values with no behaviour.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/paychat/paychat/ui/theme/
git commit -m "Give the ledger palette a hero and container pair"
```

---

### Task 2: Pure feed derivation

This is where every rule the feed obeys lives, so it can be tested without a
ViewModel, a database or a coroutine — matching how `BalanceCalculatorTest`
and `LedgerStatementTest` are written today.

**Files:**
- Create: `app/src/main/java/com/paychat/paychat/feature/transactions/TransactionFeed.kt`
- Test: `app/src/test/java/com/paychat/paychat/feature/transactions/TransactionFeedTest.kt`

**Interfaces:**
- Consumes: `TransactionEntity`, `ThreadEntity`, `BalanceCalculator`, `Money`, `Timestamps` — all existing.
- Produces:
  - `enum class FeedFilter { ALL, YOU_GAVE, YOU_GOT }`
  - `data class FeedRow(val txnId: String, val threadId: String, val peerName: String, val amount: Money, val viewerIsPayer: Boolean, val note: String?, val createdAt: Long, val unconfirmed: Boolean, val pending: Boolean)`
  - `data class FeedDay(val label: String, val rows: List<FeedRow>)`
  - `object TransactionFeed { fun rows(...): List<FeedRow>; fun filter(rows, FeedFilter): List<FeedRow>; fun group(rows, now): List<FeedDay> }`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/paychat/paychat/feature/transactions/TransactionFeedTest.kt`:

```kotlin
package com.paychat.paychat.feature.transactions

import com.paychat.paychat.core.model.TxnDirection
import com.paychat.paychat.core.model.TxnStatus
import com.paychat.paychat.core.money.Money
import com.paychat.paychat.data.local.entity.ThreadEntity
import com.paychat.paychat.data.local.entity.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class TransactionFeedTest {

    private val me = "uid-me"
    private val them = "uid-them"

    private fun txn(
        id: String,
        createdBy: String,
        direction: TxnDirection,
        amountMinor: Long = 1000,
        status: TxnStatus = TxnStatus.ACCEPTED,
        unconfirmed: Boolean = false,
        createdAt: Long = 1_700_000_000_000,
        threadId: String = "thread-1",
        note: String? = null,
    ) = TransactionEntity(
        txnId = id,
        threadId = threadId,
        createdBy = createdBy,
        direction = direction,
        amountMinor = amountMinor,
        note = note,
        status = status,
        unconfirmed = unconfirmed,
        createdAt = createdAt,
    )

    private fun thread(
        threadId: String = "thread-1",
        peerName: String = "Rakib",
        peerPhone: String = "+8801712345678",
        isLocal: Boolean = false,
    ) = ThreadEntity(
        threadId = threadId,
        peerUid = them,
        peerName = peerName,
        peerPhone = peerPhone,
        isLocal = isLocal,
        lastMessageAt = 0,
    )

    @Test
    fun `the author of a sent transaction is the payer`() {
        val rows = TransactionFeed.rows(
            transactions = listOf(txn("t1", createdBy = me, direction = TxnDirection.SENT)),
            threads = listOf(thread()),
            viewerUid = me,
        )

        assertEquals(1, rows.size)
        assertTrue(rows.single().viewerIsPayer)
    }

    @Test
    fun `the same transaction reads the other way for the counterparty`() {
        val rows = TransactionFeed.rows(
            transactions = listOf(txn("t1", createdBy = them, direction = TxnDirection.SENT)),
            threads = listOf(thread()),
            viewerUid = me,
        )

        assertEquals(false, rows.single().viewerIsPayer)
    }

    @Test
    fun `a thread with no name falls back to the phone number`() {
        val rows = TransactionFeed.rows(
            transactions = listOf(txn("t1", createdBy = me, direction = TxnDirection.SENT)),
            threads = listOf(thread(peerName = "", peerPhone = "+8801712345678")),
            viewerUid = me,
        )

        assertEquals("+8801712345678", rows.single().peerName)
    }

    @Test
    fun `a transaction whose thread is missing is still listed`() {
        val rows = TransactionFeed.rows(
            transactions = listOf(txn("t1", createdBy = me, direction = TxnDirection.SENT)),
            threads = emptyList(),
            viewerUid = me,
        )

        assertEquals(1, rows.size)
        assertEquals("", rows.single().peerName)
    }

    @Test
    fun `you gave keeps only the rows the viewer paid`() {
        val rows = TransactionFeed.rows(
            transactions = listOf(
                txn("gave", createdBy = me, direction = TxnDirection.SENT),
                txn("got", createdBy = me, direction = TxnDirection.RECEIVED),
            ),
            threads = listOf(thread()),
            viewerUid = me,
        )

        assertEquals(listOf("gave"), TransactionFeed.filter(rows, FeedFilter.YOU_GAVE).map { it.txnId })
        assertEquals(listOf("got"), TransactionFeed.filter(rows, FeedFilter.YOU_GOT).map { it.txnId })
        assertEquals(2, TransactionFeed.filter(rows, FeedFilter.ALL).size)
    }

    @Test
    fun `an unconfirmed row written by someone else is flagged`() {
        val rows = TransactionFeed.rows(
            transactions = listOf(
                txn("t1", createdBy = them, direction = TxnDirection.SENT, unconfirmed = true)
            ),
            threads = listOf(thread()),
            viewerUid = me,
        )

        assertTrue(rows.single().unconfirmed)
    }

    @Test
    fun `a pending transaction is flagged so it can be muted`() {
        val rows = TransactionFeed.rows(
            transactions = listOf(
                txn("t1", createdBy = me, direction = TxnDirection.SENT, status = TxnStatus.PENDING)
            ),
            threads = listOf(thread()),
            viewerUid = me,
        )

        assertTrue(rows.single().pending)
    }

    @Test
    fun `rows either side of midnight land in different days`() {
        val lateYesterday = Calendar.getInstance().apply {
            set(2024, Calendar.MARCH, 10, 23, 50, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val earlyToday = Calendar.getInstance().apply {
            set(2024, Calendar.MARCH, 11, 0, 10, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val now = Calendar.getInstance().apply {
            set(2024, Calendar.MARCH, 11, 12, 0, 0)
        }.timeInMillis

        val rows = TransactionFeed.rows(
            transactions = listOf(
                txn("older", createdBy = me, direction = TxnDirection.SENT, createdAt = lateYesterday),
                txn("newer", createdBy = me, direction = TxnDirection.SENT, createdAt = earlyToday),
            ),
            threads = listOf(thread()),
            viewerUid = me,
        )

        val days = TransactionFeed.group(rows, now = now)

        assertEquals(2, days.size)
        assertEquals(listOf("newer"), days.first().rows.map { it.txnId })
        assertEquals(listOf("older"), days.last().rows.map { it.txnId })
    }

    @Test
    fun `rows are newest first within a day`() {
        val rows = TransactionFeed.rows(
            transactions = listOf(
                txn("older", createdBy = me, direction = TxnDirection.SENT, createdAt = 1_700_000_000_000),
                txn("newer", createdBy = me, direction = TxnDirection.SENT, createdAt = 1_700_000_060_000),
            ),
            threads = listOf(thread()),
            viewerUid = me,
        )

        val days = TransactionFeed.group(rows, now = 1_700_000_100_000)

        assertEquals(listOf("newer", "older"), days.single().rows.map { it.txnId })
    }
}
```

Note on the two entity constructors: open `data/local/entity/Entities.kt` and confirm the required (non-defaulted) parameters of `ThreadEntity` before running. If it needs more than the ones above, add them to the `thread()` helper — do not change the entity.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "*TransactionFeedTest*"`
Expected: FAIL — unresolved reference `TransactionFeed`, `FeedFilter`.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/paychat/paychat/feature/transactions/TransactionFeed.kt`:

```kotlin
package com.paychat.paychat.feature.transactions

import com.paychat.paychat.core.ledger.BalanceCalculator
import com.paychat.paychat.core.money.Money
import com.paychat.paychat.data.local.entity.ThreadEntity
import com.paychat.paychat.data.local.entity.TransactionEntity
import com.paychat.paychat.ui.components.Timestamps

/** Which side of the ledger the feed is showing. */
enum class FeedFilter { ALL, YOU_GAVE, YOU_GOT }

/**
 * One transaction as the feed shows it. Everything that depends on who is
 * reading has already been resolved here, so the screen never has to know the
 * sign convention.
 */
data class FeedRow(
    val txnId: String,
    val threadId: String,
    val peerName: String,
    val amount: Money,
    /** True when the viewer handed the money over, whoever wrote the row. */
    val viewerIsPayer: Boolean,
    val note: String?,
    val createdAt: Long,
    /** Inherited history this viewer has not reviewed yet. */
    val unconfirmed: Boolean,
    /** Waiting on the other person, so it does not count yet. */
    val pending: Boolean,
)

data class FeedDay(val label: String, val rows: List<FeedRow>)

/**
 * Turns stored transactions into the feed. Pure on purpose: the sign, the
 * wording and the day boundaries are the parts worth testing, and none of them
 * need Android or a database.
 */
object TransactionFeed {

    fun rows(
        transactions: List<TransactionEntity>,
        threads: List<ThreadEntity>,
        viewerUid: String,
    ): List<FeedRow> {
        val nameByThread = threads.associate {
            it.threadId to it.peerName.ifBlank { it.peerPhone }
        }
        return transactions.map { txn ->
            FeedRow(
                txnId = txn.txnId,
                threadId = txn.threadId,
                // A transaction can arrive before its thread row does, so a
                // missing name is normal and must not drop the row.
                peerName = nameByThread[txn.threadId].orEmpty(),
                amount = Money(txn.amountMinor),
                viewerIsPayer = BalanceCalculator.viewerIsPayer(txn, viewerUid),
                note = txn.note,
                createdAt = txn.createdAt,
                unconfirmed = txn.unconfirmed && txn.createdBy != viewerUid,
                pending = !txn.status.isTerminal,
            )
        }
    }

    fun filter(rows: List<FeedRow>, filter: FeedFilter): List<FeedRow> = when (filter) {
        FeedFilter.ALL -> rows
        FeedFilter.YOU_GAVE -> rows.filter { it.viewerIsPayer }
        FeedFilter.YOU_GOT -> rows.filter { !it.viewerIsPayer }
    }

    /** Newest day first, and newest row first inside each day. */
    fun group(rows: List<FeedRow>, now: Long = System.currentTimeMillis()): List<FeedDay> =
        rows.sortedByDescending { it.createdAt }
            .groupBy { Timestamps.daySeparator(it.createdAt, now) }
            .map { (label, dayRows) -> FeedDay(label, dayRows) }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "*TransactionFeedTest*"`
Expected: PASS, 9 tests.

If the midnight test fails because `Timestamps.daySeparator` returns the same label for both stamps, read the function — it labels by "Today"/"Yesterday"/date. Two stamps on different calendar days cannot share a label, so a failure here means the test's `now` is wrong, not the implementation.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/paychat/paychat/feature/transactions/TransactionFeed.kt app/src/test/java/com/paychat/paychat/feature/transactions/TransactionFeedTest.kt
git commit -m "Work out the feed rows away from the screen"
```

---

### Task 3: A query for every transaction

**Files:**
- Modify: `app/src/main/java/com/paychat/paychat/data/local/dao/Daos.kt` (in `TransactionDao`, next to `observeThread` around line 141)
- Modify: `app/src/main/java/com/paychat/paychat/data/transactions/TransactionRepository.kt`
- Test: `app/src/test/java/com/paychat/paychat/data/transactions/TransactionQueryTest.kt`

**Interfaces:**
- Consumes: `TransactionDao`, `AuthRepository.currentUid`.
- Produces:
  - `TransactionDao.observeRecent(limit: Int): Flow<List<TransactionEntity>>`
  - `TransactionRepository.observeRecent(limit: Int): Flow<List<TransactionEntity>>`
  - `TransactionRepository.viewerUid(): String?` — delegates to `auth.currentUid`, so a ViewModel does not need `AuthRepository` injected.

- [ ] **Step 1: Write the failing test**

Room's own DAO needs a device, and the instrumented suite is not where this belongs. Test the repository's delegation with MockK instead, which is what `testImplementation(libs.mockk)` is there for.

Create `app/src/test/java/com/paychat/paychat/data/transactions/TransactionQueryTest.kt`:

```kotlin
package com.paychat.paychat.data.transactions

import app.cash.turbine.test
import com.paychat.paychat.core.model.TxnDirection
import com.paychat.paychat.core.model.TxnStatus
import com.paychat.paychat.data.local.dao.TransactionDao
import com.paychat.paychat.data.local.entity.TransactionEntity
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionQueryTest {

    private val dao: TransactionDao = mockk(relaxed = true)

    private fun entity(id: String) = TransactionEntity(
        txnId = id,
        threadId = "thread-1",
        createdBy = "uid-me",
        direction = TxnDirection.SENT,
        amountMinor = 1000,
        status = TxnStatus.ACCEPTED,
        createdAt = 1_700_000_000_000,
    )

    @Test
    fun `the recent feed asks the dao for the page it was given`() = runTest {
        every { dao.observeRecent(100) } returns flowOf(listOf(entity("t1")))

        TransactionQuery(dao).observeRecent(100).test {
            assertEquals(listOf("t1"), awaitItem().map { it.txnId })
            awaitComplete()
        }

        verify { dao.observeRecent(100) }
    }
}
```

`TransactionQuery` is a one-method seam so this stays a unit test — `TransactionRepository` takes seven collaborators including Firestore and is not constructible here.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "*TransactionQueryTest*"`
Expected: FAIL — unresolved reference `TransactionQuery`, and `observeRecent` is not a member of `TransactionDao`.

- [ ] **Step 3: Add the DAO query**

In `Daos.kt`, inside `TransactionDao`, directly after `observeThread`/`forThread`:

```kotlin
    /**
     * Every transaction, newest first, capped. The cap is raised as the feed is
     * scrolled; there is no cursor, because the whole page is re-read from Room
     * on any change anyway.
     */
    @Query("SELECT * FROM transactions ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TransactionEntity>>
```

- [ ] **Step 4: Add the seam and the repository method**

Create `app/src/main/java/com/paychat/paychat/data/transactions/TransactionQuery.kt`:

```kotlin
package com.paychat.paychat.data.transactions

import com.paychat.paychat.data.local.dao.TransactionDao
import com.paychat.paychat.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read-only access to the transaction table for screens that span every
 * conversation. Kept apart from [TransactionRepository], which owns writing and
 * syncing and needs Firestore to exist.
 */
@Singleton
class TransactionQuery @Inject constructor(private val dao: TransactionDao) {

    fun observeRecent(limit: Int): Flow<List<TransactionEntity>> = dao.observeRecent(limit)
}
```

Then in `TransactionRepository`, next to `observeBalances` (around line 323), add:

```kotlin
    /** The signed-in user, or null when the session has already gone. */
    fun viewerUid(): String? = auth.currentUid
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "*TransactionQueryTest*"`
Expected: PASS, 1 test.

- [ ] **Step 6: Run the whole suite and commit**

```bash
./gradlew testDebugUnitTest
git add app/src/main/java/com/paychat/paychat/data/
git add app/src/test/java/com/paychat/paychat/data/transactions/TransactionQueryTest.kt
git commit -m "Read the transaction table across every conversation"
```

---

### Task 4: The Transactions view model

**Files:**
- Create: `app/src/main/java/com/paychat/paychat/feature/transactions/TransactionsViewModel.kt`
- Test: `app/src/test/java/com/paychat/paychat/feature/transactions/TransactionsViewModelTest.kt`

**Interfaces:**
- Consumes: `TransactionQuery.observeRecent`, `TransactionRepository.viewerUid`/`observeBalances`, `ThreadsRepository.observeThreads`, `TransactionFeed`, `FeedFilter`, `BalanceCalculator.summarise`.
- Produces:
  - `data class TransactionsUiState(val summary: BalanceSummary, val people: Int, val days: List<FeedDay>, val filter: FeedFilter, val loading: Boolean)`
  - `class TransactionsViewModel` with `state: StateFlow<TransactionsUiState>`, `fun setFilter(FeedFilter)`, `fun loadMore()`

- [ ] **Step 1: Confirm the thread flow's element type**

Run: `grep -n "fun observeThreads" -A 3 app/src/main/java/com/paychat/paychat/data/chat/ThreadsRepository.kt`

The test below stubs it, so its element type must match. If it emits something other than `List<ThreadEntity>`, adjust the stub and the `TransactionFeed.rows` call to map into `ThreadEntity` — do not change `TransactionFeed`, which Task 2 already tested.

- [ ] **Step 2: Write the failing test**

Create `app/src/test/java/com/paychat/paychat/feature/transactions/TransactionsViewModelTest.kt`:

```kotlin
package com.paychat.paychat.feature.transactions

import app.cash.turbine.test
import com.paychat.paychat.core.model.TxnDirection
import com.paychat.paychat.core.model.TxnStatus
import com.paychat.paychat.data.chat.ThreadsRepository
import com.paychat.paychat.data.local.entity.ThreadBalanceEntity
import com.paychat.paychat.data.local.entity.ThreadEntity
import com.paychat.paychat.data.local.entity.TransactionEntity
import com.paychat.paychat.data.transactions.TransactionQuery
import com.paychat.paychat.data.transactions.TransactionRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TransactionsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val query: TransactionQuery = mockk()
    private val transactions: TransactionRepository = mockk()
    private val threads: ThreadsRepository = mockk()

    private val me = "uid-me"

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun txn(id: String, direction: TxnDirection, amountMinor: Long = 1000) =
        TransactionEntity(
            txnId = id,
            threadId = "thread-1",
            createdBy = me,
            direction = direction,
            amountMinor = amountMinor,
            status = TxnStatus.ACCEPTED,
            createdAt = 1_700_000_000_000,
        )

    private fun viewModel(): TransactionsViewModel {
        every { transactions.viewerUid() } returns me
        every { query.observeRecent(any()) } returns flowOf(
            listOf(
                txn("gave", TxnDirection.SENT, 240000),
                txn("got", TxnDirection.RECEIVED, 85000),
            )
        )
        every { threads.observeThreads() } returns flowOf(
            listOf(
                ThreadEntity(
                    threadId = "thread-1",
                    peerUid = "uid-them",
                    peerName = "Rakib",
                    peerPhone = "+8801712345678",
                    isLocal = false,
                    lastMessageAt = 0,
                )
            )
        )
        every { transactions.observeBalances() } returns flowOf(
            listOf(ThreadBalanceEntity(threadId = "thread-1", amountMinor = 155000))
        )
        return TransactionsViewModel(query, transactions, threads)
    }

    @Test
    fun `the feed starts with every row grouped by day`() = runTest {
        viewModel().state.test {
            val loaded = awaitItem().let { if (it.loading) awaitItem() else it }
            assertEquals(1, loaded.days.size)
            assertEquals(2, loaded.days.single().rows.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the hero counts one person per non-zero balance`() = runTest {
        viewModel().state.test {
            val loaded = awaitItem().let { if (it.loading) awaitItem() else it }
            assertEquals(1, loaded.people)
            assertEquals(155000, loaded.summary.net.minor)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `switching the filter narrows the feed`() = runTest {
        val model = viewModel()
        model.state.test {
            awaitItem().let { if (it.loading) awaitItem() else it }
            model.setFilter(FeedFilter.YOU_GAVE)
            val filtered = awaitItem()
            assertEquals(FeedFilter.YOU_GAVE, filtered.filter)
            assertEquals(listOf("gave"), filtered.days.flatMap { day -> day.rows.map { it.txnId } })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loading more asks for a bigger page`() = runTest {
        val model = viewModel()
        model.state.test {
            awaitItem().let { if (it.loading) awaitItem() else it }
            model.loadMore()
            cancelAndIgnoreRemainingEvents()
        }

        io.mockk.verify { query.observeRecent(200) }
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "*TransactionsViewModelTest*"`
Expected: FAIL — unresolved reference `TransactionsViewModel`.

- [ ] **Step 4: Write the implementation**

Create `app/src/main/java/com/paychat/paychat/feature/transactions/TransactionsViewModel.kt`:

```kotlin
package com.paychat.paychat.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paychat.paychat.core.ledger.BalanceCalculator
import com.paychat.paychat.core.ledger.BalanceSummary
import com.paychat.paychat.core.money.Money
import com.paychat.paychat.data.chat.ThreadsRepository
import com.paychat.paychat.data.transactions.TransactionQuery
import com.paychat.paychat.data.transactions.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransactionsUiState(
    val summary: BalanceSummary = BalanceSummary.EMPTY,
    /** How many conversations still have money in them. */
    val people: Int = 0,
    val days: List<FeedDay> = emptyList(),
    val filter: FeedFilter = FeedFilter.ALL,
    val loading: Boolean = true,
)

/** How many rows the feed reads, and how much further each request goes. */
private const val PAGE = 100

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val query: TransactionQuery,
    private val transactions: TransactionRepository,
    private val threads: ThreadsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TransactionsUiState())
    val state: StateFlow<TransactionsUiState> = _state.asStateFlow()

    private val limit = MutableStateFlow(PAGE)
    private val filter = MutableStateFlow(FeedFilter.ALL)

    init {
        val viewerUid = transactions.viewerUid()

        viewModelScope.launch {
            combine(
                limit.flatMapLatest { query.observeRecent(it) },
                threads.observeThreads(),
                transactions.observeBalances(),
                filter,
            ) { txns, threadRows, balances, chosen ->
                // No session means nothing to attribute rows to, so show none
                // rather than guess a side.
                val rows = if (viewerUid == null) emptyList()
                else TransactionFeed.rows(txns, threadRows, viewerUid)

                val balanceAmounts = balances.map { Money(it.amountMinor) }
                TransactionsUiState(
                    summary = BalanceCalculator.summarise(balanceAmounts),
                    people = balanceAmounts.count { !it.isZero },
                    days = TransactionFeed.group(TransactionFeed.filter(rows, chosen)),
                    filter = chosen,
                    loading = false,
                )
            }.collect { next -> _state.update { next } }
        }
    }

    fun setFilter(next: FeedFilter) {
        filter.value = next
    }

    /** Reads one page further. Harmless to call at the end of the list. */
    fun loadMore() {
        limit.update { it + PAGE }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "*TransactionsViewModelTest*"`
Expected: PASS, 4 tests.

If `threads.observeThreads()` turned out to emit a type other than `List<ThreadEntity>`, map it inside the `combine` before passing it on.

- [ ] **Step 6: Run the whole suite and commit**

```bash
./gradlew testDebugUnitTest
git add app/src/main/java/com/paychat/paychat/feature/transactions/TransactionsViewModel.kt app/src/test/java/com/paychat/paychat/feature/transactions/TransactionsViewModelTest.kt
git commit -m "Hold the feed, the filter and the hero in one state"
```

---

### Task 5: The Transactions screen

**Files:**
- Create: `app/src/main/java/com/paychat/paychat/feature/transactions/TransactionsScreen.kt`

**Interfaces:**
- Consumes: `TransactionsViewModel`, `TransactionsUiState`, `FeedDay`, `FeedRow`, `FeedFilter`, `PayChatTheme.ledger`.
- Produces: `@Composable fun TransactionsScreen(onOpenTransaction: (String) -> Unit, viewModel: TransactionsViewModel = hiltViewModel())`

No test. This is layout with no branching logic worth asserting; the rules it
renders were tested in Tasks 2 and 4. It is verified by eye on the device in
Task 9.

- [ ] **Step 1: Read a screen to copy the house style**

Run: `sed -n '1,140p' app/src/main/java/com/paychat/paychat/feature/ledger/LedgerScreen.kt`

Match how it does `collectAsStateWithLifecycle`, `LazyColumn`, list items and money colouring. Amounts use `PayChatTheme.ledger.credit` / `.debit` and `AmountStyle` / `AmountLargeStyle` from `ui/theme/`.

- [ ] **Step 2: Write the screen**

Create `app/src/main/java/com/paychat/paychat/feature/transactions/TransactionsScreen.kt`. The structure, top to bottom inside a `Scaffold`'s `LazyColumn`:

```kotlin
@Composable
fun TransactionsScreen(
    onOpenTransaction: (String) -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(Modifier.fillMaxSize()) {
        item { BalanceHero(state.summary, state.people) }
        item { FilterChips(state.filter, viewModel::setFilter) }

        if (state.days.isEmpty() && !state.loading) {
            item {
                EmptyState(
                    title = "No transactions yet",
                    message = "Money you record in a chat shows up here.",
                )
            }
        }

        state.days.forEach { day ->
            item(key = "day-${day.label}") { DayHeading(day.label) }
            items(day.rows, key = { it.txnId }) { row ->
                FeedRowItem(row, onClick = { onOpenTransaction(row.txnId) })
            }
        }

        // Reaching the end is the request for another page.
        item {
            LaunchedEffect(state.days.size) { viewModel.loadMore() }
        }
    }
}
```

Then these private composables in the same file:

- `BalanceHero(summary: BalanceSummary, people: Int)` — a `Card`/`Surface` with `Modifier.background(Brush.linearGradient(listOf(PayChatTheme.ledger.heroStart, PayChatTheme.ledger.heroEnd)))`, rounded 16.dp, 16.dp padding. Label "You will get, net" when `summary.net.isPositive`, "You will give, net" when negative, "All settled" when zero. `summary.net.abs().format()` in `AmountLargeStyle`. Below it a `Row` of three: "Get" `summary.willGet.format()`, "Give" `summary.willGive.format()`, "People" `people.toString()`.
- `FilterChips(current: FeedFilter, onChoose: (FeedFilter) -> Unit)` — a `Row` of three `FilterChip`s, labels "All", "You gave", "You got".
- `DayHeading(label: String)` — small uppercase label, `MaterialTheme.typography.labelSmall`, 8.dp vertical padding.
- `FeedRowItem(row: FeedRow, onClick: () -> Unit)` — a `ListItem` with `Modifier.clickable(onClick = onClick)`. Leading: a 40.dp `Box`, `CircleShape`, background `PayChatTheme.ledger.creditContainer` when `row.viewerIsPayer` else `debitContainer`, holding `Icons.Default.ArrowUpward` / `ArrowDownward` tinted `ledger.credit` / `ledger.debit`. Headline `row.peerName`. Supporting: `row.note` when set, plus "Awaiting review" when `row.unconfirmed`, "Waiting for them" when `row.pending`. Trailing: amount in `AmountStyle`, prefixed `+` / `−`, coloured `ledger.credit` / `ledger.debit`, dimmed via `LocalContentColor` alpha when `row.unconfirmed || row.pending`.

Sign, colour and wording all read `row.viewerIsPayer` — never recompute from `direction` or `createdBy` here.

- [ ] **Step 3: Compile**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run the whole suite and commit**

```bash
./gradlew testDebugUnitTest
git add app/src/main/java/com/paychat/paychat/feature/transactions/TransactionsScreen.kt
git commit -m "Show every transaction under a balance hero"
```

---

### Task 6: Chats, without the money summary

**Files:**
- Create: `app/src/main/java/com/paychat/paychat/feature/chats/ChatsScreen.kt` (from `feature/home/HomeScreen.kt`)
- Create: `app/src/main/java/com/paychat/paychat/feature/chats/ChatsViewModel.kt` (from `feature/home/HomeViewModel.kt`)

`feature/home/` is deleted in Task 8, once nothing references it.

**Interfaces:**
- Consumes: `ThreadsRepository`, `TransactionRepository` (unchanged calls).
- Produces:
  - `data class ChatsUiState(val threads: List<ThreadRow>, val inheritedThreadIds: List<String>, val inheritedCount: Int, val loading: Boolean, val error: String?)`
  - `ChatsViewModel` with `state`, `fun errorShown()`
  - `@Composable fun ChatsScreen(onOpenThread: (String) -> Unit, onNewChat: () -> Unit, onSearch: () -> Unit, onReviewInherited: (String) -> Unit, viewModel: ChatsViewModel = hiltViewModel())`
  - `data class ThreadRow(...)` moves here unchanged.

- [ ] **Step 1: Copy both files across with git so history follows**

```bash
git mv app/src/main/java/com/paychat/paychat/feature/home/HomeScreen.kt app/src/main/java/com/paychat/paychat/feature/chats/ChatsScreen.kt
git mv app/src/main/java/com/paychat/paychat/feature/home/HomeViewModel.kt app/src/main/java/com/paychat/paychat/feature/chats/ChatsViewModel.kt
```

- [ ] **Step 2: Rename inside the view model and drop what moved to other tabs**

In `ChatsViewModel.kt`: package becomes `com.paychat.paychat.feature.chats`; `HomeUiState` → `ChatsUiState`; `HomeViewModel` → `ChatsViewModel`. Then delete, because the Transactions tab and the Profile tab own them now:

- the `summary` field, and the `BalanceCalculator.summarise(...)` line that fills it
- the `exporting` and `statement` fields, the whole `exportEverything()` function, `statementShared()`
- the `StatementExporter` constructor parameter and its import
- the now-unused imports: `android.net.Uri`, `BalanceCalculator`, `BalanceSummary`, `StatementExporter`, `userMessage`

Keep everything else exactly as it is — `observeBalances()` still feeds each row's per-thread balance, the inherited flow, both sync launches, and `ThreadRow`.

- [ ] **Step 3: Rename inside the screen and drop the chrome that moved**

In `ChatsScreen.kt`: package becomes `com.paychat.paychat.feature.chats`; `HomeScreen` → `ChatsScreen`; `HomeViewModel` → `ChatsViewModel`. Then remove:

- the `onSettings` parameter and the `IconButton` that called it — Profile is a tab now
- the overflow `IconButton`, its `DropdownMenu`, the `showMenu` state, and the export `DropdownMenuItem`
- the `BalanceSummaryCard(...)` call and its private composable
- the `LaunchedEffect(state.statement)` block and the `shareStatement` import
- imports left unused: `Icons.Default.MoreVert`, `Icons.Default.Settings`, `DropdownMenu`, `DropdownMenuItem`, `AmountLargeStyle`, `Money` if nothing else uses it

Keep the search action, the FAB, the inherited banner, and the thread list.

- [ ] **Step 4: Point the old call site at the new names so the module still builds**

In `ui/nav/PayChatNavHost.kt`, change the `HomeScreen` import to `com.paychat.paychat.feature.chats.ChatsScreen`, call `ChatsScreen(...)`, and delete the `onSettings = ...` argument. Leave the `Routes.HOME` string alone — Task 7 renames it.

- [ ] **Step 5: Compile and run the suite**

Run: `./gradlew compileDebugKotlin testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 135 + new tests passing.

- [ ] **Step 6: Commit**

```bash
git add -A app/src/main/java/com/paychat/paychat/feature/ app/src/main/java/com/paychat/paychat/ui/nav/PayChatNavHost.kt
git commit -m "Leave the conversation list to the conversations"
```

---

### Task 7: Profile takes over Settings

**Files:**
- Create: `app/src/main/java/com/paychat/paychat/feature/settings/SettingsSections.kt`
- Create: `app/src/main/java/com/paychat/paychat/feature/profile/ProfileScreen.kt`
- Modify: `app/src/main/java/com/paychat/paychat/feature/settings/SettingsScreen.kt` (deleted in Task 8)

**Interfaces:**
- Consumes: `SettingsViewModel` unchanged — `state`, `setName`, `setPhoto`, `setTheme`, `setAppLockEnabled`, `exportEverything`, `exportData`, `deleteAccount`, `statementShared`, `dataFileShared`, `messageShown`.
- Produces:
  - In `SettingsSections.kt`: `@Composable internal fun AccountSection(state: SettingsUiState, onToggleAppLock: (Boolean) -> Unit)`, `AppearanceSection(state, onChooseTheme: (ThemeChoice) -> Unit)`, `DataSection(state, onExportStatements: () -> Unit, onExportData: () -> Unit)`, `DangerSection(onSignOut: () -> Unit, onDeleteAccount: () -> Unit)`, plus `internal fun SectionHeading`, `ThemeRow`, `NameDialog` moved as-is.
  - `@Composable fun ProfileScreen(onSignOut: () -> Unit, viewModel: SettingsViewModel = hiltViewModel())`

- [ ] **Step 1: Read the whole current screen**

Run: `cat app/src/main/java/com/paychat/paychat/feature/settings/SettingsScreen.kt`

It is 388 lines: a `Scaffold` with a top bar and back arrow, a `ProfileHeader`, an app-lock `ListItem` with a `Switch`, a theme row, two export rows, sign-out and delete rows, two confirm dialogs, and the private helpers. The split must not change any behaviour — same view model calls, same copy, same dialogs.

- [ ] **Step 2: Extract the sections, unchanged**

Create `SettingsSections.kt` and move the row groups into the four `internal` composables named above, along with `SectionHeading`, `ThemeRow`, `ThemeChoice.label()` and `NameDialog` (change `private` to `internal`). Copy the copy strings verbatim — "One PDF covering every conversation with a closing balance.", "Every conversation, message and transaction, as JSON.", "This device stops receiving notifications for the account.", "Permanent. Your profile and your number are released." Take the `ProfileHeader` composable too; `ProfileScreen` will use it as the basis of its identity block.

- [ ] **Step 3: Write the Profile screen**

Create `app/src/main/java/com/paychat/paychat/feature/profile/ProfileScreen.kt`:

```kotlin
@Composable
fun ProfileScreen(
    onSignOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Keep the existing LaunchedEffects for state.statement, state.dataFile,
    // state.message and state.deleted, copied from SettingsScreen unchanged.

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
        ) {
            ProfileIdentity(state = state, onPickPhoto = viewModel::setPhoto, onRename = viewModel::setName)
            AccountSection(state, onToggleAppLock = viewModel::setAppLockEnabled)
            AppearanceSection(state, onChooseTheme = viewModel::setTheme)
            DataSection(
                state,
                onExportStatements = viewModel::exportEverything,
                onExportData = viewModel::exportData,
            )
            DangerSection(
                onSignOut = { confirmSignOut = true },
                onDeleteAccount = { confirmDelete = true },
            )
        }
    }

    // Both confirm dialogs, copied from SettingsScreen unchanged, with the
    // sign-out one calling onSignOut().
}
```

`ProfileIdentity` is direction B: centred column, 96.dp `Avatar`, name in `titleMedium`, phone in `bodySmall` at reduced alpha, and an "Edit" `TextButton` opening the existing `NameDialog`. There is no top bar and no back arrow — a tab root has nothing to go back to.

- [ ] **Step 4: Reduce `SettingsScreen.kt` to nothing it still owns**

Leave the file in place but strip it to the sections call sequence so the module still compiles while `Routes.SETTINGS` exists. Task 8 deletes the route, the file, and this shim in one commit.

- [ ] **Step 5: Compile and run the suite**

Run: `./gradlew compileDebugKotlin testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests passing.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/paychat/paychat/feature/settings/ app/src/main/java/com/paychat/paychat/feature/profile/
git commit -m "Give the settings rows a profile to live on"
```

---

### Task 8: The shell, the routes, and the deletions

The commit that flips the app over. Everything before this was additive.

**Files:**
- Create: `app/src/main/java/com/paychat/paychat/ui/nav/MainShell.kt`
- Modify: `app/src/main/java/com/paychat/paychat/ui/nav/Destinations.kt`
- Modify: `app/src/main/java/com/paychat/paychat/ui/nav/PayChatNavHost.kt`
- Delete: `app/src/main/java/com/paychat/paychat/feature/settings/SettingsScreen.kt`
- Delete: `app/src/main/java/com/paychat/paychat/feature/home/` (should already be empty after Task 6)

**Interfaces:**
- Consumes: `ChatsScreen`, `TransactionsScreen`, `ProfileScreen`, `Routes`.
- Produces: `@Composable fun MainShell(onOpenOuter: (String) -> Unit, onSignOut: () -> Unit)`, and `Routes.MAIN`, `Routes.CHATS`, `Routes.TRANSACTIONS`, `Routes.PROFILE`.

- [ ] **Step 1: Change the routes**

In `Destinations.kt`, replace `const val HOME = "home"` and `const val SETTINGS = "settings"` with:

```kotlin
    /** The signed in landing destination: a tab shell, not a screen. */
    const val MAIN = "main"

    // Tab roots. These live in the shell's own graph, never the outer one, so
    // the bottom bar cannot appear over a full screen destination.
    const val CHATS = "chats"
    const val TRANSACTIONS = "transactions"
    const val PROFILE = "profile"
```

Leave `AUTH_ROUTES` as it is.

- [ ] **Step 2: Write the shell**

Create `app/src/main/java/com/paychat/paychat/ui/nav/MainShell.kt`:

```kotlin
package com.paychat.paychat.ui.nav

/**
 * The signed in shell: three tabs over their own graph.
 *
 * The tab roots are deliberately in a nested graph rather than the outer one.
 * That gives each tab its own back stack and scroll position, and it makes it
 * impossible for the bottom bar to show up over a chat or a transaction, which
 * both want the bottom edge for themselves.
 */
@Composable
fun MainShell(
    onOpenOuter: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    val tabs = navController(...)   // rememberNavController()
    val entry by tabs.currentBackStackEntryAsState()
    val current = entry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                TabItem(Routes.CHATS, "Chats", Icons.Default.Chat, current, tabs)
                TabItem(Routes.TRANSACTIONS, "Transactions", Icons.Default.SwapVert, current, tabs)
                TabItem(Routes.PROFILE, "Profile", Icons.Default.Person, current, tabs)
            }
        },
    ) { inner ->
        NavHost(
            navController = tabs,
            startDestination = Routes.CHATS,
            modifier = Modifier.padding(inner),
        ) {
            composable(Routes.CHATS) {
                ChatsScreen(
                    onOpenThread = { onOpenOuter(Routes.chat(it)) },
                    onNewChat = { onOpenOuter(Routes.CONTACTS) },
                    onSearch = { onOpenOuter(Routes.SEARCH) },
                    onReviewInherited = { onOpenOuter(Routes.inheritedReview(it)) },
                )
            }
            composable(Routes.TRANSACTIONS) {
                TransactionsScreen(
                    onOpenTransaction = { onOpenOuter(Routes.transactionDetail(it)) },
                )
            }
            composable(Routes.PROFILE) {
                ProfileScreen(onSignOut = onSignOut)
            }
        }
    }
}
```

`TabItem` is a private helper taking `(route, label, icon, current, controller)` and rendering a `NavigationBarItem` with `selected = current == route` and this onClick, which is what makes each tab remember where it was:

```kotlin
onClick = {
    controller.navigate(route) {
        popUpTo(controller.graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
```

- [ ] **Step 3: Wire the outer graph**

In `PayChatNavHost.kt`:

- `LaunchedEffect(gate)`: `AuthGate.SIGNED_IN -> navController.toTopLevel(Routes.MAIN)`
- Replace the whole `composable(Routes.HOME) { HomeScreen(...) }` block with:

```kotlin
            composable(Routes.MAIN) {
                MainShell(
                    onOpenOuter = { route -> navController.navigate(route) },
                    onSignOut = authGateViewModel::signOut,
                )
            }
```

- Delete the `composable(Routes.SETTINGS) { SettingsScreen(...) }` block and the `SettingsScreen` import.
- Delete the now-unused `ChatsScreen` import, since only the shell calls it.

- [ ] **Step 4: Delete what nothing points at**

```bash
git rm app/src/main/java/com/paychat/paychat/feature/settings/SettingsScreen.kt
git rm -r --ignore-unmatch app/src/main/java/com/paychat/paychat/feature/home
grep -rn "Routes.HOME\|Routes.SETTINGS\|HomeScreen\|HomeViewModel\|SettingsScreen(" app/src/main/java app/src/test/java
```

The grep must print nothing. Anything it prints is a reference the compiler would have caught anyway — fix it now.

- [ ] **Step 5: Compile and run the suite**

Run: `./gradlew compileDebugKotlin testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests passing.

- [ ] **Step 6: Commit**

```bash
git add -A app/src/main/java/com/paychat/paychat/
git commit -m "Put the app on three tabs"
```

---

### Task 9: Check it on the device

**Files:** none.

- [ ] **Step 1: Confirm a device is attached**

Run: `D:\android-sdk\platform-tools\adb.exe devices`
Expected: one line ending `device`. If the list is empty, the phone's USB debugging has dropped — reconnect it before going on.

- [ ] **Step 2: Install**

Run: `./gradlew installDebug`
Expected: `Installed on 1 device.`

- [ ] **Step 3: Walk the checks**

- All three tabs appear; the starting tab is Chats.
- Chats has no balance card, no settings icon, no overflow menu; search and the FAB still work.
- Transactions shows the hero with net / get / give / people, and the feed grouped by day. Each filter chip narrows the list.
- A feed row opens the transaction detail screen, and **the bottom bar is gone** there.
- Opening a chat from Chats also hides the bar; its ledger does too.
- Scroll Chats down, switch to Transactions, switch back: the scroll position is where you left it.
- Profile shows the identity block plus app lock, theme, both exports, sign out, delete.
- Change the theme on Profile to Light and to Dark: the hero gradient and the row badges both stay legible.
- Back from each tab root exits the app.

- [ ] **Step 4: Run everything once more**

```bash
./gradlew testDebugUnitTest connectedDebugAndroidTest
```

Expected: unit tests pass; the 3 migration tests still pass, since no schema changed.

- [ ] **Step 5: Commit anything the walkthrough forced**

If the walkthrough found nothing, there is nothing to commit — say so rather than making an empty commit.

---

## Self-review notes

Spec coverage checked section by section: navigation (Task 8), route changes (Task 8), file moves (Tasks 6, 7, 8), the DAO query and pagination (Task 3), sign/wording/filtering (Tasks 2, 4), hero and people count (Tasks 4, 5), day headers (Tasks 2, 5), unconfirmed handling (Tasks 2, 5), Profile split and export ownership (Task 7), theme tokens (Task 1), unit tests (Tasks 2, 3, 4), device checks (Task 9), and the deliberate omissions (in-tab search, date pickers, per-filter export) are absent from every task.

Two places where an executor must read before writing, both called out inline rather than guessed: `ThreadEntity`'s required constructor parameters (Task 2 Step 1) and the element type of `ThreadsRepository.observeThreads()` (Task 4 Step 1).
