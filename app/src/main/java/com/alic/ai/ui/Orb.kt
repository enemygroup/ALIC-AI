package com.alic.ai.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.alic.ai.data.OrbState
import kotlin.math.*

/** The 15 orb designs. The id is what gets saved in settings. */
data class OrbDesign(val id: String, val label: String)

val ORB_STYLES: List<OrbDesign> = listOf(
    OrbDesign("holo", "Holographic"), OrbDesign("galaxy", "Galaxy"), OrbDesign("glass", "Liquid Glass"),
    OrbDesign("plasma", "Plasma"), OrbDesign("neon", "Neon"), OrbDesign("pearl", "Pearl"),
    OrbDesign("fire", "Fire"), OrbDesign("cyber", "Cyber Core"), OrbDesign("aqua", "Aqua"),
    OrbDesign("magic", "Magic"), OrbDesign("matrix", "Matrix"), OrbDesign("chrome", "Chrome"),
    OrbDesign("aurora", "Aurora"), OrbDesign("particle", "Particle"), OrbDesign("reactor", "Reactor")
)

/** Old saved ids (dots, rings, waves, particles) map onto the new designs. */
fun normalizeOrb(id: String): String = when (id) {
    "dots", "particles" -> "particle"
    "rings" -> "reactor"
    "waves" -> "aqua"
    else -> if (ORB_STYLES.any { it.id == id }) id else "particle"
}

private const val DOTS = 420

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

/** Everything one frame of an orb design needs. */
data class OrbFrame(
    val c: Offset, val r: Float, val ph: Float, val lv: Float, val amp: Float, val tau: Float, val unit: Float,
    val pal: List<Color>, val glow: Float, val dens: Float, val particles: Boolean, val dark: Boolean
) {
    fun col(i: Int): Color = pal[((i % pal.size) + pal.size) % pal.size]
}

private fun OrbFrame.count(base: Int): Int = (base * dens).toInt().coerceAtLeast(4)

/** Outer glow for every design. Turned off when glow is disabled. */
private fun DrawScope.halo(f: OrbFrame, color: Color) {
    if (f.glow <= 0f) return
    val rad = f.r * 1.45f
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.30f * f.glow.coerceAtMost(1.5f)), Color.Transparent), center = f.c, radius = rad), radius = rad, center = f.c)
}

private fun DrawScope.styleHolo(f: OrbFrame) {
    val a = f.col(0); val b = f.col(1)
    halo(f, a)
    drawCircle(a.copy(alpha = 0.12f), f.r, f.c)
    drawCircle(a.copy(alpha = 0.7f), f.r, f.c, style = Stroke(1.6f * f.unit))
    val n = f.count(16)
    for (k in 0 until n) {
        val y = f.c.y - f.r + 2f * f.r * ((k / n.toFloat() + f.ph) % 1f)
        val dy = y - f.c.y
        val half = sqrt(max(0f, f.r * f.r - dy * dy))
        drawLine(if (k % 3 == 0) b.copy(alpha = 0.55f) else a.copy(alpha = 0.3f), Offset(f.c.x - half, y), Offset(f.c.x + half, y), strokeWidth = 1.2f * f.unit)
    }
    for (k in 0 until 3) {
        val rot = (f.ph * f.tau + k * f.tau / 3f) * 57.2958f
        val rr = f.r * (0.82f + 0.06f * k)
        drawArc(b.copy(alpha = 0.8f), rot, 80f, false, topLeft = Offset(f.c.x - rr, f.c.y - rr), size = Size(rr * 2, rr * 2), style = Stroke(1.4f * f.unit, cap = StrokeCap.Round))
    }
}

private fun DrawScope.styleGalaxy(f: OrbFrame) {
    val a = f.col(0); val b = f.col(1)
    halo(f, a)
    drawCircle(Brush.radialGradient(listOf(Color(0xFF2A0F4A), Color(0xFF0B0418)), center = f.c, radius = f.r), f.r, f.c)
    val n = f.count(160)
    for (i in 0 until n) {
        val seed = ((i * 37) % 101) / 101f
        val rr = f.r * 0.95f * sqrt(seed)
        val arm = (i % 3) * f.tau / 3f
        val ang = arm + rr / f.r * 3.2f + f.ph * f.tau * (1.2f - seed * 0.6f)
        val p = Offset(f.c.x + cos(ang) * rr, f.c.y + sin(ang) * rr * 0.9f)
        val col = if (i % 4 == 0) Color.White else if (i % 2 == 0) a else b
        drawCircle(col.copy(alpha = 0.55f + 0.4f * seed), (0.8f + seed * 1.8f) * f.unit, p)
    }
}

