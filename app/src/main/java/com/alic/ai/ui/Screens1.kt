package com.alic.ai.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import com.alic.ai.service.AlicAccessibilityService
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.alic.ai.data.ChatMessage
import com.alic.ai.data.OrbState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

// ---------------- Splash ----------------
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val scale = remember { Animatable(0.4f) }
    val fade = remember { Animatable(0f) }
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, tween(1400, easing = FastOutSlowInEasing)) }
        launch { fade.animateTo(1f, tween(1200, delayMillis = 500)) }
        delay(2600)
        done()
    }
    Column(Modifier.fillMaxSize().background(screenBg()), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth(0.8f).scale(scale.value)) { AlicOrb(OrbState.LIVE, 0f) }
        Spacer(Modifier.height(20.dp))
        Text("ALIC AI", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = C.Text, letterSpacing = 8.sp, modifier = Modifier.alpha(fade.value))
        Spacer(Modifier.height(8.dp))
        Text("THE POWER OF INTELLIGENCE", fontSize = 12.sp, color = C.Neon, letterSpacing = 3.sp, modifier = Modifier.alpha(fade.value))
    }
}

// ---------------- Home ----------------
@Composable
private fun QuickAction(label: String, icon: ImageVector, index: Int, onClick: () -> Unit) {
    Column(
        Modifier.width(78.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(Brush.linearGradient(listOf(C.orb[index % C.orb.size], C.Dark))),
            contentAlignment = Alignment.Center
        ) { Icon(icon, label, tint = C.OnPrimary, modifier = Modifier.size(26.dp)) }
        Text(label, fontSize = 11.sp, color = C.Sub, maxLines = 1, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
fun HomeScreen(vm: AlicViewModel, nav: NavController, goLive: () -> Unit) {
    val ctx = LocalContext.current
    var input by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val s = vm.settings
    fun tap() { if (s.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    fun submit() {
        if (input.isNotBlank()) { tap(); vm.send(input); input = ""; focus.clearFocus() }
    }
    // Read inside graphicsLayer (draw phase) so this pulse never recomposes the screen.
    val ringS = rememberInfiniteTransition(label = "ring").animateFloat(
        1f, 1.22f, infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "ringScale"
    )
    val accessibilityOn = remember(vm.liveActive) { AlicAccessibilityService.isEnabled(ctx) }

    Column(Modifier.fillMaxSize().background(screenBg()).padding(horizontal = 14.dp)) {
        // Header (fixed)
        Row(Modifier.fillMaxWidth().height(54.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color.White, C.Neon, C.Dark))))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("ALIC AI", fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 3.sp, color = C.Text)
                Text("THE POWER OF INTELLIGENCE", fontSize = 8.sp, letterSpacing = 1.5.sp, color = C.Neon)
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(C.Glass).padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusDot(vm.online); Spacer(Modifier.width(6.dp))
                Text(if (vm.online) "Online" else "Offline", color = C.Text, fontSize = 12.sp)
            }
            IconButton({ nav.navigate("permissions") }) { Icon(Icons.Filled.Security, "Permissions", tint = C.Text) }
        }

        // Whole page scrolls (the input bar below stays fixed)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.fillMaxWidth(0.80f)) { AlicOrb(vm.orb, vm.level, animated = s.animations) }
            Text(
                when (vm.orb) {
                    OrbState.IDLE -> "ALIC AI IS READY"; OrbState.LISTENING -> "LISTENING…"; OrbState.THINKING -> "THINKING…"
                    OrbState.SPEAKING -> "SPEAKING"; OrbState.LIVE -> "LIVE CONVERSATION"
                },
                color = C.Text, fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = 3.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusPill(if (vm.hasKey) "Gemini ready" else "Add API key", vm.hasKey) { nav.navigate("aiconfig") }
                StatusPill(if (s.wakeEnabled) "Hey Alic on" else "Hey Alic off", s.wakeEnabled) { nav.navigate("wake") }
                StatusPill(if (accessibilityOn) "Accessibility on" else "Accessibility off", accessibilityOn) { nav.navigate("permissions") }
            }
            if (vm.orb == OrbState.SPEAKING || vm.orb == OrbState.THINKING) {
                TextButton({ vm.stopAll() }) { Icon(Icons.Filled.Stop, null, tint = C.Neon); Spacer(Modifier.width(6.dp)); Text("Stop", color = C.Neon) }
            }

            val last = vm.messages.lastOrNull { !it.isUser && it.text.isNotBlank() }
            if (last != null) {
                Spacer(Modifier.height(12.dp))
                GlassCard(Modifier.fillMaxWidth(), onClick = { nav.navigate("chat") }) {
                    Text(last.text, color = C.Text, fontSize = 14.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
                    Text("Open in chat", color = C.Neon, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }

            Spacer(Modifier.height(10.dp))
            Text("QUICK ACTIONS", color = C.Sub, fontSize = 11.sp, letterSpacing = 2.sp, modifier = Modifier.align(Alignment.Start))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                QuickAction("YouTube", Icons.Filled.PlayArrow, 0) { tap(); vm.send("YouTube kholo") }
                QuickAction("Camera", Icons.Filled.CameraAlt, 1) { tap(); vm.send("Camera open karo") }
                QuickAction("WhatsApp", Icons.Filled.ChatBubble, 2) { tap(); vm.send("WhatsApp kholo") }
                QuickAction("Maps", Icons.Filled.Place, 3) { tap(); vm.send("Maps kholo") }
                QuickAction("Alarm", Icons.Filled.Alarm, 0) { tap(); vm.send("Subah 7 baje ka alarm lagao") }
                QuickAction("Torch", Icons.Filled.FlashlightOn, 1) { tap(); vm.send("Torch on karo") }
                QuickAction("Vision", Icons.Filled.Visibility, 2) { tap(); nav.navigate("vision") }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Input (fixed)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                input, { input = it }, placeholder = { Text("Message ALIC AI…", color = C.Sub) }, singleLine = true,
                modifier = Modifier.weight(1f), colors = fieldColors(), shape = RoundedCornerShape(26.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { submit() })
            )
            IconButton({ submit() }, enabled = input.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = if (input.isNotBlank()) C.Red else C.Border)
            }
            // Live talk button (the only voice input), with a pulsing ring drawn in the graphics layer
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(start = 4.dp)) {
                Box(
                    Modifier.size(58.dp).graphicsLayer { scaleX = ringS.value; scaleY = ringS.value }
                        .clip(CircleShape).background(C.Red.copy(alpha = 0.18f))
                )
                Box(
                    Modifier.size(50.dp).clip(CircleShape)
                        .background(Brush.radialGradient(listOf(C.Neon, C.Red, C.Dark)))
                        .clickable { tap(); goLive() },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.GraphicEq, "Talk live", tint = C.OnPrimary, modifier = Modifier.size(26.dp)) }
            }
        }
        Text(
            "TAP THE WAVE TO TALK LIVE", color = C.Sub, fontSize = 9.sp, letterSpacing = 2.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp, bottom = 6.dp)
        )
    }
}

