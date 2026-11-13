package com.alic.ai.ui

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.navigation.NavController
import com.alic.ai.device.Command
import java.io.File

// ---------------- Coding Studio ----------------
@Composable
fun CodingScreen(vm: AlicViewModel, nav: NavController) {
    val clip = LocalClipboardManager.current
    var lang by remember { mutableStateOf("Kotlin") }
    var mode by remember { mutableStateOf("Generate") }
    var instruction by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    val langs = listOf("Kotlin", "Python", "PHP", "HTML/CSS/JS", "JavaScript", "Java", "Firebase Rules", "Android (Compose)")
    val modes = listOf("Generate", "Explain", "Debug", "Fix", "Chat")
    Column(Modifier.fillMaxSize().background(screenBg())) {
        ScreenHeader("Coding Studio", onBack = { nav.popBackStack() }) {
            IconButton({ vm.codingRegenerate() }) { Icon(Icons.Filled.Refresh, "Regenerate", tint = C.Text) }
            IconButton({ vm.codingClear() }) { Icon(Icons.Filled.Delete, "Clear", tint = C.Text) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            ChipsRow(langs.map { it to it }, lang) { lang = it }
            ChipsRow(modes.map { it to it }, mode) { mode = it }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                code, { code = it }, label = { Text("Code (paste for explain / debug / fix)") },
                modifier = Modifier.fillMaxWidth(), minLines = 5, maxLines = 12,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Color(0xFFEDEDED)),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = C.Red, unfocusedBorderColor = C.Border, cursorColor = C.Red,
                    focusedContainerColor = Color(0xFF0C0C0C), unfocusedContainerColor = Color(0xFF0C0C0C),
                    focusedLabelColor = C.Red, unfocusedLabelColor = C.Sub
                ),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                instruction, { instruction = it }, label = { Text(if (vm.coding.isEmpty()) "What should ALIC do?" else "Follow-up / error message") },
                modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5, colors = fieldColors(), shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(8.dp))
            RedButton(if (vm.codingBusy) "Working…" else "Run ALIC AI", {
                vm.codingSend(mode, lang, instruction, code); instruction = ""
            }, Modifier.fillMaxWidth(), enabled = !vm.codingBusy, icon = Icons.Filled.Bolt)
            Text("Generated code is never executed automatically.", color = C.Sub, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(10.dp))
            vm.coding.forEach { m ->
                if (m.isUser) {
                    Text("You: " + m.text.lineSequence().firstOrNull().orEmpty() + "\n" + m.text.lines().drop(1).joinToString(" ").take(120),
                        color = C.Sub, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                } else {
                    GlassCard(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                        if (m.text.isBlank()) TypingDots() else MessageContent(m.text)
                        if (m.text.isNotBlank()) TextButton({ clip.setText(AnnotatedString(m.text)); vm.toast = "Copied" }) {
                            Icon(Icons.Filled.ContentCopy, null, tint = C.Neon, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Copy all", color = C.Neon)
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Vision ----------------
@Composable
fun VisionScreen(vm: AlicViewModel, nav: NavController) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? -> if (uri != null) vm.loadImage(uri) }
    val captureUri = remember {
        val dir = File(ctx.cacheDir, "images").apply { mkdirs() }
        FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", File(dir, "capture.jpg"))
    }
    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) vm.loadImage(captureUri) }
    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) cam.launch(captureUri) else vm.toast = "Camera permission is required to take photos."
    }
    val quick = listOf("Describe this image", "Identify the objects", "Explain this document", "Explain this screenshot", "Extract all text")
    Column(Modifier.fillMaxSize().background(screenBg())) {
        ScreenHeader("AI Vision", onBack = { nav.popBackStack() }) {
            if (vm.visionBitmap != null) IconButton({ vm.clearVision() }) { Icon(Icons.Filled.Close, "Remove image", tint = C.Text) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp)) {
            val bmp = vm.visionBitmap
            if (bmp != null) {
                Image(bmp.asImageBitmap(), "Selected image", Modifier.fillMaxWidth().heightIn(max = 300.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, C.Border, RoundedCornerShape(14.dp)), contentScale = ContentScale.Fit)
            } else {
                GlassCard(Modifier.fillMaxWidth().height(150.dp)) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Image, null, tint = C.Neon, modifier = Modifier.size(36.dp))
                        Text("Take a photo or choose an image", color = C.Sub, fontSize = 14.sp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton("Camera", {
                    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) cam.launch(captureUri)
                    else camPerm.launch(Manifest.permission.CAMERA)
                }, Modifier.weight(1f), Icons.Filled.CameraAlt)
                GhostButton("Gallery", { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.weight(1f), Icons.Filled.PhotoLibrary)
            }
            ChipsRow(quick.map { it to it }, "") { q = it; vm.askVision(it) }
            OutlinedTextField(q, { q = it }, label = { Text("Ask about the image") }, modifier = Modifier.fillMaxWidth(), minLines = 2, colors = fieldColors(), shape = RoundedCornerShape(12.dp))
            Spacer(Modifier.height(8.dp))
            RedButton(if (vm.visionBusy) "Analysing…" else "Ask ALIC", { vm.askVision(q) }, Modifier.fillMaxWidth(), enabled = !vm.visionBusy && vm.visionBitmap != null)
            Spacer(Modifier.height(10.dp))
            if (vm.visionBusy && vm.visionAnswer.isBlank()) TypingDots()
            if (vm.visionAnswer.isNotBlank()) GlassCard(Modifier.fillMaxWidth()) { MessageContent(vm.visionAnswer) }
        }
    }
}

// ---------------- YouTube ----------------
@Composable
fun YouTubeScreen(vm: AlicViewModel, nav: NavController) {
    var q by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(screenBg())) {
        ScreenHeader("YouTube Assistant", onBack = { nav.popBackStack() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp)) {
            OutlinedTextField(q, { q = it }, label = { Text("Search videos or music") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors(), shape = RoundedCornerShape(14.dp))
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RedButton("YouTube", { if (q.isBlank()) vm.toast = "Enter something to search." else vm.youtube(q.trim(), false) }, Modifier.weight(1f), icon = Icons.Filled.PlayArrow)
                GhostButton("YouTube Music", { if (q.isBlank()) vm.toast = "Enter something to search." else vm.youtube(q.trim(), true) }, Modifier.weight(1f), Icons.Filled.MusicNote)
            }
            SectionTitle("OPEN A LINK")
            OutlinedTextField(
                link, { link = it }, label = { Text("Paste a YouTube link") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), colors = fieldColors(), shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.height(8.dp))
            GhostButton("Open link", {
                val l = link.trim()
                if (Regex("^https?://(www\\.|m\\.|music\\.)?(youtu\\.be|youtube\\.com)/\\S*", RegexOption.IGNORE_CASE).containsMatchIn(l)) vm.runDirect(Command.OpenUrl(l))
                else vm.toast = "Enter a valid youtube.com or youtu.be link."
            }, Modifier.fillMaxWidth(), Icons.Filled.Link)
            SectionTitle("CHAT EXAMPLES")
            GlassCard(Modifier.fillMaxWidth()) {
                Text("“ALIC YouTube par Arijit Singh songs chalao”\n“ALIC YouTube Music par lofi play karo”\n“ALIC YouTube kholo”", color = C.Sub, fontSize = 13.sp, lineHeight = 20.sp)
            }
            Text("ALIC opens YouTube through official Android intents. Playback and sign-in are handled by the YouTube app.", color = C.Sub, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

// ---------------- App control ----------------
@Composable
fun AppsScreen(vm: AlicViewModel, nav: NavController) {
    val apps = remember { vm.installedApps() }
    var filter by remember { mutableStateOf("") }
    val shown = apps.filter { it.label.contains(filter, ignoreCase = true) }
    Column(Modifier.fillMaxSize().background(screenBg())) {
        ScreenHeader("App Control", onBack = { nav.popBackStack() })
        Text("Type “camera open karo” or “WhatsApp kholo” in chat, or tap an app below.", color = C.Sub, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 14.dp))
        OutlinedTextField(
            filter, { filter = it }, placeholder = { Text("Search installed apps", color = C.Sub) }, singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = C.Sub) },
            modifier = Modifier.fillMaxWidth().padding(12.dp), colors = fieldColors(), shape = RoundedCornerShape(14.dp)
        )
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 12.dp)) {
            items(shown, key = { it.pkg }) { a ->
                Row(Modifier.fillMaxWidth().clickable { vm.launchApp(a) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Apps, null, tint = C.Neon)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(a.label, color = C.Text, fontSize = 15.sp)
                        Text(a.pkg, color = C.Sub, fontSize = 11.sp)
                    }
                }
                HorizontalDivider(color = C.Border)
            }
            if (shown.isEmpty()) item { Text("No matching apps.", color = C.Sub, modifier = Modifier.padding(16.dp)) }
        }
    }
}
