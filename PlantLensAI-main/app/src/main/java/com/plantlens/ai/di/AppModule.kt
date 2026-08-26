package com.plantlens.ai.di

import android.content.Context
import androidx.room.Room
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.plantlens.ai.database.PlantDao
import com.plantlens.ai.database.PlantDatabase
import com.plantlens.ai.database.SavedPlantDao
import com.plantlens.ai.database.ScanHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PlantDatabase {
        return Room.databaseBuilder(
            context,
            PlantDatabase::class.java,
            PlantDatabase.DATABASE_NAME,
        )
        .addMigrations(
            PlantDatabase.MIGRATION_6_7,
            PlantDatabase.MIGRATION_7_8,
            PlantDatabase.MIGRATION_8_9
        )
        .fallbackToDestructiveMigration(dropAllTables = true)
        .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
        .build()
    }

    @Provides
    @Singleton
    fun providePlantDao(database: PlantDatabase): PlantDao = database.plantDao()

    @Provides
    @Singleton
    fun provideSavedPlantDao(database: PlantDatabase): SavedPlantDao = database.savedPlantDao()

    @Provides
    @Singleton
    fun provideScanHistoryDao(database: PlantDatabase): ScanHistoryDao = database.scanHistoryDao()

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()
}
