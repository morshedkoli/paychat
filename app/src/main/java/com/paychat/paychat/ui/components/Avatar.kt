package com.paychat.paychat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.paychat.paychat.ui.theme.WhatsAppVibrantGreen
import kotlin.math.absoluteValue

// WhatsApp Curated Avatar Palette (Teal, Forest Green, Slate, and Blue tones)
private val WhatsAppAvatarColors = listOf(
    Color(0xFF00A884), // WhatsApp Teal
    Color(0xFF008069), // WhatsApp Forest Green
    Color(0xFF26A69A), // Sea Green
    Color(0xFF34B7F1), // WhatsApp Sky Blue
    Color(0xFF0288D1), // Cerulean Blue
    Color(0xFF455A64), // Blue Grey
    Color(0xFF546E7A), // Slate Grey
    Color(0xFF00796B), // Deep Teal
)

/**
 * WhatsApp-style clean avatar with optional status indicator.
 */
@Composable
fun Avatar(
    name: String,
    key: String,
    modifier: Modifier = Modifier,
    photoUrl: String? = null,
    size: Dp = 48.dp,
    showOnline: Boolean = false,
) {
    val background = WhatsAppAvatarColors[key.hashCode().absoluteValue % WhatsAppAvatarColors.size]

    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(background),
            contentAlignment = Alignment.Center,
        ) {
            if (!photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = name,
                    modifier = Modifier.size(size).clip(CircleShape),
                )
            } else {
                Text(
                    text = name.initials(),
                    color = Color.White,
                    fontSize = (size.value * 0.38f).sp,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        if (showOnline) {
            val dotSize = (size.value * 0.28f).coerceIn(10f, 16f).dp
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(WhatsAppVibrantGreen)
                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
    }
}

private fun String.initials(): String {
    val words = trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(1).uppercase()
        else -> (words.first().take(1) + words.last().take(1)).uppercase()
    }
}
