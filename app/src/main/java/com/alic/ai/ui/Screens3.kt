package com.alic.ai.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.alic.ai.data.OrbState
import java.text.DateFormat
import java.util.Date

// ---------------- History ----------------
@Composable
fun HistoryScreen(vm: AlicViewModel, nav: NavController) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    var confirmAll by remember { mutableStateOf(false) }
    val list = vm.conversations.filter { c ->
        q.isBlank() || c.title.contains(q, true) || c.messages.any { it.text.contains(q, true) }
    }
    val df = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    Column(Modifier.fillMaxSize().background(screenBg())) {
        ScreenHeader("History") {
            IconButton({ vm.exportAll()?.let { shareText(ctx, it) } ?: run { vm.toast = "No history to export." } }) { Icon(Icons.Filled.Share, "Export all", tint = C.Text) }
            IconButton({ confirmAll = true }) { Icon(Icons.Filled.Delete, "Clear all", tint = C.Text) }
        }
        OutlinedTextField(
            q, { q = it }, placeholder = { Text("Search conversations", color = C.Sub) }, singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = C.Sub) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), colors = fieldColors(), shape = RoundedCornerShape(14.dp)
        )
        if (list.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(if (q.isBlank()) "No conversations yet. Start chatting with ALIC AI." else "No matches.", color = C.Sub)
            }
        } else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { c ->
                GlassCard(Modifier.fillMaxWidth(), onClick = { vm.openConversation(c.id); nav.navigate("chat") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.title, color = C.Text, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(df.format(Date(c.createdAt)) + " · " + c.messages.size + " messages", color = C.Sub, fontSize = 12.sp)
                        }
                        IconButton({ shareText(ctx, vm.exportText(c)) }) { Icon(Icons.Filled.Share, "Export", tint = C.Sub) }
                        IconButton({ vm.deleteConversation(c.id) }) { Icon(Icons.Filled.Delete, "Delete", tint = C.Neon) }
                    }
                }
            }
        }
    }
    if (confirmAll) AlertDialog(
        onDismissRequest = { confirmAll = false }, containerColor = C.Card,
        title = { Text("Clear all history?", color = C.Text) },
        text = { Text("All conversations will be deleted from this device.", color = C.Sub) },
        confirmButton = { TextButton({ confirmAll = false; vm.clearAllHistory() }) { Text("Clear all", color = C.Neon) } },
        dismissButton = { TextButton({ confirmAll = false }) { Text("Cancel", color = C.Sub) } }
    )
}

