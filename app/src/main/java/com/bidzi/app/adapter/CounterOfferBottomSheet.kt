package com.bidzi.app.adapter

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bidzi.app.Driver
import com.bidzi.app.MyApplication
import com.bidzi.app.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

import com.bidzi.app.databinding.BottomSheetCounterOfferBinding
import com.bidzi.app.supabase.CounterOffer
import com.bidzi.app.supabase.CounterOfferStatus
import com.bidzi.app.supabase.OfferedBy
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class CounterOfferBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetCounterOfferBinding? = null
    private val binding get() = _binding!!

    private lateinit var driver: Driver
    private lateinit var rideId: String
    private lateinit var userId: String

    private val supabase by lazy { MyApplication.supabase }
    private var isSubmitting = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetCounterOfferBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupData()
        setupListeners()
        setupChips()

        // Auto-focus keyboard
        binding.etCounterPrice.requestFocus()
        showKeyboard()
    }

    // ========================================
    // SETUP UI
    // ========================================

    private fun setupData() {
        binding.apply {
            tvDriverInitials.text = driver.initials
            tvDriverName.text = driver.name
            tvRating.text = driver.rating.toString()
            tvVehicleInfo.text = driver.vehicleType
            tvCurrentPrice.text = "₹${driver.price}"

            // Set suggested price hint
            val suggestedPrice = (driver.price * 0.9).toInt()
            etCounterPrice.hint = suggestedPrice.toString()

            // Calculate min/max
            val minCounter = (driver.price * 0.5).toInt()
            val maxCounter = driver.price - 1
            tvSubtitle.text = "Counter between ₹$minCounter - ₹$maxCounter"
        }
    }

    private fun setupListeners() {
        binding.apply {
            btnClose.setOnClickListener { dismiss() }
            btnCancel.setOnClickListener { dismiss() }

            btnSendCounter.setOnClickListener {
                if (!isSubmitting) {
                    submitCounterOffer()
                }
            }

            btnClearPrice.setOnClickListener {
                etCounterPrice.text?.clear()
                btnClearPrice.visibility = View.GONE
            }

            etCounterPrice.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    btnClearPrice.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                    updateButtonState()
                    updateValidationMessage(s)
                }

                override fun afterTextChanged(s: Editable?) {}
            })
        }
    }

    private fun setupChips() {
        val currentPrice = driver.price
        val suggestions = listOf(
            (currentPrice * 0.75).toInt(),
            (currentPrice * 0.85).toInt(),
            (currentPrice * 0.90).toInt(),
            (currentPrice * 0.95).toInt()
        )

        binding.apply {
            val chips = listOf(chipPrice1, chipPrice2, chipPrice3, chipPrice4)

            chips.forEachIndexed { index, chip ->
                val price = suggestions[index]
                val savings = currentPrice - price
                chip.text = "₹$price (-₹$savings)"

                chip.setOnClickListener {
                    etCounterPrice.setText(price.toString())
                    etCounterPrice.setSelection(etCounterPrice.text?.length ?: 0)
                }
            }
        }
    }

    // ========================================
    // VALIDATION & UI FEEDBACK
    // ========================================

    private fun updateValidationMessage(text: CharSequence?) {
        if (text.isNullOrEmpty()) {
            binding.tvNote.apply {
                this.text = "💡 Lower offers have higher acceptance rates"
                setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.yellow_light))
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            }
            return
        }

        val price = text.toString().toIntOrNull() ?: return
        val minCounter = (driver.price * 0.5).toInt()

        when {
            price >= driver.price -> {
                binding.tvNote.apply {
                    this.text = "⚠️ Counter must be less than ₹${driver.price}"
                    setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red_light))
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.red))
                }
            }
            price < minCounter -> {
                binding.tvNote.apply {
                    this.text = "⚠️ Minimum counter price is ₹$minCounter"
                    setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red_light))
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.red))
                }
            }
            else -> {
                val savings = driver.price - price
                val percentage = ((savings.toFloat() / driver.price) * 100).toInt()
                binding.tvNote.apply {
                    this.text = "✓ Good offer! You'll save ₹$savings ($percentage% off)"
                    setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_light))
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.green))
                }
            }
        }
    }


    private fun updateButtonState() {
        val counterPriceText = binding.etCounterPrice.text.toString()
        val price = counterPriceText.toIntOrNull()
        val minCounter = (driver.price * 0.5).toInt()

        val isValid = price != null && price > 0 && price >= minCounter && price < driver.price

        binding.btnSendCounter.apply {
            isEnabled = isValid && !isSubmitting
            alpha = if (isValid && !isSubmitting) 1.0f else 0.5f
            setCardBackgroundColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (isValid && !isSubmitting) R.color.orange else R.color.gray_medium
                )
            )
        }
    }

    // ========================================
    // SUBMIT COUNTER OFFER TO SUPABASE
    // ========================================

    private fun submitCounterOffer() {
        val counterPriceText = binding.etCounterPrice.text.toString()
        val counterPrice = counterPriceText.toIntOrNull()

        // Validate input
        if (counterPrice == null || counterPrice <= 0) {
            Toast.makeText(requireContext(), "Enter valid price", Toast.LENGTH_SHORT).show()
            return
        }

        val minCounter = (driver.price * 0.5).toInt()
        if (counterPrice < minCounter || counterPrice >= driver.price) {
            Toast.makeText(
                requireContext(),
                "Counter must be between ₹$minCounter - ₹${driver.price - 1}",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Show loading state
        isSubmitting = true
        updateButtonState()
        binding.btnSendCounter.findViewById<TextView>(R.id.tvSendCounterText)?.text = "Sending..."

        lifecycleScope.launch {
            try {
                // Create counter offer object
                val counterOffer = CounterOffer(
                    rideId = rideId,
                    driverId = driver.id,
                    userId = userId,
                    counterPrice = counterPrice,
                    offeredBy = OfferedBy.USER,
                    status = CounterOfferStatus.PENDING,
                    createdAt = getCurrentTimestamp()
                )

                // Insert into Supabase
                val insertedCounter = supabase
                    .from("counter_offers")
                    .insert(counterOffer) {
                        select()
                    }
                    .decodeSingle<CounterOffer>()

                Log.d("CounterOfferBS", "Counter offer created: ${insertedCounter.id}")

                // Show success and dismiss
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "Counter offer sent to ${driver.name}",
                        Toast.LENGTH_SHORT
                    ).show()

                    dismiss()
                }

            } catch (e: Exception) {
                Log.e("CounterOfferBS", "Error submitting counter offer", e)

                withContext(Dispatchers.Main) {
                    isSubmitting = false
                    updateButtonState()
                    binding.btnSendCounter.findViewById<TextView>(R.id.tvSendCounterText)?.text = "Send Counter"

                    Toast.makeText(
                        requireContext(),
                        "Failed to send counter: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // ========================================
    // HELPER FUNCTIONS
    // ========================================

    private fun getCurrentTimestamp(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
        return dateFormat.format(Date())
    }

    private fun showKeyboard() {
        binding.etCounterPrice.postDelayed({
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.etCounterPrice, InputMethodManager.SHOW_IMPLICIT)
        }, 100)
    }

    // ========================================
    // LIFECYCLE
    // ========================================

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ========================================
    // COMPANION OBJECT
    // ========================================

    companion object {
        fun newInstance(
            driver: Driver,
            rideId: String,
            userId: String
        ): CounterOfferBottomSheet {
            return CounterOfferBottomSheet().apply {
                this.driver = driver
                this.rideId = rideId
                this.userId = userId
            }
        }
    }
}


