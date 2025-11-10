package com.bidzi.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bidzi.app.PlacePrediction
import com.bidzi.app.R

class PlaceSearchAdapter(
    private val onItemClick: (PlacePrediction) -> Unit
) : RecyclerView.Adapter<PlaceSearchAdapter.ViewHolder>() {

    private var predictions = listOf<PlacePrediction>()

    fun updatePredictions(newPredictions: List<PlacePrediction>) {
        predictions = newPredictions
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(predictions[position])
    }

    override fun getItemCount() = predictions.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMainText: TextView = itemView.findViewById(R.id.tvMainText)
        private val tvSecondaryText: TextView = itemView.findViewById(R.id.tvSecondaryText)

        fun bind(prediction: PlacePrediction) {
            tvMainText.text = prediction.structuredFormatting?.mainText ?: prediction.description
            tvSecondaryText.text = prediction.structuredFormatting?.secondaryText ?: ""

            itemView.setOnClickListener {
                onItemClick(prediction)
            }
        }
    }
}
