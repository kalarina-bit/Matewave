package cc.skysparkle.matewave.puzzles

import android.content.Context
import cc.skysparkle.matewave.R
import org.json.JSONArray

data class Puzzle(
    val id: Int,
    val theme: String,
    val fen: String,
    val solutionUci: List<String>,
    val rating: Int,
    val themes: List<String> = emptyList()
)

/** Localized name of the puzzle theme, shown as the screen title. */
fun Puzzle.themeTitleRes(): Int = when (theme) {
    "tactic" -> R.string.puzzle_theme_tactic
    "mate_in_1" -> R.string.puzzle_theme_mate_in_1
    "mate_in_2" -> R.string.puzzle_theme_mate_in_2
    "mate_in_3" -> R.string.puzzle_theme_mate_in_3
    "fork" -> R.string.puzzle_theme_fork
    "pin" -> R.string.puzzle_theme_pin
    "discovered_attack" -> R.string.puzzle_theme_discovered_attack
    "rook_endgame" -> R.string.puzzle_theme_rook_endgame
    "pawn_endgame" -> R.string.puzzle_theme_pawn_endgame
    "sacrifice" -> R.string.puzzle_theme_sacrifice
    "deflection" -> R.string.puzzle_theme_deflection
    "hanging_piece" -> R.string.puzzle_theme_hanging_piece
    "skewer" -> R.string.puzzle_theme_skewer
    "bishop_endgame" -> R.string.puzzle_theme_bishop_endgame
    "zugzwang" -> R.string.puzzle_theme_zugzwang
    "trapped_piece" -> R.string.puzzle_theme_trapped_piece
    "knight_endgame" -> R.string.puzzle_theme_knight_endgame
    "promotion" -> R.string.puzzle_theme_promotion
    "attraction" -> R.string.puzzle_theme_attraction
    "double_check" -> R.string.puzzle_theme_double_check
    "clearance" -> R.string.puzzle_theme_clearance
    "queen_endgame" -> R.string.puzzle_theme_queen_endgame
    "en_passant" -> R.string.puzzle_theme_en_passant
    "interference" -> R.string.puzzle_theme_interference
    "xray" -> R.string.puzzle_theme_xray
    else -> R.string.puzzle_title_fallback
}

/** Loads the bundled Lichess puzzle set (CC0) from assets and caches it in memory. */
object PuzzleRepository {
    @Volatile
    private var cached: List<Puzzle>? = null

    fun load(context: Context): List<Puzzle> {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val json = context.assets.open("puzzles.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val arr = JSONArray(json)
            val result = ArrayList<Puzzle>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val solutionArr = o.getJSONArray("solution")
                val solution = (0 until solutionArr.length()).map { solutionArr.getString(it) }
                val themesArr = o.optJSONArray("themes")
                val themes = themesArr?.let { t -> (0 until t.length()).map { t.getString(it) } } ?: emptyList()
                result.add(
                    Puzzle(
                        id = o.getInt("id"),
                        theme = o.optString("theme", "tactic"),
                        fen = o.getString("fen"),
                        solutionUci = solution,
                        rating = o.getInt("rating"),
                        themes = themes
                    )
                )
            }
            cached = result
            return result
        }
    }

    /**
     * A puzzle close to [rating]: a random unsolved one within ±100, widening the window if
     * needed. Once every nearby puzzle is solved, solved ones are offered again.
     */
    fun pickForRating(context: Context, rating: Int, solved: Set<Int>, exclude: Int? = null): Puzzle? {
        val all = load(context).filter { it.id != exclude }
        if (all.isEmpty()) return null
        val unsolved = all.filter { it.id !in solved }
        for (pool in listOf(unsolved, all)) {
            for (window in intArrayOf(100, 200, 400)) {
                val near = pool.filter { kotlin.math.abs(it.rating - rating) <= window }
                if (near.isNotEmpty()) return near.random()
            }
        }
        return (unsolved.ifEmpty { all }).minByOrNull { kotlin.math.abs(it.rating - rating) }
    }
}
