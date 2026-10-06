package com.vtu.app.wallet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.example.auth.SupabaseProvider
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.auth.auth
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

// ==========================================
// A. DATA MODELS
// ==========================================

data class DeleteAccountResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String
)

data class DeleteUserRequest(
    @SerializedName("userId") val userId: String? = null,
    @SerializedName("user_id") val userIdAlt: String? = null,
    @SerializedName("id") val id: String? = null,
    @SerializedName("email") val email: String? = null
)

sealed interface DeleteAccountUiState {
    object Idle : DeleteAccountUiState
    object Loading : DeleteAccountUiState
    object Success : DeleteAccountUiState
    data class Error(val message: String) : DeleteAccountUiState
}

data class CreateDynamicAccountRequest(
    @SerializedName("userId") val userId: String? = null,
    @SerializedName("user_id") val userIdAlt: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("amount") val amount: Double = 1000.0,
    @SerializedName("firstname") val firstname: String? = null,
    @SerializedName("lastname") val lastname: String? = null,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("tx_ref") val txRef: String? = null,
    @SerializedName("currency") val currency: String = "NGN"
)

data class DynamicAccountData(
    @SerializedName("id") val id: String? = null,
    @SerializedName("amount") val amount: Double? = null,
    @SerializedName("account_number") val accountNumberSnake: String? = null,
    @SerializedName("accountNumber") val accountNumberCamel: String? = null,
    @SerializedName("reference") val reference: String? = null,
    @SerializedName("account_bank_name") val accountBankName: String? = null,
    @SerializedName("bank_name") val bankNameSnake: String? = null,
    @SerializedName("bankName") val bankNameCamel: String? = null,
    @SerializedName("account_name") val accountNameSnake: String? = null,
    @SerializedName("accountName") val accountNameCamel: String? = null,
    @SerializedName("account_type") val accountType: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("account_expiration_datetime") val accountExpirationDatetime: String? = null,
    @SerializedName("note") val note: String? = null,
    @SerializedName("customer_id") val customerId: String? = null,
    @SerializedName("created_datetime") val createdDatetime: String? = null,
    @SerializedName("currency") val currency: String? = null,
    @SerializedName("narration") val narration: String? = null
)

data class DynamicAccountError(
    @SerializedName("type") val type: String? = null,
    @SerializedName("code") val code: String? = null,
    @SerializedName("message") val message: String? = null
)

data class DynamicAccountResponse(
    @SerializedName("status") val status: String? = null,
    @SerializedName("success") val success: Boolean? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: DynamicAccountData? = null,
    @SerializedName("error") val error: DynamicAccountError? = null,
    @SerializedName("account_number") val accountNumberSnake: String? = null,
    @SerializedName("accountNumber") val accountNumberCamel: String? = null,
    @SerializedName("account_bank_name") val accountBankName: String? = null,
    @SerializedName("bank_name") val bankNameSnake: String? = null,
    @SerializedName("bankName") val bankNameCamel: String? = null,
    @SerializedName("account_name") val accountNameSnake: String? = null,
    @SerializedName("accountName") val accountNameCamel: String? = null,
    @SerializedName("order_ref") val orderRef: String? = null,
    @SerializedName("flw_ref") val flwRef: String? = null
) {
    val accountNumber: String?
        get() = data?.accountNumberSnake?.takeIf { it.isNotBlank() }
            ?: data?.accountNumberCamel?.takeIf { it.isNotBlank() }
            ?: accountNumberSnake?.takeIf { it.isNotBlank() }
            ?: accountNumberCamel

    val bankName: String
        get() = data?.accountBankName?.takeIf { it.isNotBlank() }
            ?: data?.bankNameSnake?.takeIf { it.isNotBlank() }
            ?: data?.bankNameCamel?.takeIf { it.isNotBlank() }
            ?: accountBankName?.takeIf { it.isNotBlank() }
            ?: bankNameSnake?.takeIf { it.isNotBlank() }
            ?: bankNameCamel
            ?: ""

    val accountName: String
        get() = "Wallet Topup"

    val expirationDatetime: String?
        get() = data?.accountExpirationDatetime

    val transferAmount: Double?
        get() = data?.amount

    val transferNote: String?
        get() = data?.note ?: data?.narration

    val errorMessage: String?
        get() = error?.message ?: message
}

