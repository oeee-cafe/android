package cafe.oeee.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cafe.oeee.R
import kotlinx.coroutines.delay

@Composable
fun EmailInputDialog(
    onDismiss: () -> Unit,
    onRequestVerification: (String) -> Unit,
    isLoading: Boolean,
    error: String?
) {
    var email by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }

    // Get string resources at composable scope
    val emailRequiredError = stringResource(R.string.settings_email_error_required)
    val emailInvalidError = stringResource(R.string.settings_email_error_invalid)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_email_verification_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_email_verification_description))

                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        emailError = null
                    },
                    label = { Text(stringResource(R.string.settings_email_label)) },
                    placeholder = { Text("user@example.com") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    enabled = !isLoading,
                    isError = emailError != null || error != null,
                    modifier = Modifier.fillMaxWidth()
                )

                emailError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        email.isBlank() -> {
                            emailError = emailRequiredError
                        }
                        !email.contains("@") -> {
                            emailError = emailInvalidError
                        }
                        else -> {
                            emailError = null
                            onRequestVerification(email)
                        }
                    }
                },
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.settings_email_send_code))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun EmailVerificationCodeDialog(
    email: String,
    expiresInSeconds: Int,
    onDismiss: () -> Unit,
    onVerify: (String) -> Unit,
    onResend: () -> Unit,
    isVerifying: Boolean,
    isResending: Boolean,
    error: String?
) {
    var code by remember { mutableStateOf("") }
    var codeError by remember { mutableStateOf<String?>(null) }
    var timeRemaining by remember { mutableStateOf(expiresInSeconds) }

    // Get string resources at composable scope
    val codeLengthError = stringResource(R.string.settings_email_error_code_length)

    // Countdown timer
    LaunchedEffect(Unit) {
        while (timeRemaining > 0) {
            delay(1000)
            timeRemaining--
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_email_verify_code_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.settings_email_verify_code_description, email))

                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                            code = it
                            codeError = null
                        }
                    },
                    label = { Text(stringResource(R.string.settings_email_code_label)) },
                    placeholder = { Text("123456") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = !isVerifying,
                    isError = codeError != null || error != null,
                    modifier = Modifier.fillMaxWidth()
                )

                // Timer display
                val minutes = timeRemaining / 60
                val seconds = timeRemaining % 60
                Text(
                    text = if (timeRemaining > 0) {
                        stringResource(R.string.settings_email_code_expires, String.format("%d:%02d", minutes, seconds))
                    } else {
                        stringResource(R.string.settings_email_code_expired)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (timeRemaining > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                )

                codeError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                error?.let {
                    Text(
                        text = when(it) {
                            "TOKEN_MISMATCH" -> stringResource(R.string.settings_email_error_code_mismatch)
                            "TOKEN_EXPIRED" -> stringResource(R.string.settings_email_error_code_expired)
                            "CHALLENGE_NOT_FOUND" -> stringResource(R.string.settings_email_error_challenge_not_found)
                            else -> it
                        },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Resend button
                TextButton(
                    onClick = onResend,
                    enabled = !isResending && !isVerifying
                ) {
                    if (isResending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.settings_email_resend_code))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        code.length != 6 -> {
                            codeError = codeLengthError
                        }
                        else -> {
                            codeError = null
                            onVerify(code)
                        }
                    }
                },
                enabled = !isVerifying && code.length == 6
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.settings_email_verify))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isVerifying) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