// ---------------- Chat ----------------
@Composable
fun ChatScreen(vm: AlicViewModel) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    var input by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val msgs = vm.messages
    LaunchedEffect(msgs.size, msgs.lastOrNull()?.text?.length) {
        if (msgs.isNotEmpty()) listState.scrollToItem(msgs.lastIndex)
    }
    fun submit() {
        if (input.isBlank()) return
        if (vm.settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        vm.send(input); input = ""
    }
    Column(Modifier.fillMaxSize().background(screenBg())) {
        ScreenHeader("Chat") {
            IconButton({ vm.newConversation() }) { Icon(Icons.Filled.Add, "New conversation", tint = C.Text) }
            IconButton({ vm.exportCurrent()?.let { shareText(ctx, it) } ?: run { vm.toast = "Nothing to export yet." } }) { Icon(Icons.Filled.Share, "Export", tint = C.Text) }
            IconButton({ confirmClear = true }) { Icon(Icons.Filled.Delete, "Clear chat", tint = C.Text) }
        }
        if (msgs.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth(0.55f)) { AlicOrb(OrbState.IDLE, 0f, animated = vm.settings.animations) }
                Text("Hello! I am ALIC AI. Your intelligent personal assistant. Tell me what you need, and I will help you.", color = C.Text, textAlign = TextAlign.Center, fontSize = 15.sp)
                Spacer(Modifier.height(14.dp))
                listOf("WhatsApp kholo", "Mummy ko call karo", "Arijit Singh ke gaane lagao", "Explain AI simply").forEach {
                    Chip(it, false) { vm.send(it) }; Spacer(Modifier.height(8.dp))
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(msgs, key = { it.id }) { m ->
                    Bubble(
                        m, isLast = m.id == msgs.last().id, busy = vm.busy,
                        onCopy = { clip.setText(AnnotatedString(m.text)); vm.toast = "Copied" },
                        onSpeak = { vm.speakMessage(m.text) }, onRegen = { vm.regenerate() }
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                input, { input = it }, placeholder = { Text("Type a message…", color = C.Sub) }, modifier = Modifier.weight(1f),
                maxLines = 4, colors = fieldColors(), shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { submit() })
            )
            if (vm.busy) IconButton({ vm.stopAll() }) { Icon(Icons.Filled.Stop, "Stop", tint = C.Red) }
            else IconButton({ submit() }, enabled = input.isNotBlank()) { Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = if (input.isNotBlank()) C.Red else C.Border) }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false }, containerColor = C.Card,
            title = { Text("Clear this chat?", color = C.Text) },
            text = { Text("This conversation will be deleted from this device.", color = C.Sub) },
            confirmButton = { TextButton({ confirmClear = false; vm.clearChat() }) { Text("Clear", color = C.Neon) } },
            dismissButton = { TextButton({ confirmClear = false }) { Text("Cancel", color = C.Sub) } }
        )
    }
}

