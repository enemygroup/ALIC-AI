package com.alic.ai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.launch

/** Shown until a valid, active, unexpired licence is verified for the signed-in Firebase account. */
@Composable
fun LicenceScreen(vm: AlicViewModel) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var create by remember { mutableStateOf(false) }
    var key by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Text("ALIC AI", color = C.Text, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = 4.sp)
        Text("Licence required", color = C.Sub, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))
        if (vm.licenceChecking) {
            Text("Checking your licence...", color = C.Sub)
        } else if (!vm.licenceSignedIn) {
            Text(if (create) "Create your account" else "Sign in", color = C.Text, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), colors = fieldColors(), shape = RoundedCornerShape(14.dp))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(pass, { pass = it }, label = { Text("Password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                colors = fieldColors(), shape = RoundedCornerShape(14.dp))
            Spacer(Modifier.height(10.dp))
            RedButton(if (busy) "Please wait..." else if (create) "Create account" else "Sign in", {
                busy = true; err = ""
                scope.launch {
                    val e = vm.licence.signIn(email, pass, create)
                    busy = false
                    if (e != null) err = e else vm.refreshLicence()
                }
            }, Modifier.fillMaxWidth(), enabled = !busy && email.isNotBlank() && pass.length >= 6)
            TextButton(onClick = { create = !create; err = "" }) {
                Text(if (create) "I have an account" else "New here? Create an account", color = C.Neon)
            }
        } else {
            Text("Signed in as " + vm.licence.currentEmail(), color = C.Sub, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            Text("Enter your 35-character licence key", color = C.Text, fontWeight = FontWeight.SemiBold)
            Text("Copy it from the website after your purchase is approved.", color = C.Sub, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(key, { key = it }, label = { Text("Licence key") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), colors = fieldColors(), shape = RoundedCornerShape(14.dp))
            Spacer(Modifier.height(10.dp))
            RedButton(if (busy) "Checking..." else "Activate", {
                busy = true; err = ""
                scope.launch {
                    val r = vm.licence.activate(key)
                    busy = false
                    if (r is com.alic.ai.licence.LicenceResult.Denied) err = r.msg else vm.refreshLicence()
                }
            }, Modifier.fillMaxWidth(), enabled = !busy && key.isNotBlank())
            TextButton(onClick = { vm.licence.signOut(); vm.refreshLicence() }) { Text("Sign out", color = C.Sub) }
        }
        if (err.isNotBlank()) Text(err, color = C.Err, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        if (vm.licenceMsg.isNotBlank() && err.isBlank()) Text(vm.licenceMsg, color = C.Sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }
}
