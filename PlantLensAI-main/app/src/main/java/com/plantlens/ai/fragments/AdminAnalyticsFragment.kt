package com.plantlens.ai.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.plantlens.ai.R
import com.plantlens.ai.databinding.FragmentAdminAnalyticsBinding
import com.plantlens.ai.interfaces.AuthRepository
import com.plantlens.ai.utils.Resource
import com.plantlens.ai.viewmodels.PlantViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class AdminAnalyticsFragment : Fragment() {

    private var _binding: FragmentAdminAnalyticsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PlantViewModel by viewModels()

    @Inject
    lateinit var authRepository: AuthRepository

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminAnalyticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        checkAdminAccess()
    }

    private fun checkAdminAccess() {
        val currentUser = viewModel.currentUser
        if (currentUser == null) {
            showAccessDenied()
            return
        }

        // Fetch user profile from Firestore to verify role
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                authRepository.getUserProfile(currentUser.uid).collect { resource ->
                    when (resource) {
                        is Resource.Success -> {
                            val profile = resource.data
                            if (profile.role == "admin") {
                                // User is verified admin, load the stats
                                setupViews()
                                observeViewModel()
                                viewModel.loadAdminStats()
                            } else {
                                showAccessDenied()
                            }
                        }
                        is Resource.Error -> {
                            showAccessDenied()
                        }
                        is Resource.Loading -> {
                            binding.adminLoadingSpinner.visibility = View.VISIBLE
                            binding.adminStatsContainer.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun showAccessDenied() {
        Toast.makeText(requireContext(), getString(R.string.error_access_denied_admin), Toast.LENGTH_LONG).show()
        findNavController().popBackStack()
    }

    private fun setupViews() {
        binding.adminLoadingSpinner.visibility = View.GONE
        binding.adminStatsContainer.visibility = View.VISIBLE
    }

    private fun observeViewModel() {
        viewModel.adminStatsState.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    binding.adminLoadingSpinner.visibility = View.VISIBLE
                    binding.adminStatsContainer.visibility = View.GONE
                }
                is Resource.Success -> {
                    binding.adminLoadingSpinner.visibility = View.GONE
                    binding.adminStatsContainer.visibility = View.VISIBLE
                    
                    val stats = resource.data
                    val numberFormat = java.text.NumberFormat.getInstance(java.util.Locale.getDefault())
                    val efficiencyFormat = java.text.NumberFormat.getInstance(java.util.Locale.getDefault()).apply {
                        maximumFractionDigits = 1
                        minimumFractionDigits = 1
                    }

                    binding.adminTotalUsers.text = numberFormat.format(stats.totalUsers)
                    binding.adminTotalScans.text = numberFormat.format(stats.totalScans)
                    binding.adminCacheHits.text = numberFormat.format(stats.cacheHits)
                    binding.adminCallsSaved.text = numberFormat.format(stats.requestsSaved)
                    binding.adminPlantNetCalls.text = numberFormat.format(stats.plantNetCalls)
                    binding.adminCacheEfficiency.text = "${efficiencyFormat.format(stats.cacheEfficiency)}%"
                    binding.adminActiveUsers.text = numberFormat.format(stats.todayActiveUsers)
                    
                    populateTopPlants(stats.topPlants, stats.totalScans)
                }
                is Resource.Error -> {
                    binding.adminLoadingSpinner.visibility = View.GONE
                    binding.adminStatsContainer.visibility = View.VISIBLE
                    Toast.makeText(requireContext(), getString(R.string.error_loading_stats_format, resource.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun populateTopPlants(topPlants: List<Pair<String, Int>>, totalScans: Int) {
        binding.adminTopPlantsList.removeAllViews()
        
        if (topPlants.isEmpty()) {
            binding.adminEmptyPlantsText.visibility = View.VISIBLE
        } else {
            binding.adminEmptyPlantsText.visibility = View.GONE
            val numberFormat = java.text.NumberFormat.getInstance(java.util.Locale.getDefault())
            val percentFormat = java.text.NumberFormat.getInstance(java.util.Locale.getDefault()).apply {
                maximumFractionDigits = 0
            }
            val totalTopScans = topPlants.sumOf { it.second }
            
            topPlants.forEachIndexed { index, pair ->
                val name = pair.first
                val count = pair.second
                val pct = if (totalScans > 0) {
                    (count.toFloat() / totalScans.toFloat()) * 100f
                } else {
                    (count.toFloat() / totalTopScans.toFloat()) * 100f
                }
                
                val rowView = layoutInflater.inflate(R.layout.item_admin_top_plant, binding.adminTopPlantsList, false)
                val rankText = rowView.findViewById<TextView>(R.id.plantRank)
                val nameText = rowView.findViewById<TextView>(R.id.plantName)
                val statsText = rowView.findViewById<TextView>(R.id.plantStats)
                val progress = rowView.findViewById<LinearProgressIndicator>(R.id.plantProgress)
                
                rankText.text = "${numberFormat.format(index + 1)}."
                nameText.text = com.plantlens.ai.utils.TranslationManager.getPlantName(name.lowercase().replace(" ", "_"), name)
                statsText.text = getString(R.string.admin_plant_stats_format, numberFormat.format(count), percentFormat.format(pct))
                progress.progress = pct.toInt()
                
                binding.adminTopPlantsList.addView(rowView)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
