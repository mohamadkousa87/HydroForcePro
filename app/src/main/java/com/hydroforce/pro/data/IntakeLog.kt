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

/** A urine-color bio-check the user logged, with any rescue water it added to the goal. */
@Entity(tableName = "bio_check_logs")
data class BioCheckLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    /** Urine color grade 1 (clear) .. 8 (severe dehydration). */
    val urineColorGrade: Int,
    /** Rescue water (ml) this check added to the current cycle's goal. */
    val addedBonusMl: Int
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

    @Query("SELECT * FROM intake_logs ORDER BY timestamp DESC LIMIT 1")
    suspend fun lastIntake(): IntakeLog?

    @Query("DELETE FROM intake_logs WHERE id = :id")
    suspend fun deleteLogById(id: Long)

    // ----- Bio-check (urine color) -----
    @Insert
    suspend fun insertBioCheck(log: BioCheckLog): Long

    @Query("SELECT * FROM bio_check_logs WHERE timestamp >= :cycleStartMillis ORDER BY timestamp DESC")
    fun bioChecksSince(cycleStartMillis: Long): Flow<List<BioCheckLog>>

    @Query("SELECT COALESCE(SUM(addedBonusMl), 0) FROM bio_check_logs WHERE timestamp >= :cycleStartMillis")
    suspend fun bioBonusSince(cycleStartMillis: Long): Int

    @Query("SELECT COUNT(*) FROM bio_check_logs WHERE timestamp >= :cycleStartMillis")
    fun bioCountSince(cycleStartMillis: Long): Flow<Int>

    @Query("SELECT * FROM bio_check_logs ORDER BY timestamp ASC")
    fun allBioChecksFlow(): Flow<List<BioCheckLog>>

    @Query("SELECT * FROM bio_check_logs ORDER BY timestamp DESC LIMIT 1")
    suspend fun lastBioCheck(): BioCheckLog?

    @Query("DELETE FROM bio_check_logs WHERE id = :id")
    suspend fun deleteBioCheck(id: Long)
}

@Database(entities = [IntakeLog::class, BioCheckLog::class], version = 3, exportSchema = false)
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

        /** Adds the bio_check_logs table; existing intake rows are untouched. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS bio_check_logs (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "timestamp INTEGER NOT NULL, " +
                        "urineColorGrade INTEGER NOT NULL, " +
                        "addedBonusMl INTEGER NOT NULL)"
                )
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hydroforce.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
    }
}
