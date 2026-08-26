package com.plantlens.ai.fragments

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.plantlens.ai.R
import com.plantlens.ai.activities.LoginActivity
import com.plantlens.ai.databinding.FragmentProfileBinding
import com.plantlens.ai.firebase.FirebaseManager
import com.plantlens.ai.interfaces.AuthRepository
import com.plantlens.ai.utils.ExportManager
import com.plantlens.ai.utils.Resource
import com.plantlens.ai.utils.TranslationManager
import com.plantlens.ai.viewmodels.PlantViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PlantViewModel by viewModels()
    private var userRole: String = "user"

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var exportManager: ExportManager

    @Inject
    lateinit var firebaseManager: FirebaseManager

    // Launcher for selecting profile photo from gallery
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val uid = viewModel.currentUser?.uid ?: firebaseManager.getCurrentUser()?.uid
            if (uid != null) {
                lifecycleScope.launch {
                    try {
                        firebaseManager.updateUserPhoto(uid, uri.toString())
                        displayUserProfile()
                        Toast.makeText(requireContext(), R.string.profile_updated_success, Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), getString(R.string.profile_update_failed, e.message), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        displayUserProfile()
        setupListeners()
        observeViewModel()
        
        viewModel.loadUserUsage()
        checkAdminRole()
    }

    private fun checkAdminRole() {
        val user = viewModel.currentUser ?: firebaseManager.getCurrentUser()
        if (user != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    authRepository.getUserProfile(user.uid).collect { resource ->
                        if (resource is Resource.Success) {
                            val profile = resource.data
                            userRole = profile.role
                            if (profile.role == "admin") {
                                binding.adminAnalyticsButton.visibility = View.VISIBLE
                            } else {
                                binding.adminAnalyticsButton.visibility = View.GONE
                            }
                            // Refresh limit and stats display with the loaded role
                            viewModel.userUsageState.value?.let { usageResource ->
                                if (usageResource is Resource.Success) {
                                    updateUsageUi(usageResource.data)
                                }
                            }
                        } else {
                            binding.adminAnalyticsButton.visibility = View.GONE
                        }
                    }
                }
            }
        } else {
            binding.adminAnalyticsButton.visibility = View.GONE
        }
    }

    private fun displayUserProfile() {
        if (_binding == null) return
        val currentContext = context ?: return
        val user = firebaseManager.getCurrentUser() ?: viewModel.currentUser
        if (user != null) {
            binding.profileDisplayName.text = user.displayName.ifEmpty { getString(R.string.guest_gardener) }
            binding.profileEmail.text = user.email.ifEmpty { getString(R.string.guest_email) }
            
            // Set Avatar
            if (user.photoUrl.isNotEmpty()) {
                try {
                    binding.profileImage.setImageURI(Uri.parse(user.photoUrl))
                } catch (e: Exception) {
                    binding.profileImage.setImageDrawable(createLetterAvatar(user.displayName))
                }
            } else {
                binding.profileImage.setImageDrawable(createLetterAvatar(user.displayName))
            }
        }

        // Initialize Theme spinner using localized strings
        val sharedPref = currentContext.getSharedPreferences("plantlens_settings", Context.MODE_PRIVATE)

        val themeOptions = arrayOf(
            getString(R.string.theme_follow_system),
            getString(R.string.theme_light),
            getString(R.string.theme_dark)
        )
        val themeAdapter = ArrayAdapter(currentContext, android.R.layout.simple_spinner_item, themeOptions)
        themeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.themeSpinner.adapter = themeAdapter
        val savedThemeMode = sharedPref.getInt("pref_theme_mode", 0)
        binding.themeSpinner.setSelection(savedThemeMode, false)

        val languages = arrayOf("English", "Tamil", "Telugu", "Hindi", "Kannada", "Malayalam", "Bengali", "Marathi", "Gujarati", "Punjabi")
        val langCodes = arrayOf("en", "ta", "te", "hi", "kn", "ml", "bn", "mr", "gu", "pa")
        val langAdapter = ArrayAdapter(currentContext, android.R.layout.simple_spinner_item, languages)
        langAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.languageSpinner.adapter = langAdapter
        val savedLang = sharedPref.getString("pref_language", "en") ?: "en"
        val langIndex = langCodes.indexOf(savedLang).coerceAtLeast(0)
        binding.languageSpinner.setSelection(langIndex, false)

        // Initialize Debug Overlay diagnostics switch state
        if (!com.plantlens.ai.BuildConfig.DEBUG) {
            binding.debugOverlayRow.visibility = View.GONE
            binding.debugOverlayDivider.visibility = View.GONE
        } else {
            binding.debugOverlayRow.visibility = View.VISIBLE
            binding.debugOverlayDivider.visibility = View.VISIBLE
            binding.debugOverlaySwitch.isChecked = sharedPref.getBoolean("pref_show_debug_overlay", true)
        }
    }

    private fun updateUsageUi(usage: FirebaseManager.UserUsage) {
        if (_binding == null) return
        val totalLimit = if (userRole == "admin") 100 else 50
        val remaining = (totalLimit - usage.todayScans).coerceAtLeast(0)
        
        val locale = Locale.getDefault()
        val numFormat = NumberFormat.getInstance(locale)
        val dateFormat = DateFormat.getDateInstance(DateFormat.LONG, locale)
        
        binding.userScansToday.text = "${numFormat.format(usage.todayScans)} / ${numFormat.format(totalLimit)}"
        binding.userRemainingScans.text = numFormat.format(remaining)
        
        val lastScanDateMs = viewModel.lastScanDate
        if (lastScanDateMs > 0L) {
            binding.userLastScanDate.text = dateFormat.format(Date(lastScanDateMs))
        } else {
            binding.userLastScanDate.text = getString(R.string.never)
        }
    }

    private fun observeViewModel() {
        viewModel.userUsageState.observe(viewLifecycleOwner) { resource ->
            if (resource is Resource.Success) {
                updateUsageUi(resource.data)
            }
        }

        viewModel.savedPlantsCount.observe(viewLifecycleOwner) { count ->
            if (_binding != null) {
                val locale = Locale.getDefault()
                val numFormat = NumberFormat.getInstance(locale)
                binding.userPlantsSaved.text = numFormat.format(count ?: 0)
                updateAchievements(count ?: 0)
            }
        }
    }

    private fun setupListeners() {
        val currentContext = context ?: return
        val sharedPref = currentContext.getSharedPreferences("plantlens_settings", Context.MODE_PRIVATE)

        // Tapping profile photo opens gallery picker
        binding.profileImage.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        // Edit profile button opens dialog
        binding.btnEditProfile.setOnClickListener {
            val dialog = EditProfileDialogFragment.newInstance()
            dialog.setOnProfileUpdatedListener {
                displayUserProfile()
            }
            dialog.show(childFragmentManager, EditProfileDialogFragment.TAG)
        }

        var isThemeSpinnerReady = false
        binding.themeSpinner.post { isThemeSpinnerReady = true }
        binding.themeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isThemeSpinnerReady) return
                val currentSelection = sharedPref.getInt("pref_theme_mode", 0)
                if (position != currentSelection && isAdded) {
                    com.plantlens.ai.utils.ThemeLocaleManager.applyThemeMode(requireContext(), position)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val langCodes = arrayOf("en", "ta", "te", "hi", "kn", "ml", "bn", "mr", "gu", "pa")
        var isLangSpinnerReady = false
        binding.languageSpinner.post { isLangSpinnerReady = true }
        binding.languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isLangSpinnerReady) return
                val currentLang = sharedPref.getString("pref_language", "en") ?: "en"
                val selectedLangCode = langCodes.getOrNull(position) ?: "en"
                if (selectedLangCode != currentLang && isAdded) {
                    com.plantlens.ai.utils.ThemeLocaleManager.applyLanguage(requireContext(), selectedLangCode)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        if (com.plantlens.ai.BuildConfig.DEBUG) {
            binding.debugOverlaySwitch.setOnCheckedChangeListener { _, isChecked ->
                sharedPref.edit().putBoolean("pref_show_debug_overlay", isChecked).apply()
            }
        }

        binding.btnExportPDF.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val file = exportManager.exportToPDF(requireContext())
                    Toast.makeText(requireContext(), getString(R.string.toast_pdf_export_success, file.name), Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), getString(R.string.toast_pdf_export_failed, e.message), Toast.LENGTH_LONG).show()
                }
            }
        }

        binding.btnExportCSV.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val file = exportManager.exportToCSV(requireContext())
                    Toast.makeText(requireContext(), getString(R.string.toast_csv_export_success, file.name), Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), getString(R.string.toast_csv_export_failed, e.message), Toast.LENGTH_LONG).show()
                }
            }
        }

        binding.adminAnalyticsButton.setOnClickListener {
            if (isAdded && findNavController().currentDestination?.id == R.id.profileFragment) {
                findNavController().navigate(R.id.action_profile_to_adminAnalytics)
            }
        }

        binding.logoutButton.setOnClickListener {
            authRepository.logout()
            Toast.makeText(requireContext(), getString(R.string.toast_logout_success), Toast.LENGTH_SHORT).show()
            
            // Redirect user straight back to Login
            val intent = Intent(requireActivity(), LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            requireActivity().finish()
        }
    }

    private fun updateAchievements(savedCount: Int) {
        if (_binding == null) return
        val currentContext = context ?: return
        val totalScans = viewModel.totalScansCount
        val sharedPref = currentContext.getSharedPreferences("plantlens_settings", Context.MODE_PRIVATE)
        val currentLang = sharedPref.getString("pref_language", "en") ?: "en"
        val isExplorer = currentLang != "en"

        val firstPlant = savedCount > 0
        val tenPlants = totalScans >= 10
        val fiftyScans = totalScans >= 50
        val expert = totalScans >= 20 && savedCount >= 3
        val explorer = isExplorer || totalScans >= 8

        binding.badgeFirstPlant.alpha = if (firstPlant) 1.0f else 0.3f
        binding.badgeTenPlants.alpha = if (tenPlants) 1.0f else 0.3f
        binding.badgeFiftyScans.alpha = if (fiftyScans) 1.0f else 0.3f
        binding.badgePlantExpert.alpha = if (expert) 1.0f else 0.3f
        binding.badgeLangExplorer.alpha = if (explorer) 1.0f else 0.3f

        // Cloud sync to Firestore
        val user = viewModel.currentUser ?: firebaseManager.getCurrentUser()
        if (user != null) {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val achievements = mapOf(
                "firstPlant" to firstPlant,
                "tenPlants" to tenPlants,
                "fiftyScans" to fiftyScans,
                "expert" to expert,
                "explorer" to explorer
            )
            db.collection("users").document(user.uid)
                .collection("achievements").document("unlocked")
                .set(achievements)
                .addOnFailureListener { e ->
                    android.util.Log.e("ProfileFragment", "Failed to sync achievements: ${e.message}")
                }
        }
    }

    private fun createLetterAvatar(name: String): Drawable {
        val letter = if (name.isNotEmpty()) name.take(1).uppercase() else "G"
        val size = 128
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Background color
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val colors = intArrayOf(
            0xFF10B981.toInt(), // Emerald
            0xFF3B82F6.toInt(), // Blue
            0xFF8B5CF6.toInt(), // Purple
            0xFFF59E0B.toInt(), // Amber
            0xFFEF4444.toInt(), // Red
            0xFFEC4899.toInt()  // Pink
        )
        val index = kotlin.math.abs(name.hashCode()) % colors.size
        paint.color = colors[index]
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        
        // Text
        paint.color = Color.WHITE
        paint.textSize = size / 2f
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        
        // Vertical center alignment
        val bounds = Rect()
        paint.getTextBounds(letter, 0, letter.length, bounds)
        val y = (size / 2f) - bounds.exactCenterY()
        canvas.drawText(letter, size / 2f, y, paint)
        
        return BitmapDrawable(resources, bitmap)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
