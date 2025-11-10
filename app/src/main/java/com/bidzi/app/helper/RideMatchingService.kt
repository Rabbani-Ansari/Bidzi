package com.bidzi.app.helper

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import android.os.Bundle
import android.util.Log
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import kotlinx.serialization.json.JsonElement

// Serialization
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import kotlin.math.*

// ==================== UPDATE RideMatchingService.kt ====================

// ==================== UPDATE RideMatchingService.kt ====================

class RideMatchingService(private val supabase: SupabaseClient) {

    private val haversineRadius = 6371.0

    companion object {
        private const val SEARCH_RADIUS_KM = 5.0
        private const val TAG = "RideMatchingService"
    }

    private fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return haversineRadius * c
    }

    suspend fun findNearbyDrivers(
        pickupLat: Double,
        pickupLon: Double,
        vehicleType: String,
        radiusKm: Double = SEARCH_RADIUS_KM
    ): List<DriverLocationData> {
        return try {
            Log.d(TAG, "Searching for drivers near: lat=$pickupLat, lon=$pickupLon, type=$vehicleType, radius=${radiusKm}km")

            // Fetch ALL online drivers of the specified vehicle type
            // We'll do client-side filtering by distance
            val driverLocations = supabase.postgrest["driver_locations"]
                .select {
                    filter {
                        eq("is_online", true)
                        eq("vehicle_type", vehicleType.lowercase())
                    }
                }
                .decodeList<DriverLocationData>()

            Log.d(TAG, "Fetched ${driverLocations.size} online ${vehicleType.lowercase()} drivers")

            val nearbyDrivers = driverLocations
                .filter { driver ->
                    val distance = calculateDistance(
                        pickupLat, pickupLon,
                        driver.latitude, driver.longitude
                    )
                    val isNearby = distance <= radiusKm
                    if (isNearby) {
                        Log.d(TAG, "  ✅ Driver ${driver.driver_id} is ${String.format("%.2f", distance)}km away")
                    }
                    isNearby
                }
                .sortedBy { driver ->
                    calculateDistance(
                        pickupLat, pickupLon,
                        driver.latitude, driver.longitude
                    )
                }

            Log.d(TAG, "Found ${nearbyDrivers.size} drivers within ${radiusKm}km radius")
            nearbyDrivers
        } catch (e: Exception) {
            Log.e(TAG, "Error finding nearby drivers: ${e.message}")
            emptyList()
        }
    }

    /**
     * FIXED: Changed rideId parameter from Long to String to support UUID
     */
    suspend fun sendRideRequestsToNearbyDrivers(
        rideId: String,  // CHANGED: Long -> String
        pickupLat: Double,
        pickupLon: Double,
        vehicleType: String,
        bidPrice: Int,
        maxDrivers: Int = 10
    ): List<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Sending ride requests for ride ID: $rideId")

            val nearbyDrivers = findNearbyDrivers(
                pickupLat, pickupLon, vehicleType
            ).take(maxDrivers)

            if (nearbyDrivers.isEmpty()) {
                Log.w(TAG, "No nearby drivers found")
                return@withContext emptyList()
            }

            val driverIds = mutableListOf<String>()

            for (driver in nearbyDrivers) {
                try {
                    val distance = calculateDistance(
                        pickupLat, pickupLon,
                        driver.latitude, driver.longitude
                    )

                    val metadata = RideRequestMetadata(
                        pickup_lat = pickupLat,
                        pickup_lon = pickupLon,
                        distance_km = distance
                    )

                    // Insert ride request
                    supabase.postgrest["ride_requests"].insert(
                        RideRequestData(
                            ride_id = rideId,  // CHANGED: Now accepts String
                            driver_id = driver.driver_id,
                            pickup_latitude = pickupLat,
                            pickup_longitude = pickupLon,
                            vehicle_type = vehicleType.lowercase(),
                            bid_price = bidPrice,
                            status = "pending"
                        )
                    )

                    // Insert notification
                    supabase.postgrest["notifications"].insert(
                        NotificationData(
                            user_id = driver.driver_id,
                            driver_id = driver.driver_id,
                            ride_id = rideId,  // CHANGED: Now accepts String
                            type = "ride_request",
                            title = "New Ride Request",
                            message = "₹$bidPrice • ${vehicleType.capitalize()}",
                            is_read = false,
                            metadata = metadata
                        )
                    )

                    driverIds.add(driver.driver_id)
                    Log.d(TAG, "✅ Sent request to driver: ${driver.driver_id} (${String.format("%.2f", distance)}km away)")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error sending to driver ${driver.driver_id}: ${e.message}")
                }
            }

            Log.d(TAG, "✅ Successfully sent ride requests to ${driverIds.size} drivers")
            driverIds
        } catch (e: Exception) {
            Log.e(TAG, "❌ Critical error in sendRideRequestsToNearbyDrivers: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun updateDriverLocation(
        driverId: String,
        latitude: Double,
        longitude: Double,
        vehicleType: String = "auto",
        isOnline: Boolean = true
    ) {
        try {
            supabase.postgrest["driver_locations"].upsert(
                DriverLocationUpdateData(
                    driver_id = driverId,
                    latitude = latitude,
                    longitude = longitude,
                    vehicle_type = vehicleType.lowercase(),
                    is_online = isOnline,
                    last_updated = System.currentTimeMillis()
                )
            )
            Log.d(TAG, "Location updated")
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}")
        }
    }

    suspend fun getPendingRideRequests(driverId: String): List<RideRequestData> {
        return try {
            supabase.postgrest["ride_requests"]
                .select {
                    filter {
                        eq("driver_id", driverId)
                        eq("status", "pending")
                    }
                    order(column = "sent_at", order = Order.DESCENDING)
                }
                .decodeList<RideRequestData>()
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}")
            emptyList()
        }
    }

    suspend fun updateRideRequestStatus(
        rideRequestId: Long,
        status: String
    ): Boolean {
        return try {
            supabase.postgrest["ride_requests"]
                .update(
                    mapOf(
                        "status" to status,
                        "responded_at" to System.currentTimeMillis()
                    )
                ) {
                    filter {
                        eq("id", rideRequestId)
                    }
                }
            Log.d(TAG, "Updated to: $status")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}")
            false
        }
    }

    suspend fun getUnreadNotifications(userId: String): List<NotificationData> {
        return try {
            supabase.postgrest["notifications"]
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("is_read", false)
                    }
                    order(column = "created_at", order = Order.ASCENDING)
                    limit(20)
                }
                .decodeList<NotificationData>()
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}")
            emptyList()
        }
    }

    suspend fun markNotificationAsRead(notificationId: Long): Boolean {
        return try {
            supabase.postgrest["notifications"]
                .update(mapOf("is_read" to true)) {
                    filter {
                        eq("id", notificationId)
                    }
                }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}")
            false
        }
    }
}

