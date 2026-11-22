package com.alic.ai.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alic.ai.data.OrbState
import com.alic.ai.licence.LicenceResult
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val GreenHi = Color(0xFF39FF7A)
private val GreenMid = Color(0xFF00D95A)
private val GreenDeep = Color(0xFF0A7A2E)
private val GreenGlass = Color(0x6612FF5A)
private val BgTop = Color(0xFF021A09)

/** Turns a backend or Firebase message into a clear popup title and text. */
private fun errorPopup(msg: String, retry: () -> Unit): PopupSpec {
    val m = msg.lowercase()
    return when {
        m.contains("expired") -> PopupSpec(PopupKind.ERROR, "License Expired", msg, buttonText = "OK", onContinue = retry)
        m.contains("does not exist") || m.contains("35 characters") || m.contains("not activated") ->
            PopupSpec(PopupKind.ERROR, "Invalid License Code", msg, buttonText = "Try Again", onContinue = retry)
        m.contains("another phone") || m.contains("another account") ->
            PopupSpec(PopupKind.ERROR, "Already In Use", msg, buttonText = "OK", onContinue = retry)
        m.contains("disabled") ->
            PopupSpec(PopupKind.ERROR, "License Disabled", msg, buttonText = "OK", onContinue = retry)
        m.contains("could not verify") || m.contains("network") || m.contains("internet") || m.contains("unable to resolve") ->
            PopupSpec(PopupKind.ERROR, "Network Error", "Check your internet connection and try again.", buttonText = "Retry", onContinue = retry)
        m.contains("password") || m.contains("email") || m.contains("credential") || m.contains("no user") || m.contains("user record") ->
            PopupSpec(PopupKind.ERROR, "Login Failed", "Invalid email or password.", buttonText = "Try Again", onContinue = retry)
        m.contains("already") && m.contains("use") ->
            PopupSpec(PopupKind.ERROR, "Account Error", "This email is already registered. Please log in.", buttonText = "OK", onContinue = retry)
        else -> PopupSpec(PopupKind.ERROR, "Something Went Wrong", msg, buttonText = "OK", onContinue = retry)
    }
}

