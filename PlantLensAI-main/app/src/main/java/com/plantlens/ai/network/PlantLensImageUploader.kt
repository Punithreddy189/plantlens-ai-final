package com.plantlens.ai.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.min

object PlantLensImageUploader {

    private const val MAX_DIMENSION = 1920

    fun prepareImagePayload(imageFile: File): MultipartBody.Part {
        // Pass 1: Decode bounds only (Zero heap allocation)
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(imageFile.absolutePath, boundsOptions)

        val origWidth = boundsOptions.outWidth
        val origHeight = boundsOptions.outHeight

        // Calculate power-of-2 subsampling factor
        var inSampleSize = 1
        val maxActualDim = max(origWidth, origHeight)
        while ((maxActualDim / inSampleSize) > (MAX_DIMENSION * 2)) {
            inSampleSize *= 2
        }

        // Pass 2: Allocate subsampled bitmap with full color depth
        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val sampledBitmap = BitmapFactory.decodeFile(imageFile.absolutePath, decodeOptions)
            ?: throw IllegalStateException("Could not decode image at ${imageFile.absolutePath}")

        // Exact proportional downscale
        val curWidth = sampledBitmap.width
        val curHeight = sampledBitmap.height
        val scale = min(
            min(MAX_DIMENSION.toFloat() / curWidth, MAX_DIMENSION.toFloat() / curHeight),
            1.0f
        )

        val targetWidth = (curWidth * scale).toInt()
        val targetHeight = (curHeight * scale).toInt()

        val finalBitmap = if (scale < 1.0f) {
            Bitmap.createScaledBitmap(sampledBitmap, targetWidth, targetHeight, true).also {
                if (it != sampledBitmap) sampledBitmap.recycle()
            }
        } else {
            sampledBitmap
        }

        // High-quality JPEG serialization (90% quality)
        val stream = ByteArrayOutputStream()
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        finalBitmap.recycle()

        val byteArray = stream.toByteArray()
        val requestBody = byteArray.toRequestBody("image/jpeg".toMediaTypeOrNull())

        return MultipartBody.Part.createFormData("file", imageFile.name, requestBody)
    }
}
