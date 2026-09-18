package com.paychat.paychat.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightScheme = lightColorScheme(
    primary = WhatsAppForestGreen,
    onPrimary = Color.White,
    primaryContainer = Teal90,
    onPrimaryContainer = Teal20,
    secondary = WhatsAppTealGreen,
    onSecondary = Color.White,
    secondaryContainer = CreditContainerLight,
    onSecondaryContainer = WhatsAppForestGreen,
    background = LightAppBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = Color(0xFFF0F2F5),
    onSurfaceVariant = LightTextSecondary,
    outline = Color(0xFFD1D7DB),
    outlineVariant = LightDivider,
)

private val DarkScheme = darkColorScheme(
    primary = WhatsAppTealGreen,
    onPrimary = DarkAppBackground,
    primaryContainer = Color(0xFF00382E),
    onPrimaryContainer = WhatsAppTealGreen,
    secondary = WhatsAppVibrantGreen,
    onSecondary = DarkAppBackground,
    secondaryContainer = CreditContainerDark,
    onSecondaryContainer = WhatsAppTealGreen,
    background = DarkAppBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = Color(0xFF1F2C34),
    onSurfaceVariant = DarkTextSecondary,
    outline = Color(0xFF374248),
    outlineVariant = DarkDivider,
)

@Composable
fun PayChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkScheme
        else -> LightScheme
    }

    val ledgerColors = if (darkTheme) {
        LedgerColors(
            credit = CreditDark,
            debit = DebitDark,
            pending = PendingDark,
            outgoingBubble = OutgoingBubbleDark,
            incomingBubble = IncomingBubbleDark,
            heroStart = HeroStartDark,
            heroEnd = HeroEndDark,
            creditContainer = CreditContainerDark,
            debitContainer = DebitContainerDark,
            chatBackground = DarkAppBackground,
            readTick = WhatsAppBlueTicks,
            unreadBadge = WhatsAppVibrantGreen,
            divider = DarkDivider,
        )
    } else {
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

    CompositionLocalProvider(LocalLedgerColors provides ledgerColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PayChatTypography,
            content = content
        )
    }
}

val LocalHideBalances = androidx.compose.runtime.compositionLocalOf { false }

