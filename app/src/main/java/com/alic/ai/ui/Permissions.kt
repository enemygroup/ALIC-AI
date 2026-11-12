package com.alic.ai.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavController
import com.alic.ai.service.AlicAccessibilityService

fun hasPerm(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

fun isDefaultAssistant(ctx: Context): Boolean = try {
    if (Build.VERSION.SDK_INT >= 29) ctx.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true
    else Settings.Secure.getString(ctx.contentResolver, "assistant")?.startsWith(ctx.packageName) == true
} catch (e: Exception) { false }

private data class PermRow(val title: String, val why: String, val icon: ImageVector, val granted: Boolean, val kind: String, val perm: String? = null)

@Composable
fun PermissionsScreen(vm: AlicViewModel, nav: NavController) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("alic_prefs", Context.MODE_PRIVATE) }
    val first = remember { !prefs.getBoolean("perm_done", false) }
    var tick by remember { mutableStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }

    val rows = remember(tick) {
        val notifOk = Build.VERSION.SDK_INT < 33 || hasPerm(ctx, Manifest.permission.POST_NOTIFICATIONS)
        listOf(
            PermRow("Microphone", "Talk to ALIC in Live conversation", Icons.Filled.Mic, hasPerm(ctx, Manifest.permission.RECORD_AUDIO), "runtime", Manifest.permission.RECORD_AUDIO),
            PermRow("Camera", "Take photos for AI Vision", Icons.Filled.CameraAlt, hasPerm(ctx, Manifest.permission.CAMERA), "runtime", Manifest.permission.CAMERA),
            PermRow("Notifications", "Show the Stop button during live sessions", Icons.Filled.Notifications, notifOk, "runtime",
                if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null),
            PermRow("Contacts", "Find a person by name when you say “call Mummy” or “message Papa”", Icons.Filled.Contacts, hasPerm(ctx, Manifest.permission.READ_CONTACTS), "runtime", Manifest.permission.READ_CONTACTS),
            PermRow("Phone calls", "Place the call directly instead of only opening the dialer", Icons.Filled.Call, hasPerm(ctx, Manifest.permission.CALL_PHONE), "runtime", Manifest.permission.CALL_PHONE),
            PermRow("Accessibility service", "Lets ALIC press Back, Home, Recents and open notifications by command. It does not read your screen.", Icons.Filled.Accessibility, AlicAccessibilityService.isEnabled(ctx), "accessibility"),
            PermRow("Display over other apps", "Lets ALIC open apps and keep working while another app is on screen", Icons.Filled.Layers, Settings.canDrawOverlays(ctx), "overlay"),
            PermRow("Default AI assistant", "Make ALIC the assistant that opens when you hold the Home button or power button.", Icons.Filled.AutoAwesome, isDefaultAssistant(ctx), "assistant")
        )
    }
    val missingRuntime = rows.filter { !it.granted && it.kind == "runtime" && it.perm != null }.map { it.perm!! }

    fun openAccessibility() {
        vm.toast = "Find ALIC AI in the list and turn it on."
        ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openAssistantSettings() {
        vm.toast = "Choose ALIC AI under “Digital assistant app”."
        try {
            ctx.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            try {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e2: Exception) {
                vm.toast = "Open Settings > Apps > Default apps > Digital assistant app and choose ALIC AI."
            }
        }
    }

    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        tick++
        if (!isDefaultAssistant(ctx)) openAssistantSettings()
    }

    fun requestAssistant() {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val rm = ctx.getSystemService(RoleManager::class.java)
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_ASSISTANT) && !rm.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
                    roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT))
                    return
                }
            }
        } catch (e: Exception) {
        }
        openAssistantSettings()
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        tick++
        if (!AlicAccessibilityService.isEnabled(ctx)) openAccessibility()
    }
    LaunchedEffect(Unit) { if (first && missingRuntime.isNotEmpty()) launcher.launch(missingRuntime.toTypedArray()) }

    fun finish() {
        prefs.edit().putBoolean("perm_done", true).apply()
        if (first) nav.navigate("home") { popUpTo("permissions") { inclusive = true } } else nav.popBackStack()
    }

    fun openOverlay() {
        vm.toast = "Turn on “Allow display over other apps” for ALIC AI."
        ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun act(r: PermRow) {
        when (r.kind) {
            "runtime" -> if (r.perm != null) launcher.launch(arrayOf(r.perm))
            "accessibility" -> openAccessibility()
            "overlay" -> openOverlay()
            else -> requestAssistant()
        }
    }

    Column(Modifier.fillMaxSize().background(screenBg())) {
        ScreenHeader("Permissions", onBack = if (first) null else ({ nav.popBackStack() }))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
            Text("ALIC AI only asks for what its features need. You can change any of these later in Android settings.", color = C.Sub, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            rows.forEach { r ->
                GlassCard(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(r.icon, null, tint = if (r.granted) Color(0xFF2ECC71) else C.Neon, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.title, color = C.Text, fontWeight = FontWeight.SemiBold)
                            Text(r.why, color = C.Sub, fontSize = 12.sp)
                        }
                        if (r.granted) Text("ON", color = Color(0xFF2ECC71), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        else Button(
                            onClick = { act(r) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = C.Red, contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 14.dp)
                        ) { Text("Allow") }
                    }
                }
            }
            Text(
                "Android 13+ may grey out the accessibility switch for apps installed from outside the Play Store. If so: Settings > Apps > ALIC AI > ⋮ menu > Allow restricted settings, then try again.",
                color = C.Sub, fontSize = 12.sp, lineHeight = 17.sp
            )
            Spacer(Modifier.height(10.dp))
            GhostButton("Open Android app settings", {
                ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }, Modifier.fillMaxWidth(), Icons.Filled.Settings)
            Spacer(Modifier.height(24.dp))
        }
        Column(Modifier.padding(14.dp)) {
            if (rows.any { !it.granted }) {
                RedButton("Allow all", {
                    val next = rows.firstOrNull { !it.granted }
                    if (missingRuntime.isNotEmpty()) launcher.launch(missingRuntime.toTypedArray())
                    else if (next != null) act(next)
                }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
            }
            GhostButton(if (first) "Continue" else "Done", { finish() }, Modifier.fillMaxWidth())
        }
    }
}
