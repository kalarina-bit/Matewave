package cc.skysparkle.matewave.ui.screens

import cc.skysparkle.matewave.ui.theme.ink

import android.content.Intent
import android.text.format.Formatter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cc.skysparkle.matewave.R
import cc.skysparkle.matewave.share.ApkShareServer
import cc.skysparkle.matewave.share.ApkSharing
import cc.skysparkle.matewave.share.LocalNetwork
import cc.skysparkle.matewave.ui.components.AppIcon
import cc.skysparkle.matewave.ui.components.ButtonIcon
import cc.skysparkle.matewave.ui.components.DialogAccent
import cc.skysparkle.matewave.ui.components.GlassCard
import cc.skysparkle.matewave.ui.components.GlassOutlinedButton
import cc.skysparkle.matewave.ui.components.GlassScaffold
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch

@Composable
fun ShareApkScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val appName = stringResource(R.string.app_title)
    val apk = remember { ApkSharing.apkFile(context) }
    var url by remember { mutableStateOf<String?>(null) }
    var preparing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val server = ApkShareServer(apk, appName)
        val ip = LocalNetwork.wifiIpv4()
        if (ip != null && apk.exists()) {
            val port = runCatching { server.start() }.getOrNull()
            if (port != null) url = "http://$ip:$port/"
        }
        view.keepScreenOn = true
        onDispose {
            server.stop()
            view.keepScreenOn = false
        }
    }

    GlassScaffold(title = stringResource(R.string.share_row_title), onBack = onBack) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AppIcon(R.drawable.ic_wifi, size = 44.dp, tint = DialogAccent)
            Text(
                stringResource(R.string.share_wifi_title),
                color = ink,
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                stringResource(R.string.share_wifi_subtitle),
                color = ink.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )

            val address = url
            if (address != null) {
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(20.dp))

                        .background(Color.White)
                        .padding(18.dp)
                ) {
                    QrCode(address, Modifier.size(204.dp))
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ink.copy(alpha = 0.10f))
                        .padding(vertical = 14.dp, horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(address, color = ink.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Text(
                    stringResource(R.string.share_no_wifi),
                    color = Color(0xFFFFC46B),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }

            Text(
                stringResource(R.string.share_apk_size, Formatter.formatShortFileSize(context, apk.length())),
                color = ink.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium
            )
            if (ApkSharing.isSplitInstall(context)) {
                Text(
                    stringResource(R.string.share_split_warning),
                    color = Color(0xFFFFC46B),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.share_instructions_title),
                color = ink,
                style = MaterialTheme.typography.titleMedium
            )
            Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    R.string.share_step_1, R.string.share_step_2, R.string.share_step_3, R.string.share_step_4
                ).forEachIndexed { index, step ->
                    Text(
                        "${index + 1}. ${stringResource(step)}",
                        color = ink.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        HorizontalDivider(color = ink.copy(alpha = 0.15f))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                stringResource(R.string.share_other_title),
                color = ink,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.share_other_hint),
                color = ink.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }

        GlassOutlinedButton(
            onClick = {
                if (!preparing) {
                    preparing = true
                    failed = false
                    scope.launch {
                        val uri = runCatching { ApkSharing.copyForSharing(context, appName) }.getOrNull()
                        preparing = false
                        if (uri == null) {
                            failed = true
                        } else {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "application/vnd.android.package-archive"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(send, null))
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            ButtonIcon(R.drawable.ic_share)
            Text(
                stringResource(if (preparing) R.string.share_preparing else R.string.share_button),
                color = ink
            )
        }
        if (failed) {
            Text(
                stringResource(R.string.share_failed),
                color = Color(0xFFFF6B6B),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) {
        QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
    }
    Canvas(modifier = modifier) {
        val modules = matrix.width
        val cell = size.width / modules
        for (y in 0 until modules) {
            for (x in 0 until modules) {
                if (matrix.get(x, y)) {
                    drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.6f, cell + 0.6f))
                }
            }
        }
    }
}
