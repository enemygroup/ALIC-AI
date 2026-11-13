package com.alic.ai.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Emerald glow used for card borders. */
fun glowBrush(): Brush = Brush.linearGradient(listOf(C.Neon.copy(alpha = 0.85f), C.Dark.copy(alpha = 0.45f), C.Neon.copy(alpha = 0.65f)))

/** Glass card: translucent surface, emerald glowing border, soft shadow. */
@Composable
fun NeoCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, selected: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .shadow(if (selected) 10.dp else 3.dp, shape, ambientColor = C.Red.copy(alpha = 0.5f), spotColor = C.Red.copy(alpha = 0.5f))
            .clip(shape)
            .background(Brush.verticalGradient(listOf(C.Glass1, C.Glass2)))
            .border(if (selected) 2.dp else 1.dp, glowBrush(), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
        content = content
    )
}

@Composable
fun GradientButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    Row(
        modifier.height(54.dp).clip(RoundedCornerShape(27.dp))
            .background(if (enabled) Brush.horizontalGradient(listOf(C.Red, C.Dark)) else Brush.horizontalGradient(listOf(C.Border, C.Border)))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) { Icon(icon, null, tint = C.OnPrimary, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = if (enabled) C.OnPrimary else C.Muted, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable
fun IconBadge(icon: ImageVector, sizeDp: Dp = 44.dp, tint: Color = C.OnPrimary) {
    Box(
        Modifier.size(sizeDp).clip(RoundedCornerShape(sizeDp * 0.3f)).background(Brush.linearGradient(listOf(C.Red, C.Dark))),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(sizeDp * 0.5f)) }
}

/** Circular avatar: picture when available, otherwise the first letter on an emerald gradient. */
@Composable
fun Avatar(label: String, sizeDp: Dp = 44.dp, picture: Bitmap? = null) {
    val ring = Modifier.size(sizeDp).clip(CircleShape).border(2.dp, glowBrush(), CircleShape)
    if (picture != null) {
        Image(picture.asImageBitmap(), "Profile picture", ring, contentScale = androidx.compose.ui.layout.ContentScale.Crop)
    } else {
        Box(ring.background(Brush.linearGradient(listOf(C.Red, C.Dark))), contentAlignment = Alignment.Center) {
            Text(label.take(1).uppercase().ifBlank { "A" }, color = C.OnPrimary, fontWeight = FontWeight.Bold, fontSize = (sizeDp.value * 0.42f).sp)
        }
    }
}

/** Settings-style row: icon badge, title, optional subtitle, chevron or custom trailing content. */
@Composable
fun ListRow(
    icon: ImageVector, title: String, subtitle: String? = null, titleColor: Color = C.Text,
    onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null
) {
    NeoCard(Modifier.fillMaxWidth().padding(bottom = 10.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = titleColor, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                if (subtitle != null) Text(subtitle, color = C.Sub, fontSize = 12.sp, maxLines = 2)
            }
            if (trailing != null) trailing() else if (onClick != null) Icon(Icons.Filled.ChevronRight, null, tint = C.Sub)
        }
    }
}

/**
 * Waveform driven by the REAL microphone level of the live session. When [active] is false it shows a calm flat
 * line instead of fake movement. Animation values are read in the draw phase (no recomposition).
 */
@Composable
fun LevelWave(level: Float, active: Boolean, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "wave")
    val ph = t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "ph")
    val lv = animateFloatAsState(level, tween(100), label = "lv")
    Canvas(modifier.fillMaxWidth().height(60.dp)) {
        val n = 48
        val slot = this.size.width / n
        val bw = slot * 0.5f
        val amp = if (active) 0.14f + lv.value.coerceIn(0f, 1f) * 0.85f else 0f
        val pal = C.orb
        for (i in 0 until n) {
            val env = 1f - abs(i - n / 2f) / (n / 2f) * 0.75f
            val w = 0.5f + 0.5f * sin(ph.value * 2f * PI.toFloat() * 3f + i * 0.5f)
            val h = this.size.height * (0.06f + amp * w * env).coerceIn(0.04f, 1f)
            drawRoundRect(
                color = pal[i % pal.size].copy(alpha = if (active) 0.95f else 0.45f),
                topLeft = Offset(i * slot + (slot - bw) / 2f, (this.size.height - h) / 2f),
                size = Size(bw, h), cornerRadius = CornerRadius(bw / 2f)
            )
        }
    }
}

/** The "A" logo mark, drawn as vector paths (no image assets). */
@Composable
fun LogoMark(sizeDp: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(sizeDp)) {
        val w = this.size.width
        val h = this.size.height
        val brush = Brush.linearGradient(listOf(C.Neon, C.Red, C.Dark), start = Offset(0f, 0f), end = Offset(w, h))
        val leg = Path().apply {
            moveTo(w * 0.16f, h * 0.88f); lineTo(w * 0.50f, h * 0.12f); lineTo(w * 0.84f, h * 0.88f)
        }
        drawPath(leg, brush, style = Stroke(width = w * 0.15f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val bar = Path().apply {
            moveTo(w * 0.31f, h * 0.64f); quadraticBezierTo(w * 0.52f, h * 0.50f, w * 0.76f, h * 0.66f)
        }
        drawPath(bar, brush, style = Stroke(width = w * 0.09f, cap = StrokeCap.Round))
    }
}

private data class NavItem(val route: String, val label: String, val icon: ImageVector)

private val NAV_ITEMS = listOf(
    NavItem("home", "Home", Icons.Filled.Home),
    NavItem("chat", "Chat", Icons.Filled.ChatBubble),
    NavItem("live", "Voice", Icons.Filled.Mic),
    NavItem("tools", "Tools", Icons.Filled.GridView),
    NavItem("profile", "Profile", Icons.Filled.Person)
)

/** Bottom navigation: Home, Chat, Voice, Tools, Profile. "Voice" starts a real Live session. */
@Composable
fun AppBottomBar(route: String?, onSelect: (String) -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).clip(shape)
            .background(Brush.verticalGradient(listOf(C.Glass1, C.Glass2)))
            .border(1.dp, glowBrush(), shape).padding(6.dp),
        horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically
    ) {
        NAV_ITEMS.forEach { item ->
            val sel = route == item.route
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                    .background(if (sel) C.Red else Color.Transparent)
                    .clickable { onSelect(item.route) }.padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(item.icon, item.label, tint = if (sel) C.OnPrimary else C.Sub, modifier = Modifier.size(22.dp))
                Text(item.label, fontSize = 10.sp, color = if (sel) C.OnPrimary else C.Sub, maxLines = 1)
            }
        }
    }
}
