package com.bidzi.app.adapter

import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bidzi.app.R
import com.bidzi.app.databinding.ItemVehicleTypeBinding
data class VehicleType(
    val id: String,
    val name: String,
    val iconResId: Int,
    val priceMultiplier: Double,  // Changed from baseMultiplier
    var isSelected: Boolean = false
)


class VehicleTypeAdapter(
    private val vehicles: List<VehicleType>,
    private val onVehicleSelected: (VehicleType) -> Unit
) : RecyclerView.Adapter<VehicleTypeAdapter.ViewHolder>() {

    private var selectedPosition = -1


    // NEW: Method to programmatically select a vehicle
    fun selectDefaultVehicle(position: Int) {
        if (position in vehicles.indices) {
            selectedPosition = position
            notifyItemChanged(position)
            onVehicleSelected(vehicles[position])
        }
    }

    inner class ViewHolder(private val binding: ItemVehicleTypeBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(vehicle: VehicleType, position: Int) {
            Log.d("VehicleAdapter", "Binding: ${vehicle.name} at position $position")

            binding.vehicleIcon.setImageResource(vehicle.iconResId)
            binding.vehicleName.text = vehicle.name

            // Highlight selected vehicle
            val strokeColor = if (position == selectedPosition) {
                ContextCompat.getColor(binding.root.context, R.color.primary_color)
            } else {
                ContextCompat.getColor(binding.root.context, R.color.stroke_gray)
            }
            binding.vehicleCard.strokeColor = strokeColor

            val backgroundColor = if (position == selectedPosition) {
                ContextCompat.getColor(binding.root.context, R.color.light_primary)
            } else {
                Color.WHITE
            }
            binding.vehicleCard.setCardBackgroundColor(backgroundColor)

            binding.root.setOnClickListener {
                val previousPosition = selectedPosition
                selectedPosition = position
                notifyItemChanged(previousPosition)
                notifyItemChanged(selectedPosition)
                onVehicleSelected(vehicle)

                Log.d("VehicleAdapter", "Selected: ${vehicle.name}")
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemVehicleTypeBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        Log.d("VehicleAdapter", "ViewHolder created")
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(vehicles[position], position)
    }

    override fun getItemCount(): Int {
        Log.d("VehicleAdapter", "Item count: ${vehicles.size}")
        return vehicles.size
    }
}
