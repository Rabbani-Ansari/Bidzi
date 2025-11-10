package com.bidzi.app.adapter

// AvailableDriversAdapter.kt
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bidzi.app.Driver
import com.bidzi.app.OfferType
import com.bidzi.app.R
import com.bidzi.app.databinding.ItemAvailableDriverBinding
import com.bidzi.app.supabase.CounterOfferStatus
import com.bidzi.app.supabase.OfferedBy
import com.google.android.material.card.MaterialCardView

class AvailableDriversAdapter(
    private val onCounterClick: (Driver) -> Unit,
    private val onAcceptClick: (Driver) -> Unit
) : ListAdapter<Driver, AvailableDriversAdapter.DriverViewHolder>(DriverDiffCallback()) {

    private var confirmedDriverId: String? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DriverViewHolder {
        val binding = ItemAvailableDriverBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DriverViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DriverViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun setConfirmedDriver(driverId: String) {
        confirmedDriverId = driverId
        notifyDataSetChanged()
    }

    fun clearConfirmedDriver() {
        confirmedDriverId = null
        notifyDataSetChanged()
    }

    inner class DriverViewHolder(
        private val binding: ItemAvailableDriverBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(driver: Driver) {
            binding.apply {
                // Basic driver info
                tvDriverInitials.text = driver.initials
                tvDriverName.text = driver.name
                tvRating.text = driver.rating.toString()
                tvTotalTrips.text = "• ${driver.totalTrips} trips"
                tvPrice.text = "₹${driver.price}"
                tvVehicleInfo.text = "${driver.vehicleType} • ${driver.vehicleNumber}"
                tvEta.text = "${driver.eta} min"

                // Online status
                viewOnlineStatus.visibility = if (driver.isOnline) View.VISIBLE else View.GONE

                // Savings
                if (driver.savings > 0) {
                    llSavings.visibility = View.VISIBLE
                    tvSavings.text = "₹${driver.savings}"
                } else {
                    llSavings.visibility = View.GONE
                }

                // Reset card styling
                cardDriver.strokeWidth = 0
                cardDriver.setCardBackgroundColor(
                    ContextCompat.getColor(itemView.context, android.R.color.white)
                )

                // Check confirmation states
                val isThisDriverConfirmed = driver.isConfirmed || driver.id == confirmedDriverId
                val isAnotherDriverConfirmed = confirmedDriverId != null && driver.id != confirmedDriverId

                // Handle counter offer display FIRST
                handleCounterOfferUI(driver)

                // Handle confirmed/unconfirmed states
                when {
                    isThisDriverConfirmed -> bindConfirmedState()
                    isAnotherDriverConfirmed -> bindDisabledState(driver)
                    else -> bindNormalState(driver)
                }

                // Click listeners
                btnCounter.setOnClickListener {
                    if (!isThisDriverConfirmed && !isAnotherDriverConfirmed &&
                        !driver.hasActiveCounter) {
                        onCounterClick(driver)
                    }
                }

                btnAccept.setOnClickListener {
                    if (!isThisDriverConfirmed && !isAnotherDriverConfirmed) {
                        onAcceptClick(driver)
                        setConfirmedDriver(driver.id)
                    }
                }
            }
        }

        private fun ItemAvailableDriverBinding.handleCounterOfferUI(driver: Driver) {
            if (driver.hasActiveCounter && driver.counterOfferedBy == OfferedBy.USER) {
                // User has made a counter offer - show status card
                cardCounterStatus.visibility = View.VISIBLE

                when (driver.counterStatus) {
                    CounterOfferStatus.PENDING -> {
                        // Pending state - BRIGHT ORANGE
                        cardCounterStatus.setCardBackgroundColor(
                            ContextCompat.getColor(itemView.context, R.color.orange_light)
                        )
                        cardCounterStatus.strokeColor =
                            ContextCompat.getColor(itemView.context, R.color.orange_dark)
                        cardCounterStatus.strokeWidth = 6

                        tvCounterStatusLabel.text = "Your Counter Offer"
                        tvCounterStatusLabel.setTextColor(
                            ContextCompat.getColor(itemView.context, R.color.text_dark)
                        )

                        tvCounterPrice.text = "₹${driver.activeCounterPrice}"
                        tvCounterPrice.setTextColor(
                            ContextCompat.getColor(itemView.context, R.color.orange_dark)
                        )

                        tvCounterStatusText.text = "⏳ Waiting for driver's response..."
                        tvCounterStatusText.setTextColor(
                            ContextCompat.getColor(itemView.context, R.color.text_medium)
                        )

                        ivCounterStatusIcon.setImageResource(R.drawable.clock)
                        ivCounterStatusIcon.setColorFilter(
                            ContextCompat.getColor(itemView.context, R.color.orange_dark)
                        )

                        // Disable counter button
                        btnCounter.isEnabled = false
                        btnCounter.alpha = 0.5f
                        tvCounterText.text = "Pending"
                    }

                    CounterOfferStatus.ACCEPTED -> {
                        // Accepted state - BRIGHT GREEN
                        cardCounterStatus.setCardBackgroundColor(
                            ContextCompat.getColor(itemView.context, R.color.green_light)
                        )
                        cardCounterStatus.strokeColor =
                            ContextCompat.getColor(itemView.context, R.color.green_dark)
                        cardCounterStatus.strokeWidth = 6

                        tvCounterStatusLabel.text = "Counter Accepted!"
                        tvCounterStatusLabel.setTextColor(
                            ContextCompat.getColor(itemView.context, R.color.text_dark)
                        )

                        tvCounterPrice.text = "₹${driver.activeCounterPrice}"
                        tvCounterPrice.setTextColor(
                            ContextCompat.getColor(itemView.context, R.color.green_dark)
                        )

                        tvCounterStatusText.text = "✓ Driver accepted your offer"
                        tvCounterStatusText.setTextColor(
                            ContextCompat.getColor(itemView.context, R.color.text_medium)
                        )

                        ivCounterStatusIcon.setImageResource(R.drawable.check)
                        ivCounterStatusIcon.setColorFilter(
                            ContextCompat.getColor(itemView.context, R.color.green_dark)
                        )

                        // Highlight accept button
                        btnAccept.setCardBackgroundColor(
                            ContextCompat.getColor(itemView.context, R.color.green)
                        )

                        // Pulse animation
                        btnAccept.animate()
                            .scaleX(1.05f).scaleY(1.05f)
                            .setDuration(300)
                            .withEndAction {
                                btnAccept.animate().scaleX(1f).scaleY(1f).setDuration(300).start()
                            }.start()
                    }

                    CounterOfferStatus.REJECTED -> {
                        // Rejected state - BRIGHT RED
                        cardCounterStatus.setCardBackgroundColor(
                            ContextCompat.getColor(itemView.context, R.color.red_light)
                        )
                        cardCounterStatus.strokeColor =
                            ContextCompat.getColor(itemView.context, R.color.red_dark)
                        cardCounterStatus.strokeWidth = 6

                        tvCounterStatusLabel.text = "Counter Rejected"
                        tvCounterStatusLabel.setTextColor(
                            ContextCompat.getColor(itemView.context, R.color.text_dark)
                        )

                        tvCounterPrice.text = "₹${driver.activeCounterPrice}"
                        tvCounterPrice.setTextColor(
                            ContextCompat.getColor(itemView.context, R.color.red_dark)
                        )

                        tvCounterStatusText.text = "✗ Driver rejected your offer"
                        tvCounterStatusText.setTextColor(
                            ContextCompat.getColor(itemView.context, R.color.text_medium)
                        )

                        ivCounterStatusIcon.setImageResource(R.drawable.cancel)
                        ivCounterStatusIcon.setColorFilter(
                            ContextCompat.getColor(itemView.context, R.color.red_dark)
                        )

                        // Re-enable counter button
                        btnCounter.isEnabled = true
                        btnCounter.alpha = 1.0f
                        tvCounterText.text = "Counter"
                    }

                    else -> {}
                }

                // Update offer status text
                tvOfferStatus.text = when (driver.counterStatus) {
                    CounterOfferStatus.PENDING -> "Counter Pending • Sent ${driver.offerTime}"
                    CounterOfferStatus.ACCEPTED -> "Counter Accepted • ${driver.offerTime}"
                    CounterOfferStatus.REJECTED -> "Counter Rejected • ${driver.offerTime}"
                    else -> driver.offerType.name
                }
            } else {
                // No counter or not from user
                cardCounterStatus.visibility = View.GONE
                btnCounter.isEnabled = true
                btnCounter.alpha = 1.0f
                tvCounterText.text = "Counter"
            }
        }

        private fun ItemAvailableDriverBinding.bindConfirmedState() {
            // Show badges
            tvConfirmedBadge.visibility = View.VISIBLE
            tvBestOfferBadge.visibility = View.GONE

            // HIDE action buttons and counter card in confirmed state
            llActionButtons.visibility = View.GONE
            cardCounterStatus.visibility = View.GONE

            // SHOW confirmed button
            btnConfirmed.visibility = View.VISIBLE

            // Green border
            cardDriver.strokeColor = ContextCompat.getColor(itemView.context, R.color.green)
            cardDriver.strokeWidth = 4

            itemView.alpha = 1.0f
            cardDriver.isClickable = false
        }

        private fun ItemAvailableDriverBinding.bindDisabledState(driver: Driver) {
            tvConfirmedBadge.visibility = View.GONE
            tvBestOfferBadge.visibility = View.GONE

            // Show action buttons, hide confirmed button
            llActionButtons.visibility = View.VISIBLE
            btnConfirmed.visibility = View.GONE

            itemView.alpha = 0.5f

            btnCounter.isEnabled = false
            btnAccept.isEnabled = false
            btnCounter.alpha = 0.5f
            btnAccept.alpha = 0.5f

            btnAccept.setCardBackgroundColor(
                ContextCompat.getColor(itemView.context, R.color.gray_medium)
            )

            tvOfferStatus.text = when (driver.offerType) {
                OfferType.ACCEPTED_BID -> "Accepted Bid • ${driver.offerTime}"
                OfferType.COUNTER_OFFER -> "Counter Offer • ${driver.offerTime}"
                OfferType.BEST_OFFER -> "Best Offer • ${driver.offerTime}"
            }
        }

        private fun ItemAvailableDriverBinding.bindNormalState(driver: Driver) {
            tvConfirmedBadge.visibility = View.GONE

            // Show action buttons, hide confirmed button
            llActionButtons.visibility = View.VISIBLE
            btnConfirmed.visibility = View.GONE

            itemView.alpha = 1.0f

            btnCounter.isEnabled = true
            btnAccept.isEnabled = true
            btnCounter.alpha = 1.0f
            btnAccept.alpha = 1.0f

            btnAccept.setCardBackgroundColor(
                ContextCompat.getColor(itemView.context, R.color.orange)
            )

            // Handle offer type badges
            when (driver.offerType) {
                OfferType.ACCEPTED_BID -> {
                    tvBestOfferBadge.visibility = View.GONE
                    tvOfferStatus.text = "Accepted Bid • ${driver.offerTime}"
                }
                OfferType.COUNTER_OFFER -> {
                    tvBestOfferBadge.visibility = View.GONE
                    tvOfferStatus.text = "Counter Offer • ${driver.offerTime}"
                }
                OfferType.BEST_OFFER -> {
                    tvBestOfferBadge.visibility = View.VISIBLE
                    tvOfferStatus.text = "Best Offer • ${driver.offerTime}"

                    cardDriver.strokeColor = ContextCompat.getColor(itemView.context, R.color.orange)
                    cardDriver.strokeWidth = 4
                }
            }
        }
    }

    class DriverDiffCallback : DiffUtil.ItemCallback<Driver>() {
        override fun areItemsTheSame(oldItem: Driver, newItem: Driver): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Driver, newItem: Driver): Boolean {
            return oldItem.price == newItem.price &&
                    oldItem.offerType == newItem.offerType &&
                    oldItem.eta == newItem.eta &&
                    oldItem.savings == newItem.savings &&
                    oldItem.isConfirmed == newItem.isConfirmed &&
                    oldItem.isOnline == newItem.isOnline &&
                    oldItem.hasActiveCounter == newItem.hasActiveCounter &&
                    oldItem.activeCounterPrice == newItem.activeCounterPrice &&
                    oldItem.counterStatus == newItem.counterStatus
        }

        override fun getChangePayload(oldItem: Driver, newItem: Driver): Any? {
            return if (!areContentsTheSame(oldItem, newItem)) "UPDATE" else null
        }
    }
}