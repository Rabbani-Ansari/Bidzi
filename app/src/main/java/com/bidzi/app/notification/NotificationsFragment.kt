package com.bidzi.app.notification


// Fragment
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.bidzi.app.databinding.FragmentNotificationsBinding

import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.bidzi.app.MyApplication
import com.bidzi.app.R
import com.bidzi.app.auth.SessionUser
import com.bidzi.app.notification.NotificationUpdate
import com.bidzi.app.notification.Notification

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    private lateinit var notificationAdapter: NotificationAdapter
    private lateinit var viewModel: NotificationViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViewModel()
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
    }

    private fun initViewModel() {
        val userId = SessionUser.currentUserId
        if (userId == null) {
            showToast("User not logged in")
            showEmptyState()
            return
        }

        val factory = NotificationViewModelFactory(userId)
        viewModel = ViewModelProvider(this, factory)[NotificationViewModel::class.java]
    }

    private fun setupRecyclerView() {
        notificationAdapter = NotificationAdapter(
            onNotificationClick = { notification -> onNotificationClick(notification) },
            onMarkAsRead = { notificationId -> viewModel.markAsRead(notificationId) }
        )

        binding.rvNotifications.apply {
            adapter = notificationAdapter
            layoutManager = LinearLayoutManager(requireContext())

            addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(
                    outRect: Rect,
                    view: View,
                    parent: RecyclerView,
                    state: RecyclerView.State
                ) {
                    outRect.bottom = resources.getDimensionPixelSize(R.dimen.notification_item_spacing)
                }
            })
        }
    }

    private fun setupClickListeners() {
        binding.ivBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.tvMarkAllRead.setOnClickListener {
            viewModel.markAllAsRead()
        }

        binding.swipeRefreshLayout?.setOnRefreshListener {
            viewModel.loadNotifications()
        }
    }

    private fun observeViewModel() {
        viewModel.notifications.observe(viewLifecycleOwner) { notifications ->
            notificationAdapter.submitList(notifications)

            if (notifications.isEmpty()) {
                showEmptyState()
            } else {
                hideEmptyState()
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                showLoading()
            } else {
                hideLoading()
            }
            binding.swipeRefreshLayout?.isRefreshing = false
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                showToast(it)
                viewModel.clearError()
            }
        }

        viewModel.hasUnreadNotifications.observe(viewLifecycleOwner) { hasUnread ->
            updateMarkAllReadButton(hasUnread)
        }
    }

    private fun onNotificationClick(notification: Notification) {
        // Handle navigation based on notification type
        when (notification.type) {
            NotificationType.NEW_RIDER -> {
                notification.rideId?.let { rideId ->
                    navigateToRideDetails(rideId)
                }
            }
            NotificationType.SHARED_RIDE_AVAILABLE -> {
                navigateToHome()
            }
            NotificationType.RIDE_COMPLETED -> {
                notification.rideId?.let { rideId ->
                    navigateToRideHistory(rideId)
                }
            }
            NotificationType.DRIVER_ACCEPTED -> {
                notification.rideId?.let { rideId ->
                    navigateToActiveRide(rideId)
                }
            }
            NotificationType.PAYMENT_SUCCESS,
            NotificationType.PAYMENT_FAILED -> {
                notification.rideId?.let { rideId ->
                    navigateToPaymentDetails(rideId)
                }
            }
            NotificationType.RATING_REMINDER -> {
                notification.rideId?.let { rideId ->
                    showRatingDialog(rideId)
                }
            }
            else -> {
                showToast(notification.title)
            }
        }
    }

    // Navigation helpers
    private fun navigateToRideDetails(rideId: Long) {
        val bundle = Bundle().apply {
            putLong("ride_id", rideId)
        }
        findNavController().navigate(R.id.action_notifications_to_rideDetails, bundle)
    }

    private fun navigateToHome() {
       //close the fragmnet or activity(Notification manage
    }

    private fun navigateToRideHistory(rideId: Long) {
        val bundle = Bundle().apply {
            putLong("ride_id", rideId)
        }
        findNavController().navigate(R.id.action_notifications_to_rideHistory, bundle)
    }

    private fun navigateToActiveRide(rideId: Long) {
        val bundle = Bundle().apply {
            putLong("ride_id", rideId)
        }
        findNavController().navigate(R.id.action_notifications_to_activeRide, bundle)
    }

    private fun navigateToPaymentDetails(rideId: Long) {
        val bundle = Bundle().apply {
            putLong("ride_id", rideId)
        }
        findNavController().navigate(R.id.action_notifications_to_paymentDetails, bundle)
    }

    private fun showRatingDialog(rideId: Long) {
//        RatingDialogFragment.newInstance(rideId)
//            .show(childFragmentManager, "RatingDialog")
    }

    // UI Helper Methods
    private fun updateMarkAllReadButton(hasUnread: Boolean) {
        binding.tvMarkAllRead.apply {
            isEnabled = hasUnread
            alpha = if (hasUnread) 1f else 0.5f
        }
    }

    private fun showLoading() {
        binding.progressBar?.visibility = View.VISIBLE
        binding.rvNotifications.visibility = View.GONE
        binding.emptyStateLayout?.visibility = View.GONE
    }

    private fun hideLoading() {
        binding.progressBar?.visibility = View.GONE
        binding.rvNotifications.visibility = View.VISIBLE
    }

    private fun showEmptyState() {
        binding.rvNotifications.visibility = View.GONE
        binding.emptyStateLayout?.visibility = View.VISIBLE
    }

    private fun hideEmptyState() {
        binding.rvNotifications.visibility = View.VISIBLE
        binding.emptyStateLayout?.visibility = View.GONE
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadNotifications()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// Extension function for Supabase to handle bulk operations
suspend fun markMultipleNotificationsAsRead(notificationIds: List<Long>) {
    try {
        notificationIds.forEach { id ->
            MyApplication.Companion.supabase
                .from("notifications")
                .update(NotificationUpdate(isRead = true)) {
                    filter {
                        eq("id", id)
                    }
                }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        throw e
    }
}