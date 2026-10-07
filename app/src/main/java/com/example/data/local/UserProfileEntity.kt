package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.SupabaseUser

@Entity(
    tableName = "user_profiles",
    indices = [Index(value = ["email"])]
)
data class UserProfileEntity(
    @PrimaryKey
    val userId: String,
    val email: String,
    val fullName: String = "",
    val phone: String? = null,
    val nin: String? = null,
    val ninHash: String? = null,
    val ninVerified: Boolean = false,
    val walletBalance: Double = 0.0,
    val cashbackBalance: Double = 0.0,
    val virtualAccountNumber: String? = null,
    val virtualBankName: String? = null,
    val virtualAccountName: String? = null,
    val dynamicAccountNumber: String? = null,
    val dynamicBankName: String? = null,
    val dynamicAccountName: String? = null,
    val dynamicAccountAmount: Double? = null,
    val biometricEnabled: Boolean = true,
    val appLockEnabled: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toSupabaseUser(): SupabaseUser = SupabaseUser(
        id = userId,
        email = email,
        fullName = fullName,
        phone = phone,
        nin = nin ?: ninHash,
        ninHash = ninHash ?: nin,
        ninVerified = ninVerified || !ninHash.isNullOrBlank() || !nin.isNullOrBlank(),
        virtualAccountNumber = virtualAccountNumber,
        virtualBankName = virtualBankName,
        virtualAccountName = virtualAccountName,
        dynamicAccountNumber = dynamicAccountNumber,
        dynamicBankName = dynamicBankName,
        dynamicAccountName = dynamicAccountName,
        dynamicAccountAmount = dynamicAccountAmount
    )

    companion object {
        fun fromSupabaseUser(
            user: SupabaseUser,
            walletBalance: Double = 0.0,
            cashbackBalance: Double = 0.0,
            biometricEnabled: Boolean = true,
            appLockEnabled: Boolean = false,
            notificationsEnabled: Boolean = true
        ): UserProfileEntity = UserProfileEntity(
            userId = user.id,
            email = user.email,
            fullName = user.fullName,
            phone = user.phone,
            nin = user.nin ?: user.ninHash,
            ninHash = user.ninHash ?: user.nin,
            ninVerified = user.ninVerified || !user.ninHash.isNullOrBlank() || !user.nin.isNullOrBlank(),
            walletBalance = walletBalance,
            cashbackBalance = cashbackBalance,
            virtualAccountNumber = user.virtualAccountNumber,
            virtualBankName = user.virtualBankName,
            virtualAccountName = user.virtualAccountName,
            dynamicAccountNumber = user.dynamicAccountNumber,
            dynamicBankName = user.dynamicBankName,
            dynamicAccountName = user.dynamicAccountName,
            dynamicAccountAmount = user.dynamicAccountAmount,
            biometricEnabled = biometricEnabled,
            appLockEnabled = appLockEnabled,
            notificationsEnabled = notificationsEnabled,
            updatedAt = System.currentTimeMillis()
        )
    }
}
