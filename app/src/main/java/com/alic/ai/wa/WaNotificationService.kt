package com.alic.ai.wa

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.app.RemoteInput
import com.alic.ai.call.CallMonitor
import com.alic.ai.data.SettingsStore
import com.alic.ai.voice.VoiceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Reads WhatsApp notifications (only when the user turned automation on) and handles one-to-one chats.
 * Group chats and messages with links are never auto-replied. Replies go through the notification's own
 * Reply action (RemoteInput), so ALIC never opens WhatsApp or types on the screen.
 * Limits: this runs only while Android keeps the listener connected; Force Stop stops it until the app is opened again.
 */
class WaNotificationService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val main = Handler(Looper.getMainLooper())
    private val seen = LinkedHashSet<String>()
    private val replyActions = HashMap<String, Pair<Notification.Action, Long>>()
    private val locks = HashMap<String, Mutex>()
    private val sentCount = HashMap<String, Int>()
    private lateinit var store: WaStore
    private lateinit var voice: VoiceEngine

    override fun onCreate() {
        super.onCreate()
        store = WaStore(applicationContext)
        voice = VoiceEngine(applicationContext).also { it.init() }
    }

    override fun onListenerConnected() {
        WaLog.status.value = "Waiting for notification"
        WaLog.log("Listener connected")
    }

    override fun onListenerDisconnected() {
        WaLog.status.value = "Stopped"
        WaLog.log("Listener disconnected. Asking Android to reconnect")
        requestRebind(ComponentName(this, WaNotificationService::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        if (n.packageName != "com.whatsapp" && n.packageName != "com.whatsapp.w4b") return
        if (!store.enabled) return
        val extras = n.notification.extras
        val group = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        if (title.isBlank()) return
        // Newest message from the other person (MessagingStyle entries with a sender)
        val parcels = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        val last = parcels?.mapNotNull { it as? Bundle }?.lastOrNull { !it.getCharSequence("sender").isNullOrBlank() }
        val text = (last?.getCharSequence("text") ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.trim().orEmpty()
        val time = last?.getLong("time") ?: n.postTime
        if (text.isBlank()) return
        val key = "$title|$text|$time"
        synchronized(seen) {
            if (!seen.add(key)) return          // already processed: never reply twice
            if (seen.size > 500) seen.remove(seen.first())
        }
        val action = n.notification.actions?.firstOrNull { it.remoteInputs?.isNotEmpty() == true }
        if (action != null) synchronized(replyActions) { replyActions[title] = action to System.currentTimeMillis() }
        else WaLog.log("No inline Reply on this notification from $title (auto reply needs it)")
        WaLog.log("Message from $title")
        if (store.readMessages) speak("$title ka message aaya hai. $text")
        scope.launch { process(title, text, group, action) }
    }

    private suspend fun process(contact: String, text: String, group: Boolean, action: Notification.Action?) {
        val mutex = synchronized(locks) { locks.getOrPut(contact.lowercase()) { Mutex() } }
        mutex.withLock {
            store.addHistory(contact, "in", text)
            WaLog.status.value = "Processing message"
            val rule = store.matchRule(contact, text)
            val reply: String? = when {
                rule != null -> rule.reply
                text.contains("http", ignoreCase = true) -> { WaLog.log("Link message from $contact: not auto-replied"); null }
                else -> { WaLog.status.value = "Generating reply"; WaEngine.generate(applicationContext, store, contact, text) }
            }
            if (reply == null) { WaLog.status.value = "Waiting for notification"; return }
            if (!store.autoReply) {
                WaLog.log("Reply ready for $contact (auto reply is OFF): $reply")
                WaLog.status.value = "Waiting for notification"
                return
            }
            if (group) { WaLog.log("Group chat: no auto reply"); return }
            val count = (sentCount[contact] ?: 0) + 1
            if (count > store.maxPerContact) { WaLog.log("Limit reached for $contact"); return }
            delay(store.delaySec * 1000L)
            val sent = sendReply(contact, reply, action)
            if (sent) {
                sentCount[contact] = count
                store.addHistory(contact, "out", reply)
                WaLog.status.value = "Reply sent"
                WaLog.log("Reply sent to $contact")
                if (store.voice) speak("$contact ko reply bhej diya hai.")
            } else {
                WaLog.status.value = "Reply failed"
                WaLog.log("Reply failed for $contact (no usable Reply action)")
            }
            WaLog.status.value = "Waiting for notification"
        }
    }

    /** Sends through the notification's inline Reply (RemoteInput). Returns false when Android refuses it. */
    private fun sendReply(contact: String, text: String, fallback: Notification.Action?): Boolean {
        val stored = synchronized(replyActions) { replyActions[contact] }
        val act = if (stored != null && System.currentTimeMillis() - stored.second < 10 * 60_000L) stored.first else fallback
        val inputs = act?.remoteInputs ?: return false
        val intent = Intent()
        val bundle = Bundle()
        inputs.forEach { bundle.putCharSequence(it.resultKey, text) }
        RemoteInput.addResultsToIntent(inputs, intent, bundle)
        return try {
            act.actionIntent.send(applicationContext, 0, intent)
            true
        } catch (e: PendingIntent.CanceledException) {
            WaLog.log("Reply action expired for $contact")
            false
        }
    }

    private fun speak(line: String) {
        if (CallMonitor.inCall || CallMonitor.ringing) return     // never talk over a phone call
        scope.launch {
            val s = SettingsStore(applicationContext).flow.first()
            main.post { voice.speakWhenReady(line, s) {} }
        }
    }

    companion object {
        fun isEnabled(ctx: Context): Boolean =
            android.provider.Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")?.contains(ctx.packageName) == true
    }
}
