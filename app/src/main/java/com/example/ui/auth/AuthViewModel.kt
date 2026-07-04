package com.example.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
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
            // In a real app, you'd use Firebase Auth with the ID token
            _uiState.value = AuthUiState.Error("Google Sign-In requires valid configuration.")
        }
    }

    fun signOut() {
        auth?.signOut()
        _uiState.value = AuthUiState.Idle
    }
}
