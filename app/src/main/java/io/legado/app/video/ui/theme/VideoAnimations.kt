package io.legado.app.video.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Composable
fun AnimatedGradientBackground(
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(
        VideoColors.GradientStart,
        VideoColors.GradientEnd
    ),
    durationMs: Int = 8000
): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "gradient")

    val animatedOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMs),
            repeatMode = RepeatMode.Restart
        ),
        label = "gradient_offset"
    )

    return modifier.then(
        Modifier.background(
            Brush.linearGradient(
                colors = colors,
                start = Offset(animatedOffset * 200f, 0f),
                end = Offset((animatedOffset * 200f) + 400f, 400f)
            )
        )
    )
}

@Composable
fun PulsingIndicator(
    modifier: Modifier = Modifier,
    color: Color = VideoColors.Primary,
    pulseColor: Color = VideoColors.Primary.copy(alpha = 0.3f)
): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    return modifier.then(
        Modifier.background(
            brush = Brush.radialGradient(
                colors = listOf(color, pulseColor, Color.Transparent),
                radius = 100f * scale
            )
        )
    )
}

@Composable
fun ProgressBarAnimated(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = VideoColors.Primary,
    trackColor: Color = VideoColors.SurfaceVariant,
    height: androidx.compose.ui.unit.Dp = 6.dp,
    shape: Shape = RoundedCornerShape(3.dp)
): Modifier {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "progress"
    )

    return modifier
        .clip(shape)
        .background(trackColor)
        .then(
            Modifier
                .fillMaxWidth(animatedProgress)
                .height(height)
                .clip(shape)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(color, color.copy(alpha = 0.8f))
                    )
                )
        )
}

@Composable
fun WaveAnimation(
    modifier: Modifier = Modifier,
    color: Color = VideoColors.Primary
): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_offset"
    )

    return modifier.then(
        Modifier.background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    color.copy(alpha = (0.1f + 0.1f * kotlin.math.sin(waveOffset * Math.PI.toFloat())).toFloat()),
                    color.copy(alpha = 0.3f)
                )
            )
        )
    )
}

@Composable
fun LoadingSkeleton(
    modifier: Modifier = Modifier
): Modifier {
    return shimmerEffect(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp)
    )
}
