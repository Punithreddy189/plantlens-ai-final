package com.plantlens.ai.di

import com.plantlens.ai.interfaces.AuthRepository
import com.plantlens.ai.interfaces.PlantRepository
import com.plantlens.ai.repository.AuthRepositoryImpl
import com.plantlens.ai.repository.PlantRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPlantRepository(
        plantRepositoryImpl: PlantRepositoryImpl,
    ): PlantRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        authRepositoryImpl: AuthRepositoryImpl,
    ): AuthRepository
}
