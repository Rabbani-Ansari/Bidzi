package com.bidzi.app.supabase

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


import android.os.Parcelable
import com.bidzi.app.Driver
import com.bidzi.app.OfferType
import kotlinx.parcelize.Parcelize
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

// For INSERT - now includes created_at
@Parcelize
@Serializable
data class RideBookingInsert(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("confirmed_driver_id")
    val confirmedDriverId: String? = null,
    @SerialName("pickup_address")
    val pickupAddress: String,
    @SerialName("pickup_lat")
    val pickupLat: Double,
    @SerialName("pickup_lng")
    val pickupLng: Double,
    @SerialName("drop_address")
    val dropAddress: String,
    @SerialName("drop_lat")
    val dropLat: Double,
    @SerialName("drop_lng")
    val dropLng: Double,
    @SerialName("vehicle_type")
    val vehicleType: String,
    @SerialName("distance_km")
    val distanceKm: Double,
    @SerialName("bid")
    val bid: Int,
    val note: String? = null,
    val status: String = "pending",
    @SerialName("created_at")
    val createdAt: String,
    // NEW: Add ride preferences
    val passengers: Int = 1,
    val luggage: Int = 0
) : Parcelable {
    // Helper function to convert to RideBooking
    fun toRideBooking(): RideBooking {
        return RideBooking(
            id = id,
            userId = userId,
            confirmedDriverId = confirmedDriverId,
            pickupAddress = pickupAddress,
            pickupLat = pickupLat,
            pickupLng = pickupLng,
            dropAddress = dropAddress,
            dropLat = dropLat,
            dropLng = dropLng,
            vehicleType = vehicleType,
            distanceKm = distanceKm,
            bid = bid,
            note = note,
            status = status,
            createdAt = createdAt,
            // NEW: Include ride preferences
            passengers = passengers,
            luggage = luggage
        )
    }
}

// For SELECT (same structure now)
@Parcelize
@Serializable
data class RideBooking(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("confirmed_driver_id")
    val confirmedDriverId: String? = null,
    @SerialName("pickup_address")
    val pickupAddress: String,
    @SerialName("pickup_lat")
    val pickupLat: Double,
    @SerialName("pickup_lng")
    val pickupLng: Double,
    @SerialName("drop_address")
    val dropAddress: String,
    @SerialName("drop_lat")
    val dropLat: Double,
    @SerialName("drop_lng")
    val dropLng: Double,
    @SerialName("vehicle_type")
    val vehicleType: String,
    @SerialName("distance_km")
    val distanceKm: Double,
    @SerialName("bid")
    val bid: Int,
    val note: String? = null,
    val status: String,
    @SerialName("created_at")
    val createdAt: String,
    // NEW: Add ride preferences
    val passengers: Int = 1,
    val luggage: Int = 0
) : Parcelable
@Parcelize
@Serializable
data class DriverRideResponse(
    val id: String,
    @SerialName("ride_id")
    val rideId: String,
    @SerialName("driver_id")
    val driverId: String,
    @SerialName("offered_price")
    val offeredPrice: Int,
    @SerialName("estimated_eta")
    val estimatedEta: Int,
    @SerialName("offer_type")
    val offerType: String,
    @SerialName("is_online")
    val isOnline: Boolean = true,
    @SerialName("is_confirmed")
    val isConfirmed: Boolean = false,
    @SerialName("created_at")
    val createdAt: String
) : Parcelable {

    // Remove parameter, calculate savings elsewhere
    fun toDriver(): Driver {


        val type = when (offerType) {
            "bid_accept" -> OfferType.ACCEPTED_BID
            "best_offer" -> OfferType.BEST_OFFER
            "counter_offer" -> OfferType.COUNTER_OFFER
            else -> OfferType.ACCEPTED_BID
        }

        return Driver(
            id = driverId,
            price = offeredPrice,
            eta = estimatedEta,
            offerType = type,
            offerTime = calculateTimeAgo(createdAt),
            savings = 0, // Will be calculated in fragment
            isConfirmed = isConfirmed,
            isOnline = isOnline
        )
    }

    private fun calculateTimeAgo(timestamp: String): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val date = sdf.parse(timestamp)
            val now = System.currentTimeMillis()
            val diff = now - (date?.time ?: now)

            when {
                diff < 60000 -> "Just now"
                diff < 3600000 -> "${diff / 60000}m ago"
                diff < 86400000 -> "${diff / 3600000}h ago"
                else -> "${diff / 86400000}d ago"
            }
        } catch (e: Exception) {
            "Just now"
        }
    }
}


