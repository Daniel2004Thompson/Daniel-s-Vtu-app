package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profiles WHERE userId = :userId LIMIT 1")
    fun observeUserProfile(userId: String): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profiles WHERE userId = :userId LIMIT 1")
    suspend fun getUserProfile(userId: String): UserProfileEntity?

    @Query("SELECT * FROM user_profiles WHERE LOWER(email) = LOWER(:email) LIMIT 1")
    suspend fun getUserProfileByEmail(email: String): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUserProfile(profile: UserProfileEntity)

    @Query("UPDATE user_profiles SET walletBalance = :walletBalance, updatedAt = :updatedAt WHERE userId = :userId")
    suspend fun updateWalletBalance(userId: String, walletBalance: Double, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE user_profiles SET cashbackBalance = :cashbackBalance, updatedAt = :updatedAt WHERE userId = :userId")
    suspend fun updateCashbackBalance(userId: String, cashbackBalance: Double, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM user_profiles WHERE userId = :userId")
    suspend fun deleteUserProfile(userId: String)
}
