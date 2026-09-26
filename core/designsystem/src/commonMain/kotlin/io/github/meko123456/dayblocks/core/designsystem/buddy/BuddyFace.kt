package io.github.meko123456.dayblocks.core.designsystem.buddy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.meko123456.dayblocks.core.domain.model.BuddyMood

/**
 * Kubi: a rounded block with a sprout on top. Drawn rather than shipped as images, so it is sharp
 * at any size and has a face for every mood without six assets per density.
 *
 * Original on purpose — a squared-off little creature with a leaf, not a bird and not a copy of
 * any existing mascot. The features are drawn in ink on the body colour in both themes, so the
 * face reads the same in light and dark.
 */
@Composable
fun BuddyFace(mood: BuddyMood, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        drawSprout(mood, s)
        drawRoundRect(Body, topLeft = Offset(0.12f * s, 0.24f * s), size = Size(0.76f * s, 0.68f * s), cornerRadius = CornerRadius(0.24f * s))
        drawRoundRect(Highlight, topLeft = Offset(0.2f * s, 0.3f * s), size = Size(0.26f * s, 0.1f * s), cornerRadius = CornerRadius(0.05f * s))
        if (mood == BuddyMood.Happy || mood == BuddyMood.Proud || mood == BuddyMood.Encouraging) {
            drawOval(Cheek, topLeft = Offset(0.19f * s, 0.6f * s), size = Size(0.13f * s, 0.07f * s))
            drawOval(Cheek, topLeft = Offset(0.68f * s, 0.6f * s), size = Size(0.13f * s, 0.07f * s))
        }
        drawEyes(mood, s)
        drawMouth(mood, s)
        drawExtras(mood, s)
    }
}

private val Body = Color(0xFFF2994A)
private val Highlight = Color(0x2EFFFFFF)
private val Ink = Color(0xFF1B1D22)
private val Cheek = Color(0x66FF6B81)
private val Leaf = Color(0xFF3FA86B)
private val Gold = Color(0xFFFFC53D)
private val Drop = Color(0xFF7CC4F5)

private val eyes = listOf(0.38f, 0.62f)
private const val EYE_Y = 0.52f

/** Upright and fresh, or wilting a little when the day is not going well or it is time to sleep. */
private fun DrawScope.drawSprout(mood: BuddyMood, s: Float) {
    val droopy = mood == BuddyMood.Disappointed || mood == BuddyMood.Sleepy
    drawLine(Leaf, Offset(0.5f * s, 0.25f * s), Offset(0.5f * s, 0.13f * s), strokeWidth = 0.035f * s, cap = StrokeCap.Round)
    val leaf = Path().apply {
        moveTo(0.5f * s, 0.14f * s)
        if (droopy) {
            quadraticTo(0.65f * s, 0.1f * s, 0.69f * s, 0.23f * s)
            quadraticTo(0.56f * s, 0.21f * s, 0.5f * s, 0.14f * s)
        } else {
            quadraticTo(0.62f * s, 0.01f * s, 0.72f * s, 0.08f * s)
            quadraticTo(0.62f * s, 0.17f * s, 0.5f * s, 0.14f * s)
        }
        close()
    }
    drawPath(leaf, Leaf)
}