@Composable
fun Bubble(m: ChatMessage, isLast: Boolean, busy: Boolean, onCopy: () -> Unit, onSpeak: () -> Unit, onRegen: () -> Unit) {
    val time = remember(m.ts) { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(m.ts)) }
    if (m.isUser) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            Box(
                Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                    .background(Brush.linearGradient(listOf(C.Red, C.Dark))).padding(12.dp)
            ) { Text(m.text, color = C.OnPrimary, fontSize = 15.sp) }
            Text(time, color = C.Sub, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp, end = 4.dp))
        }
    } else {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
            Column(
                Modifier.widthIn(max = 330.dp).clip(RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp))
                    .background(C.Card).border(1.dp, C.Border, RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)).padding(12.dp)
            ) {
                if (m.text.isBlank() && busy) TypingDots() else MessageContent(m.text)
            }
            if (m.text.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(time, color = C.Sub, fontSize = 10.sp, modifier = Modifier.padding(start = 4.dp))
                    IconButton(onCopy, Modifier.size(32.dp)) { Icon(Icons.Filled.ContentCopy, "Copy", tint = C.Sub, modifier = Modifier.size(16.dp)) }
                    IconButton(onSpeak, Modifier.size(32.dp)) { Icon(Icons.Filled.VolumeUp, "Speak", tint = C.Sub, modifier = Modifier.size(16.dp)) }
                    if (isLast && !busy) IconButton(onRegen, Modifier.size(32.dp)) { Icon(Icons.Filled.Refresh, "Regenerate", tint = C.Neon, modifier = Modifier.size(16.dp)) }
                }
            }
        }
    }
}
