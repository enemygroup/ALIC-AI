package com.alic.ai.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alic.ai.data.OrbState
import kotlinx.coroutines.delay

/** Stages follow REAL events: request sent -> first streamed token -> stream finished -> checks -> result. */
enum class CodeStage { IDLE, UNDERSTAND, WRITE, VALIDATE, DONE, ERROR }

private val RED = Color(0xFFFF1E2D)
private val NEON = Color(0xFFFF303F)
private val GREEN = Color(0xFF00E676)
private val MUTED = Color(0xFF7A5A5E)

/** Real checks on the generated reply. A bracket and fence check, not a compiler run. */
object CodeCheck {
    private val block = Regex("```([\\w+#.-]*)\\n([\\s\\S]*?)```")

    /** (language, code) for every complete fenced block in the reply. */
    fun blocks(text: String): List<Pair<String, String>> =
        block.findAll(text).map { it.groupValues[1].ifBlank { "text" } to it.groupValues[2] }.toList()

    fun run(text: String): Pair<Boolean, List<String>> {
        val lines = mutableListOf<String>()
        var ok = true
        if ((text.split("```").size - 1) % 2 == 1) {
            ok = false
            lines += "Reply ends inside an open code block (cut off)."
        }
        val found = blocks(text)
        if (found.isEmpty()) return false to (lines + "No complete code block was returned.")
        found.forEachIndexed { i, (lang, body) ->
            when {
                body.isBlank() -> { ok = false; lines += "Block ${i + 1} ($lang): empty" }
                balanced(body) -> lines += "Block ${i + 1} ($lang): brackets OK, ${body.lines().size} lines"
                else -> { ok = false; lines += "Block ${i + 1} ($lang): unbalanced brackets" }
            }
        }
        return ok to lines
    }

    fun balanced(src: String): Boolean {
        val noStr = src
            .replace(Regex("\"(\\\\.|[^\"\\\\\\n])*\""), "\"\"")
            .replace(Regex("'(\\\\.|[^'\\\\\\n])*'"), "''")
        val stack = ArrayDeque<Char>()
        val open = mapOf(')' to '(', ']' to '[', '}' to '{')
        for (ch in noStr) {
            when (ch) {
                '(', '[', '{' -> stack.addLast(ch)
                ')', ']', '}' -> if (stack.isEmpty() || stack.removeLast() != open[ch]) return false
            }
        }
        return stack.isEmpty()
    }

    fun fileName(lang: String): String {
        val ext = when (lang.lowercase()) {
            "kotlin", "kt" -> "kt"; "python", "py" -> "py"; "javascript", "js" -> "js"
            "html" -> "html"; "css" -> "css"; "java" -> "java"; "php" -> "php"; "json" -> "json"
            else -> "txt"
        }
        return "output.$ext"
    }
}

/** Matrix digital rain in the app's red palette. Runs in the draw phase only. */
@Composable
private fun MatrixRain(modifier: Modifier) {
    val phase = rememberInfiniteTransition(label = "rain").animateFloat(
        0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "rainPh"
    )
    val glyphs = remember { "01<>{}[]()=;/ABCDEF".toCharArray() }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE } }
    Canvas(modifier) {
        val cols = 18
        val colW = size.width / cols
        val rowH = colW * 1.2f
        val rows = size.height / rowH
        paint.textSize = colW * 0.9f
        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            for (c in 0 until cols) {
                val speed = 0.6f + (c % 5) * 0.25f
                val head = ((phase.value * speed + c * 0.137f) % 1f) * (rows + 14f)
                for (r in 0 until 14) {
                    val y = (head - r) * rowH
                    if (y < 0f || y > size.height) continue
                    val a = (255 * (1f - r / 14f) * 0.40f).toInt()
                    paint.color = (a shl 24) or 0x00FF1E2D
                    val idx = (c * 7 + r * 3 + (phase.value * 40).toInt()) % glyphs.size
                    nc.drawText(glyphs[idx].toString(), c * colW, y, paint)
                }
            }
        }
    }
}

