package com.alic.ai.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alic.ai.data.OrbState
import com.alic.ai.licence.LicenceResult
import kotlinx.coroutines.launch

/** Premium sign-in and licence screen: animated aurora, glowing orb, step tracker and glass cards. */
@Composable
fun LicenceScreen(vm: AlicViewModel) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var create by remember { mutableStateOf(false) }
    var key by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }

    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val step = when {
        vm.licenceChecking -> 0
        !vm.licenceSignedIn -> 1
        else -> 2
    }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground()
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 34.dp)
                .graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 40f },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AlicOrb(OrbState.LIVE, 0f, Modifier.size(150.dp))
            Spacer(Modifier.height(8.dp))
            Text("ALIC AI", color = C.Text, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = 6.sp)
            Text("THE POWER OF INTELLIGENCE", color = C.Neon, fontSize = 10.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(20.dp))

            StepTracker(step)
            Spacer(Modifier.height(18.dp))

            NeoCard(Modifier.fillMaxWidth()) {
                when (step) {
                    0 -> {
                        Text("Checking your licence...", color = C.Sub, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                    1 -> {
                        Text(if (create) "Create your account" else "Welcome back", color = C.Text, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        Text("Use the same email you used on the website.", color = C.Sub, fontSize = 12.sp)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(), colors = fieldColors(), shape = RoundedCornerShape(14.dp))
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(pass, { pass = it }, label = { Text("Password (min 6)") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                            colors = fieldColors(), shape = RoundedCornerShape(14.dp))
                        Spacer(Modifier.height(14.dp))
                        GradientButton(
                            if (busy) "Please wait..." else if (create) "Create account" else "Sign in",
                            {
                                busy = true; err = ""
                                scope.launch {
                                    val e = vm.licence.signIn(email, pass, create)
                                    busy = false
                                    if (e != null) err = e else vm.refreshLicence()
                                }
                            },
                            Modifier.fillMaxWidth(),
                            enabled = !busy && email.isNotBlank() && pass.length >= 6
                        )
                        TextButton(onClick = { create = !create; err = "" }) {
                            Text(if (create) "I already have an account" else "New here? Create an account", color = C.Neon, fontSize = 13.sp)
                        }
                    }
                    else -> {
                        Text("Signed in as", color = C.Sub, fontSize = 12.sp)
                        Text(vm.licence.currentEmail(), color = C.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(Modifier.height(14.dp))
                        Text("Enter your licence key", color = C.Text, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        Text("Copy the 35-character key from the website after your purchase is approved.", color = C.Sub, fontSize = 12.sp)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(key, { key = it }, label = { Text("Licence key") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(), colors = fieldColors(), shape = RoundedCornerShape(14.dp))
                        Spacer(Modifier.height(14.dp))
                        GradientButton(
                            if (busy) "Checking..." else "Activate ALIC",
                            {
                                busy = true; err = ""
                                scope.launch {
                                    val r = vm.licence.activate(key)
                                    busy = false
                                    if (r is LicenceResult.Denied) err = r.msg else vm.refreshLicence()
                                }
                            },
                            Modifier.fillMaxWidth(),
                            enabled = !busy && key.isNotBlank()
                        )
                        TextButton(onClick = { vm.licence.signOut(); vm.refreshLicence() }) {
                            Text("Sign out", color = C.Sub, fontSize = 13.sp)
                        }
                    }
                }
            }

            if (err.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(C.Err.copy(alpha = 0.12f))
                        .border(1.dp, C.Err.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) { Text(err, color = C.Err, fontSize = 13.sp) }
            }
            if (vm.licenceMsg.isNotBlank() && err.isBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(vm.licenceMsg, color = C.Sub, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Three glowing steps: Sign in, Licence, Ready. The active step pulses. */
@Composable
private fun StepTracker(step: Int) {
    val pulse by rememberInfiniteTransition(label = "step").animateFloat(
        0.6f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "stepPulse"
    )
    val labels = listOf("Sign in", "Licence", "Ready")
    Row(verticalAlignment = Alignment.CenterVertically) {
        labels.forEachIndexed { i, label ->
            val done = step > i
            val active = step == i || (step == 0 && i == 0)
            val col = when {
                done -> Color(0xFF00E676)
                active -> C.Neon.copy(alpha = if (active) pulse else 1f)
                else -> C.Muted
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(84.dp)) {
                Box(
                    Modifier.size(28.dp).clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Brush.radialGradient(listOf(col, col.copy(alpha = 0.2f))))
                        .border(1.dp, col, androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (done) "✓" else "${i + 1}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Text(label, color = if (done || active) C.Text else C.Muted, fontSize = 11.sp)
            }
            if (i < labels.size - 1) {
                Box(Modifier.width(26.dp).height(2.dp).background(if (step > i) Color(0xFF00E676) else C.Border))
            }
        }
    }
}
