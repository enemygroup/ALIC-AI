package com.alic.ai.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.alic.ai.wa.Rule
import com.alic.ai.wa.WaLog
import com.alic.ai.wa.WaNotificationService
import com.alic.ai.wa.WaStore
import java.util.UUID
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete

/** WhatsApp Automation control panel. Everything is OFF until the user turns it on. */
@Composable
fun WaScreen(nav: NavController) {
    val ctx = LocalContext.current
    val store = remember { WaStore(ctx) }
    val status by WaLog.status.collectAsState()
    val lines by WaLog.lines.collectAsState()
    var enabled by remember { mutableStateOf(store.enabled) }
    var autoReply by remember { mutableStateOf(store.autoReply) }
    var voice by remember { mutableStateOf(store.voice) }
    var readMsgs by remember { mutableStateOf(store.readMessages) }
    var delay by remember { mutableStateOf(store.delaySec.toString()) }
    var maxPer by remember { mutableStateOf(store.maxPerContact.toString()) }
    var rules by remember { mutableStateOf(store.rules()) }
    var contact by remember { mutableStateOf("") }
    var trigger by remember { mutableStateOf("") }
    var reply by remember { mutableStateOf("") }
    val access = remember { WaNotificationService.isEnabled(ctx) }

    Column(Modifier.fillMaxSize().background(screenBg())) {
        ScreenHeader("WhatsApp Automation", onBack = { nav.popBackStack() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            SectionTitle("STATUS")
            NeoCard(Modifier.fillMaxWidth()) {
                Text("Service: $status", color = C.Text, fontWeight = FontWeight.SemiBold)
                Text(if (access) "Notification access: on" else "Notification access: off (turn it on below)", color = if (access) C.Sub else C.Err, fontSize = 12.sp)
                GhostButton("Open notification access", {
                    ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }, Modifier.fillMaxWidth().padding(top = 8.dp))
            }

            SectionTitle("CONTROLS (all OFF by default)")
            GlassCard(Modifier.fillMaxWidth()) {
                SwitchRow("WhatsApp automation", "Reads WhatsApp notifications", enabled) { v -> enabled = v; store.enabled = v; if (!v) WaLog.status.value = "Stopped" }
                SwitchRow("Automatic reply", "Sends the reply through WhatsApp's notification Reply. Off = only shows the reply", autoReply) { v -> autoReply = v; store.autoReply = v }
                SwitchRow("Voice announcement", "Announces replies sent (not during calls)", voice) { v -> voice = v; store.voice = v }
                SwitchRow("Read incoming messages", "Speaks new messages aloud", readMsgs) { v -> readMsgs = v; store.readMessages = v }
                Text("Reply delay (seconds)", color = C.Text, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                ChipsRow(listOf("1" to "1", "3" to "3", "5" to "5", "10" to "10"), delay) { v -> delay = v; store.delaySec = v.toInt() }
                Text("Max auto replies per contact (this app run)", color = C.Text, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                ChipsRow(listOf("5" to "5", "20" to "20", "50" to "50"), maxPer) { v -> maxPer = v; store.maxPerContact = v.toInt() }
                Text("Reply language: automatic (same as the sender).", color = C.Sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }

            SectionTitle("CONTACT RULES (win over AI)")
            GlassCard(Modifier.fillMaxWidth()) {
                OutlinedTextField(contact, { contact = it }, label = { Text("Contact name (exact)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors(), shape = RoundedCornerShape(12.dp))
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(trigger, { trigger = it }, label = { Text("If message contains (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors(), shape = RoundedCornerShape(12.dp))
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(reply, { reply = it }, label = { Text("Reply with") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors(), shape = RoundedCornerShape(12.dp))
                Spacer(Modifier.height(8.dp))
                RedButton("Add rule", {
                    if (contact.isBlank() || reply.isBlank()) vm_toastHint(ctx)
                    else {
                        val list = rules + Rule(UUID.randomUUID().toString(), contact.trim(), trigger.trim(), reply.trim(), true)
                        store.saveRules(list); rules = list
                        contact = ""; trigger = ""; reply = ""
                    }
                }, Modifier.fillMaxWidth())
            }
            rules.forEach { r ->
                NeoCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.contact + if (r.trigger.isNotBlank()) "  (\"${r.trigger}\")" else "", color = C.Text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(r.reply, color = C.Sub, fontSize = 12.sp)
                        }
                        Switch(r.enabled, { v ->
                            val list = rules.map { if (it.id == r.id) it.copy(enabled = v) else it }
                            store.saveRules(list); rules = list
                        })
                        IconButton(onClick = {
                            val list = rules.filter { it.id != r.id }
                            store.saveRules(list); rules = list
                        }) { Icon(Icons.Filled.Delete, "Delete rule", tint = C.Err) }
                    }
                }
            }

            SectionTitle("ACTIVITY LOG")
            NeoCard(Modifier.fillMaxWidth()) {
                if (lines.isEmpty()) Text("No activity yet.", color = C.Sub, fontSize = 12.sp)
                lines.take(25).forEach { Text(it, color = C.Sub, fontSize = 12.sp) }
            }
            Text(
                "Force Stop stops background work until you open ALIC AI again. Android and WhatsApp can also restrict notification replies. Use this responsibly and only where WhatsApp's terms allow it.",
                color = C.Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

private fun vm_toastHint(ctx: android.content.Context) {
    android.widget.Toast.makeText(ctx, "Enter a contact name and a reply.", android.widget.Toast.LENGTH_SHORT).show()
}
