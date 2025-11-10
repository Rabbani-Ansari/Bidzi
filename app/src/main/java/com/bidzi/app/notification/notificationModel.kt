package com.bidzi.app.notification

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class Notification(
    @SerialName("id")
    val id: Long = 0,

    @SerialName("user_id")
    val userId: String,

    @SerialName("driver_id")
    val driverId: String? = null,

    @SerialName("ride_id")
    val rideId: Long? = null,

    @SerialName("type")
    val type: String,

    @SerialName("title")
    val title: String,

    @SerialName("message")
    val message: String,

    @SerialName("is_read")
    val isRead: Boolean = false,

    @SerialName("created_at")
    val createdAt: String,

    @SerialName("read_at")
    val readAt: String? = null,

    @SerialName("metadata")
    val metadata: JsonObject? = null
) : java.io.Serializable {

    // Computed property for time ago display
    fun getTimeAgo(): String {
        return try {
            val formatter = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
            formatter.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val date = formatter.parse(createdAt.substringBefore('+').substringBefore('Z'))
            val now = java.util.Date()
            val diff = now.time - (date?.time ?: 0)

            when {
                diff < 60000 -> "Just now"
                diff < 3600000 -> "${diff / 60000} mins ago"
                diff < 86400000 -> "${diff / 3600000} hours ago"
                diff < 604800000 -> "${diff / 86400000} days ago"
                else -> "${diff / 604800000} weeks ago"
            }
        } catch (e: Exception) {
            "Recently"
        }
    }
}

@Serializable
data class NotificationUpdate(
    @SerialName("is_read")
    val isRead: Boolean
)

@Serializable
data class NotificationInsert(
    @SerialName("user_id")
    val userId: String,

    @SerialName("driver_id")
    val driverId: String? = null,

    @SerialName("ride_id")
    val rideId: Long? = null,

    @SerialName("type")
    val type: String,

    @SerialName("title")
    val title: String,

    @SerialName("message")
    val message: String,

    @SerialName("metadata")
    val metadata: JsonObject? = null
)

// Helper data class for grouping notifications by date
data class NotificationGroup(
    val dateLabel: String,
    val notifications: List<Notification>
)
@Serializable
data class NotificationCountResponse(
    @SerialName("id")
    val id: Long
)