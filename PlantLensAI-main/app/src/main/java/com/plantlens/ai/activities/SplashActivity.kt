package com.plantlens.ai.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.plantlens.ai.databinding.ActivitySplashBinding
import com.plantlens.ai.interfaces.AuthRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    @Inject
    lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        com.plantlens.ai.utils.ThemeLocaleManager.init(this)

        // Show captivating splash screen for 2 seconds before routing
        Handler(Looper.getMainLooper()).postDelayed({
            checkAuthAndNavigate()
        }, 2000)
    }

    private fun checkAuthAndNavigate() {
        val currentUser = authRepository.getCurrentUser()
        if (currentUser != null) {
            // Already authenticated, direct to Dashboard
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            // Guest or unauthenticated, direct to Login card
            startActivity(Intent(this, LoginActivity::class.java))
        }
        finish()
    }
}
