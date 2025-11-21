package com.example.navdeep_bilin_myruns5.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ExerciseEntryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MyRunsDatabase : RoomDatabase() {
    abstract fun exerciseEntryDao(): ExerciseEntryDao

    companion object {
        @Volatile private var INSTANCE: MyRunsDatabase? = null // enforces single instance

        fun getInstance(context: Context): MyRunsDatabase =
            INSTANCE ?: synchronized(this) { // double check locking pattern
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MyRunsDatabase::class.java,
                    "myruns5.db"
                ).build().also { INSTANCE = it }
            }
    }
}