package com.amshu.expensesense

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "credit_cards")
data class CreditCard(
    @PrimaryKey val cardName: String = "",
    val cardHolderName: String? = null,
    val cardNumber: String? = null,
    val bankName: String? = null,
    val last4Digits: String? = null,
    val totalLimit: Double? = null,
    val availableLimit: Double? = null,
    val drawableName: String? = null,
    val orderIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
