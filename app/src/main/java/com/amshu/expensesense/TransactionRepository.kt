package com.amshu.expensesense

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TransactionRepository(private val transactionDao: TransactionDao) {

    suspend fun getTopSpending(startTime: Long, endTime: Long, limit: Int = 5): List<Transaction> =
        withContext(Dispatchers.IO) {
            transactionDao.getTopSpending(startTime, endTime, limit)
        }

    suspend fun getTransactionsInRange(startTime: Long, endTime: Long): List<Transaction> =
        withContext(Dispatchers.IO) {
            val transactions = transactionDao.getTransactionsInRange(startTime, endTime)
            Log.d("DB_DEBUG", "Fetched transactions count: ${transactions.size}")
            transactions.forEach {
                Log.d("DB_DEBUG", "TX -> ${it.title}, ${it.amount}, ${it.timestamp}, ${it.transactionType}")
            }
            transactions
        }

    suspend fun getFirstTransactionTimestamp(): Long? =
        withContext(Dispatchers.IO) {
            transactionDao.getFirstTransactionTimestamp()
        }
}
