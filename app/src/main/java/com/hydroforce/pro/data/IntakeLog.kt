package com.hydroforce.pro.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "intake_logs")
data class IntakeLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val amountMl: Int,
    /** "alarm" when verified from a scheduled alarm, "manual" from the dashboard button. */
    val source: String
)

@Dao
interface IntakeDao {
    @Insert
    suspend fun insert(log: IntakeLog): Long

    @Query("SELECT * FROM intake_logs WHERE timestamp >= :from ORDER BY timestamp DESC")
    fun logsSince(from: Long): Flow<List<IntakeLog>>

    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM intake_logs WHERE timestamp >= :from")
    suspend fun totalSince(from: Long): Int
}

@Database(entities = [IntakeLog::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun intakeDao(): IntakeDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hydroforce.db"
                ).build().also { instance = it }
            }
    }
}
