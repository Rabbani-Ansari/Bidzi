package com.bidzi.app.helper


import android.content.Context
import android.location.Location
import android.util.Log
import com.bidzi.app.adapter.PlaceSuggestion
import com.bidzi.app.adapter.PlaceType
import com.google.android.gms.maps.model.LatLng

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

class PlacesRepository(
    private val context: Context,
    private val apiKey: String
) {

    // NEW: Find nearby famous place
    suspend fun findNearbyFamousPlace(location: Location): String? = withContext(Dispatchers.IO) {
        try {
            // Search for prominent nearby places
            val url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json" +
                    "?location=${location.latitude},${location.longitude}" +
                    "&radius=500" +  // 500 meters
                    "&rankby=prominence" +
                    "&key=$apiKey"

            Log.d("PlacesRepo", "Nearby search URL: $url")

            val response = URL(url).readText()
            val jsonObject = JSONObject(response)

            val status = jsonObject.getString("status")
            Log.d("PlacesRepo", "Nearby search status: $status")

            if (status == "OK") {
                val results = jsonObject.getJSONArray("results")

                if (results.length() > 0) {
                    // Get the first prominent result
                    val firstPlace = results.getJSONObject(0)
                    val placeName = firstPlace.getString("name")
                    val vicinity = firstPlace.optString("vicinity", "")

                    // Format as "Near [Famous Place]"
                    val formattedAddress = if (vicinity.isNotEmpty()) {
                        "Near $placeName, ${vicinity.split(",").take(2).joinToString(", ")}"
                    } else {
                        "Near $placeName"
                    }

                    Log.d("PlacesRepo", "Found nearby place: $formattedAddress")
                    return@withContext formattedAddress
                }
            } else {
                Log.e("PlacesRepo", "Nearby search error: $status")
            }

            null
        } catch (e: Exception) {
            Log.e("PlacesRepo", "Nearby search exception: ${e.message}", e)
            null
        }
    }

    // IMPROVED: Better geocoding fallback
    suspend fun getReverseGeocodedAddress(location: Location): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://maps.googleapis.com/maps/api/geocode/json" +
                    "?latlng=${location.latitude},${location.longitude}" +
                    "&key=$apiKey" +
                    "&result_type=street_address|route|sublocality|locality"

            val response = URL(url).readText()
            val jsonObject = JSONObject(response)

            if (jsonObject.getString("status") == "OK") {
                val results = jsonObject.getJSONArray("results")

                if (results.length() > 0) {
                    val addressComponents = results.getJSONObject(0)
                        .getJSONArray("address_components")

                    val parts = mutableListOf<String>()

                    // Extract meaningful parts
                    for (i in 0 until minOf(addressComponents.length(), 3)) {
                        val component = addressComponents.getJSONObject(i)
                        val types = component.getJSONArray("types")
                        val longName = component.getString("long_name")

                        // Check if it's a useful type
                        for (j in 0 until types.length()) {
                            val type = types.getString(j)
                            if (type in listOf("premise", "subpremise", "route",
                                    "sublocality", "sublocality_level_1", "locality")) {
                                if (!parts.contains(longName)) {
                                    parts.add(longName)
                                }
                                break
                            }
                        }
                    }

                    return@withContext if (parts.isNotEmpty()) {
                        parts.joinToString(", ")
                    } else {
                        null
                    }
                }
            }

            null
        } catch (e: Exception) {
            Log.e("PlacesRepo", "Reverse geocoding error: ${e.message}")
            null
        }
    }

    suspend fun searchPlaces(
        query: String,
        location: Location? = null
    ): List<PlaceSuggestion> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val locationBias = location?.let {
                "&location=${it.latitude},${it.longitude}&radius=50000"
            } ?: ""

            val url = "https://maps.googleapis.com/maps/api/place/autocomplete/json" +
                    "?input=$encodedQuery" +
                    "$locationBias" +
                    "&key=$apiKey" +
                    "&components=country:in"

            Log.d("PlacesRepo", "Search URL: $url")

            val response = URL(url).readText()
            val jsonObject = JSONObject(response)

            val status = jsonObject.getString("status")
            Log.d("PlacesRepo", "API Status: $status")

            if (status == "OK") {
                val predictions = jsonObject.getJSONArray("predictions")
                val suggestions = mutableListOf<PlaceSuggestion>()

                Log.d("PlacesRepo", "Found ${predictions.length()} predictions")

                for (i in 0 until predictions.length()) {
                    val prediction = predictions.getJSONObject(i)
                    val structuredFormatting = prediction.getJSONObject("structured_formatting")

                    suggestions.add(
                        PlaceSuggestion(
                            placeId = prediction.getString("place_id"),
                            primaryText = structuredFormatting.getString("main_text"),
                            secondaryText = structuredFormatting.optString("secondary_text", ""),
                            type = PlaceType.SEARCH_RESULT
                        )
                    )
                }
                suggestions
            } else {
                Log.e("PlacesRepo", "API Error: $status")
                if (jsonObject.has("error_message")) {
                    Log.e("PlacesRepo", "Error message: ${jsonObject.getString("error_message")}")
                }
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("PlacesRepo", "Exception: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getPlaceDetails(placeId: String): LatLng? = withContext(Dispatchers.IO) {
        try {
            val url = "https://maps.googleapis.com/maps/api/place/details/json" +
                    "?place_id=$placeId" +
                    "&fields=geometry" +
                    "&key=$apiKey"

            val response = URL(url).readText()
            val jsonObject = JSONObject(response)

            if (jsonObject.getString("status") == "OK") {
                val result = jsonObject.getJSONObject("result")
                val geometry = result.getJSONObject("geometry")
                val location = geometry.getJSONObject("location")

                LatLng(
                    location.getDouble("lat"),
                    location.getDouble("lng")
                )
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getCurrentLocationSuggestion(location: Location): PlaceSuggestion {
        return PlaceSuggestion(
            placeId = "current_location",
            primaryText = "Current Location",
            secondaryText = "Use your current location",
            type = PlaceType.CURRENT_LOCATION,
            latitude = location.latitude,
            longitude = location.longitude
        )
    }
}
