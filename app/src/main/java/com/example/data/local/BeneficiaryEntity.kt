package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "beneficiaries",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["userId", "recipient", "serviceType"])
    ]
)
data class BeneficiaryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: String = "",
    val name: String,
    val recipient: String,
    val serviceType: String,
    val provider: String,
    val lastUsedTimestamp: Long = System.currentTimeMillis()
)

