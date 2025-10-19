package com.arbortag.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Project::class, Tree::class, Species::class],
    version = 1,
    exportSchema = false
)
abstract class ArborTagDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun treeDao(): TreeDao
    abstract fun speciesDao(): SpeciesDao

    companion object {
        @Volatile
        private var INSTANCE: ArborTagDatabase? = null

        fun getInstance(context: Context): ArborTagDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ArborTagDatabase::class.java,
                    "arbortag_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
