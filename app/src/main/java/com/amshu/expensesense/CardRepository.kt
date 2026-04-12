package com.amshu.expensesense

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
                android.util.Log.d("ROOM_DEBUG", "Updating Room -> Debit Card: ${card.cardName}")
                try {
                    debitCardDao.insertDebitCard(card) // insert with REPLACE handles both new and existing
                    android.util.Log.d("ROOM_DEBUG", "Room update SUCCESS (Debit Card)")
                } catch (e: Exception) {
                    android.util.Log.e("ROOM_DEBUG", "Room update FAILED (Debit Card)", e)
                }

                val username = getUsername()
                if (username != null) {
                    android.util.Log.d("FIREBASE_DEBUG", "Updating Firebase -> Debit Card: ${card.cardName}")
                    val ref = firebaseDatabase.getReference("users/$username/cards/debit_cards/${card.cardName}")
                    ref.setValue(card)
                        .addOnSuccessListener { android.util.Log.d("FIREBASE_DEBUG", "Firebase update SUCCESS (Debit Card)") }
                        .addOnFailureListener { e -> android.util.Log.e("FIREBASE_DEBUG", "Firebase update FAILED (Debit Card)", e) }
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
                android.util.Log.d("ROOM_DEBUG", "Updating Room -> Credit Card: ${card.cardName}, Available Limit: ${card.availableLimit}")
                try {
                    creditCardDao.insertCreditCard(card) // insert with REPLACE handles both new and existing
                    android.util.Log.d("ROOM_DEBUG", "Room update SUCCESS (Credit Card)")
                } catch (e: Exception) {
                    android.util.Log.e("ROOM_DEBUG", "Room update FAILED (Credit Card)", e)
                }

                val username = getUsername()
                if (username != null) {
                    val docId = card.documentId.takeIf { it.isNotEmpty() } ?: card.cardName
                    android.util.Log.d("FIREBASE_DEBUG", "Updating Firebase -> Credit Card DocId: $docId, New Balance: ${card.availableLimit}")
                    val ref = firebaseDatabase.getReference("users/$username/cards/credit_cards/$docId")
                    ref.setValue(card)
                        .addOnSuccessListener { android.util.Log.d("FIREBASE_DEBUG", "Firebase update SUCCESS (Credit Card)") }
                        .addOnFailureListener { e -> android.util.Log.e("FIREBASE_DEBUG", "Firebase update FAILED (Credit Card)", e) }
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
                    val docId = card.documentId.takeIf { it.isNotEmpty() } ?: card.cardName
                    val ref = firebaseDatabase.getReference("users/$username/cards/credit_cards/$docId")
                    ref.removeValue()
                }
                callback(true)
            } catch (e: Exception) {
                callback(false)
            }
        }.start()
    }

    // --- FETCH & SYNC OPS ---

    fun getAllCards(callback: (List<DebitCard>, List<CreditCard>, Boolean) -> Unit) {
        val username = getUsername() ?: ""
        
        // 1. Initial Load from Room (Instant UI)
        Thread {
            val localDebits = debitCardDao.getAllDebitCards()
            val localCredits = creditCardDao.getAllCreditCards()
            localDebits.forEach { android.util.Log.d("ROOM_DEBUG", "Fetched from Room -> CardId: ${it.cardName}, Linked Account: ${it.linkedBankAccountId}") }
            localCredits.forEach { android.util.Log.d("ROOM_DEBUG", "Fetched from Room -> CardId: ${it.cardName}, Balance: ${it.availableLimit}") }
            callback(localDebits, localCredits, false)
            
            // 2. Sync from Firebase in Background
            if (username.isNotEmpty()) {
                val ref = firebaseDatabase.getReference("users/$username/cards")
                
                ref.addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                    override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                        android.util.Log.d("FIREBASE_DEBUG", "------ FIREBASE FETCH START ------")
                        android.util.Log.d("SNAPSHOT_DEBUG", "Snapshot triggered")
                        
                        Thread {
                            var hasChanges = false
                            val localDebits = debitCardDao.getAllDebitCards().associateBy { it.cardName }
                            val localCredits = creditCardDao.getAllCreditCards().associateBy { it.cardName }
                            
                            // Process Debit Cards
                            val debitSnap = snapshot.child("debit_cards")
                            for (ds in debitSnap.children) {
                                val card = ds.getValue(DebitCard::class.java)
                                if (card != null) {
                                    android.util.Log.d("FIREBASE_DEBUG", "Fetched from Firebase -> CardId: ${card.cardName}, Linked Account: ${card.linkedBankAccountId}")
                                    android.util.Log.d("SNAPSHOT_DEBUG", "DocId (Debit): ${card.cardName}, Details: $card")
                                    if (!localDebits.containsKey(card.cardName)) {
                                        android.util.Log.d("ROOM_DEBUG", "Updating Room from Firebase -> Debit Card: ${card.cardName}")
                                        debitCardDao.insertDebitCard(card)
                                        hasChanges = true
                                    }
                                }
                            }
                            
                            // Process Credit Cards
                            val creditSnap = snapshot.child("credit_cards")
                            for (cs in creditSnap.children) {
                                val documentId = cs.key ?: ""
                                val parsedCard = cs.getValue(CreditCard::class.java)
                                if (parsedCard != null) {
                                    val card = parsedCard.copy(documentId = documentId)
                                    android.util.Log.d("FIREBASE_DEBUG", "DocId: $documentId, Name: ${card.cardName}, Balance: ${card.availableLimit}")
                                    
                                    if (!localCredits.containsKey(card.cardName)) {
                                        android.util.Log.d("ROOM_DEBUG", "Updating Room from Firebase -> Credit Card: ${card.cardName}, Balance: ${card.availableLimit}")
                                        creditCardDao.insertCreditCard(card)
                                        hasChanges = true
                                    }
                                }
                            }
                            
                            if (hasChanges) {
                                callback(debitCardDao.getAllDebitCards(), creditCardDao.getAllCreditCards(), true)
                            }
                            android.util.Log.d("FIREBASE_DEBUG", "------ FIREBASE FETCH END ------")
                        }.start()
                    }
                    override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                        android.util.Log.e("FIREBASE_DEBUG", "Firebase fetch FAILED", error.toException())
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

    fun getDebitCardByName(name: String, callback: (DebitCard?) -> Unit) {
        Thread {
            val card = debitCardDao.getDebitCardByName(name)
            callback(card)
        }.start()
    }

    fun getCreditCardByName(name: String, callback: (CreditCard?) -> Unit) {
        Thread {
            val card = creditCardDao.getCreditCardByName(name)
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
