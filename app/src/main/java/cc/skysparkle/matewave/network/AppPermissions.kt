package cc.skysparkle.matewave.network

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object AppPermissions {
    /** Runtime permissions for Bluetooth play: scan, advertise and connect (location before Android 12). */
    val nearby: List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun allGranted(context: Context, permissions: List<String>): Boolean =
        permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
}
