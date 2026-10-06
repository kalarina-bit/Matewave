package cc.skysparkle.matewave.settings

import android.content.Context
import androidx.compose.ui.graphics.Color

enum class BoardTheme(val titleRes: Int, val light: Color, val dark: Color) {
    CLASSIC(cc.skysparkle.matewave.R.string.board_theme_classic, Color(0xFFEDD6B0), Color(0xFFB88762)),
    GREEN(cc.skysparkle.matewave.R.string.board_theme_green, Color(0xFFEEEED2), Color(0xFF769656)),
    BLUE(cc.skysparkle.matewave.R.string.board_theme_blue, Color(0xFFDEE3E6), Color(0xFF8CA2AD)),
    GREY(cc.skysparkle.matewave.R.string.board_theme_grey, Color(0xFFDCDCDC), Color(0xFF8F8F8F))
}

class BoardAppearance(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var theme: BoardTheme
        get() = runCatching { BoardTheme.valueOf(prefs.getString(KEY_THEME, null) ?: "") }
            .getOrDefault(BoardTheme.CLASSIC)
        set(value) { prefs.edit().putString(KEY_THEME, value.name).apply() }

    var showCoordinates: Boolean
        get() = prefs.getBoolean(KEY_COORDS, true)
        set(value) { prefs.edit().putBoolean(KEY_COORDS, value).apply() }

    var useImagePieces: Boolean
        get() = prefs.getBoolean(KEY_IMAGES, true)
        set(value) { prefs.edit().putBoolean(KEY_IMAGES, value).apply() }

    var showLastMove: Boolean
        get() = prefs.getBoolean(KEY_LAST_MOVE, true)
        set(value) { prefs.edit().putBoolean(KEY_LAST_MOVE, value).apply() }

    var showCheckHighlight: Boolean
        get() = prefs.getBoolean(KEY_CHECK, true)
        set(value) { prefs.edit().putBoolean(KEY_CHECK, value).apply() }

    var showOpening: Boolean
        get() = prefs.getBoolean(KEY_OPENING, true)
        set(value) { prefs.edit().putBoolean(KEY_OPENING, value).apply() }

    private companion object {
        const val KEY_OPENING = "opening_in_game"
        const val KEY_LAST_MOVE = "last_move"
        const val KEY_CHECK = "check_highlight"
        const val PREFS = "board_appearance"
        const val KEY_THEME = "theme"
        const val KEY_COORDS = "coords"
        const val KEY_IMAGES = "images"
    }
}
