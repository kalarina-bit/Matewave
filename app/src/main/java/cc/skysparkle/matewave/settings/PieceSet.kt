package cc.skysparkle.matewave.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cc.skysparkle.matewave.R
import cc.skysparkle.matewave.engine.PieceColor
import cc.skysparkle.matewave.engine.PieceType

/** Piece pictures, white and black, in PieceType order: pawn, knight, bishop, rook, queen, king. */
enum class PieceSet(val titleRes: Int, private val white: IntArray, private val black: IntArray) {
    CLASSIC(
        R.string.piece_set_classic,
        intArrayOf(
            R.drawable.piece_classic_white_pawn, R.drawable.piece_classic_white_knight, R.drawable.piece_classic_white_bishop,
            R.drawable.piece_classic_white_rook, R.drawable.piece_classic_white_queen, R.drawable.piece_classic_white_king
        ),
        intArrayOf(
            R.drawable.piece_classic_black_pawn, R.drawable.piece_classic_black_knight, R.drawable.piece_classic_black_bishop,
            R.drawable.piece_classic_black_rook, R.drawable.piece_classic_black_queen, R.drawable.piece_classic_black_king
        )
    ),
    OUTLINE(
        R.string.piece_set_outline,
        intArrayOf(
            R.drawable.piece_outline_white_pawn, R.drawable.piece_outline_white_knight, R.drawable.piece_outline_white_bishop,
            R.drawable.piece_outline_white_rook, R.drawable.piece_outline_white_queen, R.drawable.piece_outline_white_king
        ),
        intArrayOf(
            R.drawable.piece_outline_black_pawn, R.drawable.piece_outline_black_knight, R.drawable.piece_outline_black_bishop,
            R.drawable.piece_outline_black_rook, R.drawable.piece_outline_black_queen, R.drawable.piece_outline_black_king
        )
    ),
    PASTEL(
        R.string.piece_set_pastel,
        intArrayOf(
            R.drawable.piece_pastel_white_pawn, R.drawable.piece_pastel_white_knight, R.drawable.piece_pastel_white_bishop,
            R.drawable.piece_pastel_white_rook, R.drawable.piece_pastel_white_queen, R.drawable.piece_pastel_white_king
        ),
        intArrayOf(
            R.drawable.piece_pastel_black_pawn, R.drawable.piece_pastel_black_knight, R.drawable.piece_pastel_black_bishop,
            R.drawable.piece_pastel_black_rook, R.drawable.piece_pastel_black_queen, R.drawable.piece_pastel_black_king
        )
    ),
    WOOD(
        R.string.piece_set_wood,
        intArrayOf(
            R.drawable.piece_wood_white_pawn, R.drawable.piece_wood_white_knight, R.drawable.piece_wood_white_bishop,
            R.drawable.piece_wood_white_rook, R.drawable.piece_wood_white_queen, R.drawable.piece_wood_white_king
        ),
        intArrayOf(
            R.drawable.piece_wood_black_pawn, R.drawable.piece_wood_black_knight, R.drawable.piece_wood_black_bishop,
            R.drawable.piece_wood_black_rook, R.drawable.piece_wood_black_queen, R.drawable.piece_wood_black_king
        )
    ),
    FLAT(
        R.string.piece_set_flat,
        intArrayOf(
            R.drawable.piece_flat_white_pawn, R.drawable.piece_flat_white_knight, R.drawable.piece_flat_white_bishop,
            R.drawable.piece_flat_white_rook, R.drawable.piece_flat_white_queen, R.drawable.piece_flat_white_king
        ),
        intArrayOf(
            R.drawable.piece_flat_black_pawn, R.drawable.piece_flat_black_knight, R.drawable.piece_flat_black_bishop,
            R.drawable.piece_flat_black_rook, R.drawable.piece_flat_black_queen, R.drawable.piece_flat_black_king
        )
    );

    fun res(type: PieceType, color: PieceColor): Int =
        (if (color == PieceColor.WHITE) white else black)[type.ordinal]
}

/** The chosen piece set; every board and piece picture follows it at once. */
object PieceSetState {
    private const val PREFS = "board_appearance"
    private const val KEY = "piece_set"

    var current by mutableStateOf(PieceSet.CLASSIC)
        private set

    /** Reads the saved set; call once before the first frame. */
    fun load(context: Context) {
        val saved = prefs(context).getString(KEY, null)
        current = PieceSet.entries.firstOrNull { it.name == saved } ?: PieceSet.CLASSIC
    }

    fun select(context: Context, set: PieceSet) {
        prefs(context).edit().putString(KEY, set.name).apply()
        current = set
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
