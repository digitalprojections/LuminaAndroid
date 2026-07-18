package com.oneimage.android.ui.shared

import com.oneimage.android.api.OneImageTaskResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val GENERIC_RESULT_LABELS = setOf(
    "",
    "result",
    "output",
    "file",
    "video",
    "image",
    "model",
    "mesh",
    "download"
)

fun savedAssetFilename(
    workflowName: String,
    result: OneImageTaskResult,
    defaultExtension: String,
    dateMillis: Long = System.currentTimeMillis(),
    index: Int? = null
): String {
    val extension = resultExtension(result, defaultExtension)
    val workflow = workflowName.toSlug().ifBlank { "oneimage" }
    val descriptor = result.label
        .substringBeforeLast('.', result.label)
        .toSlug()
        .takeUnless { it in GENERIC_RESULT_LABELS }
        .orEmpty()
    val parts = buildList {
        add(workflow)
        add(SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date(dateMillis.coerceAtLeast(1L))))
        if (descriptor.isNotBlank()) add(descriptor)
        if (index != null) add((index + 1).toString().padStart(2, '0'))
    }
    return "${parts.joinToString("_")}.$extension"
}

private fun resultExtension(result: OneImageTaskResult, defaultExtension: String): String {
    val candidates = listOf(result.filename, result.url, result.label)
    val extension = candidates
        .asSequence()
        .map { value ->
            value
                .substringBefore('?')
                .substringBefore('#')
                .substringAfterLast('/')
                .substringAfterLast('\\')
                .substringAfterLast('.', "")
                .lowercase(Locale.US)
        }
        .firstOrNull { it.matches(Regex("[a-z0-9]{2,5}")) }
        .orEmpty()
    return extension.ifBlank { defaultExtension.trimStart('.').lowercase(Locale.US) }
}

private fun String.toSlug(): String =
    lowercase(Locale.US)
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
