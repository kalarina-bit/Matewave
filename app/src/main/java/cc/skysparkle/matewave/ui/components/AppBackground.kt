package cc.skysparkle.matewave.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.util.DisplayMetrics
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import cc.skysparkle.matewave.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private object BackgroundAssetCache {
    @Volatile private var bitmap: ImageBitmap? = null

    fun cached(): ImageBitmap? = bitmap

    fun load(context: Context): ImageBitmap? {
        bitmap?.let { return it }
        synchronized(this) {
            bitmap?.let { return it }
            val metrics: DisplayMetrics = context.resources.displayMetrics
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

@Composable
fun AppBackgroundImage(modifier: Modifier = Modifier.fillMaxSize()) {
    val context = LocalContext.current

    var bitmap by remember { mutableStateOf(BackgroundAssetCache.cached()) }

    LaunchedEffect(Unit) {
        if (bitmap == null) {
            bitmap = withContext(Dispatchers.IO) { BackgroundAssetCache.load(context) }
        }
    }

    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(modifier = modifier.background(Color(0xFF101413)))
    }
}
