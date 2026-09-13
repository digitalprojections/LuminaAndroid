package com.oneimage.android.ui.workflow

import com.oneimage.android.api.OneImageTaskResult

internal fun mergeResults(current: List<OneImageTaskResult>, incoming: List<OneImageTaskResult>): List<OneImageTaskResult> {
    val merged = current.toMutableList()
    incoming.forEach { result ->
        val key = result.identityKey()
        val index = merged.indexOfFirst { existing ->
            val existingKey = existing.identityKey()
            if (key.isNotBlank() && existingKey.isNotBlank()) existingKey == key
            else existing.label.isNotBlank() && existing.label == result.label
        }
        if (index >= 0) {
            val existing = merged[index]
            val preferred = if (existing.isDirectResult() && result.url.startsWith("webrtc://")) existing else result
            merged[index] = preferred.copy(
                label = existing.label.ifBlank { preferred.label },
                filename = preferred.filename.ifBlank { existing.filename },
                size = preferred.size.takeIf { it > 0L } ?: existing.size
            )
        } else {
            merged += result
        }
    }
    return merged
}

private fun OneImageTaskResult.isDirectResult(): Boolean =
    url.isNotBlank() && !url.startsWith("webrtc://")


private fun OneImageTaskResult.identityKey(): String =
    filename.ifBlank { if (url.startsWith("webrtc://")) url.removePrefix("webrtc://") else "" }
        .substringAfterLast('/').substringAfterLast('\\').trim()
