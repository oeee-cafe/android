package cafe.oeee.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cafe.oeee.R
import cafe.oeee.data.model.auth.RequestEmailVerificationRequest
import cafe.oeee.data.model.auth.VerifyEmailCodeRequest
import cafe.oeee.data.remote.ApiClient
import cafe.oeee.data.remote.ApiConfig
import cafe.oeee.data.service.AuthService
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, coil.annotation.ExperimentalCoilApi::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToBannerManagement: () -> Unit = {}
) {
    val context = LocalContext.current
    val packageInfo = remember {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    val authService = AuthService.getInstance(context)
    val currentUser by authService.currentUser.collectAsState()
    val isAuthenticated by authService.isAuthenticated.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // Email verification string resources
    val emailVerifiedSuccess = stringResource(R.string.settings_email_success_verified)
    val emailCodeResent = stringResource(R.string.settings_email_success_code_resent)
    val emailVerificationFailed = stringResource(R.string.settings_email_error_verification_failed)
    val emailRequestFailed = stringResource(R.string.settings_email_error_request_failed)
    val emailResendFailed = stringResource(R.string.settings_email_error_resend_failed)
    val emailNetworkError = stringResource(R.string.settings_email_error_network)
    val developerModeEnabled = stringResource(R.string.settings_developer_mode_enabled)
    val developerModeDisabled = stringResource(R.string.settings_developer_mode_disabled)

    var isLoggingOut by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var deleteAccountPassword by remember { mutableStateOf("") }
    var deleteAccountError by remember { mutableStateOf<String?>(null) }
    var isClearingCache by remember { mutableStateOf(false) }
    var showCacheClearedMessage by remember { mutableStateOf(false) }
    var cacheSize by remember { mutableStateOf<Long?>(null) }

    // Developer mode state
    var tapCount by remember { mutableStateOf(0) }
    var isDeveloperMode by remember { mutableStateOf(ApiConfig.isDeveloperModeEnabled(context)) }
    var showDeveloperModeSnackbar by remember { mutableStateOf(false) }
    var customServerURL by remember { mutableStateOf(ApiConfig.getBaseUrl(context)) }
    var serverURLError by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Email verification state
    var showEmailInputDialog by remember { mutableStateOf(false) }
    var showVerificationCodeDialog by remember { mutableStateOf(false) }
    var isRequestingVerification by remember { mutableStateOf(false) }
    var isVerifyingCode by remember { mutableStateOf(false) }
    var isResendingCode by remember { mutableStateOf(false) }
    var emailVerificationError by remember { mutableStateOf<String?>(null) }
    var challengeId by remember { mutableStateOf<String?>(null) }
    var emailToVerify by remember { mutableStateOf<String?>(null) }
    var expiresInSeconds by remember { mutableStateOf(300) }

    // Calculate cache size on mount
    LaunchedEffect(Unit) {
        val diskCache = context.imageLoader.diskCache
        val memoryCacheSize = context.imageLoader.memoryCache?.size?.toLong() ?: 0L
        val diskCacheSize = diskCache?.size ?: 0L
        cacheSize = memoryCacheSize + diskCacheSize
    }

    // Show snackbar for developer mode
    LaunchedEffect(showDeveloperModeSnackbar) {
        if (showDeveloperModeSnackbar) {
            snackbarHostState.showSnackbar(developerModeEnabled)
            showDeveloperModeSnackbar = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Account section
            if (isAuthenticated) {
                currentUser?.let { user ->
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_account),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = user.displayName,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "@${user.loginName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Email verification section
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_email_verification),
                                style = MaterialTheme.typography.titleMedium
                            )

                            // Display current email status
                            user.email?.let { email ->
                                Text(
                                    text = email,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (user.emailVerifiedAt != null) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "✓",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = stringResource(R.string.settings_email_verified),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                } else {
                                    Text(
                                        text = stringResource(R.string.settings_email_not_verified),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            } ?: run {
                                Text(
                                    text = stringResource(R.string.settings_email_not_set),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    showEmailInputDialog = true
                                    emailVerificationError = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (user.email == null || user.emailVerifiedAt == null) {
                                        stringResource(R.string.settings_email_verify_button)
                                    } else {
                                        stringResource(R.string.settings_email_change_button)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Profile section
            if (isAuthenticated) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.profile_title),
                            style = MaterialTheme.typography.titleMedium
                        )

                        OutlinedButton(
                            onClick = onNavigateToBannerManagement,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.banner_management_title))
                        }
                    }
                }
            }

            // Cache section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_storage),
                        style = MaterialTheme.typography.titleMedium
                    )

                    // Display cache size
                    cacheSize?.let { size ->
                        val sizeText = when {
                            size < 1024 -> "$size B"
                            size < 1024 * 1024 -> "${size / 1024} KB"
                            else -> "${size / (1024 * 1024)} MB"
                        }
                        Text(
                            text = stringResource(R.string.settings_cache_size, sizeText),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (showCacheClearedMessage) {
                        Text(
                            text = stringResource(R.string.settings_cache_cleared),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            isClearingCache = true
                            coroutineScope.launch {
                                context.imageLoader.memoryCache?.clear()
                                context.imageLoader.diskCache?.clear()
                                isClearingCache = false
                                showCacheClearedMessage = true
                                cacheSize = 0L
                            }
                        },
                        enabled = !isClearingCache,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isClearingCache) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(stringResource(R.string.settings_clear_image_cache))
                        }
                    }
                }
            }

            // Advanced Settings (only visible in developer mode)
            if (isDeveloperMode) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_advanced),
                            style = MaterialTheme.typography.titleMedium
                        )

                        OutlinedTextField(
                            value = customServerURL,
                            onValueChange = { customServerURL = it },
                            label = { Text(stringResource(R.string.settings_server_url_label)) },
                            placeholder = { Text(stringResource(R.string.settings_server_url_placeholder)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = stringResource(R.string.settings_current_server_url, ApiConfig.getBaseUrl(context)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        serverURLError?.let { error ->
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val result = ApiConfig.setBaseUrl(context, customServerURL)
                                    result.fold(
                                        onSuccess = {
                                            serverURLError = "Server URL updated. Please restart the app."
                                        },
                                        onFailure = { e ->
                                            serverURLError = e.message
                                        }
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.save))
                            }

                            OutlinedButton(
                                onClick = {
                                    ApiConfig.resetToDefault(context)
                                    customServerURL = ApiConfig.getBaseUrl(context)
                                    serverURLError = "Server URL reset. Please restart the app."
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.reset))
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                // Disable developer mode and reset to default
                                ApiConfig.disableDeveloperMode(context)
                                isDeveloperMode = false
                                customServerURL = ""
                                serverURLError = null
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(developerModeDisabled)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(stringResource(R.string.settings_turn_off_developer_mode))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Delete account button (only shown when authenticated)
            if (isAuthenticated) {
                OutlinedButton(
                    onClick = {
                        showDeleteAccountDialog = true
                        deleteAccountPassword = ""
                        deleteAccountError = null
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_delete_account))
                }
            }

            // Logout button (only shown when authenticated)
            if (isAuthenticated) {
                Button(
                    onClick = {
                        isLoggingOut = true
                        coroutineScope.launch {
                            authService.logout()
                            isLoggingOut = false
                            onNavigateBack()
                        }
                    },
                    enabled = !isLoggingOut,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isLoggingOut) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onError
                        )
                    } else {
                        Text(stringResource(R.string.logout_button))
                    }
                }
            }

            // Version text with tap gesture for developer mode
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable {
                        tapCount++
                        if (tapCount >= 10 && !isDeveloperMode) {
                            isDeveloperMode = true
                            ApiConfig.setDeveloperMode(context, true)
                            showDeveloperModeSnackbar = true
                            tapCount = 0
                        }
                        // Reset tap count after 2 seconds of inactivity
                        coroutineScope.launch {
                            delay(2000)
                            if (tapCount < 10) {
                                tapCount = 0
                            }
                        }
                    }
                ) {
                    Text(
                        text = "oeee cafe",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Version ${packageInfo.versionName} (${packageInfo.longVersionCode})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Delete account confirmation dialog
        if (showDeleteAccountDialog) {
            AlertDialog(
                onDismissRequest = {
                    showDeleteAccountDialog = false
                    deleteAccountPassword = ""
                    deleteAccountError = null
                },
                title = { Text(stringResource(R.string.settings_delete_account_confirm)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.settings_delete_account_warning))
                        OutlinedTextField(
                            value = deleteAccountPassword,
                            onValueChange = { deleteAccountPassword = it },
                            label = { Text(stringResource(R.string.settings_password)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        deleteAccountError?.let { error ->
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (deleteAccountPassword.isEmpty()) {
                                deleteAccountError = context.getString(R.string.error_password_required)
                                return@Button
                            }
                            isDeletingAccount = true
                            coroutineScope.launch {
                                try {
                                    authService.deleteAccount(deleteAccountPassword)
                                    showDeleteAccountDialog = false
                                    onNavigateBack()
                                } catch (e: Exception) {
                                    deleteAccountError = e.message
                                    deleteAccountPassword = ""
                                } finally {
                                    isDeletingAccount = false
                                }
                            }
                        },
                        enabled = !isDeletingAccount,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        if (isDeletingAccount) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onError,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(stringResource(R.string.settings_delete_account))
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showDeleteAccountDialog = false
                            deleteAccountPassword = ""
                            deleteAccountError = null
                        }
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        // Email verification dialogs
        if (showEmailInputDialog) {
            EmailInputDialog(
                onDismiss = {
                    showEmailInputDialog = false
                    emailVerificationError = null
                },
                onRequestVerification = { email ->
                    isRequestingVerification = true
                    emailVerificationError = null
                    coroutineScope.launch {
                        try {
                            val response = ApiClient.apiService.requestEmailVerification(
                                RequestEmailVerificationRequest(email)
                            )
                            withContext(Dispatchers.Main) {
                                challengeId = response.challengeId
                                emailToVerify = email
                                expiresInSeconds = response.expiresInSeconds
                                showEmailInputDialog = false
                                showVerificationCodeDialog = true
                                isRequestingVerification = false
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                emailVerificationError = e.message ?: emailNetworkError
                                isRequestingVerification = false
                            }
                        }
                    }
                },
                isLoading = isRequestingVerification,
                error = emailVerificationError
            )
        }

        if (showVerificationCodeDialog && challengeId != null && emailToVerify != null) {
            EmailVerificationCodeDialog(
                email = emailToVerify!!,
                expiresInSeconds = expiresInSeconds,
                onDismiss = {
                    showVerificationCodeDialog = false
                    challengeId = null
                    emailToVerify = null
                    emailVerificationError = null
                },
                onVerify = { code ->
                    isVerifyingCode = true
                    emailVerificationError = null
                    coroutineScope.launch {
                        try {
                            // Server returns 204 No Content on success
                            ApiClient.apiService.verifyEmailCode(
                                VerifyEmailCodeRequest(
                                    challengeId = challengeId!!,
                                    token = code
                                )
                            )
                            withContext(Dispatchers.Main) {
                                // Update the current user's email and emailVerifiedAt fields locally
                                currentUser?.let { user ->
                                    authService.updateCurrentUser(
                                        user.copy(
                                            email = emailToVerify,
                                            emailVerifiedAt = java.time.Instant.now().toString()
                                        )
                                    )
                                }

                                isVerifyingCode = false
                                showVerificationCodeDialog = false
                                challengeId = null
                                emailToVerify = null
                                snackbarHostState.showSnackbar(emailVerifiedSuccess)
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                emailVerificationError = e.message ?: emailNetworkError
                                isVerifyingCode = false
                            }
                        }
                    }
                },
                onResend = {
                    isResendingCode = true
                    emailVerificationError = null
                    coroutineScope.launch {
                        try {
                            val response = ApiClient.apiService.requestEmailVerification(
                                RequestEmailVerificationRequest(emailToVerify!!)
                            )
                            withContext(Dispatchers.Main) {
                                challengeId = response.challengeId
                                expiresInSeconds = response.expiresInSeconds
                                snackbarHostState.showSnackbar(emailCodeResent)
                                isResendingCode = false
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                emailVerificationError = e.message ?: emailNetworkError
                                isResendingCode = false
                            }
                        }
                    }
                },
                isVerifying = isVerifyingCode,
                isResending = isResendingCode,
                error = emailVerificationError
            )
        }
    }
}
