package com.vtu.app.wallet

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.FilterOperator
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.contentOrNull
import java.math.BigDecimal
import com.example.auth.SupabaseInstance
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

// 1. Data model — shape of the live update message
@Serializable
data class WalletBalanceUpdate(
    val user_id: String,
    val wallet_balance: String
)

// 2. Shared In-Memory Realtime Wallet State Bridge (Single Source of Truth for public.users.wallet_balance)
object SharedWalletObserver {
    private val _liveBalance = MutableStateFlow<Double?>(null)
    val liveBalance: StateFlow<Double?> = _liveBalance.asStateFlow()

    private val listeners = mutableListOf<(Double) -> Unit>()
    private val isDispatching = java.util.concurrent.atomic.AtomicBoolean(false)

    fun addListener(listener: (Double) -> Unit) {
        synchronized(listeners) {
            if (!listeners.contains(listener)) {
                listeners.add(listener)
            }
        }
        _liveBalance.value?.let { bal ->
            if (isDispatching.compareAndSet(false, true)) {
                try {
                    listener(bal)
                } finally {
                    isDispatching.set(false)
                }
            }
        }
    }

    fun removeListener(listener: (Double) -> Unit) {
        synchronized(listeners) { listeners.remove(listener) }
    }

    fun updateBalance(newBalance: Double) {
        val previous = _liveBalance.value
        _liveBalance.value = newBalance
        if (previous != null && kotlin.math.abs(previous - newBalance) < 0.0001) {
            return
        }
        if (!isDispatching.compareAndSet(false, true)) {
            return
        }
        try {
            val snapshot = synchronized(listeners) { listeners.toList() }
            snapshot.forEach { it(newBalance) }
        } finally {
            isDispatching.set(false)
        }
    }

    fun clear() {
        _liveBalance.value = null
    }
}

// 3. ViewModel — holds the Realtime-subscribed balance from public.users.wallet_balance
class WalletViewModel : ViewModel() {
    private val _walletBalance = MutableStateFlow<BigDecimal?>(
        SharedWalletObserver.liveBalance.value?.let { BigDecimal.valueOf(it) }
    )
    val walletBalance: StateFlow<BigDecimal?> = _walletBalance.asStateFlow()
    private var walletChannel: RealtimeChannel? = null
    private var activeSubscribedUserId: String? = null
    private var externalBalanceListener: ((Double) -> Unit)? = null

    private val supabase get() = SupabaseInstance.client

    private val observerListener: (Double) -> Unit = { bal ->
        _walletBalance.value = BigDecimal.valueOf(bal)
    }

    init {
        SharedWalletObserver.addListener(observerListener)
        SharedWalletObserver.liveBalance.onEach { bal ->
            if (bal == null) {
                _walletBalance.value = null
            } else {
                _walletBalance.value = BigDecimal.valueOf(bal)
            }
        }.launchIn(viewModelScope)
    }

    fun resetState() {
        _walletBalance.value = null
        activeSubscribedUserId = null
        externalBalanceListener?.let { SharedWalletObserver.removeListener(it) }
        externalBalanceListener = null
        val ch = walletChannel
        walletChannel = null
        if (ch != null) {
            viewModelScope.launch(Dispatchers.IO) {
                try { ch.unsubscribe() } catch (_: Throwable) {}
            }
        }
    }

    fun setWalletBalance(balance: Double?) {
        _walletBalance.value = balance?.let { BigDecimal.valueOf(it) }
        if (balance != null) {
            SharedWalletObserver.updateBalance(balance)
        }
    }

    fun setWalletBalance(balance: BigDecimal?) {
        _walletBalance.value = balance
        if (balance != null) {
            SharedWalletObserver.updateBalance(balance.toDouble())
        }
    }

