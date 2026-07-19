package com.oneimage.android.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.oneimage.android.BuildConfig
import com.oneimage.android.api.OneImageApi
import com.oneimage.android.api.awaitResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class Authenticated(val user: FirebaseUser) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModel : ViewModel() {
    private val auth: FirebaseAuth? by lazy {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            null
        }
    }
    
    private val _uiState = MutableStateFlow<AuthUiState>(
        auth?.currentUser?.let { AuthUiState.Authenticated(it) } ?: AuthUiState.Idle
    )
    val uiState: StateFlow<AuthUiState> = _uiState

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            if (auth == null) {
                _uiState.value = AuthUiState.Error("Firebase not initialized. Please add google-services.json.")
                return@launch
            }
            runCatching {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val result = auth!!.signInWithCredential(credential).awaitResult()
                OneImageApi.bootstrapAccountProfile(
                    baseUrl = BuildConfig.ONEIMAGE_API_BASE_URL.ifBlank { "https://genstudio.web.app/" },
                    legalAcceptanceMethod = "login_checkbox"
                )
                result
            }.onSuccess { result ->
                result.user?.let { user ->
                    _uiState.value = AuthUiState.Authenticated(user)
                } ?: run {
                    _uiState.value = AuthUiState.Error("Google sign-in completed without a Firebase user.")
                }
            }.onFailure { error ->
                _uiState.value = AuthUiState.Error(error.message ?: "Google sign-in failed.")
            }
        }
    }

    fun onGoogleSignInFailed(message: String) {
        _uiState.value = AuthUiState.Error(message)
    }

    fun signOut() {
        auth?.signOut()
        _uiState.value = AuthUiState.Idle
    }
}
