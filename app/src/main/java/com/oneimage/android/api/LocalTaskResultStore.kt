package com.oneimage.android.api

import android.net.Uri
import com.oneimage.android.OneImageApplication
import org.json.JSONObject
import java.io.File
import java.util.Locale

data class LocalTaskResultAvailability(
    val localCount: Int,
    val totalCount: Int
) {
    val hasAnyLocal: Boolean
        get() = localCount > 0

    val hasAllLocal: Boolean
        get() = totalCount > 0 && localCount >= totalCount
}

object LocalTaskResultStore {
    private const val ROOT_DIRECTORY = "oneimage-results"
    private const val MANIFEST_FILE = "manifest.json"

    fun persistReceivedFile(file: WebRtcReceivedFile): OneImageTaskResult {
        val taskId = file.taskId?.trim().orEmpty()
        if (taskId.isBlank()) {
            return OneImageTaskResult(
                label = file.label ?: file.filename,
                url = file.url,
                filename = file.filename,
                size = file.size
            )
        }

        val sourceUri = Uri.parse(file.url)
        val context = OneImageApplication.appContext
        val taskDirectory = taskDirectory(taskId).apply { mkdirs() }
        val key = resultKey(file.filename, file.label, file.fileId)
        val safeFilename = sanitizeFilename(file.filename.ifBlank { file.label ?: file.fileId })
        val targetFile = File(taskDirectory, "${sanitizeFilename(key)}__$safeFilename")

        val inputStream = openUriInputStream(sourceUri) ?: return OneImageTaskResult(
            label = file.label ?: file.filename,
            url = file.url,
            filename = file.filename,
            size = file.size
        )
        inputStream.use { input ->
            targetFile.outputStream().use { output -> input.copyTo(output) }
        }

        val manifest = readManifest(taskId)
        manifest.put(
            key,
            JSONObject()
                .put("storedFileName", targetFile.name)
                .put("filename", file.filename)
                .put("label", file.label)
                .put("size", targetFile.length())
        )
        writeManifest(taskId, manifest)

        return OneImageTaskResult(
            label = file.label ?: file.filename,
            url = Uri.fromFile(targetFile).toString(),
            filename = file.filename,
            size = targetFile.length()
        )
    }

    fun overlayTask(task: OneImageTask): OneImageTask =
        task.copy(results = overlayResults(task.id, task.results))

    fun overlayResults(taskId: String, results: List<OneImageTaskResult>): List<OneImageTaskResult> =
        results.map { result ->
            val localFile = findLocalFile(taskId, result) ?: return@map result
            result.copy(url = Uri.fromFile(localFile).toString(), size = localFile.length())
        }

    fun availability(task: OneImageTask): LocalTaskResultAvailability {
        val manifest = readManifest(task.id)
        val localCount = if (task.results.isEmpty()) {
            manifest.keys().asSequence()
                .mapNotNull { key -> manifest.optJSONObject(key)?.optString("storedFileName").orEmpty().takeIf { it.isNotBlank() } }
                .count { File(taskDirectory(task.id), it).isFile }
        } else {
            task.results.count { findLocalFile(task.id, it) != null }
        }
        val totalCount = task.results.size.takeIf { it > 0 } ?: localCount
        return LocalTaskResultAvailability(localCount = localCount, totalCount = totalCount)
    }

    fun clearTask(taskId: String) {
        deleteRecursively(taskDirectory(taskId))
    }

    private fun findLocalFile(taskId: String, result: OneImageTaskResult): File? {
        if (taskId.isBlank()) return null
        val directory = taskDirectory(taskId)
        if (!directory.isDirectory) return null

        val manifest = readManifest(taskId)
        val key = resultKey(result.filename, result.label, result.url.removePrefix("webrtc://"))
        val manifestFileName = manifest.optJSONObject(key)?.optString("storedFileName").orEmpty()
        if (manifestFileName.isNotBlank()) {
            val direct = File(directory, manifestFileName)
            if (direct.isFile) return direct
        }

        val safeFilename = sanitizeFilename(result.filename.ifBlank { key })
        return directory.listFiles()
            ?.firstOrNull { file -> file.isFile && (file.name == safeFilename || file.name.endsWith("__$safeFilename")) }
    }

    private fun taskDirectory(taskId: String): File =
        File(OneImageApplication.appContext.filesDir, "$ROOT_DIRECTORY${File.separator}$taskId")

    private fun manifestFile(taskId: String): File =
        File(taskDirectory(taskId), MANIFEST_FILE)

    private fun readManifest(taskId: String): JSONObject {
        val file = manifestFile(taskId)
        if (!file.isFile) return JSONObject()
        return runCatching { JSONObject(file.readText()) }.getOrElse { JSONObject() }
    }

    private fun writeManifest(taskId: String, manifest: JSONObject) {
        val directory = taskDirectory(taskId).apply { mkdirs() }
        File(directory, MANIFEST_FILE).writeText(manifest.toString())
    }

    private fun resultKey(filename: String, label: String?, fallback: String): String =
        filename.ifBlank { label.orEmpty().ifBlank { fallback } }
            .trim()
            .lowercase(Locale.US)
            .ifBlank { "result" }

    private fun sanitizeFilename(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "result" }

    private fun openUriInputStream(uri: Uri) = when (uri.scheme?.lowercase(Locale.US)) {
        "content" -> OneImageApplication.appContext.contentResolver.openInputStream(uri)
        "file" -> uri.path?.let { File(it).takeIf(File::isFile)?.inputStream() }
        else -> null
    }

    private fun deleteRecursively(target: File) {
        if (!target.exists()) return
        if (target.isDirectory) {
            target.listFiles()?.forEach(::deleteRecursively)
        }
        target.delete()
    }
}