@Composable
private fun ProgressRing(fraction: Float, label: String, sub: String) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(400), label = "ring")
    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            drawArc(Color(0x33FF1E2D), 0f, 360f, false, style = Stroke(stroke))
            drawArc(
                Brush.sweepGradient(listOf(NEON, RED, NEON)), -90f, 360f * f, false,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 26.sp, fontFamily = FontFamily.Monospace)
            Text(sub, color = MUTED, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun StageRow(label: String, index: Int, stage: CodeStage, failed: Boolean, pending: Boolean = false) {
    val cur = stage.ordinal
    val done = !pending && cur > index
    val active = !pending && cur == index
    val mark = when {
        pending -> "○"
        done && failed && index == 4 -> "✖"
        done -> "✔"
        active -> "▶"
        else -> "•"
    }
    val color = when {
        pending -> MUTED
        done && failed && index == 4 -> NEON
        done -> GREEN
        active -> NEON
        else -> MUTED
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(mark, color = color, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.width(8.dp))
        Text(label, color = if (pending) MUTED else Color.White, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF120404))
            .border(1.dp, RED.copy(alpha = 0.55f), RoundedCornerShape(12.dp)).padding(10.dp)
    ) {
        Text(title, color = MUTED, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, fontFamily = FontFamily.Monospace)
    }
}

