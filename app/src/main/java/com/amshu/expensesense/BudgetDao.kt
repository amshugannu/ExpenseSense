package com.amshu.expensesense

import androidx.room.*

@Dao
interface BudgetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertBudget(budget: Budget)

    @Query("SELECT * FROM budgets WHERE monthYear = :monthYear LIMIT 1")
    fun getBudget(monthYear: String): Budget?

    @Query("UPDATE budgets SET remainingBudget = :newRemaining WHERE monthYear = :monthYear")
    fun updateRemainingBudget(monthYear: String, newRemaining: Double)

    @Update
    fun updateBudget(budget: Budget)

    @Query("SELECT * FROM budgets")
    fun getAllBudgets(): List<Budget>
}
