package com.example.data.model

data class SupabaseUser(
    val id: String,
    val email: String,
    val fullName: String = "",
    val phone: String? = null,
    val createdAt: String? = null,
    val nin: String? = null,
    val ninHash: String? = null,
    val ninVerified: Boolean = false,
    val virtualAccountNumber: String? = null,
    val virtualBankName: String? = null,
    val virtualAccountName: String? = null,
    val dynamicAccountNumber: String? = null,
    val dynamicBankName: String? = null,
    val dynamicAccountName: String? = null,
    val dynamicAccountAmount: Double? = null
)

data class SupabaseSession(
    val accessToken: String,
    val refreshToken: String? = null,
    val user: SupabaseUser
)

sealed class AuthResult {
    data class Success(val user: SupabaseUser, val session: SupabaseSession? = null, val message: String = "Success") : AuthResult()
    data class RequiresEmailConfirmation(val email: String) : AuthResult()
    data class Error(val message: String) : AuthResult()
}
