package com.plantlens.ai.fragments

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.plantlens.ai.R
import com.plantlens.ai.databinding.FragmentScannerBinding
import com.plantlens.ai.permissions.PermissionManager
import com.plantlens.ai.utils.QualityConfig
import com.plantlens.ai.utils.Resource
import com.plantlens.ai.utils.TFLiteClassifier
import com.plantlens.ai.viewmodels.ScannerViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.InputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject

@AndroidEntryPoint
class ScannerFragment : Fragment() {

    private val tag = "ScannerFragment"
    private var _binding: FragmentScannerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ScannerViewModel by viewModels()
    private lateinit var permissionManager: PermissionManager
    
    @Inject lateinit var classifier: TFLiteClassifier

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private var isCameraBound = false

    private var latitude = 28.61  // Default New Delhi
    private var longitude = 77.20 // Default New Delhi

    private val selectImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            processGalleryImage(it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScannerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraExecutor = Executors.newSingleThreadExecutor()
        permissionManager = PermissionManager(this)

        checkPermissionsAndStartCamera()
        setupListeners()
        observeViewModel()
        fetchCurrentLocation()

        if (!classifier.isDetectorAvailable()) {
            Toast.makeText(requireContext(), "Plant detector model unavailable. Using standard crop mode.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        setupDebugOverlayVisibility()
    }

    private fun setupDebugOverlayVisibility() {
        val sharedPref = requireContext().getSharedPreferences("plantlens_settings", android.content.Context.MODE_PRIVATE)
        val showOverlay = com.plantlens.ai.BuildConfig.DEBUG && sharedPref.getBoolean("pref_show_debug_overlay", true)
        binding.debugOverlayCard.visibility = if (showOverlay) View.VISIBLE else View.GONE
    }

    private fun checkPermissionsAndStartCamera() {
        permissionManager.requestCameraAndNotificationPermissions(object : PermissionManager.PermissionCallback {
            override fun onPermissionGranted() {
                startCameraX()
            }

            override fun onPermissionDenied() {
                Toast.makeText(requireContext(), getString(R.string.camera_permission_required), Toast.LENGTH_LONG).show()
                activateEmulatorFallback()
            }
        })
    }

    private fun startCameraX() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = binding.cameraPreview.surfaceProvider
                }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
                
                isCameraBound = true
                Log.d(tag, "CameraX bound successfully to lifecycle.")

            } catch (e: Exception) {
                Log.e(tag, "CameraX binding failed: ${e.message}.")
                activateEmulatorFallback()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun activateEmulatorFallback() {
        isCameraBound = false
        binding.dimmingMask.visibility = View.GONE
        binding.targetFrame.visibility = View.GONE
    }

    private fun setupListeners() {
        binding.captureButton.setOnClickListener {
            if (isCameraBound) {
                takePhotoAndProcess()
            } else {
                Toast.makeText(requireContext(), getString(R.string.camera_unavailable), Toast.LENGTH_LONG).show()
            }
        }

        binding.galleryButton.setOnClickListener {
            selectImageLauncher.launch("image/*")
        }
    }

    private fun rotateBitmapIfRequired(bitmap: Bitmap, path: String): Bitmap {
        return try {
            val exif = android.media.ExifInterface(path)
            val orientation = exif.getAttributeInt(
                android.media.ExifInterface.TAG_ORIENTATION,
                android.media.ExifInterface.ORIENTATION_NORMAL
            )
            val matrix = android.graphics.Matrix()
            when (orientation) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                else -> return bitmap
            }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            bitmap
        }
    }

