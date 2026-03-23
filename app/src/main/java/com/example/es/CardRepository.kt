package com.example.es

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class CardRepository(
    private val debitCardDao: DebitCardDao,
    private val creditCardDao: CreditCardDao,
    private val accountDao: AccountDao,
    private val legacyCardDao: CardDao // For migration
) {

    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firebaseDatabase = FirebaseDatabase.getInstance()

    private fun getUsername(): String? {
        return firebaseAuth.currentUser?.email?.substringBefore("@")?.replace(".", "_")
    }

    // --- DEBIT CARD OPS ---

    fun saveDebitCard(card: DebitCard, callback: (Boolean) -> Unit) {
        Thread {
            try {
                if (card.id == 0) debitCardDao.insertDebitCard(card)
                else debitCardDao.updateDebitCard(card)

                val username = getUsername()
                if (username != null) {
                    val ref = firebaseDatabase.getReference("users/$username/cards/debit_cards/${card.cardName}")
                    ref.setValue(card)
                }
                callback(true)
            } catch (e: Exception) {
                callback(false)
            }
        }.start()
    }

    fun deleteDebitCard(card: DebitCard, callback: (Boolean) -> Unit) {
        Thread {
            try {
                debitCardDao.deleteDebitCard(card)
                val username = getUsername()
                if (username != null) {
                    val ref = firebaseDatabase.getReference("users/$username/cards/debit_cards/${card.cardName}")
                    ref.removeValue()
                }
                callback(true)
            } catch (e: Exception) {
                callback(false)
            }
        }.start()
    }

    // --- CREDIT CARD OPS ---

    fun saveCreditCard(card: CreditCard, callback: (Boolean) -> Unit) {
        Thread {
            try {
                if (card.id == 0) creditCardDao.insertCreditCard(card)
                else creditCardDao.updateCreditCard(card)

                val username = getUsername()
                if (username != null) {
                    val ref = firebaseDatabase.getReference("users/$username/cards/credit_cards/${card.cardName}")
                    ref.setValue(card)
                }
                callback(true)
            } catch (e: Exception) {
                callback(false)
            }
        }.start()
    }

    fun deleteCreditCard(card: CreditCard, callback: (Boolean) -> Unit) {
        Thread {
            try {
                creditCardDao.deleteCreditCard(card)
                val username = getUsername()
                if (username != null) {
                    val ref = firebaseDatabase.getReference("users/$username/cards/credit_cards/${card.cardName}")
                    ref.removeValue()
                }
                callback(true)
            } catch (e: Exception) {
                callback(false)
            }
        }.start()
    }

    // --- FETCH & SYNC OPS ---

    fun getAllCards(callback: (List<DebitCard>, List<CreditCard>) -> Unit) {
        val username = getUsername() ?: ""
        
        // 1. Initial Load from Room (Instant UI)
        Thread {
            val localDebits = debitCardDao.getAllDebitCards()
            val localCredits = creditCardDao.getAllCreditCards()
            callback(localDebits, localCredits)
            
            // 2. Sync from Firebase in Background
            if (username.isNotEmpty()) {
                val ref = firebaseDatabase.getReference("users/$username/cards")
                
                ref.addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                    override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                        Thread {
                            var hasChanges = false
                            
                            // Process Debit Cards
                            val debitSnap = snapshot.child("debit_cards")
                            for (ds in debitSnap.children) {
                                val card = ds.getValue(DebitCard::class.java)
                                if (card != null) {
                                    val existing = debitCardDao.getAllDebitCards().find { it.cardName == card.cardName }
                                    if (existing == null) {
                                        debitCardDao.insertDebitCard(card)
                                        hasChanges = true
                                    }
                                }
                            }
                            
                            // Process Credit Cards
                            val creditSnap = snapshot.child("credit_cards")
                            for (cs in creditSnap.children) {
                                val card = cs.getValue(CreditCard::class.java)
                                if (card != null) {
                                    val existing = creditCardDao.getAllCreditCards().find { it.cardName == card.cardName }
                                    if (existing == null) {
                                        creditCardDao.insertCreditCard(card)
                                        hasChanges = true
                                    }
                                }
                            }
                            
                            if (hasChanges) {
                                val updatedDebits = debitCardDao.getAllDebitCards()
                                val updatedCredits = creditCardDao.getAllCreditCards()
                                callback(updatedDebits, updatedCredits)
                            }
                        }.start()
                    }
                    override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                        // Firebase fetch cancelled or failed
                    }
                })
            }
        }.start()
    }

    fun cleanupRootCards() {
        // STRICT RULE: No global "cards" root allowed
        firebaseDatabase.getReference("cards").removeValue()
    }

    // --- MIGRATION & CLEANUP ---

    fun migrateLegacyData(username: String, callback: (Boolean) -> Unit) {
        Thread {
            try {
                val userId = firebaseAuth.currentUser?.uid
                
                // Fetch from legacy Room table
                val legacyCards = legacyCardDao.getAllCards()
                if (legacyCards.isEmpty()) {
                    // No legacy data in Room, but maybe in Firebase?
                    // For now, if Room is empty, we assume migration is done.
                    return@Thread callback(false)
                }
                
                legacyCards.forEach { legacy ->
                    if (legacy.cardType == "Credit") {
                        val credit = CreditCard(
                            cardHolderName = legacy.cardHolderName,
                            cardNumber = legacy.cardNumber,
                            cardName = legacy.cardName,
                            bankName = legacy.cardName.split(" ").first(),
                            last4Digits = legacy.cardNumber.takeLast(4),
                            totalLimit = legacy.creditLimit ?: 0.0,
                            availableLimit = legacy.availableLimit ?: 0.0
                        )
                        saveCreditCard(credit) {}
                    } else {
                        val debit = DebitCard(
                            cardHolderName = legacy.cardHolderName,
                            cardNumber = legacy.cardNumber,
                            cardName = legacy.cardName,
                            bankName = legacy.cardName.split(" ").first(),
                            last4Digits = legacy.cardNumber.takeLast(4),
                            linkedBankAccountId = legacy.accountName ?: ""
                        )
                        saveDebitCard(debit) {}
                    }
                }
                
                // Cleanup old Firebase UID-based node if it exists
                if (userId != null && userId != username) {
                    val legacyRef = firebaseDatabase.getReference("users/$userId/cards")
                    legacyRef.removeValue()
                }

                // Cleanup Room legacy data so we don't migrate again
                legacyCardDao.deleteAllCards()
                
                callback(true)
            } catch (e: Exception) {
                callback(false)
            }
        }.start()
    }

    fun getDebitCardById(id: Int, callback: (DebitCard?) -> Unit) {
        Thread {
            val card = debitCardDao.getDebitCardById(id)
            callback(card)
        }.start()
    }

    fun getCreditCardById(id: Int, callback: (CreditCard?) -> Unit) {
        Thread {
            val card = creditCardDao.getCreditCardById(id)
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
