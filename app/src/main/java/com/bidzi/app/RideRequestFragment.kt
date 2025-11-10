package com.bidzi.app

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent

import com.bidzi.app.adapter.PlaceType
import com.bidzi.app.adapter.VehicleType
import com.bidzi.app.adapter.VehicleTypeAdapter
import com.bidzi.app.databinding.FragmentRideRequestBinding
import com.bidzi.app.supabase.RideBookingInsert
import io.github.jan.supabase.postgrest.from
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.math.roundToInt

import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import androidx.activity.result.IntentSenderRequest
import android.content.IntentSender
import android.location.LocationManager
import android.net.Uri
import android.os.Looper
import android.provider.Settings
import android.widget.EditText
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.bidzi.app.auth.SessionUser

import com.google.android.gms.location.FusedLocationProviderClient
import kotlinx.coroutines.Dispatchers

class RideRequestFragment : Fragment() {

    private var _binding: FragmentRideRequestBinding? = null
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

    // NEW: State for ride preferences
    private var passengerCount: Int = 1
    private var luggageCount: Int = 0

    // NEW: State to track if a request is in progress
    private var isRequestingRide = false

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val SEARCH_DELAY_MS = 500L
        private const val BASE_FARE_PER_KM = 10.0
    }

    // ActivityResultLaunchers
    private val pickupMapLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        Log.d("RideRequest", "📍 Pickup map result received, resultCode: ${result.resultCode}")
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                Log.d("RideRequest", "📍 Processing pickup map data")
                handleMapResult(data, isPickup = true)
            }
        }
    }

    private val dropMapLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        Log.d("RideRequest", "📍 Drop map result received, resultCode: ${result.resultCode}")
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                Log.d("RideRequest", "📍 Processing drop map data")
                handleMapResult(data, isPickup = false)
            }
        }
    }

    // Location permission launcher
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d("RideRequest", "✅ Location permission granted")
            checkLocationServicesAndFetchLocation()
        } else {
            Log.d("RideRequest", "❌ Location permission denied")
            Toast.makeText(
                requireContext(),
                "Location permission is required to use current location",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Location settings launcher
    private val locationSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Log.d("RideRequest", "✅ Location enabled")
            fetchCurrentLocation()
        } else {
            Log.d("RideRequest", "❌ User declined to enable location")
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
        _binding = FragmentRideRequestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRepository()
        setupRecyclerView()
        setupListeners()
        setupOutsideClickListener()
        requestLocationPermission()
        setupVehicleTypes()

        // NEW: Setup ride preferences
        setupRidePreferences()

        // NEW: Set initial button text
        updateRequestButtonText()
    }

    private fun createBooking() {
        lifecycleScope.launch {
            try {
                val firebaseUserId = SessionUser.currentUserId
                val bookingId = UUID.randomUUID().toString()

                // Generate timestamp in ISO 8601 format
                val currentTimestamp = SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
                    Locale.getDefault()
                ).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date())

                // Get bid value from input
                val bidValue = binding.bidInput.text.toString().toDoubleOrNull() ?: 0.0

                // Create booking object with created_at and ride preferences
                val bookingInsert = RideBookingInsert(
                    id = bookingId,
                    userId = firebaseUserId.toString(),
                    pickupAddress = pickupLocation!!.primaryText,
                    pickupLat = pickupLocation!!.latitude!!,
                    pickupLng = pickupLocation!!.longitude!!,
                    dropAddress = dropLocation!!.primaryText,
                    dropLat = dropLocation!!.latitude!!,
                    dropLng = dropLocation!!.longitude!!,
                    vehicleType = currentVehicleType!!.id,
                    distanceKm = distanceInKm,
                    bid = bidValue.roundToInt(), // FIX: Convert Double to Int with rounding
                    note = binding.noteInput.text.toString().takeIf { it.isNotBlank() },
                    createdAt = currentTimestamp,
                    // NEW: Include ride preferences
                    passengers = passengerCount,
                    luggage = luggageCount
                )

                // Insert into Supabase
                MyApplication.supabase.from("ride_bookings")
                    .insert(bookingInsert)

                Log.d("RideRequest", "Booking created with ID: $bookingId")
                Log.d("RideRequest", "Created at: $currentTimestamp")
                Log.d("RideRequest", "Passengers: $passengerCount, Luggage: $luggageCount")

                // Convert to RideBooking and navigate
                val rideBooking = bookingInsert.toRideBooking()

                val action = RideRequestFragmentDirections
                    .actionRideRequestFragmentToFindingDriverFragment(
                        rideBooking = rideBooking
                    )
                findNavController().navigate(action)

            } catch (e: Exception) {
                Log.e("RideRequest", "Error: ${e.message}", e)
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()

                // IMPORTANT: Reset the button state on failure so the user can try again
                setRequestButtonState(isRequesting = false)
            }
        }
    }

    // Setup in your Activity/Fragment
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

        // Auto-select Auto (middle option) by default
        vehicleTypeAdapter.selectDefaultVehicle(1) // Index 1 = Auto
    }

    // NEW: Setup ride preferences
    private fun setupRidePreferences() {
        // Initialize passenger count display
        binding.passengerCount.text = passengerCount.toString()

        // Initialize luggage count display
        binding.luggageCount.text = luggageCount.toString()

        // Setup passenger count controls
        binding.passengerMinus.setOnClickListener {
            if (passengerCount > 1) {
                passengerCount--
                binding.passengerCount.text = passengerCount.toString()
                updatePriceEstimate() // Price might change based on passenger count
            }
        }

        binding.passengerPlus.setOnClickListener {
            // Limit to reasonable number of passengers based on vehicle type
            val maxPassengers = when (currentVehicleType?.id) {
                "bike" -> 1
                "auto" -> 3
                "car" -> 4
                else -> 4
            }

            if (passengerCount < maxPassengers) {
                passengerCount++
                binding.passengerCount.text = passengerCount.toString()
                updatePriceEstimate() // Price might change based on passenger count
            } else {
                Toast.makeText(requireContext(), "Maximum $maxPassengers passengers allowed for this vehicle", Toast.LENGTH_SHORT).show()
            }
        }

        // Setup luggage count controls
        binding.luggageMinus.setOnClickListener {
            if (luggageCount > 0) {
                luggageCount--
                binding.luggageCount.text = luggageCount.toString()
                updatePriceEstimate() // Price might change based on luggage count
            }
        }

        binding.luggagePlus.setOnClickListener {
            // Limit to reasonable number of luggage based on vehicle type
            val maxLuggage = when (currentVehicleType?.id) {
                "bike" -> 1
                "auto" -> 2
                "car" -> 3
                else -> 3
            }

            if (luggageCount < maxLuggage) {
                luggageCount++
                binding.luggageCount.text = luggageCount.toString()
                updatePriceEstimate() // Price might change based on luggage count
            } else {
                Toast.makeText(requireContext(), "Maximum $maxLuggage luggage allowed for this vehicle", Toast.LENGTH_SHORT).show()
            }
        }

        // Setup preferences toggle
        binding.preferencesToggle.setOnClickListener {
            // You could expand the preferences section here if needed
            Toast.makeText(requireContext(), "Preferences section expanded", Toast.LENGTH_SHORT).show()
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

                        // Update price estimate
                        updatePriceEstimate()
                    }
                } catch (e: Exception) {
                    Log.e("RideRequest", "Error calculating distance: ${e.message}")
                }
            }
        } else {
            binding.distanceEtaText.visibility = View.GONE
            binding.btnViewRoute.visibility = View.GONE
        }
    }

    private fun updatePriceEstimate() {
        if (distanceInKm == 0.0) {
            Log.d("RideRequest", "⚠️ Distance is 0, skipping price update")
            return
        }

        val multiplier = currentVehicleType?.priceMultiplier ?: 1.0
        val vehicleName = currentVehicleType?.name ?: "vehicle"

        // NEW: Adjust price based on passenger and luggage count
        val passengerMultiplier = when {
            passengerCount > 1 && currentVehicleType?.id == "bike" -> 1.5 // Extra passenger on bike costs more
            passengerCount > 2 -> 1.2 // Extra passengers cost more
            else -> 1.0
        }

        val luggageMultiplier = when {
            luggageCount > 2 -> 1.1 // Extra luggage costs slightly more
            else -> 1.0
        }

        val baseFare = distanceInKm * BASE_FARE_PER_KM
        val estimatedFare = (baseFare * multiplier * passengerMultiplier * luggageMultiplier).toInt()

        suggestedBidMin = (estimatedFare * 0.8).toInt()
        suggestedBidMax = (estimatedFare * 1.2).toInt()

        binding.suggestedPriceText.text =
            "Suggested bid for $vehicleName: ₹$suggestedBidMin - ₹$suggestedBidMax (${String.format("%.1f", distanceInKm)} km)"

        // Update quick bid options
        updateQuickBidOptions()

        // NEW: Update button text when price is calculated
        updateRequestButtonText()

        Log.d("RideRequest", "💰 Price updated: ₹$suggestedBidMin-$suggestedBidMax for $vehicleName (${distanceInKm}km × $multiplier × $passengerMultiplier × $luggageMultiplier)")
    }

    private fun updateQuickBidOptions() {
        if (suggestedBidMin > 0 && suggestedBidMax > 0) {
            binding.quickBidOptions.visibility = View.VISIBLE

            // Calculate three bid options: min, average, max
            val avgBid = (suggestedBidMin + suggestedBidMax) / 2

            binding.bidOption1.text = "₹$suggestedBidMin"
            binding.bidOption2.text = "₹$avgBid"
            binding.bidOption3.text = "₹$suggestedBidMax"

            // Set click listeners for quick bid options
            binding.bidOption1.setOnClickListener {
                binding.bidInput.setText(suggestedBidMin.toString())
            }

            binding.bidOption2.setOnClickListener {
                binding.bidInput.setText(avgBid.toString())
            }

            binding.bidOption3.setOnClickListener {
                binding.bidInput.setText(suggestedBidMax.toString())
            }
        } else {
            binding.quickBidOptions.visibility = View.GONE
        }
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

    // Handle received location with better address formatting
    private fun onCurrentLocationReceived(location: Location) {
        currentLocation = location

        // Reverse geocode to get address
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

                    // Set as pickup location
                    if (activeInputField == binding.pickupInput) {
                        binding.pickupInput.setText(readableAddress)
                        pickupLocation = suggestion
                        binding.pickupClearIcon.visibility = View.VISIBLE
                        Log.d("RideRequest", "✅ Pickup set to current location: $readableAddress")
                    }

                    hideSuggestions()

                    lifecycleScope.launch {
                        delay(100)
                        binding.pickupInput.clearFocus()

                        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.hideSoftInputFromWindow(binding.pickupInput.windowToken, 0)
                    }

                    Toast.makeText(requireContext(), "Current location set", Toast.LENGTH_SHORT).show()

                    // Trigger distance calculation if drop location exists
                    updateDistanceAndEta()
                }
            } catch (e: Exception) {
                Log.e("RideRequest", "Geocoding error: ${e.message}")
                withContext(Dispatchers.Main) {
                    handleGeocodingError(location)
                }
            }
        }
    }

    // Format readable address from Geocoder result
    private fun formatReadableAddress(address: android.location.Address): String {
        val parts = mutableListOf<String>()

        // 1. Building/premise name or feature
        address.featureName?.let { feature ->
            if (!feature.matches(Regex("^[A-Z0-9]{2,}\\+[A-Z0-9]{2,}.*")) && // Exclude Plus Codes
                !feature.contains("Unnamed", ignoreCase = true) &&
                feature != address.thoroughfare &&
                feature.length > 1) {
                parts.add(feature)
            }
        }

        // 2. Street/road name
        address.thoroughfare?.let { road ->
            if (!parts.any { it.contains(road, ignoreCase = true) }) {
                parts.add(road)
            }
        }

        // 3. Sub-locality (neighborhood/area)
        address.subLocality?.let { area ->
            if (!parts.any { it.contains(area, ignoreCase = true) }) {
                parts.add(area)
            }
        }

        // 4. Locality (city/town)
        address.locality?.let { city ->
            if (!parts.any { it.contains(city, ignoreCase = true) }) {
                parts.add(city)
            }
        }

        // If we have good parts, return them
        if (parts.size >= 2) {
            return parts.joinToString(", ")
        }

        // Fallback: Try to extract from full address line
        address.getAddressLine(0)?.let { fullAddress ->
            // Remove Plus Code if present
            val cleanedAddress = fullAddress.replace(Regex("[A-Z0-9]{2,}\\+[A-Z0-9]{2,},?\\s*"), "")

            if (cleanedAddress.isNotBlank() && cleanedAddress.length > 3) {
                // Split and take meaningful parts
                val addressParts = cleanedAddress.split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() && it.length > 2 }
                    .take(3)

                if (addressParts.isNotEmpty()) {
                    return addressParts.joinToString(", ")
                }
            }
        }

        // Last resort: Use sub-locality and locality
        val fallbackParts = mutableListOf<String>()
        address.subLocality?.let { fallbackParts.add(it) }
        address.locality?.let { fallbackParts.add(it) }

        return if (fallbackParts.isNotEmpty()) {
            fallbackParts.joinToString(", ")
        } else {
            "Current Location"
        }
    }

    // Handle geocoding error gracefully
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
            Log.d("RideRequest", "✅ Pickup set to current location (geocoding failed)")
        }

        hideSuggestions()

        Toast.makeText(
            requireContext(),
            "Location set (address unavailable)",
            Toast.LENGTH_SHORT
        ).show()

        updateDistanceAndEta()
    }

    // Handle current location click
    private fun handleCurrentLocationClick() {
        Log.d("RideRequest", "📍 Current Location clicked")

        when {
            // Check if permission is granted
            ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                Log.d("RideRequest", "✅ Permission already granted")
                checkLocationServicesAndFetchLocation()
            }
            // Show rationale if needed
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                showLocationPermissionRationale()
            }
            // Request permission
            else -> {
                Log.d("RideRequest", "🔐 Requesting location permission")
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    // Show permission rationale
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

    // Check if location services are enabled
    private fun checkLocationServicesAndFetchLocation() {
        val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (isGpsEnabled || isNetworkEnabled) {
            Log.d("RideRequest", "✅ Location services enabled")
            fetchCurrentLocation()
        } else {
            Log.d("RideRequest", "⚠️ Location services disabled, requesting enable")
            requestEnableLocationServices()
        }
    }

    // Request to enable location services
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
            Log.d("RideRequest", "✅ Location settings satisfied")
            fetchCurrentLocation()
        }

        task.addOnFailureListener { exception ->
            if (exception is com.google.android.gms.common.api.ResolvableApiException) {
                try {
                    Log.d("RideRequest", "📍 Showing location enable dialog")
                    val intentSenderRequest = IntentSenderRequest.Builder(exception.resolution).build()
                    locationSettingsLauncher.launch(intentSenderRequest)
                } catch (sendEx: IntentSender.SendIntentException) {
                    Log.e("RideRequest", "Error showing location dialog: ${sendEx.message}")
                }
            } else {
                Log.e("RideRequest", "Location settings error: ${exception.message}")
                Toast.makeText(
                    requireContext(),
                    "Unable to enable location services",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Fetch current location with progress
    private fun fetchCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        Log.d("RideRequest", "📍 Fetching current location...")

        // Show loading
        Toast.makeText(requireContext(), "Getting your location...", Toast.LENGTH_SHORT).show()

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                Log.d("RideRequest", "✅ Got location: ${location.latitude}, ${location.longitude}")
                onCurrentLocationReceived(location)
            } else {
                Log.d("RideRequest", "⚠️ Last location is null, requesting fresh location")
                requestFreshLocation()
            }
        }.addOnFailureListener { exception ->
            Log.e("RideRequest", "❌ Failed to get location: ${exception.message}")
            Toast.makeText(
                requireContext(),
                "Failed to get current location",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Request fresh location if last location is null
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
                    Log.d("RideRequest", "✅ Got fresh location: ${location.latitude}, ${location.longitude}")
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

        // Timeout after 10 seconds
        lifecycleScope.launch {
            delay(10000)
            fusedLocationClient.removeLocationUpdates(locationCallback)
            Log.d("RideRequest", "⏱️ Location request timed out")
        }
    }

    private fun setupListeners() {
        // Back button
        binding.backButton.setOnClickListener {
            Log.d("RideRequest", "🔙 Back button clicked")
            findNavController().navigateUp()
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

        // Pickup Map Icon
        binding.pickMapIcon.setOnClickListener {
            Log.d("RideRequest", "🗺️ PICKUP MAP ICON CLICKED!")
            openMapPicker(isPickup = true)
        }

        // View Route button
        binding.btnViewRoute.setOnClickListener {
            if (pickupLocation != null && dropLocation != null) {
                val action = RideRequestFragmentDirections
                    .actionRideRequestFragmentToRoutePreviewFragment(
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

        // Drop Map Icon
        binding.dropMapIcon.setOnClickListener {
            Log.d("RideRequest", "🗺️ DROP MAP ICON CLICKED!")
            openMapPicker(isPickup = false)
        }

        // Note character counter
        binding.noteInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val count = s?.length ?: 0
                binding.noteCharCount.text = "$count/200"
                if (count > 200) {
                    binding.noteInput.error = "Maximum 200 characters allowed"
                }
            }
        })

        // NEW: Add a listener to the bid input to update the button text
        binding.bidInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                // Only update the button text if we are not in the "requesting" state
                if (!isRequestingRide) {
                    updateRequestButtonText()
                }
            }
        })

        // MODIFIED: Request button listener
        binding.requestButton.setOnClickListener {
            if (validateInputs()) {
                // Set the requesting state before starting the process
                setRequestButtonState(isRequesting = true)
                createBooking()
            }
        }
    }

    /**
     * Updates the request button's text based on the current bid input.
     */
    private fun updateRequestButtonText() {
        val bidValue = binding.bidInput.text.toString()
        if (bidValue.isNotEmpty() && bidValue.toDoubleOrNull() ?: 0.0 > 0) {
            binding.requestButton.text = "Request Ride for ₹$bidValue"
        } else {
            binding.requestButton.text = "Request Ride"
        }
    }

    /**
     * Sets the visual state of the request button and progress bar.
     * @param isRequesting True to show the loading state, false for the default state.
     */
    private fun setRequestButtonState(isRequesting: Boolean) {
        isRequestingRide = isRequesting
        binding.requestButton.isEnabled = !isRequesting
        binding.requestProgressBar.visibility = if (isRequesting) View.VISIBLE else View.GONE

        if (isRequesting) {
            binding.requestButton.text = "Requesting..."
        } else {
            // Revert to the price-based text
            updateRequestButtonText()
        }
    }

    private fun setupOutsideClickListener() {
        binding.mainContentScrollView.setOnClickListener {
            hideSuggestionsAndClearFocus()
        }
    }

    // Open map picker
    private fun openMapPicker(isPickup: Boolean) {
        Log.d("RideRequest", "🗺️ Opening map picker for ${if (isPickup) "PICKUP" else "DROP"}")

        val intent = Intent(requireContext(), MapActivity::class.java).apply {
            putExtra(MapActivity.EXTRA_LOCATION_TYPE, if (isPickup) "pickup" else "drop")
        }

        try {
            if (isPickup) {
                Log.d("RideRequest", "🚀 Launching pickup map launcher")
                pickupMapLauncher.launch(intent)
            } else {
                Log.d("RideRequest", "🚀 Launching drop map launcher")
                dropMapLauncher.launch(intent)
            }
        } catch (e: Exception) {
            Log.e("RideRequest", "❌ Error launching map: ${e.message}", e)
            Toast.makeText(requireContext(), "Error opening map", Toast.LENGTH_SHORT).show()
        }
    }

    // Handle map result
    private fun handleMapResult(data: Intent, isPickup: Boolean) {
        Log.d("RideRequest", "📥 Handling map result for ${if (isPickup) "PICKUP" else "DROP"}")

        val address = data.getStringExtra(MapActivity.EXTRA_ADDRESS)
        val latitude = data.getDoubleExtra(MapActivity.EXTRA_LATITUDE, 0.0)
        val longitude = data.getDoubleExtra(MapActivity.EXTRA_LONGITUDE, 0.0)

        Log.d("RideRequest", "📍 Address: $address")
        Log.d("RideRequest", "📍 Lat: $latitude, Lng: $longitude")

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
                Log.d("RideRequest", "✅ Pickup location set from map: $address")
            } else {
                binding.dropInput.setText(address)
                dropLocation = suggestion
                binding.dropClearIcon.visibility = View.VISIBLE
                Log.d("RideRequest", "✅ Drop location set from map: $address")
            }

            // Calculate distance if both locations are set
            updateDistanceAndEta()
        } else {
            Log.e("RideRequest", "❌ Invalid map data received")
            Toast.makeText(requireContext(), "Invalid location data", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performSearch(query: String) {
        searchJob?.cancel()

        if (query.length < 2) {
            showDefaultSuggestions()
            return
        }

        suggestionAdapter.submitList(emptyList())

        searchJob = lifecycleScope.launch {
            delay(SEARCH_DELAY_MS)

            try {
                val suggestions = placesRepository.searchPlaces(query, currentLocation)

                val finalSuggestions = if (activeInputField == binding.pickupInput && currentLocation != null) {
                    listOf(placesRepository.getCurrentLocationSuggestion(currentLocation!!)) + suggestions
                } else {
                    suggestions
                }

                withContext(Dispatchers.Main) {
                    suggestionAdapter.submitList(finalSuggestions)
                    Log.d("RideRequest", "Showing ${finalSuggestions.size} suggestions for query: $query")
                }
            } catch (e: Exception) {
                Log.e("RideRequest", "Search error: ${e.message}")
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

    private fun validateInputs(): Boolean {
        if (binding.pickupInput.text.isNullOrEmpty()) {
            binding.pickupInput.error = "Please enter pickup location"
            binding.pickupInput.requestFocus()
            return false
        }

        if (binding.dropInput.text.isNullOrEmpty()) {
            binding.dropInput.error = "Please enter drop location"
            binding.dropInput.requestFocus()
            return false
        }

        if (pickupLocation == null) {
            binding.pickupInput.error = "Please select a valid pickup location"
            binding.pickupInput.requestFocus()
            return false
        }

        if (dropLocation == null) {
            binding.dropInput.error = "Please select a valid drop location"
            binding.dropInput.requestFocus()
            return false
        }

        // Validate bid input
        val bidValue = binding.bidInput.text.toString()
        if (bidValue.isEmpty()) {
            binding.bidInput.error = "Please enter a bid amount"
            binding.bidInput.requestFocus()
            return false
        }

        val bidAmount = bidValue.toDoubleOrNull()
        if (bidAmount == null || bidAmount <= 0) {
            binding.bidInput.error = "Please enter a valid bid amount"
            binding.bidInput.requestFocus()
            return false
        }

        return true
    }

    private fun requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            getCurrentLocation()
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
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
                binding.pickupInput.hint = "Current Location"
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation()
            }
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        searchJob?.cancel()
        _binding = null
    }
}