/** Dark green glass background with a soft pulsing glow. */
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
    val glow by rememberInfiniteTransition(label = "btn").animateFloat(
        0.6f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "btnGlow"
    )
    Box(
        Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(27.dp))
            .background(
                if (enabled) Brush.horizontalGradient(listOf(GreenDeep, GreenMid, GreenHi.copy(alpha = glow), GreenMid, GreenDeep))
                else Brush.horizontalGradient(listOf(Color(0xFF1A3A22), Color(0xFF1A3A22)))
            )
            .border(1.dp, GreenHi.copy(alpha = if (enabled) 0.9f else 0.2f), RoundedCornerShape(27.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (enabled) Color.Black else Color(0xFF6B8A72), fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

@Composable
private fun SmallChip(text: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(Color(0x3312FF5A))
            .border(1.dp, GreenHi.copy(alpha = 0.6f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) { Text(text, color = GreenHi, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
}

/** Big "A" logo with a rotating neon ring and a soft outer glow. */
@Composable
private fun NeonLogo() {
    val spin by rememberInfiniteTransition(label = "ring").animateFloat(
        0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "spin"
    )
    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxSize().graphicsLayer { rotationZ = spin }
                .border(2.dp, Brush.sweepGradient(listOf(GreenHi, Color.Transparent, GreenMid, Color.Transparent, GreenHi)), CircleShape)
        )
        LogoMark(104.dp)
    }
}

/** Three steps: Sign in, Activate, Ready. */
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

@Composable
fun LicenceScreen(vm: AlicViewModel) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val upgradeCtx = LocalContext.current
    var mode by remember { mutableStateOf(0) }            // 0 = login, 1 = sign up
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var remember by remember { mutableStateOf(true) }
    var terms by remember { mutableStateOf(false) }
    var key by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf("") }
    var popup by remember { mutableStateOf<PopupSpec?>(null) }
    val step = when { vm.licenceChecking -> 0; !vm.licenceSignedIn -> 0; else -> 1 }

    val googleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        busy = true
        scope.launch {
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(res.data).await()
                val e = vm.licence.signInWithGoogleAccount(account)
                busy = false
                if (e != null) popup = errorPopup(e) { popup = null }
                else popup = PopupSpec(PopupKind.SUCCESS, "Welcome Back!", "Successfully logged in to ALIC AI.",
                    listOf("User" to vm.licence.displayName(), "Account" to "Verified"), "Continue") {
                        popup = null; vm.refreshLicence()
                    }
            } catch (ex: Exception) {
                busy = false
                popup = errorPopup(ex.message ?: "Google sign-in was cancelled.") { popup = null }
            }
        }
    }

    GreenScreen {
        SupportRow()
        NeonLogo()
        Text(uiBold("ALIC AI"), color = Color.White, fontWeight = FontWeight.Black, fontSize = 38.sp)
        Text(uiBold("Think  •  Ask  •  Explore"), color = GreenHi, fontSize = 13.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(18.dp))

        if (vm.licenceChecking) {
            GlassPanel { Text(uiBold("Checking your licence..."), color = GreenHi, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        } else if (!vm.licenceSignedIn) {
            if (mode == 0) {
                // ---------- LOGIN ----------
                GlassPanel {
                    Text(uiBold("Welcome Back!"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Text(uiBold("Login to continue your journey"), color = Color(0xFFB7CDBD), fontSize = 13.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(email, { email = it }, label = { Text(uiBold("Email Address")) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), colors = greenFieldColors(), shape = RoundedCornerShape(16.dp))
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(pass, { pass = it }, label = { Text(uiBold("Password")) }, singleLine = true,
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { TextButton(onClick = { showPass = !showPass }) { Text(if (showPass) "Hide" else "Show", color = GreenHi, fontSize = 12.sp) } },
                        modifier = Modifier.fillMaxWidth(), colors = greenFieldColors(), shape = RoundedCornerShape(16.dp))
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.clickable { remember = !remember }, verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(20.dp).clip(RoundedCornerShape(5.dp))
                                    .background(if (remember) GreenMid else Color.Transparent)
                                    .border(1.5.dp, GreenHi, RoundedCornerShape(5.dp)),
                                contentAlignment = Alignment.Center
                            ) { if (remember) Text(uiBold("✓"), color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.width(8.dp))
                            Text(uiBold("Remember Me"), color = Color.White, fontSize = 13.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = {
                            if (email.isBlank()) { popup = PopupSpec(PopupKind.ERROR, "Enter Your Email", "Type your email first, then tap Forgot Password.", buttonText = "OK") { popup = null } }
                            else {
                                busy = true
                                scope.launch {
                                    val e = vm.licence.resetPassword(email)
                                    busy = false
                                    info = if (e != null) e else "Reset link sent to $email."
                                }
                            }
                        }) { Text(uiBold("Forgot Password?"), color = GreenHi, fontSize = 12.sp) }
                    }
                    Spacer(Modifier.height(12.dp))
                    GreenButton(if (busy) "Please wait..." else "Login", !busy && email.isNotBlank() && pass.length >= 6) {
                        busy = true; info = ""
                        scope.launch {
                            val e = vm.licence.signIn(email, pass, false)
                            busy = false
                            if (e != null) popup = errorPopup(e) { popup = null }
                            else popup = PopupSpec(PopupKind.SUCCESS, "Welcome Back!", "Successfully logged in to ALIC AI.",
                                listOf("User" to vm.licence.displayName(), "Account" to "Verified"), "Continue") {
                                    popup = null; vm.refreshLicence()
                                }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    GreenButton(uiBold("Continue with Google"), !busy) {
                        val client = vm.licence.googleClient()
                        if (client == null) popup = PopupSpec(PopupKind.ERROR, "Google Sign-In", "Google sign-in is not set up in this build yet.", buttonText = "OK") { popup = null }
                        else googleLauncher.launch(client.signInIntent)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        Text(uiBold("Don't have an account?  "), color = Color(0xFFB7CDBD), fontSize = 13.sp)
                        Text(uiBold("Sign Up"), color = GreenHi, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { mode = 1; info = "" })
                    }
                }
            } else {
                // ---------- SIGN UP ----------
                GlassPanel {
                    Text(uiBold("Create Your Account"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Text(uiBold("Join the AI Revolution"), color = Color(0xFFB7CDBD), fontSize = 13.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(name, { name = it }, label = { Text(uiBold("Full Name")) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), colors = greenFieldColors(), shape = RoundedCornerShape(16.dp))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(email, { email = it }, label = { Text(uiBold("Email Address")) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), colors = greenFieldColors(), shape = RoundedCornerShape(16.dp))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(pass, { pass = it }, label = { Text(uiBold("Password (min 6)")) }, singleLine = true,
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { TextButton(onClick = { showPass = !showPass }) { Text(if (showPass) "Hide" else "Show", color = GreenHi, fontSize = 12.sp) } },
                        modifier = Modifier.fillMaxWidth(), colors = greenFieldColors(), shape = RoundedCornerShape(16.dp))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(confirm, { confirm = it }, label = { Text(uiBold("Confirm Password")) }, singleLine = true,
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(), colors = greenFieldColors(), shape = RoundedCornerShape(16.dp))
                    if (confirm.isNotBlank() && confirm != pass) {
                        Text(uiBold("Passwords do not match"), color = Color(0xFFFF9A3D), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.clickable { terms = !terms }, verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(20.dp).clip(RoundedCornerShape(5.dp))
                                .background(if (terms) GreenMid else Color.Transparent)
                                .border(1.5.dp, GreenHi, RoundedCornerShape(5.dp)),
                            contentAlignment = Alignment.Center
                        ) { if (terms) Text(uiBold("✓"), color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(8.dp))
                        Text(uiBold("I agree to the Terms & Conditions and Privacy Policy"), color = Color(0xFFD7E8DB), fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    GreenButton(if (busy) "Please wait..." else "Create Account",
                        !busy && name.isNotBlank() && email.isNotBlank() && pass.length >= 6 && pass == confirm && terms) {
                        busy = true; info = ""
                        scope.launch {
                            val e = vm.licence.signIn(email, pass, true, name)
                            busy = false
                            if (e != null) popup = errorPopup(e) { popup = null }
                            else popup = PopupSpec(PopupKind.SUCCESS, "Account Created Successfully!", "Welcome to ALIC AI!",
                                listOf("Name" to name.trim(), "Status" to "Active"), "Continue") {
                                    popup = null; vm.refreshLicence()
                                }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        Text(uiBold("Already have an account?  "), color = Color(0xFFB7CDBD), fontSize = 13.sp)
                        Text(uiBold("Login"), color = GreenHi, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { mode = 0; info = "" })
                    }
                }
            }
        } else {
            // ---------- LICENCE ACTIVATION ----------
            Text(uiBold("👑"), fontSize = 30.sp)
            Text(uiBold("License Activation"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text(uiBold("Enter your license code to unlock premium features"), color = Color(0xFFB7CDBD), fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            GlassPanel {
                OutlinedTextField(key, { key = it.uppercase().filter { c -> c.isLetterOrDigit() } }, label = { Text(uiBold("Enter License Code")) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), colors = greenFieldColors(), shape = RoundedCornerShape(16.dp))
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmallChip(uiBold("Paste")) {
                        val txt = clipboard.getText()?.text.orEmpty()
                        key = txt.uppercase().filter { c -> c.isLetterOrDigit() }
                    }
                    SmallChip(uiBold("Clear")) { key = "" }
                }
                Spacer(Modifier.height(14.dp))
                GreenButton(if (busy) "Checking..." else "Activate License", !busy && key.isNotBlank()) {
                    busy = true; info = ""
                    scope.launch {
                        val r = vm.licence.activate(key)
                        busy = false
                        when (r) {
                            is LicenceResult.Denied -> popup = errorPopup(r.msg) { popup = null }
                            is LicenceResult.Ok, is LicenceResult.Offline -> {
                                val exp = vm.licence.savedExpiry()
                                popup = PopupSpec(PopupKind.SUCCESS, "License Activated Successfully!", "Welcome to ALIC AI Premium!",
                                    listOf(
                                        "License" to "Active",
                                        "Plan" to vm.licence.savedPlan().ifBlank { "Premium" },
                                        "Valid Until" to (if (exp == 0L) "Lifetime" else SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(exp)))
                                    ), "Continue", crown = true) {
                                        popup = null; vm.refreshLicence()
                                    }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    Text(uiBold("OR"), color = Color(0xFF8AA392), fontSize = 12.sp)
                }
                Spacer(Modifier.height(10.dp))
                GreenButton(uiBold("Verify License"), !busy) {
                    busy = true; info = ""
                    scope.launch {
                        val r = vm.licence.verifyStored()
                        busy = false
                        when (r) {
                            is LicenceResult.Denied -> popup = errorPopup(r.msg) { popup = null }
                            else -> {
                                val exp = vm.licence.savedExpiry()
                                popup = PopupSpec(PopupKind.SUCCESS, "License Verified!", "Your license is valid on this account.",
                                    listOf(
                                        "License" to "Active",
                                        "Plan" to vm.licence.savedPlan().ifBlank { "Premium" },
                                        "Valid Until" to (if (exp == 0L) "Lifetime" else SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(exp)))
                                    ), "Continue", crown = true) {
                                        popup = null; vm.refreshLicence()
                                    }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            GreenButton(uiBold("Upgrade / Renew Plan"), true) { openUpgradeSite(upgradeCtx) }
            Spacer(Modifier.height(14.dp))
            GlassPanel {
                val exp = vm.licence.savedExpiry()
                val saved = vm.licence.savedKey()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(uiBold("License Status"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    Box(Modifier.clip(RoundedCornerShape(50)).background(GreenGlass).border(1.dp, GreenHi, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Text(if (saved.isBlank()) "Not active" else "Active", color = GreenHi, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                StatusRow("Plan", vm.licence.savedPlan().ifBlank { "Not set" })
                StatusRow("Expiry Date", if (exp == 0L) "Lifetime / not set" else SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(exp)))
                StatusRow("Device", "Android ${Build.VERSION.RELEASE} • ${Build.MODEL}")
                StatusRow("Signed in as", vm.licence.currentEmail())
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { vm.licence.signOut(); vm.refreshLicence() }) { Text(uiBold("Sign out"), color = Color(0xFFB7CDBD), fontSize = 13.sp) }
            }
        }

        if (vm.licenceChecking) Unit
        if (vm.licenceSignedIn && vm.licence.savedKey().isNotBlank()) {
            ResetRow(vm)
        }
        Spacer(Modifier.height(12.dp))
        StepPills(step)
        if (info.isNotBlank()) { Spacer(Modifier.height(10.dp)); Text(info, color = GreenHi, fontSize = 13.sp, textAlign = TextAlign.Center) }
        Spacer(Modifier.height(18.dp))
        Text(uiBold("Alic AI v1.0.0"), color = Color(0xFF8AA392), fontSize = 12.sp)
        Text(uiBold("Dream Big • Create More"), color = Color(0xFF8AA392), fontSize = 11.sp)
        Text("Expired or disabled? Renew on the website. Support: t.me/alicaizeta", color = Color(0xFF8AA392), fontSize = 11.sp, textAlign = TextAlign.Center)
        PremiumPopup(popup)
    }
}

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
            Text(uiBold("Support"), color = GreenHi, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
