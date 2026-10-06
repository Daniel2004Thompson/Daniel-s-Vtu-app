package com.example.data.remote

import com.example.auth.SupabaseProvider
import io.github.jan.supabase.SupabaseClient

object SupabaseRealtimeClient {
    val isConfigured: Boolean
        get() = SupabaseProvider.isConfigured

    val client: SupabaseClient
        get() = SupabaseProvider.nonNullClient
}
