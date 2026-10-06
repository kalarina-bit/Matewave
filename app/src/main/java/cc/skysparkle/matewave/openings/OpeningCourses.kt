package cc.skysparkle.matewave.openings

import android.content.Context
import cc.skysparkle.matewave.R
import cc.skysparkle.matewave.engine.Board
import cc.skysparkle.matewave.engine.ChessEngine
import cc.skysparkle.matewave.engine.Move
import cc.skysparkle.matewave.engine.Notation
import cc.skysparkle.matewave.engine.PieceColor

/** An opening line to learn, played from [side]'s point of view. */
data class OpeningCourse(
    val id: String,
    val side: PieceColor,
    val eco: String,
    val name: String,
    val sans: List<String>,
    val moves: List<Move>
) {
    fun isLearnerMove(ply: Int): Boolean = (ply % 2 == 0) == (side == PieceColor.WHITE)
}

/** Artwork for the course card. */
fun OpeningCourse.artRes(): Int = when (id) {
    "Sicilian Defense: Najdorf Variation" -> R.drawable.art_najdorf
    "Sicilian Defense: Dragon Variation" -> R.drawable.art_dragon
    "French Defense: Advance Variation" -> R.drawable.art_french_advance
    "Caro-Kann Defense: Classical Variation" -> R.drawable.art_caro_kann_classical
    "King's Indian Defense: Orthodox Variation" -> R.drawable.art_kings_indian
    "Grünfeld Defense: Exchange Variation" -> R.drawable.art_grunfeld
    "Italian Game: Giuoco Piano" -> R.drawable.art_italian
    "Ruy Lopez: Closed" -> R.drawable.art_ruy_lopez
    "King's Gambit Accepted: Kieseritzky Gambit" -> R.drawable.art_kings_gambit
    "Queen's Gambit Declined: Orthodox Defense" -> R.drawable.art_qgd_orthodox
    "London System" -> R.drawable.art_london
    "Catalan Opening: Open Defense" -> R.drawable.art_catalan
    "Caro-Kann Defense: Advance Variation" -> R.drawable.art_caro_kann_advance
    "Scandinavian Defense: Main Line" -> R.drawable.art_scandinavian
    "Pirc Defense: Classical Variation" -> R.drawable.art_pirc
    "Petrov's Defense: Classical Attack" -> R.drawable.art_petrov
    "Slav Defense: Czech Variation" -> R.drawable.art_slav
    "Nimzo-Indian Defense: Classical Variation" -> R.drawable.art_nimzo_indian
    "Dutch Defense: Leningrad Variation" -> R.drawable.art_dutch
    "Queen's Gambit Declined: Tarrasch Defense" -> R.drawable.art_tarrasch
    "Scotch Game" -> R.drawable.art_scotch
    "Vienna Game: Vienna Gambit" -> R.drawable.art_vienna
    "English Opening: King's English Variation" -> R.drawable.art_english
    "Queen's Gambit Accepted: Classical Defense" -> R.drawable.art_qga
    else -> R.drawable.ic_book
}

/**
 * Curated main lines (assets/openings/courses.tsv: side, ECO, name, SAN line). Every line
 * ends with the learner's move and was checked for legality when the file was built.
 */
object OpeningCourses {
    @Volatile private var cached: List<OpeningCourse>? = null

    fun load(context: Context): List<OpeningCourse> {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val result = context.applicationContext.assets.open("openings/courses.tsv")
                .bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.filter { it.isNotBlank() }.mapNotNull { line ->
                        val f = line.split('\t')
                        if (f.size < 4) return@mapNotNull null
                        val sans = f[3].split(' ')
                        val moves = toMoves(sans) ?: return@mapNotNull null
                        OpeningCourse(
                            id = f[2],
                            side = if (f[0] == "W") PieceColor.WHITE else PieceColor.BLACK,
                            eco = f[1],
                            name = f[2],
                            sans = sans,
                            moves = moves
                        )
                    }.toList()
                }
            cached = result
            return result
        }
    }

    private fun toMoves(sans: List<String>): List<Move>? {
        var board = Board.startPosition()
        val moves = ArrayList<Move>(sans.size)
        for (san in sans) {
            val move = Notation.fromSan(board, san) ?: return null
            moves.add(move)
            board = ChessEngine.applyMove(board, move)
        }
        return moves
    }
}

enum class CourseStatus { NEW, LOCKED, LEARNING, DUE, WAITING }

/** Stars before and after a review, and flawless reviews still needed for the next star. */
data class StarChange(val before: Int, val after: Int, val reviewsToNext: Int)

/** A course with its spaced-repetition state at a given moment. */
data class CourseInfo(
    val course: OpeningCourse,
    val status: CourseStatus,
    /** Learner moves already learned (level 1 or higher). */
    val learned: Int,
    val total: Int,
    val dueCards: Int,
    val nextDueAt: Long?,
    /** 0..3: 1 = fully learned, then one star per [OpeningSrs.REVIEWS_PER_STAR] flawless reviews. */
    val stars: Int,
    /** Flawless reviews still needed for the next star; 0 at three stars. */
    val reviewsToNext: Int = 0
) {
    val mastered: Boolean get() = stars >= 3
}

