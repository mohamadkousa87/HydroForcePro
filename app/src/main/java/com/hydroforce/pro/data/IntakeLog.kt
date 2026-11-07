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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "intake_logs")
data class IntakeLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    /** Raw volume the user drank. */
    val amountMl: Int,
    /** "alarm" when verified from a scheduled alarm, "manual" from the dashboard button. */
    val source: String,
    /** DrinkType.name; defaults to WATER for rows created before drink types existed. */
    val drinkType: String = DrinkType.WATER.name,
    /** Hydration-weighted volume that counts toward the daily goal. */
    val effectiveMl: Int = amountMl
)

@Dao
interface IntakeDao {
    @Insert
    suspend fun insert(log: IntakeLog): Long

    @Query("SELECT * FROM intake_logs WHERE timestamp >= :from ORDER BY timestamp DESC")
    fun logsSince(from: Long): Flow<List<IntakeLog>>

    @Query("SELECT COALESCE(SUM(effectiveMl), 0) FROM intake_logs WHERE timestamp >= :from")
    suspend fun totalSince(from: Long): Int

    @Query("SELECT * FROM intake_logs ORDER BY timestamp ASC")
    fun allLogsFlow(): Flow<List<IntakeLog>>

    @Query("SELECT * FROM intake_logs ORDER BY timestamp DESC")
    suspend fun allLogs(): List<IntakeLog>
}

@Database(entities = [IntakeLog::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun intakeDao(): IntakeDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** Adds drink types + hydration-weighted volume, preserving existing (water) rows. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE intake_logs ADD COLUMN drinkType TEXT NOT NULL DEFAULT 'WATER'")
                db.execSQL("ALTER TABLE intake_logs ADD COLUMN effectiveMl INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE intake_logs SET effectiveMl = amountMl")
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hydroforce.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
