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

            SectionTitle("CODING ENGINE")
            CodingEngineCard(vm)

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


@Composable
private fun CodingEngineCard(vm: AlicViewModel) {
    val s = vm.settings
    var key by remember { mutableStateOf("") }
    var model by remember(s.openaiModel) { mutableStateOf(s.openaiModel) }
    GlassCard(Modifier.fillMaxWidth()) {
        Text("Used by Coding Studio, the project builder and voice project requests.", color = C.Sub, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        ChipsRow(listOf("gemini" to "Gemini (free)", "openai" to "ChatGPT (OpenAI)"), s.codingProvider) { v ->
            vm.updateSettings { it.copy(codingProvider = v) }
        }
        Text(
            if (vm.hasOpenAIKey) "OpenAI key: saved" else "OpenAI key: not set",
            color = if (vm.hasOpenAIKey) Color(0xFF2ECC71) else C.Err, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)
        )
        OutlinedTextField(
            key, { key = it }, label = { Text("OpenAI API key (sk-...)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(),
            colors = fieldColors(), shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RedButton("Save key", { if (key.isBlank()) vm.toast = "Paste the key first." else { vm.saveOpenAIKey(key); key = "" } }, Modifier.weight(1f))
            GhostButton("Remove", { vm.saveOpenAIKey("") }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            model, { model = it }, label = { Text("OpenAI model") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), colors = fieldColors(), shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.height(8.dp))
        GhostButton("Save model", {
            vm.updateSettings { it.copy(openaiModel = model.trim().ifBlank { "gpt-4o" }) }
            vm.toast = "Model saved."
        }, Modifier.fillMaxWidth())
        Text("Keys are stored encrypted on this phone. Create a key at platform.openai.com. Usage is billed by OpenAI.", color = C.Sub, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
    }
}
