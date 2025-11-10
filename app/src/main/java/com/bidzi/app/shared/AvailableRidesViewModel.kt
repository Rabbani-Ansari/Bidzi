package com.bidzi.app.shared

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bidzi.app.model.SharedRide
import com.google.firebase.auth.FirebaseAuth
import io.github.jan.supabase.realtime.RealtimeChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


sealed class RidesUiState {
    object Loading : RidesUiState()
    object Empty : RidesUiState()
    data class Success(val rides: List<SharedRide>) : RidesUiState()
    data class Error(val message: String) : RidesUiState()
}

sealed class JoinRideState {
    object Idle : JoinRideState()
    object Loading : JoinRideState()
    data class Success(val message: String) : JoinRideState()
    data class Error(val message: String) : JoinRideState()
}

class AvailableRidesViewModel(
    private val service: SharedRidesService,
    private val locationProvider: UserLocationProvider
) : ViewModel() {

    private val _ridesState = MutableStateFlow<RidesUiState>(RidesUiState.Loading)
    val ridesState: StateFlow<RidesUiState> = _ridesState.asStateFlow()

    private val _joinRideState = MutableStateFlow<JoinRideState>(JoinRideState.Idle)
    val joinRideState: StateFlow<JoinRideState> = _joinRideState.asStateFlow()

    private var realtimeChannel: RealtimeChannel? = null
    private var userLocation: Location? = null
//
//    init {
//        loadRides()
//    }

    fun loadRides() {
        viewModelScope.launch {
            _ridesState.value = RidesUiState.Loading

            // Get user location
            userLocation = locationProvider.getCurrentLocation()
            if (userLocation == null) {
                _ridesState.value = RidesUiState.Error("Unable to get your location. Please enable GPS.")
                return@launch
            }

            // Fetch rides
            val result = service.getAvailableRides(
                userLocation!!.latitude,
                userLocation!!.longitude
            )

            result.fold(
                onSuccess = { rides ->
                    _ridesState.value = if (rides.isEmpty()) {
                        RidesUiState.Empty
                    } else {
                        // Start real-time updates
                        startRealtimeUpdates()
                        RidesUiState.Success(rides)
                    }
                },
                onFailure = { error ->
                    _ridesState.value = RidesUiState.Error(
                        error.message ?: "Failed to load rides"
                    )
                }
            )
        }
    }

    private fun startRealtimeUpdates() {
        userLocation?.let { location ->
            viewModelScope.launch {
                // Unsubscribe from previous channel if exists
                realtimeChannel?.unsubscribe()
                realtimeChannel = null

                // Subscribe to updates (now suspend function)
                service.subscribeToRideUpdates(
                    userLat = location.latitude,
                    userLng = location.longitude,
                    onUpdate = { updatedRides ->
                        _ridesState.value = if (updatedRides.isEmpty()) {
                            RidesUiState.Empty
                        } else {
                            RidesUiState.Success(updatedRides)
                        }
                    }
                )
            }
        }
    }

    fun joinRide(ride: SharedRide) {
        viewModelScope.launch {
            _joinRideState.value = JoinRideState.Loading

            val userId = FirebaseAuth.getInstance().currentUser?.uid
            if (userId == null) {
                _joinRideState.value = JoinRideState.Error("Please login to join ride")
                return@launch
            }

            val result = service.joinRide(
                rideId = ride.id,
                userId = userId,
                finalPrice = ride.basePrice
            )

            result.fold(
                onSuccess = { message ->
                    _joinRideState.value = JoinRideState.Success(message)
                    loadRides() // Refresh list
                },
                onFailure = { error ->
                    _joinRideState.value = JoinRideState.Error(
                        error.message ?: "Failed to join ride"
                    )
                }
            )
        }
    }

    fun resetJoinState() {
        _joinRideState.value = JoinRideState.Idle
    }
    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            realtimeChannel?.unsubscribe()
        }
    }
}

class AvailableRidesViewModelFactory(
    private val service: SharedRidesService,
    private val locationProvider: UserLocationProvider
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AvailableRidesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AvailableRidesViewModel(service, locationProvider) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
