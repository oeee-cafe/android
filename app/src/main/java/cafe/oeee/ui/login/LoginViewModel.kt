package cafe.oeee.ui.login

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cafe.oeee.data.service.AuthService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val loginName: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isLoginSuccess: Boolean = false
)

class LoginViewModel(context: Context) : ViewModel() {
    private val authService = AuthService.getInstance(context)

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun updateLoginName(loginName: String) {
        _uiState.value = _uiState.value.copy(loginName = loginName, errorMessage = null)
    }

    fun updatePassword(password: String) {
        _uiState.value = _uiState.value.copy(password = password, errorMessage = null)
    }

    fun login() {
        val currentState = _uiState.value
        if (currentState.loginName.isBlank() || currentState.password.isBlank()) {
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            val result = authService.login(currentState.loginName, currentState.password)

            result.fold(
                onSuccess = {
                    // Login successful, trigger navigation
                    _uiState.value = _uiState.value.copy(isLoading = false, isLoginSuccess = true)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Login failed"
                    )
                }
            )
        }
    }

    fun isFormValid(): Boolean {
        val state = _uiState.value
        return state.loginName.isNotBlank() && state.password.isNotBlank()
    }
}