    /**
     * Queries public.users.wallet_balance by the logged-in user's id — the exact same table,
     * row filter, and column used by the Realtime subscription.
     */
    suspend fun queryPublicUsersWalletBalance(userId: String? = null): Double? = withContext(Dispatchers.IO) {
        val client = supabase
        val resolvedUserId = userId?.takeIf { it.isNotBlank() && it != "usr_guest" && it != "usr_default" }
            ?: try {
                client?.auth?.retrieveUserForCurrentSession(updateSession = true)?.id
            } catch (_: Throwable) {
                try { client?.auth?.currentUserOrNull()?.id } catch (_: Throwable) { null }
            }

        if (resolvedUserId.isNullOrBlank()) {
            return@withContext SharedWalletObserver.liveBalance.value
        }

        // 1. Query public.users.wallet_balance via Supabase Postgrest client
        if (client != null) {
            try {
                val data = client.from("users").select(Columns.list("wallet_balance")) {
                    filter { eq("id", resolvedUserId) }
                    single()
                }.decodeSingle<JsonObject>()

                val rawBal = data["wallet_balance"]?.jsonPrimitive?.doubleOrNull
                    ?: data["wallet_balance"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                    ?: data["wallet_balance"]?.toString()?.trim('"')?.toDoubleOrNull()

                if (rawBal != null) {
                    SharedWalletObserver.updateBalance(rawBal)
                    return@withContext rawBal
                }
            } catch (error: Throwable) {
                Log.w("WalletViewModel", "Postgrest public.users.wallet_balance query note: ${error.message}")
            }
        }

        // 2. Direct REST query to public.users?select=wallet_balance&id=eq.<resolvedUserId>
        try {
            val baseUrl = SupabaseInstance.safeUrl
            val anonKey = SupabaseInstance.rawKey
            val token = try { client?.auth?.currentAccessTokenOrNull() } catch (_: Throwable) { null } ?: anonKey
            val bearer = if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"
            val req = Request.Builder()
                .url("$baseUrl/rest/v1/users?select=wallet_balance&id=eq.$resolvedUserId")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", bearer)
                .get()
                .build()

            OkHttpClient().newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val bodyStr = res.body?.string().orEmpty()
                    val arr = org.json.JSONArray(bodyStr)
                    if (arr.length() > 0) {
                        val obj = arr.getJSONObject(0)
                        if (obj.has("wallet_balance") && !obj.isNull("wallet_balance")) {
                            val remoteBal = obj.getDouble("wallet_balance")
                            SharedWalletObserver.updateBalance(remoteBal)
                            return@withContext remoteBal
                        }
                    }
                }
            }
        } catch (error: Throwable) {
            Log.w("WalletViewModel", "REST public.users.wallet_balance query note: ${error.message}")
        }

