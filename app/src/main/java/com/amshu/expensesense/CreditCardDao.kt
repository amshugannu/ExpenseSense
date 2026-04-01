package com.amshu.expensesense

import androidx.room.*

@Dao
interface CreditCardDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCreditCard(card: CreditCard)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(cards: List<CreditCard>)

    @Query("SELECT * FROM credit_cards ORDER BY orderIndex ASC")
    fun getAllCreditCards(): List<CreditCard>

    @Query("SELECT * FROM credit_cards WHERE cardName = :name")
    fun getCreditCardByName(name: String): CreditCard?

    @Update
    fun updateCreditCard(card: CreditCard)

    @Delete
    fun deleteCreditCard(card: CreditCard)
}