@Serializable
data class CounterOffer(
    val id: String = UUID.randomUUID().toString(),

    @SerialName("ride_id")
    val rideId: String,

    @SerialName("driver_id")
    val driverId: String,

    @SerialName("user_id")
    val userId: String,

    @SerialName("counter_price")
    val counterPrice: Int,

    @SerialName("offered_by")
    val offeredBy: OfferedBy,  // Enum: 'user' or 'driver'

    val status: CounterOfferStatus = CounterOfferStatus.PENDING,

    @SerialName("created_at")
    val createdAt: String = getCurrentTimestamp(),

    @SerialName("responded_at")
    val respondedAt: String? = null,

    val message: String? = null
)

fun getCurrentTimestamp(): String {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
    dateFormat.timeZone = TimeZone.getTimeZone("UTC")
    return dateFormat.format(Date())
}
@Serializable
enum class OfferedBy {
    @SerialName("user") USER,
    @SerialName("driver") DRIVER
}

@Serializable
enum class CounterOfferStatus {
    @SerialName("pending") PENDING,
    @SerialName("accepted") ACCEPTED,
    @SerialName("rejected") REJECTED,
    @SerialName("expired") EXPIRED
}





/**
 * Data class for inserting scheduled ride bookings into Supabase
 */
@Serializable
data class ScheduledRideBookingInsert(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("pickup_address")
    val pickupAddress: String,
    @SerialName("pickup_lat")
    val pickupLat: Double,
    @SerialName("pickup_lng")
    val pickupLng: Double,
    @SerialName("drop_address")
    val dropAddress: String,
    @SerialName("drop_lat")
    val dropLat: Double,
    @SerialName("drop_lng")
    val dropLng: Double,
    @SerialName("vehicle_type")
    val vehicleType: String,
    @SerialName("distance_km")
    val distanceKm: Double,
    val bid: Int,
    val note: String? = null,
    @SerialName("scheduled_date")
    val scheduledDate: String,
    @SerialName("scheduled_time")
    val scheduledTime: String,
    @SerialName("scheduled_timestamp")
    val scheduledTimestamp: String,
    val status: String = "scheduled",
    @SerialName("created_at")
    val createdAt: String
)

/**
 * Data class for scheduled ride bookings retrieved from Supabase
 */
@Serializable
@Parcelize
data class ScheduledRideBooking(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("pickup_address")
    val pickupAddress: String,
    @SerialName("pickup_lat")
    val pickupLat: Double,
    @SerialName("pickup_lng")
    val pickupLng: Double,
    @SerialName("drop_address")
    val dropAddress: String,
    @SerialName("drop_lat")
    val dropLat: Double,
    @SerialName("drop_lng")
    val dropLng: Double,
    @SerialName("vehicle_type")
    val vehicleType: String,
    @SerialName("distance_km")
    val distanceKm: Double,
    val bid: Int,
    val note: String? = null,
    @SerialName("scheduled_date")
    val scheduledDate: String,
    @SerialName("scheduled_time")
    val scheduledTime: String,
    @SerialName("scheduled_timestamp")
    val scheduledTimestamp: String,
    val status: String = "scheduled",
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("confirmed_driver_id")
    val confirmedDriverId: String? = null,
    @SerialName("driver_assigned_at")
    val driverAssignedAt: String? = null
) : Parcelable

/**
 * Extension function to convert insert model to booking model
 */
fun ScheduledRideBookingInsert.toScheduledRideBooking(): ScheduledRideBooking {
    return ScheduledRideBooking(
        id = this.id,
        userId = this.userId,
        pickupAddress = this.pickupAddress,
        pickupLat = this.pickupLat,
        pickupLng = this.pickupLng,
        dropAddress = this.dropAddress,
        dropLat = this.dropLat,
        dropLng = this.dropLng,
        vehicleType = this.vehicleType,
        distanceKm = this.distanceKm,
        bid = this.bid,
        note = this.note,
        scheduledDate = this.scheduledDate,
        scheduledTime = this.scheduledTime,
        scheduledTimestamp = this.scheduledTimestamp,
        status = this.status,
        createdAt = this.createdAt,
        confirmedDriverId = null,
        driverAssignedAt = null
    )
}