sealed interface DynamicAccountUiState {
    object Idle : DynamicAccountUiState
    object Loading : DynamicAccountUiState
    data class Success(
        val accountNumber: String,
        val bankName: String,
        val accountName: String,
        val amount: Double? = null,
        val expiration: String? = null,
        val note: String? = null
    ) : DynamicAccountUiState
    data class Error(val message: String) : DynamicAccountUiState
}

data class CreateVirtualAccountRequest(
    @SerializedName("email") val email: String,
    @SerializedName("firstName") val firstName: String,
    @SerializedName("lastName") val lastName: String,
    @SerializedName("phone") val phone: String,
    @SerializedName("nin") val nin: String? = null
)

data class VirtualAccountResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("accountNumber") val accountNumber: String?,
    @SerializedName("bankName") val bankName: String?,
    @SerializedName("message") val message: String?,
    @SerializedName("isKycVerified") val isKycVerified: Boolean? = null,
    @SerializedName("isKycRequired") val isKycRequired: Boolean? = null
)

sealed interface VirtualAccountUiState {
    object Idle : VirtualAccountUiState
    object Loading : VirtualAccountUiState
    data class Success(val accountNumber: String, val bankName: String) : VirtualAccountUiState
    data class Error(val message: String) : VirtualAccountUiState
}

// ==========================================
// B. RETROFIT SERVICE & NETWORK MODULE
// ==========================================

interface BackendApiService {
    // Primary Delete Account Endpoint (calls delete-account Edge Function as in deleteAccount.ts)
    @POST("delete-account")
    suspend fun deleteAccount(
        @Header("Authorization") userAuthToken: String, // "Bearer <USER_SUPABASE_JWT>"
        @Header("apikey") anonKey: String,              // Supabase Anon Key
        @Body request: DeleteUserRequest? = null
    ): Response<DeleteAccountResponse>

    // Primary Delete Account Endpoint
    @POST("delete-user-account")
    suspend fun deleteUserAccount(
        @Header("Authorization") userAuthToken: String, // "Bearer <USER_SUPABASE_JWT>"
        @Header("apikey") anonKey: String,              // Supabase Anon Key
        @Body request: DeleteUserRequest? = null
    ): Response<DeleteAccountResponse>

    // Alternate Delete User Endpoint
    @POST("delete-user")
    suspend fun deleteUserAlt(
        @Header("Authorization") userAuthToken: String,
        @Header("apikey") anonKey: String,
        @Body request: DeleteUserRequest? = null
    ): Response<DeleteAccountResponse>

    // Dynamic Virtual Account Endpoint
    @POST("Create-Dynamic-Account")
    suspend fun createDynamicAccount(
        @Header("Authorization") authorization: String,
        @Header("apikey") anonKey: String,
        @Body request: CreateDynamicAccountRequest
    ): Response<DynamicAccountResponse>

    // Fallback lowercase Dynamic Virtual Account Endpoint
    @POST("create-dynamic-account")
    suspend fun createDynamicAccountAlt(
        @Header("Authorization") authorization: String,
        @Header("apikey") anonKey: String,
        @Body request: CreateDynamicAccountRequest
    ): Response<DynamicAccountResponse>

    // Dedicated Permanent Virtual Account (Create-Permanent-Account)
    @POST("Create-Permanent-Account")
    suspend fun createPermanentAccount(
        @Header("Authorization") userAuthToken: String,
        @Header("apikey") anonKey: String,
        @Body request: CreateVirtualAccountRequest
    ): Response<VirtualAccountResponse>

    // Dedicated Virtual Account (Fallback)
    @POST("Create-Virtual-Account")
    suspend fun generateVirtualAccount(
        @Header("Authorization") userAuthToken: String,
        @Header("apikey") anonKey: String,
        @Body request: CreateVirtualAccountRequest
    ): Response<VirtualAccountResponse>

