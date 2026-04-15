package com.amshu.expensesense

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

class BudgetRepository(
    private val budgetDao: BudgetDao,
    private val transactionDao: TransactionDao
) {

    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firebaseDatabase = FirebaseDatabase.getInstance()

    private fun getUsername(): String? {
        return firebaseAuth.currentUser?.email?.substringBefore("@")
    }

    suspend fun getBudget(monthYear: String): Budget? = withContext(Dispatchers.IO) {
        budgetDao.getBudget(monthYear)
    }

    suspend fun getMonthlySpent(monthYear: String): Double = withContext(Dispatchers.IO) {
        val allTransactions = transactionDao.getAllTransactions()
        allTransactions.filter {
            val cal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
            val m = cal.get(Calendar.MONTH) + 1
            val y = cal.get(Calendar.YEAR)
            val formatted = "%d-%02d".format(y, m) // YYYY-MM
            formatted == monthYear && it.transactionType == Transaction.TYPE_EXPENSE
        }.sumOf { it.amount }
    }

    suspend fun setBudget(totalAmount: Double, monthYear: String) = withContext(Dispatchers.IO) {
        val existingBudget = budgetDao.getBudget(monthYear)
        val categoryBudgets = existingBudget?.categoryBudgets ?: emptyMap()

        val currentMonthSpent = getMonthlySpent(monthYear)
        val remaining = totalAmount - currentMonthSpent
        val budget = Budget(monthYear, totalAmount, remaining, categoryBudgets)

        // Update local Room
        budgetDao.insertBudget(budget)
        
        // Sync to Firebase (Store under 'budgets' map with YYYY-MM key)
        val username = getUsername()
        if (username != null) {
            firebaseDatabase.getReference("users/$username/monthlyBudgets/$monthYear")
                .setValue(totalAmount)
            
            // Also store the full budget object for the details
            firebaseDatabase.getReference("users/$username/budgets/$monthYear")
                .setValue(budget)
        }
    }

    suspend fun updateCategoryBudget(monthYear: String, category: String, amount: Double) = withContext(Dispatchers.IO) {
        val existingBudget = budgetDao.getBudget(monthYear) ?: Budget(monthYear)
        val updatedMap = existingBudget.categoryBudgets.toMutableMap()
        updatedMap[category] = amount
        
        val totalSpent = getMonthlySpent(monthYear)
        val updatedBudget = existingBudget.copy(
            categoryBudgets = updatedMap,
            remainingBudget = existingBudget.totalBudget - totalSpent
        )
        
        budgetDao.insertBudget(updatedBudget)
        
        val username = getUsername()
        if (username != null) {
            firebaseDatabase.getReference("users/$username/budgets/$monthYear/categoryBudgets")
                .setValue(updatedMap)
        }
    }

    suspend fun deductFromBudget(monthYear: String, amount: Double) = withContext(Dispatchers.IO) {
        val currentBudget = budgetDao.getBudget(monthYear)
        if (currentBudget != null) {
            val newRemaining = currentBudget.remainingBudget - amount
            budgetDao.updateRemainingBudget(monthYear, newRemaining)
            
            val username = getUsername()
            if (username != null) {
                firebaseDatabase.getReference("users/$username/budgets/$monthYear/remainingBudget")
                    .setValue(newRemaining)
            }
        }
    }

    suspend fun reimburseBudget(monthYear: String, amount: Double) = withContext(Dispatchers.IO) {
        val currentBudget = budgetDao.getBudget(monthYear)
        if (currentBudget != null) {
            val newRemaining = currentBudget.remainingBudget + amount
            budgetDao.updateRemainingBudget(monthYear, newRemaining)
            
            val username = getUsername()
            if (username != null) {
                firebaseDatabase.getReference("users/$username/budgets/$monthYear/remainingBudget")
                    .setValue(newRemaining)
            }
        }
    }

    fun syncBudgetFromFirebase(callback: (Double?) -> Unit) {
        val username = getUsername() ?: return
        val currentMonth = MonthUtils.getCurrentMonthKey()
        
        // Try to get current month budget first
        firebaseDatabase.getReference("users/$username/monthlyBudgets/$currentMonth")
            .get().addOnSuccessListener { snapshot ->
                val amount = snapshot.getValue(Double::class.java)
                if (amount != null) {
                    callback(amount)
                } else {
                    // Fallback to legacy field if set
                    firebaseDatabase.getReference("users/$username/monthlyBudget")
                        .get().addOnSuccessListener { legacySnapshot ->
                            callback(legacySnapshot.getValue(Double::class.java))
                        }.addOnFailureListener { callback(null) }
                }
            }.addOnFailureListener {
                callback(null)
            }
    }
}
