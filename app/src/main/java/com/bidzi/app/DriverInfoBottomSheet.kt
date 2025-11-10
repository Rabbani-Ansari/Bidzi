package com.bidzi.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class DriverInfoBottomSheet : BottomSheetDialogFragment() {

    private lateinit var driverArrivingText: TextView
    private lateinit var driverAvatar: TextView
    private lateinit var driverName: TextView
    private lateinit var driverRating: TextView
    private lateinit var vehicleNumber: TextView
    private lateinit var callButton: LinearLayout
    private lateinit var messageButton: LinearLayout
    private lateinit var coRidersSection: LinearLayout
    private lateinit var coRidersNames: TextView
    private lateinit var fareAmount: TextView
    private lateinit var distance: TextView

    private var driverData: DriverData? = null
    private var isShared: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            driverData = it.getParcelable(ARG_DRIVER_DATA)
            isShared = it.getBoolean(ARG_IS_SHARED, false)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_driver_info, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views
        driverArrivingText = view.findViewById(R.id.driverArrivingText)
        driverAvatar = view.findViewById(R.id.driverAvatar)
        driverName = view.findViewById(R.id.driverName)
        driverRating = view.findViewById(R.id.driverRating)
        vehicleNumber = view.findViewById(R.id.vehicleNumber)
        callButton = view.findViewById(R.id.callButton)
        messageButton = view.findViewById(R.id.messageButton)
        coRidersSection = view.findViewById(R.id.coRidersSection)
        coRidersNames = view.findViewById(R.id.coRidersNames)
        fareAmount = view.findViewById(R.id.fareAmount)
        distance = view.findViewById(R.id.distance)

        // Setup click listeners
        setupClickListeners()

        // Populate data
        populateDriverData()
    }

    private fun setupClickListeners() {
        callButton.setOnClickListener {
            driverData?.let { data ->
                // Handle call action - open dialer
                // val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${data.phoneNumber}"))
                // startActivity(intent)
            }
        }

        messageButton.setOnClickListener {
            driverData?.let { data ->
                // Handle message action - open SMS
                // val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${data.phoneNumber}"))
                // startActivity(intent)
            }
        }
    }

    private fun populateDriverData() {
        driverData?.let { data ->
            // Populate driver info
            driverAvatar.text = getInitials(data.name)
            driverName.text = data.name
            driverRating.text = String.format("%.1f", data.rating)
            vehicleNumber.text = data.vehicleNumber
            fareAmount.text = "₹${data.fare}"
            distance.text = String.format("%.1f km", data.distance)
            driverArrivingText.text = "Driver arriving in ${data.arrivalTime} min"

            // Show/hide co-riders section based on ride type
            if (isShared && data.coRiders.isNotEmpty()) {
                coRidersSection.visibility = View.VISIBLE
                coRidersNames.text = data.coRiders.joinToString(", ")
            } else {
                coRidersSection.visibility = View.GONE
            }
        }
    }

    private fun getInitials(name: String): String {
        val parts = name.split(" ")
        return if (parts.size >= 2) {
            "${parts[0].first()}${parts[1].first()}".uppercase()
        } else {
            name.take(2).uppercase()
        }
    }

    companion object {
        private const val ARG_DRIVER_DATA = "arg_driver_data"
        private const val ARG_IS_SHARED = "arg_is_shared"

        fun newInstance(driverData: DriverData, isShared: Boolean): DriverInfoBottomSheet {
            return DriverInfoBottomSheet().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_DRIVER_DATA, driverData)
                    putBoolean(ARG_IS_SHARED, isShared)
                }
            }
        }
    }
}

// ===================================================================
