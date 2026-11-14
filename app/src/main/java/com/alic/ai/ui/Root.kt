package com.alic.ai.ui

import android.Manifest
import android.app.Activity
import android.media.AudioManager
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.alic.ai.device.CommandRouter


/** Runs [action] once the microphone permission is granted (asks if needed). Used for Live. */
@Composable
fun rememberMicGate(vm: AlicViewModel): Gate {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val ok = hasPerm(ctx, Manifest.permission.RECORD_AUDIO)
        val p = pending
        pending = null
        if (ok) p?.invoke() else vm.toast = "Microphone permission is required for Live conversation."
    }
    val gate: Gate = remember {
        { action: () -> Unit ->
            if (hasPerm(ctx, Manifest.permission.RECORD_AUDIO)) action() else {
                pending = action
                val perms = buildList {
                    add(Manifest.permission.RECORD_AUDIO)
                    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                }
                launcher.launch(perms.toTypedArray())
            }
        }
    }
    return gate
}

@Composable
fun AlicRoot(vm: AlicViewModel) {
    val ctx = LocalContext.current
    val nav = rememberNavController()
    val snack = remember { SnackbarHostState() }
    val gate = rememberMicGate(vm)
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    LaunchedEffect(vm.toast) {
        vm.toast?.let { snack.showSnackbar(it); vm.toast = null }
    }

    val goLive: () -> Unit = {
        gate {
            vm.startLive()
            nav.navigate("live")
        }
    }
    val barRoutes = setOf("home", "chat", "live", "tools", "profile")

    Scaffold(
        containerColor = C.Bg,
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            if (route in barRoutes) {
                AppBottomBar(route) { target ->
                    if (target == "live") goLive()
                    else nav.navigate(target) { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true }
                }
            }
        }
    ) { pad ->
        NavHost(nav, startDestination = "splash", modifier = Modifier.padding(pad)) {
            composable("splash") {
                SplashScreen(ready = vm.settingsLoaded) {
                    val done = ctx.getSharedPreferences("alic_prefs", Context.MODE_PRIVATE).getBoolean("perm_done", false)
                    nav.navigate(if (done) "home" else "permissions") { popUpTo("splash") { inclusive = true } }
                }
            }
            composable("permissions") { PermissionsScreen(vm, nav) }
            composable("home") { HomeScreen(vm, nav, goLive) }
            composable("chat") { ChatScreen(vm, goLive) }
            composable("quick") { QuickScreen(vm, nav, goLive) }
            composable("tools") { ToolsScreen(vm, nav, goLive) }
            composable("profile") { ProfileScreen(vm, nav) }
            composable("settings") { SettingsScreen(vm, nav) }
            composable("history") { HistoryScreen(vm, nav) }
            composable("wake") { WakeSettingsScreen(vm, onBack = { nav.popBackStack() }) }
            composable("aiconfig") { AIConfigScreen(vm, nav) }
            composable("websearch") { WebSearchScreen(vm, nav) }
            composable("imagegen") { ImageGenScreen(vm, nav) }
            composable("fileassist") { FileAssistScreen(vm, nav) }
            composable("coding") { CodingScreen(vm, nav) }
            composable("vision") { VisionScreen(vm, nav) }
            composable("youtube") { YouTubeScreen(vm, nav) }
            composable("apps") { AppsScreen(vm, nav) }
            composable("live") { LiveScreen(vm, nav) }
        }
    }

    if (vm.codeStage != CodeStage.IDLE) CodingOverlay(vm)

    vm.pendingCommand?.let { c ->
        AlertDialog(
            onDismissRequest = { vm.confirmCommand(false) }, containerColor = C.Card,
            title = { Text("Confirm action", color = C.Text) },
            text = { Text(CommandRouter.describe(c), color = C.Sub) },
            confirmButton = { TextButton({ vm.confirmCommand(true) }) { Text("Allow", color = C.Neon) } },
            dismissButton = { TextButton({ vm.confirmCommand(false) }) { Text("Cancel", color = C.Sub) } }
        )
    }
}
