package com.paychat.paychat.ui.theme

import androidx.compose.ui.graphics.Color

// WhatsApp Signature Palette
val WhatsAppForestGreen = Color(0xFF008069) // WhatsApp light primary / app bar
val WhatsAppTealGreen = Color(0xFF00A884)   // WhatsApp dark primary & action accent
val WhatsAppVibrantGreen = Color(0xFF25D366) // Badges, active online indicator
val WhatsAppBlueTicks = Color(0xFF53BDEB)    // Double blue read ticks

// Legacy brand aliases mapping to WhatsApp palette
val Teal90 = Color(0xFFC7F8EC)
val Teal60 = WhatsAppTealGreen
val Teal40 = WhatsAppForestGreen
val Teal20 = Color(0xFF00382E)
val Teal10 = Color(0xFF001F19)

// Dark Theme Surfaces & Backgrounds
val DarkAppBackground = Color(0xFF0B141A)    // WhatsApp dark background
val DarkSurface = Color(0xFF111B21)          // WhatsApp dark chat list item & surface
val DarkHeader = Color(0xFF1F2C34)           // WhatsApp dark top app bar
val DarkTextPrimary = Color(0xFFE9EDEF)      // High contrast text
val DarkTextSecondary = Color(0xFF8696A0)    // Muted timestamps & subtitles
val DarkDivider = Color(0xFF222D34)          // Subtile hairline divider

// Light Theme Surfaces & Backgrounds
val LightAppBackground = Color(0xFFFFFFFF)   // Clean white background
val LightSurface = Color(0xFFFFFFFF)         // Clean white surfaces
val LightHeader = WhatsAppForestGreen        // Forest green header
val LightChatCanvas = Color(0xFFEFEAE2)      // WhatsApp signature chat wallpaper tint
val LightTextPrimary = Color(0xFF111B21)     // Deep slate text
val LightTextSecondary = Color(0xFF667781)   // Muted timestamps & subtitles
val LightDivider = Color(0xFFF0F2F5)         // Clean light divider

// Chat Bubbles (Authentic WhatsApp colors)
val OutgoingBubbleLight = Color(0xFFD9FDD3)  // WhatsApp light outgoing bubble
val OutgoingBubbleDark = Color(0xFF005C4B)   // WhatsApp dark outgoing bubble
val IncomingBubbleLight = Color(0xFFFFFFFF)  // WhatsApp light incoming bubble
val IncomingBubbleDark = Color(0xFF202C33)   // WhatsApp dark incoming bubble

// Financial Ledger Semantics (Adapted to WhatsApp Aesthetic)
val CreditLight = WhatsAppForestGreen
val CreditDark = WhatsAppTealGreen
val DebitLight = Color(0xFFD93025)
val DebitDark = Color(0xFFF15C6D)
val PendingLight = Color(0xFFD97706)
val PendingDark = Color(0xFFF59E0B)

// Transaction Hero Card Stops
val HeroStartLight = Color(0xFFE8F5E9)
val HeroEndLight = Color(0xFFE0F2F1)
val HeroStartDark = Color(0xFF112B23)
val HeroEndDark = Color(0xFF143630)

// Direction Arrow Containers
val CreditContainerLight = Color(0xFFD8F3E5)
val DebitContainerLight = Color(0xFFFCE8E6)
val CreditContainerDark = Color(0xFF163E32)
val DebitContainerDark = Color(0xFF4A1F1D)
