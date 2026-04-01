package com.amshu.expensesense

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "debit_cards")
data class DebitCard(
    @PrimaryKey val cardName: String = "",
    val cardHolderName: String? = null,
    val cardNumber: String? = null,
    val bankName: String? = null,
    val last4Digits: String? = null,
    val linkedBankAccountId: String? = null,
    val drawableName: String? = null,
    val orderIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
