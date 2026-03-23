package com.example.es

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class CardViewModel(private val repository: CardRepository) : ViewModel() {

    private val _cards = MutableLiveData<List<CardUIModel>>()
    val cards: LiveData<List<CardUIModel>> = _cards

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    fun loadCards() {
        _isLoading.postValue(true)
        repository.getAllCards { debits, credits ->
            val uiModels = mutableListOf<CardUIModel>()
            uiModels.addAll(debits.map { CardUIModel.Debit(it) })
            uiModels.addAll(credits.map { CardUIModel.Credit(it) })
            
            val sortedList = uiModels.sortedBy { it.createdAt }
            
            _cards.postValue(sortedList)
            _isLoading.postValue(false)
        }
    }
}
