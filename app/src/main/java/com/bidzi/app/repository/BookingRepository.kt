package com.bidzi.app.repository

// LocationRepository.kt
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.util.Log
import androidx.core.app.ActivityCompat
import com.bidzi.app.MyApplication
import com.bidzi.app.adapter.PlaceSuggestion
import com.bidzi.app.adapter.VehicleType
import com.bidzi.app.auth.SessionUser
import com.bidzi.app.supabase.RideBooking
import com.bidzi.app.supabase.RideBookingInsert
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.coroutines.resume

// BookingRepository.kt
class BookingRepository {

    suspend fun createBooking(
        pickupLocation: PlaceSuggestion,
        dropLocation: PlaceSuggestion,
        vehicleType: VehicleType,
        distanceKm: Double,
        bid: Int,
        note: String?
    ): Result<RideBooking> = withContext(Dispatchers.IO) {
        try {
            val firebaseUserId = SessionUser.currentUserId
            val bookingId = UUID.randomUUID().toString()

            val currentTimestamp = SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
                Locale.getDefault()
            ).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date())

            val bookingInsert = RideBookingInsert(
                id = bookingId,
                userId = firebaseUserId.toString(),
                pickupAddress = pickupLocation.primaryText,
                pickupLat = pickupLocation.latitude!!,
                pickupLng = pickupLocation.longitude!!,
                dropAddress = dropLocation.primaryText,
                dropLat = dropLocation.latitude!!,
                dropLng = dropLocation.longitude!!,
                vehicleType = vehicleType.id,
                distanceKm = distanceKm,
                bid = bid,
                note = note,
                createdAt = currentTimestamp
            )

            MyApplication.supabase.from("ride_bookings")
                .insert(bookingInsert)

            Log.d("BookingRepository", "Booking created with ID: $bookingId")

            val rideBooking = bookingInsert.toRideBooking()
            Result.success(rideBooking)
        } catch (e: Exception) {
            Log.e("BookingRepository", "Error creating booking: ${e.message}", e)
            Result.failure(e)
        }
    }
}