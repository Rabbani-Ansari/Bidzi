package com.bidzi.app.notification

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.bidzi.app.R
import com.bidzi.app.databinding.ActivityNotificationManagementBinding

class NotificationManagementActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationManagementBinding
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        setupStatusBar()

        onBackPressedDispatcher.addCallback(this) {
            if (!navController.navigateUp()) {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }
    }


    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_notification) as NavHostFragment
        navController = navHostFragment.navController

        // Handle back button in action bar
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.notificationsFragment -> {
                    // Entry fragment - back button will finish activity
                }
            }
        }
    }

    private fun setupStatusBar() {
        // Make status bar match app theme
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            window.statusBarColor = ContextCompat.getColor(this, R.color.white)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }




    companion object {
        fun start(context: Context) {
            val intent = Intent(context, NotificationManagementActivity::class.java)
            context.startActivity(intent)
        }

        fun startWithNotificationId(context: Context, notificationId: Long) {
            val intent = Intent(context, NotificationManagementActivity::class.java).apply {
                putExtra("notification_id", notificationId)
            }
            context.startActivity(intent)
        }
    }
}