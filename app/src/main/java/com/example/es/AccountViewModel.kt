package com.example.es

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class AccountViewModel(private val repository: AccountRepository) : ViewModel() {

    private val _accounts = MutableLiveData<List<Account>>()
    val accounts: LiveData<List<Account>> = _accounts

    fun loadAccounts() {
        repository.getAllAccounts { list ->
            _accounts.postValue(list)
        }
    }

    fun addAccount(name: String, type: String?, balance: Double) {
        val account = Account(name, type, balance)
        repository.saveAccount(account)
        loadAccounts()
    }

    fun updateBalance(accountName: String, amount: Double) {
        repository.updateBalance(accountName, amount)
        loadAccounts()
    }

    fun deleteAccount(account: Account) {
        repository.deleteAccount(account) {
            loadAccounts()
        }
    }
}
