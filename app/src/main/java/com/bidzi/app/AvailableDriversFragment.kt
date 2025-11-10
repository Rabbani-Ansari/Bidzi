package com.bidzi.app

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup


import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bidzi.app.adapter.AvailableDriversAdapter
import com.bidzi.app.adapter.CounterOfferBottomSheet
import com.bidzi.app.supabase.CounterOffer
import com.bidzi.app.supabase.CounterOfferStatus
import com.bidzi.app.supabase.DriverRideResponse
import com.bidzi.app.supabase.OfferedBy
import com.bidzi.app.supabase.RideBooking
import com.bidzi.app.supabase.getCurrentTimestamp
import com.google.android.material.snackbar.Snackbar
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class AvailableDriversFragment : Fragment() {

    private lateinit var rvDrivers: RecyclerView
    private lateinit var tvFromLocation: TextView
    private lateinit var tvToLocation: TextView
    private lateinit var tvYourBid: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var btnBack: ImageView
    private val activeCounterOffers = mutableMapOf<String, CounterOffer>() // driverId -> CounterOffer
    private var counterChannel: RealtimeChannel? = null

    private var realtimeChannel: RealtimeChannel? = null // Store the channel object


    private lateinit var driversAdapter: AvailableDriversAdapter
    private val driversList = mutableListOf<Driver>()

    private val supabase by lazy { MyApplication.supabase }
    private var rideBooking: RideBooking? = null
    private var currentRideId: String? = null
    private var channelId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_available_drivers, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rideBooking = arguments?.getParcelable("ride_booking")
//        currentRideId = rideBooking?.id
        currentRideId="ebef5038-0eac-4d14-bd8b-9535b2cd5b3a"

        initViews(view)
        setupRecyclerView()
        setupClickListeners()
        loadRideDetails()

        if (currentRideId != null) {
            fetchDriverResponses(currentRideId!!)
            setupRealtimeSubscription(currentRideId!!)
            fetchActiveCounterOffers(currentRideId!!) // Load existing counters
            setupRealtimeSubscription(currentRideId!!)
            setupCounterOffersRealtime(currentRideId!!)
        } else {
            Toast.makeText(requireContext(), "Error: No ride ID found", Toast.LENGTH_SHORT).show()
        }
    }



    private fun initViews(view: View) {
        rvDrivers = view.findViewById(R.id.rvDrivers)
        tvFromLocation = view.findViewById(R.id.tvFromLocation)
        tvToLocation = view.findViewById(R.id.tvToLocation)
        tvYourBid = view.findViewById(R.id.tvYourBid)
        tvSubtitle = view.findViewById(R.id.tvSubtitle)
        btnBack = view.findViewById(R.id.btnBack)
    }
    private fun setupRecyclerView() {
        driversAdapter = AvailableDriversAdapter(
            onCounterClick = { driver -> showCounterOfferBottomSheet(driver) },
            onAcceptClick = { driver -> handleAcceptOffer(driver) }
        )

        rvDrivers.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = driversAdapter
        }
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            requireActivity().onBackPressed()
        }
    }

    private fun loadRideDetails() {
        rideBooking?.let { booking ->
            tvFromLocation.text = booking.pickupAddress
            tvToLocation.text = booking.dropAddress
            tvYourBid.text = "Your Bid: ₹${booking.bid}"
        }
    }

    // ========================================
    // FETCH INITIAL DATA
    // ========================================

    private fun fetchDriverResponses(rideId: String) {
        lifecycleScope.launch {
            try {
                val responses = supabase
                    .from("driver_ride_responses")
                    .select {
                        filter {
                            eq("ride_id", rideId)
                        }
                        order("created_at", Order.DESCENDING)
                    }
                    .decodeList<DriverRideResponse>()

                driversList.clear()
                val yourBid = rideBooking?.bid ?: 0

                driversList.addAll(responses.map { response ->
                    val driver = response.toDriver()
                    val savings = if (driver.price < yourBid) yourBid - driver.price else 0
                    driver.copy(savings = savings, rideId = rideId)
                })

                withContext(Dispatchers.Main) {
                    updateDriversList()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                Log.e("AvailableDrivers", "Error fetching responses", e)
            }
        }
    }

    private fun fetchActiveCounterOffers(rideId: String) {
        lifecycleScope.launch {
            try {
                val counterOffers = supabase
                    .from("counter_offers")
                    .select {
                        filter {
                            eq("ride_id", rideId)
                            eq("offered_by", OfferedBy.USER.name)
                        }
                        order("created_at", Order.DESCENDING)
                    }
                    .decodeList<CounterOffer>()

                // Store active counters
                activeCounterOffers.clear()
                counterOffers.forEach { offer ->
                    activeCounterOffers[offer.driverId] = offer
                }

                withContext(Dispatchers.Main) {
                    // Update drivers list with counter data
                    updateDriversWithCounterData()
                }

            } catch (e: Exception) {
                Log.e("AvailableDrivers", "Error fetching counter offers", e)
            }
        }
    }

    private fun updateDriversWithCounterData() {
        val updatedList = driversList.map { driver ->
            val counter = activeCounterOffers[driver.id]
            if (counter != null) {
                driver.copy(
                    hasActiveCounter = true,
                    activeCounterPrice = counter.counterPrice,
                    counterStatus = counter.status,
                    counterOfferedBy = counter.offeredBy
                )
            } else {
                driver
            }
        }

        driversList.clear()
        driversList.addAll(updatedList)
        driversAdapter.submitList(updatedList.toList())
    }


    // ========================================
    // REALTIME SUBSCRIPTIONS
    // ========================================

    private fun setupRealtimeSubscription(rideId: String) {
        lifecycleScope.launch {
            try {
                realtimeChannel = supabase.channel("ride_responses_$rideId")

                val insertFlow = realtimeChannel!!.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                    table = "driver_ride_responses"
                    filter("ride_id", FilterOperator.EQ, rideId)
                }

                val updateFlow = realtimeChannel!!.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                    table = "driver_ride_responses"
                    filter("ride_id", FilterOperator.EQ, rideId)
                }

                insertFlow.onEach { action ->
                    val newResponse = action.decodeRecord<DriverRideResponse>()
                    withContext(Dispatchers.Main) {
                        addNewDriverResponse(newResponse)
                    }
                }.launchIn(lifecycleScope)

                updateFlow.onEach { action ->
                    val updatedResponse = action.decodeRecord<DriverRideResponse>()
                    withContext(Dispatchers.Main) {
                        updateDriverResponse(updatedResponse)
                    }
                }.launchIn(lifecycleScope)

                realtimeChannel!!.subscribe()

            } catch (e: Exception) {
                Log.e("AvailableDrivers", "Error setting up realtime", e)
            }
        }
    }

    private fun setupCounterOffersRealtime(rideId: String) {
        lifecycleScope.launch {
            try {
                val userId = getCurrentUserId()
                counterChannel = supabase.channel("counter_offers_$rideId")

                val insertFlow = counterChannel!!.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                    table = "counter_offers"
                    filter("ride_id", FilterOperator.EQ, rideId)
                    filter("user_id", FilterOperator.EQ, userId)
                }

                val updateFlow = counterChannel!!.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                    table = "counter_offers"
                    filter("ride_id", FilterOperator.EQ, rideId)
                    filter("user_id", FilterOperator.EQ, userId)
                }

                insertFlow.onEach { action ->
                    val counter = action.decodeRecord<CounterOffer>()
                    withContext(Dispatchers.Main) {
                        handleCounterOfferInsert(counter)
                    }
                }.launchIn(lifecycleScope)

                updateFlow.onEach { action ->
                    val counter = action.decodeRecord<CounterOffer>()
                    withContext(Dispatchers.Main) {
                        handleCounterOfferUpdate(counter)
                    }
                }.launchIn(lifecycleScope)

                counterChannel!!.subscribe()

            } catch (e: Exception) {
                Log.e("AvailableDrivers", "Error setting up counter realtime", e)
            }
        }
    }

    // ========================================
    // HANDLE COUNTER OFFER EVENTS
    // ========================================

    private fun handleCounterOfferInsert(counter: CounterOffer) {
        activeCounterOffers[counter.driverId] = counter
        updateDriverInList(counter.driverId) { driver ->
            driver.copy(
                hasActiveCounter = true,
                activeCounterPrice = counter.counterPrice,
                counterStatus = counter.status,
                counterOfferId = counter.id,
                counterOfferedBy = counter.offeredBy
            )
        }
    }

    private fun handleCounterOfferUpdate(counter: CounterOffer) {
        activeCounterOffers[counter.driverId] = counter

        when (counter.status) {
            CounterOfferStatus.ACCEPTED -> {
                showCounterAcceptedSnackbar(counter)
                updateDriverPriceAfterCounterAccepted(counter)

                // THIS IS THE FIX: Update the driver's UI state
                updateDriverInList(counter.driverId) { driver ->
                    driver.copy(
                        hasActiveCounter = true,
                        activeCounterPrice = counter.counterPrice,
                        counterStatus = CounterOfferStatus.ACCEPTED,  // ← This changes PENDING to ACCEPTED
                        counterOfferId = counter.id,
                        counterOfferedBy = counter.offeredBy
                    )
                }
            }

            CounterOfferStatus.REJECTED -> {
                showCounterRejectedSnackbar(counter)

                // Update to show REJECTED state first
                updateDriverInList(counter.driverId) { driver ->
                    driver.copy(
                        hasActiveCounter = true,
                        activeCounterPrice = counter.counterPrice,
                        counterStatus = CounterOfferStatus.REJECTED,  // ← Show rejected state
                        counterOfferId = counter.id,
                        counterOfferedBy = counter.offeredBy
                    )
                }

                // Then clear after 3 seconds
                lifecycleScope.launch {
                    delay(3000)
                    activeCounterOffers.remove(counter.driverId)
                    updateDriverInList(counter.driverId) { driver ->
                        driver.copy(
                            hasActiveCounter = false,
                            activeCounterPrice = null,
                            counterStatus = null,
                            counterOfferId = null,
                            counterOfferedBy = null
                        )
                    }
                }
            }

            else -> {
                updateDriverInList(counter.driverId) { driver ->
                    driver.copy(
                        hasActiveCounter = true,
                        activeCounterPrice = counter.counterPrice,
                        counterStatus = counter.status,
                        counterOfferId = counter.id,
                        counterOfferedBy = counter.offeredBy
                    )
                }
            }
        }
    }

    private fun updateDriverPriceAfterCounterAccepted(counter: CounterOffer) {
        lifecycleScope.launch {
            try {
                supabase.from("driver_ride_responses")
                    .update({
                        set("price", counter.counterPrice)
                        set("offer_type", "counter_offer")
                        set("updated_at", getCurrentTimestamp())
                    }) {
                        filter {
                            eq("ride_id", counter.rideId)
                            eq("driver_id", counter.driverId)
                        }
                    }
            } catch (e: Exception) {
                Log.e("AvailableDrivers", "Error updating price", e)
            }
        }
    }

    // ========================================
    // SHOW COUNTER OFFER BOTTOM SHEET
    // ========================================

    private fun showCounterOfferBottomSheet(driver: Driver) {
        // Check if already has pending counter
        val existingCounter = activeCounterOffers[driver.id]
        if (existingCounter?.status == CounterOfferStatus.PENDING) {
            Toast.makeText(
                requireContext(),
                "You already have a pending counter with ${driver.name}",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val bottomSheet = CounterOfferBottomSheet.newInstance(
            driver = driver,
            rideId = currentRideId!!,
            userId = getCurrentUserId()
        )
        bottomSheet.show(childFragmentManager, "CounterOfferBottomSheet")
    }

    // ========================================
    // HANDLE DRIVER RESPONSES
    // ========================================

    private fun addNewDriverResponse(response: DriverRideResponse) {
        val driver = response.toDriver()
        val yourBid = rideBooking?.bid ?: 0
        val savings = if (driver.price < yourBid) yourBid - driver.price else 0

        val counter = activeCounterOffers[driver.id]

        val driverWithSavings = driver.copy(
            savings = savings,
            rideId = currentRideId,
            hasActiveCounter = counter != null,
            activeCounterPrice = counter?.counterPrice,
            counterStatus = counter?.status,
            counterOfferId = counter?.id,
            counterOfferedBy = counter?.offeredBy
        )

        val existingIndex = driversList.indexOfFirst { it.id == driver.id }
        if (existingIndex == -1) {
            driversList.add(0, driverWithSavings)
            updateDriversList()

            Snackbar.make(
                requireView(),
                "🚗 New offer from ${driver.name} - ₹${driver.price}",
                Snackbar.LENGTH_LONG
            ).setAction("View") {
                rvDrivers.smoothScrollToPosition(0)
            }.show()
        }
    }

    private fun updateDriverResponse(response: DriverRideResponse) {
        val driver = response.toDriver()
        val yourBid = rideBooking?.bid ?: 0
        val savings = if (driver.price < yourBid) yourBid - driver.price else 0

        val counter = activeCounterOffers[driver.id]

        val driverWithSavings = driver.copy(
            savings = savings,
            rideId = currentRideId,
            hasActiveCounter = counter != null,
            activeCounterPrice = counter?.counterPrice,
            counterStatus = counter?.status,
            counterOfferId = counter?.id,
            counterOfferedBy = counter?.offeredBy
        )

        val existingIndex = driversList.indexOfFirst { it.id == response.driverId }

        if (existingIndex != -1) {
            val oldDriver = driversList[existingIndex]
            driversList[existingIndex] = driverWithSavings
            updateDriversList()

            if (oldDriver.price != driver.price) {
                showPriceChangeSnackbar(driver.name?:"", oldDriver.price, driver.price)
            }
        }
    }

    // ========================================
    // ACCEPT OFFER
    // ========================================

    private fun handleAcceptOffer(driver: Driver) {
        lifecycleScope.launch {
            try {
                supabase.from("driver_ride_responses")
                    .update({
                        set("is_confirmed", true)
                        set("offered_price", driver.price)
                    }) {
                        filter {
                            eq("ride_id", currentRideId!!)
                            eq("driver_id", driver.id)
                        }
                    }

                supabase.from("ride_bookings")
                    .update({
                        set("status", "confirmed")
                        set("confirmed_driver_id", driver.id)
                        set("bid", driver.price)
                    }) {
                        filter {
                            eq("id", currentRideId!!)
                        }
                    }

                withContext(Dispatchers.Main) {
                    driversList.replaceAll {
                        if (it.id == driver.id) it.copy(isConfirmed = true) else it
                    }
                    updateDriversList()

                    tvSubtitle.text = "Ride confirmed • Driver will arrive soon"

                    Snackbar.make(
                        requireView(),
                        "✓ Ride confirmed with ${driver.name}",
                        Snackbar.LENGTH_LONG
                    ).setBackgroundTint(
                        ContextCompat.getColor(requireContext(), R.color.green)
                    ).show()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                Log.e("AvailableDrivers", "Error accepting offer", e)
            }
        }
    }

    // ========================================
    // UI HELPERS
    // ========================================

    private fun updateDriverInList(driverId: String, transform: (Driver) -> Driver) {
        val index = driversList.indexOfFirst { it.id == driverId }
        if (index != -1) {
            val driver = driversList[index]
            val updatedDriver = transform(driver)

            val yourBid = rideBooking?.bid ?: 0
            val savings = if (updatedDriver.price < yourBid) yourBid - updatedDriver.price else 0

            driversList[index] = updatedDriver.copy(savings = savings)
            updateDriversList()
        }
    }

    private fun updateDriversList() {
        val updatedList = driversList.map { it.copy() }
        driversAdapter.submitList(updatedList)

        val confirmedCount = driversList.count { it.isConfirmed }
        if (confirmedCount == 0) {
            tvSubtitle.text = "${driversList.size} drivers accepted • Choose the best offer"
        }
    }

    // ========================================
    // SNACKBAR NOTIFICATIONS
    // ========================================

    private fun showCounterAcceptedSnackbar(counter: CounterOffer) {
        val driver = driversList.find { it.id == counter.driverId }

        Snackbar.make(
            requireView(),
            "🎉 ${driver?.name} accepted your counter of ₹${counter.counterPrice}!",
            Snackbar.LENGTH_LONG
        ).setAction("Accept Now") {
            driver?.let { handleAcceptOffer(it) }
        }.setBackgroundTint(
            ContextCompat.getColor(requireContext(), R.color.green)
        ).show()
    }

    private fun showCounterRejectedSnackbar(counter: CounterOffer) {
        val driver = driversList.find { it.id == counter.driverId }

        Snackbar.make(
            requireView(),
            "❌ ${driver?.name} rejected your counter offer",
            Snackbar.LENGTH_SHORT
        ).setBackgroundTint(
            ContextCompat.getColor(requireContext(), R.color.red)
        ).show()
    }

    private fun showPriceChangeSnackbar(driverName: String, oldPrice: Int, newPrice: Int) {
        val message = if (newPrice < oldPrice) {
            "🎉 $driverName reduced price: ₹$oldPrice → ₹$newPrice"
        } else {
            "⬆️ $driverName changed price: ₹$oldPrice → ₹$newPrice"
        }

        Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG)
            .setAction("View") {
                val position = driversList.indexOfFirst { it.name == driverName }
                if (position != -1) rvDrivers.smoothScrollToPosition(position)
            }.show()
    }

    // ========================================
    // COUNTER EXPIRATION
    // ========================================

    private fun startCounterExpirationCheck() {
        lifecycleScope.launch {
            while (isActive) {
                delay(30000)
                cleanupExpiredCounters()
            }
        }
    }

    private suspend fun cleanupExpiredCounters() {
        try {
            val expiredCounters = activeCounterOffers.values.filter {
                it.status == CounterOfferStatus.PENDING && isCounterExpired(it.createdAt)
            }

            expiredCounters.forEach { counter ->
                supabase.from("counter_offers")
                    .update({ set("status", "expired") }) {
                        filter { eq("id", counter.id) }
                    }

                activeCounterOffers.remove(counter.driverId)

                withContext(Dispatchers.Main) {
                    updateDriverInList(counter.driverId) { driver ->
                        driver.copy(
                            hasActiveCounter = false,
                            activeCounterPrice = null,
                            counterStatus = null,
                            counterOfferId = null,
                            counterOfferedBy = null
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AvailableDrivers", "Error cleaning expired counters", e)
        }
    }

    private fun isCounterExpired(createdAt: String): Boolean {
        return try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            dateFormat.timeZone = TimeZone.getTimeZone("UTC")
            val createdDate = dateFormat.parse(createdAt) ?: return false

            val expirationTime = 5 * 60 * 1000L // 5 minutes
            (System.currentTimeMillis() - createdDate.time) > expirationTime
        } catch (e: Exception) {
            false
        }
    }

    private fun getCurrentUserId(): String = "user123" // TODO: Replace with actual auth




    private fun showOfferTypeChangeSnackbar(driverName: String, offerType: OfferType) {
        val emoji = when (offerType) {
            OfferType.BEST_OFFER -> "⚡"
            OfferType.COUNTER_OFFER -> "🔄"
            OfferType.ACCEPTED_BID -> "✓"
        }

        val typeText = when (offerType) {
            OfferType.BEST_OFFER -> "BEST OFFER"
            OfferType.COUNTER_OFFER -> "Counter Offer"
            OfferType.ACCEPTED_BID -> "Accepted Bid"
        }

        val message = "$emoji $driverName is now $typeText"

        view?.let {
            Snackbar.make(
                it,
                message,
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }






    override fun onDestroyView() {
        super.onDestroyView()
        lifecycleScope.launch {
            try {
                realtimeChannel?.let { supabase.realtime.removeChannel(it) }
                counterChannel?.let { supabase.realtime.removeChannel(it) }
            } catch (e: Exception) {
                Log.e("AvailableDrivers", "Error removing channels", e)
            }
        }
    }

    companion object {
        fun newInstance(rideBooking: RideBooking) = AvailableDriversFragment().apply {
            arguments = Bundle().apply {
                putParcelable("ride_booking", rideBooking)
            }
        }
    }
}


