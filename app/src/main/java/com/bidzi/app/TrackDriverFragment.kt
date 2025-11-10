package com.bidzi.app

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlin.getValue


class TrackDriverFragment : Fragment() {

    private lateinit var backButton: ImageView
    private lateinit var shareButton: ImageView
    private lateinit var sosButton: ImageView

    private val args: TrackDriverFragmentArgs by navArgs()
    private var driverBottomSheet: DriverInfoBottomSheet? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_track_driver, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize header views
        backButton = view.findViewById(R.id.backButton)
        shareButton = view.findViewById(R.id.shareButton)
        sosButton = view.findViewById(R.id.sosButton)

        // Setup click listeners
        setupClickListeners()

        // Show bottom sheet
        showDriverBottomSheet()
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener {
            findNavController().navigateUp()
        }

        shareButton.setOnClickListener {
            // Handle share action
        }

        sosButton.setOnClickListener {
            // Handle SOS action
        }
    }

    private fun showDriverBottomSheet() {
        args.driverData?.let { data ->
            driverBottomSheet = DriverInfoBottomSheet.newInstance(data, args.isShared)
            driverBottomSheet?.show(childFragmentManager, "DriverInfoBottomSheet")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        driverBottomSheet?.dismiss()
    }
}