/** Full-screen developer terminal, shown over any screen while a coding request runs or has finished. */
@Composable
fun CodingOverlay(vm: AlicViewModel) {
    val stage = vm.codeStage
    val busy = stage == CodeStage.UNDERSTAND || stage == CodeStage.WRITE || stage == CodeStage.VALIDATE
    val failed = stage == CodeStage.ERROR
    val done = stage == CodeStage.DONE
    val cursor by rememberInfiniteTransition(label = "cur").animateFloat(
        0f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "curA"
    )
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(busy) {
        while (busy) { now = System.currentTimeMillis(); delay(250) }
    }
    val elapsed = (((if (busy) now else vm.codeEndMs) - vm.codeStartMs).coerceAtLeast(0L)) / 1000.0
    val blocks = CodeCheck.blocks(vm.codeStream)
    val lineCount = blocks.sumOf { it.second.lines().size }
    val okCount = vm.codeChecks.count { it.contains("brackets OK") }
    val total = blocks.size
    val ringFraction = if (done) 1f else if (total == 0) 0f else okCount.toFloat() / total
    val statusLine = when (stage) {
        CodeStage.UNDERSTAND -> "ACTIVATING AI CORE..."
        CodeStage.WRITE -> "GENERATING SOURCE CODE..."
        CodeStage.VALIDATE -> "CHECKING SYNTAX..."
        CodeStage.DONE -> "CODE CHECKS PASSED"
        CodeStage.ERROR -> "CODE CHECKS FAILED"
        CodeStage.IDLE -> ""
    }

    var showPreview by remember { mutableStateOf(false) }
    var previewPage by remember { mutableStateOf("index.html") }
    val webDir = vm.webDir
    if (showPreview && webDir != null) {
        WebsitePreview(webDir, vm.webPages, previewPage, { previewPage = it }, { showPreview = false })
    }

    Dialog(
        onDismissRequest = { if (!busy) vm.dismissCoding() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !busy, dismissOnClickOutside = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            MatrixRain(Modifier.fillMaxSize())
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("ALIC AI DEVELOPER ENGINE", color = NEON, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 15.sp)
                Text(statusLine, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.height(12.dp))

                // Stage 1-2: AI core (orb). Stage 5: real validation ring.
                if (stage == CodeStage.VALIDATE || done || failed) {
                    ProgressRing(ringFraction, if (done) "OK" else "$okCount/$total", "blocks checked")
                } else {
                    Box(Modifier.size(150.dp)) { AlicOrb(if (busy) OrbState.THINKING else OrbState.IDLE, 0f, animated = true) }
                }
                Spacer(Modifier.height(12.dp))

                Column(Modifier.fillMaxWidth()) {
                    StageRow("Understanding your request", 1, stage, failed)
                    StageRow("Generating source code (live)", 2, stage, failed)
                    StageRow("Checking syntax and brackets", 3, stage, failed)
                    StageRow(if (failed) "Result: checks failed" else "Result", 4, stage, failed)
                    StageRow("APK build: not connected yet", 5, stage, failed, pending = true)
                }
                Spacer(Modifier.height(12.dp))

                // Live terminal: real streamed text only
                val shape = RoundedCornerShape(12.dp)
                val tail = vm.codeStream.lines().takeLast(14).joinToString("\n")
                Column(
                    Modifier.fillMaxWidth().height(170.dp).clip(shape).background(Color(0xFF080000))
                        .border(1.dp, RED.copy(alpha = 0.7f), shape).padding(10.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        if (vm.codeStream.isBlank() && busy) "> waiting for Gemini..." else "> " + tail,
                        color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp
                    )
                    if (busy) Text("█", color = NEON.copy(alpha = 0.3f + 0.7f * cursor), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }

                // Code preview: first complete block, real code, real file name
                val first = blocks.firstOrNull()
                if (first != null) {
                    Spacer(Modifier.height(12.dp))
                    Column(
                        Modifier.fillMaxWidth().clip(shape).background(Color(0xFF0C0404))
                            .border(1.dp, RED.copy(alpha = 0.5f), shape).padding(10.dp)
                    ) {
                        Text(CodeCheck.fileName(first.first), color = GREEN, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        Text(
                            first.second.lines().take(12).joinToString("\n"),
                            color = Color(0xFFEDEDED), fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }

                // Dashboard: all values measured from the run
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("TIME", "%.1f s".format(elapsed), Modifier.weight(1f))
                    StatCard("CODE BLOCKS", total.toString(), Modifier.weight(1f))
                    StatCard("LINES", lineCount.toString(), Modifier.weight(1f))
                }

                // Check results
                if (vm.codeChecks.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Column(Modifier.fillMaxWidth()) {
                        vm.codeChecks.forEach { line ->
                            val good = line.contains("brackets OK") || line.startsWith("Saved") || line.startsWith("ZIP:") || line.startsWith("Pages OK")
                            Text(
                                (if (good) "✔ " else "✖ ") + line, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                                color = if (good) GREEN else NEON
                            )
                        }
                    }
                }

                // Result card (only from real outcome)
                if (done || failed) {
                    Spacer(Modifier.height(12.dp))
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(if (done) GREEN.copy(alpha = 0.10f) else NEON.copy(alpha = 0.12f))
                            .border(1.dp, if (done) GREEN else NEON, RoundedCornerShape(14.dp)).padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            if (done) "✔ CODE READY" else "✖ NEEDS ATTENTION",
                            color = if (done) GREEN else NEON, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 15.sp
                        )
                        Text(
                            if (done) "Files are saved in Download/ALIC_AI."
                            else "Open the chat to read the reply and ask ALIC to fix the failing block.",
                            color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                if (done && webDir != null) {
                    Spacer(Modifier.height(12.dp))
                    GradientButton("Open website preview", { previewPage = "index.html"; showPreview = true }, Modifier.fillMaxWidth())
                }
                if (!busy) {
                    Spacer(Modifier.height(12.dp))
                    GradientButton("Close", { vm.dismissCoding() }, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** Shows the generated website from the app's private folder. Only local files are loaded. */
@Composable
private fun WebsitePreview(dir: String, pages: List<String>, page: String, onPage: (String) -> Unit, onClose: () -> Unit) {
    var loaded by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).padding(top = 28.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    ChipsRow(pages.map { it to it.removeSuffix(".html") }, page, onPage)
                }
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close preview", tint = Color.White) }
            }
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.allowFileAccess = true
                        webViewClient = WebViewClient()
                    }
                },
                update = { view ->
                    if (loaded != page) {
                        view.loadUrl("file://$dir/$page")
                        loaded = page
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }
    }
}
