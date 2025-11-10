package com.bidzi.app.helper


import com.google.android.gms.maps.model.LatLng
import kotlin.math.*

object DistanceCalculator {

    // Pricing tiers (you can adjust these)
    private const val BASE_FARE = 50
    private const val FIRST_2KM_RATE = 15  // Higher rate for first 2 km
    private const val AFTER_2KM_RATE = 12  // Lower rate after 2 km
    private const val NIGHT_SURGE = 1.2    // 20% surge at night (8 PM - 6 AM)
    private const val BOOKING_FEE = 5

    fun calculatePriceRange(distanceKm: Double): Pair<Int, Int> {
        var minPrice = BASE_FARE + BOOKING_FEE
        var maxPrice = BASE_FARE + BOOKING_FEE

        // First 2 km calculation
        if (distanceKm <= 2.0) {
            minPrice += (distanceKm * FIRST_2KM_RATE).toInt()
            maxPrice += (distanceKm * FIRST_2KM_RATE * 1.25).toInt()
        } else {
            // First 2 km
            minPrice += (2.0 * FIRST_2KM_RATE).toInt()
            maxPrice += (2.0 * FIRST_2KM_RATE * 1.25).toInt()

            // Remaining distance
            val remainingKm = distanceKm - 2.0
            minPrice += (remainingKm * AFTER_2KM_RATE).toInt()
            maxPrice += (remainingKm * AFTER_2KM_RATE * 1.25).toInt()
        }

        // Apply night surge if applicable
        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        if (currentHour >= 20 || currentHour < 6) {
            minPrice = (minPrice * NIGHT_SURGE).toInt()
            maxPrice = (maxPrice * NIGHT_SURGE).toInt()
        }

        // Round to nearest 5
        val roundedMin = ((minPrice + 2) / 5) * 5
        val roundedMax = ((maxPrice + 2) / 5) * 5

        return Pair(roundedMin, roundedMax)
    }

    // Rest of the methods remain the same...
    fun calculateDistance(start: LatLng, end: LatLng): Double {
        val earthRadius = 6371000.0
        val lat1Rad = Math.toRadians(start.latitude)
        val lat2Rad = Math.toRadians(end.latitude)
        val deltaLat = Math.toRadians(end.latitude - start.latitude)
        val deltaLng = Math.toRadians(end.longitude - start.longitude)

        val a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2)

        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadius * c
    }

    fun calculateETA(distanceInMeters: Double): Int {
        val averageSpeedKmh = 30.0
        val distanceInKm = distanceInMeters / 1000.0
        val etaHours = distanceInKm / averageSpeedKmh
        val etaMinutes = (etaHours * 60).toInt()
        return etaMinutes.coerceAtLeast(5)
    }

    fun formatDistance(distanceInMeters: Double): String {
        return if (distanceInMeters < 1000) {
            "${distanceInMeters.toInt()} m"
        } else {
            String.format("%.1f km", distanceInMeters / 1000.0)
        }
    }

    fun formatETA(etaMinutes: Int): String {
        return if (etaMinutes < 60) {
            "$etaMinutes min"
        } else {
            val hours = etaMinutes / 60
            val minutes = etaMinutes % 60
            if (minutes == 0) "$hours hr" else "$hours hr $minutes min"
        }
    }
}
