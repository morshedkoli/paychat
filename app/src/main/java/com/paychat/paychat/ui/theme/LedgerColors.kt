package com.paychat.paychat.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colours that carry ledger meaning and therefore do not belong in the
 * Material colour scheme.
 */
@Immutable
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

val LocalLedgerColors = staticCompositionLocalOf {
    LedgerColors(
        credit = CreditLight,
        debit = DebitLight,
        pending = PendingLight,
        outgoingBubble = OutgoingBubbleLight,
        incomingBubble = IncomingBubbleLight,
        heroStart = HeroStartLight,
        heroEnd = HeroEndLight,
        creditContainer = CreditContainerLight,
        debitContainer = DebitContainerLight,
    )
}

object PayChatTheme {
    val ledger: LedgerColors
        @Composable @ReadOnlyComposable
        get() = LocalLedgerColors.current

    val typography
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography
}
