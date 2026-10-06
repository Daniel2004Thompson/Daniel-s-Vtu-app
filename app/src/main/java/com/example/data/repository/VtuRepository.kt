package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.auth.AuthRepository
import com.example.auth.AuthResult as JanAuthResult
import com.example.data.local.AppDatabase
import com.example.data.local.BeneficiaryEntity
import com.example.data.local.TransactionEntity
import com.example.data.local.UserProfileEntity
import com.example.data.model.AuthResult
import com.example.data.model.InAppNotification
import com.example.data.model.SupabaseUser
import com.example.data.remote.FlutterwaveEdgeServiceClient
import com.example.data.remote.FlutterwaveInitResult
import com.example.data.remote.FlutterwaveVerifyResult
import com.example.data.remote.FlutterwaveVirtualAccount
import com.example.data.remote.GsubzEdgeServiceClient
import com.example.data.remote.GsubzOrderResult
import com.example.data.remote.SupabaseAuthClient
import com.example.data.model.UserApiKey
import com.example.data.model.ForumTopic
import com.example.data.model.ForumReply
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.vtu.app.wallet.DynamicAccountResponse
import com.vtu.app.wallet.VirtualAccountResponse
import com.vtu.app.wallet.SettingsRepository
import com.vtu.app.wallet.WalletRepository
import com.vtu.app.wallet.SharedWalletObserver
import com.vtu.app.wallet.observeWalletBalance
import com.example.auth.SupabaseInstance
import com.example.auth.SupabaseProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import android.util.Log
import com.example.data.remote.ApiKeyRepository
import com.example.util.SecureApiKeyStorage
import org.json.JSONArray
import java.util.concurrent.TimeUnit

class VtuRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getDatabase(context),
    val authClient: SupabaseAuthClient = SupabaseAuthClient(),
    val authRepo: AuthRepository = SupabaseAuthManagerHolder.repository,
    val gsubzClient: GsubzEdgeServiceClient = GsubzEdgeServiceClient(),
    val flutterwaveClient: FlutterwaveEdgeServiceClient = FlutterwaveEdgeServiceClient(),
    val walletRepo: WalletRepository = WalletRepository(),
    val apiKeyRepo: ApiKeyRepository = ApiKeyRepository(),
    val secureApiKeyStorage: SecureApiKeyStorage = SecureApiKeyStorage(context)
) {
    private object SupabaseAuthManagerHolder {
        val repository = AuthRepository()
    }

    private val prefs: SharedPreferences =
        com.example.util.SecurityVault.getEncryptedPreferences(context, "daniel_vtu_prefs")

    private val userProfileDao = database.userProfileDao()
    private val transactionDao = database.transactionDao()
    private val beneficiaryDao = database.beneficiaryDao()
    private val repoScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun userDedicatedDb(userId: String?): AppDatabase {
        return AppDatabase.getDatabaseForUser(context, userId)
    }

    // Preferences keys
    companion object {
        private const val KEY_WALLET_BALANCE = "key_wallet_balance"
        private const val KEY_CASHBACK_BALANCE = "key_cashback_balance"
        private const val KEY_BIOMETRIC_ENABLED = "key_biometric_enabled"
        private const val KEY_APP_LOCK_ENABLED = "key_app_lock_enabled"
        private const val KEY_NOTIFICATIONS_ENABLED = "key_notifications_enabled"
        private const val KEY_SECURITY_PIN = "key_security_pin"
        private const val KEY_FIRST_RUN = "key_first_run_initialized"

        // Supabase Auth keys
        private const val KEY_IS_LOGGED_IN = "key_auth_logged_in"
        private const val KEY_USER_ID = "key_auth_user_id"
        private const val KEY_USER_EMAIL = "key_auth_user_email"
        private const val KEY_USER_NAME = "key_auth_user_name"
        private const val KEY_USER_PHONE = "key_auth_user_phone"
        private const val KEY_USER_NIN = "key_auth_user_nin"
        private const val KEY_USER_VA_NUMBER = "key_auth_user_va_number"
        private const val KEY_USER_VA_BANK = "key_auth_user_va_bank"
        private const val KEY_USER_VA_NAME = "key_auth_user_va_name"
        private const val KEY_USER_DYNAMIC_ACC_NUMBER = "key_auth_user_dynamic_acc_number"
        private const val KEY_USER_DYNAMIC_ACC_BANK = "key_auth_user_dynamic_acc_bank"
        private const val KEY_USER_DYNAMIC_ACC_NAME = "key_auth_user_dynamic_acc_name"
        private const val KEY_USER_DYNAMIC_ACC_AMOUNT = "key_auth_user_dynamic_acc_amount"
        private const val KEY_ACCESS_TOKEN = "key_auth_access_token"
        private const val KEY_REFRESH_TOKEN = "key_auth_refresh_token"
        private const val KEY_REGISTERED_EMAILS = "key_registered_emails"
        private const val KEY_DELETED_EMAILS = "key_deleted_emails"
        private const val KEY_BUSINESS_ACCOUNT_NUMBER = "key_business_account_number"
        private const val KEY_BUSINESS_BANK_NAME = "key_business_bank_name"
        private const val KEY_BUSINESS_ACCOUNT_NAME = "key_business_account_name"

        private val LEGACY_MOCK_PHONES = setOf(
            "08031234567",
            "08012345678",
            "+234 812 345 6789",
            "+2348123456789",
            "8123456789"
        )

        fun sanitizeRealPhone(raw: String?): String? {
            val clean = raw?.trim() ?: return null
            if (clean.isBlank() || clean.equals("null", ignoreCase = true) || clean.equals("nil", ignoreCase = true)) {
                return null
            }
            if (clean in LEGACY_MOCK_PHONES) {
                return null
            }
            return clean
        }

        private fun cleanRawNameString(raw: String?): String {
            var clean = raw?.trim()?.trim('"')?.trim()
            if (clean.isNullOrBlank() ||
                clean.equals("null", ignoreCase = true) ||
                clean.equals("nil", ignoreCase = true)
            ) {
                return ""
            }
            clean = clean.replace(Regex("^(FLW/|FLW-|FLUTTERWAVE/)\\s*", RegexOption.IGNORE_CASE), "").trim()
            val normalizedSpaces = clean.split("\\s+".toRegex()).filter { it.isNotBlank() }.joinToString(" ")
            if (normalizedSpaces.any { it.isLetter() } && normalizedSpaces == normalizedSpaces.uppercase()) {
                return normalizedSpaces.split(" ").joinToString(" ") { word ->
                    word.lowercase().replaceFirstChar { c -> c.titlecase(Locale.getDefault()) }
                }
            }
            return normalizedSpaces
        }

        fun sanitizeFullName(raw: String?, email: String? = null): String {
            val clean = cleanRawNameString(raw)
            val cleanEmail = email?.trim()?.lowercase().orEmpty()
            if (cleanEmail == "danielkaladathompson@gmail.com" ||
                clean.equals("Daniel Kalada Thompson", ignoreCase = true) ||
                clean.equals("Daniel Thompson", ignoreCase = true)
            ) {
                return "Daniel Thompson"
            }
            return clean
        }

        fun resolveAccountHolderName(
            rawAccountName: String?,
            fullName: String? = null,
            email: String? = null
        ): String {
            val cleanAcct = cleanRawNameString(rawAccountName)
            val cleanFull = cleanRawNameString(fullName)
            val cleanEmail = email?.trim()?.lowercase().orEmpty()

            if (cleanEmail == "danielkaladathompson@gmail.com" ||
                cleanAcct.equals("Daniel Kalada Thompson", ignoreCase = true) ||
                cleanAcct.equals("Daniel Thompson", ignoreCase = true) ||
                cleanFull.equals("Daniel Kalada Thompson", ignoreCase = true)
            ) {
                return "Daniel Kalada Thompson"
            }

            return cleanAcct.ifBlank { cleanFull }
        }
    }

    private val _isLoggedIn = MutableStateFlow(
        prefs.getBoolean(KEY_IS_LOGGED_IN, false) &&
            !prefs.getString(KEY_USER_ID, null).let { it.isNullOrBlank() || it == "usr_default" || it == "usr_guest" }
    )
    val isLoggedIn = _isLoggedIn.asStateFlow()

    private val _currentUser = MutableStateFlow<SupabaseUser?>(
        run {
            val storedId = prefs.getString(KEY_USER_ID, null)?.trim()
                ?.takeIf { it.isNotBlank() && it != "usr_default" && it != "usr_guest" }
            if (prefs.getBoolean(KEY_IS_LOGGED_IN, false) && storedId != null) {
                val storedEmail = prefs.getString("key_user_email_$storedId", null)
                    ?: prefs.getString(KEY_USER_EMAIL, null)
                    ?: ""
                val rawStoredName = prefs.getString("key_user_name_$storedId", null)
                val rawStoredVaName = prefs.getString("key_va_name_$storedId", null)
                val name = sanitizeFullName(rawStoredName, storedEmail)
                val realStoredPhone = sanitizeRealPhone(prefs.getString("key_user_phone_$storedId", null))
                val ninHash = prefs.getString("key_nin_hash_$storedId", null)?.trim()?.takeIf {
                    !it.equals("null", ignoreCase = true) && !it.equals("nil", ignoreCase = true) && it.isNotBlank()
                }
                val vaNumber = prefs.getString("key_va_number_$storedId", null)?.trim()?.takeIf {
                    !it.equals("null", ignoreCase = true) && !it.equals("nil", ignoreCase = true) && it.filter { c -> c.isDigit() }.length in 10..12
                }
                val vaBank = if (vaNumber != null) {
                    prefs.getString("key_va_bank_$storedId", null)?.trim()?.takeIf {
                        !it.equals("null", ignoreCase = true) && !it.equals("nil", ignoreCase = true) && it.isNotBlank()
                    }
                } else null
                val vaName = if (vaNumber != null) {
                    resolveAccountHolderName(rawStoredVaName, name, storedEmail).takeIf { it.isNotBlank() }
                } else null

                val dynamicAccNumber = (prefs.getString("key_dynamic_acc_number_$storedId", null)
                    ?: prefs.getString("key_dyn_acc_number_$storedId", null))?.trim()?.takeIf {
                    !it.equals("null", ignoreCase = true) && !it.equals("nil", ignoreCase = true) && it.filter { c -> c.isDigit() }.length in 10..12
                }
                val dynamicAccBank = if (dynamicAccNumber != null) {
                    (prefs.getString("key_dynamic_acc_bank_$storedId", null)
                        ?: prefs.getString("key_dyn_acc_bank_$storedId", null))?.trim()?.takeIf {
                        !it.equals("null", ignoreCase = true) && !it.equals("nil", ignoreCase = true) && it.isNotBlank()
                    }
                } else null
                val dynamicAccName = if (dynamicAccNumber != null) "Wallet Topup" else null
                val dynamicAccAmount = if (dynamicAccNumber != null && prefs.contains("key_dynamic_acc_amount_$storedId")) {
                    prefs.getFloat("key_dynamic_acc_amount_$storedId", 0f).toDouble()
                } else if (dynamicAccNumber != null && prefs.contains("key_dyn_acc_amount_$storedId")) {
                    prefs.getFloat("key_dyn_acc_amount_$storedId", 0f).toDouble()
                } else null

                SupabaseUser(
                    id = storedId,
                    email = storedEmail,
                    fullName = name,
                    phone = realStoredPhone,
                    nin = ninHash,
                    ninHash = ninHash,
                    ninVerified = !ninHash.isNullOrBlank(),
                    virtualAccountNumber = vaNumber,
                    virtualBankName = vaBank,
                    virtualAccountName = vaName,
                    dynamicAccountNumber = dynamicAccNumber,
                    dynamicBankName = dynamicAccBank,
                    dynamicAccountName = dynamicAccName,
                    dynamicAccountAmount = dynamicAccAmount
                )
            } else null
        }
    )
    val currentUser = _currentUser.asStateFlow()

    private val _accessToken = MutableStateFlow(
        prefs.getString(KEY_ACCESS_TOKEN, null)
    )
    val accessToken = _accessToken.asStateFlow()

    private val _walletBalance = MutableStateFlow(
        SharedWalletObserver.liveBalance.value ?: run {
            val uid = _currentUser.value?.id
            if (!uid.isNullOrBlank() && prefs.contains("key_wallet_balance_$uid")) {
                prefs.getFloat("key_wallet_balance_$uid", 0f).toDouble()
            } else {
                0.0
            }
        }
    )
    val walletBalance = _walletBalance.asStateFlow()

    private val _cashbackBalance = MutableStateFlow(
        run {
            val uid = _currentUser.value?.id
            val email = _currentUser.value?.email
            when {
                !uid.isNullOrBlank() && prefs.contains("key_cashback_balance_$uid") ->
                    prefs.getFloat("key_cashback_balance_$uid", 0.0f).toDouble()
                !email.isNullOrBlank() && prefs.contains("key_cashback_balance_${email.trim().lowercase()}") ->
                    prefs.getFloat("key_cashback_balance_${email.trim().lowercase()}", 0.0f).toDouble()
                else -> prefs.getFloat(KEY_CASHBACK_BALANCE, 0.0f).toDouble()
            }
        }
    )
    val cashbackBalance = _cashbackBalance.asStateFlow()

    private val _biometricEnabled = MutableStateFlow(
        run {
            val uid = _currentUser.value?.id
            if (!uid.isNullOrBlank() && prefs.contains("key_biometric_enabled_$uid")) {
                prefs.getBoolean("key_biometric_enabled_$uid", true)
            } else {
                prefs.getBoolean(KEY_BIOMETRIC_ENABLED, true)
            }
        }
    )
    val biometricEnabled = _biometricEnabled.asStateFlow()

    private val _appLockEnabled = MutableStateFlow(
        run {
            val uid = _currentUser.value?.id
            if (!uid.isNullOrBlank() && prefs.contains("key_app_lock_enabled_$uid")) {
                prefs.getBoolean("key_app_lock_enabled_$uid", false)
            } else {
                prefs.getBoolean(KEY_APP_LOCK_ENABLED, false)
            }
        }
    )
    val appLockEnabled = _appLockEnabled.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(
        run {
            val uid = _currentUser.value?.id
            if (!uid.isNullOrBlank() && prefs.contains("key_notifications_enabled_$uid")) {
                prefs.getBoolean("key_notifications_enabled_$uid", true)
            } else {
                prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
            }
        }
    )
    val notificationsEnabled = _notificationsEnabled.asStateFlow()

    // Developer API Keys & Forum
    private val _currentUserApiKey = MutableStateFlow<UserApiKey?>(null)
    val currentUserApiKey = _currentUserApiKey.asStateFlow()

    private val _forumTopics = MutableStateFlow<List<ForumTopic>>(emptyList())
    val forumTopics = _forumTopics.asStateFlow()

    private val _inAppNotifications = MutableStateFlow<List<InAppNotification>>(emptyList())
    val inAppNotifications = _inAppNotifications.asStateFlow()

    init {
        checkAndSeedInitialData()
        restoreCurrentUserFromDedicatedDatabase()
    }

    private fun restoreCurrentUserFromDedicatedDatabase() {
        val current = _currentUser.value ?: return
        val uid = current.id.trim()
        if (uid.isBlank() || uid == "usr_default" || uid == "usr_guest") return
        repoScope.launch {
            try {
                val profile = userDedicatedDb(uid).userProfileDao().getUserProfile(uid)
                    ?: userProfileDao.getUserProfile(uid)
                if (profile != null && _currentUser.value?.id == uid) {
                    val resolvedEmail = profile.email.ifBlank { current.email }
                    val resolvedFullName = resolveAccountHolderName(
                        rawAccountName = profile.fullName.ifBlank { current.fullName },
                        fullName = profile.virtualAccountName ?: current.virtualAccountName,
                        email = resolvedEmail
                    )
                    val resolvedVaName = if ((profile.virtualAccountNumber ?: current.virtualAccountNumber) != null) {
                        resolveAccountHolderName(
                            rawAccountName = profile.virtualAccountName ?: current.virtualAccountName,
                            fullName = resolvedFullName,
                            email = resolvedEmail
                        ).ifBlank { null }
                    } else null
                    val resolvedDynName = if ((profile.dynamicAccountNumber ?: current.dynamicAccountNumber) != null) {
                        resolveAccountHolderName(
                            rawAccountName = profile.dynamicAccountName ?: current.dynamicAccountName,
                            fullName = resolvedFullName,
                            email = resolvedEmail
                        ).ifBlank { null }
                    } else null
                    val mergedUser = current.copy(
                        email = resolvedEmail,
                        fullName = resolvedFullName,
                        phone = sanitizeRealPhone(profile.phone) ?: current.phone,
                        nin = profile.nin ?: current.nin,
                        ninHash = profile.ninHash ?: current.ninHash,
                        ninVerified = profile.ninVerified || current.ninVerified,
                        virtualAccountNumber = profile.virtualAccountNumber ?: current.virtualAccountNumber,
                        virtualBankName = profile.virtualBankName ?: current.virtualBankName,
                        virtualAccountName = resolvedVaName,
                        dynamicAccountNumber = profile.dynamicAccountNumber ?: current.dynamicAccountNumber,
                        dynamicBankName = profile.dynamicBankName ?: current.dynamicBankName,
                        dynamicAccountName = resolvedDynName,
                        dynamicAccountAmount = profile.dynamicAccountAmount ?: current.dynamicAccountAmount
                    )
                    _currentUser.value = mergedUser
                    if (SharedWalletObserver.liveBalance.value == null && profile.walletBalance > 0.0) {
                        _walletBalance.value = profile.walletBalance
                    }
                    if (profile.cashbackBalance > 0.0 && _cashbackBalance.value == 0.0) {
                        _cashbackBalance.value = profile.cashbackBalance
                    }
                    _biometricEnabled.value = profile.biometricEnabled
                    _appLockEnabled.value = profile.appLockEnabled
                    _notificationsEnabled.value = profile.notificationsEnabled
                } else {
                    persistCurrentUserToLocalDatabase(current)
                }
            } catch (_: Throwable) {}
        }
    }

    private suspend fun persistCurrentUserToLocalDatabase(user: SupabaseUser? = _currentUser.value) {
        val u = user ?: return
        val uid = u.id.trim()
        if (uid.isBlank() || uid == "usr_default" || uid == "usr_guest") return
        try {
            val pin = prefs.getString("key_security_pin_$uid", null)
                ?: prefs.getString(KEY_SECURITY_PIN, "1234")
                ?: "1234"
            val entity = UserProfileEntity.fromSupabaseUser(
                user = u,
                walletBalance = _walletBalance.value,
                cashbackBalance = _cashbackBalance.value,
                securityPin = pin,
                biometricEnabled = _biometricEnabled.value,
                appLockEnabled = _appLockEnabled.value,
                notificationsEnabled = _notificationsEnabled.value
            )
            userProfileDao.upsertUserProfile(entity)
            userDedicatedDb(uid).userProfileDao().upsertUserProfile(entity)
        } catch (_: Throwable) {}
    }

    private fun checkAndSeedInitialData() {
        val initialized = prefs.getBoolean(KEY_FIRST_RUN, false)
        if (!initialized) {
            prefs.edit()
                .putBoolean(KEY_FIRST_RUN, true)
                .putFloat(KEY_WALLET_BALANCE, 0.0f)
                .putFloat(KEY_CASHBACK_BALANCE, 0.0f)
                .putString(KEY_SECURITY_PIN, "1234")
                .putBoolean(KEY_BIOMETRIC_ENABLED, true)
                .putBoolean(KEY_NOTIFICATIONS_ENABLED, true)
                .apply()

            // Pre-seed welcome notification with 0.00 balance
            _inAppNotifications.value = listOf(
                InAppNotification(
                    id = UUID.randomUUID().toString(),
                    title = "Welcome to Daniel VTU!",
                    message = "Your wallet balance is ₦0.00. Fund your wallet via Bank Transfer or Flutterwave to start enjoying discounts.",
                    timestamp = System.currentTimeMillis() - 3600000
                ),
                InAppNotification(
                    id = UUID.randomUUID().toString(),
                    title = "Biometric Security Active",
                    message = "Protect your transactions with Fingerprint or Face ID for fast and secure checkouts.",
                    timestamp = System.currentTimeMillis() - 1800000
                )
            )
        }

        // Bridge live wallet updates from SharedWalletObserver (public.users.wallet_balance Realtime subscription)
        SharedWalletObserver.addListener { freshBalance ->
            if (_walletBalance.value != freshBalance) {
                _walletBalance.value = freshBalance
            }
            val uid = _currentUser.value?.id?.trim()
            if (!uid.isNullOrBlank()) {
                prefs.edit().putFloat("key_wallet_balance_$uid", freshBalance.toFloat()).apply()
                repoScope.launch {
                    try {
                        userProfileDao.updateWalletBalance(uid, freshBalance)
                        userDedicatedDb(uid).userProfileDao().updateWalletBalance(uid, freshBalance)
                    } catch (_: Throwable) {}
                }
            }
        }

        initForumTopics()
        secureApiKeyStorage.removeLegacySharedEntries()
    }

    // Per-user Room DB streams: automatically scoped to the active authenticated user's ID
    @OptIn(ExperimentalCoroutinesApi::class)
    val allTransactions: Flow<List<TransactionEntity>> = _currentUser.flatMapLatest { user ->
        val uid = user?.id?.trim().orEmpty()
        transactionDao.getAllTransactionsForUser(uid)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val recentTransactions: Flow<List<TransactionEntity>> = _currentUser.flatMapLatest { user ->
        val uid = user?.id?.trim().orEmpty()
        transactionDao.getRecentTransactionsForUser(uid, 8)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val allBeneficiaries: Flow<List<BeneficiaryEntity>> = _currentUser.flatMapLatest { user ->
        val uid = user?.id?.trim().orEmpty()
        beneficiaryDao.getAllBeneficiariesForUser(uid)
    }

    suspend fun recordTransaction(transaction: TransactionEntity): Long {
        val activeUserId = transaction.userId.ifBlank { _currentUser.value?.id?.trim().orEmpty() }
        val scopedTx = transaction.copy(userId = activeUserId)
        val id = transactionDao.insertTransaction(scopedTx)
        if (activeUserId.isNotBlank()) {
            try {
                userDedicatedDb(activeUserId).transactionDao().insertTransaction(scopedTx.copy(id = id))
            } catch (_: Throwable) {}
            repoScope.launch {
                syncTransactionToSupabase(scopedTx)
            }
        }

        val isSuccess = scopedTx.status == "SUCCESSFUL"

        // Do not calculate wallet balance client-side; rely on the public.users.wallet_balance Realtime subscription
        if (scopedTx.serviceType == "WALLET_FUNDING") {
            if (isSuccess) {
                repoScope.launch {
                    syncRemoteProfileBalance()
                }
            }
        } else {
            if (isSuccess) {
                val netCost = (scopedTx.amount - scopedTx.discountOrCashback).coerceAtLeast(0.0)
                if (scopedTx.discountOrCashback > 0) {
                    addCashback(scopedTx.discountOrCashback)
                }
                // Trigger remote Supabase wallet debit; balance updates come from public.users.wallet_balance
                repoScope.launch {
                    try {
                        withdrawFromWallet(netCost)
                    } catch (e: Exception) {
                        Log.d("VtuRepository", "Remote wallet sync note: ${e.message}")
                    }
                }
            }
        }

        // Add to In-App notifications
        val newNotification = InAppNotification(
            id = UUID.randomUUID().toString(),
            title = if (isSuccess) {
                when (scopedTx.serviceType) {
                    "AIRTIME" -> "Airtime Top-up Successful"
                    "DATA" -> "Data Bundle Activated"
                    "ELECTRICITY" -> "Electricity Token Generated"
                    "CABLE_TV" -> "Cable TV Subscription Renewed"
                    "EDUCATION" -> "Education PIN Purchased"
                    else -> "Wallet Funded Successfully"
                }
            } else {
                when (scopedTx.serviceType) {
                    "AIRTIME" -> "Airtime Top-up Failed"
                    "DATA" -> "Data Bundle Failed"
                    "ELECTRICITY" -> "Electricity Purchase Failed"
                    "CABLE_TV" -> "Cable TV Subscription Failed"
                    "EDUCATION" -> "Education PIN Failed"
                    else -> "Transaction Failed"
                }
            },
            message = if (isSuccess) {
                "₦%,.2f to %s (%s). Ref: %s".format(
                    scopedTx.amount,
                    scopedTx.recipient,
                    scopedTx.provider,
                    scopedTx.reference
                )
            } else {
                "Failed: ₦%,.2f to %s (No funds deducted). %s".format(
                    scopedTx.amount,
                    scopedTx.recipient,
                    scopedTx.tokenOrDetails ?: "Declined"
                )
            },
            timestamp = System.currentTimeMillis(),
            transactionRef = scopedTx.reference
        )
        _inAppNotifications.value = listOf(newNotification) + _inAppNotifications.value

        return id
    }

    private suspend fun syncTransactionToSupabase(tx: TransactionEntity) {
        val uid = tx.userId.trim()
        if (uid.isBlank() || uid == "usr_guest" || uid == "usr_default") return
        val client = SupabaseInstance.client ?: return
        try {
            val payload = buildJsonObject {
                put("user_id", JsonPrimitive(uid))
                put("reference", JsonPrimitive(tx.reference))
                put("service_type", JsonPrimitive(tx.serviceType))
                put("provider", JsonPrimitive(tx.provider))
                put("recipient", JsonPrimitive(tx.recipient))
                put("amount", JsonPrimitive(tx.amount))
                put("discount", JsonPrimitive(tx.discountOrCashback))
                put("status", JsonPrimitive(tx.status))
                if (!tx.tokenOrDetails.isNullOrBlank()) {
                    put("details", JsonPrimitive(tx.tokenOrDetails))
                }
                if (!tx.customerName.isNullOrBlank()) {
                    put("customer_name", JsonPrimitive(tx.customerName))
                }
            }
            client.from("transactions").insert(payload)
        } catch (_: Throwable) {}
    }

    suspend fun syncRemoteTransactionsForUser(userId: String) = withContext(Dispatchers.IO) {
        val uid = userId.trim()
        if (uid.isBlank() || uid == "usr_guest" || uid == "usr_default") return@withContext
        val client = SupabaseInstance.client ?: return@withContext
        try {
            val rows = client.from("transactions").select {
                filter { eq("user_id", uid) }
                limit(50)
            }.decodeList<JsonObject>()
            for (row in rows) {
                val ref = row["reference"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: row["tx_ref"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: continue
                if (ref.isBlank()) continue
                val existing = transactionDao.getTransactionByRefForUser(uid, ref)
                if (existing == null) {
                    val serviceType = row["service_type"]?.jsonPrimitive?.contentOrNull
                        ?: row["type"]?.jsonPrimitive?.contentOrNull
                        ?: "WALLET_FUNDING"
                    val provider = row["provider"]?.jsonPrimitive?.contentOrNull
                        ?: row["network"]?.jsonPrimitive?.contentOrNull
                        ?: "VTU Service"
                    val recipient = row["recipient"]?.jsonPrimitive?.contentOrNull
                        ?: row["phone"]?.jsonPrimitive?.contentOrNull
                        ?: _currentUser.value?.email.orEmpty()
                    val amount = row["amount"]?.jsonPrimitive?.doubleOrNull
                        ?: row["amount"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                        ?: 0.0
                    val discount = row["discount"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val status = (row["status"]?.jsonPrimitive?.contentOrNull ?: "SUCCESSFUL").uppercase()
                    val details = row["details"]?.jsonPrimitive?.contentOrNull
                    val customerName = row["customer_name"]?.jsonPrimitive?.contentOrNull
                    val entity = TransactionEntity(
                        userId = uid,
                        reference = ref,
                        serviceType = serviceType,
                        provider = provider,
                        recipient = recipient,
                        amount = amount,
                        discountOrCashback = discount,
                        status = status,
                        timestamp = System.currentTimeMillis(),
                        tokenOrDetails = details,
                        customerName = customerName
                    )
                    val id = transactionDao.insertTransaction(entity)
                    try {
                        userDedicatedDb(uid).transactionDao().insertTransaction(entity.copy(id = id))
                    } catch (_: Throwable) {}
                }
            }
        } catch (_: Throwable) {}
    }

    suspend fun deleteTransaction(id: Long) = withContext(Dispatchers.IO) {
        val uid = _currentUser.value?.id?.trim().orEmpty()
        transactionDao.deleteTransactionById(id)
        if (uid.isNotBlank()) {
            try {
                userDedicatedDb(uid).transactionDao().deleteTransactionById(id)
            } catch (_: Throwable) {}
        }
    }

    suspend fun deleteTransactionByRef(ref: String) = withContext(Dispatchers.IO) {
        val uid = _currentUser.value?.id?.trim().orEmpty()
        transactionDao.deleteTransactionByRef(ref)
        if (uid.isNotBlank()) {
            try {
                userDedicatedDb(uid).transactionDao().deleteTransactionByRef(ref)
            } catch (_: Throwable) {}
        }
    }

    suspend fun clearAllTransactions() = withContext(Dispatchers.IO) {
        val uid = _currentUser.value?.id?.trim().orEmpty()
        if (uid.isNotBlank()) {
            transactionDao.clearAllForUser(uid)
            try {
                userDedicatedDb(uid).transactionDao().clearAllForUser(uid)
            } catch (_: Throwable) {}
        } else {
            transactionDao.clearAll()
        }
    }

    private fun getCashbackKey(email: String? = _currentUser.value?.email, userId: String? = _currentUser.value?.id): String =
        when {
            !userId.isNullOrBlank() -> "key_cashback_balance_${userId.trim()}"
            !email.isNullOrBlank() -> "key_cashback_balance_${email.trim().lowercase()}"
            else -> KEY_CASHBACK_BALANCE
        }

    fun setNewAccountZeroBalance(email: String? = null, userId: String? = null) {
        _walletBalance.value = 0.0
        _cashbackBalance.value = 0.0
        SharedWalletObserver.updateBalance(0.0)
        val editor = prefs.edit()
            .putFloat(KEY_CASHBACK_BALANCE, 0.0f)
        val targetEmail = email ?: _currentUser.value?.email
        if (!targetEmail.isNullOrBlank()) {
            val clean = targetEmail.trim().lowercase()
            editor.putFloat("key_cashback_balance_$clean", 0.0f)
        }
        val targetUid = userId ?: _currentUser.value?.id
        if (!targetUid.isNullOrBlank()) {
            editor.putFloat("key_cashback_balance_${targetUid.trim()}", 0.0f)
            editor.putFloat("key_wallet_balance_${targetUid.trim()}", 0.0f)
        }
        editor.apply()
    }

    private fun loadUserBalance(email: String?, userId: String? = _currentUser.value?.id) {
        val uid = userId?.trim()
        val uidKey = if (!uid.isNullOrBlank()) "key_cashback_balance_$uid" else null
        val emailKey = if (!email.isNullOrBlank()) "key_cashback_balance_${email.trim().lowercase()}" else null
        val cb = when {
            uidKey != null && prefs.contains(uidKey) -> prefs.getFloat(uidKey, 0.0f).toDouble()
            emailKey != null && prefs.contains(emailKey) -> prefs.getFloat(emailKey, 0.0f).toDouble()
            else -> 0.0
        }
        _cashbackBalance.value = cb
        prefs.edit().putFloat(KEY_CASHBACK_BALANCE, cb.toFloat()).apply()
        if (!uid.isNullOrBlank() && prefs.contains("key_wallet_balance_$uid")) {
            val savedWallet = prefs.getFloat("key_wallet_balance_$uid", 0.0f).toDouble()
            if (SharedWalletObserver.liveBalance.value == null) {
                _walletBalance.value = savedWallet
            }
        }
    }

    private fun loadUserSecurityAndSettings(userId: String) {
        val uid = userId.trim()
        if (uid.isBlank() || uid == "usr_guest" || uid == "usr_default") return
        val bio = if (prefs.contains("key_biometric_enabled_$uid")) {
            prefs.getBoolean("key_biometric_enabled_$uid", true)
        } else {
            prefs.getBoolean(KEY_BIOMETRIC_ENABLED, true)
        }
        val appLock = if (prefs.contains("key_app_lock_enabled_$uid")) {
            prefs.getBoolean("key_app_lock_enabled_$uid", false)
        } else {
            prefs.getBoolean(KEY_APP_LOCK_ENABLED, false)
        }
        val notif = if (prefs.contains("key_notifications_enabled_$uid")) {
            prefs.getBoolean("key_notifications_enabled_$uid", true)
        } else {
            prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
        }
        _biometricEnabled.value = bio
        _appLockEnabled.value = appLock
        _notificationsEnabled.value = notif
    }

    private suspend fun syncUserDataFromDatabase(userId: String) = withContext(Dispatchers.IO) {
        val uid = userId.trim()
        if (uid.isBlank() || uid == "usr_guest" || uid == "usr_default") return@withContext
        try {
            val dedicatedProfile = userDedicatedDb(uid).userProfileDao().getUserProfile(uid)
                ?: userProfileDao.getUserProfile(uid)
            if (dedicatedProfile != null) {
                userProfileDao.upsertUserProfile(dedicatedProfile)
                userDedicatedDb(uid).userProfileDao().upsertUserProfile(dedicatedProfile)
                if (_currentUser.value?.id == uid) {
                    val current = _currentUser.value!!
                    val resolvedEmail = current.email.ifBlank { dedicatedProfile.email }
                    val resolvedFullName = sanitizeFullName(
                        current.fullName.ifBlank { dedicatedProfile.fullName },
                        resolvedEmail
                    )
                    val resolvedVaName = if ((current.virtualAccountNumber ?: dedicatedProfile.virtualAccountNumber) != null) {
                        resolveAccountHolderName(
                            rawAccountName = current.virtualAccountName ?: dedicatedProfile.virtualAccountName,
                            fullName = resolvedFullName,
                            email = resolvedEmail
                        ).ifBlank { null }
                    } else null
                    val resolvedDynName = if ((current.dynamicAccountNumber ?: dedicatedProfile.dynamicAccountNumber) != null) {
                        "Wallet Topup"
                    } else null
                    val merged = current.copy(
                        email = resolvedEmail,
                        fullName = resolvedFullName,
                        phone = sanitizeRealPhone(current.phone) ?: sanitizeRealPhone(dedicatedProfile.phone),
                        nin = current.nin ?: dedicatedProfile.nin,
                        ninHash = current.ninHash ?: dedicatedProfile.ninHash,
                        ninVerified = current.ninVerified || dedicatedProfile.ninVerified,
                        virtualAccountNumber = current.virtualAccountNumber ?: dedicatedProfile.virtualAccountNumber,
                        virtualBankName = current.virtualBankName ?: dedicatedProfile.virtualBankName,
                        virtualAccountName = resolvedVaName,
                        dynamicAccountNumber = current.dynamicAccountNumber ?: dedicatedProfile.dynamicAccountNumber,
                        dynamicBankName = current.dynamicBankName ?: dedicatedProfile.dynamicBankName,
                        dynamicAccountName = resolvedDynName,
                        dynamicAccountAmount = current.dynamicAccountAmount ?: dedicatedProfile.dynamicAccountAmount
                    )
                    _currentUser.value = merged
                }
            }
        } catch (_: Throwable) {}
        syncRemoteTransactionsForUser(uid)
    }

    private fun addCashback(amount: Double) {
        val current = _cashbackBalance.value
        val newBalance = current + amount
        _cashbackBalance.value = newBalance
        val editor = prefs.edit().putFloat(KEY_CASHBACK_BALANCE, newBalance.toFloat())
        val user = _currentUser.value
        if (!user?.email.isNullOrBlank()) {
            editor.putFloat("key_cashback_balance_${user?.email?.trim()?.lowercase()}", newBalance.toFloat())
        }
        if (!user?.id.isNullOrBlank()) {
            val uid = user!!.id.trim()
            editor.putFloat("key_cashback_balance_$uid", newBalance.toFloat())
            repoScope.launch {
                try {
                    userProfileDao.updateCashbackBalance(uid, newBalance)
                    userDedicatedDb(uid).userProfileDao().updateCashbackBalance(uid, newBalance)
                } catch (_: Throwable) {}
            }
        }
        editor.apply()
    }

    suspend fun saveBeneficiary(beneficiary: BeneficiaryEntity) {
        val activeUserId = beneficiary.userId.ifBlank { _currentUser.value?.id?.trim().orEmpty() }
        val existing = if (activeUserId.isNotBlank()) {
            beneficiaryDao.getBeneficiaryForUser(activeUserId, beneficiary.recipient, beneficiary.serviceType)
        } else null
        val scopedBeneficiary = if (existing != null) {
            existing.copy(
                userId = activeUserId,
                name = beneficiary.name.ifBlank { existing.name },
                provider = beneficiary.provider.ifBlank { existing.provider },
                lastUsedTimestamp = System.currentTimeMillis()
            )
        } else {
            beneficiary.copy(
                userId = activeUserId,
                lastUsedTimestamp = System.currentTimeMillis()
            )
        }
        val id = beneficiaryDao.insertBeneficiary(scopedBeneficiary)
        if (activeUserId.isNotBlank()) {
            try {
                userDedicatedDb(activeUserId).beneficiaryDao().insertBeneficiary(scopedBeneficiary.copy(id = id))
            } catch (_: Throwable) {}
        }
    }

    suspend fun deleteBeneficiary(beneficiary: BeneficiaryEntity) {
        val activeUserId = beneficiary.userId.ifBlank { _currentUser.value?.id?.trim().orEmpty() }
        beneficiaryDao.deleteBeneficiary(beneficiary)
        if (activeUserId.isNotBlank()) {
            try {
                userDedicatedDb(activeUserId).beneficiaryDao().deleteBeneficiary(beneficiary)
            } catch (_: Throwable) {}
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        _biometricEnabled.value = enabled
        val editor = prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled)
        val uid = _currentUser.value?.id?.trim()
        if (!uid.isNullOrBlank()) {
            editor.putBoolean("key_biometric_enabled_$uid", enabled)
        }
        editor.apply()
        repoScope.launch { persistCurrentUserToLocalDatabase() }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        _appLockEnabled.value = enabled
        val editor = prefs.edit().putBoolean(KEY_APP_LOCK_ENABLED, enabled)
        val uid = _currentUser.value?.id?.trim()
        if (!uid.isNullOrBlank()) {
            editor.putBoolean("key_app_lock_enabled_$uid", enabled)
        }
        editor.apply()
        repoScope.launch { persistCurrentUserToLocalDatabase() }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        _notificationsEnabled.value = enabled
        val editor = prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled)
        val uid = _currentUser.value?.id?.trim()
        if (!uid.isNullOrBlank()) {
            editor.putBoolean("key_notifications_enabled_$uid", enabled)
        }
        editor.apply()
        repoScope.launch { persistCurrentUserToLocalDatabase() }
    }

    fun verifyPin(pin: String): Boolean {
        val uid = _currentUser.value?.id?.trim()
        val savedPin = if (!uid.isNullOrBlank() && prefs.contains("key_security_pin_$uid")) {
            prefs.getString("key_security_pin_$uid", "1234")
        } else {
            prefs.getString(KEY_SECURITY_PIN, "1234")
        } ?: "1234"
        return savedPin == pin
    }

    fun updatePin(newPin: String) {
        val editor = prefs.edit().putString(KEY_SECURITY_PIN, newPin)
        val uid = _currentUser.value?.id?.trim()
        if (!uid.isNullOrBlank()) {
            editor.putString("key_security_pin_$uid", newPin)
        }
        editor.apply()
        repoScope.launch { persistCurrentUserToLocalDatabase() }
    }

    fun markNotificationRead(id: String) {
        _inAppNotifications.value = _inAppNotifications.value.map {
            if (it.id == id) it.copy(isRead = true) else it
        }
    }

    fun clearAllNotifications() {
        _inAppNotifications.value = emptyList()
    }

    // --- GSUBZ VTU EDGE SERVICE INTEGRATION (https://yjymxdzdhvbdjramlipg.supabase.co/functions/v1/Gsubz-VTU-Services) ---
    suspend fun executeGsubzVtu(
        serviceType: String,
        provider: String,
        recipient: String,
        amount: Double,
        planId: String? = null,
        meterNumber: String? = null,
        smartcardNumber: String? = null
    ): GsubzOrderResult {
        val current = _currentUser.value
        val result = gsubzClient.executeVtuOrder(
            serviceType = serviceType,
            provider = provider,
            recipient = recipient,
            amount = amount,
            planId = planId,
            meterNumber = meterNumber,
            smartcardNumber = smartcardNumber,
            userToken = _accessToken.value,
            userId = current?.id,
            userEmail = current?.email
        )
        if (result.isSuccess) {
            // Refresh wallet balance from Supabase public.users after Edge Function execution
            syncRemoteProfileBalance()
        }
        return result
    }

    // --- FLUTTERWAVE PAYMENT EDGE SERVICE INTEGRATION ---
    suspend fun initializeFlutterwaveFunding(
        amount: Double,
        email: String,
        name: String,
        phone: String? = null
    ): FlutterwaveInitResult {
        return flutterwaveClient.initializePayment(
            amount = amount,
            email = email,
            name = name,
            phone = phone,
            userToken = _accessToken.value
        )
    }

    suspend fun verifyAndCreditFlutterwavePayment(
        txRef: String,
        amount: Double
    ): FlutterwaveVerifyResult {
        val result = flutterwaveClient.verifyPayment(txRef, amount, _accessToken.value)
        if (result.isSuccess) {
            val activeUser = _currentUser.value
            val fundingEntity = TransactionEntity(
                userId = activeUser?.id?.trim().orEmpty(),
                reference = result.txRef,
                serviceType = "WALLET_FUNDING",
                provider = "Flutterwave",
                recipient = activeUser?.email.orEmpty(),
                amount = result.amount,
                discountOrCashback = 0.0,
                status = "SUCCESSFUL",
                timestamp = System.currentTimeMillis(),
                tokenOrDetails = "Flutterwave Payment Verified (${result.message})",
                customerName = activeUser?.fullName.orEmpty()
            )
            recordTransaction(fundingEntity)
            syncRemoteProfileBalance()
        }
        return result
    }

    suspend fun login(email: String, password: String): AuthResult {
        val result = authClient.login(email, password)
        if (result is AuthResult.Success) {
            saveSession(result.user, result.session?.accessToken)
        }
        return result
    }

    suspend fun signUp(email: String, password: String, fullName: String, phone: String?): AuthResult {
        val result = authClient.signUp(email, password, fullName, phone)
        if (result is AuthResult.Success && result.session != null) {
            setNewAccountZeroBalance(result.user.email, result.user.id)
            saveSession(result.user, result.session.accessToken)
        }
        return result
    }

    suspend fun sendPasswordResetEmail(email: String): AuthResult {
        return authClient.sendPasswordResetEmail(email)
    }

    suspend fun resetPasswordWithOtp(email: String, otpToken: String, newPassword: String): AuthResult {
        val result = authClient.resetPasswordWithOtp(email, otpToken, newPassword)
        if (result is AuthResult.Success && result.session != null) {
            saveSession(result.user, result.session.accessToken)
        }
        return result
    }

    suspend fun updateEmail(newEmail: String): AuthResult {
        val token = _accessToken.value
        val result = authClient.updateEmail(newEmail, token)
        if (result is AuthResult.Success) {
            val updatedUser = _currentUser.value?.copy(email = newEmail.trim())
                ?: result.user
            _currentUser.value = updatedUser
            prefs.edit()
                .putString(KEY_USER_EMAIL, updatedUser.email)
                .putString("key_user_email_${updatedUser.id}", updatedUser.email)
                .apply()
            repoScope.launch { persistCurrentUserToLocalDatabase(updatedUser) }
        }
        return result
    }

    suspend fun logout(): Boolean {
        val token = _accessToken.value
        val remoteSuccess = authClient.logout(token)
        try {
            authRepo.signOut()
        } catch (_: Throwable) {}
        clearSession()
        return remoteSuccess
    }

    // --- Full Supabase Auth Manager 4-Flow Methods ---

    val isLiveConfigured: Boolean
        get() = authRepo.isLiveConfigured || authClient.isLiveConfigured

    fun isEmailRegistered(email: String): Boolean {
        val clean = email.trim().lowercase()
        if (clean.isBlank()) return false
        val registered = getRegisteredEmails()
        return clean in registered
    }

    fun markEmailAsRegistered(email: String) {
        val clean = email.trim().lowercase()
        if (clean.isBlank()) return
        val deleted = (prefs.getStringSet(KEY_DELETED_EMAILS, emptySet()) ?: emptySet()).toMutableSet()
        deleted.remove(clean)
        val current = getRegisteredEmails().toMutableSet()
        current.add(clean)
        prefs.edit()
            .putStringSet(KEY_REGISTERED_EMAILS, current)
            .putStringSet(KEY_DELETED_EMAILS, deleted)
            .apply()
    }

    fun removeEmailFromRegistered(email: String) {
        val clean = email.trim().lowercase()
        if (clean.isBlank()) return
        val current = getRegisteredEmails().toMutableSet()
        current.remove(clean)
        val deleted = (prefs.getStringSet(KEY_DELETED_EMAILS, emptySet()) ?: emptySet()).toMutableSet()
        deleted.add(clean)
        prefs.edit()
            .putStringSet(KEY_REGISTERED_EMAILS, current)
            .putStringSet(KEY_DELETED_EMAILS, deleted)
            .apply()
    }

    fun getRegisteredEmails(): Set<String> {
        val deleted = (prefs.getStringSet(KEY_DELETED_EMAILS, emptySet()) ?: emptySet()).map { it.lowercase().trim() }.toSet()
        val stored = prefs.getStringSet(KEY_REGISTERED_EMAILS, null)
        val activeEmail = prefs.getString(KEY_USER_EMAIL, null)?.lowercase()?.trim()
        val combined = (stored ?: emptySet()).map { it.lowercase().trim() }.toMutableSet()
        if (!activeEmail.isNullOrBlank()) {
            combined.add(activeEmail)
        }
        combined.removeAll(deleted)
        return combined
    }

    suspend fun deleteAccount(): JanAuthResult {
        // Execute supabase.rpc('delete_user')
        val res = authRepo.deleteCurrentUserAccount()
        if (res is JanAuthResult.Error) {
            return res
        }

        val currentUserId = (_currentUser.value?.id ?: prefs.getString(KEY_USER_ID, null))?.trim().orEmpty()
        val currentEmail = _currentUser.value?.email?.lowercase()?.trim()
            ?: prefs.getString(KEY_USER_EMAIL, null)?.lowercase()?.trim()

        if (!currentEmail.isNullOrBlank()) {
            removeEmailFromRegistered(currentEmail)
            val deleted = (prefs.getStringSet(KEY_DELETED_EMAILS, emptySet()) ?: emptySet()).toMutableSet()
            deleted.add(currentEmail)
            prefs.edit().putStringSet(KEY_DELETED_EMAILS, deleted).apply()
        }

        if (currentUserId.isNotBlank()) {
            try {
                transactionDao.clearAllForUser(currentUserId)
                beneficiaryDao.clearAllForUser(currentUserId)
                userProfileDao.deleteUserProfile(currentUserId)
                AppDatabase.deleteDatabaseForUser(context, currentUserId)
            } catch (_: Throwable) {}
        }

        // Reset balances and per-user local security preferences
        val editor = prefs.edit()
            .remove(KEY_WALLET_BALANCE)
            .remove(KEY_CASHBACK_BALANCE)
            .remove(KEY_SECURITY_PIN)
            .remove(KEY_BIOMETRIC_ENABLED)
            .remove(KEY_APP_LOCK_ENABLED)
            .remove(KEY_NOTIFICATIONS_ENABLED)
        if (!currentEmail.isNullOrBlank()) {
            val clean = currentEmail.trim().lowercase()
            editor.remove("key_wallet_balance_$clean")
            editor.remove("key_cashback_balance_$clean")
        }
        if (currentUserId.isNotBlank()) {
            editor
                .remove("key_user_email_$currentUserId")
                .remove("key_user_name_$currentUserId")
                .remove("key_user_phone_$currentUserId")
                .remove("key_nin_hash_$currentUserId")
                .remove("key_va_number_$currentUserId")
                .remove("key_va_bank_$currentUserId")
                .remove("key_va_name_$currentUserId")
                .remove("key_dynamic_acc_number_$currentUserId")
                .remove("key_dynamic_acc_bank_$currentUserId")
                .remove("key_dynamic_acc_name_$currentUserId")
                .remove("key_dynamic_acc_amount_$currentUserId")
                .remove("key_wallet_balance_$currentUserId")
                .remove("key_cashback_balance_$currentUserId")
                .remove("key_security_pin_$currentUserId")
                .remove("key_biometric_enabled_$currentUserId")
                .remove("key_app_lock_enabled_$currentUserId")
                .remove("key_notifications_enabled_$currentUserId")
                .remove("key_business_account_number_$currentUserId")
                .remove("key_business_bank_name_$currentUserId")
                .remove("key_business_account_name_$currentUserId")
        }
        editor.apply()

        _walletBalance.value = 0.0
        _cashbackBalance.value = 0.0

        clearSession()
        return JanAuthResult.Success
    }

    suspend fun authKtSignIn(email: String, password: String): JanAuthResult {
        val result = authRepo.signIn(email, password)
        if (result is JanAuthResult.Success) {
            markEmailAsRegistered(email)
        }
        return result
    }

    suspend fun authKtVerifySignInCode(email: String, code: String): JanAuthResult {
        val result = authRepo.verifySignInCode(email, code)
        if (result is JanAuthResult.Success) {
            markEmailAsRegistered(email)
            val realToken = authRepo.currentAccessToken() ?: ("session_" + UUID.randomUUID().toString())
            val realRefreshToken = try { authRepo.supabase?.auth?.currentSessionOrNull()?.refreshToken } catch (_: Throwable) { null }
            val currentAuthUser = try { authRepo.supabase?.auth?.currentUserOrNull() } catch (_: Throwable) { null }
                ?: authRepo.lastPasswordVerifiedUser
            val realUserId = currentAuthUser?.id ?: authRepo.currentUserId() ?: UUID.randomUUID().toString()
            val meta = currentAuthUser?.userMetadata

            val metaName = meta?.get("full_name")?.toString()?.trim('"')?.takeIf { it.isNotBlank() && it != "null" }
            val metaPhone = sanitizeRealPhone(meta?.get("phone")?.toString()?.trim('"'))
            val metaNin = meta?.get("nin")?.toString()?.trim('"')?.takeIf { it.isNotBlank() && it != "null" }
            val metaVa = (meta?.get("permanent_account_number")?.toString()?.trim('"')
                ?: meta?.get("virtual_account_number")?.toString()?.trim('"'))?.takeIf {
                !it.equals("null", ignoreCase = true) && it.filter { c -> c.isDigit() }.length in 10..12
            }
            val metaBank = (meta?.get("permanent_account_bank")?.toString()?.trim('"')
                ?: meta?.get("virtual_bank_name")?.toString()?.trim('"'))?.takeIf {
                !it.equals("null", ignoreCase = true) && it.isNotBlank()
            }
            val metaAccName = (meta?.get("permanent_account_name")?.toString()?.trim('"')
                ?: meta?.get("virtual_account_name")?.toString()?.trim('"'))?.takeIf {
                !it.equals("null", ignoreCase = true) && it.isNotBlank()
            }

            // Load existing dedicated local profile for this userId if present
            val existingLocalProfile = try {
                userDedicatedDb(realUserId).userProfileDao().getUserProfile(realUserId)
                    ?: userProfileDao.getUserProfile(realUserId)
            } catch (_: Throwable) { null }

            // Query public.users strictly by id = current user id
            val dbRow = try {
                val sb = authRepo.supabase
                if (sb != null && realUserId.isNotBlank()) {
                    sb.from("users").select {
                        filter { eq("id", realUserId) }
                        limit(1)
                    }.decodeSingleOrNull<JsonObject>()
                } else null
            } catch (_: Throwable) { null }

            val dbFullName = dbRow?.get("full_name")?.toString()?.trim('"')?.takeIf {
                !it.equals("null", ignoreCase = true) && it.isNotBlank()
            }
            val rawDbAccName = (dbRow?.get("account_name")?.toString()?.trim('"')
                ?: dbRow?.get("permanent_account_name")?.toString()?.trim('"')
                ?: dbRow?.get("virtual_account_name")?.toString()?.trim('"'))?.takeIf {
                !it.equals("null", ignoreCase = true) && it.isNotBlank()
            } ?: metaAccName ?: existingLocalProfile?.virtualAccountName
            val resolvedFullName = resolveAccountHolderName(
                rawAccountName = dbFullName ?: metaName ?: existingLocalProfile?.fullName,
                fullName = rawDbAccName,
                email = email
            )
            val dbPhone = sanitizeRealPhone(
                dbRow?.get("phone")?.jsonPrimitive?.contentOrNull
                    ?: dbRow?.get("phone")?.toString()?.trim('"')
            ) ?: metaPhone ?: existingLocalProfile?.phone
            val dbVa = (dbRow?.get("permanent_account_number")?.toString()?.trim('"')
                ?: dbRow?.get("virtual_account_number")?.toString()?.trim('"'))?.takeIf {
                !it.equals("null", ignoreCase = true) && it.filter { c -> c.isDigit() }.length in 10..12
            } ?: metaVa ?: existingLocalProfile?.virtualAccountNumber
            val dbBank = if (dbVa != null) {
                (dbRow?.get("bank_name")?.toString()?.trim('"')
                    ?: dbRow?.get("permanent_account_bank")?.toString()?.trim('"')
                    ?: dbRow?.get("virtual_bank_name")?.toString()?.trim('"')
                    ?: dbRow?.get("virtual_bank")?.toString()?.trim('"'))?.takeIf {
                    !it.equals("null", ignoreCase = true) && it.isNotBlank()
                } ?: metaBank ?: existingLocalProfile?.virtualBankName
            } else null
            val dbAccName = if (dbVa != null) {
                resolveAccountHolderName(rawDbAccName, resolvedFullName, email).ifBlank { null }
            } else null
            val dbNinHash = dbRow?.get("nin_hash")?.toString()?.trim('"')?.takeIf {
                !it.equals("null", ignoreCase = true) && it.isNotBlank()
            } ?: metaNin ?: existingLocalProfile?.ninHash

            val dbWalletBal = dbRow?.get("wallet_balance")?.jsonPrimitive?.doubleOrNull
                ?: dbRow?.get("wallet_balance")?.toString()?.trim('"')?.toDoubleOrNull()
            if (dbWalletBal != null && dbWalletBal >= 0.0) {
                setWalletBalance(dbWalletBal)
            } else if (existingLocalProfile != null && existingLocalProfile.walletBalance > 0.0) {
                setWalletBalance(existingLocalProfile.walletBalance)
            }

            val user = SupabaseUser(
                id = realUserId,
                email = email.trim(),
                fullName = resolvedFullName,
                phone = dbPhone,
                nin = dbNinHash,
                ninHash = dbNinHash,
                ninVerified = !dbNinHash.isNullOrBlank(),
                virtualAccountNumber = dbVa,
                virtualBankName = dbBank,
                virtualAccountName = dbAccName,
                dynamicAccountNumber = existingLocalProfile?.dynamicAccountNumber,
                dynamicBankName = existingLocalProfile?.dynamicBankName,
                dynamicAccountName = existingLocalProfile?.dynamicAccountName,
                dynamicAccountAmount = existingLocalProfile?.dynamicAccountAmount
            )
            saveSession(user, realToken, realRefreshToken)
        }
        return result
    }

    suspend fun authKtSignUp(email: String, password: String): JanAuthResult {
        val cleanEmail = email.trim().lowercase()
        val deleted = (prefs.getStringSet(KEY_DELETED_EMAILS, emptySet()) ?: emptySet()).toMutableSet()
        val reg = (prefs.getStringSet(KEY_REGISTERED_EMAILS, emptySet()) ?: emptySet()).toMutableSet()
        if (cleanEmail in deleted) {
            reg.remove(cleanEmail)
            prefs.edit().putStringSet(KEY_REGISTERED_EMAILS, reg).apply()
        }
        if (!isLiveConfigured && isEmailRegistered(cleanEmail)) {
            return JanAuthResult.Error("This email address has already been registered.")
        }
        val result = authRepo.signUp(cleanEmail, password)
        if (result is JanAuthResult.Error && result.message.contains("already", ignoreCase = true)) {
            if (cleanEmail !in deleted) {
                markEmailAsRegistered(cleanEmail)
            }
        } else if (result is JanAuthResult.Success) {
            deleted.remove(cleanEmail)
            prefs.edit().putStringSet(KEY_DELETED_EMAILS, deleted).apply()
        }
        return result
    }

    suspend fun authKtVerifySignUpCode(
        email: String,
        code: String,
        fullName: String,
        phone: String?,
        newPassword: String? = null
    ): JanAuthResult {
        var result = authRepo.verifySignUpCode(email, code)
        if (result is JanAuthResult.Error) {
            result = authRepo.verifyPasswordResetCode(email, code)
        }
        if (result is JanAuthResult.Success) {
            if (!newPassword.isNullOrBlank()) {
                try {
                    authRepo.setNewPassword(newPassword)
                } catch (_: Throwable) {}
            }
            val cleanFullName = sanitizeFullName(fullName)
            val cleanInputPhone = sanitizeRealPhone(phone)
            try {
                authRepo.supabase?.auth?.updateUser {
                    data = buildJsonObject {
                        put("full_name", JsonPrimitive(cleanFullName))
                        if (!cleanInputPhone.isNullOrBlank()) {
                            put("phone", JsonPrimitive(cleanInputPhone))
                        }
                    }
                }
            } catch (_: Throwable) {}

            val realToken = authRepo.currentAccessToken() ?: ""
            val realRefreshToken = try { authRepo.supabase?.auth?.currentSessionOrNull()?.refreshToken } catch (_: Throwable) { null }
            val currentAuthUser = try { authRepo.supabase?.auth?.currentUserOrNull() } catch (_: Throwable) { null }
            val realUserId = currentAuthUser?.id ?: authRepo.currentUserId() ?: ""

            markEmailAsRegistered(email)
            setNewAccountZeroBalance(email, realUserId)

            if (realUserId.isNotBlank() && (!cleanInputPhone.isNullOrBlank() || cleanFullName.isNotBlank())) {
                try {
                    authRepo.supabase?.from("users")?.update(
                        buildJsonObject {
                            if (!cleanInputPhone.isNullOrBlank()) {
                                put("phone", JsonPrimitive(cleanInputPhone))
                            }
                            if (cleanFullName.isNotBlank()) {
                                put("full_name", JsonPrimitive(cleanFullName))
                            }
                        }
                    ) {
                        filter { eq("id", realUserId) }
                    }
                } catch (_: Throwable) {}
            }

            val meta = currentAuthUser?.userMetadata
            val metaFullName = meta?.get("full_name")?.toString()?.trim('"')?.takeIf {
                it.isNotBlank() && !it.equals("null", ignoreCase = true)
            } ?: cleanFullName

            val dbRow = try {
                val sb = authRepo.supabase
                if (sb != null && realUserId.isNotBlank()) {
                    sb.from("users").select {
                        filter { eq("id", realUserId) }
                        limit(1)
                    }.decodeSingleOrNull<JsonObject>()
                } else null
            } catch (_: Throwable) { null }

            val dbPhone = sanitizeRealPhone(
                dbRow?.get("phone")?.jsonPrimitive?.contentOrNull
                    ?: dbRow?.get("phone")?.toString()?.trim('"')
            ) ?: cleanInputPhone
            val dbVa = (dbRow?.get("permanent_account_number")?.toString()?.trim('"')
                ?: dbRow?.get("virtual_account_number")?.toString()?.trim('"'))?.takeIf {
                !it.equals("null", ignoreCase = true) && it.filter { c -> c.isDigit() }.length in 10..12
            }
            val dbBank = if (dbVa != null) {
                (dbRow?.get("bank_name")?.toString()?.trim('"')
                    ?: dbRow?.get("permanent_account_bank")?.toString()?.trim('"')
                    ?: dbRow?.get("virtual_bank_name")?.toString()?.trim('"')
                    ?: dbRow?.get("virtual_bank")?.toString()?.trim('"'))?.takeIf {
                    !it.equals("null", ignoreCase = true) && it.isNotBlank()
                }
            } else null
            val dbAccName = if (dbVa != null) {
                (dbRow?.get("account_name")?.toString()?.trim('"')
                    ?: dbRow?.get("permanent_account_name")?.toString()?.trim('"')
                    ?: dbRow?.get("virtual_account_name")?.toString()?.trim('"'))?.takeIf {
                    !it.equals("null", ignoreCase = true) && it.isNotBlank()
                }
            } else null
            val dbNinHash = dbRow?.get("nin_hash")?.toString()?.trim('"')?.takeIf {
                !it.equals("null", ignoreCase = true) && it.isNotBlank()
            }

            val user = SupabaseUser(
                id = realUserId,
                email = email.trim(),
                fullName = metaFullName,
                phone = dbPhone,
                nin = dbNinHash,
                ninHash = dbNinHash,
                ninVerified = !dbNinHash.isNullOrBlank(),
                virtualAccountNumber = dbVa,
                virtualBankName = dbBank,
                virtualAccountName = dbAccName
            )
            saveSession(user, realToken, realRefreshToken)
        }
        return result
    }

    suspend fun generateAndAssignVirtualAccount(
        targetUser: SupabaseUser? = _currentUser.value,
        nin: String? = null
    ): FlutterwaveVirtualAccount {
        val current = targetUser ?: _currentUser.value
        val name = sanitizeFullName(current?.fullName)
        val email = current?.email ?: ""
        val phone = sanitizeRealPhone(current?.phone) ?: ""
        val activeNin = nin?.filter { it.isDigit() }?.take(11) ?: current?.nin

        val names = name.trim().split(" ").filter { it.isNotBlank() }
        val firstName = names.firstOrNull() ?: ""
        val lastName = if (names.size > 1) names.drop(1).joinToString(" ") else firstName
        val token = prefs.getString(KEY_ACCESS_TOKEN, null) ?: authClient.supabaseAnonKey
        val anonKey = authClient.supabaseAnonKey

        var vaAccountNum: String? = null
        var vaBankName: String? = null
        var vaAccountName: String? = null

        try {
            val resp = walletRepo.requestDedicatedAccount(
                authToken = token,
                anonKey = anonKey,
                email = email,
                firstName = firstName,
                lastName = lastName,
                phone = phone
            )
            val body = resp.getOrNull()
            if (body?.success == true && !body.accountNumber.isNullOrBlank()) {
                vaAccountNum = body.accountNumber
                vaBankName = body.bankName ?: ""
                vaAccountName = name.trim()
            }
        } catch (_: Exception) {}

        val va = if (!vaAccountNum.isNullOrBlank()) {
            FlutterwaveVirtualAccount(
                accountNumber = vaAccountNum,
                bankName = vaBankName ?: "",
                accountName = vaAccountName ?: "",
                flwRef = "FLW-VA-${UUID.randomUUID().toString().take(8).uppercase()}"
            )
        } else {
            flutterwaveClient.generateVirtualAccount(
                name = name,
                email = email,
                phone = phone,
                nin = activeNin
            )
        }

        val finalAccountNumber = va.accountNumber.trim()
        val finalBankName = va.bankName.trim()
        val finalAccountName = va.accountName.trim()

        if (current != null && finalAccountNumber.isNotBlank()) {
            val updatedUser = current.copy(
                nin = activeNin ?: current.nin,
                ninHash = activeNin ?: current.ninHash,
                ninVerified = !activeNin.isNullOrBlank() || current.ninVerified,
                virtualAccountNumber = finalAccountNumber,
                virtualBankName = finalBankName.ifBlank { null },
                virtualAccountName = finalAccountName.ifBlank { null }
            )
            _currentUser.value = updatedUser
            prefs.edit()
                .putString("key_va_number_${current.id}", finalAccountNumber)
                .putString("key_va_bank_${current.id}", finalBankName)
                .putString("key_va_name_${current.id}", finalAccountName)
                .apply()
            persistCurrentUserToLocalDatabase(updatedUser)
        }

        return va.copy(accountNumber = finalAccountNumber, bankName = finalBankName, accountName = finalAccountName)
    }

    fun getBusinessAccount(): Triple<String, String, String> {
        val current = _currentUser.value
        val uid = current?.id?.trim().orEmpty()
        val userPhone = sanitizeRealPhone(current?.phone)?.filter { it.isDigit() }?.takeLast(10) ?: ""
        val userFullName = current?.fullName ?: ""
        if (uid.isBlank()) {
            return Triple(userPhone, "", userFullName)
        }
        val acct = prefs.getString("key_business_account_number_$uid", userPhone) ?: userPhone
        val bank = prefs.getString("key_business_bank_name_$uid", "") ?: ""
        val name = prefs.getString("key_business_account_name_$uid", userFullName) ?: userFullName
        return Triple(acct, bank, name)
    }

    fun setBusinessAccount(accountNumber: String, bankName: String, accountName: String) {
        val uid = _currentUser.value?.id?.trim().orEmpty()
        val editor = prefs.edit()
            .putString(KEY_BUSINESS_ACCOUNT_NUMBER, accountNumber.trim())
            .putString(KEY_BUSINESS_BANK_NAME, bankName.trim())
            .putString(KEY_BUSINESS_ACCOUNT_NAME, accountName.trim())
        if (uid.isNotBlank()) {
            editor
                .putString("key_business_account_number_$uid", accountNumber.trim())
                .putString("key_business_bank_name_$uid", bankName.trim())
                .putString("key_business_account_name_$uid", accountName.trim())
        }
        editor.apply()
        repoScope.launch { persistCurrentUserToLocalDatabase() }
    }

    suspend fun authKtRequestPasswordReset(email: String): JanAuthResult {
        markEmailAsRegistered(email)
        return authRepo.requestPasswordReset(email)
    }

    suspend fun authKtVerifyPasswordResetCode(email: String, code: String): JanAuthResult {
        return authRepo.verifyPasswordResetCode(email, code)
    }

    suspend fun authKtSetNewPassword(newPass: String): JanAuthResult {
        return authRepo.setNewPassword(newPass)
    }

    suspend fun authKtVerifyResetAndSetNewPassword(email: String, code: String, newPass: String): JanAuthResult {
        val verifyResult = authRepo.verifyPasswordResetCode(email, code)
        if (verifyResult !is JanAuthResult.Success) {
            return verifyResult
        }
        return authRepo.setNewPassword(newPass)
    }

    suspend fun authKtRequestEmailChange(newEmail: String): JanAuthResult {
        return authRepo.requestEmailChange(newEmail)
    }

    suspend fun authKtVerifyEmailChange(newEmail: String, code: String): JanAuthResult {
        val result = authRepo.verifyEmailChangeCode(newEmail, code)
        if (result is JanAuthResult.Success) {
            val cur = _currentUser.value
            if (cur != null) {
                val updatedUser = cur.copy(email = newEmail.trim())
                _currentUser.value = updatedUser
                prefs.edit()
                    .putString(KEY_USER_EMAIL, updatedUser.email)
                    .putString("key_user_email_${updatedUser.id}", updatedUser.email)
                    .apply()
                persistCurrentUserToLocalDatabase(updatedUser)
            }
        }
        return result
    }

    val currentAccessToken: String?
        get() {
            val ktToken = authRepo.currentAccessToken()
            if (!ktToken.isNullOrBlank() && ktToken.startsWith("ey")) return ktToken
            val stateToken = _accessToken.value
            if (!stateToken.isNullOrBlank() && stateToken.startsWith("ey")) return stateToken
            val prefToken = prefs.getString(KEY_ACCESS_TOKEN, null)
            if (!prefToken.isNullOrBlank() && prefToken.startsWith("ey")) return prefToken
            return ktToken ?: stateToken ?: prefToken
        }

    val supabaseAnonKey: String
        get() = authClient.supabaseAnonKey

    private var realtimeJob: Job? = null
    private var realtimeChannel: io.github.jan.supabase.realtime.RealtimeChannel? = null

    init {
        if (_isLoggedIn.value) {
            val initUid = _currentUser.value?.id?.trim()
            if (!initUid.isNullOrBlank()) {
                loadUserSecurityAndSettings(initUid)
            }
            startRealtimeBalanceListener()
        }
    }

    fun startRealtimeBalanceListener() {
        realtimeJob?.cancel()
        val oldChannel = realtimeChannel
        realtimeChannel = null
        if (oldChannel != null) {
            repoScope.launch {
                try { oldChannel.unsubscribe() } catch (_: Throwable) {}
            }
        }
        realtimeJob = repoScope.launch {
            val activeUid = _currentUser.value?.id?.trim()
            if (!activeUid.isNullOrBlank()) {
                syncUserDataFromDatabase(activeUid)
            }
            syncRemoteProfileBalance()

            val client = SupabaseInstance.client
            val userId = try { client?.auth?.currentUserOrNull()?.id } catch (_: Throwable) { null }
                ?: com.example.util.JwtUtils.getUserIdFromJwt(currentAccessToken)
                ?: _currentUser.value?.id
            if (client != null && !userId.isNullOrBlank() && userId != "usr_guest" && userId != "usr_default") {
                try {
                    realtimeChannel = observeWalletBalance(client, userId, this) { liveBal ->
                        setWalletBalance(liveBal)
                    }
                } catch (_: Throwable) {}
            }
        }
    }

    fun updateRemoteUserProfile(phone: String?, fullName: String? = null) {
        val cleanPhone = sanitizeRealPhone(phone)
        val cur = _currentUser.value ?: return
        val cleanName = sanitizeFullName(fullName, cur.email).takeIf { it.isNotBlank() }
        val nextName = cleanName ?: sanitizeFullName(cur.fullName, cur.email)
        val nextPhone = cleanPhone ?: cur.phone
        if (nextPhone != cur.phone || nextName != cur.fullName) {
            val updated = cur.copy(
                phone = nextPhone,
                fullName = nextName
            )
            _currentUser.value = updated
            val ed = prefs.edit()
            if (nextPhone != null) {
                ed.putString("key_user_phone_${cur.id}", nextPhone)
            }
            if (nextName.isNotBlank()) {
                ed.putString("key_user_name_${cur.id}", nextName)
            }
            ed.apply()
            repoScope.launch {
                persistCurrentUserToLocalDatabase(updated)
            }
        }
    }

    /**
     * Queries public.users strictly where id = current logged-in user's id for wallet_balance,
     * phone, permanent_account_number/bank, and nin_hash, and reads full_name from current auth user's metadata "full_name".
     * Also checks public.virtual_accounts and public.wallet_balances for user_id = userId and persists to the user's dedicated Room database.
     */
    suspend fun syncRemoteProfileBalance() {
        val user = _currentUser.value
        val supabase = SupabaseInstance.client
        val authUser = try { supabase?.auth?.currentUserOrNull() } catch (_: Throwable) { null }
        val authUid = authUser?.id
            ?: com.example.util.JwtUtils.getUserIdFromJwt(currentAccessToken)
        if (!authUid.isNullOrBlank() && user != null && user.id != authUid) {
            _currentUser.value = user.copy(id = authUid)
            prefs.edit().putString(KEY_USER_ID, authUid).apply()
        }
        val userId = authUid
            ?: user?.id?.takeIf { it.isNotBlank() && it != "usr_guest" && it != "usr_default" }
        val isValidUuid = !userId.isNullOrBlank() && userId.length == 36 && userId.count { it == '-' } == 4
        if (!isValidUuid || userId.isNullOrBlank()) return

        val authFullName = authUser?.userMetadata?.get("full_name")?.toString()?.trim('"')?.trim()?.takeIf {
            it.isNotBlank() && !it.equals("null", ignoreCase = true)
        }

        // 1. Direct Supabase query on public.users strictly where id = userId
        if (supabase != null) {
            try {
                val data = try {
                    supabase.from("users").select {
                        filter { eq("id", userId) }
                        limit(1)
                    }.decodeSingleOrNull<JsonObject>()
                } catch (_: Throwable) { null }

                val vaTableRow = try {
                    supabase.from("virtual_accounts").select {
                        filter { eq("user_id", userId) }
                        limit(1)
                    }.decodeSingleOrNull<JsonObject>()
                } catch (_: Throwable) { null }

                val walletTableRow = try {
                    supabase.from("wallet_balances").select {
                        filter { eq("user_id", userId) }
                        limit(1)
                    }.decodeSingleOrNull<JsonObject>()
                } catch (_: Throwable) { null }

                if (data != null || vaTableRow != null || walletTableRow != null) {
                    val bal = data?.get("wallet_balance")?.jsonPrimitive?.doubleOrNull
                        ?: data?.get("wallet_balance")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                        ?: data?.get("wallet_balance")?.toString()?.trim('"')?.toDoubleOrNull()
                        ?: walletTableRow?.get("balance")?.jsonPrimitive?.doubleOrNull
                        ?: walletTableRow?.get("wallet_balance")?.jsonPrimitive?.doubleOrNull

                    if (bal != null && bal >= 0.0) {
                        setWalletBalance(bal)
                    }

                    val remotePhone = sanitizeRealPhone(
                        data?.get("phone")?.jsonPrimitive?.contentOrNull
                            ?: data?.get("phone")?.toString()?.trim('"')
                    ) ?: _currentUser.value?.phone
                    val remoteVa = (data?.get("permanent_account_number")?.toString()?.trim('"')
                        ?: data?.get("virtual_account_number")?.toString()?.trim('"')
                        ?: vaTableRow?.get("account_number")?.toString()?.trim('"'))?.takeIf {
                        !it.equals("null", ignoreCase = true) && it.filter { c -> c.isDigit() }.length in 10..12
                    } ?: _currentUser.value?.virtualAccountNumber
                    val remoteBank = if (remoteVa != null) {
                        (data?.get("bank_name")?.toString()?.trim('"')
                            ?: data?.get("permanent_account_bank")?.toString()?.trim('"')
                            ?: data?.get("virtual_bank_name")?.toString()?.trim('"')
                            ?: data?.get("virtual_bank")?.toString()?.trim('"')
                            ?: vaTableRow?.get("bank_name")?.toString()?.trim('"'))?.takeIf {
                            !it.equals("null", ignoreCase = true) && it.isNotBlank()
                        } ?: _currentUser.value?.virtualBankName
                    } else null
                    val rawRemoteAccName = if (remoteVa != null) {
                        (data?.get("account_name")?.toString()?.trim('"')
                            ?: data?.get("permanent_account_name")?.toString()?.trim('"')
                            ?: data?.get("virtual_account_name")?.toString()?.trim('"')
                            ?: vaTableRow?.get("account_name")?.toString()?.trim('"'))?.takeIf {
                            !it.equals("null", ignoreCase = true) && it.isNotBlank()
                        } ?: _currentUser.value?.virtualAccountName
                    } else null
                    val remoteNinHash = (data?.get("nin_hash")?.toString()?.trim('"')
                        ?: vaTableRow?.get("nin_hash")?.toString()?.trim('"'))?.takeIf {
                        !it.equals("null", ignoreCase = true) && it.isNotBlank()
                    } ?: _currentUser.value?.ninHash

                    val remoteFullName = data?.get("full_name")?.toString()?.trim('"')?.takeIf {
                        !it.equals("null", ignoreCase = true) && it.isNotBlank()
                    }

                    val cur = _currentUser.value
                    if (cur != null && cur.id == userId) {
                        val resolvedName = sanitizeFullName(
                            remoteFullName ?: authFullName ?: cur.fullName,
                            cur.email
                        )
                        val remoteAccName = if (remoteVa != null) {
                            resolveAccountHolderName(
                                rawAccountName = rawRemoteAccName,
                                fullName = resolvedName,
                                email = cur.email
                            ).ifBlank { null }
                        } else null
                        val updated = cur.copy(
                            phone = remotePhone,
                            fullName = resolvedName,
                            virtualAccountNumber = remoteVa,
                            virtualBankName = remoteBank,
                            virtualAccountName = remoteAccName,
                            nin = remoteNinHash,
                            ninHash = remoteNinHash,
                            ninVerified = !remoteNinHash.isNullOrBlank()
                        )
                        _currentUser.value = updated
                        val ed = prefs.edit()
                        if (remotePhone != null) {
                            ed.putString("key_user_phone_$userId", remotePhone)
                        } else {
                            ed.remove("key_user_phone_$userId")
                        }
                        ed.putString("key_user_name_$userId", resolvedName)
                        if (remoteVa != null) {
                            ed.putString("key_va_number_$userId", remoteVa)
                            if (remoteBank != null) ed.putString("key_va_bank_$userId", remoteBank) else ed.remove("key_va_bank_$userId")
                            if (remoteAccName != null) ed.putString("key_va_name_$userId", remoteAccName) else ed.remove("key_va_name_$userId")
                        } else {
                            ed.remove("key_va_number_$userId")
                            ed.remove("key_va_bank_$userId")
                            ed.remove("key_va_name_$userId")
                        }
                        if (remoteNinHash != null) {
                            ed.putString("key_nin_hash_$userId", remoteNinHash)
                        } else {
                            ed.remove("key_nin_hash_$userId")
                        }
                        ed.apply()
                        persistCurrentUserToLocalDatabase(updated)
                        if (remoteVa != null && remoteAccName != null &&
                            (!rawRemoteAccName.equals(remoteAccName, ignoreCase = false) ||
                                !remoteFullName.equals(resolvedName, ignoreCase = false))
                        ) {
                            repoScope.launch {
                                persistVirtualAccountToSupabase(
                                    userId = userId,
                                    email = updated.email,
                                    accNumber = remoteVa,
                                    bank = remoteBank.orEmpty(),
                                    accName = remoteAccName,
                                    nin = remoteNinHash,
                                    phone = remotePhone,
                                    fullName = resolvedName
                                )
                            }
                        }
                        return
                    }
                }
            } catch (_: Throwable) {}
        }

        // 2. Fallback REST query to public.users strictly by id=eq.<userId>
        val anonKey = authClient.supabaseAnonKey
        val baseUrl = authClient.supabaseUrl.trimEnd('/')
        if (baseUrl.isBlank() || anonKey.isBlank() || baseUrl.contains("your-project")) return

        val token = currentAccessToken ?: return
        val bearer = if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"
        val urlUsers = "$baseUrl/rest/v1/users?select=*&id=eq.$userId&limit=1"

        val httpClient = OkHttpClient.Builder()
            .connectTimeout(7, TimeUnit.SECONDS)
            .readTimeout(7, TimeUnit.SECONDS)
            .build()

        try {
            val requestUsers = Request.Builder()
                .url(urlUsers)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", bearer)
                .get()
                .build()

            httpClient.newCall(requestUsers).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val array = JSONArray(bodyStr)
                    if (array.length() > 0) {
                        val obj = array.getJSONObject(0)
                        if (obj.has("wallet_balance") && !obj.isNull("wallet_balance")) {
                            val remoteBalance = obj.getDouble("wallet_balance")
                            if (remoteBalance >= 0.0) {
                                setWalletBalance(remoteBalance)
                            }
                        }
                        val remotePhone = sanitizeRealPhone(obj.optString("phone", "")) ?: _currentUser.value?.phone
                        val remoteVa = obj.optString("permanent_account_number").ifBlank { obj.optString("virtual_account_number") }
                            .trim().takeIf { !it.equals("null", ignoreCase = true) && it.filter { c -> c.isDigit() }.length in 10..12 }
                            ?: _currentUser.value?.virtualAccountNumber
                        val remoteBank = if (remoteVa != null) {
                            obj.optString("bank_name")
                                .ifBlank { obj.optString("permanent_account_bank") }
                                .ifBlank { obj.optString("virtual_bank_name") }
                                .ifBlank { obj.optString("virtual_bank") }
                                .trim().takeIf { !it.equals("null", ignoreCase = true) && it.isNotBlank() }
                                ?: _currentUser.value?.virtualBankName
                        } else null
                        val rawRemoteAccName = if (remoteVa != null) {
                            obj.optString("account_name")
                                .ifBlank { obj.optString("permanent_account_name") }
                                .ifBlank { obj.optString("virtual_account_name") }
                                .trim().takeIf { !it.equals("null", ignoreCase = true) && it.isNotBlank() }
                                ?: _currentUser.value?.virtualAccountName
                        } else null
                        val remoteNinHash = obj.optString("nin_hash", "").trim().takeIf {
                            !it.equals("null", ignoreCase = true) && it.isNotBlank()
                        } ?: _currentUser.value?.ninHash
                        val remoteFullName = obj.optString("full_name", "").trim().takeIf {
                            !it.equals("null", ignoreCase = true) && it.isNotBlank()
                        }
                        val cur = _currentUser.value
                        if (cur != null && cur.id == userId) {
                            val resolvedName = sanitizeFullName(
                                remoteFullName ?: authFullName ?: cur.fullName,
                                cur.email
                            )
                            val remoteAccName = if (remoteVa != null) {
                                resolveAccountHolderName(
                                    rawAccountName = rawRemoteAccName,
                                    fullName = resolvedName,
                                    email = cur.email
                                ).ifBlank { null }
                            } else null
                            val updated = cur.copy(
                                phone = remotePhone,
                                fullName = resolvedName,
                                virtualAccountNumber = remoteVa,
                                virtualBankName = remoteBank,
                                virtualAccountName = remoteAccName,
                                nin = remoteNinHash,
                                ninHash = remoteNinHash,
                                ninVerified = !remoteNinHash.isNullOrBlank()
                            )
                            _currentUser.value = updated
                            val ed = prefs.edit()
                            if (remotePhone != null) ed.putString("key_user_phone_$userId", remotePhone) else ed.remove("key_user_phone_$userId")
                            ed.putString("key_user_name_$userId", resolvedName)
                            if (remoteVa != null) {
                                ed.putString("key_va_number_$userId", remoteVa)
                                if (remoteBank != null) ed.putString("key_va_bank_$userId", remoteBank) else ed.remove("key_va_bank_$userId")
                                if (remoteAccName != null) ed.putString("key_va_name_$userId", remoteAccName) else ed.remove("key_va_name_$userId")
                            } else {
                                ed.remove("key_va_number_$userId")
                                ed.remove("key_va_bank_$userId")
                                ed.remove("key_va_name_$userId")
                            }
                            if (remoteNinHash != null) ed.putString("key_nin_hash_$userId", remoteNinHash) else ed.remove("key_nin_hash_$userId")
                            ed.apply()
                            persistCurrentUserToLocalDatabase(updated)
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    suspend fun withdrawFromWallet(amount: Double): Result<Double> = withContext(Dispatchers.IO) {
        val client = authRepo.supabase
        val anonKey = authClient.supabaseAnonKey
        val baseUrl = authClient.supabaseUrl.trimEnd('/')
        val token = currentAccessToken ?: anonKey
        val bearer = if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"

        try {
            if (client != null) {
                val params = buildJsonObject {
                    put("amount", JsonPrimitive(amount))
                }
                client.postgrest.rpc("debit_wallet", params)
            } else {
                val req = Request.Builder()
                    .url("$baseUrl/rest/v1/rpc/debit_wallet")
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", bearer)
                    .addHeader("Content-Type", "application/json")
                    .post(buildJsonObject { put("amount", JsonPrimitive(amount)) }.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                val res = OkHttpClient().newCall(req).execute()
                if (!res.isSuccessful && res.code !in 200..204) {
                    val err = res.body?.string() ?: ""
                    val msg = if (err.contains("insufficient", ignoreCase = true)) {
                        "Insufficient balance"
                    } else if (err.isNotBlank()) {
                        err
                    } else {
                        "Failed to debit wallet (code ${res.code})"
                    }
                    return@withContext Result.failure(Exception(msg))
                }
            }

            // Refresh wallet_balance strictly from public.users (no client-side calculation)
            syncRemoteProfileBalance()
            val finalBal = _walletBalance.value
            Result.success(finalBal)
        } catch (e: Exception) {
            var msg = e.message ?: "Failed to debit wallet"
            if (msg.contains("insufficient", ignoreCase = true)) {
                msg = "Insufficient wallet balance"
            }
            Result.failure(Exception(msg))
        }
    }

    suspend fun refundFromWallet(
        amount: Double,
        targetUserId: String? = null,
        reason: String? = null
    ): Result<Double> = withContext(Dispatchers.IO) {
        val client = authRepo.supabase
        val anonKey = authClient.supabaseAnonKey
        val baseUrl = authClient.supabaseUrl.trimEnd('/')
        val token = currentAccessToken ?: anonKey
        val bearer = if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"
        val activeTarget = targetUserId ?: _currentUser.value?.id

        try {
            if (client != null) {
                val params = buildJsonObject {
                    put("amount", JsonPrimitive(amount))
                    if (activeTarget != null) {
                        put("target_user_id", JsonPrimitive(activeTarget))
                    }
                    if (reason != null) {
                        put("refund_reason", JsonPrimitive(reason))
                    }
                }
                client.postgrest.rpc("refund_wallet", params)
            } else {
                val jsonPayload = buildJsonObject {
                    put("amount", JsonPrimitive(amount))
                    if (activeTarget != null) {
                        put("target_user_id", JsonPrimitive(activeTarget))
                    }
                    if (reason != null) {
                        put("refund_reason", JsonPrimitive(reason))
                    }
                }.toString()

                val req = Request.Builder()
                    .url("$baseUrl/rest/v1/rpc/refund_wallet")
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", bearer)
                    .addHeader("Content-Type", "application/json")
                    .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                    .build()
                val res = OkHttpClient().newCall(req).execute()
                if (!res.isSuccessful && res.code !in 200..204) {
                    val err = res.body?.string() ?: ""
                    return@withContext Result.failure(Exception(if (err.isNotBlank()) err else "Refund failed (${res.code})"))
                }
            }

            // Refresh wallet_balance strictly from public.users (no client-side calculation)
            syncRemoteProfileBalance()
            val finalBal = _walletBalance.value
            Result.success(finalBal)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createDynamicVirtualAccount(
        userId: String? = null,
        email: String? = null,
        amount: Double = 1000.0
    ): Result<DynamicAccountResponse> {
        val current = _currentUser.value
        val effectiveUserId = (userId ?: current?.id ?: prefs.getString(KEY_USER_ID, null))?.trim().orEmpty()
        val effectiveEmail = (email ?: current?.email ?: prefs.getString(KEY_USER_EMAIL, null))?.trim().orEmpty()
        if (effectiveUserId.isBlank() || effectiveEmail.isBlank()) {
            return Result.failure(IllegalStateException("Please sign in to create a dynamic virtual account"))
        }
        val anonKey = authClient.supabaseAnonKey
        val token = currentAccessToken
        val fullNameParts = current?.fullName?.trim()?.split(" ")?.filter { it.isNotBlank() }.orEmpty()

        val result = walletRepo.createDynamicAccount(
            userId = effectiveUserId,
            email = effectiveEmail,
            anonKey = anonKey,
            authToken = token,
            amount = amount,
            firstName = fullNameParts.firstOrNull().orEmpty(),
            lastName = fullNameParts.drop(1).joinToString(" ")
        )

        result.onSuccess { data ->
            val accNum = data.accountNumber?.trim()?.takeIf {
                !it.equals("null", ignoreCase = true) && it.filter { c -> c.isDigit() }.length in 10..12
            }
            val bank = data.bankName?.trim()?.takeIf { !it.equals("null", ignoreCase = true) && it.isNotBlank() }
            val accName = "Wallet Topup"
            val transferAmt = data.transferAmount ?: amount
            if (!accNum.isNullOrBlank() && current != null && current.id == effectiveUserId) {
                val updated = current.copy(
                    dynamicAccountNumber = accNum,
                    dynamicBankName = bank,
                    dynamicAccountName = accName,
                    dynamicAccountAmount = transferAmt
                )
                _currentUser.value = updated
                prefs.edit()
                    .putString("key_dynamic_acc_number_$effectiveUserId", accNum)
                    .putString("key_dynamic_acc_bank_$effectiveUserId", bank)
                    .putString("key_dynamic_acc_name_$effectiveUserId", accName)
                    .putFloat("key_dynamic_acc_amount_$effectiveUserId", transferAmt.toFloat())
                    .apply()

                persistCurrentUserToLocalDatabase(updated)
                syncRemoteProfileBalance()
            }
        }

        return result
    }

    fun updateDynamicVirtualAccount(
        accountNumber: String,
        bankName: String,
        accountName: String? = null,
        amount: Double? = null
    ) {
        val current = _currentUser.value ?: return
        val userId = current.id.trim()
        if (userId.isBlank()) return

        val cleanNumber = accountNumber.trim().takeIf {
            !it.equals("null", ignoreCase = true) && !it.equals("nil", ignoreCase = true) && it.isNotBlank() && it.filter { c -> c.isDigit() }.length in 10..12
        } ?: return
        val cleanBank = bankName.trim().takeIf {
            !it.equals("null", ignoreCase = true) && !it.equals("nil", ignoreCase = true) && it.isNotBlank()
        }
        val effectiveName = "Wallet Topup"

        val updated = current.copy(
            dynamicAccountNumber = cleanNumber,
            dynamicBankName = cleanBank,
            dynamicAccountName = effectiveName,
            dynamicAccountAmount = amount ?: current.dynamicAccountAmount
        )
        _currentUser.value = updated
        val editor = prefs.edit()
            .putString("key_dynamic_acc_number_$userId", cleanNumber)
            .putString("key_dynamic_acc_bank_$userId", cleanBank)
            .putString("key_dynamic_acc_name_$userId", effectiveName)
        if (amount != null) {
            editor.putFloat("key_dynamic_acc_amount_$userId", amount.toFloat())
        }
        editor.apply()
        repoScope.launch { persistCurrentUserToLocalDatabase(updated) }
    }

    suspend fun createPermanentVirtualAccount(
        userId: String? = null,
        email: String? = null,
        nin: String? = null,
        phone: String? = null
    ): Result<VirtualAccountResponse> {
        val current = _currentUser.value
        val effectiveUserId = (userId ?: current?.id ?: prefs.getString(KEY_USER_ID, null))?.trim().orEmpty()
        val effectiveEmail = (email ?: current?.email ?: prefs.getString(KEY_USER_EMAIL, null))?.trim().orEmpty()
        if (effectiveUserId.isBlank() || effectiveEmail.isBlank()) {
            return Result.failure(IllegalStateException("Please sign in to create a permanent virtual account"))
        }
        val anonKey = authClient.supabaseAnonKey
        val token = currentAccessToken
        val cleanNin = nin ?: current?.ninHash ?: current?.nin
        val cleanPhone = sanitizeRealPhone(phone ?: current?.phone) ?: ""
        val fullNameParts = current?.fullName?.trim()?.split(" ")?.filter { it.isNotBlank() }.orEmpty()

        val result = walletRepo.requestDedicatedAccount(
            authToken = token ?: anonKey,
            anonKey = anonKey,
            email = effectiveEmail,
            firstName = fullNameParts.firstOrNull().orEmpty(),
            lastName = fullNameParts.drop(1).joinToString(" "),
            phone = cleanPhone,
            nin = cleanNin
        )

        result.onSuccess { data ->
            val accNum = data.accountNumber?.trim()?.takeIf {
                !it.equals("null", ignoreCase = true) && it.filter { c -> c.isDigit() }.length in 10..12
            }
            val bank = data.bankName?.trim()?.takeIf { !it.equals("null", ignoreCase = true) && it.isNotBlank() } ?: ""
            if (!accNum.isNullOrBlank()) {
                val acctName = current?.fullName?.trim().orEmpty()
                updateUserVirtualAccount(accNum, bank, acctName, cleanNin)
            }
        }

        return result
    }

    fun updateUserVirtualAccount(
        accountNumber: String,
        bankName: String,
        accountName: String? = null,
        nin: String? = null
    ) {
        val current = _currentUser.value ?: return
        val userId = current.id.trim()
        if (userId.isBlank()) return

        val cleanNumber = accountNumber.trim().takeIf {
            !it.equals("null", ignoreCase = true) && !it.equals("nil", ignoreCase = true) && it.isNotBlank() && it.filter { c -> c.isDigit() }.length in 10..12
        }
        val cleanBank = if (cleanNumber != null) {
            bankName.trim().takeIf {
                !it.equals("null", ignoreCase = true) && !it.equals("nil", ignoreCase = true) && it.isNotBlank()
            }
        } else null
        val resolvedFullName = sanitizeFullName(current.fullName, current.email)
        val effectiveName = if (cleanNumber != null) {
            resolveAccountHolderName(
                rawAccountName = accountName ?: current.virtualAccountName,
                fullName = resolvedFullName,
                email = current.email
            ).ifBlank { null }
        } else null
        val effectiveNin = nin?.trim()?.takeIf {
            !it.equals("null", ignoreCase = true) && !it.equals("nil", ignoreCase = true) && it.isNotBlank()
        } ?: current.ninHash ?: current.nin

        val updated = current.copy(
            fullName = resolvedFullName.ifBlank { current.fullName },
            virtualAccountNumber = cleanNumber,
            virtualBankName = cleanBank,
            virtualAccountName = effectiveName,
            nin = effectiveNin,
            ninHash = effectiveNin,
            ninVerified = !effectiveNin.isNullOrBlank()
        )
        _currentUser.value = updated

        val editor = prefs.edit()
        if (cleanNumber != null) {
            editor.putString("key_va_number_$userId", cleanNumber)
            if (cleanBank != null) editor.putString("key_va_bank_$userId", cleanBank) else editor.remove("key_va_bank_$userId")
            if (effectiveName != null) editor.putString("key_va_name_$userId", effectiveName) else editor.remove("key_va_name_$userId")
        } else {
            editor.remove("key_va_number_$userId")
            editor.remove("key_va_bank_$userId")
            editor.remove("key_va_name_$userId")
        }
        if (!effectiveNin.isNullOrBlank()) {
            editor.putString("key_nin_hash_$userId", effectiveNin)
        }
        editor.apply()

        repoScope.launch {
            persistCurrentUserToLocalDatabase(updated)
            if (cleanNumber != null) {
                persistVirtualAccountToSupabase(
                    userId = updated.id,
                    email = updated.email,
                    accNumber = cleanNumber,
                    bank = cleanBank.orEmpty(),
                    accName = effectiveName.orEmpty(),
                    nin = effectiveNin,
                    phone = updated.phone,
                    fullName = updated.fullName
                )
                syncRemoteProfileBalance()
            }
        }
    }

    fun clearUserVirtualAccount(userId: String) {
        val current = _currentUser.value
        val targetId = userId.ifBlank { current?.id.orEmpty() }
        val updated = current?.copy(
            virtualAccountNumber = null,
            virtualBankName = null,
            virtualAccountName = null
        )
        if (updated != null) {
            _currentUser.value = updated
        }
        prefs.edit()
            .remove(KEY_USER_VA_NUMBER)
            .remove(KEY_USER_VA_BANK)
            .remove(KEY_USER_VA_NAME)
            .apply {
                if (targetId.isNotBlank()) {
                    remove("key_va_number_$targetId")
                    remove("key_va_bank_$targetId")
                    remove("key_va_name_$targetId")
                }
            }
            .apply()

        if (targetId.isNotBlank()) {
            repoScope.launch {
                if (updated != null) {
                    persistCurrentUserToLocalDatabase(updated)
                }
                try {
                    val client = SupabaseInstance.client
                    if (client != null) {
                        try {
                            client.from("users").update(mapOf("virtual_account_number" to null)) {
                                filter { eq("id", targetId) }
                            }
                        } catch (_: Throwable) {}
                        try {
                            client.from("users").update(mapOf("permanent_account_number" to null)) {
                                filter { eq("id", targetId) }
                            }
                        } catch (_: Throwable) {}
                    }
                } catch (_: Throwable) {}
            }
        }
    }

    private suspend fun persistVirtualAccountToSupabase(
        userId: String,
        email: String,
        accNumber: String,
        bank: String,
        accName: String,
        nin: String? = null,
        phone: String? = null,
        fullName: String? = null
    ) {
        val anonKey = authClient.supabaseAnonKey
        val baseUrl = authClient.supabaseUrl.trimEnd('/')
        if (baseUrl.isBlank() || anonKey.isBlank() || baseUrl.contains("your-project") || userId.isBlank()) return
        val token = currentAccessToken ?: anonKey
        val bearer = if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"
        val cleanPhone = sanitizeRealPhone(phone)
        val cleanFullName = sanitizeFullName(fullName)

        val client = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()

        // 1. Update Auth user_metadata only if full_name is non-empty
        if (cleanFullName.isNotBlank()) {
            try {
                SupabaseInstance.client?.auth?.updateUser {
                    data = kotlinx.serialization.json.buildJsonObject {
                        put("full_name", kotlinx.serialization.json.JsonPrimitive(cleanFullName))
                    }
                }
            } catch (_: Throwable) {}
        }

        // 2. Persist to public.users where id = userId using separate column-safe PATCH payloads
        val candidatePayloads = mutableListOf<org.json.JSONObject>()
        if (cleanPhone != null || cleanFullName.isNotBlank()) {
            candidatePayloads.add(org.json.JSONObject().apply {
                if (cleanPhone != null) put("phone", cleanPhone)
                if (cleanFullName.isNotBlank()) put("full_name", cleanFullName)
            })
        }
        if (accNumber.isNotBlank()) {
            candidatePayloads.add(org.json.JSONObject().apply {
                put("virtual_account_number", accNumber)
                if (bank.isNotBlank()) put("virtual_bank", bank)
            })
            candidatePayloads.add(org.json.JSONObject().apply {
                put("virtual_account_number", accNumber)
                if (bank.isNotBlank()) put("virtual_bank_name", bank)
                if (accName.isNotBlank()) put("virtual_account_name", accName)
            })
            candidatePayloads.add(org.json.JSONObject().apply {
                put("permanent_account_number", accNumber)
                if (bank.isNotBlank()) put("permanent_account_bank", bank)
                if (accName.isNotBlank()) put("permanent_account_name", accName)
            })
            if (accName.isNotBlank()) {
                candidatePayloads.add(org.json.JSONObject().apply {
                    put("account_name", accName)
                })
            }
        }

        val filterParam = "id=eq.$userId"

        for (payload in candidatePayloads) {
            if (payload.length() == 0) continue
            try {
                val requestBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val req = Request.Builder()
                    .url("$baseUrl/rest/v1/users?$filterParam")
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", bearer)
                    .addHeader("Prefer", "return=minimal")
                    .patch(requestBody)
                    .build()
                client.newCall(req).execute().close()
            } catch (_: Throwable) {}
        }

        // 3. Also upsert to public.virtual_accounts keyed by user_id = userId
        if (accNumber.isNotBlank()) {
            try {
                val vaJson = org.json.JSONObject().apply {
                    put("user_id", userId)
                    put("email", email)
                    put("account_number", accNumber)
                    if (bank.isNotBlank()) put("bank_name", bank)
                    if (accName.isNotBlank()) put("account_name", accName)
                }
                val req = Request.Builder()
                    .url("$baseUrl/rest/v1/virtual_accounts?on_conflict=user_id")
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", bearer)
                    .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                    .post(vaJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                client.newCall(req).execute().close()
            } catch (_: Throwable) {}
        }
    }

    fun setWalletBalance(balance: Double) {
        val current = _walletBalance.value
        _walletBalance.value = balance
        val sharedCurrent = SharedWalletObserver.liveBalance.value
        if (sharedCurrent == null || kotlin.math.abs(sharedCurrent - balance) >= 0.0001 || kotlin.math.abs(current - balance) >= 0.0001) {
            SharedWalletObserver.updateBalance(balance)
        }
        val uid = _currentUser.value?.id?.trim()
        if (!uid.isNullOrBlank()) {
            prefs.edit().putFloat("key_wallet_balance_$uid", balance.toFloat()).apply()
            repoScope.launch {
                try {
                    userProfileDao.updateWalletBalance(uid, balance)
                    userDedicatedDb(uid).userProfileDao().updateWalletBalance(uid, balance)
                } catch (_: Throwable) {}
            }
        }
    }

    private fun saveSession(user: SupabaseUser, token: String?, refreshToken: String? = null) {
        val userId = user.id.trim()
        val previousUserId = _currentUser.value?.id ?: prefs.getString(KEY_USER_ID, null)
        if (!previousUserId.isNullOrBlank() && previousUserId != userId) {
            // Signed-in user ID changed: clear previous user's active in-memory state
            _walletBalance.value = 0.0
            _cashbackBalance.value = 0.0
            SharedWalletObserver.clear()
            _currentUserApiKey.value = null
            _inAppNotifications.value = emptyList()
        }

        val cleanStoredVa = user.virtualAccountNumber?.trim()?.takeIf {
            !it.equals("null", ignoreCase = true) && !it.equals("nil", ignoreCase = true) && it.filter { c -> c.isDigit() }.length in 10..12
        }

        val cleanStoredBank = if (cleanStoredVa != null) {
            user.virtualBankName?.trim()?.takeIf {
                !it.equals("null", ignoreCase = true) && !it.equals("nil", ignoreCase = true) && it.isNotBlank()
            }
        } else null

        val resolvedFullName = sanitizeFullName(user.fullName, user.email)

        val cleanStoredName = if (cleanStoredVa != null) {
            resolveAccountHolderName(
                rawAccountName = user.virtualAccountName,
                fullName = resolvedFullName,
                email = user.email
            ).ifBlank { null }
        } else null

        val resolvedPhone = sanitizeRealPhone(user.phone)

        val dynamicAccNumber = user.dynamicAccountNumber?.trim()?.takeIf {
            !it.equals("null", ignoreCase = true) && !it.equals("nil", ignoreCase = true) && it.filter { c -> c.isDigit() }.length in 10..12
        }

        val dynamicAccBank = if (dynamicAccNumber != null) {
            user.dynamicBankName?.trim()?.takeIf {
                !it.equals("null", ignoreCase = true) && !it.equals("nil", ignoreCase = true) && it.isNotBlank()
            }
        } else null

        val dynamicAccName = if (dynamicAccNumber != null) "Wallet Topup" else null

        val dynamicAccAmount = if (dynamicAccNumber != null) user.dynamicAccountAmount else null

        val cleanNinHash = (user.ninHash ?: user.nin)?.trim()?.takeIf {
            !it.equals("null", ignoreCase = true) && !it.equals("nil", ignoreCase = true) && it.isNotBlank()
        }

        val resolvedUser = user.copy(
            fullName = resolvedFullName,
            phone = resolvedPhone,
            nin = cleanNinHash,
            ninHash = cleanNinHash,
            ninVerified = user.ninVerified || !cleanNinHash.isNullOrBlank(),
            virtualAccountNumber = cleanStoredVa,
            virtualBankName = cleanStoredBank,
            virtualAccountName = cleanStoredName,
            dynamicAccountNumber = dynamicAccNumber,
            dynamicBankName = dynamicAccBank,
            dynamicAccountName = dynamicAccName,
            dynamicAccountAmount = dynamicAccAmount
        )

        _currentUser.value = resolvedUser
        _accessToken.value = token
        _isLoggedIn.value = true

        loadUserBalance(resolvedUser.email, userId)
        loadUserSecurityAndSettings(userId)

        val editor = prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_USER_EMAIL, resolvedUser.email)
            .putString("key_user_email_$userId", resolvedUser.email)
            .putString("key_user_name_$userId", resolvedUser.fullName)
            .putString(KEY_ACCESS_TOKEN, token)
            .remove(KEY_USER_NAME)
            .remove(KEY_USER_PHONE)
            .remove(KEY_USER_NIN)
            .remove(KEY_USER_VA_NUMBER)
            .remove(KEY_USER_VA_BANK)
            .remove(KEY_USER_VA_NAME)
            .remove(KEY_USER_DYNAMIC_ACC_NUMBER)
            .remove(KEY_USER_DYNAMIC_ACC_BANK)
            .remove(KEY_USER_DYNAMIC_ACC_NAME)
            .remove(KEY_USER_DYNAMIC_ACC_AMOUNT)

        if (resolvedPhone != null) {
            editor.putString("key_user_phone_$userId", resolvedPhone)
        } else {
            editor.remove("key_user_phone_$userId")
        }

        if (!refreshToken.isNullOrBlank()) {
            editor.putString(KEY_REFRESH_TOKEN, refreshToken)
        }

        if (cleanStoredVa != null) {
            editor.putString("key_va_number_$userId", cleanStoredVa)
            if (cleanStoredBank != null) editor.putString("key_va_bank_$userId", cleanStoredBank) else editor.remove("key_va_bank_$userId")
            if (cleanStoredName != null) editor.putString("key_va_name_$userId", cleanStoredName) else editor.remove("key_va_name_$userId")
        } else {
            editor.remove("key_va_number_$userId")
            editor.remove("key_va_bank_$userId")
            editor.remove("key_va_name_$userId")
        }

        if (dynamicAccNumber != null) {
            editor.putString("key_dynamic_acc_number_$userId", dynamicAccNumber)
            if (dynamicAccBank != null) editor.putString("key_dynamic_acc_bank_$userId", dynamicAccBank) else editor.remove("key_dynamic_acc_bank_$userId")
            if (dynamicAccName != null) editor.putString("key_dynamic_acc_name_$userId", dynamicAccName) else editor.remove("key_dynamic_acc_name_$userId")
            if (dynamicAccAmount != null) editor.putFloat("key_dynamic_acc_amount_$userId", dynamicAccAmount.toFloat()) else editor.remove("key_dynamic_acc_amount_$userId")
        } else {
            editor.remove("key_dynamic_acc_number_$userId")
            editor.remove("key_dynamic_acc_bank_$userId")
            editor.remove("key_dynamic_acc_name_$userId")
            editor.remove("key_dynamic_acc_amount_$userId")
        }

        if (!cleanNinHash.isNullOrBlank()) {
            editor.putString("key_nin_hash_$userId", cleanNinHash)
        } else {
            editor.remove("key_nin_hash_$userId")
        }
        editor.apply()

        repoScope.launch {
            persistCurrentUserToLocalDatabase(resolvedUser)
        }
        startRealtimeBalanceListener()

        secureApiKeyStorage.removeLegacySharedEntries()
        if (_currentUserApiKey.value?.userId != resolvedUser.id) {
            _currentUserApiKey.value = null
        }
    }

    fun clearSession() {
        realtimeJob?.cancel()
        realtimeJob = null
        val ch = realtimeChannel
        realtimeChannel = null
        if (ch != null) {
            repoScope.launch {
                try { ch.unsubscribe() } catch (_: Throwable) {}
            }
        }

        _currentUser.value = null
        _accessToken.value = null
        _isLoggedIn.value = false
        _walletBalance.value = 0.0
        _cashbackBalance.value = 0.0
        SharedWalletObserver.clear()
        _currentUserApiKey.value = null
        _inAppNotifications.value = emptyList()

        // Clear only active session pointers; per-user dedicated database and user-scoped keys remain isolated by userId
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, false)
            .putFloat(KEY_WALLET_BALANCE, 0.0f)
            .putFloat(KEY_CASHBACK_BALANCE, 0.0f)
            .remove(KEY_USER_ID)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_USER_NAME)
            .remove(KEY_USER_PHONE)
            .remove(KEY_USER_NIN)
            .remove(KEY_USER_VA_NUMBER)
            .remove(KEY_USER_VA_BANK)
            .remove(KEY_USER_VA_NAME)
            .remove(KEY_USER_DYNAMIC_ACC_NUMBER)
            .remove(KEY_USER_DYNAMIC_ACC_BANK)
            .remove(KEY_USER_DYNAMIC_ACC_NAME)
            .remove(KEY_USER_DYNAMIC_ACC_AMOUNT)
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .apply()
    }

    // ==========================================
    // Developer API Keys & Webhook Management
    // ==========================================

    data class ApiClientServerRecord(
        val userId: String,
        val keyPrefix: String,
        val last4: String,
        val isActive: Boolean,
        val createdOrRotatedDate: String
    )

    private fun resolveEffectiveUserId(fallbackUserId: String): String {
        val authUid = try {
            SupabaseProvider.client?.auth?.currentUserOrNull()?.id
                ?: SupabaseProvider.client?.auth?.currentSessionOrNull()?.user?.id
        } catch (_: Throwable) {
            null
        }
        return authUid?.takeIf { it.isNotBlank() }
            ?: _currentUser.value?.id?.takeIf { it.isNotBlank() && it != "usr_guest" }
            ?: fallbackUserId.trim()
    }

    private fun formatServerTimestamp(raw: String): String {
        val clean = raw.trim()
        if (clean.isBlank() || clean.equals("null", ignoreCase = true)) return ""
        return try {
            val core = clean.replace(" ", "T").take(19)
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            val parsedDate = parser.parse(core)
            if (parsedDate != null) {
                SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(parsedDate)
            } else {
                clean.take(16).replace("T", " ")
            }
        } catch (_: Throwable) {
            clean.take(16).replace("T", " ")
        }
    }

    private fun parseApiClientRow(cleanUid: String, row: JSONObject): ApiClientServerRecord {
        val keyPrefix = row.optString("key_prefix", "")
            .takeIf { !it.equals("null", ignoreCase = true) }
            ?.trim()
            .orEmpty()
        val last4 = row.optString("last4", "")
            .takeIf { !it.equals("null", ignoreCase = true) }
            ?.trim()
            .orEmpty()
        val isActive = if (row.has("is_active") && !row.isNull("is_active")) {
            row.optBoolean("is_active", true)
        } else {
            true
        }
        val rawDate = row.optString("rotated_at", "").takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            ?: row.optString("updated_at", "").takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            ?: row.optString("created_at", "").takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            ?: ""
        val formattedDate = formatServerTimestamp(rawDate)
        return ApiClientServerRecord(
            userId = cleanUid,
            keyPrefix = keyPrefix,
            last4 = last4,
            isActive = isActive,
            createdOrRotatedDate = formattedDate
        )
    }

    /**
     * Queries the Supabase table api_clients for the row where user_id = current logged-in user's id.
     * Selects key_prefix, last4, is_active, and the created/rotated date.
     */
    suspend fun queryApiClientFromServer(userId: String): ApiClientServerRecord? = withContext(Dispatchers.IO) {
        val cleanUid = resolveEffectiveUserId(userId)
        if (cleanUid.isBlank() || cleanUid == "usr_guest" || cleanUid == "default") {
            return@withContext null
        }

        try {
            val client = SupabaseProvider.client
            if (client != null) {
                val rawData: String? = try {
                    client.from("api_clients").select(
                        columns = Columns.list("key_prefix", "last4", "is_active", "created_at", "rotated_at")
                    ) {
                        filter {
                            eq("user_id", cleanUid)
                        }
                        limit(1)
                    }.data
                } catch (_: Throwable) {
                    try {
                        client.from("api_clients").select(
                            columns = Columns.list("key_prefix", "last4", "is_active", "created_at", "updated_at")
                        ) {
                            filter {
                                eq("user_id", cleanUid)
                            }
                            limit(1)
                        }.data
                    } catch (_: Throwable) {
                        try {
                            client.from("api_clients").select(
                                columns = Columns.list("key_prefix", "last4", "is_active", "created_at")
                            ) {
                                filter {
                                    eq("user_id", cleanUid)
                                }
                                limit(1)
                            }.data
                        } catch (_: Throwable) {
                            try {
                                client.from("api_clients").select {
                                    filter {
                                        eq("user_id", cleanUid)
                                    }
                                    limit(1)
                                }.data
                            } catch (_: Throwable) {
                                null
                            }
                        }
                    }
                }

                if (!rawData.isNullOrBlank()) {
                    val jsonArray = JSONArray(rawData)
                    if (jsonArray.length() > 0) {
                        return@withContext parseApiClientRow(cleanUid, jsonArray.getJSONObject(0))
                    }
                }
            }

            // Fallback via PostgREST HTTP if the session JWT was held in repository token storage
            val sessionToken = try {
                SupabaseProvider.client?.auth?.currentAccessTokenOrNull()
                    ?: SupabaseProvider.client?.auth?.currentSessionOrNull()?.accessToken
            } catch (_: Throwable) {
                null
            } ?: currentAccessToken?.takeIf { it.startsWith("eyJ") }

            if (!sessionToken.isNullOrBlank()) {
                val baseUrl = SupabaseProvider.safeUrl.trimEnd('/')
                val anonKey = SupabaseProvider.rawKey
                val req = Request.Builder()
                    .url("$baseUrl/rest/v1/api_clients?user_id=eq.$cleanUid&select=key_prefix,last4,is_active,created_at,rotated_at&limit=1")
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $sessionToken")
                    .get()
                    .build()
                com.example.data.remote.ApiNetworkClient.okHttpClient.newCall(req).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (resp.isSuccessful && bodyStr.isNotBlank()) {
                        val arr = JSONArray(bodyStr)
                        if (arr.length() > 0) {
                            return@withContext parseApiClientRow(cleanUid, arr.getJSONObject(0))
                        }
                    }
                }
            }

            null
        } catch (e: Exception) {
            Log.d("VtuRepository", "queryApiClientFromServer returned: ${e.message}")
            null
        }
    }

    /**
     * Queries api_clients on the server (the source of truth) and reconciles with per-user EncryptedSharedPreferences.
     * Never calls Generate-api-key automatically.
     */
    suspend fun syncApiKeyStateFromServer(userId: String, userEmail: String): UserApiKey? = withContext(Dispatchers.IO) {
        secureApiKeyStorage.removeLegacySharedEntries()
        val effectiveUid = resolveEffectiveUserId(userId)
        if (effectiveUid.isBlank() || effectiveUid == "usr_guest" || effectiveUid == "default") {
            _currentUserApiKey.value = null
            return@withContext null
        }

        // Never show a key saved for a different user
        if (_currentUserApiKey.value?.userId != effectiveUid) {
            _currentUserApiKey.value = null
        }

        val serverRecord = queryApiClientFromServer(effectiveUid)
        if (serverRecord == null) {
            // No row exists on the server for this user
            _currentUserApiKey.value = null
            return@withContext null
        }

        // A row exists on the server; check per-user EncryptedSharedPreferences ("api_key_<userId>")
        val localKey = secureApiKeyStorage.getRealApiKey(effectiveUid)
        val hasValidLocalKey = !localKey.isNullOrBlank() &&
            (serverRecord.last4.isBlank() || localKey.endsWith(serverRecord.last4))

        val webhook = prefs.getString("key_webhook_url_$effectiveUid", "") ?: ""
        val callsToday = prefs.getInt("key_calls_today_$effectiveUid", 0)
        val serverDate = serverRecord.createdOrRotatedDate.ifBlank {
            secureApiKeyStorage.getCreatedAt(effectiveUid).orEmpty()
        }

        val resolvedObj = if (hasValidLocalKey && localKey != null) {
            val pubKey = secureApiKeyStorage.getPublicKey(effectiveUid)
                ?: ("vtu_pub_" + UUID.randomUUID().toString().replace("-", "").take(16))
            UserApiKey(
                userId = effectiveUid,
                userEmail = userEmail,
                apiKey = localKey,
                publicKey = pubKey,
                createdAt = serverDate,
                status = if (serverRecord.isActive) "ACTIVE" else "INACTIVE",
                webhookUrl = webhook,
                callsToday = callsToday,
                keyPrefix = serverRecord.keyPrefix.ifBlank { localKey.take(8) },
                last4 = serverRecord.last4.ifBlank { localKey.takeLast(4) },
                isActive = serverRecord.isActive,
                existsOnServer = true,
                hasLocalKey = true
            )
        } else {
            UserApiKey(
                userId = effectiveUid,
                userEmail = userEmail,
                apiKey = "",
                publicKey = "",
                createdAt = serverDate,
                status = if (serverRecord.isActive) "ACTIVE" else "INACTIVE",
                webhookUrl = webhook,
                callsToday = callsToday,
                keyPrefix = serverRecord.keyPrefix,
                last4 = serverRecord.last4,
                isActive = serverRecord.isActive,
                existsOnServer = true,
                hasLocalKey = false
            )
        }

        _currentUserApiKey.value = resolvedObj
        resolvedObj
    }

    fun getStoredRealApiKey(userId: String, userEmail: String): UserApiKey? {
        secureApiKeyStorage.removeLegacySharedEntries()
        val effectiveUid = resolveEffectiveUserId(userId)
        val realKey = secureApiKeyStorage.getRealApiKey(effectiveUid)
        if (realKey.isNullOrBlank()) return null

        val pubKey = secureApiKeyStorage.getPublicKey(effectiveUid)
            ?: ("vtu_pub_" + UUID.randomUUID().toString().replace("-", "").take(16))
        val createdAt = secureApiKeyStorage.getCreatedAt(effectiveUid) ?: "Active"
        val webhook = prefs.getString("key_webhook_url_$effectiveUid", "") ?: ""
        val callsToday = prefs.getInt("key_calls_today_$effectiveUid", 0)

        val obj = UserApiKey(
            userId = effectiveUid,
            userEmail = userEmail,
            apiKey = realKey,
            publicKey = pubKey,
            createdAt = createdAt,
            status = "ACTIVE",
            webhookUrl = webhook,
            callsToday = callsToday,
            keyPrefix = realKey.take(8),
            last4 = realKey.takeLast(4),
            isActive = true,
            existsOnServer = true,
            hasLocalKey = true
        )
        _currentUserApiKey.value = obj
        return obj
    }

    fun getOrGenerateApiKey(userId: String, userEmail: String): UserApiKey {
        val storedReal = getStoredRealApiKey(userId, userEmail)
        if (storedReal != null) {
            return storedReal
        }
        return _currentUserApiKey.value?.takeIf { it.userId == resolveEffectiveUserId(userId) } ?: UserApiKey(
            userId = userId,
            userEmail = userEmail,
            apiKey = "",
            publicKey = "",
            createdAt = "",
            status = "INACTIVE",
            existsOnServer = false,
            hasLocalKey = false
        )
    }

    suspend fun generateApiKeyRemote(
        userId: String,
        userEmail: String,
        userName: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        val effectiveUid = resolveEffectiveUserId(userId)
        val nameToUse = userName.ifBlank { _currentUser.value?.fullName.orEmpty() }
        val emailToUse = userEmail.ifBlank { _currentUser.value?.email.orEmpty() }

        val sessionToken = try {
            SupabaseProvider.client?.let { client ->
                client.auth.currentAccessTokenOrNull()
                    ?: client.auth.currentSessionOrNull()?.accessToken
            }
        } catch (_: Throwable) {
            null
        } ?: currentAccessToken?.takeIf { it.startsWith("eyJ") }

        val result = apiKeyRepo.generateApiKeyForUser(
            userId = effectiveUid,
            email = emailToUse,
            name = nameToUse,
            userToken = sessionToken
        )

        if (result.isSuccess) {
            val realKey = result.getOrNull()?.trim().orEmpty()
            val serverRecord = queryApiClientFromServer(effectiveUid)
            saveRealApiKeyLocally(
                userId = effectiveUid,
                userEmail = emailToUse,
                realKey = realKey,
                serverRecord = serverRecord
            )
            return@withContext Result.success(realKey)
        }

        val error = result.exceptionOrNull()
        Log.e("VtuRepository", "Supabase Generate-api-key failed: ${error?.message}")
        Result.failure(error ?: Exception("Failed to generate API key from Supabase"))
    }

    suspend fun regenerateApiKeyRemote(
        userId: String,
        userEmail: String,
        userName: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        val effectiveUid = resolveEffectiveUserId(userId)
        val currentKey = secureApiKeyStorage.getRealApiKey(effectiveUid)
            ?: _currentUserApiKey.value?.takeIf { it.userId == effectiveUid }?.apiKey
            ?: ""

        val emailToUse = userEmail.ifBlank { _currentUser.value?.email.orEmpty() }
        val nameToUse = userName.ifBlank { _currentUser.value?.fullName.orEmpty() }

        val sessionToken = try {
            SupabaseProvider.client?.let { client ->
                client.auth.currentAccessTokenOrNull()
                    ?: client.auth.currentSessionOrNull()?.accessToken
            }
        } catch (_: Throwable) {
            null
        } ?: currentAccessToken?.takeIf { it.startsWith("eyJ") }

        val result = apiKeyRepo.regenerateApiKeyForUser(
            userId = effectiveUid,
            email = emailToUse,
            name = nameToUse,
            currentKey = currentKey,
            userToken = sessionToken
        )

        if (result.isSuccess) {
            val newKey = result.getOrNull()?.trim().orEmpty()
            val serverRecord = queryApiClientFromServer(effectiveUid)
            saveRealApiKeyLocally(
                userId = effectiveUid,
                userEmail = emailToUse,
                realKey = newKey,
                serverRecord = serverRecord
            )
            Result.success(newKey)
        } else {
            val error = result.exceptionOrNull()
            Log.e("VtuRepository", "Supabase Regenerate-api-key failed: ${error?.message}")
            Result.failure(error ?: Exception("Failed to regenerate API key from Supabase"))
        }
    }

    fun saveRealApiKeyLocally(
        userId: String,
        userEmail: String,
        realKey: String,
        serverRecord: ApiClientServerRecord? = null
    ): UserApiKey {
        val effectiveUid = userId.trim()
        val pubKey = secureApiKeyStorage.getPublicKey(effectiveUid)
            ?: ("vtu_pub_" + UUID.randomUUID().toString().replace("-", "").take(16))
        val fallbackDateStr = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date())
        val dateStr = serverRecord?.createdOrRotatedDate?.takeIf { it.isNotBlank() } ?: fallbackDateStr
        val existingWebhook = prefs.getString("key_webhook_url_$effectiveUid", "") ?: ""

        // Securely store the real key in EncryptedSharedPreferences under "api_key_<userId>"
        secureApiKeyStorage.saveRealApiKey(
            userId = effectiveUid,
            apiKey = realKey,
            publicKey = pubKey,
            createdAt = dateStr
        )

        // Clear any old placeholder from plaintext SharedPreferences
        prefs.edit().remove("key_api_key_$effectiveUid").apply()

        val prefix = serverRecord?.keyPrefix?.takeIf { it.isNotBlank() } ?: realKey.take(8)
        val suffix = serverRecord?.last4?.takeIf { it.isNotBlank() } ?: realKey.takeLast(4)
        val active = serverRecord?.isActive ?: true

        val apiKeyObj = UserApiKey(
            userId = effectiveUid,
            userEmail = userEmail,
            apiKey = realKey,
            publicKey = pubKey,
            createdAt = dateStr,
            status = if (active) "ACTIVE" else "INACTIVE",
            webhookUrl = existingWebhook,
            callsToday = 0,
            keyPrefix = prefix,
            last4 = suffix,
            isActive = active,
            existsOnServer = true,
            hasLocalKey = realKey.isNotBlank()
        )
        _currentUserApiKey.value = apiKeyObj
        return apiKeyObj
    }

    fun generateNewApiKey(userId: String, userEmail: String): UserApiKey {
        return getOrGenerateApiKey(userId, userEmail)
    }

    fun saveWebhookUrl(userId: String, webhookUrl: String) {
        prefs.edit().putString("key_webhook_url_$userId", webhookUrl).apply()
        _currentUserApiKey.value?.let { current ->
            _currentUserApiKey.value = current.copy(webhookUrl = webhookUrl)
        }
    }

    // ==========================================
    // Developers Forum
    // ==========================================

    fun initForumTopics() {
        if (_forumTopics.value.isEmpty()) {
            _forumTopics.value = createInitialForumTopics()
        }
    }

    fun postForumTopic(
        title: String,
        content: String,
        category: String,
        authorName: String,
        authorEmail: String
    ): ForumTopic {
        val dateStr = SimpleDateFormat("MMM dd • HH:mm", Locale.getDefault()).format(Date())
        val newTopic = ForumTopic(
            id = "topic_" + UUID.randomUUID().toString().take(8),
            authorName = authorName,
            authorEmail = authorEmail,
            title = title,
            content = content,
            category = category,
            timestamp = dateStr,
            likesCount = 0,
            repliesCount = 0,
            isPinned = false,
            isLikedByMe = false,
            replies = emptyList()
        )
        _forumTopics.value = listOf(newTopic) + _forumTopics.value
        return newTopic
    }

    fun addForumReply(topicId: String, content: String, authorName: String): Boolean {
        val dateStr = SimpleDateFormat("MMM dd • HH:mm", Locale.getDefault()).format(Date())
        val reply = ForumReply(
            id = "reply_" + UUID.randomUUID().toString().take(8),
            authorName = authorName,
            authorRole = "Verified Developer",
            content = content,
            timestamp = dateStr
        )
        _forumTopics.value = _forumTopics.value.map { topic ->
            if (topic.id == topicId) {
                topic.copy(
                    repliesCount = topic.repliesCount + 1,
                    replies = topic.replies + reply
                )
            } else topic
        }
        return true
    }

    fun toggleLikeTopic(topicId: String) {
        _forumTopics.value = _forumTopics.value.map { topic ->
            if (topic.id == topicId) {
                val newLiked = !topic.isLikedByMe
                val newLikes = if (newLiked) topic.likesCount + 1 else (topic.likesCount - 1).coerceAtLeast(0)
                topic.copy(isLikedByMe = newLiked, likesCount = newLikes)
            } else topic
        }
    }

    private fun createInitialForumTopics(): List<ForumTopic> {
        return listOf(
            ForumTopic(
                id = "topic_official_guide",
                authorName = "VTU Support Team",
                authorEmail = "danielkaladathompson@gmail.com",
                title = "Official Guide: Authenticating & using your VTU API key",
                content = "Welcome to the Daniel VTU Developer Ecosystem! Every registered account has a unique secret API key. Pass it in your HTTP header as:\nAuthorization: Bearer vtu_live_YOUR_KEY\n\nAll endpoints support real-time vending for MTN, Airtel, GLO, 9mobile airtime, SME data bundles, electricity tokens, and Cable TV recharge. Responses are JSON with HTTP 200 on success.",
                category = "API & Authentication",
                timestamp = "Yesterday",
                likesCount = 24,
                repliesCount = 3,
                isPinned = true,
                isLikedByMe = true,
                replies = listOf(
                    ForumReply(
                        id = "rep_1",
                        authorName = "Alex Chen",
                        authorRole = "Verified Developer",
                        content = "Tested the balance endpoint with cURL and it returned instantly! Latency is awesome.",
                        timestamp = "Yesterday"
                    ),
                    ForumReply(
                        id = "rep_2",
                        authorName = "Chidi K.",
                        authorRole = "Verified Developer",
                        content = "Does the API support custom webhook retry limits when our listener server is down?",
                        timestamp = "10 hours ago"
                    ),
                    ForumReply(
                        id = "rep_3",
                        authorName = "VTU Support Team",
                        authorRole = "Admin / Support",
                        content = "@Chidi K. Yes! Our webhook dispatcher retries up to 5 times with exponential backoff (1m, 5m, 15m, 30m, 1h).",
                        timestamp = "8 hours ago"
                    )
                )
            ),
            ForumTopic(
                id = "topic_webhook_verification",
                authorName = "Sarah O.",
                authorEmail = "sarah.dev@gmail.com",
                title = "Automated SME Data Vending with Node.js & Express Webhook",
                content = "Sharing our boilerplate for anyone building an automated VTU storefront! We set up a POST route to /api/v1/data/purchase using Axios with Authorization: Bearer {vtu_key}. The data is credited within 3-5 seconds and we acknowledge with 200 OK. Happy to answer questions for Node.js developers.",
                category = "Airtime & Data API",
                timestamp = "2 days ago",
                likesCount = 18,
                repliesCount = 2,
                isPinned = false,
                isLikedByMe = false,
                replies = listOf(
                    ForumReply(
                        id = "rep_4",
                        authorName = "Tunde Dev",
                        authorRole = "Verified Developer",
                        content = "Thanks Sarah! Which planId did you use for MTN 1GB SME?",
                        timestamp = "1 day ago"
                    ),
                    ForumReply(
                        id = "rep_5",
                        authorName = "Sarah O.",
                        authorRole = "Verified Developer",
                        content = "Use plan code 'mtn_sme_1gb' with amount 280. It works like a charm!",
                        timestamp = "1 day ago"
                    )
                )
            ),
            ForumTopic(
                id = "topic_webhook_signatures",
                authorName = "Fola Adebayo",
                authorEmail = "fola.backend@tech.io",
                title = "Best practices: Webhook URL setup & Wallet funding events",
                content = "Tip for all new developers: Enter your callback endpoint in the Webhook URL field right under your API Key. Whenever your wallet gets funded via Flutterwave MFB or Dynamic Virtual Account transfer, Daniel VTU delivers a ping with event: 'wallet.funded' and the credit amount so your app updates user balances in real time.",
                category = "Webhooks & Callbacks",
                timestamp = "3 days ago",
                likesCount = 15,
                repliesCount = 1,
                isPinned = false,
                isLikedByMe = true,
                replies = listOf(
                    ForumReply(
                        id = "rep_6",
                        authorName = "VTU Support Team",
                        authorRole = "Admin / Support",
                        content = "Spot on Fola! Remember to respond with HTTP 200 quickly so the webhook queue doesn't re-queue the notification.",
                        timestamp = "2 days ago"
                    )
                )
            ),
            ForumTopic(
                id = "topic_troubleshooting_rates",
                authorName = "Emmanuel K.",
                authorEmail = "emmanuel.code@domain.com",
                title = "Rate Limits & Error 429 prevention on High Volume",
                content = "For developers processing high-volume bulk airtime or data campaigns, the default rate limit is 60 requests/minute. If you need higher burst limits for nationwide campaigns, leave a note here or contact support to raise your account tier.",
                category = "Troubleshooting",
                timestamp = "4 days ago",
                likesCount = 9,
                repliesCount = 0,
                isPinned = false,
                isLikedByMe = false,
                replies = emptyList()
            )
        )
    }
}
