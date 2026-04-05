package com.paychat.paychat.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ─── Brand colour tokens (from design system) ───────────────────────────────

object AppColors {
    // Primary brand — deep sky blue
    val Primary          = Color(0xFF1B6CA8)
    val PrimaryDark      = Color(0xFF144F7D)
    val PrimaryLight     = Color(0xFFE8F4FD)
    val PrimaryContainer = Color(0xFFD0E8F8)

    // Accent — WhatsApp-inspired green
    val Accent           = Color(0xFF25D366)
    val AccentDark       = Color(0xFF1DA851)

    // Backgrounds
    val BgScreen         = Color(0xFFF5F7FA)
    val BgSurface        = Color(0xFFFFFFFF)
    val BgChat           = Color(0xFFECE5DD)

    // Message bubbles
    val BubbleSent       = Color(0xFFDCF8C6)
    val BubbleReceived   = Color(0xFFFFFFFF)

    // Text
    val TextPrimary      = Color(0xFF1A1A2E)
    val TextSecondary    = Color(0xFF6B7280)
    val TextDisabled     = Color(0xFFBDBDBD)
    val TextOnPrimary    = Color(0xFFFFFFFF)
    val TextLink         = Color(0xFF1B6CA8)

    // Dividers & borders
    val Divider          = Color(0xFFE5E7EB)
    val Border           = Color(0xFFD1D5DB)

    // Status indicators
    val OnlineIndicator  = Color(0xFF4ADE80)
    val UnreadBadge      = Color(0xFFEF4444)
    val DeliveredTick    = Color(0xFF6B7280)
    val ReadTick         = Color(0xFF1B6CA8)

    // Feedback
    val Warning          = Color(0xFFF59E0B)
    val Error            = Color(0xFFDC2626)
    val Success          = Color(0xFF16A34A)

    // Icon tints
    val IconPrimary      = Color(0xFF374151)
    val IconSecondary    = Color(0xFF9CA3AF)
    val IconOnPrimary    = Color(0xFFFFFFFF)
}

// ─── Dark-mode palette (night tokens) ────────────────────────────────────────

object AppColorsDark {
    val Primary          = Color(0xFF64B5F6)
    val PrimaryDark      = Color(0xFF1A3A5C)
    val PrimaryLight     = Color(0xFF102030)
    val BgScreen         = Color(0xFF0D1117)
    val BgSurface        = Color(0xFF1A2233)
    val BgChat           = Color(0xFF1A1510)
    val BubbleSent       = Color(0xFF1E4620)
    val BubbleReceived   = Color(0xFF1A2233)
    val TextPrimary      = Color(0xFFE8EAF0)
    val TextSecondary    = Color(0xFF9CA3AF)
    val Divider          = Color(0xFF2D3748)
}

// ─── Composition local so any composable can access extra tokens ──────────────

@Immutable
data class ExtendedColors(
    val primary: Color,
    val primaryDark: Color,
    val primaryLight: Color,
    val primaryContainer: Color,
    val accent: Color,
    val bgScreen: Color,
    val bgSurface: Color,
    val bgChat: Color,
    val bubbleSent: Color,
    val bubbleReceived: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textOnPrimary: Color,
    val divider: Color,
    val border: Color,
    val onlineIndicator: Color,
    val unreadBadge: Color,
    val deliveredTick: Color,
    val readTick: Color,
    val iconPrimary: Color,
    val iconSecondary: Color,
    val iconOnPrimary: Color,
    val error: Color,
    val success: Color,
    val warning: Color,
    val isDark: Boolean,
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        primary = AppColors.Primary,
        primaryDark = AppColors.PrimaryDark,
        primaryLight = AppColors.PrimaryLight,
        primaryContainer = AppColors.PrimaryContainer,
        accent = AppColors.Accent,
        bgScreen = AppColors.BgScreen,
        bgSurface = AppColors.BgSurface,
        bgChat = AppColors.BgChat,
        bubbleSent = AppColors.BubbleSent,
        bubbleReceived = AppColors.BubbleReceived,
        textPrimary = AppColors.TextPrimary,
        textSecondary = AppColors.TextSecondary,
        textOnPrimary = AppColors.TextOnPrimary,
        divider = AppColors.Divider,
        border = AppColors.Border,
        onlineIndicator = AppColors.OnlineIndicator,
        unreadBadge = AppColors.UnreadBadge,
        deliveredTick = AppColors.DeliveredTick,
        readTick = AppColors.ReadTick,
        iconPrimary = AppColors.IconPrimary,
        iconSecondary = AppColors.IconSecondary,
        iconOnPrimary = AppColors.IconOnPrimary,
        error = AppColors.Error,
        success = AppColors.Success,
        warning = AppColors.Warning,
        isDark = false,
    )
}

