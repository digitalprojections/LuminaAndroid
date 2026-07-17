package com.oneimage.android.ui.support

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oneimage.android.support.SupportAnswer
import com.oneimage.android.support.SupportQaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SupportUiState(
    val query: String = "",
    val selectedCategory: String = "All",
    val selectedWorkflow: String = ALL_WORKFLOWS,
    val categories: List<String> = listOf("All"),
    val workflowFilters: List<String> = listOf(ALL_WORKFLOWS),
    val answers: List<SupportAnswer> = emptyList(),
    val usingFallback: Boolean = false,
    val error: String? = null
)

const val ALL_WORKFLOWS = "All workflows"

class SupportViewModel : ViewModel() {
    private val repository = SupportQaRepository()
    private val query = MutableStateFlow("")
    private val selectedCategory = MutableStateFlow("All")
    private val selectedWorkflow = MutableStateFlow(ALL_WORKFLOWS)
    private val qaResult = repository.observeAnswers()

    val uiState: StateFlow<SupportUiState> =
        combine(query, selectedCategory, selectedWorkflow, qaResult) { search, category, workflow, result ->
            val trimmed = search.trim().lowercase()
            val answers = result.answers.filter { answer ->
                val categoryMatches = category == "All" || answer.category == category
                val workflowMatches = workflow == ALL_WORKFLOWS || answer.workflows.contains(workflow)
                val queryMatches = trimmed.isBlank() || trimmed.split(Regex("\\s+")).all { answer.searchableText.contains(it) }
                categoryMatches && workflowMatches && queryMatches
            }
            val categories = listOf("All") + result.answers.map { it.category }.distinct().sorted()
            val workflowFilters = listOf(ALL_WORKFLOWS) + result.answers.flatMap { it.workflows }.distinct().sorted()

            SupportUiState(
                query = search,
                selectedCategory = category,
                selectedWorkflow = workflow.takeIf { it in workflowFilters } ?: ALL_WORKFLOWS,
                categories = categories,
                workflowFilters = workflowFilters,
                answers = answers,
                usingFallback = result.usingFallback,
                error = result.error
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SupportUiState())

    fun updateQuery(value: String) {
        query.value = value
    }

    fun selectCategory(value: String) {
        selectedCategory.value = value
    }

    fun selectWorkflow(value: String) {
        selectedWorkflow.value = value
    }
}
