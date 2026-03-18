package com.example.es

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cards")
data class Card(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val cardHolderName: String,
    val cardNumber: String,
    val cardName: String,
    val cardType: String, // "Debit" or "Credit"
    val creditLimit: Double? = null,
    val availableLimit: Double? = null,
    val accountName: String? = null, // Linked account name for Debit cards
    val orderIndex: Int = 0
)
