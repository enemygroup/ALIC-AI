package com.alic.ai.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

typealias Gate = (() -> Unit) -> Unit

/** Minimal Markdown: headings, **bold**, `inline code`, bullet lists. */
fun md(src: String): AnnotatedString = buildAnnotatedString {
    val lines = src.lines()
    val rx = Regex("\\*\\*(.+?)\\*\\*|`([^`]+)`")
    lines.forEachIndexed { li, raw ->
        var line = raw
        var heading = false
        val h = Regex("^#{1,6}\\s+(.*)").find(line)
        if (h != null) { line = h.groupValues[1]; heading = true }
        line = line.replace(Regex("^(\\s*)[*-]\\s+"), "\$1• ")
        val start = length
        var idx = 0
        for (m in rx.findAll(line)) {
            append(line.substring(idx, m.range.first))
            if (m.groups[1] != null) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[1]) }
            else withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0xFF2A2A2A), color = C.Neon)) { append(m.groupValues[2]) }
            idx = m.range.last + 1
        }
        append(line.substring(idx))
        if (heading) addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = C.Text), start, length)
        if (li < lines.lastIndex) append("\n")
    }
}

@Composable
fun MessageContent(text: String, color: Color = C.Text) {
    val clip = LocalClipboardManager.current
    val parts = text.split("```")
    Column {
        parts.forEachIndexed { i, seg ->
            if (i % 2 == 0) {
                if (seg.isNotBlank()) Text(md(seg.trim('\n')), color = color, fontSize = 15.sp, lineHeight = 21.sp)
            } else {
                val nl = seg.indexOf('\n')
                val lang = if (nl >= 0) seg.substring(0, nl).trim() else ""
                val code = (if (nl >= 0) seg.substring(nl + 1) else seg).trimEnd()
                val shape = RoundedCornerShape(10.dp)
                Column(
                    Modifier.padding(vertical = 6.dp).fillMaxWidth().clip(shape)
                        .background(Color(0xFF0C0C0C)).border(1.dp, C.Border, shape)
                ) {
                    Row(
                        Modifier.fillMaxWidth().background(C.Dark.copy(alpha = 0.35f)).padding(start = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(lang.ifBlank { "code" }, color = C.Neon, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { clip.setText(AnnotatedString(code)) }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Filled.ContentCopy, "Copy code", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                    Text(
                        code, fontFamily = FontFamily.Monospace, fontSize = 12.5.sp, color = Color(0xFFEDEDED),
                        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(10.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TypingDots() {
    val t = rememberInfiniteTransition(label = "dots")
    Row(Modifier.padding(vertical = 6.dp)) {
        repeat(3) { i ->
            val a by t.animateFloat(
                0.2f, 1f, infiniteRepeatable(tween(600, delayMillis = i * 160), RepeatMode.Reverse), label = "dot$i"
            )
            Box(Modifier.padding(horizontal = 3.dp).size(8.dp).clip(CircleShape).background(C.Neon.copy(alpha = a)))
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, color = C.Neon, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
}

@Composable
fun SwitchRow(title: String, sub: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = C.Text, fontSize = 15.sp)
            if (sub != null) Text(sub, color = C.Sub, fontSize = 12.sp)
        }
        Switch(
            checked, onChange, colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = C.Red, uncheckedThumbColor = C.Sub,
                uncheckedTrackColor = C.Card, uncheckedBorderColor = C.Border
            )
        )
    }
}

@Composable
fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, label: String, onChange: (Float) -> Unit) {
    var v by remember(value) { mutableStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row {
            Text(title, color = C.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(label.format(v), color = C.Sub, fontSize = 13.sp)
        }
        Slider(
            v, { v = it }, valueRange = range, onValueChangeFinished = { onChange(v) },
            colors = SliderDefaults.colors(thumbColor = C.Red, activeTrackColor = C.Red, inactiveTrackColor = C.Border)
        )
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.clip(shape)
            .background(if (selected) C.Red else C.Card)
            .border(1.dp, if (selected) C.Red else C.Border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) { Text(text, color = if (selected) C.OnPrimary else C.Text, fontSize = 13.sp, maxLines = 1) }
}

@Composable
fun ChipsRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) -> Chip(label, value == selected) { onSelect(value) } }
    }
}

/** Colour swatches for the accent choices (Aurora shows the full cyan-blue-purple-red gradient). */
@Composable
fun AccentPicker(selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Accents.choices.forEach { a ->
            val on = a.id == selected
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onSelect(a.id) }) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Brush.linearGradient(a.orb))
                        .border(if (on) 3.dp else 1.dp, if (on) C.Text else C.Border, CircleShape)
                )
                Text(a.label, color = if (on) C.Text else C.Sub, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** Collapsible settings section: icon + title header, content shown when expanded. Keeps long settings pages short. */
@Composable
fun SettingsGroup(title: String, icon: ImageVector, startOpen: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(startOpen) }
    GlassCard(Modifier.fillMaxWidth().padding(bottom = 10.dp).animateContentSize()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { open = !open }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(C.Red, C.Dark))),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = C.OnPrimary, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(12.dp))
            Text(title, color = C.Text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, if (open) "Collapse" else "Expand", tint = C.Sub)
        }
        if (open) {
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

/** Small rounded status chip (coloured dot + text). */
@Composable
fun StatusPill(text: String, ok: Boolean, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(C.Glass)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(ok); Spacer(Modifier.width(6.dp))
        Text(text, color = C.Text, fontSize = 11.sp, maxLines = 1)
    }
}
