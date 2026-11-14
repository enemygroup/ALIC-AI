package com.alic.ai.projectbuild

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Instructions for Gemini: the reply must contain every project file in a fixed, parseable format. */
object ProjectPrompt {
    val SYSTEM = """
You are the developer engine of ALIC AI. Build the user's app as a SMALL, WORKING Android project in Kotlin with Jetpack Compose.
Speed matters: answer quickly, with no long introduction. Start with one short sentence, then the files.

Output exactly these files, in this format, and nothing else:

### FILE: settings.gradle.kts
```kotlin
...
```

Files (exactly these, no tests, no extra resource files):
1. settings.gradle.kts
2. build.gradle.kts
3. app/build.gradle.kts
4. app/src/main/AndroidManifest.xml (use android:theme="@android:style/Theme.Material.Light.NoActionBar", no res files needed)
5. app/src/main/java/com/alic/app/MainActivity.kt (put ALL the Compose UI and logic in this one file)

Versions: Android Gradle Plugin 8.5.2, Kotlin 1.9.24, compose compiler extension 1.5.14, Compose BOM 2024.06.00, minSdk 26, targetSdk 34, compileSdk 34.
Keep MainActivity.kt under about 300 lines. Write full file contents. Never use placeholders or "...".
""".trimIndent()
}

/** Reads "### FILE: path" headings followed by a fenced block from a Gemini reply. */
object ProjectParser {
    private val rx = Regex("""(?m)^#{0,3}\s*(?:FILE|File):\s*`?([^\n`]+?)`?\s*\n+```[^\n]*\n([\s\S]*?)```""")

    fun parse(text: String): List<Pair<String, String>> =
        rx.findAll(text)
            .map { it.groupValues[1].trim() to it.groupValues[2] }
            .filter { safe(it.first) }
            .toList()

    /** Only plain relative paths inside the project folder are accepted. */
    private fun safe(p: String) = p.isNotBlank() && !p.startsWith("/") && !p.contains("..") && !p.contains('\\')
}

/** Saves files to Download/ALIC_AI/... using MediaStore on Android 10+ and the public folder on older versions. */
object ProjectSaver {
    private const val ROOT = "ALIC_AI"

    data class Result(val saved: Int, val failed: Int, val bytes: Long, val zipPath: String?, val zipBytes: Int)

    /** Returns bytes written, or -1 on failure. [rel] is relative to Download/ALIC_AI. */
    fun write(ctx: Context, rel: String, bytes: ByteArray, mime: String): Long {
        val slash = rel.lastIndexOf('/')
        val dir = if (slash >= 0) rel.substring(0, slash) else ""
        val name = rel.substring(slash + 1)
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = ctx.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/$ROOT" + if (dir.isEmpty()) "" else "/$dir")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return -1
            return try {
                val out = resolver.openOutputStream(uri) ?: throw IllegalStateException("no output stream")
                out.use { it.write(bytes) }
                resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
                bytes.size.toLong()
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                -1L
            }
        }
        return try {
            val f = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "$ROOT/$rel")
            f.parentFile?.mkdirs()
            f.writeBytes(bytes)
            if (f.exists() && f.length() == bytes.size.toLong()) bytes.size.toLong() else -1L
        } catch (e: Exception) {
            -1L
        }
    }

    fun saveProject(ctx: Context, project: String, files: List<Pair<String, String>>): Result {
        var failed = 0
        var total = 0L
        for ((path, body) in files) {
            val n = write(ctx, "$project/$path", body.toByteArray(Charsets.UTF_8), "text/plain")
            if (n < 0) failed++ else total += n
        }
        val zip = zipOf(project, files)
        val zn = write(ctx, "$project.zip", zip, "application/zip")
        return Result(
            saved = files.size - failed, failed = failed, bytes = total,
            zipPath = if (zn >= 0) "Download/$ROOT/$project.zip" else null, zipBytes = zip.size
        )
    }

    private fun zipOf(project: String, files: List<Pair<String, String>>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            for ((path, body) in files) {
                zos.putNextEntry(ZipEntry("$project/$path"))
                zos.write(body.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return bos.toByteArray()
    }
}

object ProjectBuilder {
    /** Folder name from the request, plus a time stamp so runs never overwrite each other. */
    fun projectName(desc: String): String {
        val base = desc.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "_").trim('_').take(32).ifBlank { "alic_project" }
        return base + "_" + SimpleDateFormat("HHmmss", Locale.US).format(Date())
    }
}
