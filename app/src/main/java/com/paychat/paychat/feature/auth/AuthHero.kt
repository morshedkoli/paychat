package com.paychat.paychat.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paychat.paychat.R
import com.paychat.paychat.ui.theme.DarkAppBackground
import com.paychat.paychat.ui.theme.WhatsAppForestGreen
import com.paychat.paychat.ui.theme.WhatsAppTealGreen
import com.paychat.paychat.ui.theme.WhatsAppVibrantGreen

/** WhatsApp-inspired background using active app theme canvas. */
@Composable
fun AuthBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier = Modifier
                .size(340.dp)
                .align(Alignment.TopStart)
                .offset(x = (-80).dp, y = (-60).dp)
                .background(
                    Brush.radialGradient(
                        listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), Color.Transparent)
                    ),
                    shape = CircleShape,
                ),
        )
        content()
    }
}

/**
 * WhatsApp-style minimal hero title and icon.
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
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BrandMark()

        Spacer(Modifier.size(16.dp))

        Text(
            "PAYCHAT",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
            ),
            color = MaterialTheme.colorScheme.primary,
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
    val pulse = rememberInfiniteTransition(label = "brandGlow")
    val glow by pulse.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowAlpha",
    )
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowScale",
    )

    Box(modifier = modifier.size(80.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(80.dp * scale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(WhatsAppTealGreen.copy(alpha = glow * 0.4f), Color.Transparent)
                    )
                ),
        )
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(WhatsAppVibrantGreen, WhatsAppTealGreen))),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .size(60.dp)
                    .padding(8.dp),
            )
        }
    }
}

@Composable
fun FooterLink(
    lead: String,
    action: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val leadColor = MaterialTheme.colorScheme.onSurfaceVariant
    val actionColor = MaterialTheme.colorScheme.primary
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = leadColor)) {
            append("$lead ")
        }
        withStyle(SpanStyle(color = actionColor, fontWeight = FontWeight.SemiBold)) {
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

@Composable
fun StaggeredEntrance(
    delayMillis: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delayMillis.toLong())
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(380, easing = EaseOutCubic)) +
            slideInVertically(tween(380, easing = EaseOutCubic)) { it / 6 },
        modifier = modifier,
    ) {
        content()
    }
}
