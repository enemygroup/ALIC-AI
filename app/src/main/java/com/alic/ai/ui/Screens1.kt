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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.alic.ai.service.AlicAccessibilityService
import com.alic.ai.wake.WakeWordService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.math.sin

// ---------------- Splash ----------------
@Composable
private fun Particles() {
    val t = rememberInfiniteTransition(label = "particles").animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "pt")
    Canvas(Modifier.fillMaxSize()) {
        for (i in 0 until 18) {
            val sx = ((i * 73) % 100) / 100f
            val sp = 0.4f + (i % 5) * 0.12f
            val y = 1f - ((t.value * sp + ((i * 37) % 100) / 100f) % 1f)
            drawCircle(
                C.Neon.copy(alpha = 0.10f + (i % 4) * 0.05f), radius = (1.5f + i % 3).dp.toPx(),
                center = Offset(sx * size.width + sin(t.value * 6.2832f + i) * 12f, y * size.height)
            )
        }
    }
}

/** Splash: the progress bar stays until the app's real state (settings) has loaded. */
@Composable
fun SplashScreen(ready: Boolean, onDone: () -> Unit) {
    val fade = remember { Animatable(0f) }
    val glow = remember { Animatable(0.6f) }
    val done by rememberUpdatedState(onDone)
    var minTime by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        launch { fade.animateTo(1f, tween(900)) }
        launch { glow.animateTo(1.25f, tween(1400, easing = FastOutSlowInEasing)) }
        delay(1800)
        minTime = true
    }
    LaunchedEffect(ready, minTime) { if (ready && minTime) { delay(250); done() } }
    Box(Modifier.fillMaxSize().background(screenBg())) {
        Particles()
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(170.dp).graphicsLayer { scaleX = glow.value; scaleY = glow.value; alpha = fade.value * 0.45f }
                        .clip(CircleShape).background(Brush.radialGradient(listOf(C.Neon.copy(alpha = 0.55f), Color.Transparent)))
                )
                Box(Modifier.graphicsLayer { alpha = fade.value }) { LogoMark(96.dp) }
            }
            Spacer(Modifier.height(18.dp))
            Text("ALIC AI", fontSize = 38.sp, fontWeight = FontWeight.Bold, color = C.Text, letterSpacing = 6.sp, modifier = Modifier.graphicsLayer { alpha = fade.value })
            Spacer(Modifier.height(6.dp))
            Text("Your Intelligent Personal Assistant", fontSize = 13.sp, color = C.Sub, modifier = Modifier.graphicsLayer { alpha = fade.value })
            Spacer(Modifier.height(36.dp))
            LinearProgressIndicator(modifier = Modifier.width(150.dp).clip(RoundedCornerShape(50)), color = C.Red, trackColor = C.Border)
            Text("Loading...", fontSize = 12.sp, color = C.Muted, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

// ---------------- Home ----------------
@Composable
private fun RoundControl(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(58.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(C.Glass1, C.Glass2)))
                .border(1.dp, glowBrush(), CircleShape).clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) { Icon(icon, label, tint = C.Neon, modifier = Modifier.size(26.dp)) }
        Text(label, color = C.Sub, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun HomeScreen(vm: AlicViewModel, nav: NavController, goLive: () -> Unit) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val s = vm.settings
    fun tap() { if (s.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    val accessibilityOn = remember(vm.liveActive) { AlicAccessibilityService.isEnabled(ctx) }
    val ringS = rememberInfiniteTransition(label = "ring").animateFloat(
        1f, 1.2f, infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "ringScale"
    )

    Column(Modifier.fillMaxSize().background(screenBg()).padding(horizontal = 16.dp)) {
        // Header: logo, online status, profile and settings shortcuts
        Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically) {
            LogoMark(34.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("ALIC AI", fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 2.sp, color = C.Text)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(vm.online); Spacer(Modifier.width(5.dp))
                    Text(if (vm.online) "Online" else "Offline", color = C.Sub, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton({ nav.navigate("profile") }) { Icon(Icons.Filled.Person, "Profile", tint = C.Text) }
            IconButton({ nav.navigate("settings") }) { Icon(Icons.Filled.Settings, "Settings", tint = C.Text) }
        }

        // Scrolling content
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.fillMaxWidth(0.74f)) { AlicOrb(vm.orb, vm.level, animated = s.animations) }
            Text(
                when {
                    !vm.online -> "You're offline"
                    vm.orb == OrbState.THINKING -> "Thinking..."
                    vm.orb == OrbState.SPEAKING -> "ALIC is speaking..."
                    vm.orb == OrbState.LISTENING || vm.orb == OrbState.LIVE -> "I'm listening..."
                    else -> "Hello, I'm ALIC AI."
                },
                color = C.Text, fontWeight = FontWeight.Bold, fontSize = 24.sp, textAlign = TextAlign.Center
            )
            Text(
                if (vm.online) "Your intelligent personal assistant." else "Voice and chat need an internet connection.",
                color = C.Sub, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(10.dp))
            LevelWave(vm.level, active = vm.liveActive && !vm.liveMuted)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusPill(if (vm.hasKey) "Gemini ready" else "Add API key", vm.hasKey) { nav.navigate("aiconfig") }
                StatusPill(if (s.wakeEnabled || s.clapEnabled) "Wake-up on" else "Wake-up off", s.wakeEnabled || s.clapEnabled) { nav.navigate("wake") }
                StatusPill(if (accessibilityOn) "Accessibility on" else "Accessibility off", accessibilityOn) { nav.navigate("permissions") }
            }
            if (vm.orb == OrbState.SPEAKING && !vm.liveActive || vm.orb == OrbState.THINKING && !vm.liveActive) {
                TextButton({ vm.stopAll() }) { Icon(Icons.Filled.Stop, null, tint = C.Neon); Spacer(Modifier.width(6.dp)); Text("Stop", color = C.Neon) }
            }
            val last = vm.messages.lastOrNull { !it.isUser && it.text.isNotBlank() }
            if (last != null) {
                Spacer(Modifier.height(12.dp))
                NeoCard(Modifier.fillMaxWidth(), onClick = { nav.navigate("chat") }) {
                    Text(last.text, color = C.Text, fontSize = 14.sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
                    Text("Open in chat", color = C.Neon, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Bottom controls: Chat | Microphone | Quick Actions
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            RoundControl(Icons.Filled.ChatBubble, "Chat") { tap(); nav.navigate("chat") }
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(92.dp).graphicsLayer { scaleX = ringS.value; scaleY = ringS.value; alpha = 0.5f }.clip(CircleShape).background(C.Red.copy(alpha = 0.25f)))
                Box(
                    Modifier.size(78.dp).clip(CircleShape).background(Brush.radialGradient(listOf(C.Neon, C.Red, C.Dark)))
                        .border(2.dp, C.Neon.copy(alpha = 0.7f), CircleShape).clickable { tap(); goLive() },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Mic, "Talk to ALIC", tint = C.OnPrimary, modifier = Modifier.size(38.dp)) }
            }
            RoundControl(Icons.Filled.GridView, "Quick Actions") { tap(); nav.navigate("quick") }
        }
    }
}

// ---------------- Chat ----------------
@Composable
fun ChatScreen(vm: AlicViewModel, goLive: () -> Unit) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    var input by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
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
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            LogoMark(32.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("ALIC AI", fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 2.sp, color = C.Text)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(vm.online); Spacer(Modifier.width(5.dp))
                    Text(if (vm.online) "Online" else "Offline", color = C.Sub, fontSize = 11.sp)
                }
            }
            Box {
                IconButton({ menu = true }) { Icon(Icons.Filled.MoreVert, "Menu", tint = C.Text) }
                DropdownMenu(menu, { menu = false }, modifier = Modifier.background(C.Bg2)) {
                    DropdownMenuItem(text = { Text("New chat", color = C.Text) }, onClick = { menu = false; vm.newConversation() })
                    DropdownMenuItem(text = { Text("Export chat", color = C.Text) }, onClick = { menu = false; vm.exportCurrent()?.let { shareText(ctx, it) } ?: run { vm.toast = "Nothing to export yet." } })
                    DropdownMenuItem(text = { Text("Clear chat", color = C.Err) }, onClick = { menu = false; confirmClear = true })
                }
            }
        }
        if (msgs.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth(0.5f)) { AlicOrb(OrbState.IDLE, 0f, animated = vm.settings.animations) }
                Text("Hello! I am ALIC AI. Your intelligent personal assistant. Tell me what you need, and I will help you.", color = C.Text, textAlign = TextAlign.Center, fontSize = 15.sp)
                Spacer(Modifier.height(14.dp))
                listOf("What is Android?", "WhatsApp kholo", "Mummy ko call karo", "Subah 7 baje ka alarm lagao").forEach {
                    Chip(it, false) { vm.send(it) }; Spacer(Modifier.height(8.dp))
                }
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(msgs, key = { it.id }) { m ->
                        Bubble(
                            m, isLast = m.id == msgs.last().id, busy = vm.busy,
                            onCopy = { clip.setText(AnnotatedString(m.text)); vm.toast = "Copied" },
                            onShare = { shareText(ctx, m.text) },
                            onSpeak = { vm.speakMessage(m.text) }, onRegen = { vm.regenerate() }
                        )
                    }
                }
                if (listState.canScrollForward) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).padding(14.dp).size(40.dp).clip(CircleShape)
                            .background(C.Red).clickable { scope.launch { listState.animateScrollToItem(msgs.lastIndex) } },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.KeyboardArrowDown, "Scroll to bottom", tint = C.OnPrimary) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                input, { input = it }, placeholder = { Text("Type a message...", color = C.Muted) }, modifier = Modifier.weight(1f),
                maxLines = 4, colors = fieldColors(), shape = RoundedCornerShape(26.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { submit() })
            )
            IconButton({ goLive() }) { Icon(Icons.Filled.Mic, "Voice", tint = C.Neon) }
            if (vm.busy) {
                IconButton({ vm.stopAll() }) { Icon(Icons.Filled.Stop, "Stop", tint = C.Err) }
            } else {
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(if (input.isNotBlank()) C.Red else C.Border)
                        .clickable(enabled = input.isNotBlank()) { submit() },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = if (input.isNotBlank()) C.OnPrimary else C.Muted) }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false }, containerColor = C.Card,
            title = { Text("Clear this chat?", color = C.Text) },
            text = { Text("This conversation will be deleted from this device.", color = C.Sub) },
            confirmButton = { TextButton({ confirmClear = false; vm.clearChat() }) { Text("Clear", color = C.Err) } },
            dismissButton = { TextButton({ confirmClear = false }) { Text("Cancel", color = C.Sub) } }
        )
    }
}

