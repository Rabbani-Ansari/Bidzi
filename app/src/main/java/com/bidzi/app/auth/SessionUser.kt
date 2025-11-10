package com.bidzi.app.auth





import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

object SessionUser {
    @Volatile
    private var _currentUserId: String? = null

    // Renamed to avoid clash with property getter
    private fun fetchCurrentUserId(): String? {
        synchronized(this) {
            if (_currentUserId == null) {
                _currentUserId = FirebaseAuth.getInstance().currentUser?.uid
            }
            return _currentUserId
        }
    }

    // Public property for safe access, returns null if not logged in
    var currentUserId: String? = null
        get() = fetchCurrentUserId()

    // Initialize or refresh the session (call during app startup or after login)
    fun initialize() {
        synchronized(this) {
            _currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        }
    }

    // Clear session (call on logout)
    fun clear() {
        synchronized(this) {
            _currentUserId = null
        }
    }

    // Check if user is logged in
    val isUserLoggedIn: Boolean
        get() = synchronized(this) {
            _currentUserId != null || FirebaseAuth.getInstance().currentUser != null
        }

    // Get userId or throw exception (for cases where login is required)
    fun requireUserId(): String {
        return currentUserId ?: throw IllegalStateException("User not logged in")
    }

    // Optional: Refresh token for critical operations (e.g., payments)
    suspend fun refreshTokenIfNeeded(): Boolean {
        return try {
            FirebaseAuth.getInstance().currentUser?.getIdToken(true)?.await()
            synchronized(this) {
                _currentUserId = FirebaseAuth.getInstance().currentUser?.uid
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    // Optional: Listen for auth state changes (if needed)
    fun addAuthStateListener(listener: FirebaseAuth.AuthStateListener) {
        FirebaseAuth.getInstance().addAuthStateListener(listener)
    }

    fun removeAuthStateListener(listener: FirebaseAuth.AuthStateListener) {
        FirebaseAuth.getInstance().removeAuthStateListener(listener)
    }
}


