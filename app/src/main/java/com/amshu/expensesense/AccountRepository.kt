package com.amshu.expensesense

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
            accounts.forEach { 
                android.util.Log.d("ROOM_DEBUG", "Fetched from Room -> Account: ${it.name}, Balance: ${it.balance}")
            }
            callback(accounts)
        }.start()
    }

    fun saveAccount(account: Account, onComplete: () -> Unit = {}) {
        Thread {
            val firebaseAccount = accountDao.getAccountByName(account.name)
            if (account.balance == 0.0 && firebaseAccount != null && firebaseAccount.balance > 0.0) {
                onComplete()
                return@Thread
            }

            // Save to Room
            android.util.Log.d("ROOM_DEBUG", "Updating Room -> Account: ${account.name}, New Balance: ${account.balance}")
            try {
                accountDao.insertAccount(account)
                android.util.Log.d("ROOM_DEBUG", "Room update SUCCESS (Account)")
            } catch (e: Exception) {
                android.util.Log.e("ROOM_DEBUG", "Room update FAILED (Account)", e)
            }

            // Sync to Firebase
            val username = getUsername()
            if (username != null) {
                android.util.Log.d("FIREBASE_DEBUG", "Updating Firebase -> Account: ${account.name}, New Balance: ${account.balance}")
                firebaseDatabase.getReference("users/$username/accounts/${account.name}")
                    .setValue(account)
                    .addOnSuccessListener { android.util.Log.d("FIREBASE_DEBUG", "Firebase update SUCCESS (Account)") }
                    .addOnFailureListener { e -> android.util.Log.e("FIREBASE_DEBUG", "Firebase update FAILED (Account)", e) }

            }
            onComplete()
        }.start()
    }

    fun updateBalance(accountName: String, amount: Double, onComplete: () -> Unit = {}) {
        Thread {
            val account = accountDao.getAccountByName(accountName)
            if (account != null) {
                val newBalance = account.balance + amount
                android.util.Log.d("ROOM_DEBUG", "Updating Room Balance -> Account: $accountName, New Balance: $newBalance")
                try {
                    accountDao.updateBalance(accountName, newBalance)
                    android.util.Log.d("ROOM_DEBUG", "Room balance update SUCCESS")
                } catch (e: Exception) {
                    android.util.Log.e("ROOM_DEBUG", "Room balance update FAILED", e)
                }

                // Sync to Firebase
                val username = getUsername()
                if (username != null) {
                    android.util.Log.d("FIREBASE_DEBUG", "Updating Firebase Balance -> Account: $accountName, New Balance: $newBalance")
                    firebaseDatabase.getReference("users/$username/accounts/$accountName/balance")
                        .setValue(newBalance)
                        .addOnSuccessListener { android.util.Log.d("FIREBASE_DEBUG", "Firebase balance update SUCCESS") }
                        .addOnFailureListener { e -> android.util.Log.e("FIREBASE_DEBUG", "Firebase balance update FAILED", e) }
                }
                onComplete()
            } else {
                android.util.Log.e("ROOM_DEBUG", "Failed to update balance: Account $accountName NOT FOUND in Room")
                onComplete()
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
