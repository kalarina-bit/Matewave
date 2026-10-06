package cc.skysparkle.matewave.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.skysparkle.matewave.audio.SoundManager
import cc.skysparkle.matewave.engine.Board
import cc.skysparkle.matewave.engine.ChessEngine
import cc.skysparkle.matewave.engine.Move
import cc.skysparkle.matewave.engine.PieceType
import cc.skysparkle.matewave.openings.CourseInfo
import cc.skysparkle.matewave.openings.CourseStatus
import cc.skysparkle.matewave.openings.OpeningBook
import cc.skysparkle.matewave.openings.OpeningCourse
import cc.skysparkle.matewave.openings.OpeningCourses
import cc.skysparkle.matewave.openings.OpeningSrs
import cc.skysparkle.matewave.settings.Haptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** LEARN: new moves with the piece highlighted. REVIEW: only due moves. PRACTICE: every move. */
enum class SessionMode { LEARN, REVIEW, PRACTICE }

enum class TrainerPhase { YOUR_MOVE, AUTO_MOVE, WRONG, COMPLETE }

data class TrainerState(
    val course: OpeningCourse? = null,
    val mode: SessionMode = SessionMode.LEARN,
    val board: Board = Board.startPosition(),
    val ply: Int = 0,
    val phase: TrainerPhase = TrainerPhase.AUTO_MOVE,
    val selectedSquare: Int? = null,
    val legalTargets: List<Move> = emptyList(),
    val lastMove: Move? = null,
    val hintSquare: Int? = null,
    /** Plies the learner has to answer in this session. */
    val asked: List<Int> = emptyList(),
    val answered: Int = 0,
    val firstTry: Int = 0,
    val wrongOnThisMove: Boolean = false,
    val result: CourseInfo? = null,
    /** Set after a review of due moves: stars gained, kept or lost. */
    val starChange: cc.skysparkle.matewave.openings.StarChange? = null
) {
    val sans: List<String> get() = course?.sans?.take(ply) ?: emptyList()
}

data class StudyOverview(
    val courses: List<CourseInfo> = emptyList(),
    val dueCards: Int = 0,
    val dueCourses: Int = 0,
    val nextDueAt: Long? = null,
    val learning: CourseInfo? = null
) {
    val learnedCount: Int get() = courses.count { it.total > 0 && it.learned == it.total }
    val masteredCount: Int get() = courses.count { it.mastered }
}

class OpeningStudyViewModel : ViewModel() {

    private val _overview = MutableStateFlow(StudyOverview())
    val overview: StateFlow<StudyOverview> = _overview

    private val _state = MutableStateFlow(TrainerState())
    val state: StateFlow<TrainerState> = _state

    private var srs: OpeningSrs? = null
    private var courses: List<OpeningCourse> = emptyList()
    private var autoJob: Job? = null

    fun init(context: Context) {
        if (srs != null) { refresh(); return }
        srs = OpeningSrs(context)
        viewModelScope.launch {
            courses = withContext(Dispatchers.IO) {
                runCatching { OpeningBook.load(context) }
                runCatching { OpeningCourses.load(context) }.getOrDefault(emptyList())
            }
            refresh()
        }
    }

    /** Recomputes statuses; due times change with the clock, so call it whenever the list is shown. */
    fun refresh() {
        val s = srs ?: return
        val now = System.currentTimeMillis()
        val inProgress = courses.firstOrNull { s.info(it, now, false).status == CourseStatus.LEARNING }
        val infos = courses.map { s.info(it, now, anotherInProgress = inProgress != null && inProgress != it) }
        _overview.value = StudyOverview(
            courses = infos,
            dueCards = infos.sumOf { it.dueCards },
            dueCourses = infos.count { it.status == CourseStatus.DUE },
            nextDueAt = infos.filter { it.status == CourseStatus.WAITING }.mapNotNull { it.nextDueAt }.minOrNull(),
            learning = infos.firstOrNull { it.status == CourseStatus.LEARNING }
        )
    }

