package com.amshu.expensesense

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import android.util.Log

class AccountRepository(private val accountDao: AccountDao) {

    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firebaseDatabase = FirebaseDatabase.getInstance()

    private fun getUsername(): String? {
        return firebaseAuth.currentUser?.email?.substringBefore("@")
    }

    fun getAllAccounts(callback: (List<Account>) -> Unit) {
        Thread {
            val accounts = accountDao.getAllAccounts()
            for (acct in accounts) {
                if (acct != null) {
                    Log.d("SYNC_DEBUG", "Room Account Read → " + acct.name + " Balance: " + acct.balance)
                }
            }
            callback(accounts)
        }.start()
    }

    fun saveAccount(account: Account, onComplete: () -> Unit = {}) {
        Thread {
            val firebaseAccount = accountDao.getAccountByName(account.name)
            if (account.balance == 0.0 && firebaseAccount != null && firebaseAccount.balance > 0.0) {
                Log.d("SYNC_DEBUG", "Prevented overwrite of valid Firebase data")
                onComplete()
                return@Thread
            }

            if (account != null) {
                Log.d("SYNC_DEBUG", "Inserting into Room → " + account.name + " Balance: " + account.balance)
            }
            // Save to Room
            accountDao.insertAccount(account)

            // Sync to Firebase
            val username = getUsername()
            if (username != null) {
                if (account != null) {
                    Log.d("SYNC_DEBUG", "Updating Firebase → " + account.name + " Balance: " + account.balance)
                }
                firebaseDatabase.getReference("users/$username/accounts/${account.name}")
                    .setValue(account)
            }
            onComplete()
        }.start()
    }

    fun updateBalance(accountName: String, amount: Double) {
        Thread {
            val account = accountDao.getAccountByName(accountName)
            if (account != null) {
                Log.d("SYNC_DEBUG", "Room Account Read → " + account.name + " Balance: " + account.balance)
                val newBalance = account.balance + amount
                accountDao.updateBalance(accountName, newBalance)

                // Sync to Firebase
                val username = getUsername()
                if (username != null) {
                    Log.d("SYNC_DEBUG", "Updating Firebase → " + account.name + " Balance: " + newBalance)
                    firebaseDatabase.getReference("users/$username/accounts/$accountName/balance")
                        .setValue(newBalance)
                }
            }
        }.start()
    }

    fun getAccountByName(name: String, callback: (Account?) -> Unit) {
        Thread {
            val account = accountDao.getAccountByName(name)
            if (account != null) {
                Log.d("SYNC_DEBUG", "Room Account Read → " + account.name + " Balance: " + account.balance)
            }
            callback(account)
        }.start()
    }

    fun setAccountBalance(accountName: String, exactBalance: Double, onComplete: () -> Unit = {}) {
        Thread {
            val account = accountDao.getAccountByName(accountName)
            if (account != null) {
                Log.d("SYNC_DEBUG", "Room Account Read → " + account.name + " Balance: " + account.balance)
                accountDao.updateBalance(accountName, exactBalance)

                // Sync to Firebase
                val username = getUsername()
                if (username != null) {
                    Log.d("SYNC_DEBUG", "Updating Firebase → " + account.name + " Balance: " + exactBalance)
                    firebaseDatabase.getReference("users/$username/accounts/$accountName/balance")
                        .setValue(exactBalance)
                }
            }
            onComplete()
        }.start()
    }

    fun deleteAccount(account: Account, onComplete: () -> Unit = {}) {
        Thread {
            try {
                // Remove from Room
                accountDao.deleteAccount(account)

                // Remove from Firebase
                val username = getUsername()
                if (username != null) {
                    firebaseDatabase.getReference("users/$username/accounts/${account.name}")
                        .removeValue()
                }
                onComplete()
            } catch (e: Exception) {
                // Handle potential DB exceptions if needed
                onComplete()
            }
        }.start()
    }
}
