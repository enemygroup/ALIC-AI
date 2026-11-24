package com.alic.ai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.alic.ai.data.ReplyLanguages

/** Pick the language ALIC always replies in. Saved and used for chat, voice and projects. */
@Composable
fun LanguageScreen(vm: AlicViewModel, nav: NavController) {
    val current = vm.settings.replyLanguage
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Reply language", onBack = { nav.popBackStack() }) {}
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("ALIC will always answer in the language you choose here.", color = C.Sub, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
            }
            items(ReplyLanguages.all) { lang ->
                NeoCard(Modifier.fillMaxWidth(), selected = current == lang.id,
                    onClick = { vm.updateSettings { it.copy(replyLanguage = lang.id) } }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(lang.label, color = C.Text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        if (current == lang.id) Icon(Icons.Filled.CheckCircle, "Selected", tint = C.Neon)
                    }
                }
            }
        }
    }
}
