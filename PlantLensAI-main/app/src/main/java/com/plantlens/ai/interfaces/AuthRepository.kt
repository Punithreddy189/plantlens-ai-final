package com.plantlens.ai.interfaces

import com.plantlens.ai.models.User
import com.plantlens.ai.utils.Resource
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    fun getCurrentUser(): User?
    fun login(email: String, password: String): Flow<Resource<User>>
    fun register(email: String, password: String): Flow<Resource<User>>
    fun logout()
    fun getUserProfile(uid: String): Flow<Resource<User>>
}