    @POST("create-virtual-account")
    suspend fun generateVirtualAccountAlt(
        @Header("Authorization") userAuthToken: String,
        @Header("apikey") anonKey: String,
        @Body request: CreateVirtualAccountRequest
    ): Response<VirtualAccountResponse>
}

object NetworkModule {
    private val BASE_URL: String
        get() = "${com.example.util.SecurityVault.supabaseUrl()}/functions/v1/"

    val apiService: BackendApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BackendApiService::class.java)
    }
}

// ==========================================
// C. SETTINGS & REPOSITORIES
// ==========================================

class SettingsRepository(private val apiService: BackendApiService = NetworkModule.apiService) {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun deleteAccountPermanently(
        authToken: String,
        anonKey: String,
        userId: String? = null,
        email: String? = null
    ): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                // Call supabase.rpc('delete_user')
                val client = com.example.auth.SupabaseInstance.client
                if (client != null) {
                    client.postgrest.rpc("delete_user")
                    client.auth.signOut()
                    Result.success(true)
                } else {
                    val bearer = if (authToken.isNotBlank()) {
                        if (authToken.startsWith("Bearer ", ignoreCase = true)) authToken else "Bearer $authToken"
                    } else {
                        "Bearer $anonKey"
                    }
                    val req = Request.Builder()
                        .url("${com.example.util.SecurityVault.supabaseUrl()}/rest/v1/rpc/delete_user")
                        .addHeader("apikey", anonKey)
                        .addHeader("Authorization", bearer)
                        .addHeader("Content-Type", "application/json")
                        .post("{}".toRequestBody(jsonMediaType))
                        .build()

                    val res = okHttpClient.newCall(req).execute()
                    if (res.isSuccessful || res.code in 200..204) {
                        Result.success(true)
                    } else {
                        val errBody = res.body?.string() ?: ""
                        Log.e("DeleteAccount", "RPC delete_user error: $errBody")
                        Result.failure(Exception(if (errBody.isNotBlank()) errBody else "Failed to delete account (code ${res.code})"))
                    }
                }
            } catch (e: Exception) {
                Log.e("DeleteAccount", "RPC delete_user exception: ${e.message}", e)
                Result.failure(e)
            }
        }
    }
}

class WalletRepository(private val apiService: BackendApiService = NetworkModule.apiService) {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun createDynamicAccount(
        userId: String,
        email: String,
        anonKey: String,
        authToken: String? = null,
        amount: Double = 1000.0,
        firstName: String? = null,
        lastName: String? = null
    ): Result<DynamicAccountResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val cleanEmail = email.trim()
                val cleanUserId = userId.trim()
                val cleanFirst = firstName?.trim() ?: ""
                val cleanLast = lastName?.trim() ?: ""
                val txRef = "DYN-${System.currentTimeMillis()}-${java.util.UUID.randomUUID().toString().take(6)}"

                val jsonPayload = JSONObject().apply {
                    put("userId", cleanUserId)
                    put("user_id", cleanUserId)
                    put("email", cleanEmail)
                    put("amount", amount)
                    put("firstname", cleanFirst)
                    put("lastname", cleanLast)
                    put("currency", "NGN")
                    put("tx_ref", txRef)
                }

                val functionNames = listOf("Create-Dynamic-Account", "create-dynamic-account")
                var lastStatusCode: Int? = null
                var lastErrorMsg: String? = null

