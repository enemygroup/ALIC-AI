package com.alic.ai.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

enum class PopupKind { SUCCESS, ERROR }

/** A popup shown only after a real result (Firebase or backend). [onContinue] runs when the button is pressed. */
class PopupSpec(
    val kind: PopupKind,
    val title: String,
    val message: String,
    val details: List<Pair<String, String>> = emptyList(),
    val buttonText: String = "Continue",
    val crown: Boolean = false,
    val onContinue: () -> Unit = {}
)

private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t

/**
 * Animated popup: dark blurred scrim, glowing ring, drawn checkmark (or warning mark), particles,
 * card scales from 0.8 to 1, title fades in, then details and the button appear.
 */
@Composable
fun PremiumPopup(spec: PopupSpec?) {
    if (spec == null) return
    val haptic = LocalHapticFeedback.current
    val progress = remember(spec) { Animatable(0f) }
    val scale = remember(spec) { Animatable(0.8f) }
    val textAlpha = remember(spec) { Animatable(0f) }
    LaunchedEffect(spec) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        launch { scale.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 300f)) }
        launch { progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
        textAlpha.animateTo(1f, tween(400, delayMillis = 500))
    }
    val success = spec.kind == PopupKind.SUCCESS
    val gold = success && ThemeState.planTier == com.alic.ai.licence.PlanTier.LIFETIME
    val accent = if (gold) Color(0xFFFFD54A) else if (success) Color(0xFF39FF7A) else Color(0xFFFF9A3D)
    val accent2 = if (gold) Color(0xFFFF9800) else if (success) Color(0xFF00D95A) else Color(0xFFFF4D4D)

    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xD9000000)), contentAlignment = Alignment.Center) {
            Column(
                Modifier.padding(26.dp)
                    .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xF2082614), Color(0xF2020A05))))
                    .border(1.5.dp, Brush.linearGradient(listOf(accent, accent2, accent)), RoundedCornerShape(28.dp))
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Ring, particles and the drawn mark
                Box(Modifier.size(130.dp), contentAlignment = Alignment.Center) {
                    val p = progress.value
                    Canvas(Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val c = Offset(w / 2f, h / 2f)
                        val r = w / 2f - 10f
                        // glow ring
                        drawCircle(accent.copy(alpha = 0.18f * p), radius = r + 8f, center = c, style = Stroke(16f))
                        drawCircle(accent.copy(alpha = 0.85f * p), radius = r, center = c, style = Stroke(4f))
                        // particles bursting outward
                        for (i in 0 until 14) {
                            val ang = i * (2.0 * Math.PI / 14.0)
                            val dist = r * (0.55f + 0.7f * p)
                            val pos = Offset(c.x + (cos(ang) * dist).toFloat(), c.y + (sin(ang) * dist).toFloat())
                            drawCircle(accent.copy(alpha = (1f - p) * 0.9f), radius = 3f + (i % 3), center = pos)
                        }
                        if (success) {
                            val p1 = Offset(w * 0.28f, h * 0.52f)
                            val p2 = Offset(w * 0.44f, h * 0.68f)
                            val p3 = Offset(w * 0.74f, h * 0.36f)
                            val f1 = (p / 0.4f).coerceIn(0f, 1f)
                            val end1 = Offset(lerpF(p1.x, p2.x, f1), lerpF(p1.y, p2.y, f1))
                            drawLine(accent, p1, end1, strokeWidth = 9f, cap = StrokeCap.Round)
                            if (p > 0.4f) {
                                val f2 = ((p - 0.4f) / 0.6f).coerceIn(0f, 1f)
                                val end2 = Offset(lerpF(p2.x, p3.x, f2), lerpF(p2.y, p3.y, f2))
                                drawLine(accent, p2, end2, strokeWidth = 9f, cap = StrokeCap.Round)
                            }
                        } else {
                            val top = Offset(w * 0.5f, h * 0.26f)
                            val bottom = Offset(w * 0.5f, lerpF(h * 0.26f, h * 0.6f, p.coerceIn(0f, 1f)))
                            drawLine(accent, top, bottom, strokeWidth = 9f, cap = StrokeCap.Round)
                            if (p > 0.8f) drawCircle(accent, radius = 6f, center = Offset(w * 0.5f, h * 0.76f))
                        }
                    }
                }
                if (spec.crown) {
                    Text(uiBold("👑"), fontSize = 26.sp, modifier = Modifier.graphicsLayer { alpha = textAlpha.value })
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    uiBold(spec.title), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = textAlpha.value }
                )
                Text(
                    uiBold(spec.message), color = Color(0xFFB7CDBD), fontSize = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).graphicsLayer { alpha = textAlpha.value }
                )
                if (spec.details.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(Color(0x33122E18))
                            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .padding(14.dp)
                    ) {
                        spec.details.forEach { (k, v) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(uiBold(k), color = Color(0xFFB7CDBD), fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Text(v, color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Box(
                    Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp))
                        .background(Brush.horizontalGradient(listOf(accent2, accent, accent2)))
                        .clickable { spec.onContinue() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(uiBold(spec.buttonText), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            }
        }
    }
}
