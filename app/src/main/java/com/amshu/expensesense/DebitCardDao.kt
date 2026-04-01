package com.amshu.expensesense

import androidx.room.*

@Dao
interface DebitCardDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertDebitCard(card: DebitCard)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(cards: List<DebitCard>)

    @Query("SELECT * FROM debit_cards ORDER BY orderIndex ASC")
    fun getAllDebitCards(): List<DebitCard>

    @Query("SELECT * FROM debit_cards WHERE cardName = :name")
    fun getDebitCardByName(name: String): DebitCard?

    @Update
    fun updateDebitCard(card: DebitCard)

    @Delete
    fun deleteDebitCard(card: DebitCard)
}