// Convenience accessor
val MaterialTheme.xColors: ExtendedColors
    @Composable get() = LocalExtendedColors.current

// ─── Material3 colour schemes ─────────────────────────────────────────────────

private val LightColors = lightColorScheme(
    primary          = AppColors.Primary,
    onPrimary        = AppColors.TextOnPrimary,
    primaryContainer = AppColors.PrimaryContainer,
    secondary        = AppColors.Accent,
    onSecondary      = Color.White,
    background       = AppColors.BgScreen,
    onBackground     = AppColors.TextPrimary,
    surface          = AppColors.BgSurface,
    onSurface        = AppColors.TextPrimary,
    surfaceVariant   = AppColors.PrimaryLight,
    outline          = AppColors.Border,
    error            = AppColors.Error,
    onError          = Color.White,
)

private val DarkColors = darkColorScheme(
    primary          = AppColorsDark.Primary,
    onPrimary        = Color.White,
    background       = AppColorsDark.BgScreen,
    onBackground     = AppColorsDark.TextPrimary,
    surface          = AppColorsDark.BgSurface,
    onSurface        = AppColorsDark.TextPrimary,
    outline          = AppColorsDark.Divider,
    error            = AppColors.Error,
    onError          = Color.White,
)

// ─── Theme entry point ────────────────────────────────────────────────────────

@Composable
fun PayChatTheme(content: @Composable () -> Unit) {
    val isDark = isSystemInDarkTheme()

    val extColors = if (isDark) {
        ExtendedColors(
            primary = AppColorsDark.Primary,
            primaryDark = AppColorsDark.PrimaryDark,
            primaryLight = AppColorsDark.PrimaryLight,
            primaryContainer = AppColorsDark.PrimaryDark,
            accent = AppColors.Accent,
            bgScreen = AppColorsDark.BgScreen,
            bgSurface = AppColorsDark.BgSurface,
            bgChat = AppColorsDark.BgChat,
            bubbleSent = AppColorsDark.BubbleSent,
            bubbleReceived = AppColorsDark.BubbleReceived,
            textPrimary = AppColorsDark.TextPrimary,
            textSecondary = AppColorsDark.TextSecondary,
            textOnPrimary = Color.White,
            divider = AppColorsDark.Divider,
            border = AppColorsDark.Divider,
            onlineIndicator = AppColors.OnlineIndicator,
            unreadBadge = AppColors.UnreadBadge,
            deliveredTick = AppColors.DeliveredTick,
            readTick = AppColorsDark.Primary,
            iconPrimary = AppColorsDark.TextSecondary,
            iconSecondary = AppColorsDark.TextSecondary,
            iconOnPrimary = Color.White,
            error = AppColors.Error,
            success = AppColors.Success,
            warning = AppColors.Warning,
            isDark = true,
        )
    } else {
        ExtendedColors(
            primary = AppColors.Primary,
            primaryDark = AppColors.PrimaryDark,
            primaryLight = AppColors.PrimaryLight,
            primaryContainer = AppColors.PrimaryContainer,
            accent = AppColors.Accent,
            bgScreen = AppColors.BgScreen,
            bgSurface = AppColors.BgSurface,
            bgChat = AppColors.BgChat,
            bubbleSent = AppColors.BubbleSent,
            bubbleReceived = AppColors.BubbleReceived,
            textPrimary = AppColors.TextPrimary,
            textSecondary = AppColors.TextSecondary,
            textOnPrimary = AppColors.TextOnPrimary,
            divider = AppColors.Divider,
            border = AppColors.Border,
            onlineIndicator = AppColors.OnlineIndicator,
            unreadBadge = AppColors.UnreadBadge,
            deliveredTick = AppColors.DeliveredTick,
            readTick = AppColors.ReadTick,
            iconPrimary = AppColors.IconPrimary,
            iconSecondary = AppColors.IconSecondary,
            iconOnPrimary = AppColors.IconOnPrimary,
            error = AppColors.Error,
            success = AppColors.Success,
            warning = AppColors.Warning,
            isDark = false,
        )
    }

    CompositionLocalProvider(LocalExtendedColors provides extColors) {
        MaterialTheme(
            colorScheme = if (isDark) DarkColors else LightColors,
            content = content,
        )
    }
}
