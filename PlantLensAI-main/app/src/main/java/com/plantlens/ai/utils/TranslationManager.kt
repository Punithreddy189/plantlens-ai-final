package com.plantlens.ai.utils

import android.content.Context
import android.util.Log
import android.util.LruCache
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.plantlens.ai.models.Plant
import java.io.InputStreamReader

object TranslationManager {
    private const val TAG = "TranslationManager"
    private var translations: Map<String, Map<String, String>> = emptyMap()
    private var currentLangCode: String = "en"
    
    // Cache for dynamic translations
    private val translationCache = LruCache<String, String>(500)

    fun getCurrentLangCode(): String = currentLangCode

    fun getCurrentLanguageName(): String {
        return when (currentLangCode) {
            "hi" -> "Hindi"
            "ta" -> "Tamil"
            "te" -> "Telugu"
            "kn" -> "Kannada"
            "ml" -> "Malayalam"
            "mr" -> "Marathi"
            "gu" -> "Gujarati"
            "pa" -> "Punjabi"
            "bn" -> "Bengali"
            else -> "English"
        }
    }

    fun init(context: Context, langCode: String) {
        currentLangCode = langCode
        translationCache.evictAll()
        if (langCode == "en") {
            translations = emptyMap()
            return
        }

        try {
            val fileName = "translations/$langCode.json"
            val inputStream = context.assets.open(fileName)
            val reader = InputStreamReader(inputStream)
            val type = object : TypeToken<Map<String, Map<String, String>>>() {}.type
            translations = Gson().fromJson(reader, type)
            reader.close()
            Log.d(TAG, "Successfully loaded translations for $langCode")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load translations for $langCode: ${e.message}")
            translations = emptyMap()
        }
    }

    fun translate(category: String, text: String): String {
        if (currentLangCode == "en" || translations.isEmpty()) {
            return text
        }
        val cacheKey = "$category:$text"
        val cached = translationCache.get(cacheKey)
        if (cached != null) {
            return cached
        }
        val categoryMap = translations[category]
        val translated = categoryMap?.get(text) ?: text
        translationCache.put(cacheKey, translated)
        return translated
    }

    fun getPlantName(plant: Plant): String {
        if (currentLangCode == "en") return plant.name
        
        // Priority 1: localNames[currentLangCode]
        val localName = plant.localNames[currentLangCode]
        if (!localName.isNullOrEmpty()) {
            return localName
        }

        // Priority 2: Hardcoded map fallback
        val staticMap = getStaticNameMap(currentLangCode)
        val staticName = staticMap[plant.id]
        if (staticName != null) {
            return staticName
        }

        // Priority 3: localNames["en"]
        val enLocalName = plant.localNames["en"]
        if (!enLocalName.isNullOrEmpty()) {
            return enLocalName
        }

        // Priority 4: plant.name
        return plant.name
    }

    fun getPlantName(plantId: String, defaultName: String): String {
        if (currentLangCode == "en") return defaultName
        val cleanId = plantId.replace("saved_", "")
        val staticMap = getStaticNameMap(currentLangCode)
        return staticMap[cleanId] ?: defaultName
    }

