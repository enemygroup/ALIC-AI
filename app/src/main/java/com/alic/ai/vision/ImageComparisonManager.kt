package com.alic.ai.vision

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs

data class CompareResult(val similarity: Int, val changed: Int, val total: Int, val diff: Bitmap)

/** Offline comparison: both images are scaled to 160x160 grayscale and pixels that differ are marked red. */
object ImageComparisonManager {
    private const val SIZE = 160
    private const val THRESHOLD = 40

    fun compare(a: Bitmap, b: Bitmap): CompareResult {
        val pa = IntArray(SIZE * SIZE)
        val pb = IntArray(SIZE * SIZE)
        Bitmap.createScaledBitmap(a, SIZE, SIZE, true).getPixels(pa, 0, SIZE, 0, 0, SIZE, SIZE)
        Bitmap.createScaledBitmap(b, SIZE, SIZE, true).getPixels(pb, 0, SIZE, 0, 0, SIZE, SIZE)
        val out = IntArray(SIZE * SIZE)
        var changed = 0
        for (i in pa.indices) {
            val ga = gray(pa[i])
            val gb = gray(pb[i])
            if (abs(ga - gb) > THRESHOLD) {
                changed++
                out[i] = Color.argb(255, 255, 30, 45)
            } else {
                out[i] = Color.argb(255, ga, ga, ga)
            }
        }
        val total = SIZE * SIZE
        val diff = Bitmap.createBitmap(out, SIZE, SIZE, Bitmap.Config.ARGB_8888)
        return CompareResult(((total - changed) * 100) / total, changed, total, diff)
    }

    private fun gray(c: Int) = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
}
