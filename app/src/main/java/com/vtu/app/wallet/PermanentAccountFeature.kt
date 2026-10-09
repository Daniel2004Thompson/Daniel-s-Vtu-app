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
        fullName: String? = null,
        phone: String? = null,
        nin: String? = null,
        currentVa: String? = null,
        onAccountRestored: ((String, String, String) -> Unit)? = null
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
        val initialResolvedName = com.example.data.repository.VtuRepository.resolveAccountHolderName(
            rawAccountName = _accountDetails.value?.account_name,
            fullName = fullName,
            email = email
        )
        if (cleanVa != null && !explicitlyResetInSession) {
            _accountDetails.value = PermanentAccountResponse(
                account_number = cleanVa,
                bank_name = _accountDetails.value?.bank_name,
                account_name = initialResolvedName.ifBlank { null }
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            val baseUrl = com.example.util.SecurityVault.supabaseUrl()
            val token = resolveAuthToken()
            val bearer = if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"
            val okClient = OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .build()

            var dbAccNum: String? = cleanVa
            var dbBank: String? = _accountDetails.value?.bank_name
            var dbNin: String? = nin?.filter { it.isDigit() }?.takeIf { it.length == 11 }
            var dbPhone: String? = com.example.data.repository.VtuRepository.sanitizeRealPhone(phone)
            var dbEmail: String = email?.trim().orEmpty()

            try {
                val userSelectCandidates = listOf(
                    "id,email,phone,permanent_account_number,permanent_account_bank",
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
                if (body.isNotBlank()) {
                    val arr = JSONArray(body)
                    if (arr.length() > 0) {
                        val obj = arr.getJSONObject(0)
                        val ninVal = obj.optCleanString("nin") ?: obj.optCleanString("nin_hash")
                        if (!ninVal.isNullOrBlank()) {
                            _ninVerified.value = true
                            if (dbNin.isNullOrBlank()) {
                                dbNin = ninVal.filter { it.isDigit() }.takeIf { it.length == 11 }
                            }
                        }
                        if (dbPhone.isNullOrBlank()) {
                            dbPhone = com.example.data.repository.VtuRepository.sanitizeRealPhone(obj.optCleanString("phone"))
                        }
                        if (dbEmail.isBlank()) {
                            dbEmail = obj.optCleanString("email").orEmpty()
                        }

                        val num = (obj.optCleanString("permanent_account_number")
                            ?: obj.optCleanString("virtual_account_number"))?.cleanAccountNumber()
                        if (num != null && !explicitlyResetInSession) {
                            dbAccNum = num
                            val bank = obj.optCleanString("bank_name")
                                ?: obj.optCleanString("permanent_account_bank")
                                ?: obj.optCleanString("virtual_bank_name")
                                ?: obj.optCleanString("virtual_bank")
                            if (!bank.isNullOrBlank()) {
                                dbBank = bank
                            }
                            val resolvedDbName = com.example.data.repository.VtuRepository.resolveAccountHolderName(
                                rawAccountName = _accountDetails.value?.account_name,
                                fullName = fullName,
                                email = dbEmail
                            )
                            _accountDetails.value = PermanentAccountResponse(
                                account_number = num,
                                bank_name = dbBank,
                                account_name = resolvedDbName.ifBlank { null }
                            )
                        }
                    }
                }

                // Query Create-Permanent-Account Supabase Edge Function URL and format as "Thompson Daniel/ <Full Name>"
                if (!explicitlyResetInSession) {
                    val edgeResult = fetchFromCreatePermanentAccountEdgeFunction(
                        userId = cleanUid,
                        email = dbEmail,
                        fullName = fullName.orEmpty(),
                        phone = dbPhone,
                        nin = dbNin,
                        accessToken = token
                    )
                    if (edgeResult != null) {
                        val finalNum = edgeResult.account_number.cleanAccountNumber() ?: dbAccNum
                        val finalBank = edgeResult.bank_name?.takeIf { it.isNotBlank() } ?: dbBank.orEmpty()
                        val strictEdgeAccountName = com.example.data.repository.VtuRepository.resolveAccountHolderName(
                            rawAccountName = edgeResult.account_name,
                            fullName = fullName,
                            email = dbEmail
                        )
                        if (finalNum != null) {
                            _ninVerified.value = true
                            _accountDetails.value = PermanentAccountResponse(
                                account_number = finalNum,
                                bank_name = finalBank,
                                account_name = strictEdgeAccountName
                            )
                            persistToSupabase(
                                userId = cleanUid,
                                email = dbEmail,
                                accNumber = finalNum,
                                bank = finalBank,
                                accName = strictEdgeAccountName,
                                nin = dbNin.orEmpty()
                            )
                            withContext(Dispatchers.Main) {
                                onAccountRestored?.invoke(finalNum, finalBank, strictEdgeAccountName)
                            }
                        }
                    } else if (dbAccNum != null) {
                        val resolvedFallbackName = com.example.data.repository.VtuRepository.resolveAccountHolderName(
                            rawAccountName = _accountDetails.value?.account_name,
                            fullName = fullName,
                            email = dbEmail
                        )
                        _accountDetails.value = PermanentAccountResponse(
                            account_number = dbAccNum,
                            bank_name = dbBank,
                            account_name = resolvedFallbackName.ifBlank { null }
                        )
                        withContext(Dispatchers.Main) {
                            onAccountRestored?.invoke(
                                dbAccNum!!,
                                dbBank.orEmpty(),
                                resolvedFallbackName
                            )
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
            val baseUrl = com.example.util.SecurityVault.supabaseUrl()
            val token = resolveAuthToken()
            val bearer = if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"
            val okClient = OkHttpClient.Builder()
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(6, TimeUnit.SECONDS)
                .build()
            try {
                val resetJson = JSONObject().apply {
                    put("permanent_account_number", JSONObject.NULL)
                    put("permanent_account_bank", JSONObject.NULL)
                    put("is_creating_account", false)
                }
                val req = Request.Builder()
                    .url("$baseUrl/rest/v1/users?id=eq.$cleanUid")
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", bearer)
                    .addHeader("Prefer", "return=minimal")
                    .patch(resetJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                okClient.newCall(req).execute().close()
            } catch (_: Throwable) {}
            val client = supabase
            if (client != null) {
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

        /**
         * Extracts the account holder name returned by the Create-Permanent-Account
         * Supabase Edge Function response JSON and ensures it follows the required
         * "Thompson Daniel/ <Customer Full Name>" format.
         */
        fun extractAccountNameFromEdgeResponse(
            json: JSONObject?,
            fallbackFullName: String? = null,
            email: String? = null
        ): String {
            if (json == null) {
                return com.example.data.repository.VtuRepository.resolveAccountHolderName(
                    rawAccountName = null,
                    fullName = fallbackFullName,
                    email = email
                )
            }
            val dataObj = json.optJSONObject("data")
            val nestedDataObj = dataObj?.optJSONObject("data")
            val accountObj = json.optJSONObject("account") ?: dataObj?.optJSONObject("account")
            val virtualAccountObj = json.optJSONObject("virtual_account") ?: dataObj?.optJSONObject("virtual_account")
            val resultObj = json.optJSONObject("result") ?: dataObj?.optJSONObject("result")

            val containers = listOfNotNull(json, dataObj, nestedDataObj, accountObj, virtualAccountObj, resultObj)
            val nameKeys = listOf(
                "account_name",
                "accountName",
                "permanent_account_name",
                "permanentAccountName",
                "virtual_account_name",
                "virtualAccountName",
                "account_holder_name",
                "accountHolderName",
                "customer_name",
                "customerName",
                "narration",
                "account_narration",
                "full_name",
                "fullName",
                "name"
            )

            for (key in nameKeys) {
                for (container in containers) {
                    val rawKeyVal = container.optCleanString(key)
                    if (!rawKeyVal.isNullOrBlank()) {
                        val candidate = com.example.data.repository.VtuRepository.resolveAccountHolderName(
                            rawAccountName = rawKeyVal,
                            fullName = fallbackFullName,
                            email = email
                        )
                        if (candidate.isNotBlank()) {
                            return candidate
                        }
                    }
                }
            }

            // Check if first_name / last_name are explicitly provided inside the Edge Function response
            for (container in containers) {
                val first = container.optCleanString("firstname")
                    ?: container.optCleanString("firstName")
                    ?: container.optCleanString("first_name")
                val last = container.optCleanString("lastname")
                    ?: container.optCleanString("lastName")
                    ?: container.optCleanString("last_name")
                if (!first.isNullOrBlank() || !last.isNullOrBlank()) {
                    val combined = com.example.data.repository.VtuRepository.resolveAccountHolderName(
                        rawAccountName = listOfNotNull(first, last).joinToString(" "),
                        fullName = fallbackFullName,
                        email = email
                    )
                    if (combined.isNotBlank()) {
                        return combined
                    }
                }
            }

            // Check Flutterwave's "note" field if formatted as "Please make a bank transfer to <Account Name>"
            for (container in containers) {
                val note = container.optCleanString("note") ?: continue
                val prefix = "Please make a bank transfer to "
                if (note.startsWith(prefix, ignoreCase = true)) {
                    val extracted = com.example.data.repository.VtuRepository.resolveAccountHolderName(
                        rawAccountName = note.substring(prefix.length).trim().trimEnd('.'),
                        fullName = fallbackFullName,
                        email = email
                    )
                    if (extracted.isNotBlank()) {
                        return extracted
                    }
                }
            }

            return com.example.data.repository.VtuRepository.resolveAccountHolderName(
                rawAccountName = null,
                fullName = fallbackFullName,
                email = email
            )
        }

        fun extractAccountNameFromEdgeResponseString(
            responseText: String?,
            fallbackFullName: String? = null,
            email: String? = null
        ): String {
            if (responseText.isNullOrBlank()) {
                return com.example.data.repository.VtuRepository.resolveAccountHolderName(
                    rawAccountName = null,
                    fullName = fallbackFullName,
                    email = email
                )
            }
            val fromOrgJson = try {
                extractAccountNameFromEdgeResponse(JSONObject(responseText), fallbackFullName, email)
            } catch (_: Throwable) {
                ""
            }
            if (fromOrgJson.isNotBlank()) return fromOrgJson

            return try {
                val root = kotlinx.serialization.json.Json.parseToJsonElement(responseText) as? kotlinx.serialization.json.JsonObject
                    ?: return com.example.data.repository.VtuRepository.resolveAccountHolderName(null, fallbackFullName, email)
                val dataObj = root["data"] as? kotlinx.serialization.json.JsonObject
                val nestedDataObj = dataObj?.get("data") as? kotlinx.serialization.json.JsonObject
                val accountObj = (root["account"] as? kotlinx.serialization.json.JsonObject)
                    ?: (dataObj?.get("account") as? kotlinx.serialization.json.JsonObject)
                val virtualAccountObj = (root["virtual_account"] as? kotlinx.serialization.json.JsonObject)
                    ?: (dataObj?.get("virtual_account") as? kotlinx.serialization.json.JsonObject)
                val resultObj = (root["result"] as? kotlinx.serialization.json.JsonObject)
                    ?: (dataObj?.get("result") as? kotlinx.serialization.json.JsonObject)

                val containers = listOfNotNull(root, dataObj, nestedDataObj, accountObj, virtualAccountObj, resultObj)
                val nameKeys = listOf(
                    "account_name",
                    "accountName",
                    "permanent_account_name",
                    "permanentAccountName",
                    "virtual_account_name",
                    "virtualAccountName",
                    "account_holder_name",
                    "accountHolderName",
                    "customer_name",
                    "customerName",
                    "narration",
                    "account_narration",
                    "full_name",
                    "fullName",
                    "name"
                )
                for (key in nameKeys) {
                    for (container in containers) {
                        val rawVal = (container[key] as? kotlinx.serialization.json.JsonPrimitive)?.content
                        if (!rawVal.isNullOrBlank()) {
                            val candidate = com.example.data.repository.VtuRepository.resolveAccountHolderName(
                                rawAccountName = rawVal,
                                fullName = fallbackFullName,
                                email = email
                            )
                            if (candidate.isNotBlank()) {
                                return candidate
                            }
                        }
                    }
                }
                com.example.data.repository.VtuRepository.resolveAccountHolderName(null, fallbackFullName, email)
            } catch (_: Throwable) {
                com.example.data.repository.VtuRepository.resolveAccountHolderName(null, fallbackFullName, email)
            }
        }

        suspend fun fetchFromCreatePermanentAccountEdgeFunction(
            userId: String,
            email: String = "",
            fullName: String = "",
            phone: String? = null,
            nin: String? = null,
            accessToken: String? = null
        ): PermanentAccountResponse? = withContext(Dispatchers.IO) {
            val cleanUid = userId.trim()
            if (cleanUid.isBlank() || cleanUid == "usr_guest" || cleanUid == "usr_default") return@withContext null
            val cleanPhone = com.example.data.repository.VtuRepository.sanitizeRealPhone(phone)?.filter { it.isDigit() }.orEmpty()
            val cleanNin = nin?.filter { it.isDigit() }?.take(11).orEmpty()
            val sanitizedName = com.example.data.repository.VtuRepository.sanitizeFullName(fullName, email)
            val nameParts = sanitizedName.split("\\s+".toRegex()).filter { it.isNotBlank() }
            val firstName = nameParts.firstOrNull().orEmpty()
            val lastName = if (nameParts.size > 1) nameParts.drop(1).joinToString(" ") else firstName

            // Release any stale lock on public.users before querying Create-Permanent-Account
            try {
                val baseUrl = com.example.util.SecurityVault.supabaseUrl()
                val anonKey = SupabaseProvider.rawKey
                val token = accessToken ?: SupabaseProvider.resolveSessionAccessToken()
                val bearer = if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"
                val unlockBody = JSONObject().apply { put("is_creating_account", false) }
                val unlockReq = Request.Builder()
                    .url("$baseUrl/rest/v1/users?id=eq.$cleanUid")
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", bearer)
                    .addHeader("Prefer", "return=minimal")
                    .patch(unlockBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .build()
                    .newCall(unlockReq)
                    .execute()
                    .close()
            } catch (_: Throwable) {}

            val payload = JSONObject().apply {
                put("userId", cleanUid)
                put("user_id", cleanUid)
                if (email.isNotBlank()) put("email", email.trim().lowercase())
                if (firstName.isNotBlank()) {
                    put("firstname", firstName)
                    put("firstName", firstName)
                }
                if (lastName.isNotBlank()) {
                    put("lastname", lastName)
                    put("lastName", lastName)
                }
                if (cleanPhone.isNotBlank()) {
                    put("phone", cleanPhone)
                    put("phonenumber", cleanPhone)
                }
                if (cleanNin.length == 11) {
                    put("nin", cleanNin)
                    put("id_number", cleanNin)
                    put("id_type", "NIN")
                }
            }

            try {
                val (_, responseText) = SupabaseProvider.postEdgeFunction(
                    functionName = "Create-Permanent-Account",
                    bodyJson = payload.toString(),
                    accessTokenOverride = accessToken
                )
                if (responseText.isBlank()) return@withContext null
                val json = try { JSONObject(responseText) } catch (_: Throwable) { null } ?: return@withContext null
                val dataObj = json.optJSONObject("data")
                val nestedDataObj = dataObj?.optJSONObject("data")
                val accountObj = json.optJSONObject("account") ?: dataObj?.optJSONObject("account")
                val virtualAccountObj = json.optJSONObject("virtual_account") ?: dataObj?.optJSONObject("virtual_account")

                val rawAccNumber = json.optCleanString("account_number")
                    ?: json.optCleanString("accountNumber")
                    ?: json.optCleanString("permanent_account_number")
                    ?: dataObj?.optCleanString("account_number")
                    ?: dataObj?.optCleanString("accountNumber")
                    ?: nestedDataObj?.optCleanString("account_number")
                    ?: accountObj?.optCleanString("account_number")
                    ?: virtualAccountObj?.optCleanString("account_number")
                val accNumber = rawAccNumber?.cleanAccountNumber()

                val bank = json.optCleanString("bank_name")
                    ?: json.optCleanString("bankName")
                    ?: json.optCleanString("permanent_account_bank")
                    ?: dataObj?.optCleanString("bank_name")
                    ?: dataObj?.optCleanString("bankName")
                    ?: nestedDataObj?.optCleanString("bank_name")
                    ?: accountObj?.optCleanString("bank_name")
                    ?: virtualAccountObj?.optCleanString("bank_name")

                val acctName = extractAccountNameFromEdgeResponse(
                    json = json,
                    fallbackFullName = sanitizedName,
                    email = email
                )

                if (accNumber != null) {
                    return@withContext PermanentAccountResponse(
                        account_number = accNumber,
                        bank_name = bank,
                        account_name = acctName
                    )
                }
            } catch (_: Throwable) {}
            null
        }
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
        onSuccess: ((String, String, String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _accountDetails.value = null

            withContext(Dispatchers.IO) {
                try {
                    val cleanUid = userId.trim()
                    val cleanId = idNumber.filter { it.isDigit() }.take(11)
                    val cleanPhone = com.example.data.repository.VtuRepository.sanitizeRealPhone(phone)?.filter { it.isDigit() } ?: ""
                    val cleanFullName = com.example.data.repository.VtuRepository.sanitizeFullName(
                        fullName.ifBlank { listOf(firstname.trim(), lastname.trim()).filter { it.isNotBlank() }.joinToString(" ") },
                        email
                    )
                    val nameParts = cleanFullName.split("\\s+".toRegex()).filter { it.isNotBlank() }
                    val resolvedFirst = nameParts.firstOrNull() ?: firstname.trim()
                    val resolvedLast = if (nameParts.size > 1) nameParts.drop(1).joinToString(" ") else lastname.trim().ifBlank { resolvedFirst }

                    // Ensure public.users lock is cleared before invoking Create-Permanent-Account
                    try {
                        val baseUrl = com.example.util.SecurityVault.supabaseUrl()
                        val token = resolveAuthToken()
                        val bearer = if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"
                        val unlockJson = JSONObject().apply {
                            put("is_creating_account", false)
                        }
                        val unlockReq = Request.Builder()
                            .url("$baseUrl/rest/v1/users?id=eq.$cleanUid")
                            .addHeader("apikey", anonKey)
                            .addHeader("Authorization", bearer)
                            .addHeader("Prefer", "return=minimal")
                            .patch(unlockJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                            .build()
                        OkHttpClient.Builder()
                            .connectTimeout(5, TimeUnit.SECONDS)
                            .readTimeout(5, TimeUnit.SECONDS)
                            .build()
                            .newCall(unlockReq)
                            .execute()
                            .close()
                    } catch (_: Throwable) {}

                    // Send request to Create-Permanent-Account with firstname & lastname so the Edge Function formats "Thompson Daniel/ <Name>"
                    val primaryPayload = JSONObject().apply {
                        put("userId", cleanUid)
                        put("user_id", cleanUid)
                        put("email", email.trim().lowercase())
                        if (resolvedFirst.isNotBlank()) {
                            put("firstname", resolvedFirst)
                            put("firstName", resolvedFirst)
                        }
                        if (resolvedLast.isNotBlank()) {
                            put("lastname", resolvedLast)
                            put("lastName", resolvedLast)
                        }
                        put("phone", cleanPhone)
                        put("phonenumber", cleanPhone)
                        put("nin", cleanId)
                        put("id_number", cleanId)
                        put("id_type", idType)
                    }

                    val token = resolveAuthToken()
                    val (statusCode, responseText) = SupabaseProvider.postEdgeFunction(
                        functionName = "Create-Permanent-Account",
                        bodyJson = primaryPayload.toString(),
                        accessTokenOverride = token
                    )
                    Log.d("PermanentAccount", "Response from Create-Permanent-Account ($cleanUid): $responseText")

                    val json = try { JSONObject(responseText) } catch (_: Throwable) { null }
                    val dataObj = json?.optJSONObject("data")
                    val rawAccNumber = json?.optCleanString("account_number")
                        ?: json?.optCleanString("accountNumber")
                        ?: json?.optCleanString("permanent_account_number")
                        ?: dataObj?.optCleanString("account_number")
                        ?: dataObj?.optCleanString("accountNumber")
                        ?: dataObj?.optCleanString("order_ref")

                    val accNumber = rawAccNumber?.cleanAccountNumber()

                    if (accNumber != null) {
                        val bank = json?.optCleanString("bank_name")
                            ?: json?.optCleanString("bankName")
                            ?: json?.optCleanString("permanent_account_bank")
                            ?: dataObj?.optCleanString("bank_name")
                            ?: dataObj?.optCleanString("bankName")
                            ?: ""

                        val acctName = extractAccountNameFromEdgeResponse(
                            json = json,
                            fallbackFullName = cleanFullName,
                            email = email
                        )

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
                            userId = cleanUid,
                            email = email,
                            accNumber = accNumber,
                            bank = bank,
                            accName = acctName,
                            nin = cleanId
                        )

                        withContext(Dispatchers.Main) {
                            onSuccess?.invoke(accNumber, bank, acctName)
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

        val payloadObj = JSONObject().apply {
            put("permanent_account_number", accNumber)
            if (bank.isNotBlank()) put("permanent_account_bank", bank)
        }

        val filterParam = "id=eq.$cleanUid"
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
}

@Composable
fun PermanentAccountSection(
    currentUser: SupabaseUser? = null,
    onAccountCreated: ((accountNumber: String, bankName: String, accountName: String) -> Unit)? = null,
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

    LaunchedEffect(userId, currentUser?.virtualAccountNumber, currentUser?.virtualAccountName) {
        if (userId.isBlank()) {
            idNumber = ""
            viewModel.resetState()
        } else {
            val email = currentUser?.email
                ?: supabaseClient?.auth?.currentUserOrNull()?.email
                ?: ""
            val resolvedFullName = com.example.data.repository.VtuRepository.sanitizeFullName(
                currentUser?.fullName,
                email
            )
            viewModel.checkExisting(
                userId = userId,
                email = email,
                fullName = resolvedFullName,
                phone = currentUser?.phone,
                nin = currentUser?.nin ?: currentUser?.ninHash,
                currentVa = currentUser?.virtualAccountNumber,
                onAccountRestored = { acc, bankName, acctName ->
                    if (currentUser?.virtualAccountNumber != acc ||
                        currentUser?.virtualBankName != bankName ||
                        (acctName.isNotBlank() && currentUser?.virtualAccountName != acctName)
                    ) {
                        onAccountCreated?.invoke(acc, bankName, acctName)
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
                    rawAccountName = details?.account_name?.takeIf { it.isNotBlank() } ?: currentUser?.virtualAccountName,
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
                            onSuccess = { acc, bankName, acctName ->
                                onAccountCreated?.invoke(acc, bankName, acctName)
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
