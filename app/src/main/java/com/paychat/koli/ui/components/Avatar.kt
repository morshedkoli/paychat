package com.paychat.koli.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlin.math.absoluteValue

private val AvatarColors = listOf(
    Color(0xFF0B7A63), Color(0xFF3B6FB0), Color(0xFF8A4FA8),
    Color(0xFFB5651D), Color(0xFF2F7D5A), Color(0xFF9C3D54),
)

/**
 * A contact's picture, or their initials on a colour derived from their phone
 * number so the same person keeps the same colour everywhere.
 */
@Composable
fun Avatar(
    name: String,
    key: String,
    modifier: Modifier = Modifier,
    photoUrl: String? = null,
    size: androidx.compose.ui.unit.Dp = 44.dp,
) {
    val background = AvatarColors[key.hashCode().absoluteValue % AvatarColors.size]

    Box(
        modifier = modifier.size(size).clip(CircleShape).background(background),
        contentAlignment = Alignment.Center,
    ) {
        if (photoUrl != null) {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                modifier = Modifier.size(size).clip(CircleShape),
            )
        } else {
            Text(
                text = name.initials(),
                color = Color.White,
                fontSize = (size.value / 2.6f).sp,
                style = MaterialTheme.typography.titleMedium,
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
