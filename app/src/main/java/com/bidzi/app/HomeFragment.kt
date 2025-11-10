package com.bidzi.app

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup


import androidx.navigation.fragment.findNavController
import com.bidzi.app.databinding.FragmentHomeBinding



import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.os.Build

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.bidzi.app.auth.SessionUser
import com.bidzi.app.notification.NotificationManagementActivity
import com.bidzi.app.notification.NotificationViewModel
import com.bidzi.app.notification.NotificationViewModelFactory


class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private var webView: WebView? = null
    private lateinit var notificationViewModel: NotificationViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("HomeFragment", "onViewCreated")

        initViewModel()
        setupWebView()
        setupClickListeners()
        observeNotifications()
    }

    private fun initViewModel() {
        val userId = SessionUser.currentUserId
        if (userId != null) {
            val factory = NotificationViewModelFactory(userId)
            notificationViewModel = ViewModelProvider(this, factory)[NotificationViewModel::class.java]
        }
    }

    private fun setupWebView() {
        Log.d("HomeFragment", "setupWebView")

        webView = WebView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }

            webChromeClient = CustomWebChromeClient()
            webViewClient = CustomWebViewClient()

            loadUrl("file:///android_asset/radar.html")
        }

        // Add WebView to the map container
        binding.mapContainer.addView(webView)
        Log.d("HomeFragment", "WebView added to layout")
    }

    private fun setupClickListeners() {
        Log.d("HomeFragment", "setupClickListeners")

        binding.fabRide.setOnClickListener {
            findNavController().navigate(R.id.rideRequestActivity)
        }

        binding.sharedRidesBtn.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_availableRidesFragment)
        }

        binding.scheduleBtn.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_scheduleRideFragment)
        }

        binding.notificationBtn.setOnClickListener {
            NotificationManagementActivity.start(requireContext())
        }
    }

    private fun observeNotifications() {
        // Check if user is logged in
        if (SessionUser.currentUserId == null) {
            binding.notificationBadge.visibility = View.GONE
            return
        }

        // Observe unread notification count
        notificationViewModel.unreadCount.observe(viewLifecycleOwner) { count ->
            updateNotificationBadge(count)
        }

        // Load initial count
        notificationViewModel.loadUnreadNotificationCount()
    }

    private fun updateNotificationBadge(count: Int) {
        if (count > 0) {
            binding.notificationBadge.visibility = View.VISIBLE
            binding.notificationBadge.text = if (count > 99) "99+" else count.toString()
        } else {
            binding.notificationBadge.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("HomeFragment", "onResume")

        // Refresh notification count when returning to this fragment
        if (SessionUser.currentUserId != null && ::notificationViewModel.isInitialized) {
            notificationViewModel.loadUnreadNotificationCount()
        }
    }

    // Custom WebChromeClient to handle loading progress
    private inner class CustomWebChromeClient : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            super.onProgressChanged(view, newProgress)
            Log.d("HomeFragment", "Page loading progress: $newProgress%")

            if (newProgress < 100) {
                binding.mapLoadingIndicator.visibility = View.VISIBLE
                binding.mapLoadingIndicator.progress = newProgress
            } else {
                binding.mapLoadingIndicator.visibility = View.GONE
            }
        }
    }

    // Custom WebViewClient to handle page loading
    private inner class CustomWebViewClient : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            Log.d("HomeFragment", "Page started loading: $url")
            binding.mapLoadingIndicator.visibility = View.VISIBLE
            binding.loadingSpinnerContainer.visibility = View.VISIBLE
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            Log.d("HomeFragment", "Page finished loading: $url")
            binding.mapLoadingIndicator.visibility = View.GONE
            binding.loadingSpinnerContainer.visibility = View.GONE
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            super.onReceivedError(view, request, error)
            Log.e("HomeFragment", "WebView error: ${error?.description}")
            binding.mapLoadingIndicator.visibility = View.GONE
            binding.loadingSpinnerContainer.visibility = View.GONE

            Toast.makeText(
                requireContext(),
                "Failed to load map. Please check your internet connection.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d("HomeFragment", "onDestroyView")

        // Clean up WebView
        binding.mapContainer.removeView(webView)
        webView?.destroy()
        webView = null

        _binding = null
    }
}
