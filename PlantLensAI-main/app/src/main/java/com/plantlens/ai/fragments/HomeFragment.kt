package com.plantlens.ai.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.plantlens.ai.R
import com.plantlens.ai.databinding.FragmentHomeBinding
import com.plantlens.ai.viewmodels.PlantViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PlantViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        val userName = viewModel.currentUser?.displayName?.ifEmpty { getString(R.string.gardener) } ?: getString(R.string.gardener)
        binding.greetingText.text = getString(R.string.welcome_greeting, userName)

        binding.scannerCard.setOnClickListener {
            if (isAdded && findNavController().currentDestination?.id == R.id.homeFragment) {
                findNavController().navigate(R.id.action_home_to_scanner)
            }
        }
    }

    private fun observeViewModel() {
        viewModel.savedPlantsCount.observe(viewLifecycleOwner) { count ->
            if (_binding != null) {
                binding.homeTotalSaved.text = (count ?: 0).toString()
            }
        }

        if (_binding != null) {
            // Fetch quick scans count locally
            binding.homeTotalScans.text = viewModel.totalScansCount.toString()
        }
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            val userName = viewModel.currentUser?.displayName?.ifEmpty { getString(R.string.gardener) } ?: getString(R.string.gardener)
            binding.greetingText.text = getString(R.string.welcome_greeting, userName)
            binding.homeTotalScans.text = viewModel.totalScansCount.toString()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
