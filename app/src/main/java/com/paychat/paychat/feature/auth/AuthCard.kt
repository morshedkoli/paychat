package com.paychat.paychat.feature.auth

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.paychat.paychat.ui.theme.Ink90
import com.paychat.paychat.ui.theme.Teal20
import com.paychat.paychat.ui.theme.Teal60
import com.paychat.paychat.ui.theme.Teal90

/**
 * The panel the signed-out forms sit in.
 *
 * It reads as part of the backdrop rather than a white sheet dropped on top:
 * a barely lighter translucent fill with a hairline edge, so the gradient
 * behind it still shows through.
 *
 * The scheme inside is forced dark whatever the device is set to, because the
 * backdrop is always dark. Letting a light-mode phone theme the contents
 * would put near-black text on it.
 */
@Composable
fun AuthCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Teal60,
            onPrimary = Teal20,
            surface = Color.White.copy(alpha = 0.06f),
            onSurface = Color.White,
            background = Color.Transparent,
            onBackground = Ink90,
            surfaceVariant = Teal20,
            onSurfaceVariant = Ink90.copy(alpha = 0.7f),
            outlineVariant = Color.White.copy(alpha = 0.22f),
            secondary = Teal90,
        ),
        typography = MaterialTheme.typography,
    ) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = CardShape,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

private val CardShape = RoundedCornerShape(24.dp)
