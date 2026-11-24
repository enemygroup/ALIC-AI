package com.alic.ai.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.alic.ai.licence.LicenceResult
import com.alic.ai.licence.PlanTier
import com.alic.ai.service.NotificationStore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val OkGreen = Color(0xFF39FF7A)
private val WarnOrange = Color(0xFFFF9A3D)
private val BadRed = Color(0xFFFF5252)
private val Grey = Color(0xFF8AA392)

/** Licence state from the saved licence and the real expiry timestamp. */
private data class LicenceInfo(
    val status: String, val color: Color, val plan: String, val tier: PlanTier,
    val expiry: Long, val daysLeft: Long, val key: String
)

private fun licenceInfo(vm: AlicViewModel): LicenceInfo {
    val key = vm.licence.savedKey()
    val exp = vm.licence.savedExpiry()
    val plan = vm.licence.savedPlan()
    val tier = PlanTier.of(plan)
    val now = System.currentTimeMillis()
    val left = if (exp == 0L) -1L else ((exp - now) + 86_399_999L) / 86_400_000L
    val (status, color) = when {
        key.isBlank() -> "Not Activated" to Grey
        exp == 0L -> "Active" to OkGreen
        now > exp -> "Expired" to BadRed
        left <= 7 -> "Expiring Soon" to WarnOrange
        else -> "Active" to OkGreen
    }
    return LicenceInfo(status, color, plan.ifBlank { "Not set" }, tier, exp, left, key)
}

private fun fmtDate(ms: Long): String =
    if (ms == 0L) "Lifetime" else SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(ms))

private fun openLink(ctx: Context, url: String) {
    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {}
}

/** Plan summary card shown at the top of the Profile page. Uses the real licence data. */
@Composable
fun PlanSummaryCard(vm: AlicViewModel, nav: NavController) {
    val info = licenceInfo(vm)
    NeoCard(Modifier.fillMaxWidth().padding(bottom = 14.dp), onClick = { nav.navigate("myplan") }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (info.tier == PlanTier.LIFETIME) "👑" else "⭐", fontSize = 22.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(uiBold("ALIC AI PREMIUM"), color = C.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Plan: ${info.plan}", color = C.Sub, fontSize = 12.sp)
            }
            Text(info.status, color = info.color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text("Expires: ${fmtDate(info.expiry)}", color = C.Sub, fontSize = 12.sp)
        Text(
            if (info.daysLeft < 0 && info.key.isNotBlank()) "Remaining: lifetime"
            else if (info.key.isBlank()) "Activate a license to see remaining days"
            else "Remaining: ${maxOf(info.daysLeft, 0)} days",
            color = C.Sub, fontSize = 12.sp
        )
    }
}

/** Full license page: masked key, status, expiry, remaining days, progress, device, activation and upgrade. */
@Composable
fun MyPlanScreen(vm: AlicViewModel, nav: NavController) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val info = licenceInfo(vm)
    var showKey by remember { mutableStateOf(false) }
    var newKey by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    val masked = if (info.key.length >= 8) info.key.take(4) + "-••••••••-" + info.key.takeLast(4) else "Not activated"
    val total = if (info.expiry == 0L || info.key.isBlank()) 1f else 30f
    val progress = if (info.daysLeft < 0) 1f else (info.daysLeft.toFloat() / total).coerceIn(0f, 1f)

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(uiBold("My License"), onBack = { nav.popBackStack() }) {}
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            NeoCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(info.plan, color = C.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    Text(info.status, color = info.color, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                // Remaining-time bar
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x33FFFFFF))) {
                    Box(Modifier.fillMaxWidth(progress).height(8.dp).clip(RoundedCornerShape(4.dp)).background(info.color))
                }
                Spacer(Modifier.height(10.dp))
                Row { Text(uiBold("License code:  "), color = C.Sub, fontSize = 13.sp); Text(if (showKey) info.key.ifBlank { "—" } else masked, color = C.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showKey = !showKey }) {
                        Icon(if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null, tint = C.Neon)
                        Text(if (showKey) " Hide" else " Show", color = C.Neon, fontSize = 13.sp)
                    }
                    if (info.key.isNotBlank()) TextButton(onClick = {
                        clip.setText(AnnotatedString(info.key)); msg = "License code copied."
                    }) {
                        Icon(Icons.Filled.ContentCopy, null, tint = C.Neon)
                        Text(uiBold(" Copy"), color = C.Neon, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Activated on: ${Build.MANUFACTURER} ${Build.MODEL}", color = C.Sub, fontSize = 12.sp)
                Text("Expiry: ${fmtDate(info.expiry)}", color = C.Sub, fontSize = 12.sp)
                Text(
                    if (info.daysLeft < 0) "Remaining: lifetime" else "Remaining: ${maxOf(info.daysLeft, 0)} days",
                    color = C.Sub, fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(12.dp))
            RedButton(uiBold("Renew / Upgrade on website"), { openLink(ctx, UPGRADE_URL) }, Modifier.fillMaxWidth())

            Spacer(Modifier.height(16.dp))
            SectionTitle(uiBold("ENTER NEW LICENSE CODE"))
            NeoCard(Modifier.fillMaxWidth()) {
                OutlinedTextField(newKey, { newKey = it.uppercase().filter { c -> c.isLetterOrDigit() } },
                    label = { Text(uiBold("35-character code")) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), colors = fieldColors(), shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.height(8.dp))
                RedButton(if (busy) "Checking..." else "Activate License", {
                    busy = true; msg = ""
                    scope.launch {
                        val r = vm.licence.activate(newKey)
                        busy = false
                        msg = when (r) {
                            is LicenceResult.Denied -> r.msg
                            else -> { newKey = ""; vm.refreshLicence(); "License activated." }
                        }
                    }
                }, Modifier.fillMaxWidth())
            }
            if (msg.isNotBlank()) Text(msg, color = C.Sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))

            Spacer(Modifier.height(16.dp))
            SectionTitle(uiBold("DEVICE"))
            NeoCard(Modifier.fillMaxWidth()) {
                Text("Android ${Build.VERSION.RELEASE} • ${Build.MODEL}", color = C.Text, fontSize = 13.sp)
                Text(uiBold("A license works on one phone. Use Settings → Help & Support to request a device reset."), color = C.Sub, fontSize = 12.sp)
            }
        }
    }
}

