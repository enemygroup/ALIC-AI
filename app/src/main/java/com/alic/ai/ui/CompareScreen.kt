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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.alic.ai.vision.CompareResult
import com.alic.ai.vision.ImageComparisonManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Compare two images on the phone. Works offline. */
@Composable
fun CompareScreen(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var first by remember { mutableStateOf<Uri?>(null) }
    var second by remember { mutableStateOf<Uri?>(null) }
    var result by remember { mutableStateOf<CompareResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val pickA = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) { first = uri; result = null } }
    val pickB = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) { second = uri; result = null } }

    fun decode(u: Uri): Bitmap? = ctx.contentResolver.openInputStream(u)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 4 })
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(uiBold("Image Compare"), onBack = { nav.popBackStack() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton(uiBold("Image 1"), { pickA.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.weight(1f), Icons.Filled.Image)
                GhostButton(uiBold("Image 2"), { pickB.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.weight(1f), Icons.Filled.Image)
            }
            Text(uiBold("1: ") + (if (first == null) "not chosen" else "chosen"), color = C.Sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            Text(uiBold("2: ") + (if (second == null) "not chosen" else "chosen"), color = C.Sub, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            GradientButton(if (busy) "Comparing..." else "Compare", {
                val a = first
                val b = second
                if (a == null || b == null) {
                    error = "Choose both images first."
                } else {
                    busy = true; error = null
                    scope.launch {
                        val r = withContext(Dispatchers.Default) {
                            val x = decode(a)
                            val y = decode(b)
                            if (x == null || y == null) null else ImageComparisonManager.compare(x, y)
                        }
                        result = r
                        if (r == null) error = "Could not read one of the images."
                        busy = false
                    }
                }
            }, Modifier.fillMaxWidth(), enabled = !busy)
            error?.let { Text(it, color = C.Err, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
            result?.let { r ->
                Spacer(Modifier.height(12.dp))
                NeoCard(Modifier.fillMaxWidth()) {
                    Text("Similarity: ${r.similarity}%", color = C.Text, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 18.sp)
                    Text("Changed areas: ${r.changed} of ${r.total} points", color = C.Sub, fontSize = 12.sp)
                    Text(
                        when {
                            r.similarity >= 95 -> "The images look almost the same."
                            r.similarity >= 70 -> "The images are similar, with some differences shown in red."
                            else -> "The images are quite different. Red marks the areas that changed."
                        },
                        color = C.Text, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)
                    )
                    Image(
                        r.diff.asImageBitmap(), "Difference map",
                        Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Fit
                    )
                    Text(uiBold("Red = different. Grey = same."), color = C.Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
