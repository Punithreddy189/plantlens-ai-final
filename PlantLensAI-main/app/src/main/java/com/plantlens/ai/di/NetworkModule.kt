package com.plantlens.ai.di

import android.util.Log
import com.plantlens.ai.BuildConfig
import com.plantlens.ai.network.OpenMeteoApiService
import com.plantlens.ai.network.PlantDiseaseApiService
import com.plantlens.ai.network.PlantNetApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val PLANTNET_BASE_URL = "https://my-api.plantnet.org/"
    private const val OPEN_METEO_BASE_URL = "https://api.open-meteo.com/"
    
    private const val FASTAPI_BACKEND_BASE_URL = "http://127.0.0.1:8000/"

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(loggingInterceptor: HttpLoggingInterceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    @Named("backendClient")
    fun provideBackendOkHttpClient(loggingInterceptor: HttpLoggingInterceptor): OkHttpClient {
        val hostFailoverInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val originalUrl = originalRequest.url

            // If using external HTTPS tunnel (Ngrok, Localtunnel, Render), connect directly
            if (originalUrl.isHttps || FASTAPI_BACKEND_BASE_URL.startsWith("https") || originalUrl.port != 8000) {
                val tunnelRequest = originalRequest.newBuilder()
                    .header("Bypass-Tunnel-Reminder", "true")
                    .build()
                Log.d("NetworkModule", "Connecting to cloud/tunnel backend: $originalUrl")
                return@Interceptor chain.proceed(tunnelRequest)
            }

            val isEmulator = android.os.Build.FINGERPRINT.startsWith("generic")
                || android.os.Build.FINGERPRINT.startsWith("unknown")
                || android.os.Build.MODEL.contains("google_sdk")
                || android.os.Build.MODEL.contains("Emulator")
                || android.os.Build.MODEL.contains("Android SDK built for x86")
                || android.os.Build.HARDWARE.contains("goldfish")
                || android.os.Build.HARDWARE.contains("ranchu")
                || android.os.Build.PRODUCT.contains("sdk")

            // Prioritize 127.0.0.1 (ADB reverse), PC Wi-Fi IP (192.168.1.3), and 10.0.2.2 (Emulator)
            val candidateHosts = if (isEmulator) {
                listOf("10.0.2.2", "127.0.0.1", "192.168.1.3")
            } else {
                listOf("127.0.0.1", "192.168.1.3", "10.0.2.2")
            }
            var lastException: IOException? = null

            for (host in candidateHosts) {
                val newUrl = originalUrl.newBuilder()
                    .host(host)
                    .build()
                val newRequest = originalRequest.newBuilder()
                    .url(newUrl)
                    .header("Bypass-Tunnel-Reminder", "true")
                    .build()
                try {
                    Log.d("NetworkModule", "Attempting connection to backend at $newUrl...")
                    val response = chain.proceed(newRequest)
                    if (response.isSuccessful || response.code < 500) {
                        Log.i("NetworkModule", "Connected successfully to backend host: $host:8000")
                        return@Interceptor response
                    }
                    return@Interceptor response
                } catch (e: IOException) {
                    Log.w("NetworkModule", "Failed to connect to host $host:8000 (${e.message}). Trying next candidate host...")
                    lastException = e
                }
            }
            throw lastException ?: IOException("Failed to reach FastAPI backend on all candidate hosts: $candidateHosts")
        }

        return OkHttpClient.Builder()
            .addInterceptor(hostFailoverInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    fun providePlantNetApiService(okHttpClient: OkHttpClient): PlantNetApiService {
        return Retrofit.Builder()
            .baseUrl(PLANTNET_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PlantNetApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideOpenMeteoApiService(okHttpClient: OkHttpClient): OpenMeteoApiService {
        return Retrofit.Builder()
            .baseUrl(OPEN_METEO_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenMeteoApiService::class.java)
    }

    @Provides
    @Singleton
    fun providePlantDiseaseApiService(@Named("backendClient") backendClient: OkHttpClient): PlantDiseaseApiService {
        return Retrofit.Builder()
            .baseUrl(FASTAPI_BACKEND_BASE_URL)
            .client(backendClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PlantDiseaseApiService::class.java)
    }

    @Provides
    @Singleton
    fun providePlantLensApiService(@Named("backendClient") backendClient: OkHttpClient): com.plantlens.ai.network.PlantLensApiService {
        return Retrofit.Builder()
            .baseUrl(FASTAPI_BACKEND_BASE_URL)
            .client(backendClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(com.plantlens.ai.network.PlantLensApiService::class.java)
    }
}

