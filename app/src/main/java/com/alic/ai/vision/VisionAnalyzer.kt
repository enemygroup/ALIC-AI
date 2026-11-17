package com.alic.ai.vision

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

data class Label(val name: String, val confidence: Int)
data class Analysis(val text: String, val labels: List<Label>)

/**
 * On-device analysis: text (OCR for Latin and Devanagari) and object/scene labels.
 * No internet is needed. Models ship with ML Kit. Must run off the main thread (suspend, uses Dispatchers.Default).
 */
object VisionAnalyzer {
    suspend fun analyze(bmp: Bitmap): Analysis {
        val input = InputImage.fromBitmap(bmp, 0)
        val latin = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val deva = TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
        val labeler = ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.6f).build())
        try {
            val latinText = latin.process(input).await().text
            val devaText = deva.process(input).await().text
            val labels = labeler.process(input).await().map { Label(it.text, (it.confidence * 100).toInt()) }
            val text = listOf(latinText, devaText).filter { it.isNotBlank() }.distinct().joinToString("\n")
            return Analysis(text, labels)
        } finally {
            latin.close(); deva.close(); labeler.close()
        }
    }
}
