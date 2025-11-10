package com.bidzi.app.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// =====================================================
// 1. DATA MODELS
// =====================================================

data class SharedRide(
    val id: String,
    val driverId: String,
    val driverName: String,
    val driverRating: Double,
    val driverTotalTrips: Int,
    val driverIsOnline: Boolean,
    val vehicleType: String,
    val vehicleNumber: String,

    // Locations
    val pickupLocation: String,
    val pickupLat: Double,
    val pickupLng: Double,
    val dropLocation: String,
    val dropLat: Double,
    val dropLng: Double,

    // Ride details
    val distance: Double, // in km
    val estimatedDuration: Int, // in minutes
    val basePrice: Double,
    val totalSeats: Int,
    val availableSeats: Int,
    val departureTime: String,
    val status: String,

    // Computed properties
    val pickupDistanceFromUser: Double = 0.0, // Will be calculated
    val filledSeats: Int = totalSeats - availableSeats
) {
    // Helper properties for UI
    val seatsInfo: String
        get() = "$filledSeats/$totalSeats seats filled"

    val distanceText: String
        get() = String.format("%.1f km", distance)

    val fareText: String
        get() = "₹${basePrice.toInt()}"

    val leavesInText: String
        get() = calculateLeavesIn(departureTime)

    val pickupDistanceText: String
        get() = String.format("%.1f km away", pickupDistanceFromUser)

    private fun calculateLeavesIn(departureTime: String): String {
        return try {
            val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val departureDate = formatter.parse(departureTime) ?: return "Unknown"
            val now = Calendar.getInstance().time
            val diffMinutes = ((departureDate.time - now.time) / (1000 * 60)).toInt()

            when {
                diffMinutes < 1 -> "Leaving now"
                diffMinutes < 60 -> "$diffMinutes min"
                else -> {
                    val hours = diffMinutes / 60
                    val mins = diffMinutes % 60
                    if (mins == 0) "$hours hr" else "${hours}h ${mins}m"
                }
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }
}

// =====================================================
// 2. SUPABASE DTOs (with @Serializable)
// =====================================================

@Serializable
data class SharedRideDto(
    val id: String,
    val driver_id: String,
    val driver_name: String,
    val driver_rating: Double,
    val driver_total_trips: Int,
    val driver_is_online: Boolean,
    val vehicle_type: String,
    val vehicle_number: String,
    val pickup_location: String,
    val pickup_lat: Double,
    val pickup_lng: Double,
    val drop_location: String,
    val drop_lat: Double,
    val drop_lng: Double,
    val distance: Double,
    val estimated_duration: Int,
    val base_price: Double,
    val total_seats: Int,
    val available_seats: Int,
    val departure_time: String,
    val status: String
)

@Serializable
data class RideParticipantDto(
    val id: String,
    val ride_id: String,
    val user_id: String,
    val seats_booked: Int,
    val final_price: Double,
    val status: String,
    val payment_status: String
)