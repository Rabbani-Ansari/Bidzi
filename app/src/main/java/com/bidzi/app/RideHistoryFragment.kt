package com.bidzi.app

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup





import androidx.recyclerview.widget.LinearLayoutManager
import com.bidzi.app.databinding.FragmentRideHistoryBinding


class RideHistoryFragment : Fragment() {

    private lateinit var binding: FragmentRideHistoryBinding
    private lateinit var rideHistoryAdapter: RideHistoryAdapter
    private val rideHistoryList = mutableListOf<RideHistory>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentRideHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        loadRideHistory()
        setupClickListeners()
    }

    private fun setupRecyclerView() {
        rideHistoryAdapter = RideHistoryAdapter(rideHistoryList) { ride ->
            onRideClick(ride)
        }

        binding.rvRideHistory.apply {
            adapter = rideHistoryAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun loadRideHistory() {
        rideHistoryList.clear()
        rideHistoryList.addAll(
            listOf(
                RideHistory(
                    id = 1,
                    destination = "Phoenix Mall",
                    source = "MG Road Metro",
                    fromLocation = "MG Road Metro",
                    withPassengers = null,
                    dateTime = "Oct 15, 2:45 PM",
                    distance = "5.2 km",
                    duration = "18 min",
                    fare = 80,
                    rating = 4.8,
                    isShared = false
                ),
                RideHistory(
                    id = 2,
                    destination = "Railway Station",
                    source = "Home",
                    fromLocation = "Home",
                    withPassengers = listOf("Priya", "Amit"),
                    dateTime = "Oct 14, 6:20 PM",
                    distance = "3.8 km",
                    duration = "12 min",
                    fare = 60,
                    rating = 5.0,
                    isShared = true
                ),
                RideHistory(
                    id = 3,
                    destination = "City Center",
                    source = "Office",
                    fromLocation = "Office",
                    withPassengers = listOf("Sneha"),
                    dateTime = "Oct 13, 1:15 PM",
                    distance = "7.5 km",
                    duration = "22 min",
                    fare = 55,
                    rating = 4.5,
                    isShared = true
                )
            )
        )
        rideHistoryAdapter.notifyDataSetChanged()
    }

    private fun setupClickListeners() {
        binding.ivBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun onRideClick(ride: RideHistory) {
        // Handle ride click - navigate to ride details or show dialog
        // Example: Navigation.findNavController(binding.root).navigate(...)
    }
}