package com.alic.ai.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** An accent colour set: buttons (primary), highlights (neon), deep shade and the multi-colour orb gradient. */
data class Accent(val id: String, val label: String, val primary: Color, val neon: Color, val deep: Color, val orb: List<Color>)

object Accents {
    val Emerald = Accent(
        "emerald", "Emerald", Color(0xFF00D98B), Color(0xFF00FF9D), Color(0xFF00A86B),
        listOf(Color(0xFF00FF9D), Color(0xFF00D98B), Color(0xFF00A86B), Color(0xFF7DFFC9))
    )
    val Aurora = Accent(
        "aurora", "Aurora", Color(0xFFFF2D55), Color(0xFF22E4FF), Color(0xFF5B2BD6),
        listOf(Color(0xFF22E4FF), Color(0xFF2F6BFF), Color(0xFFA24DFF), Color(0xFFFF2D55))
    )
    val Red = Accent("red", "Red", Color(0xFFFF1E2D), Color(0xFFFF303F), Color(0xFF8B0000), listOf(Color(0xFFFF6A72), Color(0xFFFF1E2D), Color(0xFFFF303F)))
    val Cyan = Accent("cyan", "Cyan", Color(0xFF00BFD8), Color(0xFF3DEBFF), Color(0xFF00677A), listOf(Color(0xFF8CF5FF), Color(0xFF00BFD8), Color(0xFF3DEBFF)))
    val Blue = Accent("blue", "Blue", Color(0xFF2F6BFF), Color(0xFF6EA0FF), Color(0xFF1636A8), listOf(Color(0xFF9DBEFF), Color(0xFF2F6BFF), Color(0xFF6EA0FF)))
    val Purple = Accent("purple", "Purple", Color(0xFF9B5CFF), Color(0xFFC79BFF), Color(0xFF5420B0), listOf(Color(0xFFD9BCFF), Color(0xFF9B5CFF), Color(0xFFC79BFF)))
    val Sunset = Accent(
        "sunset", "Sunset", Color(0xFFFF7A3D), Color(0xFFFF5E8A), Color(0xFF8E2C8E),
        listOf(Color(0xFFFFC46B), Color(0xFFFF7A3D), Color(0xFFFF5E8A), Color(0xFFB05CFF))
    )
    val choices = listOf(Emerald, Aurora, Red, Cyan, Blue, Purple)
    fun byId(id: String): Accent = choices.firstOrNull { it.id == id } ?: Emerald
}

/** Global theme selection (Compose state, so every screen recolours instantly). */
object ThemeState {
    var mode by mutableStateOf("dark")      // dark, light, sunset
    var accentId by mutableStateOf("emerald")
    fun apply(m: String, a: String) { mode = m; accentId = a }
    val accent: Accent get() = if (mode == "sunset") Accents.Sunset else Accents.byId(accentId)
    val isDark: Boolean get() = mode != "light"
}

object C {
    private val mode get() = ThemeState.mode
    private val dark get() = ThemeState.isDark
    private val acc get() = ThemeState.accent

    val Bg: Color get() = when (mode) { "light" -> Color(0xFFF4F4F8); "sunset" -> Color(0xFF1B0A2E); else -> Color(0xFF050807) }
    val Bg2: Color get() = when (mode) { "light" -> Color(0xFFFFFFFF); "sunset" -> Color(0xFF2A1141); else -> Color(0xFF0A100D) }
    val Card: Color get() = when (mode) { "light" -> Color(0xFFECECF3); "sunset" -> Color(0xFF35174F); else -> Color(0xFF101A15) }
    val Text: Color get() = when (mode) { "light" -> Color(0xFF14141A); "sunset" -> Color(0xFFFFF1E6); else -> Color(0xFFFFFFFF) }
    val Sub: Color get() = when (mode) { "light" -> Color(0xFF5E6068); "sunset" -> Color(0xFFD3B3C9); else -> Color(0xFFA3B5AA) }
    val Border: Color get() = when (mode) { "light" -> Color(0xFFD2D2DC); "sunset" -> Color(0xFF5B2D63); else -> Color(0xFF244C38) }

