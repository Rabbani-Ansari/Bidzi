package com.bidzi.app.adapter


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

import com.bidzi.app.R


class PlaceSuggestionAdapter(
    private val onPlaceSelected: (PlaceSuggestion) -> Unit,
    private val onCurrentLocationClicked: (PlaceSuggestion) -> Unit
) : ListAdapter<PlaceSuggestion, PlaceSuggestionAdapter.PlaceViewHolder>(PlaceDiffCallback()) {

    companion object {
        private const val TAG = "PlaceSuggestionAdapter"
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_place_suggestion, parent, false)
        return PlaceViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaceViewHolder, position: Int) {
        val suggestion = getItem(position)
        android.util.Log.d(TAG, "onBindViewHolder - Position: $position, Type: ${suggestion.type}, Primary: ${suggestion.primaryText}")
        holder.bind(suggestion)
    }

    inner class PlaceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.placeIcon)
        private val primaryText: TextView = itemView.findViewById(R.id.placePrimaryText)
        private val secondaryText: TextView = itemView.findViewById(R.id.placeSecondaryText)

        fun bind(suggestion: PlaceSuggestion) {
            android.util.Log.d(TAG, "bind() called for: ${suggestion.primaryText}")

            primaryText.text = suggestion.primaryText
            secondaryText.text = suggestion.secondaryText

            iconView.setImageResource(
                when (suggestion.type) {
                    PlaceType.CURRENT_LOCATION -> R.drawable.my_location
                    PlaceType.RECENT -> R.drawable.restore
                    PlaceType.SEARCH_RESULT -> R.drawable.ic_location_pin
                    PlaceType.MAP_SELECTED -> R.drawable.map
                    else -> R.drawable.ic_location_pin
                }
            )

            itemView.setOnClickListener {
                android.util.Log.d(TAG, "Item clicked! Type: ${suggestion.type}, Has coordinates: ${suggestion.latitude != null}")

                if (suggestion.type == PlaceType.CURRENT_LOCATION) {
                    android.util.Log.d(TAG, "CURRENT_LOCATION clicked - lat: ${suggestion.latitude}, lng: ${suggestion.longitude}")
                    onCurrentLocationClicked(suggestion)
                } else {
                    android.util.Log.d(TAG, "Regular place clicked: ${suggestion.primaryText}")
                    onPlaceSelected(suggestion)
                }
            }
        }
    }

    private class PlaceDiffCallback : DiffUtil.ItemCallback<PlaceSuggestion>() {
        override fun areItemsTheSame(oldItem: PlaceSuggestion, newItem: PlaceSuggestion): Boolean {
            return oldItem.placeId == newItem.placeId
        }

        override fun areContentsTheSame(oldItem: PlaceSuggestion, newItem: PlaceSuggestion): Boolean {
            return oldItem == newItem
        }
    }
}


data class PlaceSuggestion(
    val placeId: String,
    val primaryText: String,
    val secondaryText: String,
    val type: PlaceType,
    val latitude: Double? = null,
    val longitude: Double? = null
)

enum class PlaceType {
    CURRENT_LOCATION,
    RECENT,
    SEARCH_RESULT,
    MAP_SELECTED
}