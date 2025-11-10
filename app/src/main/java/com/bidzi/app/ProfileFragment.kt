package com.bidzi.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import com.bidzi.app.databinding.FragmentProfileBinding




import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope

import androidx.recyclerview.widget.LinearLayoutManager
import com.bidzi.app.auth.AuthActivity
import com.bidzi.app.auth.SessionUser
import com.bidzi.app.auth.UserProfileUpdate
import com.bidzi.app.auth.UserProfile
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var menuAdapter: MenuAdapter
    private val menuItems = mutableListOf<MenuItem>()
    private var isEditMode = false
    private var currentUserProfile: UserProfile? = null
    private var isSaving = false

    private val firebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenuRecyclerView()
        loadMenuItems()
        setupClickListeners()
        setupEditTextWatchers()
        loadUserProfile()
    }

    private fun loadUserProfile() {
        val userId = SessionUser.currentUserId

        if (userId == null) {
            showToast("User not logged in")
            return
        }

        lifecycleScope.launch {
            try {
                val profiles = MyApplication.supabase
                    .from("user_profiles")
                    .select(columns = Columns.ALL) {
                        filter {
                            eq("user_id", userId)
                        }
                    }
                    .decodeList<UserProfile>()

                if (profiles.isNotEmpty()) {
                    currentUserProfile = profiles.first()
                    withContext(Dispatchers.Main) {
                        setupUserProfile(currentUserProfile!!)
                    }
                } else {
                    // Create default profile using upsert
                    createDefaultProfile(userId)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    showToast("Error loading profile: ${e.message}")
                }
            }
        }
    }

    private suspend fun createDefaultProfile(userId: String) {
        try {
            val firebaseUser = firebaseAuth.currentUser
            val phoneNumber = firebaseUser?.phoneNumber ?: "+91 0000000000"

            val last4Digits = phoneNumber.replace(Regex("[^0-9]"), "").takeLast(4)
            val defaultName = "User$last4Digits"

            val newProfile = UserProfile(
                userId = userId,
                name = defaultName,
                phoneNumber = phoneNumber,
                profileInitial = defaultName.first().toString().uppercase(),
                totalRides = 0,
                rating = null,
                totalSpent = 0
            )

            // Use upsert instead of insert - safer for concurrent requests
            MyApplication.supabase
                .from("user_profiles")
                .upsert(newProfile) {
                    onConflict = "user_id"  // Specify the unique constraint column
                }

            currentUserProfile = newProfile

            withContext(Dispatchers.Main) {
                setupUserProfile(newProfile)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                showToast("Error creating profile: ${e.message}")
            }
        }
    }

    private fun setupUserProfile(userProfile: UserProfile) {
        binding.apply {
            tvProfileName.text = userProfile.name
            tvPhoneNumber.text = userProfile.phoneNumber
            tvProfileInitial.text = userProfile.profileInitial
                ?: userProfile.name.firstOrNull()?.uppercase()?.toString() ?: "U"

            // Set edit text values without triggering watchers
            etProfileName.setText(userProfile.name)
            etPhoneNumber.setText(
                userProfile.phoneNumber
                    .replace("+91", "")
                    .replace(" ", "")
                    .trim()
            )

            // Stats
            tvRidesCount.text = userProfile.totalRides.toString()
            tvRatingValue.text = userProfile.rating?.toString() ?: "-"
            tvSpentValue.text = "₹${userProfile.totalSpent}"
        }
    }

    private fun setupMenuRecyclerView() {
        menuAdapter = MenuAdapter(menuItems) { menuItem ->
            onMenuItemClick(menuItem)
        }

        binding.rvMenu.apply {
            adapter = menuAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun loadMenuItems() {
        menuItems.clear()
        menuItems.addAll(
            listOf(
                MenuItem(1, "Ride History", R.drawable.ic_ride_history, MenuItemType.RIDE_HISTORY),
                MenuItem(2, "My Ratings", R.drawable.ic_star_outline, MenuItemType.MY_RATINGS),
                MenuItem(3, "Help & Support", R.drawable.ic_help, MenuItemType.HELP_SUPPORT),
                MenuItem(4, "Settings", R.drawable.ic_settings, MenuItemType.SETTINGS),
                MenuItem(5, "Logout", R.drawable.ic_logout, MenuItemType.LOGOUT)
            )
        )
        menuAdapter.notifyDataSetChanged()
    }

    private fun setupClickListeners() {
        binding.apply {
            ivBack.setOnClickListener {
                if (isEditMode && hasUnsavedChanges()) {
                    showDiscardChangesDialog()
                } else {
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }

            tvEditToggle.setOnClickListener {
                if (!isSaving) {
                    toggleEditMode()
                }
            }

            btnSaveProfile.setOnClickListener {
                if (!isSaving) {
                    saveProfileChanges()
                }
            }
        }
    }

    private fun setupEditTextWatchers() {
        binding.apply {
            // Name EditText watcher
            etProfileName.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (isEditMode) {
                        tilProfileName.error = null
                        updateSaveButtonState()
                    }
                }
            })

            // Phone EditText watcher with auto-formatting
            etPhoneNumber.addTextChangedListener(object : TextWatcher {
                private var isFormatting = false
                private var deletingSpace = false

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    // Detect if user is deleting the space
                    if (count > 0 && after == 0) {
                        val charBeingDeleted = s?.getOrNull(start)
                        deletingSpace = charBeingDeleted == ' '
                    }
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    if (isFormatting || !isEditMode) return

                    isFormatting = true

                    try {
                        val currentText = s.toString()
                        val cursorPosition = etPhoneNumber.selectionStart

                        // Remove all non-digits
                        val digitsOnly = currentText.replace(Regex("[^0-9]"), "")

                        // Limit to 10 digits
                        val limitedDigits = if (digitsOnly.length > 10) {
                            digitsOnly.substring(0, 10)
                        } else {
                            digitsOnly
                        }

                        // Format as XXXXX XXXXX
                        val formatted = when {
                            limitedDigits.isEmpty() -> ""
                            limitedDigits.length <= 5 -> limitedDigits
                            else -> "${limitedDigits.substring(0, 5)} ${limitedDigits.substring(5)}"
                        }

                        // Only update if text actually changed
                        if (formatted != currentText) {
                            etPhoneNumber.setText(formatted)

                            // Smart cursor positioning
                            val newCursorPosition = when {
                                // If we just added the space after 5 digits
                                formatted.length == 6 && cursorPosition == 5 -> 6
                                // If user deleted the space, move cursor before the space
                                deletingSpace && cursorPosition > 0 -> (cursorPosition - 1).coerceAtLeast(0)
                                // If we're at or past the space position and there's a space
                                cursorPosition >= 5 && formatted.length > 6 -> {
                                    // Count digits before cursor in old text
                                    val digitsBefore = currentText.substring(0, cursorPosition.coerceAtMost(currentText.length))
                                        .count { it.isDigit() }
                                    // Find position in new text with same digit count
                                    var newPos = 0
                                    var digitCount = 0
                                    for (i in formatted.indices) {
                                        if (formatted[i].isDigit()) digitCount++
                                        if (digitCount >= digitsBefore) {
                                            newPos = (i + 1).coerceAtMost(formatted.length)
                                            break
                                        }
                                    }
                                    newPos
                                }
                                // Default: try to maintain cursor position
                                else -> cursorPosition.coerceAtMost(formatted.length)
                            }

                            etPhoneNumber.setSelection(newCursorPosition.coerceIn(0, formatted.length))
                        }

                        tilPhoneNumber.error = null
                        if (isEditMode) {
                            updateSaveButtonState()
                        }

                        deletingSpace = false

                    } catch (e: Exception) {
                        Log.e("ProfileFragment", "Error formatting phone number", e)
                        deletingSpace = false
                    } finally {
                        isFormatting = false
                    }
                }
            })
        }
    }

    private fun updateSaveButtonState() {
        binding.btnSaveProfile.isEnabled = hasUnsavedChanges() && isValidInput()
    }

    private fun hasUnsavedChanges(): Boolean {
        currentUserProfile ?: return false

        val currentName = binding.etProfileName.text.toString().trim()
        val currentPhone = binding.etPhoneNumber.text.toString().replace(" ", "").trim()
        val originalPhone = currentUserProfile?.phoneNumber
            ?.replace("+91", "")
            ?.replace(" ", "")
            ?.trim() ?: ""

        return currentName != currentUserProfile?.name || currentPhone != originalPhone
    }

    private fun isValidInput(): Boolean {
        val name = binding.etProfileName.text.toString().trim()
        val phone = binding.etPhoneNumber.text.toString().replace(" ", "").trim()
        return name.isNotEmpty() && phone.length == 10
    }

    private fun toggleEditMode() {
        isEditMode = !isEditMode

        binding.apply {
            if (isEditMode) {
                enterEditMode()
            } else {
                if (hasUnsavedChanges()) {
                    showDiscardChangesDialog()
                } else {
                    exitEditMode()
                }
            }
        }
    }

    private fun enterEditMode() {
        binding.apply {
            // Smooth transition
            llNameDisplay.animate()
                .alpha(0f)
                .setDuration(150)
                .withEndAction {
                    llNameDisplay.visibility = View.GONE
                    llEditMode.visibility = View.VISIBLE
                    llEditMode.alpha = 0f
                    llEditMode.animate()
                        .alpha(1f)
                        .setDuration(150)
                        .start()
                }
                .start()

            tvEditToggle.text = "Cancel"
            tvEditToggle.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))

            btnSaveProfile.visibility = View.VISIBLE
            btnSaveProfile.isEnabled = false
            btnSaveProfile.alpha = 0f
            btnSaveProfile.animate()
                .alpha(1f)
                .setDuration(200)
                .start()

            // Request focus with delay for smooth animation
            etProfileName.postDelayed({
                etProfileName.requestFocus()
                etProfileName.setSelection(etProfileName.text?.length ?: 0)
                showKeyboard(etProfileName)
            }, 200)
        }
    }

    private fun exitEditMode() {
        binding.apply {
            // Reset to original values
            currentUserProfile?.let { profile ->
                etProfileName.setText(profile.name)
                etPhoneNumber.setText(
                    profile.phoneNumber
                        .replace("+91", "")
                        .replace(" ", "")
                        .trim()
                )
            }

            // Smooth transition
            llEditMode.animate()
                .alpha(0f)
                .setDuration(150)
                .withEndAction {
                    llEditMode.visibility = View.GONE
                    llNameDisplay.visibility = View.VISIBLE
                    llNameDisplay.alpha = 0f
                    llNameDisplay.animate()
                        .alpha(1f)
                        .setDuration(150)
                        .start()
                }
                .start()

            tvEditToggle.text = "Edit"
            tvEditToggle.setTextColor(ContextCompat.getColor(requireContext(), R.color.orange))

            btnSaveProfile.animate()
                .alpha(0f)
                .setDuration(150)
                .withEndAction {
                    btnSaveProfile.visibility = View.GONE
                    btnSaveProfile.isEnabled = false
                }
                .start()

            isEditMode = false
            hideKeyboard()

            // Clear errors
            tilProfileName.error = null
            tilPhoneNumber.error = null
        }
    }

    private fun showDiscardChangesDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Discard Changes?")
            .setMessage("You have unsaved changes. Do you want to discard them?")
            .setPositiveButton("Discard") { _, _ ->
                isEditMode = true // Set to true so exitEditMode works properly
                exitEditMode()
            }
            .setNegativeButton("Keep Editing", null)
            .show()
    }

    private fun saveProfileChanges() {
        val newName = binding.etProfileName.text.toString().trim()
        val newPhone = binding.etPhoneNumber.text.toString().replace(" ", "").trim()

        // Validation
        if (newName.isEmpty()) {
            binding.tilProfileName.error = "Name cannot be empty"
            binding.etProfileName.requestFocus()
            return
        }

        if (newPhone.length != 10) {
            binding.tilPhoneNumber.error = "Enter valid 10-digit number"
            binding.etPhoneNumber.requestFocus()
            return
        }

        // Disable button and set saving state
        isSaving = true
        binding.btnSaveProfile.isEnabled = false
        binding.btnSaveProfile.text = "Saving..."
        binding.tvEditToggle.isEnabled = false

        // Format phone number
        val formattedPhone = "+91 ${newPhone.substring(0, 5)} ${newPhone.substring(5)}"

        // Save to backend using upsert
        upsertProfileToBackend(newName, formattedPhone)
    }

    private fun upsertProfileToBackend(name: String, phone: String) {
        val userId = SessionUser.currentUserId

        if (userId == null) {
            showToast("User not logged in")
            resetSaveButton()
            return
        }

        lifecycleScope.launch {
            try {
                val profileInitial = name.first().uppercase().toString()

                // Create complete profile object for upsert
                val profileToUpsert = UserProfile(
                    userId = userId,
                    name = name,
                    phoneNumber = phone,
                    profileInitial = profileInitial,
                    totalRides = currentUserProfile?.totalRides ?: 0,
                    rating = currentUserProfile?.rating,
                    totalSpent = currentUserProfile?.totalSpent ?: 0
                )

                // Use upsert - will update if exists, insert if not
                MyApplication.supabase
                    .from("user_profiles")
                    .upsert(profileToUpsert) {
                        onConflict = "user_id"  // Specify unique constraint
                    }

                // Update local copy
                currentUserProfile = profileToUpsert

                withContext(Dispatchers.Main) {
                    // Update UI with new values
                    binding.tvProfileName.text = name
                    binding.tvPhoneNumber.text = phone
                    binding.tvProfileInitial.text = profileInitial

                    showSuccessToast()

                    // Exit edit mode after successful save
                    exitEditMode()
                    resetSaveButton()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    showToast("Error updating profile: ${e.message}")
                    resetSaveButton()
                }
            }
        }
    }

    private fun resetSaveButton() {
        isSaving = false
        binding.btnSaveProfile.isEnabled = true
        binding.btnSaveProfile.text = "Save Changes"
        binding.tvEditToggle.isEnabled = true
    }

    private fun showSuccessToast() {
        Toast.makeText(
            requireContext(),
            "✓ Profile updated successfully",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun showKeyboard(view: View) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    private fun onMenuItemClick(menuItem: MenuItem) {
        // Prevent clicks during edit mode
        if (isEditMode) {
            showToast("Please save or cancel editing first")
            return
        }

        when (menuItem.type) {
            MenuItemType.RIDE_HISTORY -> {
                showToast("Ride History")
            }
            MenuItemType.MY_RATINGS -> {
                showToast("My Ratings")
            }
            MenuItemType.HELP_SUPPORT -> {
                showToast("Help & Support")
            }
            MenuItemType.SETTINGS -> {
                showToast("Settings")
            }
            MenuItemType.LOGOUT -> {
                showLogoutConfirmation()
            }
        }
    }

    private fun showLogoutConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                performLogout()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performLogout() {
        // Clear session and navigate to login
        SessionUser.currentUserId = null
        firebaseAuth.signOut()
        showToast("Logged out successfully")

        // Navigate to login screen
        val intent = Intent(requireContext(), AuthActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        hideKeyboard()
        _binding = null
    }
}

