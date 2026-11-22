package com.alic.ai.licence

import android.content.Context
import android.os.Build
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class LicenceResult {
    object Ok : LicenceResult()
    data class Denied(val msg: String) : LicenceResult()
    data class Offline(val msg: String) : LicenceResult()
}

/**
 * Firebase login + licence check. The website creates licences/{key}; the app binds an unused key to the first
 * account that activates it (one device per key). Expiry and status are read from Firestore.
 * Limit: without a server (Cloud Functions) the app enforces this check itself, so a modified APK could skip it.
 * Firestore rules protect the data. A short offline grace period keeps the app usable without internet.
 */
class LicenceManager(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("licence", Context.MODE_PRIVATE)
    private val graceMs = 3L * 24 * 3600 * 1000

    fun configured(): Boolean = FirebaseApp.getApps(ctx).isNotEmpty()
    fun signedIn(): Boolean = configured() && FirebaseAuth.getInstance().currentUser != null
    fun currentEmail(): String = if (configured()) FirebaseAuth.getInstance().currentUser?.email.orEmpty() else ""

    suspend fun signIn(email: String, password: String, create: Boolean): String? {
        if (!configured()) return "Firebase is not configured in this build."
        return try {
            val auth = FirebaseAuth.getInstance()
            if (create) auth.createUserWithEmailAndPassword(email.trim(), password).await()
            else auth.signInWithEmailAndPassword(email.trim(), password).await()
            null
        } catch (e: Exception) {
            e.message ?: "Sign-in failed."
        }
    }

    /** Sends a password reset email. Returns an error text, or null when the email was sent. */
    suspend fun resetPassword(email: String): String? {
        if (!configured()) return "Firebase is not configured in this build."
        return try {
            FirebaseAuth.getInstance().sendPasswordResetEmail(email.trim()).await()
            null
        } catch (e: Exception) {
            e.message ?: "Could not send the reset email."
        }
    }

    /** Sends a device-reset request to the admin (stored as a support message). */
    fun requestDeviceReset(onDone: (String) -> Unit) {
        val uid = currentUid()
        val key = prefs.getString("key", null)
        if (!configured() || uid == null || key == null) {
            onDone("Sign in and activate a key first.")
            return
        }
        val data = hashMapOf<String, Any>(
            "type" to "device_reset", "key" to key, "uid" to uid,
            "email" to (FirebaseAuth.getInstance().currentUser?.email ?: ""),
            "model" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "at" to System.currentTimeMillis(), "status" to "open"
        )
        FirebaseFirestore.getInstance().collection("supportMessages").add(data)
            .addOnSuccessListener { onDone("Request sent. The admin will reset your device.") }
            .addOnFailureListener { onDone("Could not send the request: " + (it.message ?: "error")) }
    }

    /** Uploads a saved crash report once the user is signed in, then deletes the local file. */
    fun sendSavedCrash(file: File) {
        if (!file.exists() || !configured()) return
        val uid = currentUid() ?: return
        val data = hashMapOf<String, Any>(
            "type" to "crash", "uid" to uid,
            "email" to (FirebaseAuth.getInstance().currentUser?.email ?: ""),
            "model" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "android" to Build.VERSION.RELEASE,
            "trace" to file.readText().take(20000),
            "at" to System.currentTimeMillis(), "status" to "open"
        )
        FirebaseFirestore.getInstance().collection("supportMessages").add(data)
            .addOnSuccessListener { file.delete() }
    }

    fun savedKey(): String = prefs.getString("key", "") ?: ""
    fun savedExpiry(): Long = prefs.getLong("exp", 0L)

    fun signOut() {
        if (configured()) FirebaseAuth.getInstance().signOut()
        prefs.edit().clear().apply()
    }

    /** Called with the key the user typed. Binds it to this account on first use. */
    suspend fun activate(raw: String): LicenceResult {
        val key = raw.uppercase(Locale.ROOT).filter { it.isLetterOrDigit() }
        if (key.length != 35) return LicenceResult.Denied("A licence key has 35 characters.")
        val uid = currentUid() ?: return LicenceResult.Denied("Sign in first.")
        return check(key, uid, bind = true)
    }

    /** Called on every app start with the saved key. Never binds. */
    suspend fun verifyStored(): LicenceResult {
        val uid = currentUid() ?: return LicenceResult.Denied("Sign in first.")
        val key = prefs.getString("key", null) ?: return LicenceResult.Denied("Enter your licence key.")
        if (prefs.getString("uid", null) != uid) return LicenceResult.Denied("Enter your licence key.")
        return check(key, uid, bind = false)
    }

    private fun currentUid(): String? = if (configured()) FirebaseAuth.getInstance().currentUser?.uid else null

    private fun deviceId(): String =
        android.provider.Settings.Secure.getString(ctx.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "unknown"

    private suspend fun check(key: String, uid: String, bind: Boolean): LicenceResult {
        return try {
            val ref = FirebaseFirestore.getInstance().collection("licenses").document(key)
            val snap = ref.get().await()
            if (!snap.exists()) return LicenceResult.Denied("This key does not exist.")
            if (snap.getString("status") != "active") return LicenceResult.Denied("This key is disabled. Contact support.")
            val exp = (snap.get("expiresAt") as? Number)?.toLong()
            if (exp != null && System.currentTimeMillis() > exp) {
                return LicenceResult.Denied("This key expired on " + fmt(exp) + ".")
            }
            val device = deviceId()
            val bound = snap.getString("boundUid")
            val boundDevice = snap.getString("boundDevice")
            val info = HashMap<String, Any>()
            info["boundModel"] = "${Build.MANUFACTURER} ${Build.MODEL}"
            info["boundEmail"] = FirebaseAuth.getInstance().currentUser?.email ?: ""
            if (bound == null) {
                if (!bind) return LicenceResult.Denied("This key is not activated yet.")
                val upd = HashMap<String, Any>(info)
                upd["boundUid"] = uid
                upd["boundDevice"] = device
                upd["boundAt"] = FieldValue.serverTimestamp()
                ref.update(upd).await()
            } else if (bound != uid) {
                return LicenceResult.Denied("This key is already used on another account.")
            } else if (boundDevice == null) {
                if (bind) {
                    val upd = HashMap<String, Any>(info)
                    upd["boundDevice"] = device
                    upd["boundAt"] = FieldValue.serverTimestamp()
                    ref.update(upd).await()
                }
            } else if (boundDevice != device) {
                return LicenceResult.Denied("This key is already used on another phone. Request a device reset from the licence screen.")
            }
            save(key, uid, exp)
            LicenceResult.Ok
        } catch (e: Exception) {
            // Network or permission problem: accept the saved licence for a short grace period only
            val exp = prefs.getLong("exp", 0L).takeIf { it > 0 }
            val last = prefs.getLong("ok", 0L)
            if (prefs.getString("key", null) == key && prefs.getString("uid", null) == uid &&
                System.currentTimeMillis() - last < graceMs && (exp == null || System.currentTimeMillis() < exp)) {
                LicenceResult.Offline("Offline: using the saved licence.")
            } else {
                LicenceResult.Denied("Could not verify the licence (${e.message ?: "network"}). Check internet and try again.")
            }
        }
    }

    private fun save(key: String, uid: String, exp: Long?) {
        prefs.edit().putString("key", key).putString("uid", uid)
            .putLong("exp", exp ?: 0L).putLong("ok", System.currentTimeMillis()).apply()
    }

    private fun fmt(ms: Long): String = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(ms))
}