// ---------------- Settings ----------------
@Composable
fun SettingsScreen(vm: AlicViewModel, nav: NavController) {
    val ctx = LocalContext.current
    val s = vm.settings
    var voiceDialog by remember { mutableStateOf(false) }
    var wipe by remember { mutableStateOf(false) }
    var doc by remember { mutableStateOf<String?>(null) }
    var personality by remember(s.personality) { mutableStateOf(s.personality) }
    var note by remember { mutableStateOf("") }
    val voicesTick by vm.voice.ttsVoicesChanged.collectAsState()
    val voices = remember(voicesTick) { vm.voice.voices() }
    val males = remember(voicesTick) { vm.voice.maleVoices() }
    val micOk = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    Column(Modifier.fillMaxSize().background(screenBg())) {
        ScreenHeader("Settings")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 24.dp)) {
            GlassCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(76.dp)) { AlicOrb(OrbState.IDLE, 0f, animated = false) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("ALIC AI", fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 3.sp, color = C.Text)
                        Text("THE POWER OF INTELLIGENCE", fontSize = 9.sp, letterSpacing = 1.5.sp, color = C.Neon)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusPill(if (vm.hasKey) "Gemini ready" else "No API key", vm.hasKey) { nav.navigate("aiconfig") }
                            StatusPill(if (s.wakeEnabled) "Wake on" else "Wake off", s.wakeEnabled) { nav.navigate("wake") }
                        }
                    }
                }
            }
            SettingsGroup("AI & Gemini", Icons.Filled.AutoAwesome, startOpen = true) {
                Text(if (vm.hasKey) "Gemini API key: configured" else "Gemini API key: not set", color = if (vm.hasKey) Color(0xFF2ECC71) else C.Neon, fontSize = 14.sp)
                Text("Model: ${s.model}", color = C.Sub, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                RedButton("Gemini API configuration", { nav.navigate("aiconfig") }, Modifier.fillMaxWidth(), icon = Icons.Filled.Lock)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(personality, { personality = it }, label = { Text("Assistant personality") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors(), shape = RoundedCornerShape(12.dp))
                if (personality != s.personality) TextButton({ vm.updateSettings { it.copy(personality = personality.trim()) } }) { Text("Save personality", color = C.Neon) }
                Text("Response preference", color = C.Text, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                ChipsRow(listOf("0" to "Concise", "1" to "Balanced", "2" to "Detailed"), s.responseStyle.toString()) { v -> vm.updateSettings { it.copy(responseStyle = v.toInt()) } }
            }

            SettingsGroup("Wake word", Icons.Filled.GraphicEq, startOpen = false) {
                Text("Say “Hey Alic” to start a Live conversation hands-free.", color = C.Sub, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                RedButton("Wake Up Detection settings", { nav.navigate("wake") }, Modifier.fillMaxWidth(), icon = Icons.Filled.GraphicEq)
            }

            SettingsGroup("Voice", Icons.Filled.RecordVoiceOver, startOpen = false) {
                SwitchRow("Deep voice preference", "Uses a male-labelled voice when your device has one", s.deepVoice) { v -> vm.updateSettings { it.copy(deepVoice = v) } }
                Text(
                    if (males.isNotEmpty()) "Male-labelled voices found: ${males.size}"
                    else "No male-labelled voice found on this device, so your default voice is used. Install more voices in Android Settings > Text-to-speech.",
                    color = if (males.isNotEmpty()) Color(0xFF2ECC71) else C.Sub, fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                GhostButton("Voice: " + (voices.firstOrNull { it.name == s.ttsVoice }?.name ?: "Automatic"), { voiceDialog = true }, Modifier.fillMaxWidth(), Icons.Filled.RecordVoiceOver)
                SliderRow("Pitch", s.pitch, 0.5f..1.5f, "%.2f") { v -> vm.updateSettings { it.copy(pitch = v) } }
                SliderRow("Speech speed", s.speed, 0.5f..1.8f, "%.2f") { v -> vm.updateSettings { it.copy(speed = v) } }
                Text("Language", color = C.Text, fontSize = 14.sp)
                ChipsRow(listOf("auto" to "Auto", "en" to "English", "hi" to "हिन्दी"), s.language) { v -> vm.updateSettings { it.copy(language = v) } }
                Spacer(Modifier.height(8.dp))
                GhostButton("Test voice", { vm.speakMessage("Hello! I am ALIC AI. Your intelligent personal assistant. Tell me what you need, and I will help you.") }, Modifier.fillMaxWidth(), Icons.Filled.VolumeUp)
            }

            SettingsGroup("Appearance", Icons.Filled.Palette, startOpen = false) {
                Box(Modifier.fillMaxWidth(0.5f).align(Alignment.CenterHorizontally)) { AlicOrb(OrbState.LIVE, 0f, animated = s.animations) }
                Text("Theme", color = C.Text, fontSize = 14.sp)
                ChipsRow(listOf("dark" to "Dark", "light" to "Light", "sunset" to "Sunset"), s.themeMode) { v -> vm.updateSettings { it.copy(themeMode = v) } }
                Text(if (s.themeMode == "sunset") "Accent colour (Sunset uses its own warm colours)" else "Accent colour", color = C.Text, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                AccentPicker(s.accent) { id -> vm.updateSettings { it.copy(accent = id) } }
                SwitchRow("Orb animations", null, s.animations) { v -> vm.updateSettings { it.copy(animations = v) } }
                SwitchRow("Haptic feedback", null, s.haptics) { v -> vm.updateSettings { it.copy(haptics = v) } }
            }

            SettingsGroup("Privacy & permissions", Icons.Filled.Security, startOpen = false) {
                SwitchRow("Memory", "Let ALIC use the notes below in every reply", s.memoryEnabled) { v -> vm.updateSettings { it.copy(memoryEnabled = v) } }
                s.memoryNotes.forEachIndexed { i, n ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("• $n", color = C.Sub, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        IconButton({ vm.updateSettings { st -> st.copy(memoryNotes = st.memoryNotes.filterIndexed { j, _ -> j != i }) } }, Modifier.size(32.dp)) { Icon(Icons.Filled.Close, "Remove note", tint = C.Neon, modifier = Modifier.size(16.dp)) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(note, { note = it }, placeholder = { Text("Add a note, e.g. “Call me Boss”", color = C.Sub) }, singleLine = true, modifier = Modifier.weight(1f), colors = fieldColors(), shape = RoundedCornerShape(12.dp))
                    IconButton({ if (note.isNotBlank()) { val n = note.trim(); vm.updateSettings { it.copy(memoryNotes = it.memoryNotes + n) }; note = "" } }) { Icon(Icons.Filled.Add, "Add note", tint = C.Neon) }
                }
                SwitchRow("Confirm before opening apps", "Ask before ALIC launches an app or YouTube", s.confirmActions) { v -> vm.updateSettings { it.copy(confirmActions = v) } }
                SwitchRow("Session notification", "Show a Stop notification during live microphone sessions", s.notifications) { v -> vm.updateSettings { it.copy(notifications = v) } }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("Clear history", { vm.clearAllHistory() }, Modifier.weight(1f))
                    GhostButton("Delete my data", { wipe = true }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                GhostButton("Permissions & accessibility", { nav.navigate("permissions") }, Modifier.fillMaxWidth(), Icons.Filled.Security)
            }

            SettingsGroup("About", Icons.Filled.Info, startOpen = false) {
                Text("ALIC AI  ·  version 1.0.0", color = C.Text, fontWeight = FontWeight.SemiBold)
                Text("THE POWER OF INTELLIGENCE", color = C.Neon, fontSize = 11.sp, letterSpacing = 2.sp)
                Text("Developer: ALIC AI project", color = C.Sub, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                Row {
                    TextButton({ doc = "privacy" }) { Text("Privacy policy", color = C.Neon) }
                    TextButton({ doc = "terms" }) { Text("Terms", color = C.Neon) }
                }
            }
        }
    }

    if (voiceDialog) AlertDialog(
        onDismissRequest = { voiceDialog = false }, containerColor = C.Card,
        title = { Text("Choose voice", color = C.Text) },
        text = {
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                item { Text("Automatic", color = if (s.ttsVoice.isBlank()) C.Neon else C.Text, modifier = Modifier.fillMaxWidth().clickable { vm.updateSettings { it.copy(ttsVoice = "") }; voiceDialog = false }.padding(12.dp)) }
                items(voices, key = { it.name }) { v ->
                    val tag = if (vm.voice.isMale(v)) "  · male" else ""
                    Text(
                        v.name + "  (" + v.locale + ")" + tag, fontSize = 13.sp,
                        color = if (v.name == s.ttsVoice) C.Neon else C.Text,
                        modifier = Modifier.fillMaxWidth().clickable { vm.updateSettings { it.copy(ttsVoice = v.name) }; voiceDialog = false }.padding(12.dp)
                    )
                }
            }
        },
        confirmButton = { TextButton({ voiceDialog = false }) { Text("Close", color = C.Sub) } }
    )
    if (wipe) AlertDialog(
        onDismissRequest = { wipe = false }, containerColor = C.Card,
        title = { Text("Delete all my data?", color = C.Text) },
        text = { Text("Removes chat history and memory notes from this device. This cannot be undone.", color = C.Sub) },
        confirmButton = { TextButton({ wipe = false; vm.deleteAllMyData() }) { Text("Delete", color = C.Neon) } },
        dismissButton = { TextButton({ wipe = false }) { Text("Cancel", color = C.Sub) } }
    )
    doc?.let { d ->
        AlertDialog(
            onDismissRequest = { doc = null }, containerColor = C.Card,
            title = { Text(if (d == "privacy") "Privacy policy" else "Terms and conditions", color = C.Text) },
            text = {
                Text(
                    run {
                        if (d == "privacy")
                            "ALIC AI sends your messages, images and voice audio to Google's Gemini API using the API key you provide. Your key is stored encrypted on this device only.\n\nChats and notes are stored only on this device. Contacts are read on the phone only to find a number when you ask ALIC to call or message someone.\n\nThe microphone is used only after you tap a voice control, and a visible notification is shown during live sessions. You can delete your history and data at any time in Settings."
                        else
                            "ALIC AI is provided as is. AI responses may be inaccurate; verify important information. You are responsible for your Gemini API usage and costs, and for complying with Google's Gemini API terms. Do not use ALIC AI for unlawful purposes. Generated code is never executed automatically; review it before use."
                    },
                    color = C.Sub, fontSize = 13.sp, modifier = Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())
                )
            },
            confirmButton = { TextButton({ doc = null }) { Text("Close", color = C.Neon) } }
        )
    }
}

// ---------------- Gemini API configuration ----------------
@Composable
fun AIConfigScreen(vm: AlicViewModel, nav: NavController) {
    val s = vm.settings
    var key by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var model by remember(s.model) { mutableStateOf(s.model) }
    var liveModel by remember(s.liveModel) { mutableStateOf(s.liveModel) }
    var baseUrl by remember(s.baseUrl) { mutableStateOf(s.baseUrl) }
    Column(Modifier.fillMaxSize().background(screenBg())) {
        ScreenHeader("Gemini API configuration", onBack = { nav.popBackStack() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 24.dp)) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(if (vm.hasKey) "Saved key: ${vm.currentKeyHint()}" else "No API key saved yet", color = if (vm.hasKey) Color(0xFF2ECC71) else C.Neon, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    key, { key = it }, label = { Text("Gemini API key") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { IconButton({ show = !show }) { Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Toggle", tint = C.Sub) } },
                    colors = fieldColors(), shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RedButton("Save key", { if (key.isBlank()) vm.toast = "Paste your key first." else { vm.saveApiKey(key); key = "" } }, Modifier.weight(1f))
                    GhostButton("Remove", { vm.saveApiKey("") }, Modifier.weight(1f))
                }
                if (!vm.secureStorageAvailable) Text("Encrypted storage is unavailable on this device; the key is kept in memory only for this session.", color = C.Neon, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                Text("Get a free key at aistudio.google.com/apikey", color = C.Sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }

            SectionTitle("MODELS")
            GlassCard(Modifier.fillMaxWidth()) {
                OutlinedTextField(model, { model = it }, label = { Text("Chat model") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors(), shape = RoundedCornerShape(12.dp))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(liveModel, { liveModel = it }, label = { Text("Live model (Gemini Live API)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors(), shape = RoundedCornerShape(12.dp))
                Text("Live voice", color = C.Text, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
                ChipsRow(listOf("Charon", "Orus", "Fenrir", "Puck", "Kore").map { it to it }, s.liveVoice) { v -> vm.updateSettings { it.copy(liveVoice = v) } }
                Text("Charon, Orus, Fenrir and Puck are male Gemini voices.", color = C.Sub, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RedButton("Save models", { vm.updateSettings { it.copy(model = model.trim().ifBlank { it.model }, liveModel = liveModel.trim().ifBlank { it.liveModel }) }; vm.toast = "Models saved." }, Modifier.weight(1f))
                    GhostButton("List models", { vm.fetchModels() }, Modifier.weight(1f))
                }
                if (vm.textModels.isNotEmpty()) {
                    Text("Tap to use as chat model:", color = C.Sub, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                    ChipsRow(vm.textModels.take(30).map { it to it }, s.model) { m -> model = m; vm.updateSettings { it.copy(model = m) } }
                }
                if (vm.liveModels.isNotEmpty()) {
                    Text("Tap to use as live model:", color = C.Sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    ChipsRow(vm.liveModels.map { it to it }, s.liveModel) { m -> liveModel = m; vm.updateSettings { it.copy(liveModel = m) } }
                }
            }

            SectionTitle("CONNECTION TEST")
            GlassCard(Modifier.fillMaxWidth()) {
                RedButton(if (vm.testBusy) "Testing…" else "Test connection", { vm.testApi() }, Modifier.fillMaxWidth(), enabled = !vm.testBusy)
                vm.testResult?.let { Text(it, color = C.Text, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
            }

            SectionTitle("BACKEND PROXY (OPTIONAL)")
            GlassCard(Modifier.fillMaxWidth()) {
                OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("API base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors(), shape = RoundedCornerShape(12.dp))
                Spacer(Modifier.height(8.dp))
                GhostButton("Save base URL", {
                    val u = baseUrl.trim().trimEnd('/')
                    if (u.startsWith("https://")) { vm.updateSettings { it.copy(baseUrl = u) }; vm.toast = "Base URL saved." } else vm.toast = "Use an https:// URL."
                }, Modifier.fillMaxWidth())
                Text("Default: https://generativelanguage.googleapis.com. For production, point this at your own server that forwards to Gemini and keeps the real key server-side.", color = C.Sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }

            SectionTitle("SECURITY NOTE")
            Text(
                "A key entered in an app is stored encrypted on your phone, but anyone with access to the device or its network traffic could still extract it. Use your own key, restrict it in Google AI Studio / Cloud Console, and never share this APK with a key embedded. For a public release, route requests through a backend proxy so no secret ships inside the APK.",
                color = C.Sub, fontSize = 12.sp, lineHeight = 18.sp
            )
        }
    }
}

// ---------------- Live conversation ----------------
@Composable
fun LiveScreen(vm: AlicViewModel, nav: NavController) {
    LaunchedEffect(vm.liveActive) { if (!vm.liveActive) nav.popBackStack() }
    BackHandler { vm.endLive() }
    val lines = vm.liveLog.filter { it.text.isNotBlank() }.takeLast(6)
    Column(Modifier.fillMaxSize().background(screenBg()).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("LIVE CONVERSATION", color = C.Text, fontWeight = FontWeight.Bold, letterSpacing = 3.sp, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            StatusDot(vm.liveConnected)
            Spacer(Modifier.width(6.dp))
            Text(
                vm.liveStatus.ifBlank { "Connecting…" },
                color = C.Sub, fontSize = 13.sp
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth(0.85f), contentAlignment = Alignment.Center) {
            AlicOrb(if (vm.orb == OrbState.IDLE || vm.orb == OrbState.LISTENING) OrbState.LIVE else vm.orb, vm.level, animated = vm.settings.animations)
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 170.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(lines, key = { it.id }) { m ->
                Text(
                    (if (m.isUser) "You: " else "ALIC: ") + m.text, fontSize = 13.sp, maxLines = 4,
                    color = if (m.isUser) C.Sub else C.Text, modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Row(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(60.dp).clip(CircleShape).background(if (vm.liveMuted) C.Card else C.Bg2).border(1.dp, C.Red, CircleShape).clickable { vm.toggleMute() },
                contentAlignment = Alignment.Center
            ) { Icon(if (vm.liveMuted) Icons.Filled.MicOff else Icons.Filled.Mic, if (vm.liveMuted) "Unmute" else "Mute", tint = C.Text) }
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(Brush.radialGradient(listOf(C.Neon, C.Red, C.Dark))).clickable { vm.endLive() },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.CallEnd, "End conversation", tint = C.OnPrimary, modifier = Modifier.size(32.dp)) }
        }
        Text(if (vm.liveMuted) "Microphone muted" else "Microphone is on", color = C.Sub, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}
