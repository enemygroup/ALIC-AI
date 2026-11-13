package com.alic.ai.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.alic.ai.wake.VoskModelManager
import com.alic.ai.wake.WakeWordService

/** Consolidated "Wake Up Detection" settings. Used inside the app (route "wake") and as the assistant's settingsActivity. */
@Composable
fun WakeSettingsScreen(vm: AlicViewModel, standalone: Boolean = false, onBack: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val s = vm.settings
    var tick by remember { mutableStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }          // refresh status rows after returning from Android Settings
    val status by WakeWordService.status.collectAsState()
    val phrases by WakeWordService.phrasesInfo.collectAsState()

    if (standalone) LaunchedEffect(vm.toast) { vm.toast?.let { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show(); vm.toast = null } }

    val micOk = remember(tick) { hasPerm(ctx, Manifest.permission.RECORD_AUDIO) }
    val notifOk = remember(tick) { Build.VERSION.SDK_INT < 33 || hasPerm(ctx, Manifest.permission.POST_NOTIFICATIONS) }
    val overlayOk = remember(tick) { Settings.canDrawOverlays(ctx) }
    val assistantOk = remember(tick) { isDefaultAssistant(ctx) }
    val batteryOk = remember(tick) { ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) == true }
    val modelInfo = remember(tick, status) { VoskModelManager.describe(ctx) }

    fun enableWake() {
        vm.updateSettings { it.copy(wakeEnabled = true) }
        if (!WakeWordService.start(ctx)) vm.toast = WakeWordService.status.value
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        tick++
        if (ok) enableWake() else vm.toast = "Microphone permission is denied, so background wake listening cannot run."
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        tick++
        if (!ok) vm.toast = "Notifications are denied. The listener still runs but its status notification may be hidden."
    }

    val callPerms = listOf(
        Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG,
        Manifest.permission.ANSWER_PHONE_CALLS, Manifest.permission.READ_CONTACTS
    )
    val callOk = remember(tick) { callPerms.all { hasPerm(ctx, it) } }
    val callLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }

    fun open(intent: Intent, fallback: Intent? = null) {
        try { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { try { fallback?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } } catch (e2: Exception) { vm.toast = "This settings page is not available on this phone." } }
    }

    Column(Modifier.fillMaxSize().background(screenBg())) {
        ScreenHeader("Wake Up Detection", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 24.dp)) {
            SectionTitle("WAKE WORD")
            GlassCard(Modifier.fillMaxWidth()) {
                SwitchRow("Wake word “Hey Alic”", "Offline detection with Vosk. Turn on from this screen so Android allows the microphone service.", s.wakeEnabled) { on ->
                    if (on) { if (micOk) enableWake() else micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                    else { vm.updateSettings { it.copy(wakeEnabled = false) }; WakeWordService.stop(ctx) }
                }
                Text("Status: $status", color = C.Sub, fontSize = 12.sp)
                if (phrases.isNotBlank()) Text("Listening phrases: $phrases", color = C.Sub, fontSize = 12.sp)
                SwitchRow("Always-on background listening", "Off = listen only while the app is open", s.wakeAlwaysOn) { v -> vm.updateSettings { it.copy(wakeAlwaysOn = v) } }
                SwitchRow("Activation sound", null, s.wakeSound) { v -> vm.updateSettings { it.copy(wakeSound = v) } }
                Text("Wake sensitivity", color = C.Text, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                ChipsRow(listOf("kam" to "Kam (600)", "medium" to "Medium (300)", "zyada" to "Zyada (120)"), s.wakeSensitivity) { v -> vm.updateSettings { it.copy(wakeSensitivity = v) } }
            }

            SectionTitle("POPUP")
            GlassCard(Modifier.fillMaxWidth()) {
                SwitchRow("Popup animation", "Entrance animation only; closing never waits for it", s.popupAnimation) { v -> vm.updateSettings { it.copy(popupAnimation = v) } }
                Text("Close after silence", color = C.Text, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                ChipsRow(listOf("5" to "5 sec", "8" to "8 sec", "15" to "15 sec"), s.silenceTimeoutSec.toString()) { v -> vm.updateSettings { it.copy(silenceTimeoutSec = v.toInt()) } }
                Text("Say “sleep”, “so jao”, “band ho jao”, “sleep mode”, “chale jao”, “band karo”, “bye” or “good night” to close it.", color = C.Sub, fontSize = 12.sp)
            }

            SectionTitle("VOICE")
            GlassCard(Modifier.fillMaxWidth()) {
                SwitchRow("Voice interruption", "On: you can interrupt ALIC while he speaks. Off: the mic is muted (silence is sent) while he speaks.", s.voiceInterruption) { v -> vm.updateSettings { it.copy(voiceInterruption = v) } }
            }

            SectionTitle("PHONE CALLS")
            GlassCard(Modifier.fillMaxWidth()) {
                SwitchRow("Announce incoming calls", "ALIC says who is calling. With “Hey Alic” on, say “Hey Alic, hang up” or “Hey Alic, uthao”.", s.announceCalls) { v -> vm.updateSettings { it.copy(announceCalls = v) } }
                Text(if (callOk) "Call permissions: all granted" else "Call permissions: some are missing (Phone state, Call log, Answer/end calls, Contacts)", color = C.Sub, fontSize = 12.sp)
                if (!callOk) GhostButton("Allow call permissions", { callLauncher.launch(callPerms.toTypedArray()) }, Modifier.padding(top = 8.dp))
                Text(
                    "Voice hang-up works while the phone is ringing. Once a call is connected Android usually blocks other apps from the microphone, so it may not hear you; test on your phone.",
                    color = C.Sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)
                )
            }

            SectionTitle("STATUS")
            StatusCard("Microphone permission", if (micOk) "Granted" else "Denied: background wake listening cannot run", micOk, if (micOk) null else "Allow") {
                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            if (Build.VERSION.SDK_INT >= 33) StatusCard("Notification permission", if (notifOk) "Granted" else "Denied: the listening notification may be hidden", notifOk, if (notifOk) null else "Allow") {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            StatusCard("Overlay permission", if (overlayOk) "Allowed" else "Not allowed: no popup is drawn, voice still works", overlayOk, "Open settings") {
                open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName)))
            }
            StatusCard("Default digital assistant", if (assistantOk) "ALIC AI is the default" else "Not set", assistantOk, "Open settings") {
                open(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS), Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            }
            StatusCard("Battery optimization", if (batteryOk) "Unrestricted" else "Optimized: Android may stop the listener", batteryOk, "Open settings") {
                open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
            StatusCard("Vosk model", modelInfo, VoskModelManager.isUsable(VoskModelManager.modelDir(ctx)), null) {}

            SectionTitle("LIMITATIONS")
            Text(
                "Android does not let a third-party app guarantee always-on microphone listening or a popup on the lock screen. " +
                    "Android 14+ only allows starting the microphone service from a visible app, so enable the wake word from this screen. " +
                    "A force-stopped app does not restart itself, and phone makers may kill background services. " +
                    "Continuous listening uses battery. Accuracy of “Hey Alic” depends on the model, accent and noise.",
                color = C.Sub, fontSize = 12.sp, lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun StatusCard(title: String, status: String, ok: Boolean, button: String?, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(ok)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = C.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(status, color = C.Sub, fontSize = 12.sp)
            }
            if (button != null) GhostButton(button, onClick)
        }
    }
}

class WakeSettingsActivity : ComponentActivity() {
    private val vm: AlicViewModel by viewModels()
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AlicTheme { WakeSettingsScreen(vm, standalone = true) { finish() } } }
    }
}
