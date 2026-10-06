package cc.skysparkle.matewave.share

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ApkSharing {
    fun apkFile(context: Context): File = File(context.applicationInfo.sourceDir)

    fun isSplitInstall(context: Context): Boolean =
        !context.applicationInfo.splitSourceDirs.isNullOrEmpty()

    suspend fun copyForSharing(context: Context, appName: String): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val target = File(dir, "$appName.apk")
        apkFile(context).copyTo(target, overwrite = true)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
    }
}