private fun DrawScope.drawEyes(mood: BuddyMood, s: Float) {
    val r = 0.055f * s
    val line = Stroke(width = 0.035f * s, cap = StrokeCap.Round)
    for (x in eyes) {
        val c = Offset(x * s, EYE_Y * s)
        when (mood) {
            BuddyMood.Happy, BuddyMood.Encouraging -> {
                drawCircle(Ink, r, c)
                drawCircle(Color.White, 0.018f * s, c + Offset(0.018f * s, -0.018f * s))
            }
            BuddyMood.Worried -> {
                drawCircle(Ink, r * 0.85f, c)
                drawCircle(Color.White, 0.015f * s, c + Offset(0.015f * s, -0.015f * s))
            }
            // Squeezed shut with delight: ^ ^
            BuddyMood.Proud -> drawArc(Ink, 200f, 140f, false, Offset(c.x - r, c.y - r * 0.4f), Size(2 * r, 1.6f * r), style = line)
            // Closed and peaceful: ‿ ‿
            BuddyMood.Sleepy -> drawArc(Ink, 20f, 140f, false, Offset(c.x - r, c.y - r * 0.9f), Size(2 * r, 1.4f * r), style = line)
            // Looking down, lids half closed: playfully let down, not upset.
            BuddyMood.Disappointed -> {
                val low = c + Offset(0f, 0.02f * s)
                drawCircle(Ink, r, low)
                drawArc(Body, 180f, 180f, true, Offset(low.x - r * 1.1f, low.y - r * 1.1f), Size(2.2f * r, 2.2f * r))
                drawLine(Ink, Offset(low.x - r, low.y), Offset(low.x + r, low.y), strokeWidth = 0.03f * s, cap = StrokeCap.Round)
            }
        }
    }
    val brow = 0.028f * s
    when (mood) {
        // Raised brows: "come on, you can do it".
        BuddyMood.Encouraging -> for (x in eyes) {
            drawArc(Ink, 200f, 140f, false, Offset(x * s - 0.06f * s, 0.39f * s), Size(0.12f * s, 0.06f * s), style = Stroke(brow, cap = StrokeCap.Round))
        }
        // Brows tilted up in the middle.
        BuddyMood.Worried -> {
            drawLine(Ink, Offset(0.31f * s, 0.44f * s), Offset(0.42f * s, 0.4f * s), strokeWidth = brow, cap = StrokeCap.Round)
            drawLine(Ink, Offset(0.58f * s, 0.4f * s), Offset(0.69f * s, 0.44f * s), strokeWidth = brow, cap = StrokeCap.Round)
        }
        else -> Unit
    }
}

private fun DrawScope.drawMouth(mood: BuddyMood, s: Float) {
    val line = Stroke(width = 0.035f * s, cap = StrokeCap.Round)
    when (mood) {
        BuddyMood.Happy -> drawArc(Ink, 20f, 140f, false, Offset(0.41f * s, 0.6f * s), Size(0.18f * s, 0.12f * s), style = line)
        BuddyMood.Proud -> drawArc(Ink, 0f, 180f, true, Offset(0.39f * s, 0.6f * s), Size(0.22f * s, 0.17f * s))
        BuddyMood.Encouraging -> drawArc(Ink, 0f, 180f, true, Offset(0.43f * s, 0.63f * s), Size(0.14f * s, 0.11f * s))
        BuddyMood.Worried -> {
            val wave = Path().apply {
                moveTo(0.42f * s, 0.72f * s)
                quadraticTo(0.46f * s, 0.68f * s, 0.5f * s, 0.72f * s)
                quadraticTo(0.54f * s, 0.76f * s, 0.58f * s, 0.72f * s)
            }
            drawPath(wave, Ink, style = line)
        }
        BuddyMood.Disappointed -> drawArc(Ink, 200f, 140f, false, Offset(0.43f * s, 0.7f * s), Size(0.14f * s, 0.08f * s), style = line)
        BuddyMood.Sleepy -> drawCircle(Ink, 0.025f * s, Offset(0.5f * s, 0.71f * s), style = Stroke(0.022f * s))
    }
}

private fun DrawScope.drawExtras(mood: BuddyMood, s: Float) {
    when (mood) {
        BuddyMood.Proud -> drawSparkle(Offset(0.86f * s, 0.2f * s), 0.07f * s)
        BuddyMood.Worried -> {
            val drop = Path().apply {
                moveTo(0.83f * s, 0.34f * s)
                quadraticTo(0.87f * s, 0.42f * s, 0.83f * s, 0.45f * s)
                quadraticTo(0.79f * s, 0.42f * s, 0.83f * s, 0.34f * s)
                close()
            }
            drawPath(drop, Drop)
        }
        BuddyMood.Sleepy -> {
            drawZ(Offset(0.76f * s, 0.12f * s), 0.08f * s)
            drawZ(Offset(0.88f * s, 0.03f * s), 0.055f * s)
        }
        else -> Unit
    }
}

private fun DrawScope.drawSparkle(center: Offset, r: Float) {
    val star = Path().apply {
        moveTo(center.x, center.y - r)
        quadraticTo(center.x, center.y, center.x + r, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + r)
        quadraticTo(center.x, center.y, center.x - r, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - r)
        close()
    }
    drawPath(star, Gold)
}

private fun DrawScope.drawZ(topLeft: Offset, w: Float) {
    val z = Path().apply {
        moveTo(topLeft.x, topLeft.y)
        lineTo(topLeft.x + w, topLeft.y)
        lineTo(topLeft.x, topLeft.y + w)
        lineTo(topLeft.x + w, topLeft.y + w)
    }
    drawPath(z, Ink, style = Stroke(width = w * 0.22f, cap = StrokeCap.Round))
}
