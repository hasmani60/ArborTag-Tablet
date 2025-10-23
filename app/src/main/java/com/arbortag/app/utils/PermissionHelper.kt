package com.arbortag.app.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Centralized helper for managing permissions and GPS availability
 * Handles all location-related permission checks and GPS status
 */
object PermissionHelper {

    const val PERMISSION_REQUEST_CODE = 1001
    const val GPS_SETTINGS_REQUEST_CODE = 1002

    // Required permissions
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    /**
     * Check if all required permissions are granted
     */
    fun hasAllPermissions(context: Context): Boolean {
        return REQUIRED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Check if GPS/Location is enabled on device
     */
    fun isGPSEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /**
     * Request all required permissions
     */
    fun requestPermissions(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            REQUIRED_PERMISSIONS,
            PERMISSION_REQUEST_CODE
        )
    }

    /**
     * Check permissions and show dialog if not granted
     */
    fun checkAndRequestPermissions(activity: Activity): Boolean {
        if (!hasAllPermissions(activity)) {
            showPermissionRationaleDialog(activity)
            return false
        }
        return true
    }

    /**
     * Check GPS and prompt user to enable if needed
     */
    fun checkAndEnableGPS(activity: Activity): Boolean {
        if (!isGPSEnabled(activity)) {
            showGPSEnableDialog(activity)
            return false
        }
        return true
    }

    /**
     * Show dialog explaining why permissions are needed
     */
    private fun showPermissionRationaleDialog(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle("Permissions Required")
            .setMessage(
                "ArborTag needs the following permissions to function:\n\n" +
                        "📷 Camera - To capture tree images\n" +
                        "📍 Location - To record GPS coordinates for each tree\n\n" +
                        "These permissions are essential for tree tagging."
            )
            .setPositiveButton("Grant Permissions") { _, _ ->
                requestPermissions(activity)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                activity.finish()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Show dialog prompting user to enable GPS
     */
    private fun showGPSEnableDialog(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle("GPS Disabled")
            .setMessage(
                "GPS is currently disabled on your device.\n\n" +
                        "ArborTag requires GPS to record accurate tree locations.\n\n" +
                        "Would you like to enable GPS now?"
            )
            .setPositiveButton("Enable GPS") { _, _ ->
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                activity.startActivityForResult(intent, GPS_SETTINGS_REQUEST_CODE)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    /**
     * Handle permission request results
     */
    fun handlePermissionResult(
        requestCode: Int,
        grantResults: IntArray,
        onGranted: () -> Unit,
        onDenied: () -> Unit
    ) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                onGranted()
            } else {
                onDenied()
            }
        }
    }

    /**
     * Check if location permission is permanently denied
     */
    fun isPermissionPermanentlyDenied(activity: Activity, permission: String): Boolean {
        return !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission) &&
                ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED
    }

    /**
     * Show dialog to open app settings
     */
    fun showAppSettingsDialog(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle("Permission Required")
            .setMessage(
                "It seems you've permanently denied permissions.\n\n" +
                        "Please enable them manually in app settings."
            )
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", activity.packageName, null)
                }
                activity.startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}