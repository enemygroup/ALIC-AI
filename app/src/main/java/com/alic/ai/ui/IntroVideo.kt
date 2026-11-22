package com.alic.ai.ui

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.alic.ai.R
import kotlinx.coroutines.delay

/** Plays the intro video after the licence is accepted. Tap to skip. Ends by itself. */
@Composable
fun IntroVideo(onDone: () -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        delay(12_000L)   // safety: never block the app for long
        onDone()
    }
    Box(Modifier.fillMaxSize().clickable { onDone() }) {
        AndroidView(
            factory = { c ->
                VideoView(c).apply {
                    setVideoURI(Uri.parse("android.resource://${c.packageName}/${R.raw.alic_intro}"))
                    setOnCompletionListener { onDone() }
                    setOnErrorListener { _, _, _ -> onDone(); true }
                    start()
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
