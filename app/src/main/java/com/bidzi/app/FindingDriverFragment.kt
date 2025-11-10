package com.bidzi.app

import android.animation.AnimatorSet
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup


import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Color

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.animation.DecelerateInterpolator

import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bidzi.app.supabase.RideBooking
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import kotlin.random.Random


import android.widget.ProgressBar
import android.widget.ScrollView

import android.widget.Toast
import androidx.core.content.ContextCompat


import com.bidzi.app.helper.RideMatchingService
import com.google.android.material.snackbar.Snackbar
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

class FindingDriverFragment : Fragment() {

    // UI Components
    private lateinit var searchingText: TextView
    private lateinit var waitingText: TextView
    private lateinit var vehicleImage: ImageView
    private lateinit var rippleContainer: FrameLayout
    private lateinit var cancelButton: Button
    private lateinit var backButton: ImageView
    private lateinit var vehicleTypeText: TextView
    private lateinit var pickupAddressText: TextView
    private lateinit var dropAddressText: TextView
    private lateinit var distanceText: TextView
    private lateinit var fareText: TextView
    private lateinit var requestProgressBar: ProgressBar

    // Overlay components
    private lateinit var overlayContainer: FrameLayout
    private lateinit var errorOverlay: LinearLayout
    private lateinit var noDriverOverlay: LinearLayout
    private lateinit var contentScrollView: ScrollView
    private lateinit var statusContainer: LinearLayout

    // Data
    private lateinit var rideBooking: RideBooking
    private lateinit var rideMatchingService: RideMatchingService
    private val supabase by lazy { MyApplication.supabase }

    // State
    private var waitingSeconds = 0
    private var timerRunning = false
    private var driverFound = false
    private var isRequestSent = false
    private val dotAnimations = arrayOf("", ".", "..", "...")
    private var dotIndex = 0
    private val timeToFindDriver = Random.nextInt(8, 20)

    // Coroutine Jobs for cleanup
    private var searchJob: Job? = null
    private var timerJob: Job? = null
    private var animationJobs = mutableListOf<Job>()

    companion object {
        private const val TAG = "FindingDriverFragment"
        private const val MAX_RETRY_ATTEMPTS = 2
        private const val SEARCH_TIMEOUT_SECONDS = 45
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "onCreateView() called")
        return try {
            inflater.inflate(R.layout.fragment_finding_driver, container, false)
        } catch (e: Exception) {
            Log.e(TAG, "Error inflating view: ${e.message}", e)
            Toast.makeText(context, "Error loading screen", Toast.LENGTH_SHORT).show()
            null
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated() called")

        try {
            // Get arguments with validation
            val args = FindingDriverFragmentArgs.fromBundle(requireArguments())
            rideBooking = args.rideBooking

            Log.d(TAG, "Ride booking loaded: ID=${rideBooking.id}, Vehicle=${rideBooking.vehicleType}, Bid=₹${rideBooking.bid}")

            // Validate ride booking data
            if (!validateRideBooking()) {
                Log.e(TAG, "Invalid ride booking data")
                showErrorOverlay("Invalid ride details. Please check your booking information.", isCritical = true)
                return
            }

            // Initialize service
            rideMatchingService = RideMatchingService(supabase)

            // Initialize views
            if (!initializeViews(view)) {
                Log.e(TAG, "Failed to initialize views")
                showErrorOverlay("Failed to load interface. Please restart the app.", isCritical = true)
                return
            }

            // Display booking details
            displayBookingDetails()

            // Start animations
            startAllAnimations()

            // Send ride requests to nearby drivers
            sendRideRequestToNearbyDrivers()

            // Set up button listeners
            setupClickListeners()

            Log.d(TAG, "Fragment initialization complete")

        } catch (e: Exception) {
            Log.e(TAG, "Critical error in onViewCreated: ${e.message}", e)
            e.printStackTrace()
            showErrorOverlay("Unexpected error occurred. Please try again.", isCritical = true)
        }
    }

    private fun validateRideBooking(): Boolean {
        return try {
            rideBooking.id.isNotEmpty() &&
                    rideBooking.vehicleType.isNotEmpty() &&
                    rideBooking.pickupLat != 0.0 &&
                    rideBooking.pickupLng != 0.0 &&
                    rideBooking.bid > 0
        } catch (e: Exception) {
            Log.e(TAG, "Validation error: ${e.message}", e)
            false
        }
    }

