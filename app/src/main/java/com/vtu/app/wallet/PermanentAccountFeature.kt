package com.vtu.app.wallet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.auth.SupabaseInstance
import com.example.auth.SupabaseProvider
import com.example.data.model.SupabaseUser
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

fun String?.isValidAccountNumber(): Boolean {
    if (this == null) return false
    val clean = this.trim()
    if (clean.equals("null", ignoreCase = true) || clean.equals("nil", ignoreCase = true) || clean.isBlank()) {
        return false
    }
    val digits = clean.filter { it.isDigit() }
    return digits.length in 10..12
}

fun String?.cleanAccountNumber(): String? {
    if (this == null) return null
    val clean = this.trim()
    if (clean.equals("null", ignoreCase = true) || clean.equals("nil", ignoreCase = true) || clean.isBlank()) {
        return null
    }
    val digits = clean.filter { it.isDigit() }
    return if (digits.length in 10..12) digits else null
}

fun JSONObject.optCleanString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val v = optString(key).trim()
    if (v.isEmpty() || v.equals("null", ignoreCase = true) || v.equals("nil", ignoreCase = true)) return null
    return v
}

@Serializable
data class PermanentAccountResponse(
    val account_number: String? = null,
    val bank_name: String? = null,
    val account_name: String? = null,
    val error: String? = null
)

class PermanentAccountViewModel : ViewModel() {
    private val _accountDetails = MutableStateFlow<PermanentAccountResponse?>(null)
    val accountDetails: StateFlow<PermanentAccountResponse?> = _accountDetails.asStateFlow()
    private val _ninVerified = MutableStateFlow(false)
    val ninVerified: StateFlow<Boolean> = _ninVerified.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val anonKey: String
        get() = SupabaseProvider.rawKey

    private val supabase get() = SupabaseProvider.client

    private var explicitlyResetInSession = false
    private var lastCheckedUserId: String? = null

    private fun resolveAuthToken(): String {
        return SupabaseProvider.resolveSessionAccessToken()
    }

    fun resetState() {
        explicitlyResetInSession = false
        lastCheckedUserId = null
        _accountDetails.value = null
        _ninVerified.value = false
        _isLoading.value = false
    }