    private fun rotateBitmapIfRequired(bitmap: Bitmap, uri: android.net.Uri): Bitmap {
        return try {
            requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = android.media.ExifInterface(inputStream)
                val orientation = exif.getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION,
                    android.media.ExifInterface.ORIENTATION_NORMAL
                )
                val matrix = android.graphics.Matrix()
                when (orientation) {
                    android.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    android.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    android.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    else -> return bitmap
                }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } ?: bitmap
        } catch (e: Exception) {
            bitmap
        }
    }

    private fun takePhotoAndProcess() {
        val imageCapture = imageCapture ?: return

        val photoFile = File(
            requireContext().cacheDir,
            "captured_leaf_${System.currentTimeMillis()}.jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        binding.scanLoadingOverlay.visibility = View.VISIBLE
        binding.loadingMessage.text = getString(R.string.capturing_leaf_photo)

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val rawBitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                    val bitmap = if (rawBitmap != null) rotateBitmapIfRequired(rawBitmap, photoFile.absolutePath) else null
                    if (bitmap != null) {
                        if (analyzeImageQuality(bitmap)) {
                            processPipelineImage(bitmap)
                        } else {
                            binding.scanLoadingOverlay.visibility = View.GONE
                        }
                    } else {
                        binding.scanLoadingOverlay.visibility = View.GONE
                        Toast.makeText(requireContext(), getString(R.string.failed_read_image), Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    binding.scanLoadingOverlay.visibility = View.GONE
                    Log.e(tag, "Photo capture failed: ${exception.message}")
                    Toast.makeText(requireContext(), getString(R.string.photo_capture_failed, exception.message), Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun processGalleryImage(uri: android.net.Uri) {
        binding.scanLoadingOverlay.visibility = View.VISIBLE
        binding.loadingMessage.text = getString(R.string.analyzing_gallery_image)
        
        try {
            val inputStream: InputStream? = requireContext().contentResolver.openInputStream(uri)
            val rawBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            val bitmap = if (rawBitmap != null) rotateBitmapIfRequired(rawBitmap, uri) else null
            
            if (bitmap != null) {
                if (analyzeImageQuality(bitmap)) {
                    processPipelineImage(bitmap)
                } else {
                    binding.scanLoadingOverlay.visibility = View.GONE
                }
            } else {
                binding.scanLoadingOverlay.visibility = View.GONE
                Toast.makeText(requireContext(), getString(R.string.could_not_parse_image), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            binding.scanLoadingOverlay.visibility = View.GONE
            Toast.makeText(requireContext(), getString(R.string.failed_read_gallery_image, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun processPipelineImage(bitmap: Bitmap) {
        val detStartTime = System.currentTimeMillis()
        val detections = classifier.detectObjects(bitmap)
        val detectionTime = System.currentTimeMillis() - detStartTime

        var cropRect: Rect? = null
        var cropMode = "CENTER_CROP"
        var detectionConfidence = 0.0f

        val leafDet = detections.firstOrNull { it.label == "leaf" }
        if (leafDet != null) {
            cropRect = leafDet.boundingBox
            cropMode = "LEAF"
            detectionConfidence = leafDet.confidence
        }
        else if (detections.isNotEmpty()) {
            val bestDet = detections.maxByOrNull { it.confidence }!!
            cropRect = bestDet.boundingBox
            cropMode = if (bestDet.label == "plant") "PLANT" else "LEAF"
            detectionConfidence = bestDet.confidence
        }
        else {
            val hsvLeaf = detectLeafBoundingBox(bitmap)
            if (hsvLeaf != null) {
                cropRect = hsvLeaf
                cropMode = "LEAF"
            } else {
                val hsvPlant = detectPlantBoundingBox(bitmap)
                if (hsvPlant != null) {
                    cropRect = hsvPlant
                    cropMode = "PLANT"
                } else {
                    val centerCropWidth = (bitmap.width * 0.70).toInt()
                    val centerCropHeight = (bitmap.height * 0.70).toInt()
                    val startX = (bitmap.width - centerCropWidth) / 2
                    val startY = (bitmap.height - centerCropHeight) / 2
                    cropRect = Rect(startX, startY, startX + centerCropWidth, startY + centerCropHeight)
                    cropMode = "CENTER_CROP"
                }
            }
        }

        val finalCropRect = cropRect
        val isLeaf = cropMode == "LEAF"

        // 1. Plant Validation Layer (runs before sending to TFLite classification)
        val validationResult = classifier.validatePlantImage(bitmap, finalCropRect)
        
        if (validationResult.isRejected) {
            viewModel.trackRejectedScan()
            binding.scanLoadingOverlay.visibility = View.GONE
            Toast.makeText(
                requireContext(), 
                "Plant not clearly visible. Please move closer to the plant.", 
                Toast.LENGTH_LONG
            ).show()
            return
        }

        showCropConfirmationDialog(bitmap, finalCropRect, isLeaf, cropMode, detectionConfidence, validationResult.score) { confirmedBitmap ->
            binding.scanLoadingOverlay.visibility = View.VISIBLE
            binding.loadingMessage.text = getString(R.string.uploading_to_ai_pipeline)
            fetchCurrentLocation()
            val penalty = if (isLeaf) 1.0f else 0.9f
            
            viewModel.processCapturedImage(
                confirmedBitmap, 
                latitude, 
                longitude, 
                penalty, 
                cropMode, 
                detectionTime,
                validationResult.score
            )
        }
    }

    private fun analyzeImageQuality(bitmap: Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height

        var totalLuminance = 0.0
        var sampleCount = 0

        val stepX = (width / 50).coerceAtLeast(1)
        val stepY = (height / 50).coerceAtLeast(1)

        for (x in 0 until width step stepX) {
            for (y in 0 until height step stepY) {
                val pixel = bitmap.getPixel(x, y)
                val r = android.graphics.Color.red(pixel)
                val g = android.graphics.Color.green(pixel)
                val b = android.graphics.Color.blue(pixel)

                val lum = 0.299 * r + 0.587 * g + 0.114 * b
                totalLuminance += lum
                sampleCount++
            }
        }

        val avgBrightness = if (sampleCount > 0) totalLuminance / sampleCount else 0.0
        val blurScore = calculateLaplacianVariance(bitmap)

        var status = "PASSED"
        var errorMessage = ""

        if (width < QualityConfig.MIN_RESOLUTION || height < QualityConfig.MIN_RESOLUTION) {
            status = "FAILED"
            errorMessage = "Resolution too low"
        }
        else if (avgBrightness < QualityConfig.MIN_LUMINANCE) {
            status = "FAILED"
            errorMessage = "Image too dark"
        }
        else if (avgBrightness > QualityConfig.MAX_LUMINANCE) {
            status = "FAILED"
            errorMessage = "Image too bright"
        }
        else if (blurScore < QualityConfig.LAPLACIAN_THRESHOLD) {
            status = "FAILED"
            errorMessage = "Image too blurry"
        }
        else {
            val nonPlantObject = detectNonPlantObject(bitmap, blurScore)
            if (nonPlantObject != null) {
                status = "FAILED"
                errorMessage = "Non-Plant Detected: $nonPlantObject"
            }
        }

        Log.i("PlantLensQuality", "PlantLens Quality Check")
        Log.d("PlantLensQuality", "Resolution: ${width}x${height}")
        Log.d("PlantLensQuality", "Brightness: ${String.format(java.util.Locale.US, "%.1f", avgBrightness)}")
        Log.d("PlantLensQuality", "BlurScore: ${String.format(java.util.Locale.US, "%.1f", blurScore)}")
        if (status == "PASSED") {
            Log.i("PlantLensQuality", "Status: PASSED")
        } else {
            Log.w("PlantLensQuality", "Status: FAILED - $errorMessage")
        }

        val showOverlay = com.plantlens.ai.BuildConfig.DEBUG && 
                requireContext().getSharedPreferences("plantlens_settings", android.content.Context.MODE_PRIVATE)
                    .getBoolean("pref_show_debug_overlay", true)

        if (showOverlay) {
            binding.debugOverlayCard.visibility = View.VISIBLE
            val successColor = ContextCompat.getColor(requireContext(), R.color.success)
            val warningColor = ContextCompat.getColor(requireContext(), R.color.warning)
            val errorColor = ContextCompat.getColor(requireContext(), R.color.error)
            val whiteColor = ContextCompat.getColor(requireContext(), R.color.white)

            binding.debugResolutionText.text = "Resolution: ${width} x ${height}"
            binding.debugResolutionText.setTextColor(
                if (width < QualityConfig.MIN_RESOLUTION || height < QualityConfig.MIN_RESOLUTION) errorColor else whiteColor
            )

            binding.debugBrightnessText.text = String.format(java.util.Locale.US, "Brightness: %.1f", avgBrightness)
            binding.debugBrightnessText.setTextColor(
                when {
                    avgBrightness < QualityConfig.MIN_LUMINANCE || avgBrightness > QualityConfig.MAX_LUMINANCE -> errorColor
                    avgBrightness in QualityConfig.MIN_LUMINANCE..50.0 || avgBrightness in 230.0..QualityConfig.MAX_LUMINANCE -> warningColor
                    else -> successColor
                }
            )

            binding.debugBlurText.text = String.format(java.util.Locale.US, "Blur Score: %.1f", blurScore)
            binding.debugBlurText.setTextColor(
                when {
                    blurScore < QualityConfig.LAPLACIAN_THRESHOLD -> errorColor
                    blurScore in QualityConfig.LAPLACIAN_THRESHOLD..5.0 -> warningColor
                    else -> successColor
                }
            )

            binding.debugStatusText.text = "Status: $status"
            binding.debugStatusText.setTextColor(
                if (status == "PASSED") successColor else errorColor
            )
        } else {
            binding.debugOverlayCard.visibility = View.GONE
        }

        if (status == "FAILED") {
            if (errorMessage.startsWith("Non-Plant Detected")) {
                val detectedObject = errorMessage.substringAfter(": ")
                showNonPlantDetectedDialog(detectedObject)
            } else {
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
            }
            return false
        }

        return true
    }

    private fun detectNonPlantObject(bitmap: Bitmap, blurScore: Double): String? {
        val width = bitmap.width
        val height = bitmap.height

        var greenCount = 0
        var grayscaleCount = 0
        var skinWarmCount = 0
        var totalCount = 0

        val stepX = (width / 40).coerceAtLeast(1)
        val stepY = (height / 40).coerceAtLeast(1)

        val hsv = FloatArray(3)
        for (x in 0 until width step stepX) {
            for (y in 0 until height step stepY) {
                val pixel = bitmap.getPixel(x, y)
                android.graphics.Color.colorToHSV(pixel, hsv)
                val hue = hsv[0]
                val sat = hsv[1]
                val value = hsv[2]

                when {
                    hue in 65f..170f && sat > 0.15f && value > 0.15f -> greenCount++
                    sat < 0.18f -> grayscaleCount++
                    (hue in 5f..38f || hue in 340f..360f) && sat in 0.15f..0.7f && value > 0.2f -> skinWarmCount++
                }
                totalCount++
            }
        }

        if (totalCount == 0) return null

        val greenRatio = greenCount.toFloat() / totalCount
        val grayscaleRatio = grayscaleCount.toFloat() / totalCount
        val skinWarmRatio = skinWarmCount.toFloat() / totalCount

        if (blurScore < 2.0 && grayscaleRatio > 0.5) {
            return "Desk"
        }
        if (blurScore < 1.0) {
            return "Desk"
        }

        if (grayscaleRatio > 0.65) {
            return if (blurScore > 10.0) {
                "Keyboard"
            } else {
                "Laptop"
            }
        }

        if (skinWarmRatio > 0.45 && greenRatio < 0.1) {
            return if (blurScore > 12.0) {
                "Pet"
            } else {
                "Human"
            }
        }

        if (greenRatio < 0.05 && skinWarmRatio < 0.1) {
            return "Monitor"
        }

        return null
    }

    private fun showNonPlantDetectedDialog(detectedObject: String) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_non_plant, null)
        val objectTextView = dialogView.findViewById<android.widget.TextView>(R.id.detectedObjectText)
        objectTextView.text = "• $detectedObject"

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialogView.findViewById<android.widget.Button>(R.id.btnScanAgain).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun detectLeafBoundingBox(bitmap: Bitmap): Rect? {
        val width = bitmap.width
        val height = bitmap.height

        var minX = width
        var maxX = 0
        var minY = height
        var maxY = 0
        var count = 0

        val stepX = (width / 20).coerceAtLeast(1)
        val stepY = (height / 20).coerceAtLeast(1)
        val hsv = FloatArray(3)

        for (x in 0 until width step stepX) {
            for (y in 0 until height step stepY) {
                val pixel = bitmap.getPixel(x, y)
                android.graphics.Color.colorToHSV(pixel, hsv)
                val hue = hsv[0]
                val sat = hsv[1]
                val value = hsv[2]

                if ((hue in 40f..170f && sat > 0.15f && value > 0.15f) || 
                    (hue in 15f..40f && sat > 0.25f && value > 0.15f)) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                    count++
                }
            }
        }

        if (count < 15) return null

        val padX = ((maxX - minX) * 0.1).toInt()
        val padY = ((maxY - minY) * 0.1).toInt()

        val startX = (minX - padX).coerceAtLeast(0)
        val startY = (minY - padY).coerceAtLeast(0)
        val endX = (maxX + padX).coerceAtMost(width)
        val endY = (maxY + padY).coerceAtMost(height)

        val cropWidth = endX - startX
        val cropHeight = endY - startY

        if (cropWidth <= 50 || cropHeight <= 50) return null

        return Rect(startX, startY, endX, endY)
    }

    private fun detectPlantBoundingBox(bitmap: Bitmap): Rect? {
        val width = bitmap.width
        val height = bitmap.height

        var minX = width
        var maxX = 0
        var minY = height
        var maxY = 0
        var count = 0

        val stepX = (width / 20).coerceAtLeast(1)
        val stepY = (height / 20).coerceAtLeast(1)
        val hsv = FloatArray(3)

        for (x in 0 until width step stepX) {
            for (y in 0 until height step stepY) {
                val pixel = bitmap.getPixel(x, y)
                android.graphics.Color.colorToHSV(pixel, hsv)
                val hue = hsv[0]
                val sat = hsv[1]
                val value = hsv[2]

                if ((hue in 35f..170f && sat > 0.12f && value > 0.12f) || 
                    (hue in 0f..35f && sat > 0.15f && value > 0.12f) || 
                    (hue in 270f..360f && sat > 0.15f && value > 0.12f)) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                    count++
                }
            }
        }

        if (count >= 10) {
            val padX = ((maxX - minX) * 0.08).toInt()
            val padY = ((maxY - minY) * 0.08).toInt()

            val startX = (minX - padX).coerceAtLeast(0)
            val startY = (minY - padY).coerceAtLeast(0)
            val endX = (maxX + padX).coerceAtMost(width)
            val endY = (maxY + padY).coerceAtMost(height)

            val cropWidth = endX - startX
            val cropHeight = endY - startY

            if (cropWidth > 50 && cropHeight > 50) {
                return Rect(startX, startY, endX, endY)
            }
        }

        return null
    }

    private fun drawBoundingBox(originalBitmap: Bitmap, cropRect: Rect): Bitmap {
        val mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(mutableBitmap)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.GREEN
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = (originalBitmap.width / 150f).coerceAtLeast(6f)
        }
        canvas.drawRect(cropRect, paint)
        return mutableBitmap
    }

    private fun showCropConfirmationDialog(
        originalBitmap: Bitmap, 
        rectToDraw: Rect, 
        isLeaf: Boolean, 
        cropMode: String,
        detectionConfidence: Float,
        validationScore: Int,
        onConfirm: (Bitmap) -> Unit
    ) {
        binding.scanLoadingOverlay.visibility = View.GONE

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_crop_confirm, null)
        
        val titleText = dialogView.findViewById<android.widget.TextView>(R.id.dialogTitle)
        val subtitleText = dialogView.findViewById<android.widget.TextView>(R.id.dialogSubtitle)
        val previewImageView = dialogView.findViewById<android.widget.ImageView>(R.id.cropPreviewImage)
        val logTextView = dialogView.findViewById<android.widget.TextView>(R.id.detectionLogText)

        val previewBitmap = drawBoundingBox(originalBitmap, rectToDraw)
        previewImageView.setImageBitmap(previewBitmap)

        val cropQuality = when {
            validationScore > 80 -> "Excellent"
            validationScore in 60..80 -> "Good"
            validationScore in 30..60 -> "Fair"
            else -> "Poor"
        }

        when (cropMode) {
            "LEAF" -> {
                titleText.text = "🌿 Leaf Detected"
                subtitleText.text = "Close-up leaf spotted. Auto-cropped to maximize classification accuracy."
                subtitleText.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary))
            }
            "PLANT" -> {
                titleText.text = "🌱 Whole Plant Detected"
                subtitleText.text = "Whole plant spotted. Bounding box applied."
                subtitleText.setTextColor(ContextCompat.getColor(requireContext(), R.color.warning))
            }
            else -> {
                titleText.text = "📐 Center Crop Mode"
                subtitleText.text = "No leaf or plant detected. Standard center crop applied."
                subtitleText.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary_light))
            }
        }

        val cropW = rectToDraw.width()
        val cropH = rectToDraw.height()
        val confText = if (detectionConfidence > 0.0f) "${(detectionConfidence * 100).toInt()}%" else "N/A"

        logTextView.text = buildString {
            append("Pipeline Crop Mode: $cropMode\n")
            append("Crop Quality Rating: $cropQuality (Score: $validationScore)\n")
            append("Detection Confidence: $confText\n")
            append("Crop Dimensions: ${cropW} x ${cropH}\n")
            append("Original Size: ${originalBitmap.width} x ${originalBitmap.height}")
        }

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialogView.findViewById<android.widget.Button>(R.id.btnCancelCrop).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<android.widget.Button>(R.id.btnConfirmCrop).setOnClickListener {
            dialog.dismiss()
            val croppedBitmap = Bitmap.createBitmap(
                originalBitmap, 
                rectToDraw.left, 
                rectToDraw.top, 
                rectToDraw.width(), 
                rectToDraw.height()
            )
            onConfirm(croppedBitmap)
        }

        dialog.show()
    }

    private fun calculateLaplacianVariance(bitmap: Bitmap): Double {
        val width = bitmap.width
        val height = bitmap.height

        val cropSize = 300
        val startX = ((width - cropSize) / 2).coerceAtLeast(0)
        val startY = ((height - cropSize) / 2).coerceAtLeast(0)
        val endX = (startX + cropSize).coerceAtMost(width)
        val endY = (startY + cropSize).coerceAtMost(height)

        val actualWidth = endX - startX
        val actualHeight = endY - startY

        if (actualWidth < 3 || actualHeight < 3) return 0.0

        val pixels = IntArray(actualWidth * actualHeight)
        bitmap.getPixels(pixels, 0, actualWidth, startX, startY, actualWidth, actualHeight)

        val gray = DoubleArray(actualWidth * actualHeight)
        for (i in 0 until actualWidth * actualHeight) {
            val color = pixels[i]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            gray[i] = 0.299 * r + 0.587 * g + 0.114 * b
        }

        val laplacianResponse = ArrayList<Double>()

        for (y in 1 until actualHeight - 1) {
            for (x in 1 until actualWidth - 1) {
                val center = gray[y * actualWidth + x]
                val left = gray[y * actualWidth + (x - 1)]
                val right = gray[y * actualWidth + (x + 1)]
                val top = gray[(y - 1) * actualWidth + x]
                val bottom = gray[(y + 1) * actualWidth + x]

                val laplacian = left + right + top + bottom - 4.0 * center
                laplacianResponse.add(laplacian)
            }
        }

        if (laplacianResponse.isEmpty()) return 0.0

        var sum = 0.0
        for (valResponse in laplacianResponse) {
            sum += valResponse
        }
        val mean = sum / laplacianResponse.size

        var varianceSum = 0.0
        for (valResponse in laplacianResponse) {
            val diff = valResponse - mean
            varianceSum += diff * diff
        }

        return varianceSum / laplacianResponse.size
    }

    private fun compressAndResizeBitmap(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val maxDimension = 1024

        val resizedBitmap = if (width > maxDimension || height > maxDimension) {
            val ratio = width.toFloat() / height.toFloat()
            val newWidth: Int
            val newHeight: Int
            if (width > height) {
                newWidth = maxDimension
                newHeight = (maxDimension / ratio).toInt()
            } else {
                newHeight = maxDimension
                newWidth = (maxDimension * ratio).toInt()
            }
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            bitmap
        }

        val outputStream = java.io.ByteArrayOutputStream()
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val byteArray = outputStream.toByteArray()
        return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
    }

    private fun fetchCurrentLocation() {
        try {
            val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            
            val hasFine = ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            
            if (hasFine || hasCoarse) {
                val gpsLoc = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                val netLoc = locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                
                val location = gpsLoc ?: netLoc
                location?.let {
                    latitude = it.latitude
                    longitude = it.longitude
                    Log.i(tag, "Coordinates retrieved: Lat: $latitude, Lng: $longitude")
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Coordinates retrieval failed: ${e.message}")
        }
    }

    private fun observeViewModel() {
        viewModel.scanState.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    binding.scanLoadingOverlay.visibility = View.VISIBLE
                }
                is Resource.Success -> {
                    binding.scanLoadingOverlay.visibility = View.GONE
                    
                    val bundle = Bundle().apply {
                        putSerializable("matched_plant", resource.data.matchedPlant)
                        putFloat("confidence_score", resource.data.confidence)
                        
                        val diseaseResult = com.plantlens.ai.models.DiseaseAnalysisResult(
                            diseaseName = resource.data.diseaseName,
                            confidence = resource.data.diseaseConfidence,
                            severity = resource.data.diseaseSeverity,
                            treatment = resource.data.treatmentRecommendation,
                            healthScore = resource.data.healthScore,
                            healthStatus = resource.data.healthStatus,
                            observations = resource.data.observations,
                            recommendations = resource.data.recommendations,
                            assessmentMethod = resource.data.assessmentMethod,
                            soilType = resource.data.soilType,
                            soilPh = resource.data.soilPh,
                            soilDrainage = resource.data.soilDrainage,
                            soilRecommendation = resource.data.soilRecommendation,
                            confidenceReason = resource.data.confidenceReason
                        )
                        putSerializable("disease_analysis_result", diseaseResult)

                        putString("disease_name", resource.data.diseaseName)
                        putFloat("disease_confidence", resource.data.diseaseConfidence)
                        putString("disease_severity", resource.data.diseaseSeverity)
                        putString("treatment_recommendation", resource.data.treatmentRecommendation)
                        putInt("health_score", resource.data.healthScore)
                        putString("health_status", resource.data.healthStatus)
                        putString("observations", resource.data.observations)
                        putString("recommendations", resource.data.recommendations)
                        putString("assessment_method", resource.data.assessmentMethod)
                        
                        putString("soil_type", resource.data.soilType)
                        putString("soil_ph", resource.data.soilPh)
                        putString("soil_drainage", resource.data.soilDrainage)
                        putString("soil_recommendation", resource.data.soilRecommendation)
                        putString("confidence_reason", resource.data.confidenceReason)
                        
                        putString("crop_mode", resource.data.cropMode)
                        putString("crop_quality", resource.data.cropQuality)
                        putInt("validation_score", resource.data.validationScore)
                        putLong("detection_time", resource.data.detectionTimeMs)
                        putLong("classification_time", resource.data.classificationTimeMs)
                        putStringArrayList("top5_commons", ArrayList(resource.data.top5CommonNames))
                        putStringArrayList("top5_scientifics", ArrayList(resource.data.top5ScientificNames))
                        putFloatArray("top5_confidences", resource.data.top5Confidences.toFloatArray())

                        // Analytics data
                        putInt("analytics_total_scans", resource.data.analyticsTotalScans)
                        putInt("analytics_success_scans", resource.data.analyticsSuccessScans)
                        putInt("analytics_rejected_scans", resource.data.analyticsRejectedScans)
                        putFloat("analytics_avg_confidence", resource.data.analyticsAvgConfidence)
                        putLong("analytics_avg_time", resource.data.analyticsAvgTime)
                        putString("image_hash", resource.data.imageHash)

                        putSerializable("top3_predictions", ArrayList(resource.data.top3Predictions))
                        putSerializable("top3_confidences", ArrayList(resource.data.top3Confidences))
                        putDouble("ambient_temp", resource.data.temp)
                        putDouble("ambient_humidity", resource.data.humidity)
                        putDouble("rain_probability", resource.data.rainProbability)
                        putDouble("uv_index", resource.data.uvIndex)
                        putDouble("wind_speed", resource.data.windSpeed)
                    }
                    
                    val totalTime = resource.data.detectionTimeMs + resource.data.classificationTimeMs
                    Log.i("PlantLensAI", "Pipeline Performance Logs")
                    Log.d("PlantLensAI", "Detection Time = ${resource.data.detectionTimeMs} ms")
                    Log.d("PlantLensAI", "Classification Time = ${resource.data.classificationTimeMs} ms")
                    Log.i("PlantLensAI", "Total Time = $totalTime ms")

                    if (isAdded && findNavController().currentDestination?.id == R.id.scannerFragment) {
                        viewModel.resetScanner()
                        findNavController().navigate(R.id.action_scanner_to_result, bundle)
                    }
                }
                is Resource.Error -> {
                    binding.scanLoadingOverlay.visibility = View.GONE
                    val msg = resource.message?.takeIf { it.isNotBlank() } 
                        ?: resource.exception.localizedMessage?.takeIf { it.isNotBlank() } 
                        ?: "Diagnostic service unavailable. Please check your connection."
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        _binding = null
    }
}
