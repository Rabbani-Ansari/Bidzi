package com.bidzi.app
// Adapter
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bidzi.app.databinding.ItemRideBinding

import android.view.View

import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter

import com.bidzi.app.Driver
import com.bidzi.app.OfferType
import com.bidzi.app.R
import com.bidzi.app.databinding.ItemAvailableDriverBinding
import com.bidzi.app.model.SharedRide
import com.bidzi.app.supabase.CounterOfferStatus
import com.bidzi.app.supabase.OfferedBy
import com.google.android.material.card.MaterialCardView


class RideAdapter(
    private val onJoinClick: (SharedRide) -> Unit
) : ListAdapter<SharedRide, RideAdapter.RideViewHolder>(RideDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RideViewHolder {
        val binding = ItemRideBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RideViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RideViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RideViewHolder(
        private val binding: ItemRideBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(ride: SharedRide) {
            binding.apply {
                // Pickup
                tvPickupLabel.text = "Pickup"
                tvPickupName.text = ride.pickupLocation
                tvPickupDistance.text = ride.pickupDistanceText

                // Drop
                tvDropLabel.text = "Drop"
                tvDropName.text = ride.dropLocation

                // Distance
                tvDistance.text = ride.distanceText

                // Seats and timing
                tvSeatsInfo.text = ride.seatsInfo
                tvLeavesIn.text = "Leaves in ${ride.leavesInText}"

                // Driver info
                tvDriverName.text = ride.driverName
                tvDriverRating.text = "${ride.driverRating} ⭐"
                tvDriverTrips.text = "${ride.driverTotalTrips} trips"

                // Vehicle
                tvVehicleInfo.text = "${ride.vehicleType} • ${ride.vehicleNumber}"

                // Online status
                viewOnlineIndicator.visibility = if (ride.driverIsOnline) {
                    View.VISIBLE
                } else {
                    View.GONE
                }

                // Fare
                tvFare.text = ride.fareText

                // Join button
                btnJoinRide.setOnClickListener {
                    onJoinClick(ride)
                }
            }
        }
    }
}

class RideDiffCallback : DiffUtil.ItemCallback<SharedRide>() {
    override fun areItemsTheSame(oldItem: SharedRide, newItem: SharedRide) =
        oldItem.id == newItem.id

    override fun areContentsTheSame(oldItem: SharedRide, newItem: SharedRide) =
        oldItem == newItem
}