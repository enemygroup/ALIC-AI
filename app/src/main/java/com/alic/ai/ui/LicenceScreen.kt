package com.alic.ai.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alic.ai.licence.LicenceResult
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val GreenHi = Color(0xFF39FF7A)
private val GreenMid = Color(0xFF00D95A)
private val GreenDeep = Color(0xFF0A7A2E)
private val GreenGlass = Color(0x6612FF5A)
private val BgTop = Color(0xFF021A09)

/** Dark green glass screen with neon edges and a soft pulsing glow behind the A logo. */
@Composable
private fun GreenScreen(content: @Composable ColumnScope.() -> Unit) {
    val pulse by rememberInfiniteTransition(label = "gl").animateFloat(
        0.55f, 1f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glowPulse"
    )
    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgTop, Color.Black)))
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(listOf(GreenMid.copy(alpha = 0.35f * pulse), Color.Transparent), center = Offset(size.width / 2f, size.height * 0.2f), radius = size.width * 0.6f),
                    radius = size.width * 0.6f, center = Offset(size.width / 2f, size.height * 0.2f)
                )
            }
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

/** Neon green glass card with a glowing edge. */
@Composable
private fun GlassPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier.fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xCC0B2A14), Color(0xCC03120A))))
            .border(1.5.dp, Brush.linearGradient(listOf(GreenHi, GreenDeep, GreenHi)), shape)
            .padding(20.dp),
        content = content
    )
}

