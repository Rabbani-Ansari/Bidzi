package com.bidzi.app.shared

import android.util.Log
import com.bidzi.app.model.SharedRide
import com.bidzi.app.model.SharedRideDto
import com.google.firebase.auth.FirebaseAuth
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class SharedRidesService(private val supabase: SupabaseClient) {

    companion object {
        private const val MAX_DISTANCE_KM = 5.0 // Show rides within 5km
        private const val MIN_DEPARTURE_MINUTES = 5 // Don't show rides leaving in < 5 min
    }

    private var realtimeChannel: RealtimeChannel? = null

    /**
     * Fetch available shared rides near user's location
     */
    suspend fun getAvailableRides(
        userLat: Double,
        userLng: Double
    ): Result<List<SharedRide>> = withContext(Dispatchers.IO) {
        try {
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
            val minDepartureTime = getMinDepartureTime()

            // Query the view - Updated syntax with Order enum
            val allRides = supabase.from("available_rides_with_drivers")
                .select() {
                    filter {
                        gte("departure_time", minDepartureTime)
                    }
                    order("departure_time", order = Order.ASCENDING)
                }
                .decodeList<SharedRideDto>()

            // Filter by distance and check if user already joined
            val filteredRides = allRides
                .mapNotNull { dto ->
                    val pickupDistance = calculateDistance(
                        userLat, userLng,
                        dto.pickup_lat, dto.pickup_lng
                    )

                    // Only include if within range
                    if (pickupDistance > MAX_DISTANCE_KM) return@mapNotNull null

                    // Check if user already joined
                    val alreadyJoined = isUserParticipant(dto.id, currentUserId)
                    if (alreadyJoined) return@mapNotNull null

                    // Convert to domain model
                    SharedRide(
                        id = dto.id,
                        driverId = dto.driver_id,
                        driverName = dto.driver_name,
                        driverRating = dto.driver_rating,
                        driverTotalTrips = dto.driver_total_trips,
                        driverIsOnline = dto.driver_is_online,
                        vehicleType = dto.vehicle_type,
                        vehicleNumber = dto.vehicle_number,
                        pickupLocation = dto.pickup_location,
                        pickupLat = dto.pickup_lat,
                        pickupLng = dto.pickup_lng,
                        dropLocation = dto.drop_location,
                        dropLat = dto.drop_lat,
                        dropLng = dto.drop_lng,
                        distance = dto.distance,
                        estimatedDuration = dto.estimated_duration,
                        basePrice = dto.base_price,
                        totalSeats = dto.total_seats,
                        availableSeats = dto.available_seats,
                        departureTime = dto.departure_time,
                        status = dto.status,
                        pickupDistanceFromUser = pickupDistance
                    )
                }
                .sortedBy { it.pickupDistanceFromUser } // Sort by nearest first

            Result.success(filteredRides)
        } catch (e: Exception) {
            Log.e("SharedRidesService", "Error fetching rides", e)
            Result.failure(e)
        }
    }

    /**
     * Check if user is already a participant
     */
    private suspend fun isUserParticipant(rideId: String, userId: String): Boolean {
        if (userId.isEmpty()) return false

        return try {
            val result = supabase.from("ride_participants")
                .select(columns = Columns.list("id")) {
                    filter {
                        eq("ride_id", rideId)
                        eq("user_id", userId)
                        neq("status", "REJECTED")
                    }
                    limit(1)
                }
                .decodeSingleOrNull<JsonObject>()

            result != null
        } catch (e: Exception) {
            Log.e("SharedRidesService", "Error checking participant", e)
            false
        }
    }

    /**
     * Join a shared ride
     */
    suspend fun joinRide(
        rideId: String,
        userId: String,
        finalPrice: Double,
        seatsBooked: Int = 1
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Check if seats are still available
            val ride = supabase.from("shared_rides")
                .select(columns = Columns.list("available_seats")) {
                    filter {
                        eq("id", rideId)
                    }
                }
                .decodeSingle<JsonObject>()

            val availableSeats = ride["available_seats"]?.jsonPrimitive?.int ?: 0

            if (availableSeats < seatsBooked) {
                return@withContext Result.failure(
                    Exception("Not enough seats available")
                )
            }

            // Insert participant
            val participant = buildJsonObject {
                put("ride_id", rideId)
                put("user_id", userId)
                put("seats_booked", seatsBooked)
                put("final_price", finalPrice)
                put("status", "PENDING") // Driver needs to confirm
            }

            supabase.from("ride_participants")
                .insert(participant)

            Result.success("Join request sent! Waiting for driver confirmation.")
        } catch (e: Exception) {
            Log.e("SharedRidesService", "Error joining ride", e)
            Result.failure(e)
        }
    }

    /**
     * Real-time subscription for ride updates
     * Call this from a CoroutineScope or suspend function
     */
    suspend fun subscribeToRideUpdates(
        userLat: Double,
        userLng: Double,
        onUpdate: (List<SharedRide>) -> Unit
    ) {
        // Unsubscribe from previous channel if exists
        realtimeChannel?.unsubscribe()

        val channel = supabase.channel("shared_rides_channel")

        channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "shared_rides"
        }.onEach {
            // Reload rides when any change happens
            val result = getAvailableRides(userLat, userLng)
            result.getOrNull()?.let(onUpdate)
        }.launchIn(CoroutineScope(Dispatchers.IO))

        // Subscribe is now a suspend function
        channel.subscribe()

        // Store reference for cleanup
        realtimeChannel = channel
    }

    /**
     * Unsubscribe from realtime updates
     */
    suspend fun unsubscribeFromRideUpdates() {
        realtimeChannel?.unsubscribe()
        realtimeChannel = null
    }

    /**
     * Calculate distance using Haversine formula
     */
    private fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val r = 6371.0 // Earth radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    /**
     * Get minimum departure time
     */
    private fun getMinDepartureTime(): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MINUTE, MIN_DEPARTURE_MINUTES)
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        return formatter.format(calendar.time)
    }
}