    private fun getStaticNameMap(langCode: String): Map<String, String> {
        return when (langCode) {
            "te" -> mapOf(
                "monstera" to "మోన్‌స్టెరా",
                "aloe_vera" to "కలబంద",
                "snake_plant" to "పాము మొక్క",
                "peace_lily" to "పీస్ లిల్లీ",
                "lavender" to "లావెండర్",
                "giant_milkweed" to "జిల్లేడు",
                "touch_me_not" to "తుమ్మ మొక్క / అత్తిపత్తి"
            )
            "ta" -> mapOf(
                "monstera" to "மான்ஸ்டெரா",
                "aloe_vera" to "கற்றாழை",
                "snake_plant" to "பாம்பு செடி",
                "peace_lily" to "அமைதி லில்லி",
                "lavender" to "லாவெண்டர்",
                "giant_milkweed" to "எருக்கு",
                "touch_me_not" to "தொட்டால் சிணுங்கி"
            )
            "hi" -> mapOf(
                "monstera" to "मॉन्स्टेरा",
                "aloe_vera" to "घृतकुमारी",
                "snake_plant" to "स्नेक प्लांट",
                "peace_lily" to "पीस लिली",
                "lavender" to "लैवेंडर",
                "giant_milkweed" to "मदार",
                "touch_me_not" to "छुईमुई"
            )
            "kn" -> mapOf(
                "monstera" to "ಮಾನ್‌ಸ್ಟೆರಾ",
                "aloe_vera" to "ಲೋಳೆಸರ",
                "snake_plant" to "ಹಾವಿನ ಗಿಡ",
                "peace_lily" to "ಪೀಸ್ ಲಿಲಿ",
                "lavender" to "ಲ್ಯಾವೆಂಡರ್",
                "giant_milkweed" to "ಎಕ್ಕದ ಗಿಡ",
                "touch_me_not" to "ಮುಟ್ಟಿದರೆ ಮುನಿ"
            )
            "ml" -> mapOf(
                "monstera" to "മോൺസ്റ്റെറ",
                "aloe_vera" to "കറ്റാർവാഴ",
                "snake_plant" to "സ്നേക്ക് പ്ലാന്റ്",
                "peace_lily" to "പീസ് ലില്ലി",
                "lavender" to "ലാവെൻഡർ",
                "giant_milkweed" to "എരുക്ക്",
                "touch_me_not" to "തൊട്ടാവാടി"
            )
            "bn" -> mapOf(
                "monstera" to "মনস্টেরা",
                "aloe_vera" to "ঘৃতকুমারী / অ্যালোভেরা",
                "snake_plant" to "স্নেক প্ল্যান্ট",
                "peace_lily" to "পিস লিলি",
                "lavender" to "ল্যাভেন্ডার",
                "giant_milkweed" to "আকন্দ",
                "touch_me_not" to "লজ্জাবতী"
            )
            "mr" -> mapOf(
                "monstera" to "मॉन्स्टेरा",
                "aloe_vera" to "कोरफड",
                "snake_plant" to "स्नेक प्लांट",
                "peace_lily" to "पीस लिली",
                "lavender" to "लॅव्हेंडर",
                "giant_milkweed" to "रुई",
                "touch_me_not" to "लाजाळू"
            )
            "gu" -> mapOf(
                "monstera" to "મોન્સ્ટેરા",
                "aloe_vera" to "એલોવેરા / કુંવારપાઠું",
                "snake_plant" to "સ્નેક પ્લાન્ટ",
                "peace_lily" to "પીસ લીલી",
                "lavender" to "લેવેન્ડર",
                "giant_milkweed" to "આકડો",
                "touch_me_not" to "લજામણી"
            )
            "pa" -> mapOf(
                "monstera" to "ਮੌਨਸਟੇਰਾ",
                "aloe_vera" to "ਗੁਵਾਰਪਾਠਾ / ਐਲੋਵੇਰਾ",
                "snake_plant" to "ਸਨੇਕ ਪਲਾਂਟ",
                "peace_lily" to "ਪੀਸ ਲਿਲੀ",
                "lavender" to "ਲੈਵੇਂਡਰ",
                "giant_milkweed" to "ਅੱਕ",
                "touch_me_not" to "ਛੂਈ-ਮੂਈ"
            )
            else -> emptyMap()
        }
    }

    fun translateSoilType(type: String): String {
        if (currentLangCode == "en" || type.isBlank() || type == "N/A") return type
        val lower = type.lowercase().trim()
        return when (currentLangCode) {
            "te" -> when {
                lower.contains("loam") && lower.contains("sand") -> "ఇసుక లోమి మట్టి"
                lower.contains("loam") && lower.contains("clay") -> "బంకమట్టి లోమి మట్టి"
                lower.contains("loam") -> "లోమి మట్టి"
                lower.contains("sand") -> "ఇసుక మట్టి"
                lower.contains("clay") -> "బంకమట్టి"
                else -> type
            }
            "hi" -> when {
                lower.contains("loam") && lower.contains("sand") -> "बलुई दोमट मिट्टी"
                lower.contains("loam") && lower.contains("clay") -> "चिकनी दोमट मिट्टी"
                lower.contains("loam") -> "दोमट मिट्टी"
                lower.contains("sand") -> "बलुई मिट्टी"
                lower.contains("clay") -> "चिकनी मिट्टी"
                else -> type
            }
            "ta" -> when {
                lower.contains("loam") && lower.contains("sand") -> "மணல் கலந்த செம்மண்"
                lower.contains("loam") -> "வண்டல் மண் / லோமி மண்"
                lower.contains("sand") -> "மணல் மண்"
                lower.contains("clay") -> "களிமண்"
                else -> type
            }
            "kn" -> when {
                lower.contains("loam") && lower.contains("sand") -> "ಮರಳು ಮಿಶ್ರಿತ ಗೋಡು ಮಣ್ಣು"
                lower.contains("loam") -> "ಗೋಡು ಮಣ್ಣು"
                lower.contains("sand") -> "ಮರಳು ಮಣ್ಣು"
                lower.contains("clay") -> "ಜೇಡಿ ಮಣ್ಣು"
                else -> type
            }
            else -> type
        }
    }

