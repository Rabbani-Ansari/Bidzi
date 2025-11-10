package com.bidzi.app.shared

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationProvider
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result ->
        continuation.resume(result)
    }
    addOnFailureListener { exception ->
        continuation.resumeWithException(exception)
    }
    addOnCanceledListener {
        continuation.cancel()
    }
}




interface UserLocationProvider {
    suspend fun getCurrentLocation(): Location?
}




class UserLocationProviderImpl(private val context: Context) : UserLocationProvider {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(): Location? = withContext(Dispatchers.Main) {
        try {
            // Try last location first (fastest)
            val lastLocation = fusedLocationClient.lastLocation.await()
            if (lastLocation != null) {
                Log.d("LocationProvider", "Using last location: ${lastLocation.latitude}, ${lastLocation.longitude}")
                return@withContext lastLocation
            }

            // Request fresh location with timeout
            Log.d("LocationProvider", "Requesting fresh location...")
            val location = withTimeoutOrNull(10000L) {
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    null
                ).await()
            }

            if (location == null) {
                Log.e("LocationProvider", "Could not get location within timeout")
            } else {
                Log.d("LocationProvider", "Got fresh location: ${location.latitude}, ${location.longitude}")
            }

            location

        } catch (e: Exception) {
            Log.e("LocationProvider", "Error getting location", e)
            null
        }
    }
}
//class LocationProviderImpl(private val context: Context) : LocationProvider {
//    override suspend fun getCurrentLocation(): Location? {
//        // TEMP: Return mock location for testing
//        return Location("mock").apply {
//            latitude = 16.7050
//            longitude = 74.2433
//        }
//    }
//}