package com.amshu.expensesense

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TransactionDao {

    @Insert
    fun insertTransaction(transaction: Transaction)

    @Insert
    fun insertAll(transactions: List<Transaction>)

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): List<Transaction>

    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY amount DESC LIMIT :limit")
    fun getTopSpending(startTime: Long, endTime: Long, limit: Int): List<Transaction>

    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :startTime AND :endTime")
    fun getTransactionsInRange(startTime: Long, endTime: Long): List<Transaction>

    @Query("SELECT MIN(timestamp) FROM transactions")
    fun getFirstTransactionTimestamp(): Long?

    @Delete
    fun deleteTransaction(transaction: Transaction)
}