    fun translateSoilDrainage(drainage: String): String {
        if (currentLangCode == "en" || drainage.isBlank() || drainage == "N/A") return drainage
        val lower = drainage.lowercase().trim()
        return when (currentLangCode) {
            "te" -> when {
                lower.contains("well") -> "మంచి నీటి పారుదల"
                lower.contains("moist") -> "తేమతో కూడిన పారుదల"
                lower.contains("moderate") -> "మధ్యస్థ పారుదల"
                else -> drainage
            }
            "hi" -> when {
                lower.contains("well") -> "अच्छी जल निकासी"
                lower.contains("moist") -> "नमी युक्त जल निकासी"
                lower.contains("moderate") -> "मध्यम जल निकासी"
                else -> drainage
            }
            "ta" -> when {
                lower.contains("well") -> "நன்றாக வடிகட்டும்"
                lower.contains("moist") -> "ஈரப்பதமான வடிகால்"
                lower.contains("moderate") -> "மிதமான வடிகால்"
                else -> drainage
            }
            "kn" -> when {
                lower.contains("well") -> "ಉತ್ತಮ ಒಳಚರಂಡಿ"
                lower.contains("moist") -> "ತೇವಾಂಶವುಳ್ಳ ಒಳಚರಂಡಿ"
                lower.contains("moderate") -> "ಮಧ್ಯಮ ಒಳಚರಂಡಿ"
                else -> drainage
            }
            else -> drainage
        }
    }

    fun translateSeverity(severity: String): String {
        if (currentLangCode == "en" || severity.isBlank() || severity == "N/A") return severity
        val lower = severity.lowercase().trim()
        return when (currentLangCode) {
            "te" -> when {
                lower.contains("critical") -> "అత్యవసరం (Critical)"
                lower.contains("high") || lower.contains("severe") -> "అధికం (High)"
                lower.contains("moderate") || lower.contains("medium") -> "మధ్యస్థం (Moderate)"
                lower.contains("low") -> "తక్కువ (Low)"
                lower.contains("optimal") || lower.contains("healthy") || lower.contains("none") -> "ఆరోగ్యకరం (Optimal)"
                else -> severity
            }
            "hi" -> when {
                lower.contains("critical") -> "अति गंभीर (Critical)"
                lower.contains("high") || lower.contains("severe") -> "गंभीर (High)"
                lower.contains("moderate") || lower.contains("medium") -> "मध्यम (Moderate)"
                lower.contains("low") -> "हल्का (Low)"
                lower.contains("optimal") || lower.contains("healthy") || lower.contains("none") -> "उत्तम (Optimal)"
                else -> severity
            }
            "ta" -> when {
                lower.contains("critical") -> "மிக தீவிரம் (Critical)"
                lower.contains("high") || lower.contains("severe") -> "அதிக தீவிரம் (High)"
                lower.contains("moderate") || lower.contains("medium") -> "மிதமானது (Moderate)"
                lower.contains("low") -> "குறைவு (Low)"
                lower.contains("optimal") || lower.contains("healthy") || lower.contains("none") -> "ஆரோக்கியமானது (Optimal)"
                else -> severity
            }
            "kn" -> when {
                lower.contains("critical") -> "ಅತ್ಯಂತ ತೀವ್ರ (Critical)"
                lower.contains("high") || lower.contains("severe") -> "ಹೆಚ್ಚಿನ ತೀವ್ರತೆ (High)"
                lower.contains("moderate") || lower.contains("medium") -> "ಮಧ್ಯಮ (Moderate)"
                lower.contains("low") -> "ಕಡಿಮೆ (Low)"
                lower.contains("optimal") || lower.contains("healthy") || lower.contains("none") -> "ಉತ್ತಮ (Optimal)"
                else -> severity
            }
            else -> severity
        }
    }
}