    val Red: Color get() = acc.primary
    val Neon: Color get() = if (dark) acc.neon else acc.primary
    val Dark: Color get() = acc.deep
    /** Text/icons drawn on the primary colour: near-black on emerald, white on the other accents. */
    val OnPrimary: Color get() = if (ThemeState.accent.id == "emerald") Color(0xFF031109) else Color.White
    val Muted: Color get() = when (mode) { "light" -> Color(0xFF8A8D96); "sunset" -> Color(0xFFA88FA3); else -> Color(0xFF64756B) }
    val Err = Color(0xFFFF5252)
    val orb: List<Color> get() = acc.orb

    val Cyan = Color(0xFF22E4FF)
    val Blue = Color(0xFF2F6BFF)
    val Purple = Color(0xFFA24DFF)

    val Glass1: Color get() = when (mode) { "light" -> Color(0xFFFFFFFF); "sunset" -> Color(0x30FFFFFF); else -> Color(0xCC14231B) }
    val Glass2: Color get() = when (mode) { "light" -> Color(0xFFF1F1F7); "sunset" -> Color(0x10FFFFFF); else -> Color(0x990A100D) }
    val Glass: Color get() = when (mode) { "light" -> Color(0x14000000); "sunset" -> Color(0x26FFFFFF); else -> Color(0x6614231B) }
    val BgTop: Color get() = lerp(Bg, acc.deep, if (dark) 0.30f else 0.10f)
}

fun screenBg(): Brush = Brush.verticalGradient(listOf(C.BgTop, C.Bg, C.Bg))

@Composable
fun AlicTheme(content: @Composable () -> Unit) {
    val scheme = if (ThemeState.isDark) darkColorScheme(
        primary = C.Red, onPrimary = Color.White, secondary = C.Neon, onSecondary = Color.White,
        background = C.Bg, onBackground = C.Text, surface = C.Bg2, onSurface = C.Text,
        surfaceVariant = C.Card, onSurfaceVariant = C.Sub, outline = C.Border, error = C.Neon,
        primaryContainer = C.Dark, onPrimaryContainer = Color.White, surfaceContainer = C.Card
    ) else lightColorScheme(
        primary = C.Red, onPrimary = Color.White, secondary = C.Neon, onSecondary = Color.White,
        background = C.Bg, onBackground = C.Text, surface = C.Bg2, onSurface = C.Text,
        surfaceVariant = C.Card, onSurfaceVariant = C.Sub, outline = C.Border, error = C.Neon,
        primaryContainer = C.Dark, onPrimaryContainer = Color.White, surfaceContainer = C.Card
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(C.Glass1, C.Glass2)))
            .border(1.dp, C.Border, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
        content = content
    )
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = C.Red, unfocusedBorderColor = C.Border, cursorColor = C.Red,
    focusedContainerColor = C.Bg2, unfocusedContainerColor = C.Bg2,
    focusedLabelColor = C.Red, unfocusedLabelColor = C.Sub,
    focusedTextColor = C.Text, unfocusedTextColor = C.Text
)

@Composable
fun RedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.height(48.dp), shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = C.Red, contentColor = C.OnPrimary, disabledContainerColor = C.Dark.copy(alpha = 0.4f), disabledContentColor = C.Sub)
    ) {
        if (icon != null) { androidx.compose.material3.Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    OutlinedButton(
        onClick = onClick, modifier = modifier.height(44.dp), shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, C.Red), colors = ButtonDefaults.outlinedButtonColors(contentColor = C.Text)
    ) {
        if (icon != null) { androidx.compose.material3.Icon(icon, null, Modifier.size(18.dp), tint = C.Neon); Spacer(Modifier.width(8.dp)) }
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp).height(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = C.Text) }
        } else Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.Text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

@Composable
fun StatusDot(on: Boolean) {
    Box(Modifier.size(9.dp).clip(CircleShape).background(if (on) Color(0xFF2ECC71) else C.Err))
}

fun shareText(ctx: Context, text: String) {
    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    ctx.startActivity(Intent.createChooser(i, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
