package com.example.es

import androidx.room.*

@Dao
interface CardDao {
    @Insert
    fun insertCard(card: Card)

    @Query("SELECT * FROM cards ORDER BY orderIndex ASC")
    fun getAllCards(): List<Card>

    @Query("SELECT * FROM cards WHERE id = :id")
    fun getCardById(id: Int): Card?

    @Update
    fun updateCard(card: Card)

    @Delete
    fun deleteCard(card: Card)
}
