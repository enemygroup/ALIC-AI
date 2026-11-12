package com.alic.ai.wake

import android.os.SystemClock

/** Shared "ignore wake words until" clock (used after the popup closes so the same utterance cannot re-trigger). */
object WakeGate {
    @Volatile var ignoreUntil = 0L
    fun ignoreFor(ms: Long) { ignoreUntil = maxOf(ignoreUntil, SystemClock.elapsedRealtime() + ms) }
    fun ignoring(): Boolean = SystemClock.elapsedRealtime() < ignoreUntil
}
