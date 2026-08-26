package com.plantlens.ai.repository

import com.plantlens.ai.firebase.FirebaseManager
import com.plantlens.ai.interfaces.AuthRepository
import com.plantlens.ai.models.User
import com.plantlens.ai.utils.ErrorHandler
import com.plantlens.ai.utils.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val firebaseManager: FirebaseManager,
    private val savedPlantDao: com.plantlens.ai.database.SavedPlantDao
) : AuthRepository {

    override fun getCurrentUser(): User? {
        return firebaseManager.getCurrentUser()
    }

    override fun login(email: String, password: String): Flow<Resource<User>> = flow {
        emit(Resource.Loading)
        try {
            val user = firebaseManager.loginUser(email, password)
            emit(Resource.Success(user))
        } catch (e: Exception) {
            emit(Resource.Error(e, ErrorHandler.parseError(e)))
        }
    }.flowOn(Dispatchers.IO)

    override fun register(email: String, password: String): Flow<Resource<User>> = flow {
        emit(Resource.Loading)
        try {
            val user = firebaseManager.registerUser(email, password)
            emit(Resource.Success(user))
        } catch (e: Exception) {
            emit(Resource.Error(e, ErrorHandler.parseError(e)))
        }
    }.flowOn(Dispatchers.IO)

    override fun logout() {
        firebaseManager.logout()
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                savedPlantDao.clearAllSavedPlants()
            } catch (e: Exception) {
                android.util.Log.e("AuthRepositoryImpl", "Error clearing saved plants on logout: ${e.message}")
            }
        }
    }

    override fun getUserProfile(uid: String): Flow<Resource<User>> = flow {
        emit(Resource.Loading)
        try {
            val user = firebaseManager.fetchUserProfile(uid)
            if (user != null) {
                emit(Resource.Success(user))
            } else {
                emit(Resource.Error(Exception("User profile not found")))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e, ErrorHandler.parseError(e)))
        }
    }.flowOn(Dispatchers.IO)
}