@Composable
fun Bubble(m: ChatMessage, isLast: Boolean, busy: Boolean, onCopy: () -> Unit, onShare: () -> Unit, onSpeak: () -> Unit, onRegen: () -> Unit) {
    val time = remember(m.ts) { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(m.ts)) }
    if (m.isUser) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            Box(
                Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp))
                    .background(Brush.linearGradient(listOf(C.Red, C.Dark))).padding(horizontal = 14.dp, vertical = 10.dp)
            ) { Text(m.text, color = C.OnPrimary, fontSize = 15.sp) }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp, end = 4.dp)) {
                Text(time, color = C.Muted, fontSize = 10.sp)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.DoneAll, null, tint = C.Neon, modifier = Modifier.size(13.dp))
            }
        }
    } else {
        val failed = m.text.startsWith("⚠️")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(34.dp).clip(CircleShape).border(1.dp, glowBrush(), CircleShape), contentAlignment = Alignment.Center) { LogoMark(20.dp) }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f, fill = false)) {
                val shape = RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp)
                Column(
                    Modifier.widthIn(max = 320.dp).clip(shape).background(Brush.verticalGradient(listOf(C.Glass1, C.Glass2)))
                        .border(1.dp, if (failed) C.Err.copy(alpha = 0.7f) else C.Border, shape).padding(12.dp)
                ) {
                    if (m.text.isBlank() && busy) TypingDots() else MessageContent(m.text)
                    if (failed && isLast && !busy) TextButton(onRegen, contentPadding = PaddingValues(0.dp)) {
                        Icon(Icons.Filled.Refresh, null, tint = C.Neon, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Retry", color = C.Neon)
                    }
                }
                if (m.text.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(time, color = C.Muted, fontSize = 10.sp, modifier = Modifier.padding(start = 4.dp))
                        IconButton(onCopy, Modifier.size(32.dp)) { Icon(Icons.Filled.ContentCopy, "Copy", tint = C.Sub, modifier = Modifier.size(16.dp)) }
                        IconButton(onShare, Modifier.size(32.dp)) { Icon(Icons.Filled.Share, "Share", tint = C.Sub, modifier = Modifier.size(16.dp)) }
                        IconButton(onSpeak, Modifier.size(32.dp)) { Icon(Icons.Filled.VolumeUp, "Speak", tint = C.Sub, modifier = Modifier.size(16.dp)) }
                        if (isLast && !busy && !failed) IconButton(onRegen, Modifier.size(32.dp)) { Icon(Icons.Filled.Refresh, "Regenerate", tint = C.Neon, modifier = Modifier.size(16.dp)) }
                    }
                }
            }
        }
    }
}
