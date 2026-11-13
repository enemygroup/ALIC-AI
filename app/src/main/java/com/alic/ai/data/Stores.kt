package com.alic.ai.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "alic_settings")

class SettingsStore(private val ctx: Context) {
    private object K {
        val model = stringPreferencesKey("model")
        val liveModel = stringPreferencesKey("live_model")
        val liveVoice = stringPreferencesKey("live_voice")
        val baseUrl = stringPreferencesKey("base_url")
        val personality = stringPreferencesKey("personality")
        val style = intPreferencesKey("style")
        val ttsVoice = stringPreferencesKey("tts_voice")
        val deep = booleanPreferencesKey("deep")
        val pitch = floatPreferencesKey("pitch")
        val speed = floatPreferencesKey("speed")
        val language = stringPreferencesKey("language")
        val haptics = booleanPreferencesKey("haptics")
        val anim = booleanPreferencesKey("anim")
        val memory = booleanPreferencesKey("memory")
        val notes = stringPreferencesKey("notes")
        val confirm = booleanPreferencesKey("confirm_actions_v2")
        val notif = booleanPreferencesKey("notif")
        val guest = booleanPreferencesKey("guest")
        val theme = stringPreferencesKey("theme_mode")
        val accent = stringPreferencesKey("accent")
        val wakeEnabled = booleanPreferencesKey("wake_enabled")
        val wakeAlwaysOn = booleanPreferencesKey("wake_always_on")
        val wakeSound = booleanPreferencesKey("wake_sound")
        val wakeSens = stringPreferencesKey("wake_sensitivity")
        val popupAnim = booleanPreferencesKey("popup_animation")
        val silence = intPreferencesKey("silence_timeout")
        val barge = booleanPreferencesKey("voice_interruption")
        val announce = booleanPreferencesKey("announce_calls")
        val persona = stringPreferencesKey("persona")
        val userName = stringPreferencesKey("user_name")
        val imageModel = stringPreferencesKey("image_model")
        val clapEnabled = booleanPreferencesKey("clap_enabled")
        val clapMode = stringPreferencesKey("clap_mode")
        val clapSens = stringPreferencesKey("clap_sensitivity")
    }

    private fun read(p: Preferences): AppSettings {
        val d = AppSettings()
        return AppSettings(
            model = p[K.model] ?: d.model,
            liveModel = p[K.liveModel] ?: d.liveModel,
            liveVoice = p[K.liveVoice] ?: d.liveVoice,
            baseUrl = p[K.baseUrl] ?: d.baseUrl,
            personality = p[K.personality] ?: d.personality,
            responseStyle = p[K.style] ?: d.responseStyle,
            ttsVoice = p[K.ttsVoice] ?: d.ttsVoice,
            deepVoice = p[K.deep] ?: d.deepVoice,
            pitch = p[K.pitch] ?: d.pitch,
            speed = p[K.speed] ?: d.speed,
            language = p[K.language] ?: d.language,
            haptics = p[K.haptics] ?: d.haptics,
            animations = p[K.anim] ?: d.animations,
            memoryEnabled = p[K.memory] ?: d.memoryEnabled,
            memoryNotes = (p[K.notes] ?: "").split('\u0001').filter { it.isNotBlank() },
            confirmActions = p[K.confirm] ?: d.confirmActions,
            notifications = p[K.notif] ?: d.notifications,
            guest = p[K.guest] ?: d.guest,
            themeMode = p[K.theme] ?: d.themeMode,
            accent = p[K.accent] ?: d.accent,
            wakeEnabled = p[K.wakeEnabled] ?: d.wakeEnabled,
            wakeAlwaysOn = p[K.wakeAlwaysOn] ?: d.wakeAlwaysOn,
            wakeSound = p[K.wakeSound] ?: d.wakeSound,
            wakeSensitivity = p[K.wakeSens] ?: d.wakeSensitivity,
            popupAnimation = p[K.popupAnim] ?: d.popupAnimation,
            silenceTimeoutSec = p[K.silence] ?: d.silenceTimeoutSec,
            voiceInterruption = p[K.barge] ?: d.voiceInterruption,
            announceCalls = p[K.announce] ?: d.announceCalls,
            persona = p[K.persona] ?: d.persona,
            userName = p[K.userName] ?: d.userName,
            imageModel = p[K.imageModel] ?: d.imageModel,
            clapEnabled = p[K.clapEnabled] ?: d.clapEnabled,
            clapMode = p[K.clapMode] ?: d.clapMode,
            clapSensitivity = p[K.clapSens] ?: d.clapSensitivity
        )
    }

    val flow: Flow<AppSettings> = ctx.dataStore.data.map { read(it) }

    suspend fun update(block: (AppSettings) -> AppSettings) {
        ctx.dataStore.edit { p ->
            val s = block(read(p))
            p[K.model] = s.model; p[K.liveModel] = s.liveModel; p[K.liveVoice] = s.liveVoice
            p[K.baseUrl] = s.baseUrl; p[K.personality] = s.personality; p[K.style] = s.responseStyle
            p[K.ttsVoice] = s.ttsVoice; p[K.deep] = s.deepVoice; p[K.pitch] = s.pitch
            p[K.speed] = s.speed; p[K.language] = s.language; p[K.haptics] = s.haptics
            p[K.anim] = s.animations; p[K.memory] = s.memoryEnabled
            p[K.notes] = s.memoryNotes.joinToString("\u0001"); p[K.confirm] = s.confirmActions
            p[K.notif] = s.notifications; p[K.guest] = s.guest; p[K.theme] = s.themeMode; p[K.accent] = s.accent
            p[K.wakeEnabled] = s.wakeEnabled; p[K.wakeAlwaysOn] = s.wakeAlwaysOn; p[K.wakeSound] = s.wakeSound
            p[K.wakeSens] = s.wakeSensitivity; p[K.popupAnim] = s.popupAnimation
            p[K.silence] = s.silenceTimeoutSec; p[K.barge] = s.voiceInterruption
            p[K.announce] = s.announceCalls; p[K.persona] = s.persona; p[K.userName] = s.userName; p[K.imageModel] = s.imageModel
            p[K.clapEnabled] = s.clapEnabled; p[K.clapMode] = s.clapMode; p[K.clapSens] = s.clapSensitivity
        }
    }
}

/** API key lives only in EncryptedSharedPreferences (Android Keystore backed). Never in source or Git. */
class SecureStore(ctx: Context) {
    private val prefs: SharedPreferences? = try {
        val key = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            ctx, "alic_secure", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        null
    }
    private var memoryOnly: String = ""

    var apiKey: String
        get() = prefs?.getString("gemini_key", "") ?: memoryOnly
        set(v) {
            if (prefs != null) prefs.edit().putString("gemini_key", v.trim()).apply() else memoryOnly = v.trim()
        }

    val secureStorageAvailable: Boolean get() = prefs != null
}
