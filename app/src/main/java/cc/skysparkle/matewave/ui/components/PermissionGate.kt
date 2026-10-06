package cc.skysparkle.matewave.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import cc.skysparkle.matewave.R

/**
 * Asks for [permissions] after a short explanation. Once Android stops showing its own prompt
 * (the user declined twice), the explanation leads to the app's system settings instead, so
 * the button never silently does nothing.
 */
@Composable
fun rememberPermissionRequester(
    permissions: List<String>,
    rationaleTitle: String,
    rationaleText: String,
    onResult: (granted: Boolean) -> Unit
): () -> Unit {
    val context = LocalContext.current
    var showRationale by remember { mutableStateOf(false) }
    var openSettings by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        onResult(results.values.all { it })
    }

    if (showRationale) {
        PermissionRationaleDialog(
            title = rationaleTitle,
            text = rationaleText,
            confirmText = stringResource(if (openSettings) R.string.permission_open_settings else R.string.permission_allow),
            onConfirm = {
                showRationale = false
                if (openSettings) {
                    openAppSettings(context)
                    onResult(false)
                } else {
                    markRequested(context, permissions)
                    launcher.launch(permissions.toTypedArray())
                }
            },
            onDismiss = {
                showRationale = false
                onResult(false)
            }
        )
    }

    return {
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            onResult(true)
        } else {
            openSettings = permanentlyDenied(context, permissions)
            showRationale = true
        }
    }
}

private const val REQUESTS_PREFS = "permission_requests"

private fun markRequested(context: Context, permissions: List<String>) {
    val editor = context.getSharedPreferences(REQUESTS_PREFS, Context.MODE_PRIVATE).edit()
    permissions.forEach { editor.putBoolean(it, true) }
    editor.apply()
}

/** Asked before, still denied, and Android would not show its prompt again. */
private fun permanentlyDenied(context: Context, permissions: List<String>): Boolean {
    val activity = context.findActivity() ?: return false
    val prefs = context.getSharedPreferences(REQUESTS_PREFS, Context.MODE_PRIVATE)
    return permissions.any { permission ->
        prefs.getBoolean(permission, false) &&
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }
}

private fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun PermissionRationaleDialog(
    title: String,
    text: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = title,
        actions = {
            DialogAction(stringResource(R.string.permission_not_now), onDismiss)
            DialogAction(confirmText, onConfirm)
        }
    ) {
        DialogText(text)
    }
}
