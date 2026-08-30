package com.plantlens.ai.adapters

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.plantlens.ai.R
import com.plantlens.ai.databinding.ItemSavedPlantBinding
import com.plantlens.ai.models.SavedPlant
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SavedPlantAdapter(
    private val clickListener: SavedPlantClickListener
) : ListAdapter<SavedPlant, SavedPlantAdapter.SavedViewHolder>(SavedDiffCallback()) {

    interface SavedPlantClickListener {
        fun onWaterClicked(savedPlant: SavedPlant)
        fun onDeleteClicked(savedPlant: SavedPlant)
        fun onItemClicked(savedPlant: SavedPlant)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SavedViewHolder {
        val binding = ItemSavedPlantBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SavedViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SavedViewHolder, position: Int) {
        val savedPlant = getItem(position)
        holder.bind(savedPlant, clickListener)
    }

    class SavedViewHolder(
        private val binding: ItemSavedPlantBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(savedPlant: SavedPlant, listener: SavedPlantClickListener) {
            val localizedName = com.plantlens.ai.utils.TranslationManager.getPlantName(savedPlant.plantId, savedPlant.plantName)
            binding.savedPlantNickname.text = savedPlant.nickname.ifEmpty { localizedName }
            binding.savedPlantName.text = if (savedPlant.nickname.isNotEmpty()) {
                "$localizedName (${savedPlant.scientificName})"
            } else {
                savedPlant.scientificName
            }

            // Load thumbnail using PlantImageMapper
            val imageRes = com.plantlens.ai.utils.PlantImageMapper.getDrawableRes(savedPlant.plantName)
            binding.savedPlantThumbnail.setImageResource(imageRes)

            // Format Saved Date (e.g. 30 Aug 2026, 09:05 PM)
            val displayDate = if (savedPlant.addedDate > 0L) savedPlant.addedDate else savedPlant.createdAt
            if (displayDate > 0L) {
                val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                binding.savedPlantDate.text = "Saved: ${dateFormat.format(Date(displayDate))}"
                binding.savedPlantDate.visibility = android.view.View.VISIBLE
            } else {
                binding.savedPlantDate.visibility = android.view.View.GONE
            }

            // Format Last Watered time
            val now = System.currentTimeMillis()
            val lastWateredStr = if (savedPlant.lastWatered > 0L) {
                val timeSpan = DateUtils.getRelativeTimeSpanString(
                    savedPlant.lastWatered,
                    now,
                    DateUtils.MINUTE_IN_MILLIS
                )
                itemView.context.getString(R.string.last_watered_label, timeSpan)
            } else {
                itemView.context.getString(R.string.last_watered_never)
            }
            binding.savedLastWateredText.text = lastWateredStr

            // Format Next Water time and dynamic color styling
            val dateFormat = java.text.DateFormat.getDateInstance(java.text.DateFormat.LONG, Locale.getDefault())
            val nextWaterDateStr = dateFormat.format(Date(savedPlant.nextWaterDate))

            if (savedPlant.nextWaterDate <= now) {
                // Watering is overdue! Renders red text
                binding.savedNextWaterText.text = itemView.context.getString(R.string.watering_overdue)
                binding.savedNextWaterText.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.error)
                )
            } else {
                // In schedule! Renders green text
                binding.savedNextWaterText.text = itemView.context.getString(R.string.next_water_label, nextWaterDateStr)
                binding.savedNextWaterText.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.primary)
                )
            }

            // Click listeners
            binding.itemWaterButton.setOnClickListener {
                listener.onWaterClicked(savedPlant)
            }

            binding.deleteSavedPlantButton.setOnClickListener {
                listener.onDeleteClicked(savedPlant)
            }

            binding.root.setOnClickListener {
                if (savedPlant.plantId.isNotBlank()) {
                    listener.onItemClicked(savedPlant)
                }
            }
        }
    }

    class SavedDiffCallback : DiffUtil.ItemCallback<SavedPlant>() {
        override fun areItemsTheSame(oldItem: SavedPlant, newItem: SavedPlant): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: SavedPlant, newItem: SavedPlant): Boolean {
            return oldItem == newItem
        }
    }
}
