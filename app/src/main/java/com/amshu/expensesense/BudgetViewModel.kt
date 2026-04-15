package com.amshu.expensesense

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class BudgetViewModel(private val repository: BudgetRepository) : ViewModel() {

    private val _budget = MutableLiveData<Budget?>()
    val budget: LiveData<Budget?> = _budget

    fun loadCurrentMonthBudget() {
        val currentMonthYear = MonthUtils.getCurrentMonthKey()
        viewModelScope.launch {
            val currentBudget = repository.getBudget(currentMonthYear)
            if (currentBudget != null) {
                _budget.postValue(currentBudget)
            } else {
                // If local is missing, try syncing from Firebase
                repository.syncBudgetFromFirebase { firebaseAmount ->
                    if (firebaseAmount != null && firebaseAmount > 0) {
                        setMonthlyBudget(firebaseAmount)
                    } else {
                        _budget.postValue(null)
                    }
                }
            }
        }
    }

    fun setMonthlyBudget(amount: Double) {
        val currentMonthYear = MonthUtils.getCurrentMonthKey()
        viewModelScope.launch {
            repository.setBudget(amount, currentMonthYear)
            
            // Refresh after short delay
            delay(800)
            val updated = repository.getBudget(currentMonthYear)
            _budget.postValue(updated)
        }
    }
}
