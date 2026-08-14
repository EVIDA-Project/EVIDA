package com.example.evida.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [EvidenceLog::class], version = 3, exportSchema = false)
abstract class EvidenceLogDatabase : RoomDatabase() {

    abstract fun evidenceLogDao(): EvidenceLogDao

    companion object {
        @Volatile
        private var INSTANCE: EvidenceLogDatabase? = null

        fun getDatabase(context: Context): EvidenceLogDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    EvidenceLogDatabase::class.java,
                    "evidence_log_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
