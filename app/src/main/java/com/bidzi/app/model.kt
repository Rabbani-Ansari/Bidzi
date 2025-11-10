package com.bidzi.app

// ===================================================================
// DriverData.kt
import android.os.Parcelable
import com.bidzi.app.supabase.CounterOfferStatus
import com.bidzi.app.supabase.OfferedBy
import kotlinx.parcelize.Parcelize

import com.google.firebase.firestore.PropertyName


// Driver.kt
data class Driver(
    val id: String,
    val name: String?=null,
    val rideId: String?=null,
    val rating: Double?=null,
    val totalTrips: Int?=null,
    val initials: String?=null,
    val vehicleType: String?=null,
    val vehicleNumber: String?=null,
    val price: Int,
    val eta: Int,
    val offerType: OfferType,
    val offerTime: String,
    val savings: Int = 0,
    var isConfirmed: Boolean = false,
    val isOnline: Boolean = true,
    // Counter offer related fields
    // Counter offer related fields
    val hasActiveCounter: Boolean = false,
    val activeCounterPrice: Int? = null,
    val counterStatus: CounterOfferStatus? = null,
    val counterOfferId: String? = null,
    val counterOfferedBy: OfferedBy? = null
)

enum class OfferType {
    ACCEPTED_BID,
    COUNTER_OFFER,
    BEST_OFFER
}

// TripLocation.kt
data class TripLocation(
    val from: String,
    val to: String
)
/**
 * Data model for nearby vehicles displayed on the map
 * Can be used with Firebase Firestore or local mock data
 */
data class NearbyVehicle(
    @PropertyName("id")
    var id: String = "",

    @PropertyName("type")
    var type: String = "Auto", // Auto, Bike, Car

    @PropertyName("driver_name")
    var driverName: String = "",

    @PropertyName("rating")
    var rating: Float = 0f,

    @PropertyName("latitude")
    var latitude: Double = 0.0,

    @PropertyName("longitude")
    var longitude: Double = 0.0,

    @PropertyName("is_available")
    var isAvailable: Boolean = true,

    @PropertyName("vehicle_number")
    var vehicleNumber: String = "",

    @PropertyName("current_speed")
    var currentSpeed: Float = 0f,

    @PropertyName("heading")
    var heading: Float = 0f, // Direction in degrees

    @PropertyName("last_updated")
    var lastUpdated: Long = System.currentTimeMillis()
) {
    // No-arg constructor required by Firebase
    constructor() : this(
        id = "",
        type = "Auto",
        driverName = "",
        rating = 0f,
        latitude = 0.0,
        longitude = 0.0
    )
}

@Parcelize
data class DriverData(
    val name: String,
    val rating: Float,
    val vehicleNumber: String,
    val fare: Int,
    val distance: Float,
    val arrivalTime: Int,
    val phoneNumber: String,
    val coRiders: List<String> = emptyList()
) : Parcelable


// Data Classes
data class RideLocation(
    val name: String,
    val type: String // "pickup" or "drop"
)

data class Ride(
    val id: Int,
    val pickup: RideLocation,
    val drop: RideLocation,
    val distance: String,
    val seatsInfo: String, // e.g., "2/3 seats filled"
    val leavesIn: String, // e.g., "8 min"
    val riders: List<String>, // e.g., ["Rahul", "Priya"]
    val fare: Int // e.g., 60
)

// Data Classes
data class ScheduleRideData(
    val pickupLocation: String = "Current Location",
    val dropLocation: String = "Phoenix Mall",
    val selectedDate: String = "",
    val selectedTime: String = "9:00 AM",
    val bidPrice: Int = 80,
    val suggestedPriceMin: Int = 75,
    val suggestedPriceMax: Int = 100
)
// Data Classes
data class RideHistory(
    val id: Int,
    val destination: String,
    val source: String,
    val fromLocation: String,
    val withPassengers: List<String>? = null,
    val dateTime: String,
    val distance: String,
    val duration: String,
    val fare: Int,
    val rating: Double,
    val isShared: Boolean = false
)




data class MenuItem(
    val id: Int,
    val title: String,
    val icon: Int,
    val type: MenuItemType
)

enum class MenuItemType {
    RIDE_HISTORY,
    MY_RATINGS,
    HELP_SUPPORT,
    SETTINGS,
    LOGOUT
}