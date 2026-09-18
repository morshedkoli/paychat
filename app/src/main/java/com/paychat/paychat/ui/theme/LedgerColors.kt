package com.paychat.paychat.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colours that carry ledger and WhatsApp chat semantics.
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
    val chatBackground: Color = Color.Transparent,
    val readTick: Color = WhatsAppBlueTicks,
    val unreadBadge: Color = WhatsAppVibrantGreen,
    val divider: Color = Color(0xFF222D34),
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
        chatBackground = LightChatCanvas,
        readTick = WhatsAppBlueTicks,
        unreadBadge = WhatsAppVibrantGreen,
        divider = LightDivider,
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
