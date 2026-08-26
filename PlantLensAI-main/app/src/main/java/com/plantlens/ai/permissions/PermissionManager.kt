package com.plantlens.ai.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class PermissionManager(private val fragment: Fragment) {

    interface PermissionCallback {
        fun onPermissionGranted()
        fun onPermissionDenied()
    }

    private var permissionCallback: PermissionCallback? = null

    private val requestLauncher: ActivityResultLauncher<Array<String>> =
        fragment.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
            val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions[Manifest.permission.POST_NOTIFICATIONS] ?: true
            } else {
                true
            }

            if (cameraGranted && notificationsGranted) {
                permissionCallback?.onPermissionGranted()
            } else {
                permissionCallback?.onPermissionDenied()
            }
        }

    fun requestCameraAndNotificationPermissions(callback: PermissionCallback) {
        this.permissionCallback = callback
        val context = fragment.requireContext()

        val permissionsToRequest = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val allGranted = permissionsToRequest.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            callback.onPermissionGranted()
        } else {
            requestLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    companion object {
        fun hasCameraPermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}