                for (fnName in functionNames) {
                    try {
                        val (statusCode, okBodyStr) = SupabaseProvider.postEdgeFunction(
                            functionName = fnName,
                            bodyJson = jsonPayload.toString(),
                            accessTokenOverride = authToken
                        )
                        lastStatusCode = statusCode

                        if (okBodyStr.isNotBlank()) {
                            val rootJson = JSONObject(okBodyStr)
                            val dataObj = rootJson.optJSONObject("data")
                            val errorObj = rootJson.optJSONObject("error")
                            val status = rootJson.optString("status")
                            val message = rootJson.optString("message")

                            val accNum = dataObj?.optString("account_number")?.takeIf { it.isNotBlank() }
                                ?: dataObj?.optString("accountNumber")?.takeIf { it.isNotBlank() }
                                ?: rootJson.optString("account_number").takeIf { it.isNotBlank() }
                                ?: rootJson.optString("accountNumber").takeIf { it.isNotBlank() }

                            if (!accNum.isNullOrBlank()) {
                                val bank = dataObj?.optString("account_bank_name")?.takeIf { it.isNotBlank() }
                                    ?: dataObj?.optString("bank_name")?.takeIf { it.isNotBlank() }
                                    ?: rootJson.optString("bank_name").takeIf { it.isNotBlank() }
                                    ?: ""

                                val accName = "Wallet Topup"

                                val parsedResponse = DynamicAccountResponse(
                                    status = "success",
                                    success = true,
                                    message = message.ifBlank { "Dynamic account created successfully" },
                                    data = DynamicAccountData(
                                        accountNumberSnake = accNum,
                                        accountBankName = bank,
                                        accountNameSnake = accName,
                                        amount = dataObj?.optDouble("amount") ?: amount,
                                        accountExpirationDatetime = dataObj?.optString("account_expiration_datetime"),
                                        note = dataObj?.optString("note")
                                    )
                                )
                                return@withContext Result.success(parsedResponse)
                            }

                            val errDetail = errorObj?.optString("message")?.takeIf { it.isNotBlank() }
                                ?: message.takeIf { it.isNotBlank() }
                                ?: (if (status == "failed") "Dynamic account generation failed" else null)

                            if (!errDetail.isNullOrBlank()) {
                                lastErrorMsg = errDetail
                            }
                        }
                    } catch (e: Exception) {
                        lastErrorMsg = e.localizedMessage
                    }
                }

                val failMsg = lastErrorMsg
                    ?: (if (lastStatusCode != null) "Dynamic account service returned code $lastStatusCode" else "Failed to connect to dynamic account service")
                Result.failure(Exception(failMsg))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun requestDedicatedAccount(
        authToken: String,
        anonKey: String,
        email: String,
        firstName: String,
        lastName: String,
        phone: String,
        nin: String? = null
    ): Result<VirtualAccountResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val cleanNin = nin?.filter { it.isDigit() }?.take(11)
                val payload = JSONObject().apply {
                    put("email", email)
                    put("firstName", firstName)
                    put("lastName", lastName)
                    put("phone", phone)
                    if (!cleanNin.isNullOrBlank() && cleanNin.length == 11) {
                        put("nin", cleanNin)
                    }
                }

                val functionNames = listOf(
                    "Create-Permanent-Account",
                    "Create-Virtual-Account",
                    "create-virtual-account"
                )

                var lastMessage: String? = null
                var lastKycReq: Boolean? = null

                for (fnName in functionNames) {
                    try {
                        val (statusCode, bodyStr) = SupabaseProvider.postEdgeFunction(
                            functionName = fnName,
                            bodyJson = payload.toString(),
                            accessTokenOverride = authToken
                        )
                        if (bodyStr.isNotBlank()) {
                            val json = JSONObject(bodyStr)
                            val dataObj = json.optJSONObject("data")
                            val accNum = json.optString("accountNumber").takeIf { it.isNotBlank() }
                                ?: json.optString("account_number").takeIf { it.isNotBlank() }
                                ?: dataObj?.optString("account_number")?.takeIf { it.isNotBlank() }
                                ?: dataObj?.optString("accountNumber")?.takeIf { it.isNotBlank() }
                            val bank = json.optString("bankName").takeIf { it.isNotBlank() }
                                ?: json.optString("bank_name").takeIf { it.isNotBlank() }
                                ?: dataObj?.optString("bank_name")?.takeIf { it.isNotBlank() }
                                ?: dataObj?.optString("bankName")?.takeIf { it.isNotBlank() }

                            if (statusCode in 200..299 && !accNum.isNullOrBlank()) {
                                return@withContext Result.success(
                                    VirtualAccountResponse(
                                        success = true,
                                        accountNumber = accNum,
                                        bankName = bank ?: "",
                                        message = json.optString("message").ifBlank { "Account created" },
                                        isKycVerified = true,
                                        isKycRequired = false
                                    )
                                )
                            }
                            lastMessage = json.optString("message").takeIf { it.isNotBlank() }
                                ?: json.optString("error").takeIf { it.isNotBlank() }
                            if (json.has("isKycRequired")) {
                                lastKycReq = json.optBoolean("isKycRequired")
                            }
                        }
                    } catch (_: Exception) {}
                }

