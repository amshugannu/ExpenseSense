package com.amshu.expensesense

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class CardViewModel(private val repository: CardRepository) : ViewModel() {

    private val _cards = MutableLiveData<Pair<List<CardUIModel>, Boolean>>()
    val cards: LiveData<Pair<List<CardUIModel>, Boolean>> = _cards

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    fun loadCards() {
        _isLoading.postValue(true)
        repository.getAllCards { debits, credits, isFromFirebase ->
            val uiModels = mutableListOf<CardUIModel>()
            uiModels.addAll(debits.map { CardUIModel.Debit(it) })
            uiModels.addAll(credits.map { CardUIModel.Credit(it) })
            
            val sortedList = uiModels.sortedBy { it.createdAt }
            
            _cards.postValue(Pair(sortedList, isFromFirebase))
            _isLoading.postValue(false)
        }
    }
}
