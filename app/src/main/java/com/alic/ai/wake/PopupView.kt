package com.alic.ai.wake

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import com.alic.ai.live.LiveSessionController.Phase
import com.alic.ai.ui.ThemeState
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

private fun orbColors(): IntArray = ThemeState.accent.orb.map { it.toArgb() }.toIntArray()
private fun withAlpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a shl 24)
private const val TAU = (2 * PI).toFloat()
private const val FRAME_MS = 33L

/** Shared frame clock for the popup: 30 frames per second, stopped when the view detaches. */
private class FrameLoop(private val onFrame: (Float) -> Unit) {
    private val h = Handler(Looper.getMainLooper())
    private var t = 0f
    private var running = false
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            t = (t + 0.0235f) % 1f
            onFrame(t)
            h.postDelayed(this, FRAME_MS)
        }
    }
    fun start() { if (!running) { running = true; h.post(tick) } }
    fun stop() { running = false; h.removeCallbacks(tick) }
}

/** Glowing circular orb. Gradients are cached and rebuilt only when the size changes. */
class OrbView(ctx: Context) : View(ctx) {
    var t = 0f
    var level = 0f
    var phase = Phase.LISTENING
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var outer: RadialGradient? = null
    private var core: RadialGradient? = null
    private var builtFor = -1

    private fun build(r: Float, pal: IntArray) {
        val cx = width / 2f
        val cy = height / 2f
        outer = RadialGradient(cx, cy, r, intArrayOf(withAlpha(pal[0], 0xAA), withAlpha(pal.last(), 0x44), Color.TRANSPARENT), floatArrayOf(0.3f, 0.7f, 1f), Shader.TileMode.CLAMP)
        core = RadialGradient(cx, cy, r * 0.62f, intArrayOf(Color.WHITE, pal[0], pal[pal.size / 2], pal.last()), floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP)
        builtFor = width * 10000 + height
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f; val cy = height / 2f; val r = min(width, height) / 2f
        val pal = orbColors()
        if (builtFor != width * 10000 + height) build(r, pal)
        val pulse = when (phase) {
            Phase.SPEAKING -> 0.85f + 0.10f * sin(t * TAU * 2)
            Phase.THINKING -> 0.80f + 0.06f * sin(t * TAU * 3)
            Phase.LISTENING -> 0.72f + 0.28f * level.coerceIn(0f, 1f)
        }
        p.style = Paint.Style.FILL
        p.shader = outer
        c.drawCircle(cx, cy, r, p)
        val cr = r * 0.62f * pulse
        p.shader = core
        c.drawCircle(cx, cy, cr, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.06f
        p.color = withAlpha(pal[pal.size / 2], 0xCC)
        c.drawCircle(cx, cy, r * 0.82f, p)
    }
}

/** Animated waveform bars. */
class WaveView(ctx: Context) : View(ctx) {
    var t = 0f
    var level = 0f
    var phase = Phase.LISTENING
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val pal = orbColors()
        val n = 22
        val slot = width / n.toFloat()
        val bw = slot * 0.55f
        val amp = when (phase) { Phase.LISTENING -> 0.10f + level.coerceIn(0f, 1f) * 0.9f; Phase.THINKING -> 0.18f; Phase.SPEAKING -> 0.65f }
        for (i in 0 until n) {
            val w = 0.5f + 0.5f * sin(t * TAU * 3 + i * 0.55f)
            val h = height * (0.12f + amp * w * 0.88f).coerceIn(0.08f, 1f)
            val x = i * slot + (slot - bw) / 2f
            p.color = pal[i % pal.size]
            c.drawRoundRect(x, (height - h) / 2f, x + bw, (height + h) / 2f, bw / 2f, bw / 2f, p)
        }
    }
}

/**
 * The compact dark card: orb, "ALIC AI" title, state label, waveform and a × button. Shared by the WindowManager
 * overlay. It runs a 30 fps loop only while attached, and stops it on detach so nothing leaks.
 */
class PopupView(ctx: Context, private val entranceAnimation: Boolean, private val onClose: () -> Unit) : FrameLayout(ctx) {
    private val d = ctx.resources.displayMetrics.density
    private fun dp(v: Int) = (v * d).toInt()
    private val orb = OrbView(ctx)
    private val wave = WaveView(ctx)
    private val stateText = TextView(ctx)
    private var shownPhase: Phase? = null
    private val frames = FrameLoop { t ->
        orb.t = t; wave.t = t
        orb.invalidate(); wave.invalidate()
    }

    init {
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(0xF2101014.toInt()); cornerRadius = dp(28).toFloat(); setStroke(dp(1), 0x33FFFFFF)
            }
            setPadding(dp(16), dp(14), dp(6), dp(14))
        }
        card.addView(orb, LinearLayout.LayoutParams(dp(56), dp(56)))
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(ctx).apply { text = "ALIC AI"; setTextColor(Color.WHITE); textSize = 16f; typeface = Typeface.DEFAULT_BOLD })
        stateText.apply { setTextColor(0xFFB8B8C0.toInt()); textSize = 13f }
        col.addView(stateText)
        col.addView(wave, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(24)).apply { topMargin = dp(6) })
        card.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(14) })
        card.addView(TextView(ctx).apply {
            text = "×"; textSize = 28f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; contentDescription = "Close"
            setOnClickListener { onClose() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        render(Phase.LISTENING, 0f)
    }

    fun render(phase: Phase, level: Float) {
        if (phase != shownPhase) {
            shownPhase = phase
            stateText.text = when (phase) {
                Phase.LISTENING -> "I'm listening..."
                Phase.THINKING -> "Thinking..."
                Phase.SPEAKING -> "Alic is speaking..."
            }
        }
        orb.phase = phase; orb.level = level; wave.phase = phase; wave.level = level
    }

    fun playEntrance() {
        if (!entranceAnimation) return
        alpha = 0f; translationY = dp(36).toFloat()
        animate().alpha(1f).translationY(0f).setDuration(220).start()
    }

    /** Stops the frame loop and cancels view animations. Safe to call more than once. */
    fun release() {
        frames.stop()
        animate().cancel()
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); frames.start() }
    override fun onDetachedFromWindow() { release(); super.onDetachedFromWindow() }
}