    fun infoOf(course: OpeningCourse): CourseInfo? = _overview.value.courses.firstOrNull { it.course == course }

    /** Opens a course in the mode its state calls for. Returns false for a locked course. */
    fun open(course: OpeningCourse): Boolean {
        val info = infoOf(course) ?: return false
        val mode = when (info.status) {
            CourseStatus.LOCKED -> return false
            CourseStatus.NEW, CourseStatus.LEARNING -> SessionMode.LEARN
            CourseStatus.DUE -> SessionMode.REVIEW
            CourseStatus.WAITING -> SessionMode.PRACTICE
        }
        start(course, mode)
        return true
    }

    fun reviewNext(): Boolean {
        val next = _overview.value.courses.firstOrNull { it.status == CourseStatus.DUE } ?: return false
        start(next.course, SessionMode.REVIEW)
        return true
    }

    fun practiceAgain() {
        _state.value.course?.let { start(it, SessionMode.PRACTICE) }
    }

    /** Next thing to do after a session: a due review, then continuing or starting a course. */
    fun continueStudy(): Boolean {
        refresh()
        val o = _overview.value
        val current = _state.value.course
        val target = o.courses.firstOrNull { it.status == CourseStatus.DUE }
            ?: o.learning
            ?: o.courses.firstOrNull { it.status == CourseStatus.NEW && it.course != current }
            ?: return false
        return open(target.course)
    }

    private fun start(course: OpeningCourse, mode: SessionMode) {
        autoJob?.cancel()
        val s = srs ?: return
        val now = System.currentTimeMillis()
        val learnerPlies = course.moves.indices.filter { course.isLearnerMove(it) }
        val asked = when (mode) {
            SessionMode.LEARN -> learnerPlies.filter { s.level(course, it) == 0 }
            SessionMode.REVIEW -> learnerPlies.filter { s.isDue(course, it, now) }
            SessionMode.PRACTICE -> learnerPlies
        }
        // Learning resumes where it stopped: everything before the first new move is set up instantly.
        val resumePly = if (mode == SessionMode.LEARN) (asked.firstOrNull() ?: 0) else 0
        var board = Board.startPosition()
        for (i in 0 until resumePly) board = ChessEngine.applyMove(board, course.moves[i])
        _state.value = TrainerState(
            course = course,
            mode = mode,
            board = board,
            ply = resumePly,
            lastMove = course.moves.getOrNull(resumePly - 1),
            asked = asked
        )
        continueLine()
    }

    fun restart() {
        val s = _state.value
        val course = s.course ?: return
        start(course, s.mode)
    }

    fun showHint() {
        val s = _state.value
        val course = s.course ?: return
        if (s.phase != TrainerPhase.YOUR_MOVE && s.phase != TrainerPhase.WRONG) return
        // A hint counts like a mistake for scheduling: the move was not recalled.
        if (!s.wrongOnThisMove && s.mode != SessionMode.LEARN) {
            srs?.fail(course, s.ply, System.currentTimeMillis())
        }
        _state.value = s.copy(hintSquare = course.moves[s.ply].from, wrongOnThisMove = true)
    }

    fun onSquareClick(square: Int) {
        val s = _state.value
        val course = s.course ?: return
        if (s.phase != TrainerPhase.YOUR_MOVE && s.phase != TrainerPhase.WRONG) return
        if (s.selectedSquare != null) {
            val candidates = s.legalTargets.filter { it.to == square }
            if (candidates.isNotEmpty()) {
                val move = candidates.firstOrNull { it.promotion == null || it.promotion == PieceType.QUEEN } ?: candidates.first()
                answer(course, move)
                return
            }
        }
        val piece = s.board.squares[square]
        if (piece != null && piece.color == s.board.sideToMove) {
            SoundManager.playSelect()
            Haptics.select()
            _state.value = s.copy(selectedSquare = square, legalTargets = ChessEngine.legalMovesFrom(s.board, square))
        } else {
            _state.value = s.copy(selectedSquare = null, legalTargets = emptyList())
        }
    }

