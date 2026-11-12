package com.alic.ai.wake

import android.content.Context
import android.content.res.AssetManager
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * Copies the Vosk model from assets/ to filesDir/vosk-model on first run and checks it.
 *
 * Put the extracted model contents (NOT the zip) in ONE of:
 *   app/src/main/assets/model-en-us/   (vosk-model-small-en-us-0.15, preferred when present)
 *   app/src/main/assets/model-hi/      (vosk-model-small-hi-0.22)
 * Keep the model's internal folders (am, conf, graph, ivector) exactly as they are.
 */
object VoskModelManager {
    private val ASSET_CANDIDATES = listOf("model-en-us", "model-hi")
    private const val TARGET = "vosk-model"
    private const val MARKER = ".alic_source"

    /**
     * Wake phrase spellings we are willing to spot. Only phrases whose every word exists in the loaded model's
     * vocabulary are used (see usablePhrases): the English model understands Latin spellings, the Hindi model
     * only Devanagari ones. "Alic" is not a dictionary word in either model, so the nearest real words
     * ("alec" / एलेक) are accepted. Accuracy for "Hey Alic" must be tested on the target phone.
     */
    val CANDIDATE_PHRASES = listOf(
        "hey alic", "hey alick", "hey alec", "hey alik",
        "हे एलेक", "हेय एलेक", "हे एलिस", "हेय एलिस", "हे एलिक", "हे ऐलिक", "हे अलिक"
    )

    fun modelDir(ctx: Context) = File(ctx.filesDir, TARGET)

    fun assetSource(ctx: Context): String? = ASSET_CANDIDATES.firstOrNull {
        try { ctx.assets.list(it)?.isNotEmpty() == true } catch (e: Exception) { false }
    }

    fun isUsable(dir: File): Boolean =
        File(dir, "am/final.mdl").length() > 0 && File(dir, "conf/mfcc.conf").exists() &&
            (File(dir, "graph/HCLr.fst").exists() || File(dir, "graph/HCLG.fst").exists())

    /** Human-readable status for the settings screen. */
    fun describe(ctx: Context): String {
        val dir = modelDir(ctx)
        val src = assetSource(ctx)
        return when {
            isUsable(dir) -> "Ready (${File(dir, MARKER).takeIf { it.exists() }?.readText()?.trim() ?: "model"}, copied to app storage)"
            src != null -> "Bundled as assets/$src; will be copied on first run"
            else -> "Missing: put the extracted model in app/src/main/assets/model-hi/ (or model-en-us/)"
        }
    }

    /** Copies assets -> filesDir (only when needed) and returns the usable model directory. Blocking; call off the main thread. */
    fun ensureInstalled(ctx: Context): File {
        val dir = modelDir(ctx)
        val src = assetSource(ctx)
        if (src == null) {
            if (isUsable(dir)) return dir
            throw IllegalStateException("No Vosk model found in assets (expected assets/model-hi or assets/model-en-us)")
        }
        val marker = File(dir, MARKER)
        if (isUsable(dir) && marker.exists() && marker.readText().trim() == src) return dir   // already copied

        val tmp = File(ctx.filesDir, "$TARGET.tmp")
        tmp.deleteRecursively()
        copyAssets(ctx.assets, src, tmp)
        // Kaldi reads ivector/online_cmvn.conf; official models ship it as an empty file, which some packagers drop.
        if (File(tmp, "ivector").isDirectory) File(tmp, "ivector/online_cmvn.conf").apply { if (!exists()) createNewFile() }
        if (!isUsable(tmp)) {
            tmp.deleteRecursively()
            throw IllegalStateException("Model copy is incomplete or corrupt (am/final.mdl, conf/mfcc.conf or graph/ missing)")
        }
        File(tmp, MARKER).writeText(src)
        dir.deleteRecursively()
        if (!tmp.renameTo(dir)) throw IllegalStateException("Could not finalize the model folder")
        return dir
    }

    private fun copyAssets(am: AssetManager, path: String, dest: File) {
        val children = am.list(path) ?: emptyArray()
        if (children.isEmpty()) {
            try {
                dest.parentFile?.mkdirs()
                am.open(path).use { input -> FileOutputStream(dest).use { out -> input.copyTo(out, 64 * 1024) } }
            } catch (e: java.io.FileNotFoundException) {
                dest.mkdirs() // an empty directory, not a file
            }
        } else {
            dest.mkdirs()
            for (name in children) copyAssets(am, "$path/$name", File(dest, name))
        }
    }

    /** Returns the candidate phrases whose words are all present in the model vocabulary. */
    fun usablePhrases(dir: File): List<String> {
        val wanted = CANDIDATE_PHRASES.flatMap { it.split(' ') }.toSet()
        val present = vocabularyContains(dir, wanted)
        return CANDIDATE_PHRASES.filter { p -> p.split(' ').all { it in present } }
    }

    private fun vocabularyContains(dir: File, words: Set<String>): Set<String> {
        val wordsTxt = File(dir, "graph/words.txt")
        if (wordsTxt.exists()) {
            val found = HashSet<String>()
            wordsTxt.useLines { lines -> lines.forEach { l -> val w = l.substringBefore(' ').trim(); if (w in words) found.add(w) } }
            return found
        }
        // Models without words.txt keep the word list as an OpenFst symbol table inside Gr.fst:
        // each entry is [int32 little-endian byte length][UTF-8 word][int64 key]. Look for exact entries.
        val fst = listOf("graph/Gr.fst", "graph/HCLG.fst", "graph/HCLr.fst").map { File(dir, it) }.firstOrNull { it.exists() }
            ?: return emptySet()
        val patterns = words.associateWith { w ->
            val b = w.toByteArray(Charsets.UTF_8)
            val n = b.size
            String(byteArrayOf((n and 0xFF).toByte(), ((n shr 8) and 0xFF).toByte(), 0, 0) + b, Charsets.ISO_8859_1)
        }
        val maxLen = patterns.values.maxOfOrNull { it.length } ?: return emptySet()
        val found = HashSet<String>()
        RandomAccessFile(fst, "r").use { raf ->
            val chunk = 4 * 1024 * 1024
            val buf = ByteArray(chunk + maxLen)
            var pos = 0L
            val len = raf.length()
            while (pos < len && found.size < words.size) {
                raf.seek(pos)
                val n = raf.read(buf, 0, buf.size)
                if (n <= 0) break
                val s = String(buf, 0, n, Charsets.ISO_8859_1)
                for ((w, p) in patterns) if (w !in found && s.contains(p)) found.add(w)
                pos += chunk
            }
        }
        return found
    }
}