/** A single plan card. The button opens the website (the only place to buy). */
@Composable
private fun PlanCardPremium(title: String, subtitle: String, price: String, unit: String, features: List<String>, button: String, highlight: Boolean, onClick: () -> Unit) {
    val gold = Color(0xFFFFD54A)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(Color(0xCC061A10))
            .border(if (highlight) 2.dp else 1.dp, if (highlight) gold else C.Neon.copy(alpha = 0.6f), RoundedCornerShape(22.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (highlight) {
            Text(uiBold("Most Popular"), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(gold).padding(horizontal = 12.dp, vertical = 4.dp))
            Spacer(Modifier.height(6.dp))
        }
        Text(uiBold(title), color = C.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(subtitle, color = C.Sub, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(price, color = if (highlight) gold else C.Text, fontWeight = FontWeight.Black, fontSize = 32.sp)
            Spacer(Modifier.width(4.dp))
            Text(unit, color = C.Sub, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
        }
        features.forEach { f -> Text("✓  $f", color = C.Text, fontSize = 13.sp, modifier = Modifier.fillMaxWidth()) }
        Spacer(Modifier.height(10.dp))
        RedButton(uiBold(button), onClick, Modifier.fillMaxWidth())
    }
}

/** Premium page: banner, benefits, three plans (prices from the website), current membership and buttons. */
@Composable
fun PremiumScreen(vm: AlicViewModel, nav: NavController) {
    val ctx = LocalContext.current
    val info = licenceInfo(vm)
    var prices by remember { mutableStateOf<Triple<Long, Long, Long>?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        prices = vm.licence.subscriptionPrices()
        loaded = true
    }
    val monthly = prices?.first ?: 99L
    val yearly = prices?.second ?: 499L
    val lifetime = prices?.third ?: 1999L
    val gold = Color(0xFFFFD54A)
    val pulse by rememberInfiniteTransition(label = "crown").animateFloat(
        0.92f, 1.08f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "crownPulse"
    )

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Premium", onBack = { nav.popBackStack() }) {}
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Banner
            Text("👑", fontSize = 52.sp, modifier = Modifier.graphicsLayer { scaleX = pulse; scaleY = pulse })
            Text(uiBold("ALIC AI PREMIUM"), color = gold, fontWeight = FontWeight.Black, fontSize = 24.sp, textAlign = TextAlign.Center)
            Text(uiBold("Unlock the full power of ALIC AI"), color = C.Text, fontSize = 14.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(14.dp))

            // Benefits
            NeoCard(Modifier.fillMaxWidth()) {
                listOf(
                    "⚡  AI chats within your plan",
                    "🧠  Advanced AI models",
                    "🚫  No ads experience",
                    "🛟  Priority support",
                    "🎨  Exclusive themes",
                    "🛠  More AI tools"
                ).forEach { Text(it, color = C.Text, fontSize = 14.sp, modifier = Modifier.padding(vertical = 3.dp)) }
            }

            // Plans
            Spacer(Modifier.height(16.dp))
            PlanCardPremium("Monthly Plan", "Perfect for short-term use", "₹$monthly", "/month",
                listOf("Full Premium Access", "All AI Tools"), "Choose Plan", false) { openLink(ctx, UPGRADE_URL) }
            Spacer(Modifier.height(12.dp))
            PlanCardPremium("Yearly Plan", "Best value for you", "₹$yearly", "/year",
                listOf("Full Premium Access", "All AI Tools", "Priority Support"), "Upgrade Now", true) { openLink(ctx, UPGRADE_URL) }
            Spacer(Modifier.height(12.dp))
            PlanCardPremium("Lifetime Plan", "One-time payment", "₹$lifetime", "one-time",
                listOf("Lifetime Access", "All Premium Features", "VIP Support"), "Buy Now", false) { openLink(ctx, UPGRADE_URL) }
            if (loaded && prices == null) {
                Text("Showing default prices. The server price could not be loaded.", color = C.Muted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            }

            // Current membership
            Spacer(Modifier.height(16.dp))
            SectionTitle("CURRENT MEMBERSHIP")
            NeoCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(uiBold("Current plan"), color = C.Sub, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(info.plan, color = gold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(uiBold("License status"), color = C.Sub, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(info.status, color = info.color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(uiBold("Expires on"), color = C.Sub, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(fmtDate(info.expiry), color = C.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(uiBold("Remaining"), color = C.Sub, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(
                        if (info.daysLeft < 0 && info.key.isNotBlank()) "Lifetime"
                        else if (info.key.isBlank()) "Not activated"
                        else "${maxOf(info.daysLeft, 0)} days",
                        color = C.Neon, fontWeight = FontWeight.Bold, fontSize = 14.sp
                    )
                }
                Spacer(Modifier.height(10.dp))
                RedButton(uiBold("Renew Subscription"), { openLink(ctx, UPGRADE_URL) }, Modifier.fillMaxWidth())
            }

            // Buttons
            Spacer(Modifier.height(14.dp))
            GhostButton(uiBold("Payment History"), { openLink(ctx, UPGRADE_URL) }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            GhostButton(uiBold("My License"), { nav.navigate("myplan") }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            GhostButton(uiBold("Help & Support"), { nav.navigate("help") }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Text(
                "Prices and payment happen on the ALIC AI website. Premium activates only after your license is verified, never from a button press.",
                color = C.Muted, fontSize = 11.sp, textAlign = TextAlign.Center
            )
        }
    }
}

/** Notifications received on this phone (needs Media control and notifications access). */
@Composable
fun NotificationCenterScreen(nav: NavController) {
    val items = remember { NotificationStore.latest(30) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(uiBold("Notifications"), onBack = { nav.popBackStack() }) {}
        if (items.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(uiBold("No notifications yet."), color = C.Text, fontWeight = FontWeight.SemiBold)
                Text(uiBold("Turn on Media control and notifications in Permissions to see your phone notifications here."), color = C.Sub, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items) { n ->
                    NeoCard(Modifier.fillMaxWidth()) {
                        Text(n.pkg, color = C.Sub, fontSize = 11.sp)
                        Text(n.title.ifBlank { "Notification" }, color = C.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        if (n.text.isNotBlank()) Text(n.text, color = C.Sub, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/** Help, support on Telegram, and a short FAQ. */
@Composable
fun HelpSupportScreen(nav: NavController) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf("") }
    val faq = listOf(
        "How do I activate my license?" to "Log in, then enter the 35-character code you got after purchase on the license screen.",
        "Why does my license work only on one phone?" to "Each license is tied to one phone. Request a device reset from My License if you change phones.",
        "When does my license expire?" to "The expiry date is shown on My License. Renew on the website before it ends.",
        "Voice commands do not work." to "Check Permissions: microphone, accessibility and media control must be on."
    )
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(uiBold("Help & Support"), onBack = { nav.popBackStack() }) {}
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            SectionTitle(uiBold("CONTACT"))
            NeoCard(Modifier.fillMaxWidth().padding(bottom = 8.dp), onClick = { openLink(ctx, "https://t.me/alicaizeta") }) {
                Text(uiBold("Contact support on Telegram"), color = C.Text, fontWeight = FontWeight.SemiBold)
                Text("@alicaizeta", color = C.Sub, fontSize = 12.sp)
            }
            NeoCard(Modifier.fillMaxWidth().padding(bottom = 8.dp), onClick = { openLink(ctx, "https://t.me/alicaizeta") }) {
                Text(uiBold("Report a problem"), color = C.Text, fontWeight = FontWeight.SemiBold)
                Text(uiBold("Send a screenshot and what you were doing"), color = C.Sub, fontSize = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
            SectionTitle(uiBold("FAQ"))
            faq.forEach { (q, a) ->
                NeoCard(Modifier.fillMaxWidth().padding(bottom = 8.dp), onClick = { open = if (open == q) "" else q }) {
                    Text(q, color = C.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    if (open == q) Text(a, color = C.Sub, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(uiBold("ALIC AI v1.0.0 • Developer: Rohit"), color = C.Muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}
