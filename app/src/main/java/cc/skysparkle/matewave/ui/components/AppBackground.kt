package cc.skysparkle.matewave.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import cc.skysparkle.matewave.R
import cc.skysparkle.matewave.ui.theme.AppBackground
import cc.skysparkle.matewave.ui.theme.ChessGreen
import cc.skysparkle.matewave.ui.theme.Lime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private object BackgroundAssetCache {
    @Volatile private var bitmap: ImageBitmap? = null

    fun cached(): ImageBitmap? = bitmap

    fun load(context: Context): ImageBitmap? {
        bitmap?.let { return it }
        synchronized(this) {
            bitmap?.let { return it }
            val metrics = context.resources.displayMetrics
            val target = maxOf(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(1)

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(context.resources, R.drawable.bg_forest, bounds)

            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > target * 2) sample *= 2

            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
            }
            val decoded = runCatching {
                BitmapFactory.decodeResource(context.resources, R.drawable.bg_forest, options)
                    ?.asImageBitmap()
            }.getOrNull()
            bitmap = decoded
            return decoded
        }
    }
}

/** Slight green cast so the grey mist matches the accent colours. */
private val ForestTint = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            0.80f, 0f, 0f, 0f, 0f,
            0f, 0.95f, 0f, 0f, 6f,
            0f, 0f, 0.85f, 0f, 2f,
            0f, 0f, 0f, 1f, 0f
        )
    )
)

/**
 * The misty forest artwork, deeply darkened so it reads as texture: the mist stays
 * visible behind the headings at the top and fades into near-black lower down.
 */
@Composable
fun AppBackdrop() {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf(BackgroundAssetCache.cached()) }

    LaunchedEffect(Unit) {
        if (bitmap == null) {
            bitmap = withContext(Dispatchers.IO) { BackgroundAssetCache.load(context) }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        bitmap?.let { image ->
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                colorFilter = ForestTint
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val shade = Brush.verticalGradient(
                        0.00f to AppBackground.copy(alpha = 0.72f),
                        0.30f to AppBackground.copy(alpha = 0.80f),
                        0.60f to AppBackground.copy(alpha = 0.90f),
                        1.00f to AppBackground.copy(alpha = 0.96f)
                    )
                    val glow = Brush.radialGradient(
                        colors = listOf(ChessGreen.copy(alpha = 0.14f), ChessGreen.copy(alpha = 0f)),
                        center = Offset(size.width * 0.9f, 0f),
                        radius = size.maxDimension * 0.5f
                    )
                    val lime = Brush.radialGradient(
                        colors = listOf(Lime.copy(alpha = 0.05f), Lime.copy(alpha = 0f)),
                        center = Offset(0f, size.height),
                        radius = size.maxDimension * 0.45f
                    )
                    onDrawBehind {
                        drawRect(shade)
                        drawRect(glow)
                        drawRect(lime)
                    }
                }
        )
    }
}
