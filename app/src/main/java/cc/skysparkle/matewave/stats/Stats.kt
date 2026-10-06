package cc.skysparkle.matewave.stats

import android.content.Context

object Stats {
    private const val PREFS = "chess_stats_prefs"
    private const val KEY_WINS = "wins_vs_ai"
    private const val KEY_GAMES = "games_played_vs_ai"
    private const val KEY_STREAK = "current_streak"
    private const val KEY_RATINGS = "rating_history"
    private const val KEY_SOLVED = "solved_puzzles"
    private const val RATING_LIMIT = 40

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun prefs() = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun wins(): Int = prefs()?.getInt(KEY_WINS, 0) ?: 0

    fun gamesPlayed(): Int = prefs()?.getInt(KEY_GAMES, 0) ?: 0

    fun winPercent(): Int {
        val played = gamesPlayed()
        return if (played == 0) 0 else ((wins() * 100f) / played).let { Math.round(it) }
    }

    fun streak(): Int = prefs()?.getInt(KEY_STREAK, 0) ?: 0

    /** Result of a rated game against the AI or another player: 1.0 win, 0.5 draw, 0.0 loss. */
    fun recordResult(result: Double) {
        val p = prefs() ?: return
        val win = result >= 1.0
        p.edit()
            .putInt(KEY_WINS, p.getInt(KEY_WINS, 0) + if (win) 1 else 0)
            .putInt(KEY_GAMES, p.getInt(KEY_GAMES, 0) + 1)
            .putInt(KEY_STREAK, if (win) p.getInt(KEY_STREAK, 0) + 1 else 0)
            .apply()
    }

    /** Rating change after a rated game; the statistics graph shows this history. */
    fun recordRating(before: Int, after: Int) {
        val p = prefs() ?: return
        val history = readRatings().ifEmpty { listOf(before) }
        val ratings = (history + after).takeLast(RATING_LIMIT)
        p.edit().putString(KEY_RATINGS, ratings.joinToString(",")).apply()
    }

    private fun readRatings(): List<Int> {
        val raw = prefs()?.getString(KEY_RATINGS, "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split(",").mapNotNull { it.toIntOrNull() }
    }

    fun solvedPuzzles(): Set<Int> =
        prefs()?.getStringSet(KEY_SOLVED, emptySet())?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()

    fun markPuzzleSolved(id: Int) {
        val p = prefs() ?: return
        val set = (p.getStringSet(KEY_SOLVED, emptySet()) ?: emptySet()).toMutableSet()
        set.add(id.toString())
        p.edit().putStringSet(KEY_SOLVED, set).apply()
    }

    /** Rating history for the graph; with fewer than two games it starts from the current rating. */
    fun ratingCurve(currentRating: Int): List<Int> {
        val ratings = readRatings()
        return when {
            ratings.isEmpty() -> listOf(currentRating, currentRating)
            ratings.size == 1 -> listOf(ratings.first(), ratings.first())
            else -> ratings
        }
    }
}
