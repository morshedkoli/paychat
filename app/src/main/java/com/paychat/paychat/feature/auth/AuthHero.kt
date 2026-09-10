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
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paychat.paychat.R
import com.paychat.paychat.ui.theme.Ink10
import com.paychat.paychat.ui.theme.Sand95
import com.paychat.paychat.ui.theme.Teal40
import com.paychat.paychat.ui.theme.Teal60
import com.paychat.paychat.ui.theme.Teal90

/**
 * Forces the light scheme for the glass card's contents.
 *
 * The card floats on a dark backdrop regardless of the device's own theme,
 * so the fields inside it must stay light too - otherwise a phone in dark
 * mode would render dark text on the same dark-mode surface colour, right on
 * top of the card's light background.
 */
@Composable
fun LightCardScheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Teal40,
            onPrimary = Color.White,
            surface = Color.White,
            onSurface = Ink10,
            background = Sand95,
            onBackground = Ink10,
            surfaceVariant = Sand95,
            onSurfaceVariant = Ink10.copy(alpha = 0.6f),
        ),
        typography = MaterialTheme.typography,
        content = content,
    )
}

/** Deep gradient backdrop the whole signed-out flow sits on, with a soft glow behind the hero. */
@Composable
fun AuthBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Ink10, Color(0xFF0C2420), Ink10))
            ),
    ) {
        Box(
            modifier = Modifier
                .size(340.dp)
                .align(Alignment.TopStart)
                .offset(x = (-80).dp, y = (-60).dp)
                .background(
                    Brush.radialGradient(listOf(Teal60.copy(alpha = 0.35f), Color.Transparent)),
                    shape = CircleShape,
                ),
        )
        content()
    }
}

/**
 * The medallion, eyebrow and headline shared by every screen in the signed
 * out flow.
 *
 * The mark reuses the launcher's own speech-bubble glyph rather than a
 * second logo, so the identity someone taps from their home screen is the
 * same one that greets them here. It carries the flow's one continuous
 * motion, a slow pulsing glow, so the rest of the screen can stay still.
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
                letterSpacing = 4.sp,
            ),
            color = Teal90,
        )
        Text(
            headline,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
            ),
            color = Color.White,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun BrandMark(modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "brandGlow")
    val glow by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowAlpha",
    )
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowScale",
    )

    Box(modifier = modifier.size(88.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(88.dp * scale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Teal60.copy(alpha = glow * 0.5f), Color.Transparent)
                    )
                ),
        )
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(Teal90, Teal60))),
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
        withStyle(SpanStyle(color = Color.White.copy(alpha = 0.6f))) {
            append("$lead ")
        }
        withStyle(SpanStyle(color = Teal90, fontWeight = FontWeight.SemiBold)) {
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

/**
 * Fades and lifts [content] in once, after [delayMillis].
 *
 * The signed-out flow reveals itself in one orchestrated pass, hero then
 * card then button then footer, rather than each screen doing its own
 * scattered entrance.
 */
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
        enter = fadeIn(tween(420, easing = EaseOutCubic)) +
            slideInVertically(tween(420, easing = EaseOutCubic)) { it / 5 },
        modifier = modifier,
    ) {
        content()
    }
}
