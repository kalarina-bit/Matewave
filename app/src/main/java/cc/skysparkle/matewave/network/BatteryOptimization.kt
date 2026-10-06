package cc.skysparkle.matewave.network

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

object BatteryOptimization {
    fun isIgnoring(context: Context): Boolean {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return runCatching { power.isIgnoringBatteryOptimizations(context.packageName) }
            .getOrDefault(true)
    }

    fun openSettings(context: Context) {
        val intents = listOf(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}")),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        )
        for (intent in intents) {
            val ok = runCatching {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            if (ok) return
        }
        NetworkLog.log("SYS", "Could not open battery settings")
    }
}
