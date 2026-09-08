package com.paychat.paychat.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paychat.paychat.R
import com.paychat.paychat.ui.theme.Teal40
import com.paychat.paychat.ui.theme.Teal60

/**
 * The medallion, eyebrow and headline shared by every screen in the signed
 * out flow.
 *
 * The mark reuses the launcher's own speech-bubble glyph rather than a
 * second logo, so the identity someone taps from their home screen is the
 * same one that greets them here.
 */
@Composable
fun AuthHero(
    headline: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BrandMark()

        Spacer(Modifier.size(20.dp))

        Text(
            "PAYCHAT",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.sp,
            ),
            color = Teal40,
        )
        Text(
            headline,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
            ),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BrandMark(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(64.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Teal60, Teal40))),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .padding(10.dp),
        )
    }
}

/**
 * One line of plain text ending in a coloured, tappable word.
 *
 * Replaces a stack of centred [androidx.compose.material3.TextButton]s: a
 * single sentence reads as one choice instead of a list of buttons.
 */
@Composable
fun FooterLink(
    lead: String,
    action: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
            append("$lead ")
        }
        withStyle(SpanStyle(color = Teal40, fontWeight = FontWeight.SemiBold)) {
            append(action)
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = modifier.clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
            onClick = onClick,
        ),
    )
}
