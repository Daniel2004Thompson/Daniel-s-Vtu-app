package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BeneficiaryDao {
    @Query("SELECT * FROM beneficiaries WHERE userId = :userId ORDER BY lastUsedTimestamp DESC")
    fun getAllBeneficiariesForUser(userId: String): Flow<List<BeneficiaryEntity>>

    @Query("SELECT * FROM beneficiaries WHERE userId = :userId AND serviceType = :serviceType ORDER BY lastUsedTimestamp DESC")
    fun getBeneficiariesByServiceForUser(userId: String, serviceType: String): Flow<List<BeneficiaryEntity>>

    @Query("SELECT * FROM beneficiaries WHERE userId = :userId AND recipient = :recipient AND serviceType = :serviceType LIMIT 1")
    suspend fun getBeneficiaryForUser(userId: String, recipient: String, serviceType: String): BeneficiaryEntity?

    @Query("SELECT * FROM beneficiaries ORDER BY lastUsedTimestamp DESC")
    fun getAllBeneficiaries(): Flow<List<BeneficiaryEntity>>

    @Query("SELECT * FROM beneficiaries WHERE serviceType = :serviceType ORDER BY lastUsedTimestamp DESC")
    fun getBeneficiariesByService(serviceType: String): Flow<List<BeneficiaryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBeneficiary(beneficiary: BeneficiaryEntity): Long

    @Delete
    suspend fun deleteBeneficiary(beneficiary: BeneficiaryEntity)

    @Query("DELETE FROM beneficiaries WHERE userId = :userId")
    suspend fun clearAllForUser(userId: String)

    @Query("DELETE FROM beneficiaries")
    suspend fun clearAll()
}

