package com.bidzi.app

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat


import android.Manifest
import android.content.pm.PackageManager
import android.location.Location

import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions

import androidx.activity.result.contract.ActivityResultContracts

import android.util.Log

import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult

import com.google.android.gms.location.Priority


import android.location.LocationManager

import android.os.Looper

import android.widget.Toast



import android.content.Intent

import android.provider.Settings

import androidx.appcompat.app.AlertDialog

import com.google.android.gms.location.SettingsClient
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.tasks.Task


import android.location.Geocoder

import android.os.Handler

import android.widget.Button
import android.widget.ImageView
import android.widget.TextView

import java.util.Locale



import com.google.gson.Gson
import okhttp3.*
import java.io.IOException

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

import android.location.Address

import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton


import android.text.Editable
import android.text.TextWatcher

import android.view.View

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bidzi.app.adapter.PlaceSearchAdapter
import com.google.android.gms.location.*

import com.google.android.material.textfield.TextInputEditText

import java.util.*
import kotlin.math.*

import android.content.Context

import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import com.bidzi.app.databinding.ActivityMapBinding
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.PointOfInterest
import com.google.gson.annotations.SerializedName




class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMapBinding

    private var locationType: String = "pickup" // pickup or drop
    companion object {
        const val EXTRA_LOCATION_TYPE = "location_type"
        const val EXTRA_ADDRESS = "address"
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
    }


    private lateinit var map: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var geocoder: Geocoder
    private val okHttpClient = OkHttpClient()
    private val gson = Gson()

    private lateinit var searchAdapter: PlaceSearchAdapter

    private var locationPermissionGranted = false
    private var selectedLatLng: LatLng? = null
    private var fullLocationDescription: String? = null
    private var tapMarker: Marker? = null

    private val TAG = "MapActivity"
    private val DEFAULT_LOCATION = LatLng(19.6956, 72.7717) // Palghar Railway Station
    private val placesApiKey by lazy { getString(R.string.google_maps_key) }

    private val handler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null



    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        locationPermissionGranted = isGranted
        if (isGranted) {
            checkLocationSettings()
        } else {
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            useDefaultLocation()
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                val currentLatLng = LatLng(location.latitude, location.longitude)
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 16f))
                fusedLocationClient.removeLocationUpdates(this)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Get location type from intent
        locationType = intent.getStringExtra(EXTRA_LOCATION_TYPE) ?: "pickup"

        // Update title based on type
        updateTitle()

        // Initialize services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        geocoder = Geocoder(this, Locale.getDefault())

        // Setup search RecyclerView
        searchAdapter = PlaceSearchAdapter { prediction ->
            onSearchResultClick(prediction)
        }
        binding.rvSearchResults.apply {
            layoutManager = LinearLayoutManager(this@MapActivity)
            adapter = searchAdapter
        }

        // Setup map
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupClickListeners()
        setupSearchFunctionality()
    }
    private fun updateTitle() {
        val title = if (locationType == "pickup") {
            "Select Pickup Location"
        } else {
            "Select Drop Location"
        }
        // Update your toolbar/title here if needed
        supportActionBar?.title = title
    }

    // Update confirmSelection to return data
    private fun confirmSelection(latLng: LatLng) {
        val locationText = binding.tvConfirmationAddress.text.toString()

        Log.d(TAG, "✅ Location confirmed: $locationText")

        val resultIntent = Intent().apply {
            putExtra(EXTRA_LOCATION_TYPE, locationType)
            putExtra(EXTRA_ADDRESS, locationText)
            putExtra(EXTRA_LATITUDE, latLng.latitude)
            putExtra(EXTRA_LONGITUDE, latLng.longitude)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }


    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.uiSettings.isMyLocationButtonEnabled = false
        map.uiSettings.isZoomControlsEnabled = false

        setupMapTapListener()
        getLocationPermission()
    }

    // Setup map tap listeners
    private fun setupMapTapListener() {
        // Regular map click
        map.setOnMapClickListener { latLng ->
            onMapTapped(latLng, null)
        }

        // POI (Point of Interest) click - hospitals, restaurants, etc.
        map.setOnPoiClickListener { poi ->
            Log.d(TAG, "🏥 POI tapped: ${poi.name}")
            onMapTapped(poi.latLng, poi.name)
        }
    }

    // Handle map/POI tap
    private fun onMapTapped(latLng: LatLng, placeName: String?) {
        // Remove previous marker
        tapMarker?.remove()

        // Add new marker at tapped location
        tapMarker = map.addMarker(
            MarkerOptions()
                .position(latLng)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                .title(placeName ?: "Selected Location")
        )

        // Animate camera to location
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17f), 300, null)

        // Show confirmation bottom sheet
        showLocationConfirmationSheet(latLng, placeName)
    }

    // Show confirmation bottom sheet
    private fun showLocationConfirmationSheet(latLng: LatLng, placeName: String?) {
        binding.locationConfirmationSheet.visibility = View.VISIBLE
        binding.tvConfirmationTitle.text = "Select this location?"
        binding.tvConfirmationCoordinates.text =
            String.format("Lat: %.4f, Lng: %.4f", latLng.latitude, latLng.longitude)

        // Fetch and display address
        if (placeName != null) {
            binding.tvConfirmationAddress.text = placeName
            fetchFullAddressForConfirmation(latLng, placeName)
        } else {
            binding.tvConfirmationAddress.text = "Fetching address..."
            fetchFullAddressForConfirmation(latLng, null)
        }

        // Cancel button
        binding.btnCancelSelection.setOnClickListener {
            cancelSelection()
        }

        // Confirm button
        binding.btnConfirmSelection.setOnClickListener {
            confirmSelection(latLng)
        }
    }

    // SOLUTION 2: Fetch full address - prioritize POI name
    private fun fetchFullAddressForConfirmation(latLng: LatLng, placeName: String?) {
        // If we have POI name, use it immediately
        if (placeName != null) {
            Log.d(TAG, "✅ Using tapped POI name: $placeName")

            getGeocoderAddress(latLng) { address ->
                val displayText = buildAddressWithPoiName(placeName, address)
                runOnUiThread {
                    binding.tvConfirmationAddress.text = displayText
                }
            }
            return
        }

        // No POI name - fetch from Places API
        fetchNearbyPlaces(latLng, radius = 300) { places ->
            getGeocoderAddress(latLng) { address ->
                val displayText = if (!places.isNullOrEmpty() && address != null) {
                    val closestPlace = findClosestPlace(latLng, places)
                    if (closestPlace != null) {
                        val distance = calculateDistance(
                            latLng.latitude, latLng.longitude,
                            closestPlace.geometry?.location?.lat ?: 0.0,
                            closestPlace.geometry?.location?.lng ?: 0.0
                        )
                        formatCabStyleAddress(closestPlace, address, distance)
                    } else {
                        formatAddressFromGeocoder(address)
                    }
                } else if (address != null) {
                    formatAddressFromGeocoder(address)
                } else {
                    "Unknown location"
                }

                runOnUiThread {
                    binding.tvConfirmationAddress.text = displayText
                }
            }
        }
    }

    // SOLUTION 2: Helper to build address with POI name
    private fun buildAddressWithPoiName(poiName: String, address: Address?): String {
        val parts = mutableListOf<String>()

        // Always add POI name first
        parts.add(poiName)

        if (address != null) {
            // Add road if not in POI name
            address.thoroughfare?.let { road ->
                if (!poiName.contains(road, ignoreCase = true)) {
                    parts.add(road)
                }
            }

            // Add area/neighborhood
            address.subLocality?.let { area ->
                if (!poiName.contains(area, ignoreCase = true) &&
                    !parts.any { it.contains(area, ignoreCase = true) }) {
                    parts.add(area)
                }
            }

            // Add city
            address.locality?.let { city ->
                if (!poiName.contains(city, ignoreCase = true) &&
                    !parts.any { it.contains(city, ignoreCase = true) }) {
                    parts.add(city)
                }
            }
        }

        return parts.joinToString(", ")
    }

    // Cancel selection
    private fun cancelSelection() {
        tapMarker?.remove()
        tapMarker = null
        binding.locationConfirmationSheet.visibility = View.GONE
    }


    // Setup click listeners
    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.fabCurrentLocation.setOnClickListener {
            if (locationPermissionGranted) {
                getCurrentLocationAndMove()
            } else {
                Toast.makeText(this, "Location permission required", Toast.LENGTH_SHORT).show()
                getLocationPermission()
            }
        }

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.text?.clear()
            binding.rvSearchResults.visibility = View.GONE
            binding.searchResultsCard.visibility = View.GONE
        }
    }

    // Setup search functionality
    private fun setupSearchFunctionality() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                binding.btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE

                searchRunnable?.let { handler.removeCallbacks(it) }

                if (query.length >= 3) {
                    searchRunnable = Runnable { searchPlaces(query) }
                    handler.postDelayed(searchRunnable!!, 500)
                } else {
                    binding.rvSearchResults.visibility = View.GONE
                    binding.searchResultsCard.visibility = View.GONE
                }
            }
        })

        binding.etSearch.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                handler.postDelayed({
                    binding.rvSearchResults.visibility = View.GONE
                    binding.searchResultsCard.visibility = View.GONE
                }, 200)
            }
        }
    }

    // Search places using Autocomplete API
    private fun searchPlaces(query: String) {
        val userLocation = selectedLatLng ?: DEFAULT_LOCATION

        val url = "https://maps.googleapis.com/maps/api/place/autocomplete/json?" +
                "input=$query" +
                "&location=${userLocation.latitude},${userLocation.longitude}" +
                "&radius=50000" +
                "&components=country:in" +
                "&key=$placesApiKey"

        Log.d(TAG, "🔍 Searching: $query")

        val request = Request.Builder().url(url).build()

        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "❌ Search failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { responseBody ->
                    try {
                        val searchResponse = gson.fromJson(responseBody, AutocompleteResponse::class.java)
                        runOnUiThread {
                            if (!searchResponse.predictions.isNullOrEmpty()) {
                                searchAdapter.updatePredictions(searchResponse.predictions)
                                binding.rvSearchResults.visibility = View.VISIBLE
                                binding.searchResultsCard.visibility = View.VISIBLE
                            } else {
                                binding.rvSearchResults.visibility = View.GONE
                                binding.searchResultsCard.visibility = View.GONE
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Search parse error: ${e.message}")
                    }
                }
            }
        })
    }

    // Handle search result click
    private fun onSearchResultClick(prediction: PlacePrediction) {
        binding.rvSearchResults.visibility = View.GONE
        binding.searchResultsCard.visibility = View.GONE
        binding.etSearch.clearFocus()

        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)

        Log.d(TAG, "🔍 Getting details for: ${prediction.description}")

        val url = "https://maps.googleapis.com/maps/api/place/details/json?" +
                "place_id=${prediction.placeId}" +
                "&fields=geometry,name,formatted_address" +
                "&key=$placesApiKey"

        val request = Request.Builder().url(url).build()

        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "❌ Place details failed: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this@MapActivity, "Network error", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { responseBody ->
                    try {
                        val detailsResponse = gson.fromJson(responseBody, PlaceDetailsResponse::class.java)

                        if (detailsResponse.status == "OK") {
                            val location = detailsResponse.result?.geometry?.location
                            val placeName = detailsResponse.result?.name

                            if (location != null && placeName != null) {
                                val latLng = LatLng(location.lat, location.lng)
                                runOnUiThread {
                                    onMapTapped(latLng, placeName)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Parse error: ${e.message}")
                    }
                }
            }
        })
    }

    // Helper methods
    private fun findClosestPlace(userLatLng: LatLng, places: List<Place>): Place? {
        val properTrainStations = places.filter { place ->
            place.name != null &&
                    place.geometry?.location != null &&
                    place.types?.any { it == "train_station" || it == "transit_station" } == true &&
                    place.name.let { name ->
                        (name.contains("station", ignoreCase = true) ||
                                name.contains("railway", ignoreCase = true) ||
                                name.length > 15) &&
                                !name.equals("Palghar", ignoreCase = true) &&
                                !name.equals("Mumbai", ignoreCase = true)
                    }
        }

        if (properTrainStations.isNotEmpty()) {
            return properTrainStations.minByOrNull { place ->
                calculateDistance(
                    userLatLng.latitude, userLatLng.longitude,
                    place.geometry?.location?.lat ?: 0.0,
                    place.geometry?.location?.lng ?: 0.0
                )
            }
        }

        val validPlaces = places.filter { place ->
            place.name != null &&
                    place.geometry?.location != null &&
                    !place.name.equals("Palghar", ignoreCase = true) &&
                    !place.name.equals("Mumbai", ignoreCase = true) &&
                    place.types?.none { it == "locality" || it == "political" } == true
        }

        return validPlaces.minByOrNull { place ->
            calculateDistance(
                userLatLng.latitude, userLatLng.longitude,
                place.geometry?.location?.lat ?: 0.0,
                place.geometry?.location?.lng ?: 0.0
            )
        }
    }

    private fun formatCabStyleAddress(place: Place, address: Address, distance: Double): String {
        val addressParts = mutableListOf<String>()

        place.name?.let { name ->
            val proximityPrefix = when {
                distance < 50 -> ""
                distance < 150 -> "Near "
                else -> "Close to "
            }
            addressParts.add("$proximityPrefix$name")
        }

        address.thoroughfare?.let { road ->
            if (!addressParts.any { it.contains(road, ignoreCase = true) }) {
                addressParts.add(road)
            }
        }

        val areaName = address.subLocality ?: address.locality
        areaName?.let { area ->
            if (!addressParts.any { it.contains(area, ignoreCase = true) }) {
                addressParts.add(area)
            }
        }

        if (addressParts.none { it.contains(address.locality ?: "", ignoreCase = true) }) {
            address.locality?.let { addressParts.add(it) }
        }

        return addressParts.joinToString(", ")
    }

    private fun formatAddressFromGeocoder(address: Address): String {
        val parts = mutableListOf<String>()

        address.featureName?.let {
            if (!it.contains("Unnamed") && it != address.thoroughfare && it.length > 1) {
                parts.add(it)
            }
        }

        address.thoroughfare?.let { parts.add(it) }
        address.subLocality?.let { parts.add(it) } ?: address.locality?.let { parts.add(it) }

        if (parts.none { it.contains(address.locality ?: "", ignoreCase = true) }) {
            address.locality?.let { parts.add(it) }
        }

        return if (parts.isEmpty()) {
            address.getAddressLine(0) ?: "Unknown location"
        } else {
            parts.joinToString(", ")
        }
    }

    private fun getGeocoderAddress(latLng: LatLng, callback: (Address?) -> Unit) {
        try {
            val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
            callback(if (!addresses.isNullOrEmpty()) addresses[0] else null)
        } catch (e: Exception) {
            Log.e(TAG, "Geocoder error: ${e.message}")
            callback(null)
        }
    }

    private fun fetchNearbyPlaces(latLng: LatLng, radius: Int, callback: (List<Place>?) -> Unit) {
        val url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json?" +
                "location=${latLng.latitude},${latLng.longitude}" +
                "&radius=$radius" +
                "&rankby=prominence" +
                "&key=$placesApiKey"

        val request = Request.Builder().url(url).build()

        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { callback(null) }
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { responseBody ->
                    try {
                        val placesResponse = gson.fromJson(responseBody, NearbyPlacesResponse::class.java)
                        runOnUiThread { callback(placesResponse.results) }
                    } catch (e: Exception) {
                        runOnUiThread { callback(null) }
                    }
                }
            }
        })
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    private fun getLocationPermission() {
        locationPermissionGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (locationPermissionGranted) {
            checkLocationSettings()
        } else {
            locationPermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun checkLocationSettings() {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        val isEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!isEnabled) {
            showLocationSettingsDialog()
        } else {
            updateLocationUI()
            getCurrentLocationAndMove()
        }
    }

    private fun showLocationSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Enable Location")
            .setMessage("Please enable location services")
            .setPositiveButton("Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton("Cancel") { _, _ -> useDefaultLocation() }
            .show()
    }

    private fun getCurrentLocationAndMove() {
        try {
            if (locationPermissionGranted) {
                fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        val currentLatLng = LatLng(location.latitude, location.longitude)
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 16f))
                    } else {
                        requestNewLocationData()
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: ${e.message}")
        }
    }

    private fun requestNewLocationData() {
        try {
            if (locationPermissionGranted) {
                val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
                Handler(Looper.getMainLooper()).postDelayed({
                    useDefaultLocation()
                    fusedLocationClient.removeLocationUpdates(locationCallback)
                }, 8000)
            }
        } catch (e: SecurityException) {
            useDefaultLocation()
        }
    }

    private fun useDefaultLocation() {
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_LOCATION, 15f))
    }

    private fun updateLocationUI() {
        if (::map.isInitialized) {
            try {
                map.isMyLocationEnabled = locationPermissionGranted
            } catch (e: SecurityException) {}
        }
    }

    override fun onResume() {
        super.onResume()
        if (locationPermissionGranted) checkLocationSettings()
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        searchRunnable?.let { handler.removeCallbacks(it) }
    }
}

// Data classes
data class NearbyPlacesResponse(val results: List<Place>?, val status: String?)

data class Place(
    val name: String?,
    val vicinity: String?,
    val geometry: Geometry?,
    val types: List<String>?,
    @SerializedName("place_id") val placeId: String?
)

data class Geometry(val location: PlaceLocation?, val viewport: Viewport?)
data class PlaceLocation(val lat: Double, val lng: Double)
data class Viewport(val northeast: PlaceLocation?, val southwest: PlaceLocation?)

data class AutocompleteResponse(val predictions: List<PlacePrediction>?, val status: String?)

data class PlacePrediction(
    val description: String,
    @SerializedName("place_id") val placeId: String,
    @SerializedName("structured_formatting") val structuredFormatting: StructuredFormatting?
) {
    data class StructuredFormatting(
        @SerializedName("main_text") val mainText: String,
        @SerializedName("secondary_text") val secondaryText: String?
    )
}

data class PlaceDetailsResponse(
    val result: PlaceDetails?,
    val status: String?,
    @SerializedName("error_message") val errorMessage: String?
)

data class PlaceDetails(
    val geometry: Geometry?,
    val name: String?,
    @SerializedName("formatted_address") val formattedAddress: String?
)
