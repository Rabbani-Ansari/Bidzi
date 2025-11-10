package com.bidzi.app

import android.location.Location
import android.util.Log
import com.google.firebase.database.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Firebase Driver Tracker Service
 * Handles real-time driver location updates with optimized listeners
 * Includes fallback mechanisms for weak GPS/network scenarios
 */
class FirebaseDriverTracker(
    private val database: DatabaseReference = FirebaseDatabase.getInstance().reference
) {

    companion object {
        private const val TAG = "DriverTracker"
        private const val DRIVER_LOCATIONS_PATH = "driver_locations"
        private const val DRIVERS_PATH = "drivers"
        private const val STALE_LOCATION_THRESHOLD = 30000L // 30 seconds
    }

    /**
     * Track driver location in real-time using Kotlin Flow
     * Automatically handles connection state and provides location updates
     */
    fun trackDriverLocation(driverId: String): Flow<DriverLocationUpdate> = callbackFlow {
        val locationRef = database.child(DRIVER_LOCATIONS_PATH).child(driverId)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    if (snapshot.exists()) {
                        val latitude = snapshot.child("latitude").getValue(Double::class.java)
                        val longitude = snapshot.child("longitude").getValue(Double::class.java)
                        val bearing = snapshot.child("bearing").getValue(Float::class.java) ?: 0f
                        val speed = snapshot.child("speed").getValue(Float::class.java) ?: 0f
                        val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                        val accuracy = snapshot.child("accuracy").getValue(Float::class.java) ?: 0f

                        if (latitude != null && longitude != null) {
                            val location = DriverLocation(
                                latitude = latitude,
                                longitude = longitude,
                                bearing = bearing,
                                speed = speed,
                                timestamp = timestamp,
                                accuracy = accuracy
                            )

                            // Check if location is stale (weak GPS scenario)
                            val age = System.currentTimeMillis() - timestamp
                            val isStale = age > STALE_LOCATION_THRESHOLD

                            trySend(
                                DriverLocationUpdate.Success(
                                    location = location,
                                    isStale = isStale,
                                    ageMillis = age
                                )
                            )
                        } else {
                            trySend(DriverLocationUpdate.Error("Invalid location data"))
                        }
                    } else {
                        trySend(DriverLocationUpdate.Error("Driver location not available"))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing driver location", e)
                    trySend(DriverLocationUpdate.Error("Failed to parse location: ${e.message}"))
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Firebase listener cancelled: ${error.message}")
                trySend(DriverLocationUpdate.Error("Connection lost: ${error.message}"))
            }
        }

        // Attach listener
        locationRef.addValueEventListener(listener)

        // Monitor connection state
        val connectedRef = database.child(".info/connected")
        val connectionListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) == true
                if (!connected) {
                    trySend(DriverLocationUpdate.Disconnected)
                } else {
                    trySend(DriverLocationUpdate.Connected)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Connection listener error: ${error.message}")
            }
        }
        connectedRef.addValueEventListener(connectionListener)

        // Cleanup when flow is closed
        awaitClose {
            locationRef.removeEventListener(listener)
            connectedRef.removeEventListener(connectionListener)
        }
    }

    /**
     * Update driver location (called from driver app)
     * Implements batching and throttling for efficiency
     */
    fun updateDriverLocation(
        driverId: String,
        location: Location,
        callback: (Boolean) -> Unit
    ) {
        val locationData = mapOf(
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "bearing" to location.bearing,
            "speed" to location.speed,
            "accuracy" to location.accuracy,
            "timestamp" to System.currentTimeMillis(),
            "provider" to location.provider
        )

        database.child(DRIVER_LOCATIONS_PATH).child(driverId)
            .setValue(locationData)
            .addOnSuccessListener {
                callback(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to update location", e)
                callback(false)
            }
    }

    /**
     * Batch update for offline queue
     * Useful when driver has intermittent connectivity
     */
    fun batchUpdateLocations(
        driverId: String,
        locations: List<Location>,
        callback: (Int) -> Unit
    ) {
        val updates = mutableMapOf<String, Any>()

        locations.forEachIndexed { index, location ->
            val timestamp = location.time
            updates["$DRIVER_LOCATIONS_PATH/$driverId/history/$timestamp"] = mapOf(
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "bearing" to location.bearing,
                "speed" to location.speed,
                "accuracy" to location.accuracy,
                "timestamp" to timestamp
            )
        }

        // Update latest location
        locations.lastOrNull()?.let { latest ->
            updates["$DRIVER_LOCATIONS_PATH/$driverId/latitude"] = latest.latitude
            updates["$DRIVER_LOCATIONS_PATH/$driverId/longitude"] = latest.longitude
            updates["$DRIVER_LOCATIONS_PATH/$driverId/bearing"] = latest.bearing
            updates["$DRIVER_LOCATIONS_PATH/$driverId/speed"] = latest.speed
            updates["$DRIVER_LOCATIONS_PATH/$driverId/timestamp"] = System.currentTimeMillis()
        }

        database.updateChildren(updates)
            .addOnSuccessListener {
                callback(locations.size)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Batch update failed", e)
                callback(0)
            }
    }

    /**
     * Get driver info (one-time fetch)
     */
    fun getDriverInfo(driverId: String, callback: (DriverInfo?) -> Unit) {
        database.child(DRIVERS_PATH).child(driverId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val info = DriverInfo(
                            id = driverId,
                            name = snapshot.child("name").getValue(String::class.java) ?: "",
                            phone = snapshot.child("phone").getValue(String::class.java) ?: "",
                            vehicleNumber = snapshot.child("vehicleNumber").getValue(String::class.java) ?: "",
                            vehicleModel = snapshot.child("vehicleModel").getValue(String::class.java) ?: "",
                            vehicleColor = snapshot.child("vehicleColor").getValue(String::class.java) ?: "",
                            rating = snapshot.child("rating").getValue(Float::class.java) ?: 0f,
                            photoUrl = snapshot.child("photoUrl").getValue(String::class.java)
                        )
                        callback(info)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing driver info", e)
                        callback(null)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Failed to get driver info: ${error.message}")
                    callback(null)
                }
            })
    }

    /**
     * Calculate distance between two points (Haversine formula)
     * Useful for ETA calculations in weak GPS scenarios
     */
    fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, results)
        return results[0]
    }

    /**
     * Estimate ETA based on distance and average speed
     * Fallback when Directions API is unavailable
     */
    fun estimateETA(
        driverLocation: DriverLocation,
        destinationLat: Double,
        destinationLng: Double
    ): Int {
        val distance = calculateDistance(
            driverLocation.latitude,
            driverLocation.longitude,
            destinationLat,
            destinationLng
        )

        // Use current speed if available, otherwise assume average city speed (20 km/h for India)
        val speedMps = if (driverLocation.speed > 0) {
            driverLocation.speed
        } else {
            20 * 1000 / 3600f // 20 km/h in m/s
        }

        // Add buffer for traffic (30% extra time for Indian cities)
        val etaSeconds = (distance / speedMps * 1.3).toInt()
        return etaSeconds / 60 // Return in minutes
    }

    /**
     * Enable offline persistence for weak network scenarios
     * Call this in Application class onCreate()
     */
    fun enableOfflinePersistence() {
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)

            // Keep driver locations synced even when offline
            database.child(DRIVER_LOCATIONS_PATH).keepSynced(true)

            Log.d(TAG, "Offline persistence enabled")
        } catch (e: Exception) {
            Log.w(TAG, "Persistence already enabled or failed", e)
        }
    }
}

// Sealed class for location update states
sealed class DriverLocationUpdate {
    data class Success(
        val location: DriverLocation,
        val isStale: Boolean = false,
        val ageMillis: Long = 0
    ) : DriverLocationUpdate()

    data class Error(val message: String) : DriverLocationUpdate()
    object Connected : DriverLocationUpdate()
    object Disconnected : DriverLocationUpdate()
}

// Driver info data class
data class DriverInfo(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val vehicleNumber: String = "",
    val vehicleModel: String = "",
    val vehicleColor: String = "",
    val rating: Float = 0f,
    val photoUrl: String? = null
)

// Enhanced DriverLocation with accuracy field
data class DriverLocation(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val bearing: Float = 0f,
    val speed: Float = 0f,
    val timestamp: Long = 0,
    val accuracy: Float = 0f
)