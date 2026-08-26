package com.plantlens.ai.utils

import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

object ErrorHandler {

    fun parseError(throwable: Throwable): String {
        return when (throwable) {
            is SocketTimeoutException -> "Network connection failed"
            is IOException -> "Network connection failed"
            is HttpException -> {
                when (throwable.code()) {
                    401 -> "API configuration issue"
                    429 -> "Daily scan limit reached"
                    else -> "PlantNet unavailable"
                }
            }
            is FirebaseAuthInvalidUserException -> "No user account exists with this email address."
            is FirebaseAuthInvalidCredentialsException -> "Incorrect email or password. Please try again."
            is FirebaseAuthUserCollisionException -> "An account with this email address already exists."
            is FirebaseException -> "Firebase connection error: ${throwable.localizedMessage}"
            is IllegalArgumentException -> "TFLite Classification error: ${throwable.message}"
            else -> "PlantNet unavailable"
        }
    }
}
