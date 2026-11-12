package com.alic.ai.wake

import android.animation.ValueAnimator
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
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import com.alic.ai.live.LiveSessionController
import com.alic.ai.live.LiveSessionController.Phase
import com.alic.ai.ui.ThemeState
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

private fun orbColors(): IntArray = ThemeState.accent.orb.map { it.toArgb() }.toIntArray()
private fun withAlpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a shl 24)
private const val TAU = (2 * PI).toFloat()

/** Glowing circular orb. */
class OrbView(ctx: Context) : View(ctx) {
    var t = 0f
    var level = 0f
    var phase = Phase.LISTENING
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val cx = width / 2f; val cy = height / 2f; val r = min(width, height) / 2f
        val pal = orbColors()
        val pulse = when (phase) {
            Phase.SPEAKING -> 0.85f + 0.10f * sin(t * TAU * 2)
            Phase.THINKING -> 0.80f + 0.06f * sin(t * TAU * 3)
            Phase.LISTENING -> 0.72f + 0.28f * level.coerceIn(0f, 1f)
        }
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx, cy, r, intArrayOf(withAlpha(pal[0], 0xAA), withAlpha(pal.last(), 0x44), Color.TRANSPARENT), floatArrayOf(0.3f, 0.7f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, p)
        val cr = r * 0.62f * pulse
        p.shader = RadialGradient(cx, cy, cr, intArrayOf(Color.WHITE, pal[0], pal[pal.size / 2], pal.last()), floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP)
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
        val n = 26
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
 * The compact dark card: orb, "ALIC AI" title, state label, waveform and a × button. No text input.
 * Shared by the WindowManager overlay and the VoiceInteractionSession content view.
 */
class PopupView(ctx: Context, private val entranceAnimation: Boolean, private val onClose: () -> Unit) : FrameLayout(ctx) {
    private val d = ctx.resources.displayMetrics.density
    private fun dp(v: Int) = (v * d).toInt()
    private val orb = OrbView(ctx)
    private val wave = WaveView(ctx)
    private val stateText = TextView(ctx)
    private var anim: ValueAnimator? = null
    private var shownPhase: Phase? = null

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

    private fun startAnim() {
        if (anim != null) return
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1400; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
            addUpdateListener { orb.t = it.animatedValue as Float; wave.t = orb.t; orb.invalidate(); wave.invalidate() }
            start()
        }
    }

    /** Cancels animations/callbacks. Safe to call more than once. */
    fun release() {
        anim?.cancel(); anim = null
        animate().cancel()
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); startAnim() }
    override fun onDetachedFromWindow() { release(); super.onDetachedFromWindow() }
}
