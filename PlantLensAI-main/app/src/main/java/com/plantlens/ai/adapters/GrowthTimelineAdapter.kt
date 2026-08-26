package com.plantlens.ai.adapters

import android.graphics.BitmapFactory
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.plantlens.ai.R
import com.plantlens.ai.databinding.ItemGrowthTimelineBinding
import com.plantlens.ai.models.GrowthTimelineEntry
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GrowthTimelineAdapter : ListAdapter<GrowthTimelineEntry, GrowthTimelineAdapter.TimelineViewHolder>(TimelineDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimelineViewHolder {
        val binding = ItemGrowthTimelineBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TimelineViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TimelineViewHolder, position: Int) {
        val entry = getItem(position)
        holder.bind(entry)
    }

    class TimelineViewHolder(
        private val binding: ItemGrowthTimelineBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: GrowthTimelineEntry) {
            val context = itemView.context
            val dateFormat = java.text.DateFormat.getDateTimeInstance(
                java.text.DateFormat.LONG,
                java.text.DateFormat.SHORT,
                java.util.Locale.getDefault()
            )
            binding.tvTimelineDate.text = dateFormat.format(Date(entry.timestamp))

            val numberFormat = java.text.NumberFormat.getInstance(java.util.Locale.getDefault())
            val heightFormat = java.text.NumberFormat.getInstance(java.util.Locale.getDefault()).apply {
                maximumFractionDigits = 1
                minimumFractionDigits = 1
            }
            binding.tvTimelineMetrics.text = context.getString(
                R.string.timeline_metrics_format,
                heightFormat.format(entry.heightCm),
                numberFormat.format(entry.healthScore)
            )
            
            // Translate the initial registration message if matches exactly
            val rawNotes = entry.notes.ifEmpty { context.getString(R.string.no_notes_recorded) }
            val cleanNotes = if (rawNotes.startsWith("Initial classification & registration.")) {
                val suffix = rawNotes.substringAfter("Initial classification & registration.", "").trim()
                val mainMsg = com.plantlens.ai.utils.TranslationManager.translate("timeline_status", "Initial classification & registration.")
                if (suffix.isNotEmpty()) "$mainMsg $suffix" else mainMsg
            } else {
                com.plantlens.ai.utils.TranslationManager.translate("timeline_status", rawNotes)
            }
            binding.tvTimelineNotes.text = cleanNotes

            binding.tvTimelineMethod.text = context.getString(
                R.string.timeline_assessment_format,
                entry.assessmentMethod
            )

            // Load progressive photo
            if (entry.imagePath.isNotEmpty()) {
                val file = File(entry.imagePath)
                if (file.exists()) {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    if (bitmap != null) {
                        binding.ivTimelinePhoto.setImageBitmap(bitmap)
                        binding.cardTimelinePhoto.visibility = View.VISIBLE
                    } else {
                        binding.cardTimelinePhoto.visibility = View.GONE
                    }
                } else {
                    // Try parsing as URI
                    try {
                        binding.ivTimelinePhoto.setImageURI(Uri.parse(entry.imagePath))
                        binding.cardTimelinePhoto.visibility = View.VISIBLE
                    } catch (e: Exception) {
                        binding.cardTimelinePhoto.visibility = View.GONE
                    }
                }
            } else {
                binding.cardTimelinePhoto.visibility = View.GONE
            }
        }
    }

    class TimelineDiffCallback : DiffUtil.ItemCallback<GrowthTimelineEntry>() {
        override fun areItemsTheSame(oldItem: GrowthTimelineEntry, newItem: GrowthTimelineEntry): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: GrowthTimelineEntry, newItem: GrowthTimelineEntry): Boolean {
            return oldItem == newItem
        }
    }
}
