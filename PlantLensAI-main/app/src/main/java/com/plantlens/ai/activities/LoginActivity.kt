package com.plantlens.ai.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.plantlens.ai.R
import com.plantlens.ai.databinding.ActivityLoginBinding
import com.plantlens.ai.interfaces.AuthRepository
import com.plantlens.ai.utils.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private var isLoginMode = true // true = login, false = register

    @Inject
    lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        com.plantlens.ai.utils.ThemeLocaleManager.init(this)
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
    }

    private fun setupListeners() {
        binding.actionButton.setOnClickListener {
            handleAuthAction()
        }

        binding.toggleModeText.setOnClickListener {
            toggleAuthMode()
        }
    }

    private fun toggleAuthMode() {
        isLoginMode = !isLoginMode
        if (isLoginMode) {
            binding.authTitle.text = getString(R.string.auth_title_login)
            binding.authSubtitle.text = getString(R.string.auth_subtitle_login)
            binding.confirmPasswordLayout.visibility = View.GONE
            binding.actionButton.text = getString(R.string.action_sign_in)
            binding.toggleModeText.text = getString(R.string.toggle_sign_up)
        } else {
            binding.authTitle.text = getString(R.string.auth_title_register)
            binding.authSubtitle.text = getString(R.string.auth_subtitle_register)
            binding.confirmPasswordLayout.visibility = View.VISIBLE
            binding.actionButton.text = getString(R.string.action_sign_up)
            binding.toggleModeText.text = getString(R.string.toggle_sign_in)
        }
    }

    private fun handleAuthAction() {
        val email = binding.emailInput.text.toString().trim()
        val password = binding.passwordInput.text.toString().trim()
        val confirmPassword = binding.confirmPasswordInput.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill in all inputs.", Toast.LENGTH_SHORT).show()
            return
        }

        if (password.length < 6) {
            Toast.makeText(this, "Password must be at least 6 characters.", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isLoginMode && (password != confirmPassword)) {
            Toast.makeText(this, "Passwords do not match.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val authFlow = if (isLoginMode) {
                authRepository.login(email, password)
            } else {
                authRepository.register(email, password)
            }

            authFlow.collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        binding.authProgress.visibility = View.VISIBLE
                        binding.actionButton.isEnabled = false
                    }
                    is Resource.Success -> {
                        binding.authProgress.visibility = View.GONE
                        binding.actionButton.isEnabled = true
                        Toast.makeText(this@LoginActivity, "Welcome, ${resource.data.displayName}!", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    }
                    is Resource.Error -> {
                        binding.authProgress.visibility = View.GONE
                        binding.actionButton.isEnabled = true
                        Toast.makeText(this@LoginActivity, resource.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}
