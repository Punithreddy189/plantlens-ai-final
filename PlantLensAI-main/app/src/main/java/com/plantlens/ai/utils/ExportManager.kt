package com.plantlens.ai.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.util.Log
import com.plantlens.ai.interfaces.PlantRepository
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportManager @Inject constructor(
    private val plantRepository: PlantRepository
) {
    private val TAG = "ExportManager"

    suspend fun exportToCSV(context: Context): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }
        val file = File(downloadsDir, "PlantLens_Export_${System.currentTimeMillis()}.csv")
        val writer = FileWriter(file)
        
        val sharedPref = context.getSharedPreferences("plantlens_analytics", Context.MODE_PRIVATE)
        val totalScans = sharedPref.getInt("analytics_total_scans", 0)
        val successScans = sharedPref.getInt("analytics_success_scans", 0)
        val rejectedScans = sharedPref.getInt("analytics_rejected_scans", 0)
        val avgConf = sharedPref.getFloat("analytics_avg_confidence", 0.0f) * 100
        val avgTime = sharedPref.getLong("analytics_avg_time", 0L)
        
        writer.append("--- USER ANALYTICS OVERVIEW ---\n")
        writer.append("Metric,Value\n")
        writer.append("Total Scans,${totalScans}\n")
        writer.append("Success Scans,${successScans}\n")
        writer.append("Rejected Scans,${rejectedScans}\n")
        writer.append("Average Confidence,${String.format(Locale.US, "%.1f%%", avgConf)}\n")
        writer.append("Average Analysis Time,${avgTime} ms\n\n")

        writer.append("--- SAVED PLANTS IN MY GARDEN ---\n")
        writer.append("Nickname,Scientific Name,Added Date,Last Watered,Next Watering,Frequency (days)\n")
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        
        try {
            val savedPlants = plantRepository.getSavedPlants().first()
            for (plant in savedPlants) {
                val addedStr = sdf.format(Date(plant.addedDate))
                val lastWateredStr = if (plant.lastWatered > 0L) sdf.format(Date(plant.lastWatered)) else "Never"
                val nextWaterStr = sdf.format(Date(plant.nextWaterDate))
                writer.append("\"${plant.nickname}\",\"${plant.scientificName}\",${addedStr},${lastWateredStr},${nextWaterStr},${plant.wateringFrequency}\n")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching saved plants for CSV export: ${e.message}")
        }
        writer.append("\n")

        writer.append("--- IDENTIFICATION SCAN HISTORY ---\n")
        writer.append("Plant Name,Scientific Name,Timestamp,Confidence,Family,Genus,Latitude,Longitude\n")
        try {
            val history = plantRepository.getLocalScanHistory().first()
            for (record in history) {
                val dateStr = sdf.format(Date(record.timestamp))
                val confStr = String.format(Locale.US, "%.1f%%", record.confidence * 100)
                writer.append("\"${record.plantName}\",\"${record.scientificName}\",${dateStr},${confStr},\"${record.family}\",\"${record.genus}\",${record.latitude},${record.longitude}\n")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching scan history for CSV export: ${e.message}")
        }

        writer.flush()
        writer.close()
        Log.d(TAG, "CSV exported successfully: ${file.absolutePath}")
        return file
    }

    suspend fun exportToPDF(context: Context): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }
        val file = File(downloadsDir, "PlantLens_Export_${System.currentTimeMillis()}.pdf")
        
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas
        
        val paint = Paint()
        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 12f
            isAntiAlias = true
        }
        
        val headerPaint = Paint().apply {
            color = Color.parseColor("#059669")
            textSize = 20f
            style = Paint.Style.FILL
            isFakeBoldText = true
            isAntiAlias = true
        }

        val subheaderPaint = Paint().apply {
            color = Color.parseColor("#0F766E")
            textSize = 14f
            style = Paint.Style.FILL
            isFakeBoldText = true
            isAntiAlias = true
        }

        var y = 40f
        
        canvas.drawText("PlantLens AI - Premium Care Report", 20f, y, headerPaint)
        y += 20f
        
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        canvas.drawText("Generated on: ${sdf.format(Date())}", 20f, y, textPaint)
        y += 30f

        canvas.drawText("User Analytics Overview", 20f, y, subheaderPaint)
        y += 20f
        
        val sharedPref = context.getSharedPreferences("plantlens_analytics", Context.MODE_PRIVATE)
        val totalScans = sharedPref.getInt("analytics_total_scans", 0)
        val successScans = sharedPref.getInt("analytics_success_scans", 0)
        val rejectedScans = sharedPref.getInt("analytics_rejected_scans", 0)
        val avgConf = sharedPref.getFloat("analytics_avg_confidence", 0.0f) * 100
        val avgTime = sharedPref.getLong("analytics_avg_time", 0L)

        canvas.drawText("• Total Scans: $totalScans", 30f, y, textPaint); y += 16f
        canvas.drawText("• Success Scans: $successScans", 30f, y, textPaint); y += 16f
        canvas.drawText("• Rejected Scans: $rejectedScans", 30f, y, textPaint); y += 16f
        canvas.drawText("• Avg Confidence: ${String.format(Locale.US, "%.1f%%", avgConf)}", 30f, y, textPaint); y += 16f
        canvas.drawText("• Avg Analysis Latency: $avgTime ms", 30f, y, textPaint); y += 24f

        canvas.drawText("My Garden Plants", 20f, y, subheaderPaint)
        y += 20f
        
        try {
            val savedPlants = plantRepository.getSavedPlants().first()
            if (savedPlants.isEmpty()) {
                canvas.drawText("No saved plants in your garden.", 30f, y, textPaint)
                y += 20f
            } else {
                for (plant in savedPlants.take(10)) {
                    val lastWateredStr = if (plant.lastWatered > 0L) sdf.format(Date(plant.lastWatered)) else "Never"
                    canvas.drawText("${plant.nickname} (${plant.scientificName}) | Last Watered: $lastWateredStr", 30f, y, textPaint)
                    y += 16f
                }
                if (savedPlants.size > 10) {
                    canvas.drawText("... and ${savedPlants.size - 10} more plants.", 30f, y, textPaint)
                    y += 16f
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error rendering saved plants in PDF: ${e.message}")
        }
        y += 16f

        canvas.drawText("Recent Scan History", 20f, y, subheaderPaint)
        y += 20f
        
        try {
            val history = plantRepository.getLocalScanHistory().first()
            if (history.isEmpty()) {
                canvas.drawText("No plant scans recorded.", 30f, y, textPaint)
                y += 20f
            } else {
                for (record in history.take(10)) {
                    val dateStr = sdf.format(Date(record.timestamp))
                    val confStr = String.format(Locale.US, "%.1f%%", record.confidence * 100)
                    canvas.drawText("${record.plantName} - $confStr | Scanned: $dateStr", 30f, y, textPaint)
                    y += 16f
                }
                if (history.size > 10) {
                    canvas.drawText("... and ${history.size - 10} more scan logs.", 30f, y, textPaint)
                    y += 16f
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error rendering scan history in PDF: ${e.message}")
        }

        pdfDocument.finishPage(page)
        
        val fos = FileOutputStream(file)
        pdfDocument.writeTo(fos)
        pdfDocument.close()
        fos.close()
        Log.d(TAG, "PDF exported successfully: ${file.absolutePath}")
        return file
    }
}
