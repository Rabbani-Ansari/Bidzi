package com.bidzi.app

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup


// Fragment
import android.app.DatePickerDialog
import android.app.TimePickerDialog


import com.bidzi.app.databinding.FragmentScheduleRideBinding

import java.util.*


import androidx.navigation.fragment.findNavController
import com.bidzi.app.adapter.PlaceSuggestionAdapter


import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location

import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.inputmethod.InputMethodManager

import android.widget.Toast
import androidx.core.app.ActivityCompat

import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bidzi.app.adapter.PlaceSuggestion
import com.bidzi.app.helper.DistanceCalculator
import com.bidzi.app.helper.PlacesRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.location.LocationManager
import android.os.Looper
import androidx.activity.result.IntentSenderRequest
import com.bidzi.app.adapter.PlaceType
import com.bidzi.app.adapter.VehicleType
import com.bidzi.app.adapter.VehicleTypeAdapter
import com.bidzi.app.databinding.FragmentRideRequestBinding
import com.bidzi.app.supabase.RideBookingInsert
import com.bidzi.app.supabase.ScheduledRideBookingInsert
import io.github.jan.supabase.postgrest.from
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.math.roundToInt

class ScheduleRideFragment : Fragment() {

    private var _binding: FragmentScheduleRideBinding? = null
    private val binding get() = _binding!!

    private val placesApiKey by lazy { getString(R.string.google_maps_key) }
    private lateinit var placesRepository: PlacesRepository
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Adapters
    private lateinit var suggestionAdapter: PlaceSuggestionAdapter
    private lateinit var vehicleTypeAdapter: VehicleTypeAdapter

