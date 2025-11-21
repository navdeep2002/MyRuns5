package com.example.navdeep_bilin_myruns5.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ExerciseRepository(
    private val dao: ExerciseEntryDao,
    private val io: CoroutineDispatcher = Dispatchers.IO
) {
    val allEntries: Flow<List<ExerciseEntryEntity>> = dao.getAll()

    suspend fun insert(entry: ExerciseEntryEntity): Long = withContext(io) { dao.insert(entry) }
    suspend fun getById(id: Long): ExerciseEntryEntity? = withContext(io) { dao.getById(id) }
    suspend fun deleteById(id: Long) = withContext(io) { dao.deleteById(id) }

    suspend fun deleteAllEntries() { dao.deleteAll()}
}