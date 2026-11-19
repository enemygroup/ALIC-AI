package com.alic.ai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.alic.ai.data.OrbState

/** Choose one of the 15 orb designs, preview it live, and tune size, glow, speed, density and particles. */
@Composable
fun OrbStudioScreen(vm: AlicViewModel, nav: NavController) {
    val s = vm.settings
    val current = normalizeOrb(s.orbStyle)
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Orb Studio", onBack = { nav.popBackStack() }) {
            TextButton(onClick = {
                vm.updateSettings {
                    it.copy(orbStyle = "particle", orbSize = 1f, orbGlow = 1f, orbSpeed = 1f, orbDensity = 1f, orbParticles = true, orbGlowOn = true)
                }
            }) { Text("Reset", color = C.Neon) }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(14.dp)) {
            item {
                Box(Modifier.fillMaxWidth(0.62f).align(Alignment.CenterHorizontally)) { AlicOrb(OrbState.LIVE, 0f, animated = true) }
                Text(
                    "Preview: " + (ORB_STYLES.firstOrNull { it.id == current }?.label ?: ""),
                    color = C.Sub, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp)
                )
            }
            item { SectionTitle("STYLE") }
            items(ORB_STYLES.chunked(3)) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { d ->
                        NeoCard(Modifier.weight(1f), selected = current == d.id, onClick = { vm.updateSettings { it.copy(orbStyle = d.id) } }) {
                            Text(d.label, color = C.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(6.dp))
            }
            item { SectionTitle("ADJUST") }
            item { SliderRow("Size", s.orbSize, 0.6f..1.2f, "%.2f") { v -> vm.updateSettings { it.copy(orbSize = v) } } }
            item { SliderRow("Glow intensity", s.orbGlow, 0f..1.5f, "%.2f") { v -> vm.updateSettings { it.copy(orbGlow = v) } } }
            item { SliderRow("Animation speed", s.orbSpeed, 0.5f..2f, "%.2f") { v -> vm.updateSettings { it.copy(orbSpeed = v) } } }
            item { SliderRow("Particle density", s.orbDensity, 0.4f..1.5f, "%.2f") { v -> vm.updateSettings { it.copy(orbDensity = v) } } }
            item { SwitchRow("Particles", "Floating and orbiting particles", s.orbParticles) { v -> vm.updateSettings { it.copy(orbParticles = v) } } }
            item { SwitchRow("Glow", "Outer glow and halo", s.orbGlowOn) { v -> vm.updateSettings { it.copy(orbGlowOn = v) } } }
            item {
                Text("Changes are saved automatically and shown on the Home orb.", color = C.Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}