    private fun answer(course: OpeningCourse, move: Move) {
        val s = _state.value
        val expected = course.moves[s.ply]
        val now = System.currentTimeMillis()
        if (move.from != expected.from || move.to != expected.to || move.promotion != expected.promotion) {
            Haptics.alert()
            if (!s.wrongOnThisMove && s.mode != SessionMode.LEARN) srs?.fail(course, s.ply, now)
            _state.value = s.copy(
                phase = TrainerPhase.WRONG,
                wrongOnThisMove = true,
                // After a mistake the right piece is shown, so the line can be finished.
                hintSquare = expected.from,
                selectedSquare = null,
                legalTargets = emptyList()
            )
            return
        }
        when (s.mode) {
            SessionMode.LEARN -> srs?.learn(course, s.ply, now)
            else -> if (!s.wrongOnThisMove) srs?.success(course, s.ply, now)
        }
        _state.value = s.copy(answered = s.answered + 1, firstTry = s.firstTry + if (s.wrongOnThisMove) 0 else 1)
        play(expected)
        continueLine()
    }

    private fun play(move: Move) {
        val s = _state.value
        val capture = s.board.squares[move.to] != null || move.isEnPassant
        if (capture) SoundManager.playCapture() else SoundManager.playMove()
        if (capture) Haptics.capture() else Haptics.move()
        _state.value = s.copy(
            board = ChessEngine.applyMove(s.board, move),
            ply = s.ply + 1,
            lastMove = move,
            selectedSquare = null,
            legalTargets = emptyList(),
            hintSquare = null,
            wrongOnThisMove = false
        )
    }

    /** Auto-plays the opponent's moves and the learner's moves that are not asked this session. */
    private fun continueLine() {
        val s = _state.value
        val course = s.course ?: return
        when {
            s.ply >= course.moves.size -> finish()
            course.isLearnerMove(s.ply) && s.ply in s.asked -> {
                val hint = if (s.mode == SessionMode.LEARN) course.moves[s.ply].from else null
                _state.value = s.copy(phase = TrainerPhase.YOUR_MOVE, hintSquare = hint)
            }
            else -> {
                _state.value = s.copy(phase = TrainerPhase.AUTO_MOVE)
                val ply = s.ply
                autoJob = viewModelScope.launch {
                    delay(if (course.isLearnerMove(ply)) KNOWN_MOVE_DELAY_MS else REPLY_DELAY_MS)
                    val now = _state.value
                    if (now.course != course || now.ply != ply) return@launch
                    play(course.moves[ply])
                    continueLine()
                }
            }
        }
    }

    /**
     * The trainer screen was closed. A review left after a mistake counts as failed, so quitting
     * cannot save a star that a finished review would have cost.
     */
    fun leave() {
        autoJob?.cancel()
        val s = _state.value
        val course = s.course ?: return
        if (s.phase == TrainerPhase.COMPLETE) return
        val mistake = s.firstTry < s.answered || s.wrongOnThisMove
        if (s.mode == SessionMode.REVIEW && s.asked.isNotEmpty() && mistake) {
            val change = srs?.recordReview(course, flawless = false)
            refresh()
            _state.value = s.copy(phase = TrainerPhase.COMPLETE, hintSquare = null, result = infoOf(course), starChange = change)
        } else {
            refresh()
        }
    }

    private fun finish() {
        val s = _state.value
        val course = s.course ?: return
        // Only reviews of due moves change stars: they are spaced by the schedule, so stars
        // cannot be farmed by repeating a line. Practice sessions leave stars alone.
        val change = if (s.mode == SessionMode.REVIEW && s.asked.isNotEmpty()) {
            srs?.recordReview(course, flawless = s.firstTry == s.asked.size)
        } else null
        refresh()
        _state.value = s.copy(phase = TrainerPhase.COMPLETE, hintSquare = null, result = infoOf(course), starChange = change)
    }

    private companion object {
        const val REPLY_DELAY_MS = 500L
        const val KNOWN_MOVE_DELAY_MS = 300L
    }
}
