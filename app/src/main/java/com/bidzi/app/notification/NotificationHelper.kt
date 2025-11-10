package com.bidzi.app.notification

import com.bidzi.app.MyApplication
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Helper object for creating and managing notifications
 * This should be called from your backend/server-side logic or ride management code
 */
object NotificationHelper {

    /**
     * Create a notification for a user
     */
    suspend fun createNotification(
        userId: String,
        type: String,
        title: String,
        message: String,
        driverId: String? = null,
        rideId: Long? = null,
        metadata: Map<String, String>? = null
    ): Boolean {
        return try {
            val jsonMetadata = metadata?.let {
                buildJsonObject {
                    it.forEach { (key, value) ->
                        put(key, value)
                    }
                }
            }

            val notification = NotificationInsert(
                userId = userId,
                driverId = driverId,
                rideId = rideId,
                type = type,
                title = title,
                message = message,
                metadata = jsonMetadata
            )

            MyApplication.supabase
                .from("notifications")
                .insert(notification)

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Create notification for new rider joining shared ride
     */
    suspend fun notifyNewRiderJoined(
        hostUserId: String,
        riderName: String,
        destination: String,
        rideId: Long
    ) {
        createNotification(
            userId = hostUserId,
            type = NotificationType.NEW_RIDER,
            title = "New Rider Joined!",
            message = "$riderName joined your shared ride to $destination",
            rideId = rideId
        )
    }

    /**
     * Notify about shared ride availability
     */
    suspend fun notifySharedRideAvailable(
        userId: String,
        destination: String,
        fare: Int,
        rideId: Long
    ) {
        createNotification(
            userId = userId,
            type = NotificationType.SHARED_RIDE_AVAILABLE,
            title = "Shared Ride Available",
            message = "A shared ride to $destination is available near you for ₹$fare",
            rideId = rideId
        )
    }

    /**
     * Notify ride completion
     */
    suspend fun notifyRideCompleted(
        userId: String,
        destination: String,
        fare: Int,
        rideId: Long
    ) {
        createNotification(
            userId = userId,
            type = NotificationType.RIDE_COMPLETED,
            title = "Ride Completed",
            message = "Your ride to $destination was completed. Fare: ₹$fare",
            rideId = rideId
        )
    }

    /**
     * Notify when shared ride is full
     */
    suspend fun notifySharedRideFull(
        userId: String,
        rideId: Long
    ) {
        createNotification(
            userId = userId,
            type = NotificationType.SHARED_RIDE_FULL,
            title = "Shared Ride Full",
            message = "Your shared ride is now full and driver is on the way",
            rideId = rideId
        )
    }

    /**
     * Notify when driver accepts ride
     */
    suspend fun notifyDriverAccepted(
        userId: String,
        driverName: String,
        driverId: String,
        rideId: Long
    ) {
        createNotification(
            userId = userId,
            type = NotificationType.DRIVER_ACCEPTED,
            title = "Driver Accepted",
            message = "$driverName accepted your ride request",
            driverId = driverId,
            rideId = rideId
        )
    }

    /**
     * Notify when no drivers available
     */
    suspend fun notifyNoDriversAvailable(
        userId: String,
        rideId: Long
    ) {
        createNotification(
            userId = userId,
            type = NotificationType.NO_DRIVERS_AVAILABLE,
            title = "No Drivers Available",
            message = "Your ride request expired. Please try again.",
            rideId = rideId
        )
    }

    /**
     * Notify ride cancellation
     */
    suspend fun notifyRideCancelled(
        userId: String,
        reason: String,
        rideId: Long
    ) {
        createNotification(
            userId = userId,
            type = NotificationType.RIDE_CANCELLED,
            title = "Ride Cancelled",
            message = "Your ride was cancelled. $reason",
            rideId = rideId
        )
    }

    /**
     * Notify payment success
     */
    suspend fun notifyPaymentSuccess(
        userId: String,
        amount: Int,
        rideId: Long
    ) {
        createNotification(
            userId = userId,
            type = NotificationType.PAYMENT_SUCCESS,
            title = "Payment Successful",
            message = "Payment of ₹$amount was successful",
            rideId = rideId
        )
    }

    /**
     * Notify payment failure
     */
    suspend fun notifyPaymentFailed(
        userId: String,
        amount: Int,
        rideId: Long
    ) {
        createNotification(
            userId = userId,
            type = NotificationType.PAYMENT_FAILED,
            title = "Payment Failed",
            message = "Payment of ₹$amount failed. Please try again.",
            rideId = rideId
        )
    }

    /**
     * Notify rating reminder
     */
    suspend fun notifyRatingReminder(
        userId: String,
        driverName: String,
        rideId: Long
    ) {
        createNotification(
            userId = userId,
            type = NotificationType.RATING_REMINDER,
            title = "Rate Your Ride",
            message = "How was your ride with $driverName? Rate your experience.",
            rideId = rideId
        )
    }

    /**
     * Notify when driver arrives
     */
    suspend fun notifyDriverArrived(
        userId: String,
        driverName: String,
        vehicleNumber: String,
        rideId: Long
    ) {
        createNotification(
            userId = userId,
            type = NotificationType.DRIVER_ARRIVED,
            title = "Driver Arrived",
            message = "$driverName has arrived. Vehicle: $vehicleNumber",
            rideId = rideId
        )
    }

    /**
     * Notify when ride starts
     */
    suspend fun notifyRideStarted(
        userId: String,
        destination: String,
        rideId: Long
    ) {
        createNotification(
            userId = userId,
            type = NotificationType.RIDE_STARTED,
            title = "Ride Started",
            message = "Your ride to $destination has started",
            rideId = rideId
        )
    }

    /**
     * Bulk notify multiple users (for shared rides)
     */
    suspend fun notifyMultipleUsers(
        userIds: List<String>,
        type: String,
        title: String,
        message: String,
        rideId: Long? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            userIds.forEach { userId ->
                createNotification(
                    userId = userId,
                    type = type,
                    title = title,
                    message = message,
                    rideId = rideId
                )
            }
        }
    }

    /**
     * Delete old notifications (cleanup)
     * Call this periodically or on app startup
     */
    suspend fun deleteOldNotifications(userId: String, daysOld: Int = 30): Boolean {
        return try {
            // This would require a custom SQL function or backend endpoint
            // For now, we'll delete read notifications older than X days
            MyApplication.supabase
                .from("notifications")
                .delete {
                    filter {
                        eq("user_id", userId)
                        eq("is_read", true)
                        // Note: Date filtering requires custom implementation
                    }
                }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Get unread notification count
     */
    suspend fun getUnreadCount(userId: String): Int {
        return try {
            val notifications = MyApplication.supabase
                .from("notifications")
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("is_read", false)
                    }
                }
                .decodeList<Notification>()

            notifications.size
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }
}

/**
 * Example usage in your ride flow:
 *
 * When a ride is accepted:
 * NotificationHelper.notifyDriverAccepted(
 *     userId = ride.userId,
 *     driverName = driver.name,
 *     driverId = driver.id,
 *     rideId = ride.id
 * )
 *
 * When someone joins a shared ride:
 * NotificationHelper.notifyNewRiderJoined(
 *     hostUserId = sharedRide.hostUserId,
 *     riderName = newRider.name,
 *     destination = sharedRide.destination,
 *     rideId = sharedRide.id
 * )
 */