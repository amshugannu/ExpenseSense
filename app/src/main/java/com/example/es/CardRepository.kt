package com.example.es

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class CardRepository(private val cardDao: CardDao, private val accountDao: AccountDao) {

    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firebaseDatabase = FirebaseDatabase.getInstance()

    private fun getUsername(): String? {
        return firebaseAuth.currentUser?.email?.substringBefore("@")
    }

    fun getAllCards(callback: (List<Card>) -> Unit) {
        Thread {
            val cards = cardDao.getAllCards()
            callback(cards)
        }.start()
    }
    
    // Fixed version
    fun getAllCardsList(callback: (List<Card>) -> Unit) {
        Thread {
            val cards = cardDao.getAllCards()
            callback(cards)
        }.start()
    }

    fun saveCard(card: Card, callback: (Boolean) -> Unit) {
        Thread {
            try {
                if (card.id == 0) {
                    cardDao.insertCard(card)
                } else {
                    cardDao.updateCard(card)
                }

                // Sync to Firebase
                val username = getUsername()
                if (username != null) {
                    firebaseDatabase.getReference("users/$username/cards/${card.cardName}")
                        .setValue(card)
                }
                callback(true)
            } catch (e: Exception) {
                callback(false)
            }
        }.start()
    }

    fun deleteCard(card: Card, callback: (Boolean) -> Unit) {
        Thread {
            try {
                cardDao.deleteCard(card)
                val username = getUsername()
                if (username != null) {
                    firebaseDatabase.getReference("users/$username/cards/${card.cardName}")
                        .removeValue()
                }
                callback(true)
            } catch (e: Exception) {
                callback(false)
            }
        }.start()
    }

    fun getCardById(id: Int, callback: (Card?) -> Unit) {
        Thread {
            val card = cardDao.getCardById(id)
            callback(card)
        }.start()
    }

    fun checkAccountExists(accountName: String, callback: (Boolean) -> Unit) {
        Thread {
            val account = accountDao.getAccountByName(accountName)
            callback(account != null)
        }.start()
    }
}
