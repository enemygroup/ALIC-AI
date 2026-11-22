package com.alic.ai.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** Where users buy or renew a plan. */
const val UPGRADE_URL = "https://alic-ai-zeta.vercel.app"

fun openUpgradeSite(ctx: Context) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UPGRADE_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        // no browser available: nothing else to do
    }
}

/** Settings row that opens the website to upgrade. */
@Composable
fun UpgradeRow() {
    val ctx = LocalContext.current
    ListRow(Icons.Filled.Star, uiBold("Upgrade Plan"), uiBold("Buy or renew on the ALIC AI website"), onClick = { openUpgradeSite(ctx) })
}
