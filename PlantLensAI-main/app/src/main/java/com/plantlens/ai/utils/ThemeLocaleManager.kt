package com.plantlens.ai.utils

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowInsetsControllerCompat

object ThemeLocaleManager {
    fun init(context: Context) {
        val sharedPref = context.getSharedPreferences("plantlens_settings", Context.MODE_PRIVATE)
        
        // 1. Apply Theme Mode
        val themeMode = sharedPref.getInt("pref_theme_mode", 0)
        val nightMode = when (themeMode) {
            0 -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            1 -> AppCompatDelegate.MODE_NIGHT_NO
            2 -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }
        
        // Apply status bar icons color using WindowInsetsControllerCompat if context is Activity
        if (context is Activity) {
            try {
                val isDarkTheme = when (nightMode) {
                    AppCompatDelegate.MODE_NIGHT_YES -> true
                    AppCompatDelegate.MODE_NIGHT_NO -> false
                    else -> {
                        val currentNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                        currentNightMode == Configuration.UI_MODE_NIGHT_YES
                    }
                }
                val window = context.window
                val decorView = window?.decorView
                if (window != null && decorView != null) {
                    val windowInsetsController = WindowInsetsControllerCompat(window, decorView)
                    windowInsetsController.isAppearanceLightStatusBars = !isDarkTheme
                }
            } catch (e: Exception) {
                // Defensive fallback to prevent crashes on edge-case decorView attachment
            }
        }
        
        // 2. Apply Language Locale
        val langCode = sharedPref.getString("pref_language", "en") ?: "en"
        TranslationManager.init(context, langCode)
    }

    fun applyLanguage(context: Context, langCode: String) {
        val sharedPref = context.getSharedPreferences("plantlens_settings", Context.MODE_PRIVATE)
        sharedPref.edit().putString("pref_language", langCode).apply()
        TranslationManager.init(context, langCode)
        
        val locales = LocaleListCompat.forLanguageTags(langCode)
        AppCompatDelegate.setApplicationLocales(locales)
    }

    fun applyThemeMode(context: Context, themeMode: Int) {
        val sharedPref = context.getSharedPreferences("plantlens_settings", Context.MODE_PRIVATE)
        sharedPref.edit().putInt("pref_theme_mode", themeMode).apply()
        val nightMode = when (themeMode) {
            0 -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            1 -> AppCompatDelegate.MODE_NIGHT_NO
            2 -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }
}
