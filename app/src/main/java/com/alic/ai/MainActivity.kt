package com.alic.ai

import android.graphics.Color as AColor
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.alic.ai.ui.AlicRoot
import com.alic.ai.ui.AlicTheme
import com.alic.ai.ui.AlicViewModel
import com.alic.ai.ui.C
import java.io.File

class MainActivity : ComponentActivity() {
    private val vm: AlicViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val crash = File(filesDir, "crash.txt")
        if (crash.exists()) {
            showCrash(crash)
            return
        }
        handleIntent(intent)
        setContent {
            AlicTheme {
                Box(Modifier.fillMaxSize().background(C.Bg)) { AlicRoot(vm) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        if (i == null) return
        if (i.getBooleanExtra("alic_setup", false)) vm.toast = "Finish setup: allow the microphone and add your Gemini API key."
        else if (i.getBooleanExtra("alic_live", false) || i.action == Intent.ACTION_ASSIST) vm.requestLive()
    }

    override fun onStart() {
        super.onStart()
        vm.onAppStart(applicationContext)
    }

    override fun onStop() {
        vm.onAppStop(applicationContext)
        super.onStop()
    }

    private fun showCrash(f: File) {
        val trace = try { f.readText() } catch (e: Exception) { "Report unreadable" }
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AColor.BLACK)
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            setTextColor(AColor.WHITE)
            textSize = 16f
            text = "ALIC AI crashed last time.\nScreenshot the red lines below and send them."
        })
        val scroll = ScrollView(this)
        scroll.addView(TextView(this).apply {
            setTextColor(0xFFFF5555.toInt())
            textSize = 11f
            setTextIsSelectable(true)
            text = trace.take(3500)
        })
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(Button(this).apply {
            text = "Delete report and try again"
            setOnClickListener { f.delete(); recreate() }
        })
        setContentView(root)
    }
}