private fun DrawScope.styleGlass(f: OrbFrame) {
    val a = f.col(0)
    halo(f, a)
    drawCircle(
        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.28f), a.copy(alpha = 0.10f), Color.Transparent),
            start = Offset(f.c.x - f.r, f.c.y - f.r), end = Offset(f.c.x + f.r, f.c.y + f.r)),
        f.r, f.c
    )
    drawCircle(a.copy(alpha = 0.6f), f.r, f.c, style = Stroke(2f * f.unit))
    drawOval(Color.White.copy(alpha = 0.55f), topLeft = Offset(f.c.x - f.r * 0.55f, f.c.y - f.r * 0.6f), size = Size(f.r * 0.5f, f.r * 0.25f))
    drawArc(a.copy(alpha = 0.5f), 200f, 60f, false, topLeft = Offset(f.c.x - f.r * 0.8f, f.c.y - f.r * 0.8f), size = Size(f.r * 1.6f, f.r * 1.6f), style = Stroke(3f * f.unit))
}

private fun DrawScope.stylePlasma(f: OrbFrame) {
    val a = f.col(0); val b = f.col(1)
    halo(f, a)
    drawCircle(Color(0xFF0A0420), f.r, f.c)
    val bolts = f.count(7)
    for (k in 0 until bolts) {
        val base = k * f.tau / bolts + f.ph * f.tau * 0.5f
        val path = Path()
        for (s in 0..12) {
            val rr = f.r * s / 12f
            val jit = sin(s * 2.7f + f.ph * f.tau * 6f + k * 1.3f) * f.r * 0.09f * (0.4f + f.amp + f.lv)
            val x = f.c.x + cos(base) * rr - sin(base) * jit
            val y = f.c.y + sin(base) * rr + cos(base) * jit
            if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, if (k % 2 == 0) a else b, style = Stroke(1.6f * f.unit, cap = StrokeCap.Round))
    }
}

private fun DrawScope.styleNeon(f: OrbFrame) {
    val a = f.col(0); val b = f.col(1)
    halo(f, a)
    drawCircle(Color(0xFF05060C), f.r, f.c)
    for (k in 0 until 4) {
        val rr = f.r * (0.55f + 0.14f * k)
        val col = if (k % 2 == 0) a else b
        val alpha = (0.85f - k * 0.18f) * (0.85f + 0.15f * sin(f.ph * f.tau * 2 + k))
        drawCircle(col.copy(alpha = alpha), rr, f.c, style = Stroke((4f - k * 0.6f) * f.unit))
    }
}

private fun DrawScope.stylePearl(f: OrbFrame) {
    val a = f.col(0); val b = f.col(1); val c3 = f.col(2)
    halo(f, Color.White)
    drawCircle(
        Brush.radialGradient(listOf(Color.White, Color(0xFFF3E8F0), Color(0xFFD9E8F5)),
            center = Offset(f.c.x - f.r * 0.25f, f.c.y - f.r * 0.3f), radius = f.r * 1.2f),
        f.r, f.c
    )
    val tints = listOf(a, b, c3)
    for (k in 0 until 3) {
        val start = (f.ph * 360f * (1f + k * 0.2f)) + k * 120f
        drawArc(tints[k].copy(alpha = 0.35f), start, 100f, false, topLeft = Offset(f.c.x - f.r * 0.9f, f.c.y - f.r * 0.9f), size = Size(f.r * 1.8f, f.r * 1.8f), style = Stroke(3f * f.unit, cap = StrokeCap.Round))
    }
}

private fun DrawScope.styleFire(f: OrbFrame) {
    halo(f, Color(0xFFFF5A1F))
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFD27A), Color(0xFFFF5A1F), Color(0xFF7A0B0B)), center = f.c, radius = f.r), f.r, f.c)
    val n = f.count(90)
    for (i in 0 until n) {
        val prog = (f.ph + i / n.toFloat()) % 1f
        val spread = sin(i * 1.7f + f.ph * f.tau * 3f) * f.r * (0.2f + 0.5f * prog)
        val p = Offset(f.c.x + spread, f.c.y + f.r * 0.9f - prog * f.r * 2.1f)
        val col = if (prog < 0.5f) Color(0xFFFFE08A) else if (prog < 0.8f) Color(0xFFFF8A1F) else Color(0xFFFF2D2D)
        drawCircle(col.copy(alpha = (1f - prog) * 0.9f), (1.5f + 2.5f * (1f - prog)) * f.unit, p)
    }
}