    private fun initializeViews(view: View): Boolean {
        return try {
            searchingText = view.findViewById(R.id.searchingText)
            waitingText = view.findViewById(R.id.waitingText)
            vehicleImage = view.findViewById(R.id.vehicleImage)
            rippleContainer = view.findViewById(R.id.rippleContainer)
            cancelButton = view.findViewById(R.id.cancelButton)
            backButton = view.findViewById(R.id.backButton)
            vehicleTypeText = view.findViewById(R.id.vehicleTypeText)
            pickupAddressText = view.findViewById(R.id.pickupAddressText)
            dropAddressText = view.findViewById(R.id.dropAddressText)
            distanceText = view.findViewById(R.id.distanceText)
            fareText = view.findViewById(R.id.fareText)
            requestProgressBar = view.findViewById(R.id.waitingProgress)

            // Overlay components
            overlayContainer = view.findViewById(R.id.overlayContainer)
            errorOverlay = view.findViewById(R.id.errorOverlay)
            noDriverOverlay = view.findViewById(R.id.noDriverOverlay)
            contentScrollView = view.findViewById(R.id.contentScrollView)
            statusContainer = view.findViewById(R.id.statusContainer)

            Log.d(TAG, "All views initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing views: ${e.message}", e)
            false
        }
    }

    private fun setupClickListeners() {
        cancelButton.setOnClickListener {
            Log.d(TAG, "Cancel button clicked")
            cancelRideBooking()
        }
        backButton.setOnClickListener {
            Log.d(TAG, "Back button clicked")
            cancelRideBooking()
        }

        // Error overlay buttons
        view?.findViewById<Button>(R.id.errorRetryButton)?.setOnClickListener {
            Log.d(TAG, "Error retry button clicked")
            hideOverlay()
            retrySearch()
        }

        view?.findViewById<Button>(R.id.errorCancelButton)?.setOnClickListener {
            Log.d(TAG, "Error cancel button clicked")
            cancelRideBooking()
        }

        // No driver overlay buttons
        view?.findViewById<Button>(R.id.noDriverKeepWaitingButton)?.setOnClickListener {
            Log.d(TAG, "Keep waiting button clicked")
            hideOverlay()
            if (!timerRunning) {
                timerRunning = true
                startDriverFoundSimulation()
            }
        }

        view?.findViewById<Button>(R.id.noDriverRetryButton)?.setOnClickListener {
            Log.d(TAG, "No driver retry button clicked")
            hideOverlay()
            retrySearch()
        }

        view?.findViewById<Button>(R.id.noDriverCancelButton)?.setOnClickListener {
            Log.d(TAG, "No driver cancel button clicked")
            cancelRideBooking()
        }
    }

    private fun displayBookingDetails() {
        try {
            pickupAddressText.text = rideBooking.pickupAddress.takeIf { it.isNotEmpty() } ?: "Current Location"
            dropAddressText.text = rideBooking.dropAddress.takeIf { it.isNotEmpty() } ?: "Destination"
            distanceText.text = String.format("%.1f km", rideBooking.distanceKm)
            fareText.text = "₹${rideBooking.bid}"

            // Set vehicle type and image
            when (rideBooking.vehicleType.lowercase()) {
                "bike" -> {
                    vehicleTypeText.text = "Bike"
                    vehicleImage.setImageResource(R.drawable.motorbike)
                }
                "auto" -> {
                    vehicleTypeText.text = "Auto"
                    vehicleImage.setImageResource(R.drawable.tuk_tuk2)
                }
                "car" -> {
                    vehicleTypeText.text = "Car"
                    vehicleImage.setImageResource(R.drawable.taxi)
                }
                else -> {
                    vehicleTypeText.text = rideBooking.vehicleType.replaceFirstChar { it.uppercase() }
                    vehicleImage.setImageResource(R.drawable.tuk_tuk2)
                }
            }

            Log.d(TAG, "Booking details displayed: Pickup=${rideBooking.pickupAddress}, Drop=${rideBooking.dropAddress}, Distance=${rideBooking.distanceKm}km, Fare=₹${rideBooking.bid}")
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying booking details: ${e.message}", e)
        }
    }

    private fun startAllAnimations() {
        timerRunning = true
        startSearchingTextAnimation()
        startWaitingTimer()
        startRippleAnimation()
        startVehicleFloatingAnimation()
        Log.d(TAG, "All animations started")
    }

    // In FindingDriverFragment.kt, replace the sendRideRequestToNearbyDrivers function:

