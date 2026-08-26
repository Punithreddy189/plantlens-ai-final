package com.plantlens.ai.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.plantlens.ai.R
import com.plantlens.ai.databinding.ItemPlantBinding
import com.plantlens.ai.models.Plant

class PlantAdapter(
    private val clickListener: PlantClickListener
) : ListAdapter<Plant, PlantAdapter.PlantViewHolder>(PlantDiffCallback()) {

    interface PlantClickListener {
        fun onPlantClicked(plant: Plant)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlantViewHolder {
        val binding = ItemPlantBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PlantViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlantViewHolder, position: Int) {
        val plant = getItem(position)
        holder.bind(plant, clickListener)
    }

    class PlantViewHolder(
        private val binding: ItemPlantBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(plant: Plant, listener: PlantClickListener) {
            val context = itemView.context
            val localizedName = com.plantlens.ai.utils.TranslationManager.getPlantName(plant)
            binding.itemPlantName.text = localizedName
            binding.itemPlantScientific.text = plant.scientificName
            
            val localizedCategory = when (plant.category.lowercase()) {
                "indoor" -> context.getString(R.string.indoor_plants_chip)
                "outdoor" -> context.getString(R.string.outdoor_plants_chip)
                "succulent", "succulents" -> context.getString(R.string.succulents_chip)
                "medicinal" -> context.getString(R.string.medicinal_plants_chip)
                "flowering" -> context.getString(R.string.flowering_plants_chip)
                else -> plant.category
            }
            binding.itemPlantCategory.text = localizedCategory

            // Bind static plant drawable
            val imageRes = com.plantlens.ai.utils.PlantImageMapper.getDrawableRes(plant.name)
            binding.itemPlantImage.setImageResource(imageRes)
            
            // Clear color tint so images show in full color
            binding.itemPlantImage.imageTintList = null

            binding.root.setOnClickListener {
                listener.onPlantClicked(plant)
            }
        }
    }

    class PlantDiffCallback : DiffUtil.ItemCallback<Plant>() {
        override fun areItemsTheSame(oldItem: Plant, newItem: Plant): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Plant, newItem: Plant): Boolean {
            return oldItem == newItem
        }
    }
}