                val isKycReq = lastKycReq == true || cleanNin.isNullOrBlank() || cleanNin.length != 11
                val returnedMsg = lastMessage ?: "No permanent account yet. Verify your NIN to create one"
                Result.success(
                    VirtualAccountResponse(
                        success = false,
                        accountNumber = null,
                        bankName = null,
                        message = returnedMsg,
                        isKycRequired = isKycReq,
                        isKycVerified = false
                    )
                )
            } catch (e: Exception) {
                Result.success(
                    VirtualAccountResponse(
                        success = false,
                        accountNumber = null,
                        bankName = null,
                        message = "No permanent account yet. Verify your NIN to create one",
                        isKycRequired = true,
                        isKycVerified = false
                    )
                )
            }
        }
    }
}

// ==========================================
// D. VIEWMODELS
// ==========================================

class DeleteAccountViewModel(
    private val repository: SettingsRepository = SettingsRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow<DeleteAccountUiState>(DeleteAccountUiState.Idle)
    val uiState: StateFlow<DeleteAccountUiState> = _uiState.asStateFlow()

    fun deleteAccount(
        authToken: String,
        anonKey: String,
        userId: String? = null,
        email: String? = null,
        onAccountDeleted: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = DeleteAccountUiState.Loading
            val result = repository.deleteAccountPermanently(
                authToken = authToken,
                anonKey = anonKey,
                userId = userId,
                email = email
            )

            result.onSuccess {
                _uiState.value = DeleteAccountUiState.Success
                onAccountDeleted() // Callback to log out user and navigate to Login / Register Screen
            }.onFailure { error ->
                _uiState.value = DeleteAccountUiState.Error(error.localizedMessage ?: "Deletion failed")
                onAccountDeleted()
            }
        }
    }
}

class DynamicAccountViewModel(
    private val repository: WalletRepository = WalletRepository(NetworkModule.apiService)
) : ViewModel() {
    private val _uiState = MutableStateFlow<DynamicAccountUiState>(DynamicAccountUiState.Idle)
    val uiState: StateFlow<DynamicAccountUiState> = _uiState.asStateFlow()
    private var activeUserId: String? = null

    fun resetState() {
        activeUserId = null
        _uiState.value = DynamicAccountUiState.Idle
    }

    fun onUserChanged(userId: String?) {
        if (userId.isNullOrBlank() || (activeUserId != null && activeUserId != userId)) {
            resetState()
        }
        activeUserId = userId
    }

    fun createVirtualAccount(
        userId: String,
        email: String,
        anonKey: String,
        authToken: String? = null,
        amount: Double = 1000.0,
        firstName: String? = null,
        lastName: String? = null,
        onAccountCreated: (accountNumber: String, bankName: String) -> Unit = { _, _ -> }
    ) {
        if (activeUserId != null && activeUserId != userId) {
            resetState()
        }
        activeUserId = userId
        viewModelScope.launch {
            _uiState.value = DynamicAccountUiState.Loading
            val result = repository.createDynamicAccount(
                userId = userId,
                email = email,
                anonKey = anonKey,
                authToken = authToken,
                amount = amount,
                firstName = firstName,
                lastName = lastName
            )

                result.onSuccess { res ->
                val acc = res.accountNumber
                val bank = res.bankName
                if (!acc.isNullOrBlank()) {
                    _uiState.value = DynamicAccountUiState.Success(
                        accountNumber = acc,
                        bankName = bank,
                        accountName = "Wallet Topup",
                        amount = res.transferAmount ?: amount,
                        expiration = res.expirationDatetime,
                        note = res.transferNote
                    )
                    onAccountCreated(acc, bank)
                } else {
                    _uiState.value = DynamicAccountUiState.Error(res.errorMessage ?: "Account number not returned")
                }
            }.onFailure { error ->
                _uiState.value = DynamicAccountUiState.Error(error.localizedMessage ?: "Failed to create dynamic account")
            }
        }
    }
}

