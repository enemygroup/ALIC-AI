package com.alic.ai.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.alic.ai.data.OrbState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val DOTS = 420
private const val DEPTHS = 5
private const val MAX_COLORS = 4

/** Points evenly spread over a unit sphere (Fibonacci lattice), stored flat as x,y,z. */
private val SPHERE: FloatArray by lazy {
    val a = FloatArray(DOTS * 3)
    for (i in 0 until DOTS) {
        val y = 1f - 2f * (i + 0.5f) / DOTS
        val r = sqrt(1f - y * y)
        val th = i * 2.3999632f
        a[i * 3] = cos(th) * r
        a[i * 3 + 1] = y
        a[i * 3 + 2] = sin(th) * r
    }
    a
}

/** Reusable buffers so drawing allocates nothing per frame. Dots are drawn in (depth x colour) batches. */
private class OrbScratch {
    val pts = Array(DEPTHS * MAX_COLORS) { FloatArray(DOTS * 2) }
    val cnt = IntArray(DEPTHS * MAX_COLORS)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
    }
}

private val DEPTH_ALPHA = floatArrayOf(0.16f, 0.32f, 0.52f, 0.76f, 1f)
private val DEPTH_SIZE = floatArrayOf(1.4f, 2.1f, 3.0f, 4.0f, 5.2f)

/**
 * Dotted 3D orb coloured by the selected theme (Aurora = cyan, blue, purple, red).
 * Performance: all animation values are read inside the draw lambda (never in composition), so the orb
 * redraws every frame WITHOUT recomposing the screen around it, and dots are drawn in ~20 batches.
 */
@Composable
fun AlicOrb(state: OrbState, level: Float, modifier: Modifier = Modifier, animated: Boolean = true) {
    val t = rememberInfiniteTransition(label = "orb")
    val phaseS = t.animateFloat(0f, 1f, infiniteRepeatable(tween(12000, easing = LinearEasing)), label = "phase")
    val breatheS = t.animateFloat(0.95f, 1.05f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
    val lvS = animateFloatAsState(level, tween(120), label = "level")
    val spin = when (state) {
        OrbState.IDLE -> 1; OrbState.LISTENING -> 1; OrbState.THINKING -> 6; OrbState.SPEAKING -> 2; OrbState.LIVE -> 2
    }
    val scratch = remember { OrbScratch() }

    Canvas(modifier.aspectRatio(1f)) {
        val ph = if (animated) phaseS.value else 0f
        val lv = lvS.value
        val pal = C.orb
        val nPal = minOf(pal.size, MAX_COLORS)
        val isDark = ThemeState.isDark
        val c = center
        val tau = (2 * PI).toFloat()
        val unit = 1.dp.toPx()
        val base = size.minDimension / 2f * 0.80f
        val pulse = when (state) {
            OrbState.IDLE -> if (animated) breatheS.value else 1f
            OrbState.LISTENING -> 1f + lv * 0.10f
            OrbState.THINKING -> 1f + 0.02f * sin(ph * tau * 6)
            OrbState.SPEAKING -> 1f + 0.04f * sin(ph * tau * 6) + lv * 0.05f
            OrbState.LIVE -> 1f + 0.03f * sin(ph * tau * 4) + lv * 0.08f
        }
        val amp = when (state) {
            OrbState.IDLE -> 0.025f; OrbState.LISTENING -> 0.04f + lv * 0.30f; OrbState.THINKING -> 0.08f
            OrbState.SPEAKING -> 0.13f + lv * 0.10f; OrbState.LIVE -> 0.08f + lv * 0.30f
        }
        val r = base * pulse

        // soft coloured halo
        drawCircle(
            Brush.radialGradient(
                listOf(pal[0].copy(alpha = if (isDark) 0.28f else 0.20f), pal.last().copy(alpha = 0.10f), Color.Transparent),
                center = c, radius = r * 1.35f
            ),
            radius = r * 1.35f, center = c
        )

        val ay = ph * tau * spin
        val tilt = 0.40f
        val cy = cos(ay); val sy = sin(ay)
        val ct = cos(tilt); val st = sin(tilt)
        val wave = ph * tau * 3

        java.util.Arrays.fill(scratch.cnt, 0)
        for (i in 0 until DOTS) {
            val px = SPHERE[i * 3]; val py = SPHERE[i * 3 + 1]; val pz = SPHERE[i * 3 + 2]
            val d = 1f + amp * sin(wave + py * 7f + px * 5f)
            val x0 = px * d; val y0 = py * d; val z0 = pz * d
            val x1 = x0 * cy + z0 * sy
            val z1 = -x0 * sy + z0 * cy
            val y2 = y0 * ct - z1 * st
            val z2 = y0 * st + z1 * ct
            val depth = ((z2 + 1f) / 2f).coerceIn(0f, 0.999f)
            val persp = 1f / (1f - z2 * 0.22f)
            val di = (depth * DEPTHS).toInt()
            val colorT = ((py + 1f) / 2f) * 0.7f + ((px + 1f) / 2f) * 0.3f
            val ci = (colorT * nPal).toInt().coerceIn(0, nPal - 1)
            val b = di * MAX_COLORS + ci
            val n = scratch.cnt[b]
            scratch.pts[b][n * 2] = c.x + x1 * r * persp
            scratch.pts[b][n * 2 + 1] = c.y + y2 * r * persp
            scratch.cnt[b] = n + 1
        }

        val shimmer = if (state == OrbState.THINKING) 0.82f + 0.18f * sin(ph * tau * 8) else 1f
        val grow = 1f + lv * 0.35f
        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            val p = scratch.paint
            for (di in 0 until DEPTHS) {
                for (ci in 0 until nPal) {
                    val b = di * MAX_COLORS + ci
                    val n = scratch.cnt[b]
                    if (n == 0) continue
                    var argb = pal[ci].toArgb()
                    if (isDark && di == DEPTHS - 1) argb = ColorUtils.blendARGB(argb, 0xFFFFFFFF.toInt(), 0.40f)
                    val a = (255 * DEPTH_ALPHA[di] * shimmer).toInt().coerceIn(0, 255)
                    p.color = (argb and 0x00FFFFFF) or (a shl 24)
                    p.strokeWidth = DEPTH_SIZE[di] * unit * grow
                    nc.drawPoints(scratch.pts[b], 0, n * 2, p)
                }
            }
        }

        // core spark
        val spark = if (isDark) Color.White.copy(alpha = 0.55f) else pal[0].copy(alpha = 0.45f)
        drawCircle(
            Brush.radialGradient(listOf(spark, pal[0].copy(alpha = 0.22f), Color.Transparent), center = c, radius = r * 0.30f),
            radius = r * 0.30f, center = c
        )
    }
}