@Composable
private fun GreenButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(27.dp))
            .background(
                if (enabled) Brush.horizontalGradient(listOf(GreenDeep, GreenMid, GreenHi, GreenMid, GreenDeep))
                else Brush.horizontalGradient(listOf(Color(0xFF1A3A22), Color(0xFF1A3A22)))
            )
            .border(1.dp, GreenHi.copy(alpha = if (enabled) 0.9f else 0.2f), RoundedCornerShape(27.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (enabled) Color.Black else Color(0xFF6B8A72), fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

/** Big "A" logo with a rotating neon ring and a soft outer glow. */
@Composable
private fun NeonLogo() {
    val spin by rememberInfiniteTransition(label = "ring").animateFloat(
        0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "spin"
    )
    Box(Modifier.size(170.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxSize().graphicsLayer { rotationZ = spin }
                .border(2.dp, Brush.sweepGradient(listOf(GreenHi, Color.Transparent, GreenMid, Color.Transparent, GreenHi)), CircleShape)
        )
        LogoMark(120.dp)
    }
}

/** Walk-through of the two steps: sign in, then activate. */
@Composable
private fun StepPills(step: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf("Sign in", "Activate", "Ready").forEachIndexed { i, label ->
            val on = step >= i
            Box(
                Modifier.clip(RoundedCornerShape(50))
                    .background(if (on) GreenGlass else Color(0x22FFFFFF))
                    .border(1.dp, if (on) GreenHi else Color(0x44FFFFFF), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) { Text(label, color = if (on) GreenHi else Color(0xFF8AA392), fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
fun LicenceScreen(vm: AlicViewModel) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var create by remember { mutableStateOf(false) }
    var remember by remember { mutableStateOf(true) }
    var key by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }
    val step = when { vm.licenceChecking -> 0; !vm.licenceSignedIn -> 0; else -> 1 }

    GreenScreen {
        SupportRow()
        NeonLogo()
        Text("Alic AI", color = Color.White, fontWeight = FontWeight.Black, fontSize = 40.sp)
        Text("Smarter  •  Faster  •  Powerful", color = GreenHi, fontSize = 13.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(18.dp))

        if (vm.licenceChecking) {
            GlassPanel { Text("Checking your licence...", color = GreenHi, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        } else if (!vm.licenceSignedIn) {
            // ---- LOGIN ----
            GlassPanel {
                Text(if (create) "Create Account" else "Welcome Back!", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Text(if (create) "Create your account to get started" else "Sign in to continue with Alic AI", color = Color(0xFFB7CDBD), fontSize = 13.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(email, { email = it }, label = { Text("Email Address") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), colors = greenFieldColors(), shape = RoundedCornerShape(16.dp))
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(pass, { pass = it }, label = { Text("Password (min 6)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                    colors = greenFieldColors(), shape = RoundedCornerShape(16.dp))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.clickable { remember = !remember }, verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(20.dp).clip(RoundedCornerShape(5.dp))
                                .background(if (remember) GreenMid else Color.Transparent)
                                .border(1.5.dp, GreenHi, RoundedCornerShape(5.dp)),
                            contentAlignment = Alignment.Center
                        ) { if (remember) Text("✓", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(8.dp))
                        Text("Remember Me", color = Color.White, fontSize = 13.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        if (email.isBlank()) { err = "Enter your email first."; return@TextButton }
                        busy = true; err = ""; info = ""
                        scope.launch {
                            val e = vm.licence.resetPassword(email)
                            busy = false
                            if (e != null) err = e else info = "Reset link sent to $email."
                        }
                    }) { Text("Forgot Password?", color = GreenHi, fontSize = 12.sp) }
                }
                Spacer(Modifier.height(12.dp))
                GreenButton(if (busy) "Please wait..." else if (create) "Create Account" else "Login", !busy && email.isNotBlank() && pass.length >= 6) {
                    busy = true; err = ""; info = ""
                    scope.launch {
                        val e = vm.licence.signIn(email, pass, create)
                        busy = false
                        if (e != null) err = e else vm.refreshLicence()
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    Text(if (create) "Already have an account?  " else "Don't have an account?  ", color = Color(0xFFB7CDBD), fontSize = 13.sp)
                    Text(if (create) "Login" else "Create Account", color = GreenHi, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { create = !create; err = ""; info = "" })
                }
            }
        } else {
            // ---- LICENCE ACTIVATION ----
            Text("License Activation", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(12.dp))
            GlassPanel {
                Text("Activate Your License", color = GreenHi, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("Enter the 35-character key you received after purchase.", color = Color(0xFFB7CDBD), fontSize = 13.sp)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(key, { key = it }, label = { Text("Enter your license key") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), colors = greenFieldColors(), shape = RoundedCornerShape(16.dp))
                Spacer(Modifier.height(12.dp))
                GreenButton(if (busy) "Checking..." else "Activate License", !busy && key.isNotBlank()) {
                    busy = true; err = ""; info = ""
                    scope.launch {
                        val r = vm.licence.activate(key)
                        busy = false
                        if (r is LicenceResult.Denied) err = r.msg else vm.refreshLicence()
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            GlassPanel {
                val exp = vm.licence.savedExpiry()
                val saved = vm.licence.savedKey()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("License Status", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    Box(Modifier.clip(RoundedCornerShape(50)).background(GreenGlass).border(1.dp, GreenHi, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Text(if (saved.isBlank()) "Not active" else "Active", color = GreenHi, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(10.dp))
                StatusRow("Expiry Date", if (exp == 0L) "Lifetime / not set" else SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(exp)))
                StatusRow("Device Info", "Android ${Build.VERSION.RELEASE} • ${Build.MODEL}")
                StatusRow("Signed in as", vm.licence.currentEmail())
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { vm.licence.signOut(); vm.refreshLicence() }) { Text("Sign out", color = Color(0xFFB7CDBD), fontSize = 13.sp) }
            }
        }

        Spacer(Modifier.height(12.dp))
        StepPills(step)
        if (info.isNotBlank()) { Spacer(Modifier.height(10.dp)); Text(info, color = GreenHi, fontSize = 13.sp, textAlign = TextAlign.Center) }
        if (err.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x33FF3B3B))
                .border(1.dp, Color(0xFFFF5252), RoundedCornerShape(14.dp)).padding(12.dp)) {
                Text(err, color = Color(0xFFFFB4B4), fontSize = 13.sp)
            }
        }
        if (vm.licenceMsg.isNotBlank() && err.isBlank() && info.isBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(vm.licenceMsg, color = Color(0xFFB7CDBD), fontSize = 12.sp, textAlign = TextAlign.Center)
        }
        if (vm.licenceSignedIn && vm.licence.savedKey().isNotBlank()) {
            ResetRow(vm)
        }
        Spacer(Modifier.height(18.dp))
        Text("Alic AI v1.0.0", color = Color(0xFF8AA392), fontSize = 12.sp)
        Text("Dream Big • Create More", color = Color(0xFF8AA392), fontSize = 11.sp)
        Text("Expired or disabled? Renew on the website. Support: t.me/alicaizeta", color = Color(0xFF8AA392), fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color(0xFFB7CDBD), fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
    }
}

@Composable
private fun greenFieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedBorderColor = GreenHi, unfocusedBorderColor = GreenDeep.copy(alpha = 0.8f), cursorColor = GreenHi,
    focusedContainerColor = Color(0x44052210), unfocusedContainerColor = Color(0x33052210),
    focusedLabelColor = GreenHi, unfocusedLabelColor = Color(0xFF9CC3A6),
    focusedTextColor = Color.White, unfocusedTextColor = Color.White
)

/** Animated Telegram support pill at the top right. Opens https://t.me/alicaizeta. */
@Composable
private fun SupportRow() {
    val context = LocalContext.current
    val pulse by rememberInfiniteTransition(label = "support").animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "supportPulse"
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Row(
            Modifier
                .graphicsLayer { scaleX = 0.97f + 0.05f * pulse; scaleY = 0.97f + 0.05f * pulse }
                .clip(RoundedCornerShape(50))
                .background(Color(0x33122E18))
                .border(1.5.dp, GreenHi.copy(alpha = pulse), RoundedCornerShape(50))
                .clickable {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/alicaizeta"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("\uD83C\uDFA7", fontSize = 16.sp)
            Spacer(Modifier.width(6.dp))
            Text("Support", color = GreenHi, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

/** Asks the admin to reset the device binding (for a new phone). */
@Composable
private fun ResetRow(vm: AlicViewModel) {
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        TextButton(enabled = !busy, onClick = {
            busy = true; msg = ""
            vm.licence.requestDeviceReset { m -> msg = m; busy = false }
        }) {
            Text(if (busy) "Sending..." else "Request device reset (new phone)", color = GreenHi, fontSize = 13.sp)
        }
        if (msg.isNotBlank()) Text(msg, color = Color(0xFFB7CDBD), fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}
