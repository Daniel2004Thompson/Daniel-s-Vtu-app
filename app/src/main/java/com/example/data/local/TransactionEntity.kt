package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["userId", "reference"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: String = "",
    val reference: String,
    val serviceType: String, // AIRTIME, DATA, ELECTRICITY, CABLE_TV, EDUCATION, WALLET_FUNDING
    val provider: String,    // MTN, Airtel, Glo, 9mobile, IKEDC, DSTV, WAEC, etc.
    val recipient: String,   // Phone number, meter number, smartcard number
    val amount: Double,
    val discountOrCashback: Double = 0.0,
    val status: String,      // SUCCESSFUL, PENDING, FAILED
    val timestamp: Long = System.currentTimeMillis(),
    val tokenOrDetails: String? = null, // e.g. Prepaid meter 20-digit token or bundle name
    val customerName: String? = null
)

