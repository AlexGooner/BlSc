package com.citrus.blsc.utils

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionHelper {

    fun getBluetoothPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    fun areBluetoothPermissionsGranted(context: Context): Boolean {
        val permissions = getBluetoothPermissions()
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(
                context,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun isBluetoothEnabled(): Boolean {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        return bluetoothAdapter?.isEnabled == true
    }

    @SuppressLint("ObsoleteSdkInt")
    fun isLocationEnabled(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val locationManager =
                context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            locationManager.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            val contentResolver = context.contentResolver

            @Suppress("DEPRECATION")
            val locationMode = Settings.Secure.getInt(
                contentResolver,
                Settings.Secure.LOCATION_MODE,
                Settings.Secure.LOCATION_MODE_OFF
            )
            locationMode != Settings.Secure.LOCATION_MODE_OFF
        }
    }

    fun areAllRequirementsMet(context: Context): Boolean {
        return areBluetoothPermissionsGranted(context) &&
                isBluetoothEnabled() &&
                isLocationEnabled(context)
                isNotificationPermissionGranted(context)
    }

    fun getMissingRequirements(context: Context): List<String> {
        val missing = mutableListOf<String>()

        if (!areBluetoothPermissionsGranted(context)) {
            missing.add("Разрешения Bluetooth и локации")
        }

        if (!isBluetoothEnabled()) {
            missing.add("Включенный Bluetooth")
        }

        if (!isLocationEnabled(context)) {
            missing.add("Включенная геолокация (GPS)")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!isNotificationPermissionGranted(context)) {
                missing.add("Разрешение на уведомления")
            }
        }
        return missing
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun requestEnableBluetooth(activity: Activity, requestCode: Int) {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        activity.startActivityForResult(enableBtIntent, requestCode)
    }

    fun requestEnableLocation(activity: Activity) {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        activity.startActivity(intent)
    }

    fun isNotificationPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Для версий ниже Android 13 разрешение не требуется
        }
    }
    fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf<String>()

        permissions.addAll(getBluetoothPermissions().toList())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        return permissions
    }

}