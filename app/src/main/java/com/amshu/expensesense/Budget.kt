package com.amshu.expensesense

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey val monthYear: String = "", 
    val totalBudget: Double = 0.0,
    val remainingBudget: Double = 0.0,
    val categoryBudgets: Map<String, Double> = emptyMap()
)
