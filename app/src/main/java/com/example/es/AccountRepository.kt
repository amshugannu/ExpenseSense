package com.example.es

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class AccountRepository(private val accountDao: AccountDao) {

    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firebaseDatabase = FirebaseDatabase.getInstance()

    private fun getUsername(): String? {
        return firebaseAuth.currentUser?.email?.substringBefore("@")
    }

    fun getAllAccounts(callback: (List<Account>) -> Unit) {
        Thread {
            val accounts = accountDao.getAllAccounts()
            callback(accounts)
        }.start()
    }

    fun saveAccount(account: Account, onComplete: () -> Unit = {}) {
        Thread {
            // Save to Room
            accountDao.insertAccount(account)

            // Sync to Firebase
            val username = getUsername()
            if (username != null) {
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
                val newBalance = account.balance + amount
                accountDao.updateBalance(accountName, newBalance)

                // Sync to Firebase
                val username = getUsername()
                if (username != null) {
                    firebaseDatabase.getReference("users/$username/accounts/$accountName/balance")
                        .setValue(newBalance)
                }
            }
        }.start()
    }

    fun getAccountByName(name: String, callback: (Account?) -> Unit) {
        Thread {
            val account = accountDao.getAccountByName(name)
            callback(account)
        }.start()
    }

    fun setAccountBalance(accountName: String, exactBalance: Double, onComplete: () -> Unit = {}) {
        Thread {
            val account = accountDao.getAccountByName(accountName)
            if (account != null) {
                accountDao.updateBalance(accountName, exactBalance)

                // Sync to Firebase
                val username = getUsername()
                if (username != null) {
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
