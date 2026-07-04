package com.example.ui.imagegen

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class ImageGenUiState {
    object Idle : ImageGenUiState()
    object Loading : ImageGenUiState()
    data class Success(val base64Image: String) : ImageGenUiState()
    data class Error(val message: String) : ImageGenUiState()
}

class ImageGenViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<ImageGenUiState>(ImageGenUiState.Idle)
    val uiState: StateFlow<ImageGenUiState> = _uiState

    fun generateImage(prompt: String) {
        viewModelScope.launch {
            _uiState.value = ImageGenUiState.Loading
            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                    generationConfig = GenerationConfig(
                        imageConfig = ImageConfig(aspectRatio = "1:1", imageSize = "1K"),
                        responseModalities = listOf("IMAGE")
                    )
                )
                val response = RetrofitClient.geminiService.generateImage(apiKey, request)
                val base64 = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.inlineData?.data
                if (base64 != null) {
                    _uiState.value = ImageGenUiState.Success(base64)
                } else {
                    _uiState.value = ImageGenUiState.Error("No image generated")
                }
            } catch (e: Exception) {
                _uiState.value = ImageGenUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