private fun DrawScope.styleCyber(f: OrbFrame) {
    val a = f.col(0); val b = f.col(1)
    halo(f, a)
    drawCircle(Color(0xFF060A12), f.r, f.c)
    for (k in 0 until 4) {
        val rr = f.r * (0.45f + 0.15f * k)
        val rot = f.ph * 360f * (if (k % 2 == 0) 1f else -1f) * (0.6f + k * 0.2f)
        drawArc(a.copy(alpha = 0.9f), rot, 150f, false, topLeft = Offset(f.c.x - rr, f.c.y - rr), size = Size(rr * 2, rr * 2), style = Stroke(2f * f.unit, cap = StrokeCap.Round))
        for (j in 0 until 12) {
            val ang = j * (2f * PI.toFloat() / 12f) + rot / 57.2958f
            drawLine(b.copy(alpha = 0.7f), Offset(f.c.x + cos(ang) * rr * 0.97f, f.c.y + sin(ang) * rr * 0.97f), Offset(f.c.x + cos(ang) * rr * 1.03f, f.c.y + sin(ang) * rr * 1.03f), 1.5f * f.unit)
        }
    }
    drawCircle(b.copy(alpha = 0.6f + 0.4f * f.lv), f.r * 0.18f, f.c)
}

private fun DrawScope.styleAqua(f: OrbFrame) {
    val a = f.col(0); val b = f.col(1)
    halo(f, a)
    drawCircle(Color(0xFF031420), f.r, f.c)
    val cols = 56
    for (i in 0 until cols) {
        val nx = -1f + 2f * i / cols
        val x = f.c.x + nx * f.r
        val half = sqrt(max(0f, 1f - nx * nx)) * f.r
        val surf = f.c.y + f.r * 0.05f + sin(nx * 3f + f.ph * f.tau * 2f) * f.r * (0.05f + 0.1f * f.amp)
        val bottom = f.c.y + half
        if (surf < bottom) drawLine(if (i % 2 == 0) a.copy(alpha = 0.55f) else b.copy(alpha = 0.45f), Offset(x, surf), Offset(x, bottom), strokeWidth = 2f * f.r / cols + 1f)
    }
}

private fun DrawScope.styleMagic(f: OrbFrame) {
    val a = f.col(0); val b = f.col(1)
    halo(f, a)
    drawCircle(Brush.radialGradient(listOf(a.copy(alpha = 0.35f), Color(0xFF12051F)), center = f.c, radius = f.r), f.r, f.c)
    for (arm in 0 until 3) {
        val path = Path()
        for (s in 0..40) {
            val t = s / 40f
            val rr = f.r * t
            val ang = arm * f.tau / 3f + t * 4f + f.ph * f.tau
            val x = f.c.x + cos(ang) * rr
            val y = f.c.y + sin(ang) * rr
            if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, b.copy(alpha = 0.7f), style = Stroke(2f * f.unit, cap = StrokeCap.Round))
    }
    val n = f.count(40)
    for (i in 0 until n) {
        val ang = i * 0.91f + f.ph * f.tau
        val rr = f.r * (0.3f + 0.65f * (((i * 17) % 31) / 31f))
        drawCircle(a.copy(alpha = 0.9f), (1f + (i % 3)) * f.unit, Offset(f.c.x + cos(ang) * rr, f.c.y + sin(ang) * rr))
    }
}

private fun DrawScope.styleMatrix(f: OrbFrame) {
    val g = f.col(0)
    halo(f, g)
    drawCircle(Color(0xFF020A04), f.r, f.c)
    val cols = 16
    for (j in 0 until cols) {
        val nx = -1f + (j + 0.5f) * 2f / cols
        val x = f.c.x + nx * f.r
        val half = sqrt(max(0f, 1f - nx * nx)) * f.r
        val drops = f.count(5)
        for (q in 0 until drops) {
            val prog = (f.ph * (0.6f + (j % 4) * 0.2f) + j * 0.137f + q / drops.toFloat()) % 1f
            val y = f.c.y - half + prog * 2f * half
            drawCircle(g.copy(alpha = 0.35f + 0.55f * (1f - prog)), 1.6f * f.unit, Offset(x, y))
        }
    }
}

private fun DrawScope.styleChrome(f: OrbFrame) {
    halo(f, Color(0xFFBFC7D5))
    drawCircle(
        Brush.linearGradient(listOf(Color(0xFFF5F7FA), Color(0xFF6B7280), Color(0xFF1C1F26), Color(0xFFD1D5DB)),
            start = Offset(f.c.x - f.r, f.c.y - f.r), end = Offset(f.c.x + f.r, f.c.y + f.r)),
        f.r, f.c
    )
    for (k in 0 until 5) {
        val y = f.c.y - f.r + 2f * f.r * ((k / 5f + f.ph) % 1f)
        val dy = y - f.c.y
        val half = sqrt(max(0f, f.r * f.r - dy * dy))
        drawLine(Color.White.copy(alpha = 0.35f), Offset(f.c.x - half, y), Offset(f.c.x + half, y), 2f * f.unit)
    }
    drawCircle(Color(0xFF9CA3AF), f.r, f.c, style = Stroke(2f * f.unit))
}