    fun checkExisting(
        userId: String,
        email: String? = null,
        currentVa: String? = null,
        onAccountRestored: ((String, String) -> Unit)? = null
    ) {
        val cleanUid = userId.trim()
        if (cleanUid.isBlank() || cleanUid == "usr_guest" || cleanUid == "usr_default") {
            resetState()
            return
        }
        if (lastCheckedUserId != cleanUid) {
            explicitlyResetInSession = false
            _accountDetails.value = null
            _ninVerified.value = false
            lastCheckedUserId = cleanUid
        }
        val cleanVa = currentVa.cleanAccountNumber()
        if (cleanVa != null && !explicitlyResetInSession) {
            _accountDetails.value = PermanentAccountResponse(
                account_number = cleanVa,
                bank_name = _accountDetails.value?.bank_name,
                account_name = _accountDetails.value?.account_name
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            val baseUrl = com.example.util.SecurityVault.supabaseUrl()
            val token = resolveAuthToken()
            val bearer = if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"
            val okClient = OkHttpClient.Builder()
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(6, TimeUnit.SECONDS)
                .build()

            try {
                val userSelectCandidates = listOf(
                    com.example.data.repository.VtuRepository.USERS_SAFE_COLUMNS_FULL.joinToString(","),
                    com.example.data.repository.VtuRepository.USERS_SAFE_COLUMNS_STANDARD.joinToString(","),
                    "id,permanent_account_number,permanent_account_bank"
                )
                var body = ""
                for (cols in userSelectCandidates) {
                    val req = Request.Builder()
                        .url("$baseUrl/rest/v1/users?select=$cols&id=eq.$cleanUid&limit=1")
                        .addHeader("apikey", anonKey)
                        .addHeader("Authorization", bearer)
                        .get()
                        .build()
                    val ok = okClient.newCall(req).execute().use { res ->
                        if (res.isSuccessful) {
                            body = res.body?.string().orEmpty()
                            true
                        } else {
                            false
                        }
                    }
                    if (ok) break
                }
                var foundInUsers = false
                if (body.isNotBlank()) {
                    val arr = JSONArray(body)
                    if (arr.length() > 0) {
                        val obj = arr.getJSONObject(0)
                        val ninHash = obj.optCleanString("nin_hash")
                        _ninVerified.value = !ninHash.isNullOrBlank()

                        val num = (obj.optCleanString("permanent_account_number")
                            ?: obj.optCleanString("virtual_account_number"))?.cleanAccountNumber()
                        if (num != null && !explicitlyResetInSession) {
                            foundInUsers = true
                            val bank = obj.optCleanString("bank_name")
                                ?: obj.optCleanString("permanent_account_bank")
                                ?: obj.optCleanString("virtual_bank_name")
                                ?: obj.optCleanString("virtual_bank")
                            val rawName = obj.optCleanString("account_name")
                                ?: obj.optCleanString("permanent_account_name")
                                ?: obj.optCleanString("virtual_account_name")
                            val rawFullName = obj.optCleanString("full_name") ?: obj.optCleanString("name")
                            val rowEmail = obj.optCleanString("email") ?: email
                            val name = com.example.data.repository.VtuRepository.resolveAccountHolderName(
                                rawAccountName = rawName,
                                fullName = rawFullName,
                                email = rowEmail
                            )

                            _accountDetails.value = PermanentAccountResponse(
                                account_number = num,
                                bank_name = bank,
                                account_name = name
                            )
                            if (name.isNotBlank() && rawName != name) {
                                persistToSupabase(
                                    userId = cleanUid,
                                    email = rowEmail.orEmpty(),
                                    accNumber = num,
                                    bank = bank.orEmpty(),
                                    accName = name,
                                    nin = ""
                                )
                            }
                            withContext(Dispatchers.Main) {
                                onAccountRestored?.invoke(num, bank.orEmpty())
                            }
                        }
                    }
                }
                if (!foundInUsers && !explicitlyResetInSession) {
                    val vaReq = Request.Builder()
                        .url("$baseUrl/rest/v1/virtual_accounts?select=user_id,email,account_number,bank_name,account_name&user_id=eq.$cleanUid&limit=1")
                        .addHeader("apikey", anonKey)
                        .addHeader("Authorization", bearer)
                        .get()
                        .build()
                    okClient.newCall(vaReq).execute().use { vaRes ->
                        if (vaRes.isSuccessful) {
                            val vaBody = vaRes.body?.string().orEmpty()
                            val vaArr = JSONArray(vaBody)
                            if (vaArr.length() > 0) {
                                val vaObj = vaArr.getJSONObject(0)
                                val num = vaObj.optCleanString("account_number")?.cleanAccountNumber()
                                if (num != null) {
                                    val bank = vaObj.optCleanString("bank_name")
                                    val rawName = vaObj.optCleanString("account_name")
                                    val rowEmail = vaObj.optCleanString("email") ?: email
                                    val name = com.example.data.repository.VtuRepository.resolveAccountHolderName(
                                        rawAccountName = rawName,
                                        fullName = null,
                                        email = rowEmail
                                    )
                                    _accountDetails.value = PermanentAccountResponse(
                                        account_number = num,
                                        bank_name = bank,
                                        account_name = name
                                    )
                                    if (name.isNotBlank() && rawName != name) {
                                        persistToSupabase(
                                            userId = cleanUid,
                                            email = rowEmail.orEmpty(),
                                            accNumber = num,
                                            bank = bank.orEmpty(),
                                            accName = name,
                                            nin = ""
                                        )
                                    }
                                    withContext(Dispatchers.Main) {
                                        onAccountRestored?.invoke(num, bank.orEmpty())
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    fun clearAccount(userId: String) {
        explicitlyResetInSession = true
        _accountDetails.value = null
        val cleanUid = userId.trim()
        if (cleanUid.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val client = supabase
            if (client != null) {
                try {
                    client.from("users").update(mapOf("virtual_account_number" to null)) {
                        filter { eq("id", cleanUid) }
                    }
                } catch (_: Throwable) {}
                try {
                    client.from("users").update(mapOf("permanent_account_number" to null)) {
                        filter { eq("id", cleanUid) }
                    }
                } catch (_: Throwable) {}
            }
        }
    }

    companion object {
        val CREATE_PERMANENT_ACCOUNT_URL: String
            get() = "${com.example.util.SecurityVault.supabaseUrl()}/functions/v1/Create-Permanent-Account"
    }

    fun createPermanentAccount(
        userId: String,
        email: String,
        firstname: String,
        lastname: String,
        fullName: String = "",
        idNumber: String,
        idType: String = "NIN",
        phone: String? = null,
        onSuccess: ((String, String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _accountDetails.value = null

            withContext(Dispatchers.IO) {
                try {
                    val cleanId = idNumber.filter { it.isDigit() }.take(11)
                    val cleanPhone = com.example.data.repository.VtuRepository.sanitizeRealPhone(phone)?.filter { it.isDigit() } ?: ""

                    val resolvedFullName = com.example.data.repository.VtuRepository.resolveAccountHolderName(
                        rawAccountName = fullName,
                        fullName = "${firstname.trim()} ${lastname.trim()}".trim(),
                        email = email
                    )
                    val nameParts = resolvedFullName.split("\\s+".toRegex()).filter { it.isNotBlank() }
                    val firstWithMiddle = if (nameParts.size > 2) nameParts.dropLast(1).joinToString(" ") else nameParts.firstOrNull() ?: firstname.trim()
                    val lastOnly = if (nameParts.size > 1) nameParts.last() else lastname.trim()
                    val middleOnly = if (nameParts.size > 2) nameParts.drop(1).dropLast(1).joinToString(" ") else ""

                    // Payload matching user's Create-Permanent-Account edge function
                    val payload = JSONObject().apply {
                        put("userId", userId)
                        put("user_id", userId)
                        put("email", email.trim().lowercase())
                        put("firstname", firstWithMiddle)
                        put("firstName", firstWithMiddle)
                        put("middlename", middleOnly)
                        put("middleName", middleOnly)
                        put("lastname", lastOnly)
                        put("lastName", lastOnly)
                        put("full_name", resolvedFullName)
                        put("fullName", resolvedFullName)
                        put("name", resolvedFullName)
                        put("account_name", resolvedFullName)
                        put("accountName", resolvedFullName)
                        put("narration", resolvedFullName)
                        put("phone", cleanPhone)
                        put("phonenumber", cleanPhone)
                        put("nin", cleanId)
                        put("id_type", "NIN")
                    }

                    val token = resolveAuthToken()
                    val (statusCode, responseText) = SupabaseProvider.postEdgeFunction(
                        functionName = "Create-Permanent-Account",
                        bodyJson = payload.toString(),
                        accessTokenOverride = token
                    )
                    Log.d("PermanentAccount", "Response from Create-Permanent-Account ($userId): $responseText")

                    val json = try { JSONObject(responseText) } catch (_: Throwable) { null }
                    val dataObj = json?.optJSONObject("data")

                    // Extract account number from top-level or data object
                    val rawAccNumber = json?.optCleanString("account_number")
                        ?: json?.optCleanString("accountNumber")
                        ?: dataObj?.optCleanString("account_number")
                        ?: dataObj?.optCleanString("accountNumber")
                        ?: dataObj?.optCleanString("order_ref")

                    val accNumber = rawAccNumber?.cleanAccountNumber()

                    if (accNumber != null) {
                        val bank = json?.optCleanString("bank_name")
                            ?: json?.optCleanString("bankName")
                            ?: dataObj?.optCleanString("bank_name")
                            ?: dataObj?.optCleanString("bankName")
                            ?: ""

                        val rawAcctName = json?.optCleanString("account_name")
                            ?: json?.optCleanString("accountName")
                            ?: dataObj?.optCleanString("account_name")
                            ?: dataObj?.optCleanString("accountName")
                        val acctName = com.example.data.repository.VtuRepository.resolveAccountHolderName(
                            rawAccountName = rawAcctName,
                            fullName = resolvedFullName,
                            email = email
                        ).ifBlank { resolvedFullName }

                        val successResp = PermanentAccountResponse(
                            account_number = accNumber,
                            bank_name = bank,
                            account_name = acctName
                        )

                        explicitlyResetInSession = false
                        _ninVerified.value = true
                        _accountDetails.value = successResp

                        // Persist to Supabase database where id = current user id
                        persistToSupabase(
                            userId = userId,
                            email = email,
                            accNumber = accNumber,
                            bank = bank,
                            accName = acctName,
                            nin = cleanId
                        )

                        withContext(Dispatchers.Main) {
                            onSuccess?.invoke(accNumber, bank)
                        }
                    } else {
                        // Extract error message from edge function response
                        val errorTitle = json?.optCleanString("error")
                            ?: json?.optCleanString("message")
                            ?: ""
                        val detailsObj = json?.optJSONObject("details")
                        val detailsErrorObj = detailsObj?.optJSONObject("error")
                        val detailMessage = detailsErrorObj?.optCleanString("message")
                            ?: detailsObj?.optCleanString("message")
                            ?: ""

                        val rawCombinedError = when {
                            detailMessage.isNotBlank() && errorTitle.isNotBlank() -> "$errorTitle: $detailMessage"
                            detailMessage.isNotBlank() -> detailMessage
                            errorTitle.isNotBlank() -> errorTitle
                            else -> "Network connection bad. Please check your internet connection and try again."
                        }

                        val lowerErr = rawCombinedError.lowercase()
                        // Provide clear and actionable KYC / NIN messaging without exposing backend details
                        val userFriendlyError = when {
                            rawCombinedError.contains("Invalid nin", ignoreCase = true) ||
                            rawCombinedError.contains("Invalid identity", ignoreCase = true) ->
                                "Identity Verification Failed: The 11-digit NIN could not be verified by NIMC. Please double-check your 11-digit NIN and try again."
                            rawCombinedError.contains("nin is required", ignoreCase = true) ||
                            rawCombinedError.contains("identity is required", ignoreCase = true) ->
                                "A valid 11-digit NIN is required to create a permanent account."
                            rawCombinedError.contains("KYC", ignoreCase = true) || rawCombinedError.contains("verification", ignoreCase = true) ->
                                "KYC Verification Required: Please verify your 11-digit NIN to activate your dedicated virtual account."
                            statusCode >= 500 ||
                            statusCode == 401 ||
                            statusCode == 403 ||
                            statusCode == 404 ||
                            lowerErr.contains("supabase") ||
                            lowerErr.contains("gsubz") ||
                            lowerErr.contains("edge") ||
                            lowerErr.contains("function") ||
                            lowerErr.contains("credential") ||
                            lowerErr.contains("configured") ||
                            lowerErr.contains("network") ||
                            lowerErr.contains("timeout") ||
                            lowerErr.contains("connect") ||
                            lowerErr.contains("http ") ->
                                "Network connection bad. Please check your internet connection and try again."
                            else -> rawCombinedError.replace(Regex("Supabase|Gsubz|Edge\\s*Function", RegexOption.IGNORE_CASE), "").trim()
                                .ifBlank { "Network connection bad. Please check your internet connection and try again." }
                        }

                        _accountDetails.value = PermanentAccountResponse(error = userFriendlyError)
                    }
                } catch (e: Exception) {
                    Log.e("PermanentAccount", "Error calling Create-Permanent-Account", e)
                    _accountDetails.value = PermanentAccountResponse(
                        error = "Network connection bad. Please check your internet connection and try again."
                    )
                } finally {
                    _isLoading.value = false
                }
            }
        }
    }

    private suspend fun persistToSupabase(
        userId: String,
        email: String,
        accNumber: String,
        bank: String,
        accName: String,
        nin: String
    ) {
        val cleanUid = userId.trim()
        if (cleanUid.isBlank()) return
        val baseUrl = com.example.util.SecurityVault.supabaseUrl()
        val token = resolveAuthToken()
        val bearer = if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"
        val client = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()

        val userProfileFullName = if (email.trim().equals("danielkaladathompson@gmail.com", ignoreCase = true)) {
            "Daniel Thompson"
        } else null

        // Persist into public.users table where id = userId (never overwrite full_name with NIN account_name)
        val candidatePayloads = listOf(
            JSONObject().apply {
                put("virtual_account_number", accNumber)
                if (bank.isNotBlank()) put("virtual_bank", bank)
                if (!userProfileFullName.isNullOrBlank()) put("full_name", userProfileFullName)
            },
            JSONObject().apply {
                put("virtual_account_number", accNumber)
                if (bank.isNotBlank()) put("virtual_bank_name", bank)
                if (accName.isNotBlank()) put("virtual_account_name", accName)
                if (!userProfileFullName.isNullOrBlank()) put("full_name", userProfileFullName)
            },
            JSONObject().apply {
                put("permanent_account_number", accNumber)
                if (bank.isNotBlank()) put("permanent_account_bank", bank)
                if (accName.isNotBlank()) {
                    put("permanent_account_name", accName)
                    put("account_name", accName)
                }
                if (!userProfileFullName.isNullOrBlank()) put("full_name", userProfileFullName)
            }
        )

        val filterParam = "id=eq.$cleanUid"

        for (payloadObj in candidatePayloads) {
            if (payloadObj.length() == 0) continue
            try {
                val reqBody = payloadObj.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val req = Request.Builder()
                    .url("$baseUrl/rest/v1/users?$filterParam")
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", bearer)
                    .addHeader("Prefer", "return=minimal")
                    .patch(reqBody)
                    .build()
                client.newCall(req).execute().close()
            } catch (_: Throwable) {}
        }

        // Also upsert into public.virtual_accounts keyed by user_id
        try {
            val vaJson = JSONObject().apply {
                put("user_id", cleanUid)
                put("email", email.trim().lowercase())
                put("account_number", accNumber)
                if (bank.isNotBlank()) put("bank_name", bank)
                if (accName.isNotBlank()) put("account_name", accName)
            }
            val vaReq = Request.Builder()
                .url("$baseUrl/rest/v1/virtual_accounts?on_conflict=user_id")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", bearer)
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(vaJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            client.newCall(vaReq).execute().close()
        } catch (_: Throwable) {}
    }
}

@Composable
fun PermanentAccountSection(
    currentUser: SupabaseUser? = null,
    onAccountCreated: ((accountNumber: String, bankName: String) -> Unit)? = null,
    onAccountReset: (() -> Unit)? = null,
    viewModel: PermanentAccountViewModel = viewModel()
) {
    val idType = "NIN"
    var idNumber by remember { mutableStateOf("") }
    var showKycHelp by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    val details by viewModel.accountDetails.collectAsState()
    val ninVerifiedFromDb by viewModel.ninVerified.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current

    val supabaseClient = SupabaseInstance.client
    val userId = currentUser?.id
        ?: supabaseClient?.auth?.currentUserOrNull()?.id
        ?: ""

    LaunchedEffect(userId, currentUser?.virtualAccountNumber) {
        if (userId.isBlank()) {
            idNumber = ""
            viewModel.resetState()
        } else {
            val email = currentUser?.email
                ?: supabaseClient?.auth?.currentUserOrNull()?.email
                ?: ""
            viewModel.checkExisting(
                userId = userId,
                email = email,
                currentVa = currentUser?.virtualAccountNumber,
                onAccountRestored = { acc, bankName ->
                    if (currentUser?.virtualAccountNumber != acc) {
                        onAccountCreated?.invoke(acc, bankName)
                    }
                }
            )
        }
    }

    if (userId.isBlank()) return
    val email = currentUser?.email
        ?: supabaseClient?.auth?.currentUserOrNull()?.email
        ?: ""

    val fullName = com.example.data.repository.VtuRepository.sanitizeFullName(
        currentUser?.fullName,
        email
    )
    val parts = fullName.split("\\s+".toRegex()).filter { it.isNotBlank() }
    val firstname = parts.firstOrNull().orEmpty()
    val lastname = if (parts.size > 1) parts.drop(1).joinToString(" ") else ""
    val isNinVerified = currentUser?.ninVerified == true || !currentUser?.ninHash.isNullOrBlank() || ninVerifiedFromDb

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            val validAccountNumber = details?.account_number.cleanAccountNumber()
                ?: currentUser?.virtualAccountNumber.cleanAccountNumber()
            val hasActiveAccount = validAccountNumber != null

            if (hasActiveAccount) {
                val accNumber = validAccountNumber!!
                val bank = details?.bank_name?.trim()?.takeIf {
                    !it.equals("null", ignoreCase = true) && !it.equals("nil", ignoreCase = true) && it.isNotBlank()
                } ?: currentUser?.virtualBankName?.trim()?.takeIf {
                    !it.equals("null", ignoreCase = true) && !it.equals("nil", ignoreCase = true) && it.isNotBlank()
                } ?: ""
                val acctName = com.example.data.repository.VtuRepository.resolveAccountHolderName(
                    rawAccountName = details?.account_name ?: currentUser?.virtualAccountName,
                    fullName = fullName,
                    email = email
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2E7D32).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Active",
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Permanent Dedicated Account",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isNinVerified) "NIN Verified • Dedicated Virtual Account Active" else "Dedicated Virtual Account • Active",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "BANK NAME",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = bank.ifBlank { "—" },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "ACCOUNT NUMBER",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = accNumber,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Account Number", accNumber))
                                    Toast.makeText(context, "Account number copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy account number",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "ACCOUNT NAME",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = acctName.ifBlank { "—" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Any transfer sent to this dedicated account credits your wallet balance automatically in real-time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // KYC & Transfer Troubleshooter Card
                Spacer(modifier = Modifier.height(14.dp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Bank shows 'Recipient KYC incomplete'?",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "If banks say KYC verification is incomplete, your Flutterwave merchant compliance needs approval or this account was created before key rotation.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(
                            onClick = { showKycHelp = !showKycHelp },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = if (showKycHelp) "Hide KYC Checklist ▲" else "View Flutterwave KYC Checklist ▼",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (showKycHelp) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "1. Valid 11-Digit NIN: Ensure your 11-digit National Identification Number matches your registered name.\n" +
                                       "2. Network Connection: Ensure you have a stable internet connection.\n" +
                                       "3. Reset & Re-link: Tap below to unlink this account and generate a new one with your verified NIN.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { showResetConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset / Re-link with Verified NIN")
                }

                if (showResetConfirm) {
                    AlertDialog(
                        onDismissRequest = { showResetConfirm = false },
                        title = { Text("Reset Permanent Account?") },
                        text = {
                            Text("This will unlink account $accNumber and allow you to re-enter your verified 11-digit NIN to generate a fresh permanent account.")
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showResetConfirm = false
                                    viewModel.clearAccount(userId)
                                    onAccountReset?.invoke()
                                    Toast.makeText(context, "Account unlinked. You can now re-link.", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Reset Account", color = Color.White)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showResetConfirm = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            } else {
                // Account creation form (Empty state)
                Text(
                    text = "No permanent account yet. Verify your NIN to create one",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "National Identity Number (NIN) Verification",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = idNumber,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }
                        if (digits.length <= 11) idNumber = digits
                    },
                    label = { Text("NIN Number") },
                    placeholder = { Text("Enter 11-digit NIN") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    trailingIcon = {
                        Text(
                            text = "${idNumber.length}/11",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (idNumber.length == 11) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    },
                    isError = details?.error != null
                )

                details?.error?.let { errText ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Verification Note",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = errText,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = {
                        viewModel.createPermanentAccount(
                            userId = userId,
                            email = email,
                            firstname = firstname,
                            lastname = lastname,
                            fullName = fullName,
                            idNumber = idNumber,
                            idType = idType,
                            phone = currentUser?.phone,
                            onSuccess = { acc, bankName ->
                                onAccountCreated?.invoke(acc, bankName)
                                Toast.makeText(context, "Permanent Account Created: $acc", Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    enabled = !isLoading && idNumber.length == 11,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Verifying & Creating Account...")
                    } else {
                        Text("Verify NIN & Get Account", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
