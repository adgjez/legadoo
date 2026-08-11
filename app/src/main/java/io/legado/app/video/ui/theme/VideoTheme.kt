package io.legado.app.video.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

object VideoColors {
    val Primary = Color(0xFF6366F1)
    val PrimaryDark = Color(0xFF4F46E5)
    val PrimaryLight = Color(0xFF818CF8)
    val Secondary = Color(0xFFEC4899)
    val Accent = Color(0xFF10B981)
    val Background = Color(0xFF0F0F1A)
    val Surface = Color(0xFF1A1A2E)
    val SurfaceVariant = Color(0xFF252540)
    val SurfaceLight = Color(0xFF2D2D44)
    val OnBackground = Color(0xFFF1F5F9)
    val OnSurface = Color(0xFFF1F5F9)
    val OnSurfaceVariant = Color(0xFF94A3B8)
    val Success = Color(0xFF10B981)
    val Warning = Color(0xFFF59E0B)
    val Error = Color(0xFFEF4444)
    val Info = Color(0xFF3B82F6)
    val Premium = Color(0xFFFFB800)
    
    val OnPrimary = Color(0xFFFFFFFF)
    val OnPrimaryContainer = Color(0xFF1A1A2E)
    val OnSecondary = Color(0xFFFFFFFF)
    
    val StatusDraft = Color(0xFF6B7280)
    val StatusAnalyzing = Color(0xFF3B82F6)
    val StatusPlanning = Color(0xFF8B5CF6)
    val StatusGenerating = Color(0xFFF59E0B)
    val StatusCompleted = Color(0xFF10B981)
    val StatusFailed = Color(0xFFEF4444)
    
    val GradientStart = Color(0xFF667EEA)
    val GradientEnd = Color(0xFF764BA2)
    val GradientWarmStart = Color(0xFFFF6B6B)
    val GradientWarmEnd = Color(0xFFFFB800)
    val GradientCoolStart = Color(0xFF3B82F6)
    val GradientCoolEnd = Color(0xFF10B981)
    
    val CardOverlay = Color(0x80000000)
    val Divider = Color(0x1FFFFFFF)
    val Border = Color(0x33FFFFFF)
    val TextSecondary = Color(0xFF94A3B8)
    
    val GlassBackground = Color(0x1AFFFFFF)
    val ShimmerBase = Color(0xFF252540)
    val ShimmerHighlight = Color(0xFF3D3D5C)
}

object VideoShapes {
    val ExtraSmall = RoundedCornerShape(4.dp)
    val Small = RoundedCornerShape(8.dp)
    val Medium = RoundedCornerShape(12.dp)
    val Large = RoundedCornerShape(16.dp)
    val ExtraLarge = RoundedCornerShape(24.dp)
    val Card = RoundedCornerShape(20.dp)
    val Button = RoundedCornerShape(12.dp)
}

object VideoDimens {
    val Tiny = 4.dp
    val Small = 8.dp
    val Medium = 16.dp
    val Large = 24.dp
    val ExtraLarge = 32.dp
    
    val CardElevation = 4.dp
    val ButtonHeight = 52.dp
    val BottomNavHeight = 64.dp
    val TopBarHeight = 56.dp
    
    val ImageThumbnailSize = 80.dp
    val ImageLargeSize = 200.dp
    val AvatarSize = 48.dp
}

@Composable
fun shimmerEffect(
    modifier: Modifier = Modifier,
    shape: Shape = VideoShapes.Medium
): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val translationAnim by infiniteTransition.animateFloat(
        initialValue = -100f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translation"
    )

    return modifier
        .clip(shape)
        .background(VideoColors.ShimmerBase)
        .graphicsLayer {
            val brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    VideoColors.ShimmerHighlight.copy(alpha = 0.3f),
                    Color.Transparent
                ),
                start = Offset(x = translationAnim, y = 0f),
                end = Offset(x = translationAnim + 200f, y = 0f)
            )
        }
}

@Composable
fun gradientBackground(
    modifier: Modifier = Modifier,
    startColor: Color = VideoColors.GradientStart,
    endColor: Color = VideoColors.GradientEnd,
    shape: Shape = RoundedCornerShape(0.dp)
): Modifier {
    return modifier
        .clip(shape)
        .background(
            Brush.verticalGradient(
                colors = listOf(startColor, endColor)
            )
        )
}

@Composable
fun glassMorphism(
    modifier: Modifier = Modifier,
    shape: Shape = VideoShapes.Medium
): Modifier {
    return modifier
        .clip(shape)
        .background(VideoColors.GlassBackground)
}

fun Modifier.shadowElevation(elevation: Float = VideoDimens.CardElevation.value): Modifier {
    return this.then(
        Modifier.graphicsLayer {
            this.shadowElevation = elevation
            this.shape = VideoShapes.Card
        }
    )
}

enum class VideoAnimation {
    FADE_IN,
    SLIDE_UP,
    SLIDE_DOWN,
    SCALE_IN,
    PULSE,
    SHAKE
}
