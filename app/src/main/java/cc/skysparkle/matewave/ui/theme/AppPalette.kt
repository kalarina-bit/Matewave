package cc.skysparkle.matewave.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import cc.skysparkle.matewave.R

/** The colours every screen is drawn with. The app stays dark in every style. */
data class AppPalette(
    /** Highlights, selected states, progress. */
    val accent: Color,
    /** The one main action on a screen. */
    val action: Color,
    /** Text and icons on top of [accent] or [action]. */
    val onAccent: Color,
    val background: Color,
    val surface1: Color,
    val surface2: Color,
    /** The forest backdrop keeps its green cast only with the original colours. */
    val greenBackdrop: Boolean = false
)

enum class AppStyle(val titleRes: Int) {
    MATEWAVE(R.string.app_style_matewave),
    LAVENDER(R.string.app_style_lavender),
    PEACH(R.string.app_style_peach),
    MINT(R.string.app_style_mint),
    SKY(R.string.app_style_sky),
    DEVICE(R.string.app_style_device);

    companion object {
        /** Wallpaper colours exist from Android 12. */
        val deviceSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

        val available: List<AppStyle> get() = entries.filter { it != DEVICE || deviceSupported }
    }
}

private val MatewavePalette = AppPalette(
    accent = Color(0xFF35E07E),
    action = Color(0xFFE3F84B),
    onAccent = Color(0xFF0B1610),
    background = Color(0xFF0C0F0E),
    surface1 = Color(0xFF161B19),
    surface2 = Color(0xFF1F2623),
    greenBackdrop = true
)

private val LavenderPalette = AppPalette(
    accent = Color(0xFFC8B6FF),
    action = Color(0xFFFFC6E5),
    onAccent = Color(0xFF1E1630),
    background = Color(0xFF0F0E14),
    surface1 = Color(0xFF1A1822),
    surface2 = Color(0xFF25222F)
)

private val PeachPalette = AppPalette(
    accent = Color(0xFFFFB59E),
    action = Color(0xFFFFE08A),
    onAccent = Color(0xFF2A1710),
    background = Color(0xFF12100E),
    surface1 = Color(0xFF1D1A17),
    surface2 = Color(0xFF28231F)
)

private val MintPalette = AppPalette(
    accent = Color(0xFF9EE7C8),
    action = Color(0xFFD8F5A8),
    onAccent = Color(0xFF0F231B),
    background = Color(0xFF0D110F),
    surface1 = Color(0xFF171C1A),
    surface2 = Color(0xFF202724)
)

private val SkyPalette = AppPalette(
    accent = Color(0xFFA5CBFF),
    action = Color(0xFFB8F1EE),
    onAccent = Color(0xFF0F1A2A),
    background = Color(0xFF0D1014),
    surface1 = Color(0xFF171B21),
    surface2 = Color(0xFF20252D)
)

/** The selected style, kept in compose state so every screen recolours at once. */
object AppThemeState {
    private const val PREFS = "app_theme"
    private const val KEY_STYLE = "style"

    var style by mutableStateOf(AppStyle.MATEWAVE)
        private set

    var palette by mutableStateOf(MatewavePalette)
        private set

    /** Reads the saved style; call once before the first frame. */
    fun load(context: Context) {
        val saved = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_STYLE, null)
        val style = AppStyle.entries.firstOrNull { it.name == saved } ?: AppStyle.MATEWAVE
        applyStyle(context, style)
    }

    fun select(context: Context, style: AppStyle) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_STYLE, style.name).apply()
        applyStyle(context, style)
    }

    private fun applyStyle(context: Context, requested: AppStyle) {
        val style = if (requested == AppStyle.DEVICE && !AppStyle.deviceSupported) AppStyle.MATEWAVE else requested
        this.style = style
        palette = paletteFor(context, style)
    }

    /** Preview colours for the style picker. */
    fun paletteFor(context: Context, style: AppStyle): AppPalette = when (style) {
        AppStyle.MATEWAVE -> MatewavePalette
        AppStyle.LAVENDER -> LavenderPalette
        AppStyle.PEACH -> PeachPalette
        AppStyle.MINT -> MintPalette
        AppStyle.SKY -> SkyPalette
        AppStyle.DEVICE -> devicePalette(context)
    }

    private fun devicePalette(context: Context): AppPalette {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return MatewavePalette
        val scheme = dynamicDarkColorScheme(context)
        return AppPalette(
            accent = scheme.primary,
            action = scheme.tertiary,
            onAccent = scheme.onPrimary,
            background = scheme.background,
            surface1 = scheme.surfaceContainer,
            surface2 = scheme.surfaceContainerHigh
        )
    }
}
