package cc.skysparkle.matewave.network

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.content.ContextCompat

data class NetworkStatus(
    val bluetoothSupported: Boolean,
    val bluetoothOn: Boolean,
    val wifiConnected: Boolean,
    val permissionsGranted: Boolean,
    val notificationsGranted: Boolean
) {
    val bluetoothReady: Boolean
        get() = bluetoothSupported && bluetoothOn && permissionsGranted

    val lanReady: Boolean get() = wifiConnected

    val anyChannelReady: Boolean get() = bluetoothReady || lanReady

    val onlineBlocked: Boolean get() = !anyChannelReady
}

object NetworkRequirements {
    fun check(context: Context): NetworkStatus {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter
        val supported = adapter != null &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        val on = try { adapter?.isEnabled == true } catch (e: SecurityException) { false }

        return NetworkStatus(
            bluetoothSupported = supported,
            bluetoothOn = on,
            wifiConnected = isWifiConnected(context),
            permissionsGranted = AppPermissions.nearby.all { granted(context, it) },
            notificationsGranted = notificationsGranted(context)
        )
    }

    private fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun notificationsGranted(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            granted(context, android.Manifest.permission.POST_NOTIFICATIONS)
        } else true

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
