package com.bidzi.app.activity

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import com.bidzi.app.R
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority

import androidx.core.view.WindowCompat
import com.bidzi.app.databinding.ActivityRideRequestBinding

// Assuming binding; adjust if using findViewById

class RideRequestActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "RideRequestActivity"
    }

    private lateinit var binding: ActivityRideRequestBinding  // Optional: Use if ViewBinding enabled

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")

        // UPDATED: Enable edge-to-edge for consistent status bar handling
        enableEdgeToEdge()

        setContentView(R.layout.activity_ride_request)

        // UPDATED: Set status bar color to make it visible (e.g., white; change to your theme color)
        window.statusBarColor = getColor(R.color.your_status_bar_color)  // Define in colors.xml, e.g., #FFFFFF for white

        // UPDATED: Handle light/dark status bar icons based on background color
        // Assuming light background (dark icons); adjust isAppearanceLightStatusBars = false for dark bg/light icons
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        // UPDATED: Apply window insets for edge-to-edge (prevents content overlap)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // If using ViewBinding (recommended), initialize here:
        // binding = ActivityRideRequestBinding.inflate(layoutInflater)
        // setContentView(binding.root)
        // Then apply insets to binding.root

        setupNavigation()
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        // Additional setup if needed, e.g., navController.addOnDestinationChangedListener { ... }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}