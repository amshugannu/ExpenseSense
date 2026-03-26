package com.amshu.expensesense

import androidx.room.*

@Dao
interface AccountDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAccount(account: Account)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(accounts: List<Account>)

    @Query("SELECT * FROM accounts")
    fun getAllAccounts(): List<Account>

    @Query("SELECT * FROM accounts WHERE name = :name")
    fun getAccountByName(name: String): Account?

    @Query("UPDATE accounts SET balance = :newBalance WHERE name = :name")
    fun updateBalance(name: String, newBalance: Double)

    @Update
    fun updateAccount(account: Account)

    @Delete
    fun deleteAccount(account: Account)
}
