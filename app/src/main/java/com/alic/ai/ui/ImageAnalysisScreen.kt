package com.alic.ai.ui

import androidx.compose.foundation.background
import androidx.compose.material3.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.alic.ai.vision.Analysis
import com.alic.ai.vision.VisionAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Screenshot, document and gallery analysis: on-device OCR (Hindi + English) and object/scene labels. */
@Composable
fun ImageAnalysisScreen(vm: AlicViewModel, nav: NavController) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var result by remember { mutableStateOf<Analysis?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            result = null; error = null
            scope.launch {
                val b = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 })
                    }
                }
                bitmap = b; preview = b
                if (b == null) error = "Could not open the image."
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Image Analysis", onBack = { nav.popBackStack() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            GhostButton("Choose image or screenshot", { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.fillMaxWidth(), Icons.Filled.Image)
            Spacer(Modifier.height(10.dp))
            preview?.let { p ->
                Image(p.asImageBitmap(), "Selected image", Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Fit)
            }
            Spacer(Modifier.height(10.dp))
            GradientButton(if (busy) "Analysing..." else "Analyse", {
                val b = bitmap
                if (b == null) error = "Choose an image first."
                else {
                    busy = true; error = null
                    scope.launch {
                        val r = withContext(Dispatchers.Default) { runCatching { VisionAnalyzer.analyze(b) }.getOrNull() }
                        result = r
                        if (r == null) error = "Analysis failed on this device."
                        busy = false
                    }
                }
            }, Modifier.fillMaxWidth(), enabled = !busy && bitmap != null)
            error?.let { Text(it, color = C.Err, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
            result?.let { r ->
                Spacer(Modifier.height(12.dp))
                NeoCard(Modifier.fillMaxWidth()) {
                    Text("Objects and scene", color = C.Text, fontWeight = FontWeight.Bold)
                    if (r.labels.isEmpty()) Text("Nothing clear was recognised.", color = C.Sub, fontSize = 13.sp)
                    else r.labels.forEach { Text("${it.name}  ${it.confidence}%", color = C.Sub, fontSize = 13.sp) }
                }
                Spacer(Modifier.height(10.dp))
                NeoCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("Text in image (Hindi + English)", color = C.Text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (r.text.isNotBlank()) IconButton(onClick = { clip.setText(AnnotatedString(r.text)); vm.toast = "Text copied" }) {
                            Icon(Icons.Filled.ContentCopy, "Copy text", tint = C.Neon)
                        }
                    }
                    Text(if (r.text.isBlank()) "No text found." else r.text, color = C.Sub, fontSize = 13.sp)
                }
                Spacer(Modifier.height(10.dp))
                GradientButton("Explain with AI", {
                    val summary = buildString {
                        append("Explain this image for me. ")
                        if (r.labels.isNotEmpty()) append("Objects/scene detected: ").append(r.labels.joinToString { it.name }).append(". ")
                        if (r.text.isNotBlank()) append("Text found in it: ").append(r.text.take(1500))
                    }
                    vm.send(summary)
                    nav.navigate("chat")
                }, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
