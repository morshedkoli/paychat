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
import androidx.compose.ui.platform.LocalContext

private val LightScheme = lightColorScheme(
    primary = Teal40,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = Teal90,
    onPrimaryContainer = Teal20,
    secondary = Teal60,
    background = Sand95,
    onBackground = Ink10,
    surface = androidx.compose.ui.graphics.Color.White,
    onSurface = Ink10,
)

private val DarkScheme = darkColorScheme(
    primary = Teal60,
    onPrimary = Teal10,
    primaryContainer = Teal20,
    onPrimaryContainer = Teal90,
    secondary = Teal90,
    background = Ink10,
    onBackground = Ink90,
    surface = Ink20,
    onSurface = Ink90,
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
            CreditDark, DebitDark, PendingDark, OutgoingBubbleDark, IncomingBubbleDark,
            HeroStartDark, HeroEndDark, CreditContainerDark, DebitContainerDark,
        )
    } else {
        LedgerColors(
            CreditLight, DebitLight, PendingLight, OutgoingBubbleLight, IncomingBubbleLight,
            HeroStartLight, HeroEndLight, CreditContainerLight, DebitContainerLight,
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
