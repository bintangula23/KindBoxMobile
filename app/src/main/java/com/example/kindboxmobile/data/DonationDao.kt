package com.example.kindboxmobile.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DonationDao {
    @Query("SELECT * FROM donations")
    fun getAllDonations(): Flow<List<DonationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(donations: List<DonationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(donation: DonationEntity)

    @Query("DELETE FROM donations WHERE id = :id")
    suspend fun deleteById(id: String)
}