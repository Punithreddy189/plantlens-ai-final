package com.plantlens.ai.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.button.MaterialButton
import com.plantlens.ai.R
import com.plantlens.ai.adapters.PlantAdapter
import com.plantlens.ai.databinding.FragmentLibraryBinding
import com.plantlens.ai.models.Plant
import com.plantlens.ai.viewmodels.PlantViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LibraryFragment : Fragment(), PlantAdapter.PlantClickListener {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PlantViewModel by viewModels()
    private lateinit var plantAdapter: PlantAdapter
    private var activeChip: MaterialButton? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupListeners()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        plantAdapter = PlantAdapter(this)
        binding.plantsRecyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = plantAdapter
        }
    }

    private fun setupListeners() {
        // Search text change listener
        binding.librarySearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.setSearchQuery(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Alpha sorting toggle
        binding.sortButton.setOnClickListener {
            viewModel.toggleSorting()
        }

        // Category filter chips clicks
        activeChip = binding.chipAll
        setupCategoryChip(binding.chipAll, "All")
        setupCategoryChip(binding.chipIndoor, "Indoor")
        setupCategoryChip(binding.chipSucculent, "Succulent")
        setupCategoryChip(binding.chipOutdoor, "Outdoor")
    }

    private fun setupCategoryChip(chip: MaterialButton, category: String) {
        chip.setOnClickListener {
            if (activeChip == chip) return@setOnClickListener

            // Reset previous active chip style
            activeChip?.apply {
                setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.surface_light))
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary_light))
                strokeWidth = 1
            }

            // Select new active chip style
            chip.apply {
                setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.primary))
                setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                strokeWidth = 0
            }

            activeChip = chip
            viewModel.setCategoryFilter(category)
        }
    }

    private fun observeViewModel() {
        viewModel.plantsList.observe(viewLifecycleOwner) { list ->
            if (list.isEmpty()) {
                binding.plantsRecyclerView.visibility = View.GONE
                binding.emptyStateText.visibility = View.VISIBLE
            } else {
                binding.plantsRecyclerView.visibility = View.VISIBLE
                binding.emptyStateText.visibility = View.GONE
                plantAdapter.submitList(list)
            }
        }
    }

    override fun onPlantClicked(plant: Plant) {
        val bundle = Bundle().apply {
            putString("plantId", plant.id)
        }
        findNavController().navigate(R.id.action_library_to_details, bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
