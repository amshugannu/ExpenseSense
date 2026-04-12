package com.amshu.expensesense

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
        val budget: Budget?,
        val creditCard: CreditCard? = null
    )

    fun addExpense(
        transaction: Transaction,
        username: String,
        callback: (Boolean, String?) -> Unit
    ) {
        Thread {
            try {
                android.util.Log.d("EXPENSE_DEBUG", "------ ADD EXPENSE START ------")
                android.util.Log.d("EXPENSE_DEBUG", "Expense Amount: ${transaction.amount}")
                android.util.Log.d("EXPENSE_DEBUG", "Card/Account: ${transaction.referenceId ?: transaction.accountName}")

                val mutationResult = applyExpenseTransaction(transaction, isDelete = false)
                syncExpenseToFirebase(transaction, username)
                syncAccountToFirebase(username, mutationResult.account)
                syncBudgetToFirebase(username, mutationResult.budget)
                syncCreditCardToFirebase(username, mutationResult.creditCard)
                
                android.util.Log.d("EXPENSE_DEBUG", "------ ADD EXPENSE END ------")
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
                syncCreditCardToFirebase(username, mutationResult.creditCard)
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

    fun updateExpense(
        oldTransaction: Transaction,
        newTransaction: Transaction,
        paymentMethod: String,
        referenceId: String?,
        username: String,
        callback: (Boolean, String?) -> Unit
    ) {
        val normalizedNewTransaction = newTransaction.copy(
            paymentMethod = paymentMethod,
            referenceId = referenceId ?: newTransaction.referenceId
        )
        Thread {
            try {
                var delMutation: ExpenseMutationResult? = null
                var addMutation: ExpenseMutationResult? = null
                database.runInTransaction {
                    delMutation = applyExpenseTransaction(oldTransaction, isDelete = true)
                    addMutation = applyExpenseTransaction(normalizedNewTransaction, isDelete = false)
                }
                
                removeExpenseFromFirebase(oldTransaction, username)
                syncExpenseToFirebase(normalizedNewTransaction, username)
                
                // Sync the old account & budget to Firebase (only if they are different from the new ones)
                if (delMutation?.account != null && delMutation?.account?.name != addMutation?.account?.name) {
                    syncAccountToFirebase(username, delMutation?.account)
                }
                if (delMutation?.budget != null && delMutation?.budget?.monthYear != addMutation?.budget?.monthYear) {
                    syncBudgetToFirebase(username, delMutation?.budget)
                }
                if (delMutation?.creditCard != null && delMutation?.creditCard?.cardName != addMutation?.creditCard?.cardName) {
                    syncCreditCardToFirebase(username, delMutation?.creditCard)
                }

                // Sync the newly impacted account & budget to Firebase
                syncAccountToFirebase(username, addMutation?.account)
                syncBudgetToFirebase(username, addMutation?.budget)
                syncCreditCardToFirebase(username, addMutation?.creditCard)
                
            } catch (e: Exception) {
                callback(false, e.message)
                return@Thread
            }
            callback(true, null)
        }.start()
    }

    private fun applyExpenseTransaction(
        transaction: Transaction,
        isDelete: Boolean
    ): ExpenseMutationResult {
        var updatedAccount: Account? = null
        var updatedBudget: Budget? = null
        var updatedCreditCard: CreditCard? = null

        database.runInTransaction {
            updatedAccount = updateLinkedAccount(transaction, isDelete)
            updatedCreditCard = updateLinkedCreditCard(transaction, isDelete)
            updatedBudget = updateBudget(transaction, isDelete)

            if (isDelete) {
                transactionDao.deleteTransaction(transaction)
            } else {
                transactionDao.insertTransaction(transaction)
            }
        }

        return ExpenseMutationResult(
            account = updatedAccount,
            budget = updatedBudget,
            creditCard = updatedCreditCard
        )
    }

    private fun updateLinkedAccount(transaction: Transaction, isDelete: Boolean): Account? {
        val account = resolveAccount(transaction) ?: return null
        
        
        val isIncome = transaction.transactionType == Transaction.TYPE_INCOME
        val isCredit = account.type.equals("Credit", ignoreCase = true)
        
        android.util.Log.d("EXPENSE_DEBUG", "Account: ${account.name}")
        android.util.Log.d("EXPENSE_DEBUG", "Balance BEFORE deduction: ${account.balance}")
        
        val updatedBalance = when {
            isIncome -> {
                if (isCredit) {
                    if (isDelete) account.balance + transaction.amount else account.balance - transaction.amount
                } else {
                    if (isDelete) account.balance - transaction.amount else account.balance + transaction.amount
                }
            }
            else -> { // Expense
                if (isCredit) {
                    if (isDelete) account.balance - transaction.amount else account.balance + transaction.amount
                } else {
                    if (isDelete) account.balance + transaction.amount else account.balance - transaction.amount
                }
            }
        }

        android.util.Log.d("EXPENSE_DEBUG", "Balance AFTER deduction: $updatedBalance")

        val updatedAccount = account.copy(balance = updatedBalance)
        android.util.Log.d("ROOM_DEBUG", "Updating Room -> Account: ${account.name}, New Balance: $updatedBalance")
        try {
            accountDao.updateAccount(updatedAccount)
            android.util.Log.d("ROOM_DEBUG", "Room update SUCCESS (Account)")
        } catch (e: Exception) {
            android.util.Log.e("ROOM_DEBUG", "Room update FAILED (Account)", e)
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
        return resolved
    }

    private fun updateLinkedCreditCard(transaction: Transaction, isDelete: Boolean): CreditCard? {
        if (transaction.paymentMethod != "Credit Card") return null

        val cardName = transaction.referenceId ?: transaction.accountName
        val card = creditCardDao.getAllCreditCards().find { it.cardName == cardName } ?: return null
        val currentAvailableLimit = card.availableLimit ?: 0.0
        val isIncome = transaction.transactionType == Transaction.TYPE_INCOME
        android.util.Log.d("EXPENSE_DEBUG", "Credit Card: ${card.cardName}")
        android.util.Log.d("EXPENSE_DEBUG", "Available Limit BEFORE deduction: $currentAvailableLimit")

        val updatedAvailableLimit = if (isIncome) {
            if (isDelete) currentAvailableLimit - transaction.amount else currentAvailableLimit + transaction.amount
        } else {
            if (isDelete) {
                currentAvailableLimit + transaction.amount
            } else {
                val nextLimit = currentAvailableLimit - transaction.amount
                if (nextLimit < 0) {
                    android.util.Log.e("EXPENSE_DEBUG", "Credit limit exceeded")
                    throw Exception("Credit limit exceeded")
                }
                nextLimit
            }
        }
        android.util.Log.d("EXPENSE_DEBUG", "Available Limit AFTER deduction: $updatedAvailableLimit")

        val updatedCard = card.copy(availableLimit = updatedAvailableLimit)
        android.util.Log.d("ROOM_DEBUG", "Updating Room -> Credit Card: ${card.cardName}, New Limit: $updatedAvailableLimit")
        try {
            creditCardDao.updateCreditCard(updatedCard)
            android.util.Log.d("ROOM_DEBUG", "Room update SUCCESS (Credit Card)")
            return updatedCard
        } catch (e: Exception) {
            android.util.Log.e("ROOM_DEBUG", "Room update FAILED (Credit Card)", e)
            return null
        }
    }

    private fun updateBudget(transaction: Transaction, isDelete: Boolean): Budget? {
        if (transaction.transactionType == Transaction.TYPE_INCOME) return null
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
            "type" to transaction.transactionType,
            "account" to (transaction.referenceId ?: transaction.accountName),
            "paymentMethod" to transaction.paymentMethod,
            "note" to transaction.note
        )

        FirebaseDatabase.getInstance()
            .getReference("users/$username/expenses/$category/$firebaseId")
            .setValue(firebaseExpenseData)
            .addOnSuccessListener {
                android.util.Log.d("EXPENSE_DEBUG", "Expense saved to Firebase successfully")
            }
            .addOnFailureListener { e ->
                android.util.Log.e("EXPENSE_DEBUG", "Failed to save expense to Firebase", e)
            }
    }

    private fun removeExpenseFromFirebase(transaction: Transaction, username: String) {
        val firebaseId = transaction.firebaseId ?: return
        FirebaseDatabase.getInstance()
            .getReference("users/$username/expenses/${transaction.category}/$firebaseId")
            .removeValue()
    }

    private fun syncAccountToFirebase(username: String, account: Account?) {
        if (account == null) return
        android.util.Log.d("EXPENSE_DEBUG", "Updating account ${account.name} balance in Firebase to: ${account.balance}")
        FirebaseDatabase.getInstance()
            .getReference("users/$username/accounts/${account.name}/balance")
            .setValue(account.balance)
            .addOnSuccessListener {
                android.util.Log.d("EXPENSE_DEBUG", "Account balance updated successfully in Firebase")
            }
            .addOnFailureListener { e ->
                android.util.Log.e("EXPENSE_DEBUG", "Failed to update account balance in Firebase", e)
            }
    }

    private fun syncBudgetToFirebase(username: String, budget: Budget?) {
        if (budget == null) return
        FirebaseDatabase.getInstance()
            .getReference("users/$username/budgets/${budget.monthYear}/remainingBudget")
            .setValue(budget.remainingBudget)
    }

    private fun syncCreditCardToFirebase(username: String, creditCard: CreditCard?) {
        if (creditCard == null) return
        
        // CardRepository natively replaces dots with underscores for the card namespace, so we must mirror that here!
        val sanitizedUsername = username.replace(".", "_")
        val documentId = creditCard.documentId.takeIf { it.isNotEmpty() } ?: creditCard.cardName
        val newBalance = creditCard.availableLimit
        android.util.Log.d("FIREBASE_DEBUG", "Updating Firebase -> DocId: $documentId, New Balance: $newBalance (User: $sanitizedUsername)")

        FirebaseDatabase.getInstance()
            .getReference("users/$sanitizedUsername/cards/credit_cards/$documentId/availableLimit")
            .setValue(newBalance)
            .addOnSuccessListener {
                android.util.Log.d("FIREBASE_DEBUG", "Firebase balance update SUCCESS")
            }
            .addOnFailureListener { e ->
                android.util.Log.e("FIREBASE_DEBUG", "Firebase balance update FAILED", e)
            }
    }
}
