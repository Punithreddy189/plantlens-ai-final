package com.plantlens.ai.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.plantlens.ai.R
import com.plantlens.ai.adapters.SavedPlantAdapter
import com.plantlens.ai.databinding.FragmentSavedPlantsBinding
import com.plantlens.ai.models.SavedPlant
import com.plantlens.ai.viewmodels.PlantViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SavedPlantsFragment : Fragment(), SavedPlantAdapter.SavedPlantClickListener {

    private var _binding: FragmentSavedPlantsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PlantViewModel by viewModels()
    private lateinit var savedPlantAdapter: SavedPlantAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSavedPlantsBinding.inflate(inflater, container, false)
        return binding.root
    }

    private var allSavedPlants = listOf<SavedPlant>()
    private var currentSearchQuery = ""
    private var currentCategoryFilter = "All Plants"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSearchAndFilters()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        savedPlantAdapter = SavedPlantAdapter(this)
        binding.gardenRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = savedPlantAdapter
        }
    }

    private fun setupSearchAndFilters() {
        // Search text listener
        binding.searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString()?.lowercase()?.trim() ?: ""
                applyFilter()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // Chips category selection listener
        binding.categoryChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            currentCategoryFilter = when (checkedIds.firstOrNull()) {
                R.id.chipIndoor -> "Indoor Plants"
                R.id.chipOutdoor -> "Outdoor Plants"
                R.id.chipSucculents -> "Succulents"
                R.id.chipMedicinal -> "Medicinal Plants"
                R.id.chipFlowering -> "Flowering Plants"
                else -> "All Plants"
            }
            applyFilter()
        }
    }

    private fun applyFilter() {
        val filteredList = allSavedPlants.filter { plant ->
            // Filter by search query (match name, nickname, or scientificName)
            val matchesSearch = currentSearchQuery.isEmpty() ||
                    plant.plantName.lowercase().contains(currentSearchQuery) ||
                    plant.nickname.lowercase().contains(currentSearchQuery) ||
                    plant.scientificName.lowercase().contains(currentSearchQuery)

            val matchesCategory = currentCategoryFilter == "All Plants" || 
                    getMockCategoryForPlant(plant.plantName) == currentCategoryFilter

            matchesSearch && matchesCategory
        }

        if (filteredList.isEmpty()) {
            binding.gardenRecyclerView.visibility = View.GONE
            binding.gardenEmptyState.visibility = View.VISIBLE
        } else {
            binding.gardenRecyclerView.visibility = View.VISIBLE
            binding.gardenEmptyState.visibility = View.GONE
            savedPlantAdapter.submitList(filteredList)
        }
    }

    private fun getMockCategoryForPlant(plantName: String): String {
        return when (plantName.lowercase()) {
            "peace lily", "monstera deliciosa", "snake plant" -> "Indoor Plants"
            "lavender" -> "Flowering Plants"
            "aloe vera" -> "Succulents"
            else -> "Outdoor Plants"
        }
    }

    private fun observeViewModel() {
        viewModel.savedPlantsList.observe(viewLifecycleOwner) { list ->
            allSavedPlants = list
            applyFilter()
            viewModel.rescheduleAllReminders(list)
        }

        viewModel.gardenAnalytics.observe(viewLifecycleOwner) { analytics ->
            val numberFormat = java.text.NumberFormat.getInstance(java.util.Locale.getDefault())
            val growthFormat = java.text.NumberFormat.getInstance(java.util.Locale.getDefault()).apply {
                maximumFractionDigits = 1
                minimumFractionDigits = 1
            }
            binding.tvTotalPlants.text = numberFormat.format(analytics.totalPlants)
            binding.tvAverageHealth.text = "${numberFormat.format(analytics.averageHealthScore)}%"
            binding.tvNeedingAttention.text = numberFormat.format(analytics.plantsNeedingAttention)
            binding.tvTotalLogs.text = numberFormat.format(analytics.totalGrowthLogs)
            binding.tvAverageGrowth.text = "+${growthFormat.format(analytics.averageHeightIncrease)} cm"
            binding.tvMostImproved.text = analytics.mostImprovedPlant
        }
    }

    override fun onWaterClicked(savedPlant: SavedPlant) {
        viewModel.waterPlant(savedPlant.id)
        Toast.makeText(
            requireContext(),
            getString(R.string.toast_watered_success, savedPlant.nickname),
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDeleteClicked(savedPlant: SavedPlant) {
        viewModel.removePlantFromGarden(savedPlant)
        Toast.makeText(
            requireContext(),
            getString(R.string.toast_removed_success, savedPlant.nickname),
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onItemClicked(savedPlant: SavedPlant) {
        if (savedPlant.plantId.isNotBlank() && findNavController().currentDestination?.id == R.id.savedPlantsFragment) {
            val bundle = Bundle().apply {
                putString("plantId", savedPlant.plantId)
            }
            findNavController().navigate(R.id.action_saved_to_details, bundle)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
