package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.auth.AuthResult as JanAuthResult
import com.example.data.local.BeneficiaryEntity
import com.example.data.local.TransactionEntity
import com.example.data.model.AirtimeNetworkPricing
import com.example.data.model.AuthResult
import com.example.data.model.InAppNotification
import com.example.data.model.NetworkProvider
import com.example.data.model.SupabaseUser
import com.example.data.model.UserApiKey
import com.example.data.model.ForumTopic
import com.example.data.model.ForumReply
import com.example.data.remote.PinChangeOutcome
import com.example.data.remote.PinVerifyOutcome
import com.example.data.remote.SupabaseRealtimeClient
import com.example.data.repository.VtuRepository
import com.example.util.NotificationHelper
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.FilterOperator
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random

data class PendingTransaction(
    val title: String,
    val serviceType: String,
    val provider: String,
    val recipient: String,
    val amount: Double,
    val discount: Double = 0.0,
    val details: String? = null,
    val customerName: String? = null,
    val planId: String? = null,
    val meterNumber: String? = null,
    val smartcardNumber: String? = null,
    val serviceId: String? = null
)

sealed class PinSetupCheckState {
    data object Idle : PinSetupCheckState()
    data object Checking : PinSetupCheckState()
    data object NeedsPinCreation : PinSetupCheckState()
    data object HasPin : PinSetupCheckState()
    data class Error(val message: String) : PinSetupCheckState()
}

class VtuViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VtuRepository(application)

    // Supabase Auth states
    val isLoggedIn: StateFlow<Boolean> = repository.isLoggedIn
    val currentUser: StateFlow<SupabaseUser?> = repository.currentUser
    val isSupabaseConfigured: Boolean get() = repository.authClient.isLiveConfigured
    val isGsubzLiveConfigured: Boolean get() = repository.gsubzClient.isLiveConfigured
    val isFlutterwaveLiveConfigured: Boolean get() = repository.flutterwaveClient.isLiveConfigured

    // Flutterwave Payment States
    private val _isFlutterwaveLoading = MutableStateFlow(false)
    val isFlutterwaveLoading = _isFlutterwaveLoading.asStateFlow()

    private val _flutterwavePendingRef = MutableStateFlow<String?>(null)
    val flutterwavePendingRef = _flutterwavePendingRef.asStateFlow()

    private val _flutterwavePaymentUrl = MutableStateFlow<String?>(null)
    val flutterwavePaymentUrl = _flutterwavePaymentUrl.asStateFlow()

    private val _flutterwaveError = MutableStateFlow<String?>(null)
    val flutterwaveError = _flutterwaveError.asStateFlow()

    private val _authLoading = MutableStateFlow(false)
    val authLoading = _authLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError = _authError.asStateFlow()

    private val _authSuccessMessage = MutableStateFlow<String?>(null)
    val authSuccessMessage = _authSuccessMessage.asStateFlow()

    // 2-Step Sign In Flow States (Password -> Verification Code)
    private val _signInStep = MutableStateFlow(1)
    val signInStep = _signInStep.asStateFlow()
    private val _pendingSignInEmail = MutableStateFlow("")
    val pendingSignInEmail = _pendingSignInEmail.asStateFlow()

    // 2-Step Sign Up Flow States (Registration -> Verification Code)
    private val _signUpStep = MutableStateFlow(1)
    val signUpStep = _signUpStep.asStateFlow()
    private val _pendingSignUpEmail = MutableStateFlow("")
    val pendingSignUpEmail = _pendingSignUpEmail.asStateFlow()
    private val _pendingSignUpFullName = MutableStateFlow("")
    val pendingSignUpFullName = _pendingSignUpFullName.asStateFlow()
    private val _pendingSignUpPhone = MutableStateFlow<String?>(null)
    val pendingSignUpPhone = _pendingSignUpPhone.asStateFlow()
    private val _pendingSignUpPassword = MutableStateFlow("")
    val pendingSignUpPassword = _pendingSignUpPassword.asStateFlow()

    // 2-Step Email Change Flow States (New Email -> Verification Code)
    private val _emailChangeStep = MutableStateFlow(1)
    val emailChangeStep = _emailChangeStep.asStateFlow()
    private val _pendingNewEmail = MutableStateFlow("")
    val pendingNewEmail = _pendingNewEmail.asStateFlow()

    // 2-Step Password Reset Flow States (Email -> Verification Code & New Password)
    private val _passwordResetStep = MutableStateFlow(1)
    val passwordResetStep = _passwordResetStep.asStateFlow()
    private val _pendingPasswordResetEmail = MutableStateFlow("")
    val pendingPasswordResetEmail = _pendingPasswordResetEmail.asStateFlow()

    val walletBalance: StateFlow<Double> = repository.walletBalance
    val cashbackBalance: StateFlow<Double> = repository.cashbackBalance
    val airtimePrices: StateFlow<Map<NetworkProvider, AirtimeNetworkPricing>> = repository.airtimePrices
    val biometricEnabled: StateFlow<Boolean> = repository.biometricEnabled
    val appLockEnabled: StateFlow<Boolean> = repository.appLockEnabled
    val notificationsEnabled: StateFlow<Boolean> = repository.notificationsEnabled
    val inAppNotifications: StateFlow<List<InAppNotification>> = repository.inAppNotifications

    val allTransactions: StateFlow<List<TransactionEntity>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentTransactions: StateFlow<List<TransactionEntity>> = repository.recentTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val beneficiaries: StateFlow<List<BeneficiaryEntity>> = repository.allBeneficiaries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // App Lock state
    private val _isAppUnlocked = MutableStateFlow(!repository.appLockEnabled.value)
    val isAppUnlocked = _isAppUnlocked.asStateFlow()

    // Active auth dialog
    private val _pendingTransactionForAuth = MutableStateFlow<PendingTransaction?>(null)
    val pendingTransactionForAuth = _pendingTransactionForAuth.asStateFlow()

    // Transaction PIN remote states (Supabase RPCs are the single source of truth)
    private val _pinSetupState = MutableStateFlow<PinSetupCheckState>(PinSetupCheckState.Idle)
    val pinSetupState = _pinSetupState.asStateFlow()

    private val _isPinActionLoading = MutableStateFlow(false)
    val isPinActionLoading = _isPinActionLoading.asStateFlow()

    private val _pinDialogError = MutableStateFlow<String?>(null)
    val pinDialogError = _pinDialogError.asStateFlow()

    // Transaction receipt sheet
    private val _activeReceipt = MutableStateFlow<TransactionEntity?>(null)
    val activeReceipt = _activeReceipt.asStateFlow()

    private var observedUserId: String? = repository.currentUser.value?.id

    init {
        currentUser.onEach { user ->
            val newId = user?.id
            if (observedUserId != newId) {
                observedUserId = newId
                resetTransientUserStates()
            }
        }.launchIn(viewModelScope)
    }

    fun resetTransientUserStates() {
        val ch = realtimeBalanceChannel
        realtimeBalanceChannel = null
        if (ch != null) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try { ch.unsubscribe() } catch (_: Throwable) {}
            }
        }
        _isFlutterwaveLoading.value = false
        _flutterwavePendingRef.value = null
        _flutterwavePaymentUrl.value = null
        _flutterwaveError.value = null
        _pendingTransactionForAuth.value = null
        _activeReceipt.value = null
        _inAppAlertBanner.value = null
        _showFundWalletSheet.value = false
        _apiKeyStatusMessage.value = null
        _isGeneratingApiKey.value = false
        _isLoadingApiKeyState.value = false
        _emailChangeStep.value = 1
        _pendingNewEmail.value = ""
    }

    val supabaseAnonKey: String
        get() = repository.supabaseAnonKey

    val currentAccessToken: String?
        get() = repository.currentAccessToken

    fun refreshRemoteBalance() {
        viewModelScope.launch {
            repository.syncRemoteProfileBalance()
        }
    }

    fun refreshAirtimePrices() {
        viewModelScope.launch {
            repository.refreshAirtimePrices()
        }
    }

    fun refreshWalletRealtime(onDone: ((Double) -> Unit)? = null) {
        viewModelScope.launch {
            repository.syncRemoteProfileBalance()
            val bal = repository.walletBalance.value
            onDone?.invoke(bal)
        }
    }

    fun createDynamicVirtualAccount(
        userId: String? = null,
        email: String? = null,
        amount: Double = 1000.0,
        onComplete: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val res = repository.createDynamicVirtualAccount(userId, email, amount)
            res.onSuccess { data ->
                _uiMessage.value = "Dynamic virtual account created: ${data.accountNumber}"
                onComplete(true, data.accountNumber)
            }.onFailure { err ->
                _uiMessage.value = err.localizedMessage ?: "Failed to create dynamic account"
                onComplete(false, err.localizedMessage)
            }
        }
    }

    fun updateUserVirtualAccount(
        accountNumber: String,
        bankName: String,
        accountName: String? = null,
        nin: String? = null
    ) {
        repository.updateUserVirtualAccount(accountNumber, bankName, accountName, nin)
        _uiMessage.value = "Virtual Account active: $accountNumber"
    }

    fun updateDynamicAccount(
        accountNumber: String,
        bankName: String,
        accountName: String? = null,
        amount: Double? = null
    ) {
        repository.updateDynamicVirtualAccount(accountNumber, bankName, accountName, amount)
        _uiMessage.value = "Dynamic Account active: $accountNumber"
    }

    fun clearUserVirtualAccount(userId: String) {
        repository.clearUserVirtualAccount(userId)
        _uiMessage.value = "Virtual Account reset. You can now re-link with a verified NIN."
    }

    fun setLiveWalletBalance(balance: Double) {
        repository.setWalletBalance(balance)
    }

    fun updateRemoteUserProfile(phone: String?, fullName: String? = null) {
        repository.updateRemoteUserProfile(phone, fullName)
    }

    private var realtimeBalanceChannel: io.github.jan.supabase.realtime.RealtimeChannel? = null

    fun subscribeToWalletBalance(userId: String, onBalanceChanged: (Double) -> Unit = {}) {
        if (userId.isBlank() || userId == "usr_guest" || userId == "usr_default") return
        if (!SupabaseRealtimeClient.isConfigured) return
        try {
            val channel = SupabaseRealtimeClient.client.channel("wallet-balance-$userId-${System.nanoTime()}")
            realtimeBalanceChannel = channel

            val flow = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                table = "users"
                filter("id", FilterOperator.EQ, userId)
            }

            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    flow.collect { action ->
                        val newBalance = action.record["wallet_balance"]?.jsonPrimitive?.doubleOrNull
                        if (newBalance != null) {
                            setLiveWalletBalance(newBalance)
                            onBalanceChanged(newBalance)
                        }
                    }
                } catch (e: Throwable) {
                    Log.w("VtuViewModel", "Supabase realtime flow ended: ${e.message}")
                }
            }

            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    channel.subscribe()
                } catch (e: Throwable) {
                    Log.w("VtuViewModel", "Supabase realtime channel subscription error: ${e.message}")
                }
            }
        } catch (e: Throwable) {
            Log.w("VtuViewModel", "Error subscribing to wallet balance: ${e.message}")
        }
    }

    // Real-time banner alert
    private val _inAppAlertBanner = MutableStateFlow<InAppNotification?>(null)
    val inAppAlertBanner = _inAppAlertBanner.asStateFlow()

    // Fund Wallet dialog
    private val _showFundWalletSheet = MutableStateFlow(false)
    val showFundWalletSheet = _showFundWalletSheet.asStateFlow()

    // Toast or snackbar messages
    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage = _uiMessage.asStateFlow()

    fun dismissUiMessage() {
        _uiMessage.value = null
    }

    fun openFundWalletSheet() {
        _showFundWalletSheet.value = true
    }

    fun closeFundWalletSheet() {
        _showFundWalletSheet.value = false
    }

    fun dismissReceipt() {
        _activeReceipt.value = null
    }

    fun deleteTransaction(transaction: TransactionEntity, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction.id)
            if (_activeReceipt.value?.id == transaction.id) {
                _activeReceipt.value = null
            }
            _uiMessage.value = "Transaction removed from history."
            onComplete()
        }
    }

    fun deleteTransactionById(id: Long, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteTransaction(id)
            if (_activeReceipt.value?.id == id) {
                _activeReceipt.value = null
            }
            _uiMessage.value = "Transaction removed from history."
            onComplete()
        }
    }

    fun clearAllTransactions(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.clearAllTransactions()
            _activeReceipt.value = null
            _uiMessage.value = "All transaction history cleared."
            onComplete()
        }
    }

    fun dismissAlertBanner() {
        _inAppAlertBanner.value = null
    }

    fun dismissAuthDialog() {
        _pendingTransactionForAuth.value = null
        _pinDialogError.value = null
    }

    fun clearPinDialogError() {
        _pinDialogError.value = null
    }

    fun unlockApp() {
        _isAppUnlocked.value = true
    }

    fun lockApp() {
        if (appLockEnabled.value) {
            _isAppUnlocked.value = false
        }
    }

    fun toggleBiometric(enabled: Boolean) {
        repository.setBiometricEnabled(enabled)
    }

    fun toggleAppLock(enabled: Boolean) {
        repository.setAppLockEnabled(enabled)
        if (!enabled) {
            _isAppUnlocked.value = true
        }
    }

    fun toggleNotifications(enabled: Boolean) {
        repository.setNotificationsEnabled(enabled)
    }

    /**
     * Calls Supabase RPC has_transaction_pin ({}) after login or on app start.
     * - false -> NeedsPinCreation (navigate to Create PIN screen)
     * - true  -> HasPin (go straight to dashboard; never ask a user with a PIN to create one again)
     */
    fun checkHasTransactionPin(onResult: ((Boolean) -> Unit)? = null) {
        if (!isLoggedIn.value) return
        viewModelScope.launch {
            _pinSetupState.value = PinSetupCheckState.Checking
            val res = repository.hasTransactionPin()
            res.onSuccess { hasPin ->
                _pinSetupState.value = if (hasPin) {
                    PinSetupCheckState.HasPin
                } else {
                    PinSetupCheckState.NeedsPinCreation
                }
                onResult?.invoke(hasPin)
            }.onFailure { err ->
                _pinSetupState.value = PinSetupCheckState.Error(
                    err.localizedMessage ?: "Unable to verify PIN status. Please check your connection."
                )
            }
        }
    }

    /**
     * Calls Supabase RPC set_transaction_pin with {"p_pin": "<4-digit-pin>"}.
     */
    fun createTransactionPin(
        pin: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isPinActionLoading.value = true
            val res = repository.setTransactionPin(pin)
            _isPinActionLoading.value = false
            res.onSuccess {
                _pinSetupState.value = PinSetupCheckState.HasPin
                _uiMessage.value = "Transaction PIN created"
                onSuccess()
            }.onFailure { err ->
                val msg = err.localizedMessage ?: "Failed to create PIN"
                onError(msg)
            }
        }
    }

    /**
     * Calls Supabase RPC change_transaction_pin with {"p_old": "<old>", "p_new": "<new>"}.
     * - false: "Old PIN is wrong"
     * - true: "PIN changed"
     * - locked: "Too many wrong attempts. Try again in 15 minutes."
     */
    fun changeTransactionPin(
        oldPin: String,
        newPin: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            _isPinActionLoading.value = true
            val outcome = repository.changeTransactionPin(oldPin = oldPin, newPin = newPin)
            _isPinActionLoading.value = false
            when (outcome) {
                is PinChangeOutcome.Changed -> {
                    _uiMessage.value = "PIN changed"
                    onResult(true, "PIN changed")
                }
                is PinChangeOutcome.WrongOldPin -> {
                    onResult(false, "Old PIN is wrong")
                }
                is PinChangeOutcome.Locked -> {
                    onResult(false, "Too many wrong attempts. Try again in 15 minutes.")
                }
                is PinChangeOutcome.Error -> {
                    val msg = if (outcome.message.contains("locked", ignoreCase = true)) {
                        "Too many wrong attempts. Try again in 15 minutes."
                    } else {
                        outcome.message
                    }
                    onResult(false, msg)
                }
            }
        }
    }

    /**
     * Verifies the 4-digit transaction PIN via Supabase RPC verify_transaction_pin.
     */
    fun verifyTransactionPinAsync(
        pin: String,
        onResult: (PinVerifyOutcome) -> Unit
    ) {
        viewModelScope.launch {
            _isPinActionLoading.value = true
            val outcome = repository.verifyTransactionPin(pin)
            _isPinActionLoading.value = false
            onResult(outcome)
        }
    }

    fun clearAuthMessages() {
        _authError.value = null
        _authSuccessMessage.value = null
    }

    fun loginWithSupabase(
        email: String,
        pass: String,
        onSuccess: () -> Unit = {}
    ) {
        if (email.isBlank() || pass.isBlank()) {
            _authError.value = "Please enter both email and password"
            return
        }
        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            _authSuccessMessage.value = null

            val result = repository.login(email.trim(), pass)
            _authLoading.value = false

            when (result) {
                is AuthResult.Success -> {
                    _authSuccessMessage.value = "Welcome back, ${result.user.fullName}!"
                    _uiMessage.value = "Signed in as ${result.user.email}"
                    onSuccess()
                }
                is AuthResult.RequiresEmailConfirmation -> {
                    _authSuccessMessage.value = "Please confirm your email (${result.email}) before logging in."
                }
                is AuthResult.Error -> {
                    _authError.value = result.message
                }
            }
        }
    }

    fun signUpWithSupabase(
        email: String,
        pass: String,
        fullName: String,
        phone: String?,
        onSuccess: () -> Unit = {}
    ) {
        if (email.isBlank() || pass.isBlank() || fullName.isBlank()) {
            _authError.value = "Please fill in all required fields"
            return
        }
        if (pass.length < 6) {
            _authError.value = "Password must be at least 6 characters"
            return
        }
        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            _authSuccessMessage.value = null

            val result = repository.signUp(email.trim(), pass, fullName.trim(), phone?.trim())
            _authLoading.value = false

            when (result) {
                is AuthResult.Success -> {
                    _authSuccessMessage.value = "Account created successfully!"
                    _uiMessage.value = "Welcome to Daniel VTU, ${result.user.fullName}!"
                    onSuccess()
                }
                is AuthResult.RequiresEmailConfirmation -> {
                    _authSuccessMessage.value = "Account created! Please check ${result.email} to verify your email."
                }
                is AuthResult.Error -> {
                    _authError.value = result.message
                }
            }
        }
    }

    fun logoutFromSupabase(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _authLoading.value = true
            try {
                repository.logout()
            } catch (_: Throwable) {}
            resetTransientUserStates()
            _signInStep.value = 1
            _signUpStep.value = 1
            _passwordResetStep.value = 1
            _pendingSignInEmail.value = ""
            _pendingSignUpEmail.value = ""
            _pendingSignUpFullName.value = ""
            _pendingSignUpPhone.value = null
            _pendingSignUpPassword.value = ""
            _pendingPasswordResetEmail.value = ""
            _authError.value = null
            _authSuccessMessage.value = null
            _isAppUnlocked.value = true
            _authLoading.value = false
            _uiMessage.value = "Logged out successfully"
            onComplete()
        }
    }

    fun sendPasswordResetEmail(email: String, onSuccess: () -> Unit = {}) {
        if (email.isBlank() || !email.contains("@")) {
            _authError.value = "Please enter a valid email address."
            return
        }
        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            _authSuccessMessage.value = null

            val result = repository.sendPasswordResetEmail(email.trim())
            _authLoading.value = false

            when (result) {
                is AuthResult.Success -> {
                    _authSuccessMessage.value = result.message
                    _uiMessage.value = result.message
                    onSuccess()
                }
                is AuthResult.Error -> {
                    _authError.value = result.message
                }
                is AuthResult.RequiresEmailConfirmation -> {
                    _authSuccessMessage.value = "Verification code sent to ${result.email}"
                    onSuccess()
                }
            }
        }
    }

    fun resetPasswordWithOtp(
        email: String,
        otpToken: String,
        newPass: String,
        onSuccess: () -> Unit = {}
    ) {
        if (email.isBlank() || otpToken.isBlank() || newPass.isBlank()) {
            _authError.value = "Please complete all fields (Email, Code, New Password)."
            return
        }
        if (newPass.length < 6) {
            _authError.value = "Password must be at least 6 characters."
            return
        }
        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            _authSuccessMessage.value = null

            val result = repository.resetPasswordWithOtp(email.trim(), otpToken.trim(), newPass)
            _authLoading.value = false

            when (result) {
                is AuthResult.Success -> {
                    _authSuccessMessage.value = result.message
                    _uiMessage.value = "Password changed! Welcome back."
                    onSuccess()
                }
                is AuthResult.Error -> {
                    _authError.value = result.message
                }
                is AuthResult.RequiresEmailConfirmation -> {
                    _authSuccessMessage.value = "Password updated. Check ${result.email} for verification."
                    onSuccess()
                }
            }
        }
    }

    fun updateEmail(newEmail: String, onSuccess: () -> Unit = {}) {
        if (newEmail.isBlank() || !newEmail.contains("@")) {
            _authError.value = "Please enter a valid new email address."
            return
        }
        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            _authSuccessMessage.value = null

            val result = repository.updateEmail(newEmail.trim())
            _authLoading.value = false

            when (result) {
                is AuthResult.Success -> {
                    _authSuccessMessage.value = result.message
                    _uiMessage.value = "Email address successfully updated!"
                    onSuccess()
                }
                is AuthResult.Error -> {
                    _authError.value = result.message
                }
                is AuthResult.RequiresEmailConfirmation -> {
                    _authSuccessMessage.value = "Please verify your new email address: ${result.email}"
                    onSuccess()
                }
            }
        }
    }

    // =========================================================================
    // SUPABASE AUTH MANAGER 4-FLOW IMPLEMENTATION (auth-kt)
    // 1) Sign Up + email verification code
    // 2) Sign In with password + verification code before session starts
    // 3) Password Reset + verification code
    // 4) Email Address Change + verification code
    // =========================================================================

    // 1) Sign Up Flow
    fun authKtSignUp(
        email: String,
        pass: String,
        fullName: String,
        phone: String?,
        onCodeRequired: () -> Unit = {}
    ) {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank() || pass.isBlank() || fullName.isBlank()) {
            _authError.value = "Please fill in all required fields."
            return
        }
        if (pass.length < 6) {
            _authError.value = "Password must be at least 6 characters."
            return
        }
        if (!repository.isLiveConfigured && repository.isEmailRegistered(cleanEmail)) {
            _signUpStep.value = 1
            _authError.value = "This email address has already been registered."
            return
        }
        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            _authSuccessMessage.value = null

            _pendingSignUpEmail.value = cleanEmail
            _pendingSignUpPassword.value = pass
            _pendingSignUpFullName.value = fullName.trim()
            _pendingSignUpPhone.value = phone?.trim()

            val result = repository.authKtSignUp(cleanEmail, pass)
            _authLoading.value = false

            when (result) {
                is JanAuthResult.Success -> {
                    _signUpStep.value = 2
                    _authSuccessMessage.value = "Verification code sent to $cleanEmail. Enter the 6-digit code to complete sign up."
                    onCodeRequired()
                }
                is JanAuthResult.Error -> {
                    _signUpStep.value = 1
                    _authError.value = result.message
                }
            }
        }
    }

    fun authKtVerifySignUpCode(code: String, onSuccess: () -> Unit = {}) {
        if (code.isBlank() || code.length < 4) {
            _authError.value = "Please enter the valid verification code."
            return
        }
        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            _authSuccessMessage.value = null

            val email = _pendingSignUpEmail.value
            val fullName = _pendingSignUpFullName.value
            val phone = _pendingSignUpPhone.value
            val password = _pendingSignUpPassword.value

            val result = repository.authKtVerifySignUpCode(email, code.trim(), fullName, phone, password)
            _authLoading.value = false

            when (result) {
                is JanAuthResult.Success -> {
                    _signUpStep.value = 1
                    _authSuccessMessage.value = "Account verified! Welcome to Daniel VTU."
                    _uiMessage.value = "Welcome, $fullName!"
                    onSuccess()
                }
                is JanAuthResult.Error -> {
                    _authError.value = result.message
                }
            }
        }
    }

    fun generateDedicatedVirtualAccount(
        onSuccess: (com.example.data.remote.FlutterwaveVirtualAccount) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isFlutterwaveLoading.value = true
            _flutterwaveError.value = null
            try {
                val va = repository.generateAndAssignVirtualAccount()
                _uiMessage.value = "Flutterwave account generated: ${va.accountNumber} (${va.bankName})"
                onSuccess(va)
            } catch (e: Exception) {
                val err = e.localizedMessage ?: "Failed to generate virtual account"
                _flutterwaveError.value = err
                onError(err)
            } finally {
                _isFlutterwaveLoading.value = false
            }
        }
    }

    fun cancelSignUpCode() {
        _signUpStep.value = 1
        _authError.value = null
    }

    // 2) Sign In Flow (Step 1: check password & email code -> Step 2: verify code to start session)
    fun authKtSignIn(
        email: String,
        pass: String,
        onCodeRequired: () -> Unit = {}
    ) {
        if (email.isBlank() || pass.isBlank()) {
            _authError.value = "Please enter both email and password."
            return
        }
        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            _authSuccessMessage.value = null

            val result = repository.authKtSignIn(email.trim(), pass)
            _authLoading.value = false

            when (result) {
                is JanAuthResult.Success -> {
                    _pendingSignInEmail.value = email.trim()
                    _signInStep.value = 2
                    _authSuccessMessage.value = "Password confirmed! We sent a 6-digit verification code to ${email.trim()}."
                    onCodeRequired()
                }
                is JanAuthResult.Error -> {
                    _authError.value = result.message
                }
            }
        }
    }

    fun authKtVerifySignInCode(code: String, onSuccess: () -> Unit = {}) {
        if (code.isBlank() || code.length < 4) {
            _authError.value = "Please enter the verification code sent to your email."
            return
        }
        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            _authSuccessMessage.value = null

            val email = _pendingSignInEmail.value
            val result = repository.authKtVerifySignInCode(email, code.trim())
            _authLoading.value = false

            when (result) {
                is JanAuthResult.Success -> {
                    _signInStep.value = 1
                    _authSuccessMessage.value = "Code verified successfully! Welcome back."
                    _uiMessage.value = "Signed in as $email"
                    onSuccess()
                }
                is JanAuthResult.Error -> {
                    _authError.value = result.message
                }
            }
        }
    }

    fun cancelSignInCode() {
        _signInStep.value = 1
        _authError.value = null
    }

    // 3) Password Reset Flow (request code -> verify code & set new password)
    fun setPasswordResetStep(step: Int) {
        _passwordResetStep.value = step
    }

    fun cancelPasswordResetCode() {
        _passwordResetStep.value = 1
        _authError.value = null
    }

    fun authKtRequestPasswordReset(email: String, onCodeSent: () -> Unit = {}) {
        if (email.isBlank() || !email.contains("@")) {
            _authError.value = "Please enter a valid email address."
            return
        }
        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            _authSuccessMessage.value = null

            val result = repository.authKtRequestPasswordReset(email.trim())
            _authLoading.value = false

            when (result) {
                is JanAuthResult.Success -> {
                    _pendingPasswordResetEmail.value = email.trim()
                    _passwordResetStep.value = 2
                    _authSuccessMessage.value = "We sent a 6-digit recovery code to ${email.trim()}."
                    onCodeSent()
                }
                is JanAuthResult.Error -> {
                    _authError.value = result.message
                }
            }
        }
    }

    fun authKtVerifyPasswordResetCode(
        email: String,
        code: String,
        onVerified: () -> Unit = {}
    ) {
        if (code.isBlank() || code.length < 4) {
            _authError.value = "Please enter the 6-digit verification code."
            return
        }
        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            _authSuccessMessage.value = null

            val result = repository.authKtVerifyPasswordResetCode(email.trim(), code.trim())
            _authLoading.value = false

            when (result) {
                is JanAuthResult.Success -> {
                    _passwordResetStep.value = 3
                    _authSuccessMessage.value = "Code verified! Please set your new password below."
                    onVerified()
                }
                is JanAuthResult.Error -> {
                    _authError.value = result.message
                }
            }
        }
    }

    fun authKtSetNewPassword(
        newPass: String,
        onSuccess: () -> Unit = {}
    ) {
        if (newPass.length < 6) {
            _authError.value = "New password must be at least 6 characters."
            return
        }
        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            _authSuccessMessage.value = null

            val result = repository.authKtSetNewPassword(newPass)
            _authLoading.value = false

            when (result) {
                is JanAuthResult.Success -> {
                    _passwordResetStep.value = 1
                    _authSuccessMessage.value = "Password updated successfully! You can now sign in."
                    _uiMessage.value = "Password reset complete!"
                    onSuccess()
                }
                is JanAuthResult.Error -> {
                    _authError.value = result.message
                }
            }
        }
    }

    fun authKtVerifyResetAndSetNewPassword(
        email: String,
        code: String,
        newPass: String,
        onSuccess: () -> Unit = {}
    ) {
        if (email.isBlank() || code.isBlank() || newPass.isBlank()) {
            _authError.value = "Please fill in Email, Verification Code, and New Password."
            return
        }
        if (newPass.length < 6) {
            _authError.value = "New password must be at least 6 characters."
            return
        }
        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            _authSuccessMessage.value = null

            val result = repository.authKtVerifyResetAndSetNewPassword(email.trim(), code.trim(), newPass)
            _authLoading.value = false

            when (result) {
                is JanAuthResult.Success -> {
                    _passwordResetStep.value = 1
                    _authSuccessMessage.value = "Password updated successfully! You can now sign in."
                    _uiMessage.value = "Password reset complete!"
                    onSuccess()
                }
                is JanAuthResult.Error -> {
                    _authError.value = result.message
                }
            }
        }
    }

    // 4) Email Address Change Flow (request change -> verify code)
    fun authKtRequestEmailChange(newEmail: String, onCodeSent: () -> Unit = {}) {
        val cleanEmail = newEmail.trim().lowercase()
        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            _authError.value = "Please enter a valid email address."
            return
        }
        val currentEmail = currentUser.value?.email?.trim()?.lowercase()
        if (cleanEmail == currentEmail) {
            _authError.value = "The new email address is the same as your current email address."
            return
        }
        if (repository.isEmailRegistered(cleanEmail)) {
            _authError.value = "This email address has already been registered."
            return
        }
        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            _authSuccessMessage.value = null

            val result = repository.authKtRequestEmailChange(cleanEmail)
            _authLoading.value = false

            when (result) {
                is JanAuthResult.Success -> {
                    _pendingNewEmail.value = cleanEmail
                    _emailChangeStep.value = 2
                    _authSuccessMessage.value = "Verification code sent to $cleanEmail! Please verify below."
                    onCodeSent()
                }
                is JanAuthResult.Error -> {
                    _authError.value = result.message
                }
            }
        }
    }

    fun authKtVerifyEmailChange(code: String, onSuccess: () -> Unit = {}) {
        if (code.isBlank() || code.length < 4) {
            _authError.value = "Please enter the verification code sent to your new email."
            return
        }
        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            _authSuccessMessage.value = null

            val newEmail = _pendingNewEmail.value
            val result = repository.authKtVerifyEmailChange(newEmail, code.trim())
            _authLoading.value = false

            when (result) {
                is JanAuthResult.Success -> {
                    repository.markEmailAsRegistered(newEmail)
                    _emailChangeStep.value = 1
                    _authSuccessMessage.value = "Email successfully changed to $newEmail!"
                    _uiMessage.value = "Email updated to $newEmail"
                    onSuccess()
                }
                is JanAuthResult.Error -> {
                    _authError.value = result.message
                }
            }
        }
    }

    fun cancelEmailChangeCode() {
        _emailChangeStep.value = 1
        _authError.value = null
    }

    fun deleteAccount(onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            try {
                val result = repository.deleteAccount()
                if (result is com.example.auth.AuthResult.Error) {
                    Log.e("VtuViewModel", "delete_user error: ${result.message}")
                    _authError.value = result.message
                    _uiMessage.value = result.message
                } else {
                    resetTransientUserStates()
                    _isAppUnlocked.value = true
                    _authSuccessMessage.value = "Account deleted successfully."
                    _uiMessage.value = "Account deleted successfully."
                    onSuccess()
                }
            } catch (e: Exception) {
                Log.e("VtuViewModel", "delete_user error: ${e.message}", e)
                _authError.value = e.message ?: "Failed to delete account"
                _uiMessage.value = e.message ?: "Failed to delete account"
            } finally {
                _authLoading.value = false
            }
        }
    }

    /**
     * Withdraw/debit feature:
     * Requires 4-digit transaction PIN verification via Supabase verify_transaction_pin
     * before calling repository.withdrawFromWallet (Supabase RPC debit_wallet).
     */
    private var pendingWithdrawalSuccessCallback: (() -> Unit)? = null

    fun withdrawWallet(amount: Double, context: Context, onSuccess: () -> Unit = {}) {
        if (amount <= 0.0) {
            Toast.makeText(context, "Please enter a valid withdrawal amount", Toast.LENGTH_SHORT).show()
            return
        }
        val currentBal = walletBalance.value
        if (amount > currentBal) {
            Toast.makeText(context, "Insufficient balance. Available: ₦%,.2f".format(currentBal), Toast.LENGTH_LONG).show()
            return
        }

        pendingWithdrawalSuccessCallback = onSuccess
        _pinDialogError.value = null
        val user = currentUser.value
        val destination = user?.virtualAccountNumber
            ?: user?.email
            ?: "Linked Account"
        _pendingTransactionForAuth.value = PendingTransaction(
            title = "Wallet Withdrawal",
            serviceType = "WITHDRAWAL",
            provider = "Wallet Withdrawal",
            recipient = destination,
            amount = amount,
            discount = 0.0,
            details = "Withdraw ₦%,.2f from wallet".format(amount),
            customerName = user?.fullName
        )
    }

    private fun executeWithdrawalInternal(pending: PendingTransaction, onComplete: () -> Unit) {
        val amount = pending.amount
        val appContext = getApplication<Application>()
        viewModelScope.launch {
            _authLoading.value = true
            try {
                val result = repository.withdrawFromWallet(amount)
                if (result.isSuccess) {
                    val newBal = result.getOrNull() ?: walletBalance.value
                    val dateStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
                    val reference = "WDR-$dateStr-${(100000..999999).random()}"
                    val activeUid = currentUser.value?.id?.trim().orEmpty()
                    val entity = TransactionEntity(
                        userId = activeUid,
                        reference = reference,
                        serviceType = "WITHDRAWAL",
                        provider = pending.provider,
                        recipient = pending.recipient,
                        amount = amount,
                        discountOrCashback = 0.0,
                        status = "SUCCESSFUL",
                        timestamp = System.currentTimeMillis(),
                        tokenOrDetails = "Wallet Withdrawal • New Balance: ₦%,.2f".format(newBal),
                        customerName = pending.customerName
                    )
                    repository.recordTransaction(entity)
                    Toast.makeText(appContext, "₦%,.2f withdrawn successfully. New balance: ₦%,.2f".format(amount, newBal), Toast.LENGTH_LONG).show()
                    _uiMessage.value = "₦%,.2f withdrawn successfully".format(amount)
                    _activeReceipt.value = entity
                    pendingWithdrawalSuccessCallback?.invoke()
                    pendingWithdrawalSuccessCallback = null
                    onComplete()
                } else {
                    val err = sanitizeUserFacingError(result.exceptionOrNull()?.message)
                    Toast.makeText(appContext, err, Toast.LENGTH_LONG).show()
                    _uiMessage.value = err
                }
            } catch (e: Exception) {
                val err = sanitizeUserFacingError(e.message)
                Toast.makeText(appContext, err, Toast.LENGTH_LONG).show()
                _uiMessage.value = err
            } finally {
                _authLoading.value = false
            }
        }
    }

    /**
     * Refund feature:
     * Executes the server refund RPC and refreshes wallet_balance from public.users.
     */
    fun refundWallet(
        amount: Double,
        context: Context,
        reason: String = "Refund deduction",
        targetUserId: String? = null,
        onSuccess: (Double) -> Unit = {}
    ) {
        if (amount <= 0) {
            Toast.makeText(context, "Please enter a valid refund amount", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            _authLoading.value = true
            try {
                val result = repository.refundFromWallet(amount, targetUserId, reason)
                if (result.isSuccess) {
                    val remainder = result.getOrNull() ?: walletBalance.value
                    Toast.makeText(context, "₦%,.2f refunded. New balance: ₦%,.2f".format(amount, remainder), Toast.LENGTH_LONG).show()
                    _uiMessage.value = "₦%,.2f refunded. Remainder: ₦%,.2f".format(amount, remainder)
                    onSuccess(remainder)
                } else {
                    val err = sanitizeUserFacingError(result.exceptionOrNull()?.message)
                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                    _uiMessage.value = err
                }
            } catch (e: Exception) {
                val err = sanitizeUserFacingError(e.message)
                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                _uiMessage.value = err
            } finally {
                _authLoading.value = false
            }
        }
    }

    fun prepareTransaction(pending: PendingTransaction) {
        _pinDialogError.value = null
        _pendingTransactionForAuth.value = pending
    }

    fun authorizePendingWithBiometric(onComplete: () -> Unit = {}) {
        val pending = _pendingTransactionForAuth.value ?: return
        _pendingTransactionForAuth.value = null
        _pinDialogError.value = null
        if (pending.serviceType.equals("WITHDRAWAL", ignoreCase = true)) {
            executeWithdrawalInternal(pending, onComplete)
        } else {
            executeTransactionInternal(pending, onComplete)
        }
    }

    /**
     * Verifies the 4-digit PIN via Supabase RPC verify_transaction_pin before every purchase
     * (airtime, data, cable TV, electricity, exam pins) and every withdrawal.
     * - true: continue with the transaction.
     * - false: show "Wrong PIN".
     * - error message containing "locked": show "Too many wrong attempts. Try again in 15 minutes."
     * Never skips this check and never accepts a default PIN.
     */
    fun verifyPinAndExecutePending(pin: String, onComplete: () -> Unit = {}) {
        val pending = _pendingTransactionForAuth.value ?: return
        viewModelScope.launch {
            _isPinActionLoading.value = true
            _pinDialogError.value = null
            val outcome = repository.verifyTransactionPin(pin)
            _isPinActionLoading.value = false

            when (outcome) {
                is PinVerifyOutcome.Verified -> {
                    _pendingTransactionForAuth.value = null
                    _pinDialogError.value = null
                    if (pending.serviceType.equals("WITHDRAWAL", ignoreCase = true)) {
                        executeWithdrawalInternal(pending, onComplete)
                    } else {
                        executeTransactionInternal(pending, onComplete)
                    }
                }
                is PinVerifyOutcome.WrongPin -> {
                    _pinDialogError.value = "Wrong PIN"
                }
                is PinVerifyOutcome.Locked -> {
                    _pinDialogError.value = "Too many wrong attempts. Try again in 15 minutes."
                }
                is PinVerifyOutcome.Error -> {
                    _pinDialogError.value = if (outcome.message.contains("locked", ignoreCase = true)) {
                        "Too many wrong attempts. Try again in 15 minutes."
                    } else {
                        outcome.message
                    }
                }
            }
        }
    }

    private fun executeTransactionInternal(pending: PendingTransaction, onComplete: () -> Unit) {
        viewModelScope.launch {
            _authLoading.value = true
            val gsubzResult = try {
                repository.executeGsubzVtu(
                    serviceType = pending.serviceType,
                    provider = pending.provider,
                    recipient = pending.recipient,
                    amount = pending.amount,
                    planId = pending.planId,
                    meterNumber = pending.meterNumber,
                    smartcardNumber = pending.smartcardNumber,
                    serviceIdOverride = pending.serviceId
                )
            } finally {
                _authLoading.value = false
                repository.syncRemoteProfileBalance()
            }

            if (!gsubzResult.isSuccess && !gsubzResult.isPending) {
                val errMsg = gsubzResult.message.trim().ifBlank { "Transaction failed" }
                _uiMessage.value = errMsg
                Toast.makeText(getApplication(), errMsg, Toast.LENGTH_LONG).show()
                val activeUid = currentUser.value?.id?.trim().orEmpty()
                val cleanRef = gsubzResult.transactionId.replace(Regex("GSUBZ-ERR-|GSUBZ-", RegexOption.IGNORE_CASE), "VTU-")
                val failedEntity = TransactionEntity(
                    userId = activeUid,
                    reference = cleanRef,
                    serviceType = pending.serviceType,
                    provider = pending.provider,
                    recipient = pending.recipient,
                    amount = pending.amount,
                    discountOrCashback = 0.0,
                    status = "FAILED",
                    timestamp = System.currentTimeMillis(),
                    tokenOrDetails = errMsg,
                    customerName = pending.customerName
                )
                repository.recordTransaction(failedEntity)
                onComplete()
                return@launch
            }

            val activeUid = currentUser.value?.id?.trim().orEmpty()
            val reference = gsubzResult.transactionId.replace(Regex("GSUBZ-ERR-|GSUBZ-", RegexOption.IGNORE_CASE), "VTU-")
            val isAirtime = pending.serviceType.equals("AIRTIME", ignoreCase = true)

            val serverAirtimeValue = gsubzResult.airtimeValue ?: pending.amount
            val serverCashback = gsubzResult.cashback ?: if (isAirtime) 0.0 else pending.discount
            val serverCharged = gsubzResult.charged ?: gsubzResult.amountPaid

            if (gsubzResult.newBalance != null && gsubzResult.newBalance >= 0.0) {
                repository.setWalletBalance(gsubzResult.newBalance)
            }

            val tokenOrDetails = when {
                isAirtime -> {
                    val baseStatusText = if (gsubzResult.isPending) {
                        "Purchase is being confirmed"
                    } else {
                        "Instant Top-up Successful"
                    }
                    "$baseStatusText [SERVER_RECEIPT:airtime=$serverAirtimeValue,cashback=$serverCashback,charged=$serverCharged]"
                }
                pending.serviceType == "ELECTRICITY" -> {
                    val rawToken = (gsubzResult.tokenOrPin ?: "Token: ${generateMeterToken()}")
                        .replace(Regex("Gsubz-VTU-Services|Gsubz|Supabase|Edge\\s*Function", RegexOption.IGNORE_CASE), "")
                        .trim()
                    if (rawToken.startsWith("Token", ignoreCase = true)) rawToken else "Token: $rawToken"
                }
                pending.serviceType == "EDUCATION" -> (gsubzResult.tokenOrPin ?: generateExamPin())
                    .replace(Regex("Gsubz-VTU-Services|Gsubz|Supabase|Edge\\s*Function", RegexOption.IGNORE_CASE), "")
                    .trim()
                pending.serviceType == "CABLE_TV" -> {
                    val cleanDetails = pending.details
                        ?.replace(Regex("Gsubz-VTU-Services|Gsubz|Supabase|Edge\\s*Function", RegexOption.IGNORE_CASE), "")
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                    if (cleanDetails != null) "$cleanDetails • Successful" else "Bouquet Activated • IUC: ${pending.recipient} • Successful"
                }
                pending.serviceType == "DATA" -> {
                    val cleanPlan = pending.details
                        ?.replace(Regex("\\(\\s*Gsubz\\s*Live\\s*\\)|Gsubz-VTU-Services|Gsubz|Supabase|Edge\\s*Function", RegexOption.IGNORE_CASE), "")
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                    if (cleanPlan != null) "$cleanPlan • Successful" else "Data Bundle Activated • Successful"
                }
                else -> if (gsubzResult.isPending) "Purchase is being confirmed" else "Instant Top-up Successful"
            }

            val txStatus = if (gsubzResult.isPending) "PENDING" else "SUCCESSFUL"
            val entity = TransactionEntity(
                userId = activeUid,
                reference = reference,
                serviceType = pending.serviceType,
                provider = pending.provider,
                recipient = pending.recipient,
                amount = if (isAirtime) serverAirtimeValue else pending.amount,
                discountOrCashback = serverCashback,
                status = txStatus,
                timestamp = System.currentTimeMillis(),
                tokenOrDetails = tokenOrDetails,
                customerName = pending.customerName
            )

            repository.recordTransaction(entity)

            repository.saveBeneficiary(
                BeneficiaryEntity(
                    userId = activeUid,
                    name = pending.customerName ?: "${pending.provider} - ${pending.recipient}",
                    recipient = pending.recipient,
                    serviceType = pending.serviceType,
                    provider = pending.provider
                )
            )

            if (gsubzResult.isPending) {
                _uiMessage.value = "Purchase is being confirmed"
            } else if (notificationsEnabled.value) {
                NotificationHelper.showTransactionNotification(
                    context = getApplication(),
                    title = "Transaction Successful!",
                    message = "${pending.title} to ${pending.recipient} completed.",
                    reference = reference,
                    amount = entity.amount,
                    token = if (pending.serviceType == "ELECTRICITY" || pending.serviceType == "EDUCATION") tokenOrDetails else null
                )
            }

            val alert = InAppNotification(
                id = reference,
                title = if (gsubzResult.isPending) {
                    "Purchase is being confirmed"
                } else {
                    "${pending.provider} ${pending.serviceType.replace('_', ' ')} Successful"
                },
                message = "₦%,.2f paid for %s.".format(
                    serverCharged,
                    pending.recipient
                ),
                transactionRef = reference
            )
            _inAppAlertBanner.value = alert
            _activeReceipt.value = entity
            onComplete()

            delay(6000)
            if (_inAppAlertBanner.value?.id == reference) {
                _inAppAlertBanner.value = null
            }
        }
    }

    // --- FLUTTERWAVE PAYMENT METHOD HANDLERS ---
    fun initiateFlutterwavePayment(
        amount: Double,
        onReady: (paymentUrl: String, txRef: String) -> Unit = { _, _ -> },
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isFlutterwaveLoading.value = true
            _flutterwaveError.value = null
            try {
                val user = currentUser.value
                val email = user?.email ?: ""
                val name = user?.fullName ?: ""
                val phone = com.example.data.repository.VtuRepository.sanitizeRealPhone(user?.phone)

                val result = repository.initializeFlutterwaveFunding(amount, email, name, phone)
                if (result.isSuccess) {
                    _flutterwavePendingRef.value = result.txRef
                    _flutterwavePaymentUrl.value = result.paymentUrl
                    onReady(result.paymentUrl ?: "", result.txRef)
                } else {
                    val err = sanitizeUserFacingError(result.message)
                    _flutterwaveError.value = err
                    onError(err)
                }
            } catch (e: Exception) {
                val err = sanitizeUserFacingError(e.localizedMessage)
                _flutterwaveError.value = err
                onError(err)
            } finally {
                _isFlutterwaveLoading.value = false
            }
        }
    }

    fun verifyFlutterwavePayment(
        txRef: String,
        amount: Double,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isFlutterwaveLoading.value = true
            _flutterwaveError.value = null
            try {
                val result = repository.verifyAndCreditFlutterwavePayment(txRef, amount)
                if (result.isSuccess) {
                    _flutterwavePendingRef.value = null
                    _flutterwavePaymentUrl.value = null
                    _showFundWalletSheet.value = false

                    if (notificationsEnabled.value) {
                        NotificationHelper.showTransactionNotification(
                            context = getApplication(),
                            title = "Flutterwave Payment Confirmed!",
                            message = "₦%,.2f has been credited to your wallet via Flutterwave.".format(amount),
                            reference = result.txRef,
                            amount = amount
                        )
                    }

                    val alert = InAppNotification(
                        id = result.txRef,
                        title = "Wallet Funded (Flutterwave)",
                        message = "₦%,.2f credited. New Balance: ₦%,.2f".format(amount, walletBalance.value),
                        transactionRef = result.txRef
                    )
                    _inAppAlertBanner.value = alert
                    onSuccess()

                    delay(6000)
                    if (_inAppAlertBanner.value?.id == result.txRef) {
                        _inAppAlertBanner.value = null
                    }
                } else {
                    val err = sanitizeUserFacingError(result.message)
                    _flutterwaveError.value = err
                    onError(err)
                }
            } catch (e: Exception) {
                val err = sanitizeUserFacingError(e.localizedMessage)
                _flutterwaveError.value = err
                onError(err)
            } finally {
                _isFlutterwaveLoading.value = false
            }
        }
    }

    fun resetFlutterwaveState() {
        _flutterwavePendingRef.value = null
        _flutterwavePaymentUrl.value = null
        _flutterwaveError.value = null
        _isFlutterwaveLoading.value = false
    }

    fun fundWalletInstant(amount: Double) {
        viewModelScope.launch {
            val dateStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
            val randomDigits = (100000..999999).random()
            val reference = "DEP-$dateStr-$randomDigits"
            val user = currentUser.value
            val bankDetails = listOfNotNull(user?.virtualBankName, user?.virtualAccountNumber)
                .filter { it.isNotBlank() }
                .joinToString(" / ")

            val entity = TransactionEntity(
                userId = user?.id?.trim().orEmpty(),
                reference = reference,
                serviceType = "WALLET_FUNDING",
                provider = "Virtual Bank Transfer",
                recipient = user?.email.orEmpty(),
                amount = amount,
                discountOrCashback = 0.0,
                status = "SUCCESSFUL",
                timestamp = System.currentTimeMillis(),
                tokenOrDetails = bankDetails.ifBlank { null },
                customerName = user?.fullName?.takeIf { it.isNotBlank() }
            )

            repository.recordTransaction(entity)
            _showFundWalletSheet.value = false

            if (notificationsEnabled.value) {
                NotificationHelper.showTransactionNotification(
                    context = getApplication(),
                    title = "Wallet Top-up Successful!",
                    message = "₦%,.2f has been credited to your wallet.".format(amount),
                    reference = reference,
                    amount = amount
                )
            }

            val alert = InAppNotification(
                id = reference,
                title = "Wallet Credited",
                message = "₦%,.2f added via instant transfer. New Balance: ₦%,.2f".format(amount, walletBalance.value),
                transactionRef = reference
            )
            _inAppAlertBanner.value = alert
            _activeReceipt.value = entity

            delay(6000)
            if (_inAppAlertBanner.value?.id == reference) {
                _inAppAlertBanner.value = null
            }
        }
    }

    fun markNotificationRead(id: String) {
        repository.markNotificationRead(id)
    }

    fun clearNotifications() {
        repository.clearAllNotifications()
    }

    fun openReceiptForTransaction(tx: TransactionEntity) {
        _activeReceipt.value = tx
    }

    private fun generateMeterToken(): String {
        val r = Random()
        return "%04d-%04d-%04d-%04d-%04d".format(
            r.nextInt(9000) + 1000,
            r.nextInt(9000) + 1000,
            r.nextInt(9000) + 1000,
            r.nextInt(9000) + 1000,
            r.nextInt(9000) + 1000
        )
    }

    private fun generateExamPin(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val serial = (1..10).map { chars.random() }.joinToString("")
        val pin = (1000000000..9999999999).random().toString()
        return "Serial: $serial | PIN: $pin"
    }

    fun getBusinessAccount(): Triple<String, String, String> {
        return repository.getBusinessAccount()
    }

    fun setBusinessAccount(accountNumber: String, bankName: String, accountName: String) {
        repository.setBusinessAccount(accountNumber, bankName, accountName)
    }

    fun submitPendingBankTransfer(
        amount: Double,
        senderName: String,
        bankUsed: String,
        sessionRef: String,
        onSuccess: (TransactionEntity) -> Unit = {}
    ) {
        viewModelScope.launch {
            val activeUser = currentUser.value
            val reference = if (sessionRef.isNotBlank()) sessionRef.trim() else "TX-TRANSFER-${System.currentTimeMillis()}-${(1000..9999).random()}"
            val entity = TransactionEntity(
                userId = activeUser?.id?.trim().orEmpty(),
                reference = reference,
                serviceType = "WALLET_FUNDING",
                provider = bankUsed.ifBlank { "Bank Transfer" },
                recipient = activeUser?.email.orEmpty(),
                amount = amount,
                discountOrCashback = 0.0,
                status = "PENDING",
                timestamp = System.currentTimeMillis(),
                tokenOrDetails = "Manual Bank Transfer via ${bankUsed.ifBlank { "Bank Transfer" }}",
                customerName = senderName.ifBlank { activeUser?.fullName?.takeIf { it.isNotBlank() } }
            )

            repository.recordTransaction(entity)
            _showFundWalletSheet.value = false

            val alert = InAppNotification(
                id = reference,
                title = "Transfer Submitted",
                message = "₦%,.2f transfer via %s submitted. Awaiting confirmation.".format(amount, bankUsed.ifBlank { "Bank Transfer" }),
                transactionRef = reference
            )
            _inAppAlertBanner.value = alert
            _activeReceipt.value = entity
            onSuccess(entity)
        }
    }

    // ==========================================
    // Developer API Keys & Webhooks
    // ==========================================

    val userApiKey: StateFlow<UserApiKey?> = repository.currentUserApiKey
    val forumTopics: StateFlow<List<ForumTopic>> = repository.forumTopics

    private val _isGeneratingApiKey = MutableStateFlow(false)
    val isGeneratingApiKey: StateFlow<Boolean> = _isGeneratingApiKey.asStateFlow()

    private val _apiKeyStatusMessage = MutableStateFlow<String?>(null)
    val apiKeyStatusMessage: StateFlow<String?> = _apiKeyStatusMessage.asStateFlow()

    private val _isLoadingApiKeyState = MutableStateFlow(false)
    val isLoadingApiKeyState: StateFlow<Boolean> = _isLoadingApiKeyState.asStateFlow()

    fun clearApiKeyStatusMessage() {
        _apiKeyStatusMessage.value = null
    }

    fun loadUserApiKey(userId: String, userEmail: String): UserApiKey {
        return repository.getOrGenerateApiKey(userId, userEmail)
    }

    /**
     * Queries the Supabase table api_clients for the logged-in user's row (server is source of truth)
     * and checks per-user EncryptedSharedPreferences ("api_key_<userId>").
     * Never calls the Generate-api-key function automatically when the screen opens.
     */
    fun loadOrFetchApiKey(
        userId: String,
        userEmail: String,
        userName: String = "",
        forceRefresh: Boolean = false
    ) {
        viewModelScope.launch {
            _isLoadingApiKeyState.value = true
            _apiKeyStatusMessage.value = null
            try {
                repository.syncApiKeyStateFromServer(userId, userEmail)
            } finally {
                _isLoadingApiKeyState.value = false
            }
        }
    }

    fun generateNewApiKey(
        userId: String,
        userEmail: String,
        userName: String = "",
        onComplete: ((Boolean, String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            _isGeneratingApiKey.value = true
            _apiKeyStatusMessage.value = null
            val result = repository.generateApiKeyRemote(userId, userEmail, userName)
            _isGeneratingApiKey.value = false
            if (result.isSuccess) {
                val key = result.getOrNull() ?: ""
                _apiKeyStatusMessage.value = "API Key generated and saved securely."
                onComplete?.invoke(true, key)
            } else {
                val err = sanitizeUserFacingError(result.exceptionOrNull()?.message)
                _apiKeyStatusMessage.value = err
                onComplete?.invoke(false, err)
            }
        }
    }

    fun regenerateApiKey(
        userId: String,
        userEmail: String,
        userName: String = "",
        onComplete: ((Boolean, String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            _isGeneratingApiKey.value = true
            _apiKeyStatusMessage.value = null
            val result = repository.regenerateApiKeyRemote(userId, userEmail, userName)
            _isGeneratingApiKey.value = false
            if (result.isSuccess) {
                val key = result.getOrNull() ?: ""
                _apiKeyStatusMessage.value = "New API Key generated and saved securely."
                onComplete?.invoke(true, key)
            } else {
                val err = sanitizeUserFacingError(result.exceptionOrNull()?.message)
                _apiKeyStatusMessage.value = err
                onComplete?.invoke(false, err)
            }
        }
    }

    private fun sanitizeUserFacingError(raw: String?): String {
        val text = raw?.trim().orEmpty()
        if (text.isBlank()) {
            return "Network connection bad. Please check your internet connection and try again."
        }
        val lower = text.lowercase()
        if (lower.contains("insufficient")) {
            return text.replace(Regex("Gsubz-VTU-Services|Gsubz|Supabase|Edge\\s*Function", RegexOption.IGNORE_CASE), "").trim()
        }
        if (lower.contains("gsubz") ||
            lower.contains("supabase") ||
            lower.contains("edge") ||
            lower.contains("function") ||
            lower.contains("credential") ||
            lower.contains("configured") ||
            lower.contains("api key") ||
            lower.contains("apikey") ||
            lower.contains("network") ||
            lower.contains("timeout") ||
            lower.contains("timed out") ||
            lower.contains("connect") ||
            lower.contains("unreachable") ||
            lower.contains("resolve host") ||
            lower.contains("bad gateway") ||
            lower.contains("http ") ||
            lower.contains("code 5") ||
            lower.contains("code 4")
        ) {
            return "Network connection bad. Please check your internet connection and try again."
        }
        return text.replace(Regex("Gsubz-VTU-Services|Gsubz|Supabase|Edge\\s*Function", RegexOption.IGNORE_CASE), "").trim()
            .ifBlank { "Network connection bad. Please check your internet connection and try again." }
    }

    fun saveCustomApiKey(userId: String, userEmail: String, customKey: String) {
        if (customKey.isNotBlank()) {
            repository.saveRealApiKeyLocally(userId, userEmail, customKey.trim())
            _apiKeyStatusMessage.value = "Live API Key saved and activated."
        }
    }

    fun saveWebhookUrl(userId: String, url: String) {
        repository.saveWebhookUrl(userId, url)
    }

    // ==========================================
    // Developers Forum
    // ==========================================

    fun createForumTopic(title: String, content: String, category: String, authorName: String, authorEmail: String) {
        repository.postForumTopic(title, content, category, authorName, authorEmail)
    }

    fun addForumReply(topicId: String, content: String, authorName: String) {
        repository.addForumReply(topicId, content, authorName)
    }

    fun toggleLikeTopic(topicId: String) {
        repository.toggleLikeTopic(topicId)
    }
}
