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
    val sortOrder: Long,
    val active: Boolean
) {
    val searchableText: String =
        (listOf(question, shortAnswer, answer, category) + keywords).joinToString(" ").lowercase()

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

        private fun timestampMillis(value: Any?): Long = when (value) {
            is Timestamp -> value.toDate().time
            is java.util.Date -> value.time
            is Number -> value.toLong()
            else -> 0L
        }
    }
}