// ==================== UPDATED DATA CLASSES ====================

@Serializable
data class DriverLocationData(
    @SerialName("driver_id")
    val driver_id: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("vehicle_type")
    val vehicle_type: String,
    @SerialName("is_online")
    val is_online: Boolean,
    @SerialName("last_updated")
    val last_updated: Long
)

@Serializable
data class DriverLocationUpdateData(
    @SerialName("driver_id")
    val driver_id: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("vehicle_type")
    val vehicle_type: String,
    @SerialName("is_online")
    val is_online: Boolean,
    @SerialName("last_updated")
    val last_updated: Long
)

@Serializable
data class RideRequestData(
    val id: Long? = null,
    @SerialName("ride_id")
    val ride_id: String,  // CHANGED: Long -> String (to match UUID)
    @SerialName("driver_id")
    val driver_id: String,
    @SerialName("pickup_latitude")
    val pickup_latitude: Double,
    @SerialName("pickup_longitude")
    val pickup_longitude: Double,
    @SerialName("vehicle_type")
    val vehicle_type: String,
    @SerialName("bid_price")
    val bid_price: Int,
    val status: String,
    @SerialName("sent_at")
    val sent_at: String? = null,
    @SerialName("responded_at")
    val responded_at: String? = null
)

@Serializable
data class RideRequestMetadata(
    @SerialName("pickup_lat")
    val pickup_lat: Double? = null,
    @SerialName("pickup_lon")
    val pickup_lon: Double? = null,
    @SerialName("distance_km")
    val distance_km: Double? = null
)

@Serializable
data class NotificationData(
    val id: Long? = null,
    @SerialName("user_id")
    val user_id: String,
    @SerialName("driver_id")
    val driver_id: String? = null,
    @SerialName("ride_id")
    val ride_id: String? = null,  // CHANGED: Long -> String (to match UUID)
    val type: String,
    val title: String,
    val message: String,
    @SerialName("is_read")
    val is_read: Boolean = false,
    @SerialName("created_at")
    val created_at: String? = null,
    @SerialName("read_at")
    val read_at: String? = null,
    val metadata: RideRequestMetadata? = null
)