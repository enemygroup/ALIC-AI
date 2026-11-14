package com.alic.ai.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.asImageBitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.alic.ai.data.Personas
import com.alic.ai.data.OrbState
import com.alic.ai.device.Command
import com.alic.ai.live.LiveSessionController
import com.alic.ai.wake.WakeWordService
import org.json.JSONObject

// ---------------- Voice assistant (full screen, real session state) ----------------
@Composable
fun LiveScreen(vm: AlicViewModel, nav: NavController) {
    val ctx = LocalContext.current
    val c = LiveSessionController
    LaunchedEffect(vm.liveActive) { if (!vm.liveActive && vm.voiceNote == null) nav.popBackStack() }
    val statusLine = when {
        vm.voiceNote != null -> vm.voiceNote!!
        !vm.liveActive -> "Session ended."
        c.phase == LiveSessionController.Phase.SPEAKING -> "ALIC is speaking..."
        c.phase == LiveSessionController.Phase.THINKING -> "Thinking..."
        c.muted -> "Microphone muted"
        else -> "I'm listening..."
    }
    Column(Modifier.fillMaxSize().background(screenBg()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            Text("Voice Assistant", color = C.Text, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Spacer(Modifier.weight(1f))
            IconButton({ vm.endLive(); nav.popBackStack() }) { Icon(Icons.Filled.Close, "Close", tint = C.Text) }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth(0.82f)) { AlicOrb(vm.orb, vm.level, animated = vm.settings.animations) }
        Spacer(Modifier.height(10.dp))
        NeoCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Mic, null, tint = if (vm.liveActive && !c.muted) C.Neon else C.Muted, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(statusLine, color = C.Text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(vm.liveStatus.ifBlank { "" }, color = C.Muted, fontSize = 11.sp, maxLines = 1)
                }
                StatusDot(vm.liveConnected)
            }
        }
        Spacer(Modifier.height(12.dp))
        LevelWave(vm.level, active = vm.liveActive && !vm.liveMuted)
        Spacer(Modifier.height(10.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(vm.liveLog.takeLast(12), key = { it.id }) { m ->
                Text((if (m.isUser) "You: " else if (m.role == "tool") "" else "ALIC: ") + m.text, color = if (m.isUser) C.Sub else C.Text, fontSize = 13.sp)
            }
        }
        Row(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(60.dp).clip(CircleShape).background(C.Glass2).border(1.dp, glowBrush(), CircleShape)
                    .clickable { vm.toggleMute() },
                contentAlignment = Alignment.Center
            ) { Icon(if (vm.liveMuted) Icons.Filled.MicOff else Icons.Filled.Mic, if (vm.liveMuted) "Unmute" else "Mute", tint = C.Text) }
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(Brush.radialGradient(listOf(C.Neon, C.Red, C.Dark)))
                    .clickable { vm.endLive(); nav.popBackStack() },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.CallEnd, "End", tint = C.OnPrimary, modifier = Modifier.size(30.dp)) }
        }
    }
}