        // Fallback to the current value already held by the active Realtime subscription
        SharedWalletObserver.liveBalance.value
    }

    fun loadInitialBalance(userId: String? = null) {
        viewModelScope.launch {
            queryPublicUsersWalletBalance(userId)
        }
    }

    /**
     * Manual reload action: does NOT run a separate computation or query any other table.
     * Queries the identical wallet_balance column from public.users by the logged-in user's id
     * (or re-displays the current value held by the active Realtime subscription), so manual reload
     * and live Realtime updates share a single source of truth.
     */
    fun refreshWalletRealtime(userId: String? = null, onDone: ((Double) -> Unit)? = null) {
        viewModelScope.launch {
            val freshOrCurrent = queryPublicUsersWalletBalance(userId)
                ?: SharedWalletObserver.liveBalance.value
                ?: _walletBalance.value?.toDouble()
                ?: 0.0
            onDone?.invoke(freshOrCurrent)
        }
    }

    fun fetchWalletBalance(userId: String? = null) {
        viewModelScope.launch {
            queryPublicUsersWalletBalance(userId)
        }
    }

    /**
     * Withdraw/debit feature:
     * Calls the Supabase RPC debit_wallet with an amount, and refreshes wallet_balance
     * strictly from public.users.wallet_balance (no client-side subtraction).
     */
    suspend fun debitWallet(amount: Double): Result<Unit> {
        return withContext(Dispatchers.IO) {
            val client = supabase
            var rpcSucceeded = false
            var insufficientBalance = false

            if (client != null) {
                try {
                    val params = buildJsonObject {
                        put("amount", amount)
                    }
                    client.postgrest.rpc("debit_wallet", params)
                    rpcSucceeded = true
                } catch (e: Throwable) {
                    Log.w("WalletViewModel", "RPC debit_wallet returned error: ${e.message}", e)
                    if (e.message?.contains("insufficient", ignoreCase = true) == true) {
                        insufficientBalance = true
                    }
                }
            } else {
                try {
                    val anonKey = SupabaseInstance.rawKey
                    val token = try { SupabaseInstance.client?.auth?.currentAccessTokenOrNull() } catch (_: Throwable) { null } ?: anonKey
                    val bearer = if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"
                    val req = Request.Builder()
                        .url("${SupabaseInstance.safeUrl}/rest/v1/rpc/debit_wallet")
                        .addHeader("apikey", anonKey)
                        .addHeader("Authorization", bearer)
                        .addHeader("Content-Type", "application/json")
                        .post(buildJsonObject { put("amount", amount) }.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    OkHttpClient().newCall(req).execute().use { res ->
                        if (res.isSuccessful || res.code in 200..204) {
                            rpcSucceeded = true
                        } else {
                            val body = res.body?.string() ?: ""
                            if (body.contains("insufficient", ignoreCase = true)) {
                                insufficientBalance = true
                            }
                        }
                    }
                } catch (e: Throwable) {
                    Log.w("WalletViewModel", "HTTP RPC debit_wallet error: ${e.message}", e)
                }
            }

            if (insufficientBalance) {
                return@withContext Result.failure(Exception("Insufficient wallet balance"))
            }

            if (rpcSucceeded) {
                queryPublicUsersWalletBalance()
            }
            Result.success(Unit)
        }
    }

    fun startWalletUpdates(userId: String) {
        if (userId.isBlank() || userId == "usr_guest" || userId == "usr_default") {
            resetState()
            return
        }
        if (activeSubscribedUserId != null && activeSubscribedUserId != userId) {
            resetState()
        }
        if (activeSubscribedUserId == userId && walletChannel != null) return
        activeSubscribedUserId = userId

        viewModelScope.launch {
            val client = supabase ?: return@launch
            try {
                walletChannel?.unsubscribe()
            } catch (_: Throwable) {}

            try {
                walletChannel = observeWalletBalance(
                    supabase = client,
                    userId = userId,
                    scope = viewModelScope
                ) { newBalance ->
                    _walletBalance.value = BigDecimal.valueOf(newBalance)
                }
            } catch (e: Throwable) {
                Log.w("WalletViewModel", "Error attaching realtime observeWalletBalance: ${e.message}", e)
            }
        }
    }

    fun subscribeToWalletBalance(userId: String, onBalanceChanged: (Double) -> Unit) {
        if (userId.isBlank() || userId == "usr_guest" || userId == "usr_default") {
            resetState()
            return
        }
        startWalletUpdates(userId)
        externalBalanceListener?.let { SharedWalletObserver.removeListener(it) }
        externalBalanceListener = onBalanceChanged
        SharedWalletObserver.addListener(onBalanceChanged)
    }

    override fun onCleared() {
        SharedWalletObserver.removeListener(observerListener)
        externalBalanceListener?.let { SharedWalletObserver.removeListener(it) }
        externalBalanceListener = null
        activeSubscribedUserId = null
        val ch = walletChannel
        walletChannel = null
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ch?.unsubscribe()
            } catch (_: Throwable) {}
        }
        super.onCleared()
    }
}

/**
 * Realtime observer for Supabase 'public.users' table wallet_balance updates.
 * Listens directly to PostgreSQL changes on public.users filtered by the logged-in user's ID.
 */
fun observeWalletBalance(
    supabase: SupabaseClient,
    userId: String,
    scope: CoroutineScope,
    onBalanceChanged: (Double) -> Unit
): RealtimeChannel? {
    return try {
        val uniqueTopic = "wallet-$userId-${System.nanoTime()}"
        val channel = supabase.channel(uniqueTopic)

        val changes = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "users"
            filter("id", FilterOperator.EQ, userId)
        }

        scope.launch(Dispatchers.IO) {
            try {
                changes.collect { update ->
                    val raw = update.record["wallet_balance"]
                    val newBalance = raw?.jsonPrimitive?.doubleOrNull
                        ?: raw?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                        ?: raw?.toString()?.trim('"')?.toDoubleOrNull()
                        ?: raw?.toString()?.toDoubleOrNull()
                    if (newBalance != null) {
                        SharedWalletObserver.updateBalance(newBalance)
                        withContext(Dispatchers.Main) {
                            onBalanceChanged(newBalance)
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.w("WalletFeature", "Realtime flow collection ended safely: ${e.message}")
            }
        }

        scope.launch(Dispatchers.IO) {
            try {
                supabase.realtime.connect()
                channel.subscribe()
            } catch (e: Throwable) {
                Log.w("WalletFeature", "Realtime channel connect/subscribe note: ${e.message}")
            }
        }

        channel
    } catch (e: Throwable) {
        Log.w("WalletFeature", "Realtime observeWalletBalance skipped: ${e.message}")
        null
    }
}

