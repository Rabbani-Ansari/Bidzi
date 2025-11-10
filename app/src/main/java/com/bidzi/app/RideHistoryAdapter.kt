package com.bidzi.app

// Adapter
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bidzi.app.databinding.ItemRideHistoryBinding


class RideHistoryAdapter(
    private val rides: List<RideHistory>,
    private val onRideClick: (RideHistory) -> Unit
) : RecyclerView.Adapter<RideHistoryAdapter.RideHistoryViewHolder>() {

    inner class RideHistoryViewHolder(private val binding: ItemRideHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(ride: RideHistory) {
            binding.apply {
                // Destination
                tvDestination.text = ride.destination
                tvFare.text = "₹${ride.fare}"

                // Source
                tvFromLocation.text = "From: ${ride.fromLocation}"

                // Date and Time
                tvDateTime.text = ride.dateTime

                // Shared badge visibility
                if (ride.isShared) {
                    tvSharedBadge.visibility = android.view.View.VISIBLE
                    // Show passengers
                    tvWithPassengers.visibility = android.view.View.VISIBLE
                    tvWithPassengers.text = if (ride.withPassengers != null) {
                        "With: ${ride.withPassengers.joinToString(", ")}"
                    } else {
                        ""
                    }
                } else {
                    tvSharedBadge.visibility = android.view.View.GONE
                    tvWithPassengers.visibility = android.view.View.GONE
                }

                // Distance and duration
                tvDistanceDuration.text = "${ride.distance} • ${ride.duration}"

                // Rating
                tvRating.text = ride.rating.toString()

                // Click listener
                root.setOnClickListener {
                    onRideClick(ride)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RideHistoryViewHolder {
        val binding = ItemRideHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RideHistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RideHistoryViewHolder, position: Int) {
        holder.bind(rides[position])
    }

    override fun getItemCount(): Int = rides.size
}
