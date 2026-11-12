package cc.skysparkle.matewave.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private const val MAX_DIMENSION_PX = 640
private const val JPEG_QUALITY = 60

const val AVATAR_MAX_DIMENSION_PX = 320
const val AVATAR_JPEG_QUALITY = 75

fun encodeImageForChat(
    context: Context,
    uri: Uri,
    maxDimension: Int = MAX_DIMENSION_PX,
    quality: Int = JPEG_QUALITY
): String? {
    return try {
        val resolver = context.contentResolver
        // Read the size first and decode a reduced copy: a full camera photo would not fit in memory.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = resolver.openInputStream(uri) ?: return null
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDimension) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null

        val rotation = runCatching {
            resolver.openInputStream(uri)?.use { input ->
                when (ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }
        }.getOrNull() ?: 0f

        val scale = minOf(1f, maxDimension.toFloat() / maxOf(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            if (rotation != 0f) postRotate(rotation)
        }
        val prepared = if (scale < 1f || rotation != 0f) {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        } else {
            decoded
        }

        val output = ByteArrayOutputStream()
        prepared.compress(Bitmap.CompressFormat.JPEG, quality, output)
        Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }
}

private object DecodedImageCache {
    private val cache = object : android.util.LruCache<String, Bitmap>(budgetBytes()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private fun budgetBytes(): Int =
        (Runtime.getRuntime().maxMemory() / 16).toInt().coerceIn(4 shl 20, 32 shl 20)

    fun key(base64: String, maxDimension: Int) = "$maxDimension:${base64.length}:${base64.hashCode()}"

    fun get(key: String): Bitmap? = cache.get(key)
    fun put(key: String, bitmap: Bitmap) { cache.put(key, bitmap) }
}

/** Larger images (from the opponent) are refused; ours never exceed [MAX_DIMENSION_PX]. */
const val MAX_INCOMING_IMAGE_PX = 4096

/** Upper bound for the decoded size when the caller does not ask for a smaller one. */
private const val DEFAULT_DECODE_PX = 1280

/** Width and height of an encoded image, or null if it is not a readable image. */
private fun imageBounds(bytes: ByteArray): Pair<Int, Int>? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    return if (bounds.outWidth > 0 && bounds.outHeight > 0) bounds.outWidth to bounds.outHeight else null
}

/**
 * Checks an image received from another device before it is shown or stored: it must be a
 * readable image no larger than [MAX_INCOMING_IMAGE_PX] on either side. A small file can
 * declare a huge resolution, and decoding it at full size would run out of memory.
 */
fun isAcceptableIncomingImage(base64: String): Boolean = try {
    val size = imageBounds(Base64.decode(base64, Base64.NO_WRAP))
    size != null && size.first <= MAX_INCOMING_IMAGE_PX && size.second <= MAX_INCOMING_IMAGE_PX
} catch (e: Exception) {
    false
}

fun decodeChatImage(base64: String, maxDimension: Int = 0): Bitmap? {
    return try {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        // The size is always checked first: the image may come from another device.
        val (width, height) = imageBounds(bytes) ?: return null
        if (width > MAX_INCOMING_IMAGE_PX || height > MAX_INCOMING_IMAGE_PX) return null
        val target = if (maxDimension > 0) maxDimension else DEFAULT_DECODE_PX
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= target) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }
}

@Composable
fun rememberChatImage(base64: String?, maxDimension: Int = 0): ImageBitmap? {
    if (base64 == null) return null
    val key = remember(base64, maxDimension) { DecodedImageCache.key(base64, maxDimension) }
    val state = produceState<ImageBitmap?>(
        initialValue = DecodedImageCache.get(key)?.asImageBitmap(),
        key1 = key
    ) {
        if (value == null) {
            value = withContext(Dispatchers.Default) {
                val bitmap = decodeChatImage(base64, maxDimension)
                if (bitmap != null) DecodedImageCache.put(key, bitmap)
                bitmap?.asImageBitmap()
            }
        }
    }
    return state.value
}
