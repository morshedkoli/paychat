package com.paychat.paychat.ui.components

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.paychat.paychat.core.money.Money
import com.paychat.paychat.ui.theme.PayChatTheme

/**
 * Waiting, in this app's own hand.
 *
 * Two shapes for two jobs: a coin for the moment the app opens and has the
 * screen to itself, and a ring for the small waits inside a row or a button.
 * Both ease rather than turn at a constant rate, which is the whole difference
 * between a considered spinner and the stock one.
 */

/** Everything the coin's turn is made of, kept together so the timing reads in one place. */
private const val FLIP_MILLIS = 1600

/**
 * A taka coin turning over.
 *
 * The flip is a real rotation about the Y axis rather than a squashing
 * ellipse, so [androidx.compose.ui.graphics.GraphicsLayerScope.cameraDistance]
 * carries the whole illusion: too near and the coin distorts into a funnel, too
 * far and the turn flattens into a wipe. Eight times the coin's own width sits
 * where it looks like an object rather than an effect.
 *
 * @param size the coin's diameter. Below about 24.dp the symbol stops being
 *   legible, and [PayChatSpinner] is the right thing instead.
 */
@Composable
fun TakaCoinLoader(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    val animated = animationsAreOn()
    val density = LocalDensity.current

    val rotation by if (animated) {
        rememberInfiniteTransition(label = "coin").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                // The pauses either side of the halfway point are what give the
                // coin weight: it turns, shows its face, and turns again.
                animation = keyframes {
                    durationMillis = FLIP_MILLIS
                    0f at 0 using CoinEasing
                    180f at (FLIP_MILLIS * 45 / 100) using LinearEasing
                    180f at (FLIP_MILLIS * 55 / 100) using CoinEasing
                    360f at FLIP_MILLIS
                },
                repeatMode = RepeatMode.Restart,
            ),
            label = "rotationY",
        )
    } else {
        // Animations are off on this phone, so a turning coin would simply sit
        // frozen mid-flip and read as a hung screen. Show its face instead.
        remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    Box(
        modifier = modifier
            .size(size)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = with(density) { size.toPx() } * 8f
            }
            .clip(CircleShape)
            .background(PayChatTheme.ledger.credit),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            Money.SYMBOL,
            color = MaterialTheme.colorScheme.surface,
            fontWeight = FontWeight.Bold,
            fontSize = with(density) { (size * 0.44f).toSp() },
            // Past a quarter turn the far face is showing, and a symbol painted
            // on it would be mirrored. Turning the text with it keeps the mark
            // the right way round, the way a struck coin carries a face on both
            // sides rather than one see-through one.
            modifier = Modifier.graphicsLayer {
                scaleX = if (rotation > 90f && rotation < 270f) -1f else 1f
            },
        )
    }
}

/**
 * The small wait: an arc that sweeps with organic momentum and a luminous tail.
 *
 * Rather than a rigid constant machine turn or a jerky reversing arc, this
 * spinner turns with a continuous sinusoidal velocity rhythm: accelerating through
 * the sweep with natural momentum and gently gathering at the apex, while the arc
 * breathes smoothly forward. A subtle track ring grounds the circle, and an
 * optional gradient tail melts seamlessly into the track.
 */
@Composable
fun PayChatSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    strokeWidth: Dp = 2.dp,
    color: Color = PayChatTheme.ledger.credit,
    trackColor: Color = color.copy(alpha = 0.15f),
    useGradient: Boolean = true,
) {
    val animated = animationsAreOn()
    val progress by if (animated) {
        rememberInfiniteTransition(label = "spinner").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = SpinnerMath.CYCLE_MILLIS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "spinner_progress",
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    val rotation = if (animated) SpinnerMath.rotation(progress) else 0f
    val sweep = if (animated) SpinnerMath.sweep(progress) else SpinnerMath.MAX_SWEEP

    Canvas(
        modifier
            .size(size)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }
    ) {
        val stroke = strokeWidth.toPx()
        val diameter = this.size.minDimension
        val radius = (diameter - stroke) / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val topLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2f, radius * 2f)

        // 1. Subtle, elegant track ring
        if (trackColor.alpha > 0f) {
            drawCircle(
                color = trackColor,
                radius = radius,
                center = center,
                style = Stroke(width = stroke),
            )
        }

        // 2. Active arc with rounded caps and seamless light trail
        if (useGradient) {
            val headFraction = (sweep / 360f).coerceIn(0.1f, 1f)
            val brush = Brush.sweepGradient(
                0.0f to color.copy(alpha = 0f),
                headFraction * 0.35f to color.copy(alpha = 0.25f),
                headFraction * 0.75f to color.copy(alpha = 0.75f),
                headFraction to color,
                1.0f to color,
                center = center,
            )
            rotate(degrees = rotation, pivot = center) {
                drawArc(
                    brush = brush,
                    startAngle = 0f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        } else {
            drawArc(
                color = color,
                startAngle = rotation,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}

internal object SpinnerMath {
    const val CYCLE_MILLIS = 1100
    const val MIN_SWEEP = 200f
    const val MAX_SWEEP = 280f

    /**
     * Continuous 360-degree rotation with an organic sinusoidal velocity modulation.
     * Velocity is strictly positive at every point (minimum > 200 deg/cycle),
     * and boundary values/derivatives match infinitely, ensuring zero hitching or stuttering.
     */
    fun rotation(progress: Float): Float {
        val angleRad = (progress * 2.0 * Math.PI).toFloat()
        val modulated = progress * 360f - 24f * kotlin.math.sin(angleRad)
        return (modulated % 360f + 360f) % 360f
    }

    /**
     * Smoothly breathes between [MIN_SWEEP] and [MAX_SWEEP].
     * The rate of change is strictly less than the forward rotation rate,
     * ensuring the leading tip always advances clockwise.
     */
    fun sweep(progress: Float): Float {
        val angleRad = (progress * 2.0 * Math.PI).toFloat()
        val midpoint = (MIN_SWEEP + MAX_SWEEP) / 2f
        val amplitude = (MAX_SWEEP - MIN_SWEEP) / 2f
        return midpoint + amplitude * kotlin.math.cos(angleRad)
    }
}

/** Slow at the extremes, quick through the middle. */
private val CoinEasing = CubicBezierEasing(0.5f, 0.05f, 0.5f, 0.95f)

/**
 * Whether this phone actually plays animations.
 *
 * Developer options and some battery savers set the animator scale to zero, and
 * an infinite transition then never advances — a spinner that never moves looks
 * like a hang, which is the opposite of what it is for.
 */
@Composable
private fun animationsAreOn(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }
}
