package cafe.oeee.ui.signup

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cafe.oeee.R
import cafe.oeee.data.service.AuthService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SignupUiState(
    val loginName: String = "",
    val password: String = "",
    val passwordConfirm: String = "",
    val displayName: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSignupSuccess: Boolean = false
)

class SignupViewModel(private val context: Context) : ViewModel() {
    private val authService = AuthService.getInstance(context)

    private val _uiState = MutableStateFlow(SignupUiState())
    val uiState: StateFlow<SignupUiState> = _uiState.asStateFlow()

    fun updateLoginName(loginName: String) {
        _uiState.value = _uiState.value.copy(loginName = loginName, errorMessage = null)
    }

    fun updatePassword(password: String) {
        _uiState.value = _uiState.value.copy(password = password, errorMessage = null)
    }

    fun updatePasswordConfirm(passwordConfirm: String) {
        _uiState.value = _uiState.value.copy(passwordConfirm = passwordConfirm, errorMessage = null)
    }

    fun updateDisplayName(displayName: String) {
        _uiState.value = _uiState.value.copy(displayName = displayName, errorMessage = null)
    }

    fun signup() {
        val currentState = _uiState.value

        // Validate all fields are filled
        if (currentState.loginName.isBlank() ||
            currentState.password.isBlank() ||
            currentState.passwordConfirm.isBlank() ||
            currentState.displayName.isBlank()) {
            return
        }

        // Validate passwords match
        if (currentState.password != currentState.passwordConfirm) {
            _uiState.value = _uiState.value.copy(errorMessage = context.getString(R.string.error_passwords_not_match))
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            val result = authService.signup(
                currentState.loginName,
                currentState.password,
                currentState.displayName
            )

            result.fold(
                onSuccess = {
                    // Signup successful, user is auto-logged in, trigger navigation
                    _uiState.value = _uiState.value.copy(isLoading = false, isSignupSuccess = true)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Signup failed"
                    )
                }
            )
        }
    }

    fun isFormValid(): Boolean {
        val state = _uiState.value
        return state.loginName.isNotBlank() &&
               state.password.isNotBlank() &&
               state.passwordConfirm.isNotBlank() &&
               state.displayName.isNotBlank()
    }
}
