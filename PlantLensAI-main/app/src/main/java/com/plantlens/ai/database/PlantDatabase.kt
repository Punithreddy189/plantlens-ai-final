package com.plantlens.ai.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.plantlens.ai.models.Plant
import com.plantlens.ai.models.SavedPlant
import com.plantlens.ai.models.ScanRecord
import com.plantlens.ai.models.WeatherRecord

@Database(
    entities = [
        Plant::class, 
        SavedPlant::class, 
        ScanRecord::class,
        com.plantlens.ai.models.GrowthTimelineEntry::class,
        com.plantlens.ai.models.DiseaseHistory::class,
        WeatherRecord::class
    ],
    version = 9,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class PlantDatabase : RoomDatabase() {

    abstract fun plantDao(): PlantDao
    abstract fun savedPlantDao(): SavedPlantDao
    abstract fun scanHistoryDao(): ScanHistoryDao

    companion object {
        const val DATABASE_NAME = "plantlens_db"

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add new columns to plants table
                db.execSQL("ALTER TABLE plants ADD COLUMN uses TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE plants ADD COLUMN localNames TEXT NOT NULL DEFAULT '{}'")

                // Create weather_records table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `weather_records` (
                        `id` TEXT NOT NULL,
                        `latitude` REAL NOT NULL,
                        `longitude` REAL NOT NULL,
                        `temperature` REAL NOT NULL,
                        `humidity` REAL NOT NULL,
                        `rainProbability` REAL NOT NULL,
                        `uvIndex` REAL NOT NULL,
                        `windSpeed` REAL NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_plants ADD COLUMN healthStatus TEXT NOT NULL DEFAULT 'healthy'")
                db.execSQL("ALTER TABLE saved_plants ADD COLUMN disease TEXT NOT NULL DEFAULT 'Healthy'")
                db.execSQL("ALTER TABLE saved_plants ADD COLUMN confidence INTEGER NOT NULL DEFAULT 95")
                db.execSQL("ALTER TABLE saved_plants ADD COLUMN isSaved INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE saved_plants ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE saved_plants ADD COLUMN nickname TEXT NOT NULL DEFAULT ''")
                } catch (_: Exception) {}
            }
        }
    }
}
