package com.bidzi.app.activity

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import com.bidzi.app.R
import com.bidzi.app.databinding.ActivityMainBinding
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.location.SettingsClient
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var locationManager: LocationManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var settingsClient: SettingsClient
    private var locationPermissionLauncher: ActivityResultLauncher<Array<String>>? = null
    private var locationSettingsLauncher: ActivityResultLauncher<IntentSenderRequest>? = null
    private var notificationPermissionLauncher: ActivityResultLauncher<String>? = null
    private var hasShownInitialBottomSheet = false
    private var isLocationFlowInProgress = false
    private var isNotificationFlowInProgress = false
    private var isPreciseLocationGranted = false

    companion object {
        private const val TAG = "MainActivity"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called")
        // Initialize ViewBinding immediately
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Apply edge-to-edge insets properly
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }
        // Initialize location services
        initializeLocationServices()
        // Initialize notification services
        initializeNotificationServices()
        // Setup navigation
        setupNavigation()
        // Check location status - show bottom sheet on first launch
        checkLocationStatus()
        // After location, check notification status
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                // FIXED: Use anonymous object to avoid supertype ambiguity
                // Delay slightly to chain after location
                owner.lifecycleScope.launch {
                    delay(500)  // Optional: Brief delay for UX
                    checkNotificationStatus()
                }
            }
        })
    }

    private fun initializeLocationServices() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        settingsClient = LocationServices.getSettingsClient(this)

        // Use RequestMultiplePermissions for fine + coarse to enable modern dialog
        locationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            isLocationFlowInProgress = false
            val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
            val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            when {
                fineGranted -> {
                    isPreciseLocationGranted = true
                    Log.d(TAG, "Precise location granted")
                    checkLocationSettings()
                }
                coarseGranted -> {
                    isPreciseLocationGranted = false
                    Log.d(TAG, "Approximate location granted only")
                    Toast.makeText(this, "Approximate location enabled. For best results, enable precise access in settings.", Toast.LENGTH_LONG).show()
                    checkLocationSettings()
                }
                else -> {
                    Log.d(TAG, "Location permission denied (both fine and coarse)")
                    showLocationPermissionDenied()
                }
            }
        }

        // Settings launcher
        locationSettingsLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            isLocationFlowInProgress = false
            if (result.resultCode == RESULT_OK) {
                Log.d(TAG, "Location settings enabled")
            } else {
                Log.d(TAG, "Location settings declined")
                showLocationSettingsDialog()
            }
        }
    }

    private fun initializeNotificationServices() {
        // Notification permission launcher (for Android 13+)
        notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            isNotificationFlowInProgress = false
            if (isGranted) {
                Log.d(TAG, "Notification permission granted")
                // Proceed with notification setup, e.g., create channels
                setupNotifications()
            } else {
                Log.d(TAG, "Notification permission denied")
                showNotificationPermissionDenied()
            }
        }
    }

    private fun setupNavigation() {
        Log.d(TAG, "Setting up navigation")
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
        if (navHostFragment == null) {
            Log.e(TAG, "NavHostFragment not found!")
            return
        }
        navController = navHostFragment.navController
        Log.d(TAG, "NavController initialized. Current destination: ${navController.currentDestination?.id}")
        binding.bottomNavigation.setupWithNavController(navController)
        Log.d(TAG, "BottomNavigationView setup complete")
        navController.addOnDestinationChangedListener { _, destination, _ ->
            Log.d(TAG, "Navigation changed to: ${destination.label} (ID: ${destination.id})")
        }
        binding.bottomNavigation.setOnItemSelectedListener { menuItem ->
            Log.d(TAG, "Bottom nav item selected: ${menuItem.title} (ID: ${menuItem.itemId})")
            NavigationUI.onNavDestinationSelected(menuItem, navController)
        }
    }

    private fun checkLocationStatus() {
        // First, check if location services are enabled (no permission needed)
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        val servicesEnabled = isGpsEnabled || isNetworkEnabled

        if (!servicesEnabled) {
            // Services disabled: Show custom bottom sheet first
            if (!hasShownInitialBottomSheet) {
                hasShownInitialBottomSheet = true
                showLocationBottomSheet()
                return
            } else {
                checkLocationSettings()
                return
            }
        }

        // Services are enabled: Now check permission
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            isPreciseLocationGranted = true  // Assume precise if fine granted
            checkLocationSettings()
        } else {
            // Permission denied: Show bottom sheet
            if (!hasShownInitialBottomSheet) {
                hasShownInitialBottomSheet = true
                showLocationBottomSheet()
            }
        }
    }

    private fun checkNotificationStatus() {
        // Check if notification permission is needed (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Permission not granted
                if (ActivityCompat.shouldShowRequestPermissionRationale(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                ) {
                    // Show bottom sheet with explanation
                    showNotificationBottomSheet()
                } else {
                    // No explanation needed, request directly
                    requestNotificationPermission()
                }
            } else {
                Log.d(TAG, "Notification permission already granted")
                setupNotifications()
            }
        } else {
            // Pre-Android 13: No runtime permission needed
            setupNotifications()
        }
    }

    private fun requestLocationPermission() {
        if (isLocationFlowInProgress) {
            Log.d(TAG, "Location flow already in progress, skipping duplicate request")
            return
        }
        isLocationFlowInProgress = true

        locationPermissionLauncher?.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun requestNotificationPermission() {
        if (isNotificationFlowInProgress) {
            Log.d(TAG, "Notification flow already in progress, skipping duplicate request")
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        isNotificationFlowInProgress = true
        notificationPermissionLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun showLocationPermissionDenied() {
        if (isLocationFlowInProgress) {
            Log.d(TAG, "Location flow already in progress, skipping")
            return
        }
        // Optional: Show a final dialog or toast
        Toast.makeText(this, "Location permission required for core features.", Toast.LENGTH_LONG).show()
    }

    private fun showNotificationPermissionDenied() {
        if (isNotificationFlowInProgress) {
            Log.d(TAG, "Notification flow already in progress, skipping")
            return
        }
        // Optional: Show a final dialog or toast
        Toast.makeText(this, "Notifications disabled. You may miss important updates.", Toast.LENGTH_LONG).show()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", packageName, null)
        intent.data = uri
        startActivity(intent)
    }

    private fun checkLocationSettings() {
        if (isLocationFlowInProgress) {
            Log.d(TAG, "Location flow already in progress, skipping duplicate check")
            return
        }

        val priority = if (isPreciseLocationGranted) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        val locationRequest = LocationRequest.Builder(priority, 10000L).build()
        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(false)

        val task = settingsClient.checkLocationSettings(builder.build())
        task.addOnSuccessListener {
            Log.d(TAG, "Location settings are satisfied (priority: $priority)")
        }
        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                isLocationFlowInProgress = true
                try {
                    val intentSenderRequest = IntentSenderRequest.Builder(exception.resolution).build()
                    locationSettingsLauncher?.launch(intentSenderRequest)
                } catch (e: IntentSender.SendIntentException) {
                    Log.e(TAG, "Error launching location settings", e)
                    isLocationFlowInProgress = false
                }
            }
        }
    }

    private fun showLocationSettingsDialog() {
        if (!isLocationFlowInProgress) {
            showLocationBottomSheet()
        }
    }

    private fun showLocationBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val bottomSheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_location_permission, null)
        bottomSheetDialog.setContentView(bottomSheetView)

        // Make it non-cancelable on first app launch
        bottomSheetDialog.setCancelable(!hasShownInitialBottomSheet)

        bottomSheetDialog.setOnDismissListener {
            Log.d(TAG, "Location bottom sheet dismissed")
        }

        bottomSheetDialog.show()

        // Handle button clicks
        bottomSheetView.findViewById<MaterialButton>(R.id.btnEnableLocation).setOnClickListener {
            bottomSheetDialog.dismiss()

            val isPermissionGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            val servicesEnabled = isGpsEnabled || isNetworkEnabled

            when {
                !isPermissionGranted -> {
                    requestLocationPermission()
                }
                !servicesEnabled -> {
                    checkLocationSettings()
                }
                else -> {
                    Log.d(TAG, "Location fully enabled")
                }
            }
        }

        bottomSheetView.findViewById<MaterialButton>(R.id.btnCancel).setOnClickListener {
            bottomSheetDialog.dismiss()
            Toast.makeText(this, "Location access denied. Some features may not work properly.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showNotificationBottomSheet() {
        // Assume you have a similar layout: bottom_sheet_notification_permission.xml
        // With TextView explaining why notifications are needed, and buttons for Enable/Cancel
        val bottomSheetDialog = BottomSheetDialog(this)
        val bottomSheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_notification_permission, null)
        bottomSheetDialog.setContentView(bottomSheetView)

        // Non-cancelable for better UX
        bottomSheetDialog.setCancelable(false)

        bottomSheetDialog.setOnDismissListener {
            Log.d(TAG, "Notification bottom sheet dismissed")
        }

        bottomSheetDialog.show()

        // Handle button clicks
        bottomSheetView.findViewById<MaterialButton>(R.id.btnEnableNotifications).setOnClickListener {
            bottomSheetDialog.dismiss()
            requestNotificationPermission()
        }

        bottomSheetView.findViewById<MaterialButton>(R.id.btnCancelNotifications).setOnClickListener {
            bottomSheetDialog.dismiss()
            // Proceed without notifications
            setupNotifications()  // Or skip if optional
        }
    }

    private fun setupNotifications() {
        // Implement your notification channel creation, etc.
        // E.g., if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { createNotificationChannel() }
        Log.d(TAG, "Notifications set up")
    }
}