// ---------------- Quick Actions (each card runs the real existing feature) ----------------
@Composable
fun QuickScreen(vm: AlicViewModel, nav: NavController, goLive: () -> Unit) {
    data class Q(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val act: () -> Unit)
    val items = listOf(
        Q("Ask AI", Icons.Filled.AutoAwesome) { nav.navigate("chat") },
        Q("Weather", Icons.Filled.WbSunny) { vm.send("Aaj ka mausam kaisa hai?"); nav.navigate("chat") },
        Q("Reminders", Icons.Filled.Notifications) { nav.navigate("tools") },
        Q("Alarm", Icons.Filled.Alarm) { vm.send("Subah 7 baje ka alarm lagao"); nav.navigate("chat") },
        Q("Timer", Icons.Filled.Timer) { vm.send("5 minute ka timer lagao"); nav.navigate("chat") },
        Q("Music", Icons.Filled.MusicNote) { nav.navigate("youtube") },
        Q("Tools", Icons.Filled.Build) { nav.navigate("tools") },
        Q("Profile", Icons.Filled.Person) { nav.navigate("profile") },
        Q("Settings", Icons.Filled.Settings) { nav.navigate("settings") }
    )
    Column(Modifier.fillMaxSize().background(screenBg())) {
        ScreenHeader("Quick Actions", onBack = { nav.popBackStack() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Column(Modifier.fillMaxWidth()) {
                items.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { q ->
                            NeoCard(Modifier.weight(1f).height(118.dp), onClick = q.act) {
                                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                    IconBadge(q.icon, 46.dp)
                                }
                                Text(q.label, color = C.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            NeoCard(Modifier.fillMaxWidth().padding(bottom = 16.dp), onClick = { goLive() }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Filled.Mic, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Talk to ALIC", color = C.Text, fontWeight = FontWeight.SemiBold)
                        Text("Start a live voice conversation", color = C.Sub, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ---------------- Tools dashboard (existing tools only) ----------------
@Composable
fun ToolsScreen(vm: AlicViewModel, nav: NavController, goLive: () -> Unit) {
    data class T(val title: String, val sub: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val route: String?, val act: (() -> Unit)? = null)
    val tools = listOf(
        T("Web Search", "Answers from Google", Icons.Filled.Search, "websearch"),
        T("Image Generator", "Create images from text", Icons.Filled.Image, "imagegen"),
        T("File Assistant", "Ask about a file", Icons.Filled.AttachFile, "fileassist"),
        T("Coding Studio", "Generate and debug code", Icons.Filled.Code, "coding"),
        T("AI Vision", "Camera and gallery questions", Icons.Filled.CameraAlt, "vision"),
        T("YouTube & Music", "Search videos and songs", Icons.Filled.PlayArrow, "youtube"),
        T("Apps", "Open installed apps", Icons.Filled.Apps, "apps"),
        T("Alarm", "Set an alarm by voice or text", Icons.Filled.Alarm, null) { vm.send("Subah 7 baje ka alarm lagao") },
        T("Timer", "Start a countdown", Icons.Filled.Timer, null) { vm.send("5 minute ka timer lagao") },
        T("Weather", "Ask about today's weather", Icons.Filled.WbSunny, null) { vm.send("Aaj ka mausam kaisa hai?") },
        T("Wake-up", "Hey Alic and clap settings", Icons.Filled.GraphicEq, "wake"),
        T("Permissions", "Mic, camera, calls, overlay", Icons.Filled.Security, "permissions")
    )
    Column(Modifier.fillMaxSize().background(screenBg())) {
        ScreenHeader("Tools")
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp)) {
            items(tools.chunked(2)) { pair ->
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { t ->
                        NeoCard(Modifier.weight(1f).height(138.dp), onClick = { if (t.route != null) nav.navigate(t.route) else t.act?.invoke() }) {
                            IconBadge(t.icon, 42.dp)
                            Spacer(Modifier.height(10.dp))
                            Text(t.title, color = C.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(t.sub, color = C.Sub, fontSize = 11.sp, maxLines = 2)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

// ---------------- Web search (Gemini + Google Search grounding) ----------------
@Composable
fun WebSearchScreen(vm: AlicViewModel, nav: NavController) {
    var q by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(screenBg()).padding(bottom = 8.dp)) {
        ScreenHeader("Web Search", onBack = { nav.popBackStack() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            OutlinedTextField(q, { q = it }, label = { Text("Search the web") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors(), shape = RoundedCornerShape(16.dp))
            Spacer(Modifier.height(10.dp))
            GradientButton("Search", { vm.searchWeb(q) }, Modifier.fillMaxWidth(), Icons.Filled.Search, enabled = !vm.webBusy && q.isNotBlank())
            Spacer(Modifier.height(12.dp))
            if (vm.webBusy) CircularProgressIndicator(color = C.Red, modifier = Modifier.size(26.dp))
            if (vm.webAnswer.isNotBlank()) NeoCard(Modifier.fillMaxWidth()) { MessageContent(vm.webAnswer) }
        }
    }
}

// ---------------- Image generator ----------------
@Composable
fun ImageGenScreen(vm: AlicViewModel, nav: NavController) {
    var p by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(screenBg())) {
        ScreenHeader("Image Generator", onBack = { nav.popBackStack() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            OutlinedTextField(p, { p = it }, label = { Text("Describe the image") }, minLines = 3, modifier = Modifier.fillMaxWidth(), colors = fieldColors(), shape = RoundedCornerShape(16.dp))
            Spacer(Modifier.height(10.dp))
            GradientButton("Generate", { vm.generateImage(p) }, Modifier.fillMaxWidth(), Icons.Filled.AutoAwesome, enabled = !vm.imageBusy && p.isNotBlank())
            Spacer(Modifier.height(12.dp))
            if (vm.imageBusy) CircularProgressIndicator(color = C.Red, modifier = Modifier.size(26.dp))
            vm.imageBitmap?.let { bmp ->
                NeoCard(Modifier.fillMaxWidth()) {
                    androidx.compose.foundation.Image(bmp.asImageBitmap(), "Generated image", Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                }
            }
            if (vm.imageNote.isNotBlank()) Text(vm.imageNote, color = C.Sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

// ---------------- File assistant ----------------
@Composable
fun FileAssistScreen(vm: AlicViewModel, nav: NavController) {
    var q by remember { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> if (uri != null) vm.loadFile(uri) }
    Column(Modifier.fillMaxSize().background(screenBg())) {
        ScreenHeader("File Assistant", onBack = { nav.popBackStack() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            NeoCard(Modifier.fillMaxWidth(), onClick = { pick.launch(arrayOf("*/*")) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Filled.AttachFile, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(if (vm.fileName.isBlank()) "Choose a file (up to 12 MB)" else vm.fileName, color = C.Text, maxLines = 2)
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(q, { q = it }, label = { Text("Question about the file") }, minLines = 2, modifier = Modifier.fillMaxWidth(), colors = fieldColors(), shape = RoundedCornerShape(16.dp))
            Spacer(Modifier.height(10.dp))
            GradientButton(if (vm.fileBusy) "Reading..." else "Ask", { vm.askFile(q) }, Modifier.fillMaxWidth(), Icons.Filled.Send, enabled = !vm.fileBusy && vm.fileName.isNotBlank())
            Spacer(Modifier.height(12.dp))
            if (vm.fileBusy) CircularProgressIndicator(color = C.Red, modifier = Modifier.size(26.dp))
            if (vm.fileAnswer.isNotBlank()) NeoCard(Modifier.fillMaxWidth()) { MessageContent(vm.fileAnswer) }
        }
    }
}

// ---------------- Profile ----------------
@Composable
fun ProfileScreen(vm: AlicViewModel, nav: NavController) {
    val ctx = LocalContext.current
    val s = vm.settings
    var editName by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.loadAvatar() }
    var nameDraft by remember(s.userName) { mutableStateOf(s.userName) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? -> if (uri != null) vm.setAvatar(uri) }
    Column(Modifier.fillMaxSize().background(screenBg())) {
        ScreenHeader("Profile", onBack = { nav.popBackStack() }) {
            IconButton({ nav.navigate("settings") }) { Icon(Icons.Filled.Settings, "Settings", tint = C.Text) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Avatar(s.userName.ifBlank { "You" }, 104.dp, vm.avatar)
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(C.Red).clickable { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Edit, "Change picture", tint = C.OnPrimary, modifier = Modifier.size(18.dp)) }
            }
            Spacer(Modifier.height(10.dp))
            Text(s.userName.ifBlank { "Your name" }, color = C.Text, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text("ALIC AI member", color = C.Sub, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            ListRow(Icons.Filled.Badge, "Name", if (s.userName.isBlank()) "Not set" else s.userName, onClick = { editName = !editName })
            if (editName) {
                NeoCard(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    OutlinedTextField(nameDraft, { nameDraft = it }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors(), shape = RoundedCornerShape(14.dp))
                    Spacer(Modifier.height(8.dp))
                    GradientButton("Save name", { vm.updateSettings { it.copy(userName = nameDraft.trim()) }; editName = false }, Modifier.fillMaxWidth())
                }
            }
            ListRow(Icons.Filled.Info, "App version", "1.0.0")
            ListRow(Icons.Filled.Policy, "Privacy policy", "How your data is stored", onClick = { vm.toast = "Chats and notes are stored only on this device. Your Gemini key is encrypted on the device." })
            ListRow(Icons.Filled.Share, "Share app", "Send ALIC AI to a friend", onClick = { shareText(ctx, "Try ALIC AI, your intelligent personal assistant: THE POWER OF INTELLIGENCE.") })
            ListRow(Icons.Filled.History, "Conversation history", "${vm.conversations.size} saved", onClick = { nav.navigate("history") })
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---------------- Settings (premium cards, every row persists) ----------------
@Composable
fun SettingsScreen(vm: AlicViewModel, nav: NavController) {
    val s = vm.settings
    val ctx = LocalContext.current
    var wipe by remember { mutableStateOf(false) }
    var voiceDialog by remember { mutableStateOf(false) }
    var personaDialog by remember { mutableStateOf(false) }
    var doc by remember { mutableStateOf<String?>(null) }
    val persona = Personas.byId(s.persona)
    Column(Modifier.fillMaxSize().background(screenBg())) {
        ScreenHeader("Settings", onBack = { nav.popBackStack() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            SectionTitle("AI SETTINGS")
            ListRow(Icons.Filled.Key, "Gemini API key", if (vm.hasKey) "Configured (${vm.currentKeyHint()})" else "Not set", onClick = { nav.navigate("aiconfig") })
            ListRow(Icons.Filled.Tune, "AI behaviour", "Response style: ${listOf("Concise", "Balanced", "Detailed")[s.responseStyle.coerceIn(0, 2)]}", onClick = { nav.navigate("aiconfig") })
            ListRow(Icons.Filled.Psychology, "Memory", if (s.memoryEnabled) "On: ${s.memoryNotes.size} notes" else "Off") {
                Switch(s.memoryEnabled, { v -> vm.updateSettings { it.copy(memoryEnabled = v) } }, colors = SwitchDefaults.colors(checkedTrackColor = C.Red, checkedThumbColor = C.OnPrimary))
            }
            ListRow(Icons.Filled.History, "Conversation history", "${vm.conversations.size} saved", onClick = { nav.navigate("history") })

            SectionTitle("VOICE")
            ListRow(Icons.Filled.RecordVoiceOver, "Assistant persona", "${persona.name} · ${persona.gender} · ${persona.tags}", onClick = { personaDialog = true })
            ListRow(Icons.Filled.GraphicEq, "Voice", s.liveVoice.ifBlank { "Default" }, onClick = { voiceDialog = true })
            ListRow(Icons.Filled.Campaign, "Voice interruption", if (s.voiceInterruption) "On: you can interrupt ALIC" else "Off: mic is muted while ALIC speaks") {
                Switch(s.voiceInterruption, { v -> vm.updateSettings { it.copy(voiceInterruption = v) } }, colors = SwitchDefaults.colors(checkedTrackColor = C.Red, checkedThumbColor = C.OnPrimary))
            }
            ListRow(Icons.Filled.Mic, "Microphone permission", if (hasPerm(ctx, android.Manifest.permission.RECORD_AUDIO)) "Granted" else "Not granted", onClick = { nav.navigate("permissions") })

            SectionTitle("WAKE")
            ListRow(Icons.Filled.Hearing, "Hey Alic", if (s.wakeEnabled) "On: offline wake word" else "Off", onClick = { nav.navigate("wake") })
            ListRow(Icons.Filled.TouchApp, "Clap wake-up", if (s.clapEnabled) "On: ${if (s.clapMode == "double") "double" else "single"} clap" else "Off", onClick = { nav.navigate("wake") })
            ListRow(Icons.Filled.Sensors, "Background listening", if (s.wakeAlwaysOn) "On" else "Off, listens only while the app is open", onClick = { nav.navigate("wake") })
            ListRow(Icons.Filled.Tune, "Wake sensitivity", when (s.wakeSensitivity) { "kam" -> "Low"; "zyada" -> "High"; else -> "Medium" }, onClick = { nav.navigate("wake") })

            SectionTitle("POPUP")
            ListRow(Icons.Filled.AutoAwesome, "Popup animation", if (s.popupAnimation) "On" else "Off") {
                Switch(s.popupAnimation, { v -> vm.updateSettings { it.copy(popupAnimation = v) } }, colors = SwitchDefaults.colors(checkedTrackColor = C.Red, checkedThumbColor = C.OnPrimary))
            }
            ListRow(Icons.Filled.Timer, "Silence timeout", "${s.silenceTimeoutSec} seconds", onClick = { nav.navigate("wake") })
            ListRow(Icons.Filled.Layers, "Overlay permission", if (android.provider.Settings.canDrawOverlays(ctx)) "Allowed" else "Not allowed", onClick = { nav.navigate("permissions") })

            SectionTitle("APPEARANCE")
            ListRow(Icons.Filled.Palette, "Theme", when (s.themeMode) { "light" -> "Light"; "sunset" -> "Sunset"; else -> "Dark" }) {}
            ChipsRow(listOf("dark" to "Dark", "light" to "Light", "sunset" to "Sunset"), s.themeMode) { v -> vm.updateSettings { it.copy(themeMode = v) } }
            Spacer(Modifier.height(8.dp))
            Text("Accent", color = C.Sub, fontSize = 12.sp)
            AccentPicker(s.accent) { id -> vm.updateSettings { it.copy(accent = id) } }
            ListRow(Icons.Filled.Vibration, "Haptic feedback", if (s.haptics) "On" else "Off") {
                Switch(s.haptics, { v -> vm.updateSettings { it.copy(haptics = v) } }, colors = SwitchDefaults.colors(checkedTrackColor = C.Red, checkedThumbColor = C.OnPrimary))
            }
            ListRow(Icons.Filled.Animation, "Orb animations", if (s.animations) "On" else "Off") {
                Switch(s.animations, { v -> vm.updateSettings { it.copy(animations = v) } }, colors = SwitchDefaults.colors(checkedTrackColor = C.Red, checkedThumbColor = C.OnPrimary))
            }

            SectionTitle("SYSTEM")
            ListRow(Icons.Filled.Notifications, "Notifications", if (s.notifications) "On" else "Off") {
                Switch(s.notifications, { v -> vm.updateSettings { it.copy(notifications = v) } }, colors = SwitchDefaults.colors(checkedTrackColor = C.Red, checkedThumbColor = C.OnPrimary))
            }
            ListRow(Icons.Filled.BatteryChargingFull, "Battery optimization", "Keep the listener running", onClick = { nav.navigate("wake") })
            ListRow(Icons.Filled.SettingsVoice, "Default digital assistant", "Choose ALIC AI as the assistant", onClick = { nav.navigate("permissions") })
            ListRow(Icons.Filled.Security, "Permissions", "All app permissions", onClick = { nav.navigate("permissions") })
            ListRow(Icons.Filled.DeleteSweep, "Delete my data", "Chats and notes on this device", titleColor = C.Err, onClick = { wipe = true })

            SectionTitle("ABOUT")
            ListRow(Icons.Filled.Info, "About ALIC AI", "Version 1.0.0 · THE POWER OF INTELLIGENCE", onClick = { doc = "about" })
            ListRow(Icons.Filled.Policy, "Privacy policy", null, onClick = { doc = "privacy" })
            ListRow(Icons.Filled.Gavel, "Terms and conditions", null, onClick = { doc = "terms" })
        }
    }

    if (personaDialog) AlertDialog(
        onDismissRequest = { personaDialog = false }, containerColor = C.Card,
        title = { Text("Assistant persona", color = C.Text) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(Personas.all) { p ->
                    NeoCard(Modifier.fillMaxWidth().padding(bottom = 8.dp), selected = s.persona == p.id, onClick = { vm.selectPersona(p.id); personaDialog = false }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(p.name, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name, color = C.Text, fontWeight = FontWeight.SemiBold)
                                Text("${p.gender} · ${p.tags}", color = C.Sub, fontSize = 12.sp)
                            }
                            if (s.persona == p.id) Icon(Icons.Filled.CheckCircle, null, tint = C.Neon)
                        }
                    }
                }
                item {
                    Text("Custom voice", color = C.Sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp))
                    ChipsRow(Personas.voices.map { it to it }, s.liveVoice) { v -> vm.selectCustomVoice(v); personaDialog = false }
                }
            }
        },
        confirmButton = { TextButton({ personaDialog = false }) { Text("Close", color = C.Sub) } }
    )
    if (voiceDialog) AlertDialog(
        onDismissRequest = { voiceDialog = false }, containerColor = C.Card,
        title = { Text("Gemini voice", color = C.Text) },
        text = { ChipsRow(Personas.voices.map { it to it }, s.liveVoice) { v -> vm.selectCustomVoice(v); voiceDialog = false } },
        confirmButton = { TextButton({ voiceDialog = false }) { Text("Close", color = C.Sub) } }
    )
    if (wipe) AlertDialog(
        onDismissRequest = { wipe = false }, containerColor = C.Card,
        title = { Text("Delete all my data?", color = C.Text) },
        text = { Text("Removes chat history and memory notes from this device. This cannot be undone.", color = C.Sub) },
        confirmButton = { TextButton({ wipe = false; vm.deleteAllMyData() }) { Text("Delete", color = C.Err) } },
        dismissButton = { TextButton({ wipe = false }) { Text("Cancel", color = C.Sub) } }
    )
    doc?.let { d ->
        AlertDialog(
            onDismissRequest = { doc = null }, containerColor = C.Card,
            title = { Text(when (d) { "privacy" -> "Privacy policy"; "terms" -> "Terms and conditions"; else -> "About ALIC AI" }, color = C.Text) },
            text = {
                Text(
                    when (d) {
                        "privacy" -> "ALIC AI sends your messages, images, files and voice audio to Google's Gemini API using the API key you provide. Your key is stored encrypted on this device only. Chats and notes stay on this device. The microphone is used only after you start a voice session or enable the wake word, and a notification is shown while it listens."
                        "terms" -> "ALIC AI is provided as is. AI responses may be inaccurate; verify important information. You are responsible for your Gemini API usage and costs. Generated code is never executed automatically."
                        else -> "ALIC AI is your intelligent personal assistant. Version 1.0.0. THE POWER OF INTELLIGENCE."
                    },
                    color = C.Sub, fontSize = 13.sp, modifier = Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())
                )
            },
            confirmButton = { TextButton({ doc = null }) { Text("Close", color = C.Neon) } }
        )
    }
}

// ---------------- Compact floating popup (same view as the wake overlay) ----------------
@Composable
fun PopupPreview() {
    val t = rememberInfiniteTransition(label = "pp").animateFloat(0f, 1f, infiniteRepeatable(tween(1600)), label = "ppv")
    NeoCard(Modifier.fillMaxWidth().graphicsLayer { alpha = 0.95f + 0.05f * t.value }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LogoMark(40.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("ALIC AI", color = C.Text, fontWeight = FontWeight.Bold)
                Text("I'm listening...", color = C.Sub, fontSize = 12.sp)
            }
        }
    }
}
