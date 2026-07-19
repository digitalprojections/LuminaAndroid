package com.oneimage.android.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.oneimage.android.BuildConfig
import com.oneimage.android.api.OneImageAccountProfile
import com.oneimage.android.api.OneImageApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AccountUiState(
    val isLoading: Boolean = false,
    val isAcceptingLegal: Boolean = false,
    val profile: OneImageAccountProfile? = null,
    val error: String? = null
)

class AccountViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState

    init {
        viewModelScope.launch {
            com.oneimage.android.api.AccountManager.profileFlow.collect { profile ->
                _uiState.value = _uiState.value.copy(profile = profile)
            }
        }
    }

    fun loadProfile() {
        if (_uiState.value.isLoading) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching {
                OneImageApi.bootstrapAccountProfile(
                    BuildConfig.ONEIMAGE_API_BASE_URL.ifBlank { "https://genstudio.web.app/" }
                )
            }.onSuccess { profile ->
                _uiState.value = _uiState.value.copy(isLoading = false, profile = profile)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = error.message ?: "Could not load account profile."
                )
            }
        }
    }

    fun acceptLegalAgreements() {
        if (_uiState.value.isAcceptingLegal) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAcceptingLegal = true, error = null)
            runCatching {
                val baseUrl = BuildConfig.ONEIMAGE_API_BASE_URL.ifBlank { "https://genstudio.web.app/" }
                OneImageApi.acceptLegalAgreements(baseUrl = baseUrl, method = "account_gate")
                OneImageApi.bootstrapAccountProfile(baseUrl)
            }.onSuccess { profile ->
                _uiState.value = _uiState.value.copy(
                    isAcceptingLegal = false,
                    profile = profile
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isAcceptingLegal = false,
                    error = error.message ?: "Could not accept Privacy & Terms."
                )
            }
        }
    }

    fun signOut() {
        FirebaseAuth.getInstance().signOut()
        _uiState.value = AccountUiState()
    }
}
