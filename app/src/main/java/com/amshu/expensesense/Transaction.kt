package com.amshu.expensesense

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [androidx.room.Index(value = ["firebaseId"], unique = true)]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val amount: Double,
    val category: String,
    val accountName: String, // Associate with an account (legacy, can be referenceId now)
    val timestamp: Long,
    val paymentMethod: String = "Cash", // Cash, UPI, Debit, Credit
    val referenceId: String? = null,    // ID of the bank/card
    val firebaseId: String? = null
)