private fun DrawScope.styleAurora(f: OrbFrame) {
    halo(f, f.col(0))
    drawCircle(Color(0xFF050714), f.r, f.c)
    for (k in 0 until 6) {
        val path = Path()
        for (s in 0..40) {
            val nx = -1f + 2f * s / 40
            val x = f.c.x + nx * f.r
            val y = f.c.y + (k - 2.5f) * f.r * 0.22f + sin(nx * 3.2f + f.ph * f.tau + k * 0.8f) * f.r * (0.12f + 0.1f * f.lv)
            if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, f.col(k).copy(alpha = 0.65f), style = Stroke(3f * f.unit, cap = StrokeCap.Round))
    }
}

private fun DrawScope.styleParticle(f: OrbFrame) {
    halo(f, f.col(0))
    var n = f.count(DOTS)
    if (!f.particles) n /= 3
    for (i in 0 until n) {
        val idx = (i % DOTS) * 3
        val d = 1f + f.amp * sin(f.ph * f.tau * 3 + SPHERE[idx + 1] * 7f)
        val x = SPHERE[idx] * d
        val y = SPHERE[idx + 1] * d
        val depth = ((SPHERE[idx + 2] + 1f) / 2f).coerceIn(0f, 1f)
        drawCircle(f.col(i).copy(alpha = 0.15f + 0.85f * depth), (0.8f + 2f * depth) * f.unit, Offset(f.c.x + x * f.r, f.c.y + y * f.r))
    }
}

private fun DrawScope.styleReactor(f: OrbFrame) {
    val red = f.col(0)
    halo(f, red)
    drawCircle(Brush.radialGradient(listOf(Color(0xFF2A0606), Color(0xFF0C0404)), center = f.c, radius = f.r), f.r, f.c)
    for (k in 0 until 3) {
        val rr = f.r * (0.5f + 0.2f * k)
        val rot = f.ph * 360f * (if (k % 2 == 0) 1f else -1f) * (0.8f + k * 0.3f)
        for (seg in 0 until 6) {
            drawArc(red.copy(alpha = 0.9f), rot + seg * 60f, 40f, false, topLeft = Offset(f.c.x - rr, f.c.y - rr), size = Size(rr * 2, rr * 2), style = Stroke(4f * f.unit, cap = StrokeCap.Butt))
        }
    }
    val pulse = 0.7f + 0.3f * sin(f.ph * f.tau * 2f) + f.lv * 0.3f
    drawCircle(Brush.radialGradient(listOf(Color.White, red, Color.Transparent), center = f.c, radius = f.r * 0.3f * pulse), f.r * 0.3f * pulse, f.c)
}

/** Shared lifecycle-safe orb. Animation values are read in the draw phase only. */
@Composable
fun AlicOrb(state: OrbState, level: Float, modifier: Modifier = Modifier, animated: Boolean = true, style: String? = null) {
    val t = rememberInfiniteTransition(label = "orb")
    val phaseS = t.animateFloat(0f, 1f, infiniteRepeatable(tween(12000, easing = LinearEasing)), label = "phase")
    val breatheS = t.animateFloat(0.95f, 1.05f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
    val lvS = animateFloatAsState(level, tween(120), label = "level")

    Canvas(modifier.aspectRatio(1f)) {
        val tau = (2 * PI).toFloat()
        val ph = if (animated) (phaseS.value * ThemeState.orbSpeed) % 1f else 0f
        val lv = lvS.value
        val unit = 1.dp.toPx()
        val base = size.minDimension / 2f * 0.80f * ThemeState.orbSize
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
        val frame = OrbFrame(
            c = center, r = base * pulse, ph = ph, lv = lv, amp = amp, tau = tau, unit = unit,
            pal = C.orb.take(6), glow = if (ThemeState.orbGlowOn) ThemeState.orbGlow else 0f,
            dens = ThemeState.orbDensity, particles = ThemeState.orbParticles, dark = ThemeState.isDark
        )
        when (normalizeOrb(style ?: ThemeState.orbStyle)) {
            "holo" -> styleHolo(frame)
            "galaxy" -> styleGalaxy(frame)
            "glass" -> styleGlass(frame)
            "plasma" -> stylePlasma(frame)
            "neon" -> styleNeon(frame)
            "pearl" -> stylePearl(frame)
            "fire" -> styleFire(frame)
            "cyber" -> styleCyber(frame)
            "aqua" -> styleAqua(frame)
            "magic" -> styleMagic(frame)
            "matrix" -> styleMatrix(frame)
            "chrome" -> styleChrome(frame)
            "aurora" -> styleAurora(frame)
            "reactor" -> styleReactor(frame)
            else -> styleParticle(frame)
        }
    }
}
