package com.example.navdeep_bilin_myruns5.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseEntryDao {
    @Insert suspend fun insert(entry: ExerciseEntryEntity): Long // insert new excercise

    // streamline all the entries, newer ones go first
    @Query("SELECT * FROM exercise_entries ORDER BY dateTimeMillis DESC")
    fun getAll(): Flow<List<ExerciseEntryEntity>>

    //fetch single entrie's by their ID
    @Query("SELECT * FROM exercise_entries WHERE id = :id")
    suspend fun getById(id: Long): ExerciseEntryEntity?

    //remove the entry by its ID
    @Query("DELETE FROM exercise_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM exercise_entries")
    suspend fun deleteAll()

    // remove using the entity object
    @Delete suspend fun delete(entry: ExerciseEntryEntity)
}


// summary:

//Responsibilities:
// *  - Insert, delete, and query ExerciseEntry rows via Room
// *  - Have a Flow<List<ExerciseEntryEntity>> for live UI updates in History
// *
// * Threading:
// *  - All suspend functions are called from coroutines on Dispatchers.IO via Repository
// *
// * Notes:
// *  - getAll() returns Flow so History auto-refreshes on DB changes
