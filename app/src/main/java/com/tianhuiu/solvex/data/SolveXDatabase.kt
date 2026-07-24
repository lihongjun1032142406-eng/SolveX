package com.tianhuiu.solvex.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.tianhuiu.solvex.data.dao.HistoryDao
import com.tianhuiu.solvex.data.models.HistoryItem

@Database(entities = [HistoryItem::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class SolveXDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var INSTANCE: SolveXDatabase? = null

        fun getDatabase(context: Context): SolveXDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SolveXDatabase::class.java,
                    "solvex_database"
                ).fallbackToDestructiveMigration(false)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
