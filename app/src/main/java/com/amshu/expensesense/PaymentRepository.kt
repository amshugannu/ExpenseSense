package com.amshu.expensesense

import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*

class PaymentRepository(
    private val database: AppDatabase,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val debitCardDao: DebitCardDao,
    private val creditCardDao: CreditCardDao,
    private val budgetDao: BudgetDao
) {

    private data class ExpenseMutationResult(
        val account: Account?,
        val budget: Budget?
    )

    fun addExpense(
        transaction: Transaction,
        username: String,
        callback: (Boolean, String?) -> Unit
    ) {
        Thread {
            try {
                val mutationResult = applyExpenseTransaction(transaction, isDelete = false)
                syncExpenseToFirebase(transaction, username)
                syncAccountToFirebase(username, mutationResult.account)
                syncBudgetToFirebase(username, mutationResult.budget)
                callback(true, null)
            } catch (e: Exception) {
                callback(false, e.message)
            }
        }.start()
    }

    fun deleteExpense(
        transaction: Transaction,
        username: String,
        callback: (Boolean, String?) -> Unit
    ) {
        Thread {
            try {
                val mutationResult = applyExpenseTransaction(transaction, isDelete = true)
                removeExpenseFromFirebase(transaction, username)
                syncAccountToFirebase(username, mutationResult.account)
                syncBudgetToFirebase(username, mutationResult.budget)
                callback(true, null)
            } catch (e: Exception) {
                callback(false, e.message)
            }
        }.start()
    }

    fun saveExpense(
        transaction: Transaction,
        paymentMethod: String,
        referenceId: String?,
        username: String,
        callback: (Boolean, String?) -> Unit
    ) {
        val normalizedTransaction = transaction.copy(
            paymentMethod = paymentMethod,
            referenceId = referenceId ?: transaction.referenceId
        )
        addExpense(normalizedTransaction, username, callback)
    }

    private fun applyExpenseTransaction(
        transaction: Transaction,
        isDelete: Boolean
    ): ExpenseMutationResult {
        var updatedAccount: Account? = null
        var updatedBudget: Budget? = null

        database.runInTransaction {
            updatedAccount = updateLinkedAccount(transaction, isDelete)
            updateLinkedCreditCard(transaction, isDelete)
            updatedBudget = updateBudget(transaction, isDelete)

            if (isDelete) {
                transactionDao.deleteTransaction(transaction)
            } else {
                transactionDao.insertTransaction(transaction)
            }
        }

        return ExpenseMutationResult(
            account = updatedAccount,
            budget = updatedBudget
        )
    }

    private fun updateLinkedAccount(transaction: Transaction, isDelete: Boolean): Account? {
        val account = resolveAccount(transaction) ?: return null
        
        if (account != null) {
            if (isDelete) {
                if (BuildConfig.DEBUG) { Log.d("SYNC_DEBUG", "Before Delete Reverse → " + account.name + " Balance: " + account.balance) }
            } else {
                if (BuildConfig.DEBUG) { Log.d("SYNC_DEBUG", "Before Update → " + account.name + " Balance: " + account.balance) }
            }
        }
        
        val updatedBalance = when {
            account.type.equals("Credit", ignoreCase = true) && isDelete -> account.balance - transaction.amount
            account.type.equals("Credit", ignoreCase = true) -> account.balance + transaction.amount
            isDelete -> account.balance + transaction.amount
            else -> account.balance - transaction.amount
        }

        val updatedAccount = account.copy(balance = updatedBalance)
        accountDao.updateAccount(updatedAccount)

        if (updatedAccount != null) {
            if (isDelete) {
                if (BuildConfig.DEBUG) { Log.d("SYNC_DEBUG", "After Delete Reverse → " + updatedAccount.name + " Balance: " + updatedAccount.balance) }
            } else {
                if (BuildConfig.DEBUG) { Log.d("SYNC_DEBUG", "After Update → " + updatedAccount.name + " Balance: " + updatedAccount.balance) }
            }
        }

        return updatedAccount
    }

    private fun resolveAccount(transaction: Transaction): Account? {
        val resolved = when (transaction.paymentMethod) {
            "Cash" -> accountDao.getAccountByName("Cash")
            "UPI" -> {
                val accountName = transaction.referenceId ?: transaction.accountName
                accountName.takeIf { it.isNotBlank() }?.let(accountDao::getAccountByName)
            }
            "Debit Card" -> {
                val cardName = transaction.referenceId ?: transaction.accountName
                val card = debitCardDao.getAllDebitCards().find { it.cardName == cardName }
                    ?: return null
                val linkedAccountName = card.linkedBankAccountId ?: return null
                accountDao.getAccountByName(linkedAccountName)
            }
            "Credit Card" -> {
                val accountName = transaction.referenceId ?: transaction.accountName
                accountName.takeIf { it.isNotBlank() }?.let(accountDao::getAccountByName)
            }
            else -> {
                val accountName = transaction.referenceId ?: transaction.accountName
                accountName.takeIf { it.isNotBlank() }?.let(accountDao::getAccountByName)
            }
        }
        if (resolved != null) {
            if (BuildConfig.DEBUG) { Log.d("SYNC_DEBUG", "Room Account Read → " + resolved.name + " Balance: " + resolved.balance) }
        }
        return resolved
    }

    private fun updateLinkedCreditCard(transaction: Transaction, isDelete: Boolean) {
        if (transaction.paymentMethod != "Credit Card") return

        val cardName = transaction.referenceId ?: transaction.accountName
        val card = creditCardDao.getAllCreditCards().find { it.cardName == cardName } ?: return
        val currentAvailableLimit = card.availableLimit ?: 0.0
        val updatedAvailableLimit = if (isDelete) {
            currentAvailableLimit + transaction.amount
        } else {
            val nextLimit = currentAvailableLimit - transaction.amount
            if (nextLimit < 0) {
                throw Exception("Credit limit exceeded")
            }
            nextLimit
        }

        creditCardDao.updateCreditCard(card.copy(availableLimit = updatedAvailableLimit))
    }

    private fun updateBudget(transaction: Transaction, isDelete: Boolean): Budget? {
        val monthYear = SimpleDateFormat("MM-yyyy", Locale.getDefault())
            .format(Date(transaction.timestamp))
        val currentBudget = budgetDao.getBudget(monthYear) ?: return null
        val currentSpent = (currentBudget.totalBudget - currentBudget.remainingBudget).coerceAtLeast(0.0)
        val updatedSpent = if (isDelete) {
            (currentSpent - transaction.amount).coerceAtLeast(0.0)
        } else {
            currentSpent + transaction.amount
        }
        val updatedBudget = currentBudget.copy(
            remainingBudget = currentBudget.totalBudget - updatedSpent
        )
        budgetDao.updateBudget(updatedBudget)

        return updatedBudget
    }

    private fun syncExpenseToFirebase(transaction: Transaction, username: String) {
        val firebaseId = transaction.firebaseId ?: return
        val category = transaction.category
        
        val firebaseExpenseData = mapOf(
            "timestamp" to transaction.timestamp,
            "amount" to transaction.amount,
            "account" to (transaction.referenceId ?: transaction.accountName),
            "paymentMethod" to transaction.paymentMethod
        )

        FirebaseDatabase.getInstance()
            .getReference("users/$username/expenses/$category/$firebaseId")
            .setValue(firebaseExpenseData)
    }

    private fun removeExpenseFromFirebase(transaction: Transaction, username: String) {
        val firebaseId = transaction.firebaseId ?: return
        FirebaseDatabase.getInstance()
            .getReference("users/$username/expenses/${transaction.category}/$firebaseId")
            .removeValue()
    }

    private fun syncAccountToFirebase(username: String, account: Account?) {
        if (account == null) return
        if (BuildConfig.DEBUG) { Log.d("SYNC_DEBUG", "Updating Firebase → " + account.name + " Balance: " + account.balance) }
        FirebaseDatabase.getInstance()
            .getReference("users/$username/accounts/${account.name}/balance")
            .setValue(account.balance)
    }

    private fun syncBudgetToFirebase(username: String, budget: Budget?) {
        if (budget == null) return
        FirebaseDatabase.getInstance()
            .getReference("users/$username/budgets/${budget.monthYear}/remainingBudget")
            .setValue(budget.remainingBudget)
    }
}
