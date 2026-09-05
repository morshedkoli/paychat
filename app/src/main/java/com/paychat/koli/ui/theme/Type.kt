package com.paychat.koli.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val PayChatTypography = Typography()

/**
 * Amounts are always rendered with this style so digits line up in columns.
 */
val AmountStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 18.sp,
    letterSpacing = 0.sp,
)

val AmountLargeStyle = AmountStyle.copy(fontSize = 32.sp, fontWeight = FontWeight.Bold)
