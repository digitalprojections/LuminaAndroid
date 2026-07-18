package com.oneimage.android.support

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

data class SupportAnswer(
    val id: String,
    val question: String,
    val shortAnswer: String,
    val answer: String,
    val category: String,
    val keywords: List<String>,
    val workflows: List<String>,
    val sortOrder: Long,
    val active: Boolean
) {
    val searchableText: String =
        (listOf(question, shortAnswer, answer, category) + keywords + workflows).joinToString(" ").lowercase()

    companion object {
        fun fromDocument(document: DocumentSnapshot): SupportAnswer? {
            val data = document.data ?: return null
            val active = data["active"] as? Boolean ?: true
            return SupportAnswer(
                id = document.id,
                question = string(data["question"]).ifBlank { return null },
                shortAnswer = string(data["shortAnswer"]),
                answer = string(data["answer"]).ifBlank { string(data["fullAnswer"]) },
                category = string(data["category"]).ifBlank { string(data["section"]).ifBlank { "General" } },
                keywords = keywords(data["keywords"]),
                workflows = workflows(data["workflows"], data["workflow"], document.id),
                sortOrder = (data["sortOrder"] as? Number)?.toLong() ?: timestampMillis(data["updatedAt"]),
                active = active
            )
        }

        private fun string(value: Any?): String = value?.toString().orEmpty()

        private fun keywords(value: Any?): List<String> = when (value) {
            is List<*> -> value.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotBlank) }
            is String -> value.split(",", " ").mapNotNull { it.trim().takeIf(String::isNotBlank) }
            else -> emptyList()
        }

        private fun workflows(listValue: Any?, singleValue: Any?, id: String): List<String> {
            val explicit = when (listValue) {
                is List<*> -> listValue.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotBlank) }
                is String -> listValue.split(",", "|").mapNotNull { it.trim().takeIf(String::isNotBlank) }
                else -> emptyList()
            } + singleValue?.toString()?.trim().orEmpty().takeIf(String::isNotBlank).let { value ->
                if (value == null) emptyList() else listOf(value)
            }
            return (explicit.ifEmpty { inferredWorkflows(id) }).distinct()
        }

        private fun timestampMillis(value: Any?): Long = when (value) {
            is Timestamp -> value.toDate().time
            is java.util.Date -> value.time
            is Number -> value.toLong()
            else -> 0L
        }
    }
}

fun inferredWorkflows(id: String): List<String> = when {
    id in setOf("workflow-image-generation") -> listOf("Image Generation")
    id in setOf("workflow-video-generation") -> listOf("Video Generation")
    id in setOf("workflow-lipsync", "workflow-lipsync-limits") -> listOf("LipSync")
    id in setOf("workflow-keyframes") -> listOf("Keyframes")
    id in setOf("workflow-story-images") -> listOf("Story Images")
    id in setOf("workflow-ref-restyle") -> listOf("Ref Restyle")
    id in setOf("workflow-character-replacement", "workflow-character-replacement-limits") -> listOf("Character Replacement")
    id in setOf("workflow-game-mesh", "results-mesh-viewer") -> listOf("Game Mesh")
    id in setOf("workflow-upscaler") -> listOf("Game Asset Upscaler")
    id in setOf("workflow-single-i2v", "workflow-single-i2v-ratios") -> listOf("Single I2V")
    else -> emptyList()
}
