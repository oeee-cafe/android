package cafe.oeee.ui.signup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.oeee.R

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SignupScreen(
    onSignupSuccess: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: SignupViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return SignupViewModel(context) as T
            }
        }
    )
    val uiState by viewModel.uiState.collectAsState()

    // Autofill setup
    val autofill = LocalAutofill.current
    val autofillTree = LocalAutofillTree.current

    val usernameAutofillNode = AutofillNode(
        autofillTypes = listOf(AutofillType.Username),
        onFill = { viewModel.updateLoginName(it) }
    )
    val passwordAutofillNode = AutofillNode(
        autofillTypes = listOf(AutofillType.Password),
        onFill = { viewModel.updatePassword(it) }
    )

    autofillTree.children[usernameAutofillNode.id] = usernameAutofillNode
    autofillTree.children[passwordAutofillNode.id] = passwordAutofillNode

    // Navigate to home when signup succeeds
    if (uiState.isSignupSuccess) {
        onSignupSuccess()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Title
            Text(
                text = stringResource(R.string.signup_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 48.dp)
            )

            // Signup Form
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Login Name field
                OutlinedTextField(
                    value = uiState.loginName,
                    onValueChange = { viewModel.updateLoginName(it) },
                    label = { Text(stringResource(R.string.signup_login_name)) },
                    singleLine = true,
                    enabled = !uiState.isLoading,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            usernameAutofillNode.boundingBox = coordinates.boundsInWindow()
                        }
                        .onFocusChanged { focusState ->
                            autofill?.run {
                                if (focusState.isFocused) {
                                    requestAutofillForNode(usernameAutofillNode)
                                } else {
                                    cancelAutofillForNode(usernameAutofillNode)
                                }
                            }
                        }
                )

                // Display Name field
                OutlinedTextField(
                    value = uiState.displayName,
                    onValueChange = { viewModel.updateDisplayName(it) },
                    label = { Text(stringResource(R.string.signup_display_name)) },
                    singleLine = true,
                    enabled = !uiState.isLoading,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Password field
                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = { viewModel.updatePassword(it) },
                    label = { Text(stringResource(R.string.signup_password)) },
                    singleLine = true,
                    enabled = !uiState.isLoading,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            passwordAutofillNode.boundingBox = coordinates.boundsInWindow()
                        }
                        .onFocusChanged { focusState ->
                            autofill?.run {
                                if (focusState.isFocused) {
                                    requestAutofillForNode(passwordAutofillNode)
                                } else {
                                    cancelAutofillForNode(passwordAutofillNode)
                                }
                            }
                        }
                )

                // Password Confirm field
                OutlinedTextField(
                    value = uiState.passwordConfirm,
                    onValueChange = { viewModel.updatePasswordConfirm(it) },
                    label = { Text(stringResource(R.string.signup_password_confirm)) },
                    singleLine = true,
                    enabled = !uiState.isLoading,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (viewModel.isFormValid() && !uiState.isLoading) {
                                viewModel.signup()
                            }
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Error message
                if (uiState.errorMessage != null) {
                    Text(
                        text = uiState.errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }

                // Signup button
                Button(
                    onClick = { viewModel.signup() },
                    enabled = viewModel.isFormValid() && !uiState.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.signup_button),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}