class VirtualAccountViewModel(
    private val repository: WalletRepository = WalletRepository(NetworkModule.apiService)
) : ViewModel() {

    private val _uiState = MutableStateFlow<VirtualAccountUiState>(VirtualAccountUiState.Idle)
    val uiState: StateFlow<VirtualAccountUiState> = _uiState.asStateFlow()

    fun generateAccount(
        authToken: String,
        anonKey: String,
        email: String,
        firstName: String,
        lastName: String,
        phone: String
    ) {
        viewModelScope.launch {
            _uiState.value = VirtualAccountUiState.Loading

            val result = repository.requestDedicatedAccount(
                authToken = authToken,
                anonKey = anonKey,
                email = email,
                firstName = firstName,
                lastName = lastName,
                phone = phone
            )

            result.onSuccess { response ->
                if (response.accountNumber != null && response.bankName != null) {
                    _uiState.value = VirtualAccountUiState.Success(
                        accountNumber = response.accountNumber,
                        bankName = response.bankName
                    )
                } else {
                    _uiState.value = VirtualAccountUiState.Error("Invalid account data returned.")
                }
            }.onFailure { error ->
                _uiState.value = VirtualAccountUiState.Error(error.localizedMessage ?: "Network request failed.")
            }
        }
    }
}

// ==========================================
// E. UI BUTTON COMPONENT WITH CONFIRMATION
// ==========================================

@Composable
fun DeleteAccountButton(
    userAuthToken: String,
    supabaseAnonKey: String,
    userId: String? = null,
    email: String? = null,
    onAccountDeleted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DeleteAccountViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    var showDialog by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = modifier) {
        Button(
            onClick = { showDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("profile_delete_account_button")
        ) {
            if (uiState is DeleteAccountUiState.Loading) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text("Delete Account Permanently", fontSize = 16.sp)
            }
        }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("Permanently Delete Account?") },
                text = { Text("Are you sure you want to delete your account permanently? This action cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDialog = false
                            onAccountDeleted()
                            viewModel.deleteAccount(
                                authToken = userAuthToken,
                                anonKey = supabaseAnonKey,
                                userId = userId,
                                email = email,
                                onAccountDeleted = {}
                            )
                        },
                        modifier = Modifier.testTag("confirm_delete_account_button")
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (uiState is DeleteAccountUiState.Error) {
            Text(
                text = (uiState as DeleteAccountUiState.Error).message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

// ==========================================
// F. GENERATE VIRTUAL ACCOUNT SCREEN (LEGACY)
// ==========================================

@Composable
fun GenerateVirtualAccountScreen(
    userAuthToken: String,
    supabaseAnonKey: String,
    viewModel: VirtualAccountViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    var email by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Daniel's VTU - Funding Account",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        when (val state = uiState) {
            is VirtualAccountUiState.Success -> {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Your Dedicated Bank Account",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = state.accountNumber,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 2.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = state.bankName,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            else -> {
                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    label = { Text("First Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    label = { Text("Last Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (state is VirtualAccountUiState.Error) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Button(
                    onClick = {
                        viewModel.generateAccount(
                            authToken = userAuthToken,
                            anonKey = supabaseAnonKey,
                            email = email,
                            firstName = firstName,
                            lastName = lastName,
                            phone = phone
                        )
                    },
                    enabled = state !is VirtualAccountUiState.Loading,
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    if (state is VirtualAccountUiState.Loading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text("Generate Virtual Account")
                    }
                }
            }
        }
    }
}

