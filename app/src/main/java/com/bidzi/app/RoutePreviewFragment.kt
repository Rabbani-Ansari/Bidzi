package com.bidzi.app

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bidzi.app.databinding.FragmentRoutePreviewBinding
import com.bidzi.app.helper.DistanceCalculator
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class RoutePreviewFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentRoutePreviewBinding? = null
    private val binding get() = _binding!!

    private val args: RoutePreviewFragmentArgs by navArgs()

    private lateinit var map: GoogleMap
    private var routePolyline: Polyline? = null
    private val okHttpClient = OkHttpClient()

    private val placesApiKey by lazy { getString(R.string.google_maps_key) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRoutePreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMap()
        setupListeners()
        displayLocationInfo()
    }

    private fun setupMap() {
        val mapFragment = childFragmentManager.findFragmentById(R.id.routeMapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnConfirmRoute.setOnClickListener {
            // Navigate back to ride request with confirmed route
            findNavController().navigateUp()
        }
    }

    private fun displayLocationInfo() {
        binding.pickupAddressText.text = args.pickupAddress
        binding.dropAddressText.text = args.dropAddress

        // Calculate and display distance
        val pickup = LatLng(args.pickupLat.toDouble(), args.pickupLng.toDouble())
        val drop = LatLng(args.dropLat.toDouble(), args.dropLng.toDouble())

        val distanceInMeters = DistanceCalculator.calculateDistance(pickup, drop)
        val etaMinutes = DistanceCalculator.calculateETA(distanceInMeters)

        val distanceText = DistanceCalculator.formatDistance(distanceInMeters)
        val etaText = DistanceCalculator.formatETA(etaMinutes)

        binding.routeDistanceText.text = "$distanceText"
        binding.routeEtaText.text = "$etaText"
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        // Configure map UI
        map.uiSettings.apply {
            isZoomControlsEnabled = true
            isCompassEnabled = true
            isMyLocationButtonEnabled = false
        }

        val pickup = LatLng(args.pickupLat.toDouble(), args.pickupLng.toDouble())
        val drop = LatLng(args.dropLat.toDouble(), args.dropLng.toDouble())

        // Add markers
        addMarkers(pickup, drop)

        // Fetch and draw route
        fetchAndDrawRoute(pickup, drop)
    }

    private fun addMarkers(pickup: LatLng, drop: LatLng) {
        // Pickup marker (Green)
        map.addMarker(
            MarkerOptions()
                .position(pickup)
                .title("Pickup")
                .snippet(args.pickupAddress)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
        )

        // Drop marker (Red)
        map.addMarker(
            MarkerOptions()
                .position(drop)
                .title("Drop")
                .snippet(args.dropAddress)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
        )

        // Zoom to show both markers
        val bounds = LatLngBounds.Builder()
            .include(pickup)
            .include(drop)
            .build()

        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 150))
    }

    private fun fetchAndDrawRoute(pickup: LatLng, drop: LatLng) {
        binding.routeLoadingProgress.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // FREE OSRM API - No key required!
                val url = "https://router.project-osrm.org/route/v1/driving/" +
                        "${pickup.longitude},${pickup.latitude};" +
                        "${drop.longitude},${drop.latitude}?" +
                        "overview=full&geometries=polyline&steps=true"

                Log.d("RoutePreview", "🗺️ Fetching route from OSRM (FREE)")

                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "BidZi-Cab-App")
                    .build()

                val response = okHttpClient.newCall(request).execute()
                val responseBody = response.body?.string()

                if (responseBody != null) {
                    val jsonObject = JSONObject(responseBody)
                    val code = jsonObject.getString("code")

                    Log.d("RoutePreview", "📊 OSRM Status: $code")

                    if (code == "Ok") {
                        val routes = jsonObject.getJSONArray("routes")
                        if (routes.length() > 0) {
                            val route = routes.getJSONObject(0)

                            // Get encoded polyline
                            val polylinePoints = route.getString("geometry")

                            // Get distance (in meters) and duration (in seconds)
                            val distanceMeters = route.getDouble("distance")
                            val durationSeconds = route.getDouble("duration")

                            val distance = DistanceCalculator.formatDistance(distanceMeters)
                            val duration = DistanceCalculator.formatETA((durationSeconds / 60).toInt())

                            Log.d("RoutePreview", "✅ Got route: $distance, $duration")

                            withContext(Dispatchers.Main) {
                                binding.routeLoadingProgress.visibility = View.GONE
                                binding.routeDistanceText.text = distance
                                binding.routeEtaText.text = duration
                                drawPolyline(polylinePoints)

                                Toast.makeText(
                                    requireContext(),
                                    "Route loaded",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } else {
                        Log.e("RoutePreview", "❌ OSRM Error: $code")

                    }
                }
            } catch (e: Exception) {
                Log.e("RoutePreview", "❌ Error: ${e.message}", e)

            }
        }
    }


    private fun drawPolyline(encodedPolyline: String) {
        routePolyline?.remove()

        val decodedPath = decodePolyline(encodedPolyline)
        val polylineOptions = PolylineOptions()
            .addAll(decodedPath)
            .color(ContextCompat.getColor(requireContext(), R.color.route_color))
            .width(12f)
            .geodesic(true)

        routePolyline = map.addPolyline(polylineOptions)
    }

    private fun drawStraightLine(pickup: LatLng, drop: LatLng) {
        routePolyline?.remove()

        val polylineOptions = PolylineOptions()
            .add(pickup)
            .add(drop)
            .color(ContextCompat.getColor(requireContext(), R.color.route_color))
            .width(10f)

        routePolyline = map.addPolyline(polylineOptions)
    }

    // Decode polyline from Google Directions API
    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            val p = LatLng(lat.toDouble() / 1E5, lng.toDouble() / 1E5)
            poly.add(p)
        }

        return poly
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
