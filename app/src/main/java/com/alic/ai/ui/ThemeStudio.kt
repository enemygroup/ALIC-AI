package com.alic.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Icon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

/** Pick one of the 15 app themes. The choice applies to the whole app at once and is saved. */
@Composable
fun ThemeStudioScreen(vm: AlicViewModel, nav: NavController) {
    val current = vm.settings.appTheme
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(uiBold("Theme Studio"), onBack = { nav.popBackStack() }) {
            TextButton(onClick = { vm.updateSettings { it.copy(appTheme = "") } }) { Text(uiBold("Reset"), color = C.Neon) }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(ThemePalettes.all) { p ->
                NeoCard(Modifier.fillMaxWidth(), selected = current == p.id, onClick = { vm.updateSettings { it.copy(appTheme = p.id) } }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name, color = C.Text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        listOf(p.bg, p.primary, p.secondary, p.accent).forEach { col ->
                            Box(Modifier.padding(start = 6.dp).size(20.dp).clip(CircleShape).background(col))
                        }
                        if (current == p.id) Icon(Icons.Filled.CheckCircle, "Selected", tint = C.Neon, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}