    // State
    private var currentLocation: Location? = null
    private var activeInputField: android.widget.EditText? = null
    private var searchJob: Job? = null
    private var pickupLocation: PlaceSuggestion? = null
    private var dropLocation: PlaceSuggestion? = null
    private var currentVehicleType: VehicleType? = null
    private var distanceInKm: Double = 0.0
    private var suggestedBidMin: Int = 0
    private var suggestedBidMax: Int = 0
    private var selectedDate: String = ""
    private var selectedTime: String = "9:00 AM"
    private var selectedCalendar: Calendar = Calendar.getInstance()
    private var isSchedulingRide = false

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val BASE_FARE_PER_KM = 10.0
    }

    // ActivityResultLaunchers
    private val pickupMapLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                handleMapResult(data, isPickup = true)
            }
        }
    }

    private val dropMapLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                handleMapResult(data, isPickup = false)
            }
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            checkLocationServicesAndFetchLocation()
        } else {
            Toast.makeText(
                requireContext(),
                "Location permission is required to use current location",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val locationSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            fetchCurrentLocation()
        } else {
            Toast.makeText(
                requireContext(),
                "Please enable location to use current location feature",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScheduleRideBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRepository()
        setupRecyclerView()
        setupListeners()
        requestLocationPermission()
        setupVehicleTypes()
        initializeDateTime()
        updateScheduleButtonText()
    }

    private fun setupRepository() {
        placesRepository = PlacesRepository(requireContext(), placesApiKey)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
    }

    private fun setupRecyclerView() {
        suggestionAdapter = PlaceSuggestionAdapter(
            onPlaceSelected = { suggestion ->
                onPlaceSelected(suggestion)
            },
            onCurrentLocationClicked = {
                handleCurrentLocationClick()
            }
        )

        binding.suggestionsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = suggestionAdapter
        }
    }

    private fun setupVehicleTypes() {
        val vehicles = listOf(
            VehicleType("bike", "Bike", R.drawable.motorbike, 0.7),
            VehicleType("auto", "Auto", R.drawable.tuk_tuk2, 1.0),
            VehicleType("car", "Car", R.drawable.taxi, 1.5)
        )

        vehicleTypeAdapter = VehicleTypeAdapter(vehicles) { selectedVehicle ->
            currentVehicleType = selectedVehicle
            updatePriceEstimate()
        }

        binding.vehicleTypeRecyclerView.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = vehicleTypeAdapter
        }

        // Auto-select Auto by default
        vehicleTypeAdapter.selectDefaultVehicle(1)
    }

    private fun initializeDateTime() {
        // Initialize with current time + 1 hour minimum
        selectedCalendar.add(Calendar.HOUR_OF_DAY, 1)
        updateTimeDisplay()
    }

    private fun setupListeners() {
        // Back button
        binding.ivBack.setOnClickListener {
            findNavController().navigateUp()
        }

        // Date picker
        binding.etPickDate.setOnClickListener {
            showDatePicker()
        }

        // Time selection
        binding.tvSelectedTime.setOnClickListener {
            showTimePicker()
        }

        // Pickup input listeners
        binding.pickupInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString() ?: ""
                binding.pickupClearIcon.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE

                if (activeInputField == binding.pickupInput) {
                    performSearch(text)
                }
            }
        })

        binding.pickupInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                activeInputField = binding.pickupInput
                val currentText = binding.pickupInput.text.toString()
                showSuggestions()
                if (currentText.isNotEmpty()) {
                    performSearch(currentText)
                } else {
                    showDefaultSuggestions()
                }
            }
        }

        binding.pickupClearIcon.setOnClickListener {
            binding.pickupInput.text.clear()
            pickupLocation = null
            binding.pickupInput.requestFocus()
        }

        binding.pickMapIcon.setOnClickListener {
            openMapPicker(isPickup = true)
        }

        // Drop input listeners
        binding.dropInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString() ?: ""
                binding.dropClearIcon.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE

                if (activeInputField == binding.dropInput) {
                    performSearch(text)
                }
            }
        })

        binding.dropInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                activeInputField = binding.dropInput
                val currentText = binding.dropInput.text.toString()
                showSuggestions()
                if (currentText.isNotEmpty()) {
                    performSearch(currentText)
                } else {
                    showDefaultSuggestions()
                }
            }
        }

        binding.dropClearIcon.setOnClickListener {
            binding.dropInput.text.clear()
            dropLocation = null
            binding.dropInput.requestFocus()
        }

        binding.dropMapIcon.setOnClickListener {
            openMapPicker(isPickup = false)
        }

        // Bid input listener
        binding.etBidPrice.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!isSchedulingRide) {
                    updateScheduleButtonText()
                }
            }
        })

        // Quick bid options
        binding.bidOption1.setOnClickListener {
            binding.etBidPrice.setText(suggestedBidMin.toString())
        }

        binding.bidOption2.setOnClickListener {
            val avgBid = (suggestedBidMin + suggestedBidMax) / 2
            binding.etBidPrice.setText(avgBid.toString())
        }

        binding.bidOption3.setOnClickListener {
            binding.etBidPrice.setText(suggestedBidMax.toString())
        }

        // Schedule ride button
        binding.btnScheduleRide.setOnClickListener {
            if (validateInputs()) {
                setScheduleButtonState(isScheduling = true)
                createScheduledBooking()
            }
        }

        // View route button
        binding.btnViewRoute.setOnClickListener {
            if (pickupLocation != null && dropLocation != null) {
                val action = ScheduleRideFragmentDirections
                    .actionScheduleRideFragmentToRoutePreviewFragment(
                        pickupAddress = pickupLocation!!.primaryText,
                        pickupLat = pickupLocation!!.latitude!!.toFloat(),
                        pickupLng = pickupLocation!!.longitude!!.toFloat(),
                        dropAddress = dropLocation!!.primaryText,
                        dropLat = dropLocation!!.latitude!!.toFloat(),
                        dropLng = dropLocation!!.longitude!!.toFloat()
                    )
                findNavController().navigate(action)
            }
        }

        // Outside click listener
        binding.mainContentScrollView.setOnClickListener {
            hideSuggestionsAndClearFocus()
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                selectedCalendar.set(Calendar.YEAR, year)
                selectedCalendar.set(Calendar.MONTH, month)
                selectedCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

                selectedDate = "$dayOfMonth/${month + 1}/$year"
                binding.etPickDate.setText(selectedDate)
                updateScheduleButtonText()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        // Set minimum date to today
        datePickerDialog.datePicker.minDate = System.currentTimeMillis()
        datePickerDialog.show()
    }

    private fun showTimePicker() {
        val hour = selectedCalendar.get(Calendar.HOUR_OF_DAY)
        val minute = selectedCalendar.get(Calendar.MINUTE)

        val timePickerDialog = TimePickerDialog(
            requireContext(),
            { _, selectedHour, selectedMinute ->
                selectedCalendar.set(Calendar.HOUR_OF_DAY, selectedHour)
                selectedCalendar.set(Calendar.MINUTE, selectedMinute)
                updateTimeDisplay()
                updateScheduleButtonText()
            },
            hour,
            minute,
            false
        )
        timePickerDialog.show()
    }

    private fun updateTimeDisplay() {
        val hour = selectedCalendar.get(Calendar.HOUR_OF_DAY)
        val minute = selectedCalendar.get(Calendar.MINUTE)
        val amPm = if (hour >= 12) "PM" else "AM"
        val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
        selectedTime = String.format("%02d:%02d %s", displayHour, minute, amPm)
        binding.tvSelectedTime.text = selectedTime
    }

    private fun performSearch(query: String) {
        searchJob?.cancel()

        if (query.length < 2) {
            showDefaultSuggestions()
            return
        }

        suggestionAdapter.submitList(emptyList())

        searchJob = lifecycleScope.launch {
            delay(500)

            try {
                val suggestions = placesRepository.searchPlaces(query, currentLocation)

                val finalSuggestions = if (activeInputField == binding.pickupInput && currentLocation != null) {
                    listOf(placesRepository.getCurrentLocationSuggestion(currentLocation!!)) + suggestions
                } else {
                    suggestions
                }

                withContext(Dispatchers.Main) {
                    suggestionAdapter.submitList(finalSuggestions)
                }
            } catch (e: Exception) {
                Log.e("ScheduleRide", "Search error: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Search failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showDefaultSuggestions() {
        val suggestions = mutableListOf<PlaceSuggestion>()

        if (activeInputField == binding.pickupInput && currentLocation != null) {
            suggestions.add(placesRepository.getCurrentLocationSuggestion(currentLocation!!))
        }

        suggestionAdapter.submitList(suggestions)
    }

    private fun onPlaceSelected(suggestion: PlaceSuggestion) {
        activeInputField?.let { input ->
            input.setText(suggestion.primaryText)

            if (input == binding.pickupInput) {
                pickupLocation = suggestion
                binding.pickupClearIcon.visibility = View.VISIBLE
            } else {
                dropLocation = suggestion
                binding.dropClearIcon.visibility = View.VISIBLE
            }

            hideSuggestions()

            lifecycleScope.launch {
                delay(100)
                input.clearFocus()

                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(input.windowToken, 0)
            }

            if (suggestion.latitude == null || suggestion.longitude == null) {
                fetchPlaceDetails(suggestion)
            } else {
                updateDistanceAndEta()
            }
        }
    }

    private fun fetchPlaceDetails(suggestion: PlaceSuggestion) {
        lifecycleScope.launch {
            val latLng = placesRepository.getPlaceDetails(suggestion.placeId)
            latLng?.let {
                val updatedSuggestion = suggestion.copy(
                    latitude = it.latitude,
                    longitude = it.longitude
                )

                if (activeInputField == binding.pickupInput) {
                    pickupLocation = updatedSuggestion
                } else {
                    dropLocation = updatedSuggestion
                }

                updateDistanceAndEta()
            }
        }
    }

    private fun handleCurrentLocationClick() {
        when {
            ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                checkLocationServicesAndFetchLocation()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                showLocationPermissionRationale()
            }
            else -> {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    private fun showLocationPermissionRationale() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Location Permission Required")
            .setMessage("We need your location permission to set your current location as pickup point.")
            .setPositiveButton("Grant Permission") { _, _ ->
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkLocationServicesAndFetchLocation() {
        val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (isGpsEnabled || isNetworkEnabled) {
            fetchCurrentLocation()
        } else {
            requestEnableLocationServices()
        }
    }

    private fun requestEnableLocationServices() {
        val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
            10000
        ).build()

        val builder = com.google.android.gms.location.LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)

        val client = com.google.android.gms.location.LocationServices.getSettingsClient(requireActivity())
        val task = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            fetchCurrentLocation()
        }

        task.addOnFailureListener { exception ->
            if (exception is com.google.android.gms.common.api.ResolvableApiException) {
                try {
                    val intentSenderRequest = IntentSenderRequest.Builder(exception.resolution).build()
                    locationSettingsLauncher.launch(intentSenderRequest)
                } catch (sendEx: IntentSender.SendIntentException) {
                    Log.e("ScheduleRide", "Error showing location dialog: ${sendEx.message}")
                }
            }
        }
    }

    private fun fetchCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        Toast.makeText(requireContext(), "Getting your location...", Toast.LENGTH_SHORT).show()

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                onCurrentLocationReceived(location)
            } else {
                requestFreshLocation()
            }
        }.addOnFailureListener { exception ->
            Log.e("ScheduleRide", "Failed to get location: ${exception.message}")
            Toast.makeText(
                requireContext(),
                "Failed to get current location",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun requestFreshLocation() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
            5000
        ).setMaxUpdates(1).build()

        val locationCallback = object : com.google.android.gms.location.LocationCallback() {
            override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
                locationResult.lastLocation?.let { location ->
                    onCurrentLocationReceived(location)
                    fusedLocationClient.removeLocationUpdates(this)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )

        lifecycleScope.launch {
            delay(10000)
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    private fun onCurrentLocationReceived(location: Location) {
        currentLocation = location

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val geocoder = android.location.Geocoder(requireContext(), Locale.getDefault())
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)

                val readableAddress = if (!addresses.isNullOrEmpty()) {
                    formatReadableAddress(addresses[0])
                } else {
                    "Current Location"
                }

                withContext(Dispatchers.Main) {
                    val suggestion = PlaceSuggestion(
                        placeId = "current_location_${System.currentTimeMillis()}",
                        primaryText = readableAddress,
                        secondaryText = "Your current location",
                        type = PlaceType.CURRENT_LOCATION,
                        latitude = location.latitude,
                        longitude = location.longitude
                    )

                    if (activeInputField == binding.pickupInput) {
                        binding.pickupInput.setText(readableAddress)
                        pickupLocation = suggestion
                        binding.pickupClearIcon.visibility = View.VISIBLE
                    }

                    hideSuggestions()

                    lifecycleScope.launch {
                        delay(100)
                        binding.pickupInput.clearFocus()

                        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.hideSoftInputFromWindow(binding.pickupInput.windowToken, 0)
                    }

                    Toast.makeText(requireContext(), "Current location set", Toast.LENGTH_SHORT).show()
                    updateDistanceAndEta()
                }
            } catch (e: Exception) {
                Log.e("ScheduleRide", "Geocoding error: ${e.message}")
                withContext(Dispatchers.Main) {
                    handleGeocodingError(location)
                }
            }
        }
    }

    private fun formatReadableAddress(address: android.location.Address): String {
        val parts = mutableListOf<String>()

        address.featureName?.let { feature ->
            if (!feature.matches(Regex("^[A-Z0-9]{2,}\\+[A-Z0-9]{2,}.*")) &&
                !feature.contains("Unnamed", ignoreCase = true) &&
                feature != address.thoroughfare &&
                feature.length > 1) {
                parts.add(feature)
            }
        }

        address.thoroughfare?.let { road ->
            if (!parts.any { it.contains(road, ignoreCase = true) }) {
                parts.add(road)
            }
        }

        address.subLocality?.let { area ->
            if (!parts.any { it.contains(area, ignoreCase = true) }) {
                parts.add(area)
            }
        }

        address.locality?.let { city ->
            if (!parts.any { it.contains(city, ignoreCase = true) }) {
                parts.add(city)
            }
        }

        if (parts.size >= 2) {
            return parts.joinToString(", ")
        }

        address.getAddressLine(0)?.let { fullAddress ->
            val cleanedAddress = fullAddress.replace(Regex("[A-Z0-9]{2,}\\+[A-Z0-9]{2,},?\\s*"), "")

            if (cleanedAddress.isNotBlank() && cleanedAddress.length > 3) {
                val addressParts = cleanedAddress.split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() && it.length > 2 }
                    .take(3)

                if (addressParts.isNotEmpty()) {
                    return addressParts.joinToString(", ")
                }
            }
        }

        val fallbackParts = mutableListOf<String>()
        address.subLocality?.let { fallbackParts.add(it) }
        address.locality?.let { fallbackParts.add(it) }

        return if (fallbackParts.isNotEmpty()) {
            fallbackParts.joinToString(", ")
        } else {
            "Current Location"
        }
    }

    private fun handleGeocodingError(location: Location) {
        val suggestion = PlaceSuggestion(
            placeId = "current_location_${System.currentTimeMillis()}",
            primaryText = "Current Location",
            secondaryText = "Lat: ${String.format("%.4f", location.latitude)}, Lng: ${String.format("%.4f", location.longitude)}",
            type = PlaceType.CURRENT_LOCATION,
            latitude = location.latitude,
            longitude = location.longitude
        )

        if (activeInputField == binding.pickupInput) {
            binding.pickupInput.setText("Current Location")
            pickupLocation = suggestion
            binding.pickupClearIcon.visibility = View.VISIBLE
        }

        hideSuggestions()
        Toast.makeText(requireContext(), "Location set (address unavailable)", Toast.LENGTH_SHORT).show()
        updateDistanceAndEta()
    }

    private fun openMapPicker(isPickup: Boolean) {
        val intent = Intent(requireContext(), MapActivity::class.java).apply {
            putExtra(MapActivity.EXTRA_LOCATION_TYPE, if (isPickup) "pickup" else "drop")
        }

        try {
            if (isPickup) {
                pickupMapLauncher.launch(intent)
            } else {
                dropMapLauncher.launch(intent)
            }
        } catch (e: Exception) {
            Log.e("ScheduleRide", "Error launching map: ${e.message}", e)
            Toast.makeText(requireContext(), "Error opening map", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleMapResult(data: Intent, isPickup: Boolean) {
        val address = data.getStringExtra(MapActivity.EXTRA_ADDRESS)
        val latitude = data.getDoubleExtra(MapActivity.EXTRA_LATITUDE, 0.0)
        val longitude = data.getDoubleExtra(MapActivity.EXTRA_LONGITUDE, 0.0)

        if (address != null && latitude != 0.0 && longitude != 0.0) {
            val suggestion = PlaceSuggestion(
                placeId = "map_selected_${System.currentTimeMillis()}",
                primaryText = address,
                secondaryText = "Selected from map",
                type = PlaceType.MAP_SELECTED,
                latitude = latitude,
                longitude = longitude
            )

            if (isPickup) {
                binding.pickupInput.setText(address)
                pickupLocation = suggestion
                binding.pickupClearIcon.visibility = View.VISIBLE
            } else {
                binding.dropInput.setText(address)
                dropLocation = suggestion
                binding.dropClearIcon.visibility = View.VISIBLE
            }

            updateDistanceAndEta()
        }
    }

    private fun updateDistanceAndEta() {
        val pickup = pickupLocation
        val drop = dropLocation

        if (pickup?.latitude != null && pickup.longitude != null &&
            drop?.latitude != null && drop.longitude != null
        ) {
            lifecycleScope.launch {
                try {
                    val startLatLng = com.google.android.gms.maps.model.LatLng(
                        pickup.latitude!!,
                        pickup.longitude!!
                    )
                    val endLatLng = com.google.android.gms.maps.model.LatLng(
                        drop.latitude!!,
                        drop.longitude!!
                    )

                    val distanceInMeters = DistanceCalculator.calculateDistance(startLatLng, endLatLng)
                    distanceInKm = distanceInMeters / 1000.0
                    val etaMinutes = DistanceCalculator.calculateETA(distanceInMeters)

                    val distanceText = DistanceCalculator.formatDistance(distanceInMeters)
                    val etaText = DistanceCalculator.formatETA(etaMinutes)

                    withContext(Dispatchers.Main) {
                        binding.distanceEtaText.apply {
                            text = "Distance: $distanceText  •  ETA: $etaText"
                            visibility = View.VISIBLE
                        }

                        binding.btnViewRoute.visibility = View.VISIBLE
                        updatePriceEstimate()
                    }
                } catch (e: Exception) {
                    Log.e("ScheduleRide", "Error calculating distance: ${e.message}")
                }
            }
        } else {
            binding.distanceEtaText.visibility = View.GONE
            binding.btnViewRoute.visibility = View.GONE
        }
    }

    private fun updatePriceEstimate() {
        if (distanceInKm == 0.0) return

        val multiplier = currentVehicleType?.priceMultiplier ?: 1.0
        val vehicleName = currentVehicleType?.name ?: "vehicle"

        val baseFare = distanceInKm * BASE_FARE_PER_KM
        val estimatedFare = (baseFare * multiplier).toInt()

        suggestedBidMin = (estimatedFare * 0.8).toInt()
        suggestedBidMax = (estimatedFare * 1.2).toInt()

        binding.tvSuggestedRange.text =
            "Suggested bid for $vehicleName: ₹$suggestedBidMin - ₹$suggestedBidMax (${String.format("%.1f", distanceInKm)} km)"

        updateQuickBidOptions()
        updateScheduleButtonText()

        Log.d("ScheduleRide", "💰 Price updated: ₹$suggestedBidMin-$suggestedBidMax for $vehicleName (${distanceInKm}km × $multiplier)")
    }

    private fun updateQuickBidOptions() {
        if (suggestedBidMin > 0 && suggestedBidMax > 0) {
            binding.quickBidOptions.visibility = View.VISIBLE

            val avgBid = (suggestedBidMin + suggestedBidMax) / 2

            binding.bidOption1.text = "₹$suggestedBidMin"
            binding.bidOption2.text = "₹$avgBid"
            binding.bidOption3.text = "₹$suggestedBidMax"
        } else {
            binding.quickBidOptions.visibility = View.GONE
        }
    }

    private fun updateScheduleButtonText() {
        val bidValue = binding.etBidPrice.text.toString()
        if (bidValue.isNotEmpty() && bidValue.toDoubleOrNull() ?: 0.0 > 0) {
            binding.btnScheduleRide.text = "Schedule Ride - ₹$bidValue"
        } else {
            binding.btnScheduleRide.text = "Schedule Ride"
        }
    }

    private fun setScheduleButtonState(isScheduling: Boolean) {
        isSchedulingRide = isScheduling
        binding.btnScheduleRide.isEnabled = !isScheduling
        binding.scheduleProgressBar.visibility = if (isScheduling) View.VISIBLE else View.GONE

        if (isScheduling) {
            binding.btnScheduleRide.text = "Scheduling..."
        } else {
            updateScheduleButtonText()
        }
    }

    private fun createScheduledBooking() {
        lifecycleScope.launch {
            try {
                val firebaseUserId = "user123" // Replace with actual user ID
                val bookingId = UUID.randomUUID().toString()

                // Generate current timestamp in ISO 8601 format
                val currentTimestamp = SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
                    Locale.getDefault()
                ).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date())

                // Generate scheduled timestamp from selectedCalendar
                val scheduledTimestamp = SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
                    Locale.getDefault()
                ).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(selectedCalendar.time)

                // Get bid value from input
                val bidValue = binding.etBidPrice.text.toString().toDoubleOrNull() ?: 0.0

                // Create scheduled booking object
                val scheduledBookingInsert = ScheduledRideBookingInsert(
                    id = bookingId,
                    userId = firebaseUserId,
                    pickupAddress = pickupLocation!!.primaryText,
                    pickupLat = pickupLocation!!.latitude!!,
                    pickupLng = pickupLocation!!.longitude!!,
                    dropAddress = dropLocation!!.primaryText,
                    dropLat = dropLocation!!.latitude!!,
                    dropLng = dropLocation!!.longitude!!,
                    vehicleType = currentVehicleType!!.id,
                    distanceKm = distanceInKm,
                    bid = bidValue.roundToInt(),
                    note = binding.noteInput.text.toString().takeIf { it.isNotBlank() },
                    scheduledDate = selectedDate,
                    scheduledTime = selectedTime,
                    scheduledTimestamp = scheduledTimestamp,
                    createdAt = currentTimestamp
                )

                // Insert into Supabase
                MyApplication.supabase.from("scheduled_ride_bookings")
                    .insert(scheduledBookingInsert)

                Log.d("ScheduleRide", "Scheduled booking created with ID: $bookingId")
                Log.d("ScheduleRide", "Scheduled for: $scheduledTimestamp")

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "Ride scheduled for $selectedDate at $selectedTime",
                        Toast.LENGTH_LONG
                    ).show()

                    // Navigate back or to confirmation screen
                    findNavController().navigateUp()
                }

            } catch (e: Exception) {
                Log.e("ScheduleRide", "Error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    setScheduleButtonState(isScheduling = false)
                }
            }
        }
    }

    private fun validateInputs(): Boolean {
        // Validate date selection
        if (selectedDate.isEmpty()) {
            Toast.makeText(requireContext(), "Please select a date", Toast.LENGTH_SHORT).show()
            binding.etPickDate.requestFocus()
            return false
        }

        // Validate scheduled time is in future
        val now = Calendar.getInstance()
        if (selectedCalendar.timeInMillis < now.timeInMillis) {
            Toast.makeText(
                requireContext(),
                "Scheduled time must be in the future",
                Toast.LENGTH_SHORT
            ).show()
            return false
        }

        // Validate minimum advance booking (at least 30 minutes from now)
        val minTime = Calendar.getInstance().apply {
            add(Calendar.MINUTE, 30)
        }
        if (selectedCalendar.timeInMillis < minTime.timeInMillis) {
            Toast.makeText(
                requireContext(),
                "Please schedule at least 30 minutes in advance",
                Toast.LENGTH_SHORT
            ).show()
            return false
        }

        // Validate pickup location
        if (binding.pickupInput.text.isNullOrEmpty()) {
            binding.pickupInput.error = "Please enter pickup location"
            binding.pickupInput.requestFocus()
            return false
        }

        if (pickupLocation == null) {
            binding.pickupInput.error = "Please select a valid pickup location"
            binding.pickupInput.requestFocus()
            return false
        }

        // Validate drop location
        if (binding.dropInput.text.isNullOrEmpty()) {
            binding.dropInput.error = "Please enter drop location"
            binding.dropInput.requestFocus()
            return false
        }

        if (dropLocation == null) {
            binding.dropInput.error = "Please select a valid drop location"
            binding.dropInput.requestFocus()
            return false
        }

        // Validate bid input
        val bidValue = binding.etBidPrice.text.toString()
        if (bidValue.isEmpty()) {
            binding.etBidPrice.error = "Please enter a bid amount"
            binding.etBidPrice.requestFocus()
            return false
        }

        val bidAmount = bidValue.toDoubleOrNull()
        if (bidAmount == null || bidAmount <= 0) {
            binding.etBidPrice.error = "Please enter a valid bid amount"
            binding.etBidPrice.requestFocus()
            return false
        }

        // Validate vehicle type selection
        if (currentVehicleType == null) {
            Toast.makeText(requireContext(), "Please select a vehicle type", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun showSuggestions() {
        binding.suggestionsRecyclerView.visibility = View.VISIBLE
    }

    private fun hideSuggestions() {
        binding.suggestionsRecyclerView.visibility = View.GONE
    }

    private fun hideSuggestionsAndClearFocus() {
        hideSuggestions()
        binding.pickupInput.clearFocus()
        binding.dropInput.clearFocus()

        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    private fun requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            getCurrentLocation()
        }
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                currentLocation = it
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchJob?.cancel()
        _binding = null
    }
}