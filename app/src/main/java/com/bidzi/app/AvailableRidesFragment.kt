package com.bidzi.app

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController

import androidx.recyclerview.widget.LinearLayoutManager
import com.bidzi.app.databinding.FragmentAvailableRidesBinding
import com.bidzi.app.model.SharedRide
import com.bidzi.app.shared.AvailableRidesViewModel
import com.bidzi.app.shared.AvailableRidesViewModelFactory
import com.bidzi.app.shared.JoinRideState

import com.bidzi.app.shared.RidesUiState
import com.bidzi.app.shared.SharedRidesService
import com.bidzi.app.shared.UserLocationProviderImpl
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.launch
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts

import androidx.core.content.ContextCompat

class AvailableRidesFragment : Fragment() {

    private var _binding: FragmentAvailableRidesBinding? = null
    private val binding get() = _binding!!

    private lateinit var rideAdapter: RideAdapter

    private val viewModel: AvailableRidesViewModel by viewModels {
        AvailableRidesViewModelFactory(
            SharedRidesService(MyApplication.supabase),
            UserLocationProviderImpl(requireContext())
        )
    }

    // Permission launcher
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true -> {
                // Fine location granted
                viewModel.loadRides()
            }
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                // Coarse location granted (still works)
                viewModel.loadRides()
            }
            else -> {
                // No location permission
                showError("Location permission is required to find nearby rides")
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAvailableRidesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeStates()
        setupListeners()

        // Check and request permissions
        checkLocationPermission()
    }

    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted
                viewModel.loadRides()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                // Show explanation then request
                showPermissionRationale()
            }
            else -> {
                // Request permission
                requestLocationPermission()
            }
        }
    }

    private fun requestLocationPermission() {
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun showPermissionRationale() {
        AlertDialog.Builder(requireContext())
            .setTitle("Location Permission Required")
            .setMessage("This app needs location permission to show you nearby shared rides.")
            .setPositiveButton("Grant Permission") { _, _ ->
                requestLocationPermission()
            }
            .setNegativeButton("Cancel") { _, _ ->
                showError("Location permission is required to find nearby rides")
            }
            .show()
    }

    private fun setupRecyclerView() {
        rideAdapter = RideAdapter { ride ->
            showJoinConfirmation(ride)
        }

        binding.rvRides.apply {
            adapter = rideAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun observeStates() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.ridesState.collect { state ->
                when (state) {
                    is RidesUiState.Loading -> showLoading()
                    is RidesUiState.Success -> showRides(state.rides)
                    is RidesUiState.Empty -> showEmpty()
                    is RidesUiState.Error -> showError(state.message)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.joinRideState.collect { state ->
                when (state) {
                    is JoinRideState.Success -> {
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                        viewModel.resetJoinState()
                    }
                    is JoinRideState.Error -> {
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                        viewModel.resetJoinState()
                    }
                    else -> {}
                }
            }
        }
    }

    private fun setupListeners() {
        binding.ivBack.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun showLoading() {
        binding.rvRides.visibility = View.GONE
        binding.progressBar?.visibility = View.VISIBLE
        binding.tvEmptyMessage?.visibility = View.GONE
    }

    private fun showRides(rides: List<SharedRide>) {
        binding.rvRides.visibility = View.VISIBLE
        binding.progressBar?.visibility = View.GONE
        binding.tvEmptyMessage?.visibility = View.GONE
        rideAdapter.submitList(rides)
    }

    private fun showEmpty() {
        binding.rvRides.visibility = View.GONE
        binding.progressBar?.visibility = View.GONE
        binding.tvEmptyMessage?.visibility = View.VISIBLE
        binding.tvEmptyMessage?.text = "No shared rides available nearby"
    }

    private fun showError(message: String) {
        binding.rvRides.visibility = View.GONE
        binding.progressBar?.visibility = View.GONE
        binding.tvEmptyMessage?.visibility = View.VISIBLE
        binding.tvEmptyMessage?.text = message
    }

    private fun showJoinConfirmation(ride: SharedRide) {
        AlertDialog.Builder(requireContext())
            .setTitle("Join Shared Ride")
            .setMessage(
                "Join ${ride.driverName}'s ride?\n\n" +
                        "From: ${ride.pickupLocation}\n" +
                        "To: ${ride.dropLocation}\n" +
                        "Fare: ${ride.fareText}\n" +
                        "Leaves in: ${ride.leavesInText}"
            )
            .setPositiveButton("Join") { _, _ ->
                viewModel.joinRide(ride)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}