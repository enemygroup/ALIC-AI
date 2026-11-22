package com.alic.ai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.alic.ai.data.AlicRule
import com.alic.ai.data.AlicRuleStore
import java.util.UUID

/** Your own rules for ALIC. Each enabled rule is followed in every chat, voice and WhatsApp reply. */
@Composable
fun RulesScreen(nav: NavController) {
    val ctx = LocalContext.current
    val store = remember { AlicRuleStore(ctx) }
    var rules by remember { mutableStateOf(store.all()) }
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(uiBold("ALIC Rules"), onBack = { nav.popBackStack() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            NeoCard(Modifier.fillMaxWidth()) {
                Text(uiBold("Built-in: ALIC stays silent when more than one person is talking, and never interrupts a group conversation."), color = C.Sub, fontSize = 12.sp)
            }
            SectionTitle(uiBold("YOUR RULES"))
            NeoCard(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    text, { text = it }, label = { Text(uiBold("Write a rule")) }, minLines = 2,
                    modifier = Modifier.fillMaxWidth(), colors = fieldColors(), shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.height(8.dp))
                RedButton(uiBold("Add rule"), {
                    if (text.isNotBlank()) {
                        val list = rules + AlicRule(UUID.randomUUID().toString(), text.trim(), true)
                        store.save(list); rules = list; text = ""
                    }
                }, Modifier.fillMaxWidth())
            }
            rules.forEach { r ->
                NeoCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(r.text, color = C.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Switch(r.enabled, { v ->
                            val list = rules.map { if (it.id == r.id) it.copy(enabled = v) else it }
                            store.save(list); rules = list
                        })
                        IconButton(onClick = {
                            val list = rules.filter { it.id != r.id }
                            store.save(list); rules = list
                        }) { Icon(Icons.Filled.Delete, "Delete rule", tint = C.Err) }
                    }
                }
            }
            Text("Examples: \"Reply in Hinglish\", \"Keep answers under 3 lines\", \"Never mention my salary\".", color = C.Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
        }
    }
}