    private fun sendRideRequestToNearbyDrivers() {
        if (isRequestSent) {
            Log.w(TAG, "Request already sent, skipping duplicate")
            return
        }

        Log.d(TAG, "Starting driver search process...")
        isRequestSent = true

        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                Log.d(TAG, "Using ride ID (UUID): ${rideBooking.id}")
                requestProgressBar?.visibility = View.VISIBLE

                // FIXED: Pass the UUID string directly (no conversion needed)
                val driverIds = withTimeout(SEARCH_TIMEOUT_SECONDS * 1000L) {
                    rideMatchingService.sendRideRequestsToNearbyDrivers(
                        rideId = rideBooking.id,  // Pass UUID string directly
                        pickupLat = rideBooking.pickupLat,
                        pickupLon = rideBooking.pickupLng,
                        vehicleType = rideBooking.vehicleType,
                        bidPrice = rideBooking.bid.toInt(),
                        maxDrivers = 25
                    )
                }

                requestProgressBar?.visibility = View.GONE
                Log.d(TAG, "Successfully sent requests to ${driverIds.size} drivers")

                when {
                    driverIds.isEmpty() -> {
                        Log.w(TAG, "No drivers found nearby")
                        showNoDriversOverlay()
                    }
                    driverIds.size < 5 -> {
                        Log.i(TAG, "Limited drivers found: ${driverIds.size}")
                        showSnackbar("Request sent to ${driverIds.size} nearby drivers", Snackbar.LENGTH_SHORT)
                        startDriverFoundSimulation()
                    }
                    else -> {
                        Log.i(TAG, "Good number of drivers found: ${driverIds.size}")
                        showSnackbar("Request sent to ${driverIds.size} drivers", Snackbar.LENGTH_SHORT)
                        startDriverFoundSimulation()
                    }
                }

            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Request timeout after ${SEARCH_TIMEOUT_SECONDS}s")
                handleSearchError("Search is taking longer than expected", isTimeout = true)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Invalid data: ${e.message}", e)
                handleSearchError("Invalid ride information", isCritical = true)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending ride requests: ${e.message}", e)
                e.printStackTrace()
                handleSearchError("Unable to connect to server")
            }
        }
    }



    private fun startDriverFoundSimulation() {
        Log.d(TAG, "Starting driver found simulation (will trigger in ${timeToFindDriver}s)")

        timerJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(timeToFindDriver * 1000L)

            if (timerRunning && isAdded && !driverFound) {
                Log.i(TAG, "Simulation complete - driver found!")
                driverFound = true
                onDriverFound()
            } else {
                Log.d(TAG, "Simulation cancelled: timerRunning=$timerRunning, isAdded=$isAdded, driverFound=$driverFound")
            }
        }
    }

    private fun handleSearchError(message: String, isTimeout: Boolean = false, isCritical: Boolean = false) {
        if (!isAdded) return

        requestProgressBar?.visibility = View.GONE
        timerRunning = false

        when {
            isCritical -> {
                showErrorOverlay(message, isCritical = true)
            }
            isTimeout -> {
                showNoDriversOverlay(isTimeout = true)
            }
            else -> {
                showErrorOverlay(message)
            }
        }
    }

    private fun showNoDriversOverlay(isTimeout: Boolean = false) {
        if (!isAdded) return

        Log.d(TAG, "Showing no drivers overlay (timeout=$isTimeout)")

        view?.findViewById<TextView>(R.id.noDriverTitle)?.text =
            if (isTimeout) "Taking Longer Than Expected" else "No Drivers Available"

        view?.findViewById<TextView>(R.id.noDriverMessage)?.text =
            if (isTimeout)
                "We're still searching for drivers in your area. This is taking longer than usual."
            else
                "No drivers are available nearby at the moment.\n\nTry increasing your offer or wait a bit."

        showOverlay(noDriverOverlay)
    }

    private fun showErrorOverlay(message: String, isCritical: Boolean = false) {
        if (!isAdded) return

        Log.e(TAG, "Showing error overlay: $message (critical=$isCritical)")

        view?.findViewById<TextView>(R.id.errorMessage)?.text = message
        view?.findViewById<Button>(R.id.errorRetryButton)?.visibility =
            if (isCritical) View.GONE else View.VISIBLE

        showOverlay(errorOverlay)
    }

    private fun showOverlay(overlay: View) {
        overlayContainer.visibility = View.VISIBLE
        errorOverlay.visibility = if (overlay == errorOverlay) View.VISIBLE else View.GONE
        noDriverOverlay.visibility = if (overlay == noDriverOverlay) View.VISIBLE else View.GONE

        // Animate overlay entrance
        overlay.alpha = 0f
        overlay.animate()
            .alpha(1f)
            .setDuration(300)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun hideOverlay() {
        overlayContainer.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                if (isAdded) {
                    overlayContainer.visibility = View.GONE
                    overlayContainer.alpha = 1f
                }
            }
            .start()
    }

    private fun showSnackbar(message: String, duration: Int = Snackbar.LENGTH_SHORT) {
        if (!isAdded) return

        view?.let { v ->
            Snackbar.make(v, message, duration)
                .setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.snackbar_bg))
                .setTextColor(Color.WHITE)
                .show()
        }
    }

    private fun retrySearch() {
        Log.d(TAG, "Retrying driver search...")
        isRequestSent = false
        driverFound = false
        timerRunning = true
        waitingSeconds = 0
        sendRideRequestToNearbyDrivers()
    }

    private fun onDriverFound() {
        if (!isAdded || driverFound) return

        timerRunning = false
        driverFound = true

        Log.i(TAG, "Driver found! Navigating to available drivers screen...")

        try {
            val action = FindingDriverFragmentDirections
                .actionFindingDriverFragmentToAvailableDriversFragment(rideBooking)
            findNavController().navigate(action)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Navigation argument error: ${e.message}", e)
            showSnackbar("Error loading driver details")
        } catch (e: Exception) {
            Log.e(TAG, "Navigation error: ${e.message}", e)
            showSnackbar("Unable to show available drivers")
        }
    }

    private fun cancelRideBooking() {
        if (!isAdded) return

        Log.d(TAG, "Cancelling ride booking: ID=${rideBooking.id}")

        // Stop all operations
        timerRunning = false
        driverFound = false
        cancelAllJobs()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Show progress
                cancelButton.isEnabled = false
                cancelButton.text = "Cancelling..."

                // Update ride status
                supabase.postgrest["ride_bookings"]
                    .update(mapOf("status" to "cancelled")) {
                        filter { eq("id", rideBooking.id) }
                    }

                Log.i(TAG, "Ride booking cancelled successfully")
                showSnackbar("Ride cancelled")

                delay(300)

                if (isAdded) {
                    findNavController().navigateUp()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling booking: ${e.message}", e)

                if (isAdded) {
                    showSnackbar("Cancellation failed, but going back")
                    delay(500)
                    findNavController().navigateUp()
                }
            }
        }
    }

    // ANIMATIONS

    private fun startSearchingTextAnimation() {
        val job = viewLifecycleOwner.lifecycleScope.launch {
            while (timerRunning && isAdded) {
                try {
                    searchingText.text = "Searching for drivers${dotAnimations[dotIndex]}"
                    dotIndex = (dotIndex + 1) % dotAnimations.size
                    delay(500)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in text animation: ${e.message}")
                    break
                }
            }
        }
        animationJobs.add(job)
    }

    private fun startWaitingTimer() {
        val job = viewLifecycleOwner.lifecycleScope.launch {
            while (timerRunning && isAdded) {
                try {
                    waitingText.text = "Waiting ${waitingSeconds}s"
                    waitingSeconds++
                    delay(1000)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in timer: ${e.message}")
                    break
                }
            }
        }
        animationJobs.add(job)
    }

    private fun startRippleAnimation() {
        val job = viewLifecycleOwner.lifecycleScope.launch {
            while (timerRunning && isAdded) {
                try {
                    val ripple1 = rippleContainer.findViewById<View>(R.id.ripple1)
                    ripple1?.let {
                        AnimatorSet().apply {
                            playTogether(
                                ObjectAnimator.ofFloat(it, View.SCALE_X, 1f, 1.8f).apply { duration = 1200 },
                                ObjectAnimator.ofFloat(it, View.SCALE_Y, 1f, 1.8f).apply { duration = 1200 },
                                ObjectAnimator.ofFloat(it, View.ALPHA, 0.8f, 0f).apply { duration = 1200 }
                            )
                            start()
                        }
                    }
                    delay(1200)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in ripple animation: ${e.message}")
                    break
                }
            }
        }
        animationJobs.add(job)
    }

    private fun startVehicleFloatingAnimation() {
        try {
            val floatAnimator = ValueAnimator.ofFloat(0f, -20f, 0f).apply {
                duration = 2000
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener { animation ->
                    if (timerRunning && isAdded) {
                        vehicleImage?.translationY = animation.animatedValue as Float
                    }
                }
            }
            floatAnimator.start()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting float animation: ${e.message}")
        }
    }

    private fun cancelAllJobs() {
        Log.d(TAG, "Cancelling all background jobs")
        searchJob?.cancel()
        timerJob?.cancel()
        animationJobs.forEach { it.cancel() }
        animationJobs.clear()
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause() called")
        timerRunning = false
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume() called - driverFound=$driverFound")
        if (!driverFound && isRequestSent && overlayContainer.visibility != View.VISIBLE) {
            timerRunning = true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView() called")
        timerRunning = false
        driverFound = false
        cancelAllJobs()
    }
}


