package com.bidzi.app.notification

import android.view.View
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bidzi.app.MyApplication
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch
import kotlin.compareTo
import kotlin.toString

// NotificationViewModel.kt
// NotificationViewModel.kt
class NotificationViewModel(private val userId: String) : ViewModel() {

    private val _notifications = MutableLiveData<List<Notification>>()
    val notifications: LiveData<List<Notification>> = _notifications

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _hasUnreadNotifications = MutableLiveData<Boolean>()
    val hasUnreadNotifications: LiveData<Boolean> = _hasUnreadNotifications

    private val _unreadCount = MutableLiveData<Int>()
    val unreadCount: LiveData<Int> = _unreadCount

    init {
        loadNotifications()
    }

    fun loadNotifications() {
        if (_isLoading.value == true) return

        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                val fetchedNotifications = MyApplication.Companion.supabase
                    .from("notifications")
                    .select(columns = Columns.ALL) {
                        filter {
                            eq("user_id", userId)
                        }
                        order("created_at", Order.DESCENDING)
                        limit(100)
                    }
                    .decodeList<Notification>()

                _notifications.postValue(fetchedNotifications)
                updateUnreadStatus(fetchedNotifications)

            } catch (e: Exception) {
                e.printStackTrace()
                _error.postValue("Error loading notifications: ${e.message}")
                _notifications.postValue(emptyList())
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    fun loadUnreadNotificationCount() {
        viewModelScope.launch {
            try {
                val notifications = MyApplication.Companion.supabase
                    .from("notifications")
                    .select(columns = Columns.list("id")) {
                        filter {
                            eq("user_id", userId)
                            eq("is_read", false)
                        }
                    }
                    .decodeList<NotificationCountResponse>()

                val count = notifications.size
                _unreadCount.postValue(count)

            } catch (e: Exception) {
                e.printStackTrace()
                _unreadCount.postValue(0)
            }
        }
    }

    fun markAsRead(notificationId: Long) {
        viewModelScope.launch {
            try {
                MyApplication.Companion.supabase
                    .from("notifications")
                    .update(NotificationUpdate(isRead = true)) {
                        filter {
                            eq("id", notificationId)
                        }
                    }

                // Update local list
                val currentList = _notifications.value ?: return@launch
                val updatedList = currentList.map {
                    if (it.id == notificationId) it.copy(isRead = true) else it
                }
                _notifications.postValue(updatedList)
                updateUnreadStatus(updatedList)

            } catch (e: Exception) {
                e.printStackTrace()
                _error.postValue("Failed to mark as read")
            }
        }
    }

    fun markAllAsRead() {
        val currentList = _notifications.value ?: return
        val hasUnread = currentList.any { !it.isRead }

        if (!hasUnread) {
            _error.postValue("No unread notifications")
            return
        }

        viewModelScope.launch {
            try {
                MyApplication.Companion.supabase
                    .from("notifications")
                    .update(NotificationUpdate(isRead = true)) {
                        filter {
                            eq("user_id", userId)
                            eq("is_read", false)
                        }
                    }

                // Update local list
                val updatedList = currentList.map { it.copy(isRead = true) }
                _notifications.postValue(updatedList)
                updateUnreadStatus(updatedList)

            } catch (e: Exception) {
                e.printStackTrace()
                _error.postValue("Failed to mark all as read")
            }
        }
    }

    fun deleteNotification(notificationId: Long) {
        viewModelScope.launch {
            try {
                MyApplication.Companion.supabase
                    .from("notifications")
                    .delete {
                        filter {
                            eq("id", notificationId)
                        }
                    }

                val currentList = _notifications.value ?: return@launch
                val updatedList = currentList.filter { it.id != notificationId }
                _notifications.postValue(updatedList)
                updateUnreadStatus(updatedList)

            } catch (e: Exception) {
                e.printStackTrace()
                _error.postValue("Failed to delete notification")
            }
        }
    }

    fun clearAllNotifications() {
        viewModelScope.launch {
            try {
                MyApplication.Companion.supabase
                    .from("notifications")
                    .delete {
                        filter {
                            eq("user_id", userId)
                        }
                    }

                _notifications.postValue(emptyList())
                _hasUnreadNotifications.postValue(false)
                _unreadCount.postValue(0)

            } catch (e: Exception) {
                e.printStackTrace()
                _error.postValue("Failed to clear notifications")
            }
        }
    }

    private fun updateUnreadStatus(notifications: List<Notification>) {
        val unreadList = notifications.filter { !it.isRead }
        _hasUnreadNotifications.postValue(unreadList.isNotEmpty())
        _unreadCount.postValue(unreadList.size)
    }

    fun clearError() {
        _error.value = null
    }
}
// ViewModelFactory
class NotificationViewModelFactory(
    private val userId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NotificationViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NotificationViewModel(userId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}