/**
 * Spaced repetition for opening lines. Every learner move of a course is a card with a level
 * and a due time. A correct first answer on a due card raises its level; a wrong answer sends
 * that card back to level 1. Intervals grow from 4 hours to 90 days.
 */
class OpeningSrs(private val store: CardStore) {

    /** Where cards are kept: SharedPreferences in the app, a map in tests. */
    interface CardStore {
        fun get(key: String): String?
        fun put(key: String, value: String)
    }

    constructor(context: Context) : this(object : CardStore {
        private val prefs = context.applicationContext.getSharedPreferences("opening_srs", Context.MODE_PRIVATE)
        override fun get(key: String): String? = prefs.getString(key, null)
        override fun put(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    })

    private fun key(course: OpeningCourse, ply: Int) = "${course.id}|$ply"

    fun level(course: OpeningCourse, ply: Int): Int = read(course, ply).first

    fun isDue(course: OpeningCourse, ply: Int, now: Long): Boolean {
        val (level, due) = read(course, ply)
        return level > 0 && due <= now
    }

    private fun read(course: OpeningCourse, ply: Int): Pair<Int, Long> {
        val raw = store.get(key(course, ply)) ?: return 0 to 0L
        val parts = raw.split(',')
        return (parts.getOrNull(0)?.toIntOrNull() ?: 0) to (parts.getOrNull(1)?.toLongOrNull() ?: 0L)
    }

    private fun write(course: OpeningCourse, ply: Int, level: Int, due: Long) {
        store.put(key(course, ply), "$level,$due")
    }

    /** First correct play of a new move. */
    fun learn(course: OpeningCourse, ply: Int, now: Long) {
        if (level(course, ply) == 0) write(course, ply, 1, now + INTERVALS[1])
    }

    /** Correct first answer. Only a due card moves up: practising early does not skip reviews. */
    fun success(course: OpeningCourse, ply: Int, now: Long) {
        val (level, due) = read(course, ply)
        if (level == 0 || due > now) return
        val next = minOf(MAX_LEVEL, level + 1)
        write(course, ply, next, now + INTERVALS[next])
    }

    fun fail(course: OpeningCourse, ply: Int, now: Long) {
        if (level(course, ply) == 0) return
        write(course, ply, 1, now + RELEARN_MS)
    }

    private fun reviewsKey(course: OpeningCourse) = "${course.id}|reviews"

    private fun reviewCount(course: OpeningCourse): Int = store.get(reviewsKey(course))?.toIntOrNull() ?: 0

    private fun starsFor(reviews: Int): Int = 1 + minOf(2, reviews / REVIEWS_PER_STAR)

    private fun reviewsToNext(reviews: Int): Int =
        if (reviews >= 2 * REVIEWS_PER_STAR) 0 else REVIEWS_PER_STAR - reviews % REVIEWS_PER_STAR

    /**
     * Records a finished review of due moves. A flawless one counts towards the next star; a
     * mistake or hint takes one star away (the first star, "learned", is never lost) and the
     * lost star has to be earned again from the start.
     */
    fun recordReview(course: OpeningCourse, flawless: Boolean): StarChange {
        val reviews = reviewCount(course)
        val before = starsFor(reviews)
        val after = if (flawless) {
            minOf(reviews + 1, 2 * REVIEWS_PER_STAR)
        } else {
            // Back to the start of the previous star.
            maxOf(0, (before - 2) * REVIEWS_PER_STAR)
        }
        store.put(reviewsKey(course), after.toString())
        return StarChange(before, starsFor(after), reviewsToNext(after))
    }

    fun info(course: OpeningCourse, now: Long, anotherInProgress: Boolean): CourseInfo {
        val plies = course.moves.indices.filter { course.isLearnerMove(it) }
        val cards = plies.map { read(course, it) }
        val learned = cards.count { it.first > 0 }
        val due = cards.count { it.first > 0 && it.second <= now }
        val nextDue = cards.filter { it.first > 0 }.minOfOrNull { it.second }
        val reviews = reviewCount(course)
        val stars = if (learned < plies.size) 0 else starsFor(reviews)
        val status = when {
            learned == 0 && anotherInProgress -> CourseStatus.LOCKED
            learned == 0 -> CourseStatus.NEW
            learned < plies.size -> CourseStatus.LEARNING
            due > 0 -> CourseStatus.DUE
            else -> CourseStatus.WAITING
        }
        return CourseInfo(course, status, learned, plies.size, due, nextDue, stars, if (stars == 0) 0 else reviewsToNext(reviews))
    }

    companion object {
        const val MAX_LEVEL = 7
        /** Flawless reviews needed for each of the second and third stars. */
        const val REVIEWS_PER_STAR = 3
        private const val HOUR = 3_600_000L
        private const val DAY = 24 * HOUR
        /** Interval after reaching each level (index = level). */
        val INTERVALS = longArrayOf(0, 4 * HOUR, DAY, 3 * DAY, 7 * DAY, 16 * DAY, 35 * DAY, 90 * DAY)
        const val RELEARN_MS = 10 * 60_000L
    }
}
