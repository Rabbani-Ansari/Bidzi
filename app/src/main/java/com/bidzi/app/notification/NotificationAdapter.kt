package com.bidzi.app.notification

import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

import com.bidzi.app.R
import com.bidzi.app.databinding.ItemNotificationBinding
import java.text.SimpleDateFormat


import com.bidzi.app.notification.NotificationUpdate
import java.util.Locale

class NotificationAdapter(
    private val onNotificationClick: (Notification) -> Unit,
    private val onMarkAsRead: (Long) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

    private val notifications = mutableListOf<Notification>()

    inner class NotificationViewHolder(private val binding: ItemNotificationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(notification: Notification) {
            binding.apply {
                tvTitle.text = notification.title
                tvMessage.text = notification.message
                tvTimeAgo.text = notification.getTimeAgo()

                // Set icon and colors based on type
                val (iconRes, colorRes) = getNotificationStyle(notification.type)

                ivIcon.setImageResource(iconRes)
                ivIcon.setColorFilter(
                    root.context.getColor(colorRes),
                    PorterDuff.Mode.SRC_IN
                )

                // Show/hide unread indicator
                notificationDot.visibility = if (notification.isRead) {
                    View.GONE
                } else {
                    View.VISIBLE
                }

                // Background alpha for read notifications
                root.alpha = if (notification.isRead) 0.7f else 1.0f

                // Click listener
                root.setOnClickListener {
                    if (!notification.isRead) {
                        onMarkAsRead(notification.id)
                    }
                    onNotificationClick(notification)
                }
            }
        }

        private fun getNotificationStyle(type: String): Pair<Int, Int> {
            return when (type) {
                NotificationType.NEW_RIDER ->
                    R.drawable.ic_new_rider to R.color.orange

                NotificationType.SHARED_RIDE_AVAILABLE ->
                    R.drawable.ic_shared_ride to R.color.green

                NotificationType.RIDE_COMPLETED,
                NotificationType.SHARED_RIDE_FULL,
                NotificationType.PAYMENT_SUCCESS ->
                    R.drawable.ic_checkmark to R.color.green

                NotificationType.DRIVER_ACCEPTED,
                NotificationType.DRIVER_ARRIVED,
                NotificationType.RIDE_STARTED ->
                    R.drawable.ic_clock to R.color.orange

                NotificationType.NO_DRIVERS_AVAILABLE,
                NotificationType.RIDE_CANCELLED ->
                    R.drawable.ic_close to R.color.red

                NotificationType.PAYMENT_FAILED ->
                    R.drawable.ic_close to R.color.red

                NotificationType.RATING_REMINDER ->
                    R.drawable.star to R.color.orange

                else ->
                    R.drawable.ic_notification to R.color.orange
            }
        }

        private fun getTimeAgo(createdAt: String): String {
            return try {
                val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val date = formatter.parse(createdAt)
                val now = System.currentTimeMillis()
                val diff = now - (date?.time ?: now)

                when {
                    diff < 60_000 -> "Just now"
                    diff < 3600_000 -> "${diff / 60_000}m ago"
                    diff < 86400_000 -> "${diff / 3600_000}h ago"
                    diff < 604800_000 -> "${diff / 86400_000}d ago"
                    else -> {
                        val displayFormatter = SimpleDateFormat("MMM dd", Locale.getDefault())
                        displayFormatter.format(date)
                    }
                }
            } catch (e: Exception) {
                "Recently"
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val binding = ItemNotificationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NotificationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(notifications[position])
    }

    override fun getItemCount(): Int = notifications.size

    fun submitList(newNotifications: List<Notification>) {
        val diffCallback = NotificationDiffCallback(notifications, newNotifications)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        notifications.clear()
        notifications.addAll(newNotifications)
        diffResult.dispatchUpdatesTo(this)
    }

    fun updateNotification(notificationId: Long, isRead: Boolean) {
        val index = notifications.indexOfFirst { it.id == notificationId }
        if (index != -1) {
            notifications[index] = notifications[index].copy(isRead = isRead)
            notifyItemChanged(index)
        }
    }

    private class NotificationDiffCallback(
        private val oldList: List<Notification>,
        private val newList: List<Notification>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }
}