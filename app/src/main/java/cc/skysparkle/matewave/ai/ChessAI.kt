package cc.skysparkle.matewave.ai

import cc.skysparkle.matewave.engine.Board
import cc.skysparkle.matewave.engine.ChessEngine
import cc.skysparkle.matewave.engine.Move
import cc.skysparkle.matewave.engine.Notation
import cc.skysparkle.matewave.engine.PieceColor
import cc.skysparkle.matewave.engine.Position

enum class AiDifficulty(val maxDepth: Int, val timeBudgetMs: Long, val noiseCp: Int) {
    EASY(2, 400, 150),
    MEDIUM(3, 1000, 40),
    HARD(5, 2500, 8),
    EXPERT(7, 4500, 0)
}

/**
 * Chess engine opponent: minimax with alpha-beta pruning, iterative deepening and a
 * quiescence search over captures. The time budget per move is derived from the
 * remaining clock, and lower difficulty levels add Gaussian noise at the root so the
 * AI does not play the same game every time (mates are never randomized away).
 *
 * The search makes and takes back moves on one [Position] and keeps moves in reused int
 * buffers, so it allocates nothing per node.
 */
object ChessAI {
    /** Piece values by [Position] piece type: pawn, knight, bishop, rook, queen, king. */
    private val VALUE = intArrayOf(0, 100, 320, 330, 500, 900, 20000)

    private const val MATE = 100_000

    private val pawnTable = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0,
        5, 10, 10, -20, -20, 10, 10, 5,
        5, -5, -10, 0, 0, -10, -5, 5,
        0, 0, 0, 20, 20, 0, 0, 0,
        5, 5, 10, 25, 25, 10, 5, 5,
        10, 10, 20, 30, 30, 20, 10, 10,
        50, 50, 50, 50, 50, 50, 50, 50,
        0, 0, 0, 0, 0, 0, 0, 0
    )

    private val knightTable = intArrayOf(
        -50, -40, -30, -30, -30, -30, -40, -50,
        -40, -20, 0, 5, 5, 0, -20, -40,
        -30, 5, 10, 15, 15, 10, 5, -30,
        -30, 0, 15, 20, 20, 15, 0, -30,
        -30, 5, 15, 20, 20, 15, 5, -30,
        -30, 0, 10, 15, 15, 10, 0, -30,
        -40, -20, 0, 0, 0, 0, -20, -40,
        -50, -40, -30, -30, -30, -30, -40, -50
    )

    private val bishopTable = intArrayOf(
        -20, -10, -10, -10, -10, -10, -10, -20,
        -10, 5, 0, 0, 0, 0, 5, -10,
        -10, 10, 10, 10, 10, 10, 10, -10,
        -10, 0, 10, 10, 10, 10, 0, -10,
        -10, 5, 5, 10, 10, 5, 5, -10,
        -10, 0, 5, 10, 10, 5, 0, -10,
        -10, 0, 0, 0, 0, 0, 0, -10,
        -20, -10, -10, -10, -10, -10, -10, -20
    )

    private val rookTable = intArrayOf(
        0, 0, 0, 5, 5, 0, 0, 0,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        5, 10, 10, 10, 10, 10, 10, 5,
        0, 0, 0, 0, 0, 0, 0, 0
    )

    private val queenTable = intArrayOf(
        -20, -10, -10, -5, -5, -10, -10, -20,
        -10, 0, 5, 0, 0, 0, 0, -10,
        -10, 5, 5, 5, 5, 5, 0, -10,
        0, 0, 5, 5, 5, 5, 0, -5,
        -5, 0, 5, 5, 5, 5, 0, -5,
        -10, 0, 5, 5, 5, 5, 0, -10,
        -10, 0, 0, 0, 0, 0, 0, -10,
        -20, -10, -10, -5, -5, -10, -10, -20
    )

    private val kingMiddleTable = intArrayOf(
        20, 30, 10, 0, 0, 10, 30, 20,
        20, 20, 0, 0, 0, 0, 20, 20,
        -10, -20, -20, -20, -20, -20, -20, -10,
        -20, -30, -30, -40, -40, -30, -30, -20,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30
    )

    private val kingEndTable = intArrayOf(
        -50, -30, -30, -30, -30, -30, -30, -50,
        -30, -30, 0, 0, 0, 0, -30, -30,
        -30, -10, 20, 30, 30, 20, -10, -30,
        -30, -10, 30, 40, 40, 30, -10, -30,
        -30, -10, 30, 40, 40, 30, -10, -30,
        -30, -10, 20, 30, 30, 20, -10, -30,
        -30, -20, -10, 0, 0, -10, -20, -30,
        -50, -40, -30, -20, -20, -30, -40, -50
    )

    // Material plus table bonus per piece code and square, signed from White's side. Kings are
    // kept apart because their table depends on the phase.
    private val PIECE_SQUARE = IntArray(16 * 64)
    private val KING_MIDDLE = IntArray(16 * 64)
    private val KING_END = IntArray(16 * 64)
    private val NON_PAWN_VALUE = IntArray(16)

    init {
        for (color in Position.WHITE..Position.BLACK) for (type in Position.PAWN..Position.KING) {
            val code = Position.code(type, color)
            val sign = if (color == Position.WHITE) 1 else -1
            if (type != Position.PAWN && type != Position.KING) NON_PAWN_VALUE[code] = VALUE[type]
            for (sq in 0 until 64) {
                val idx = if (color == Position.WHITE) sq else sq xor 56
                val i = code * 64 + sq
                when (type) {
                    Position.PAWN -> PIECE_SQUARE[i] = sign * (VALUE[type] + pawnTable[idx])
                    Position.KNIGHT -> PIECE_SQUARE[i] = sign * (VALUE[type] + knightTable[idx])
                    Position.BISHOP -> PIECE_SQUARE[i] = sign * (VALUE[type] + bishopTable[idx])
                    Position.ROOK -> PIECE_SQUARE[i] = sign * (VALUE[type] + rookTable[idx])
                    Position.QUEEN -> PIECE_SQUARE[i] = sign * (VALUE[type] + queenTable[idx])
                    else -> {
                        KING_MIDDLE[i] = sign * (VALUE[type] + kingMiddleTable[idx])
                        KING_END[i] = sign * (VALUE[type] + kingEndTable[idx])
                    }
                }
            }
        }
    }

    /** Score from White's side; the endgame king table applies with little non-pawn material. */
    private fun evaluate(position: Position): Int {
        val squares = position.squares
        var score = 0
        var nonPawnMaterial = 0
        var kingMiddle = 0
        var kingEnd = 0
        for (sq in 0 until 64) {
            val code = squares[sq]
            if (code == 0) continue
            val i = code * 64 + sq
            if (Position.typeOf(code) == Position.KING) {
                kingMiddle += KING_MIDDLE[i]
                kingEnd += KING_END[i]
            } else {
                score += PIECE_SQUARE[i]
                nonPawnMaterial += NON_PAWN_VALUE[code]
            }
        }
        return score + if (nonPawnMaterial <= 1_300) kingEnd else kingMiddle
    }

    private fun moveScore(squares: IntArray, move: Int): Int {
        var score = 0
        val victim = squares[Position.moveTo(move)]
        if (victim != 0) {
            val attacker = squares[Position.moveFrom(move)]
            score = VALUE[Position.typeOf(victim)] * 16 -
                (if (attacker != 0) VALUE[Position.typeOf(attacker)] / 100 else 0)
        } else if (Position.isEnPassant(move)) {
            score = VALUE[Position.PAWN] * 16
        }
        if (Position.promotionOf(move) == Position.QUEEN) score += 800
        return score
    }

    private class Search(
        private val position: Position,
        private val deadline: Long,
        private val history: Set<String>,
        /** Sorted [Position.keyHash] values of [history]. */
        private val historyHashes: LongArray,
        private val isCancelled: () -> Boolean
    ) {
        private var nodes = 0
        var timedOut = false
            private set

        // Move stack: each node uses the slots from [top] on and frees them when it returns.
        private var moves = IntArray(Position.MAX_MOVES * 16)
        private var scores = IntArray(Position.MAX_MOVES * 16)
        private var top = 0

        private fun timeUp(): Boolean {
            if (timedOut) return true
            nodes++
            if ((nodes and 1023) == 0 && (System.currentTimeMillis() >= deadline || isCancelled())) timedOut = true
            return timedOut
        }

        /**
         * Generates the moves from [start] and orders them by [moveScore], best first and stable,
         * as sorting the list of legal moves would.
         */
        private fun generateOrdered(start: Int, tacticalOnly: Boolean): Int {
            if (start + Position.MAX_MOVES > moves.size) {
                moves = moves.copyOf(moves.size * 2)
                scores = scores.copyOf(scores.size * 2)
            }
            val moves = moves
            val scores = scores
            val end = position.generate(moves, start, tacticalOnly)
            for (i in start until end) scores[i] = moveScore(position.squares, moves[i])
            for (i in start + 1 until end) {
                val move = moves[i]
                val score = scores[i]
                var j = i - 1
                while (j >= start && scores[j] < score) {
                    moves[j + 1] = moves[j]
                    scores[j + 1] = scores[j]
                    j--
                }
                moves[j + 1] = move
                scores[j + 1] = score
            }
            return end
        }

        /** Whether the position occurred in the game before the search started. */
        private fun isRepetition(): Boolean {
            if (historyHashes.isEmpty() || historyHashes.binarySearch(position.keyHash()) < 0) return false
            return Notation.positionKey(position.toBoard()) in history
        }

        private fun quiesce(alphaIn: Int, betaIn: Int, maximizing: Boolean, qDepth: Int): Int {
            val standPat = evaluate(position)
            if (timeUp() || qDepth == 0) return standPat
            var alpha = alphaIn
            var beta = betaIn
            if (maximizing) {
                if (standPat >= beta) return standPat
                if (standPat > alpha) alpha = standPat
            } else {
                if (standPat <= alpha) return standPat
                if (standPat < beta) beta = standPat
            }
            val start = top
            val end = generateOrdered(start, tacticalOnly = true)
            top = end
            var best = standPat
            for (i in start until end) {
                val move = moves[i]
                position.make(move)
                if (!position.moverKingSafe()) {
                    position.unmake(move)
                    continue
                }
                val value = quiesce(alpha, beta, !maximizing, qDepth - 1)
                position.unmake(move)
                if (maximizing) {
                    if (value > best) best = value
                    if (value > alpha) alpha = value
                } else {
                    if (value < best) best = value
                    if (value < beta) beta = value
                }
                if (beta <= alpha || timedOut) break
            }
            top = start
            return best
        }

        fun minimax(depth: Int, alphaIn: Int, betaIn: Int, maximizing: Boolean, ply: Int): Int {
            if (timeUp()) return evaluate(position)
            var alpha = alphaIn
            var beta = betaIn

            if (ply in 1..2 && isRepetition()) return 0

            if (position.halfmove >= 100 || position.isDeadPosition()) return 0

            val inCheck = position.inCheck()
            if (depth <= 0 && !inCheck) return quiesce(alpha, beta, maximizing, QUIESCENCE_DEPTH)

            val nextDepth = if (depth <= 0) 0 else depth - 1

            val start = top
            val end = generateOrdered(start, tacticalOnly = false)
            top = end
            var legal = 0
            var best = if (maximizing) Int.MIN_VALUE else Int.MAX_VALUE
            for (i in start until end) {
                val move = moves[i]
                position.make(move)
                if (!position.moverKingSafe()) {
                    position.unmake(move)
                    continue
                }
                legal++
                val value = minimax(nextDepth, alpha, beta, !maximizing, ply + 1)
                position.unmake(move)
                if (maximizing) {
                    best = maxOf(best, value)
                    alpha = maxOf(alpha, value)
                } else {
                    best = minOf(best, value)
                    beta = minOf(beta, value)
                }
                if (beta <= alpha || timedOut) break
            }
            top = start

            if (legal == 0) {
                return if (inCheck) {
                    if (maximizing) -MATE + ply else MATE - ply
                } else 0
            }
            return best
        }
    }

    private fun hashesOf(history: Set<String>): LongArray {
        val hashes = LongArray(history.size)
        var n = 0
        for (key in history) {
            val board = runCatching { Board.fromFen(key) }.getOrNull() ?: continue
            hashes[n++] = Position(board).keyHash()
        }
        return hashes.copyOf(n).also { it.sort() }
    }

    fun bestMove(
        board: Board,
        difficulty: AiDifficulty,
        history: Set<String> = emptySet(),
        clockMillis: Long? = null,
        incrementMillis: Long = 0L,
        random: java.util.Random = java.util.Random(),
        /** Polled during the search; returning true stops it early. */
        isCancelled: () -> Boolean = { false }
    ): Move? {
        val rootMoves = ChessEngine.legalMoves(board)
        if (rootMoves.isEmpty()) return null
        if (rootMoves.size == 1) return rootMoves.first()

        val budget = if (clockMillis == null || clockMillis > 24L * 3600 * 1000) {
            difficulty.timeBudgetMs
        } else {
            minOf(difficulty.timeBudgetMs, clockMillis / 30 + incrementMillis * 3 / 4).coerceAtLeast(MIN_BUDGET_MS)
        }

        val maximizing = board.sideToMove == PieceColor.WHITE
        val position = Position(board)
        val search = Search(position, System.currentTimeMillis() + budget, history, hashesOf(history), isCancelled)

        var order = rootMoves.sortedByDescending { moveScore(position.squares, Position.encode(it)) }
        var lastScored: List<Pair<Move, Int>> = emptyList()

        var depth = 1
        while (depth <= difficulty.maxDepth && !search.timedOut) {
            var alpha = Int.MIN_VALUE
            var beta = Int.MAX_VALUE
            var bestValueThisDepth = if (maximizing) Int.MIN_VALUE else Int.MAX_VALUE
            var bestMoveThisDepth: Move? = null
            val scored = ArrayList<Pair<Move, Int>>(order.size)
            var completed = true

            for (m in order) {
                val move = Position.encode(m)
                position.make(move)

                val a = if (difficulty.noiseCp > 0) Int.MIN_VALUE else alpha
                val b = if (difficulty.noiseCp > 0) Int.MAX_VALUE else beta
                val value = search.minimax(depth - 1, a, b, !maximizing, 1)
                position.unmake(move)

                if (search.timedOut) { completed = false; break }
                scored.add(m to value)
                if (maximizing) {
                    if (bestMoveThisDepth == null || value > bestValueThisDepth) {
                        bestValueThisDepth = value; bestMoveThisDepth = m
                    }
                    alpha = maxOf(alpha, bestValueThisDepth)
                } else {
                    if (bestMoveThisDepth == null || value < bestValueThisDepth) {
                        bestValueThisDepth = value; bestMoveThisDepth = m
                    }
                    beta = minOf(beta, bestValueThisDepth)
                }
            }

            if (completed && bestMoveThisDepth != null) {
                lastScored = if (maximizing) scored.sortedByDescending { it.second }
                else scored.sortedBy { it.second }
                order = lastScored.map { it.first }
            } else {
                break
            }
            depth++
        }

        if (lastScored.isEmpty()) return order.first()
        val bestEntry = lastScored.first()
        if (difficulty.noiseCp <= 0) return bestEntry.first

        if (kotlin.math.abs(bestEntry.second) > MATE / 2) return bestEntry.first

        return lastScored
            .filter { kotlin.math.abs(it.second) < MATE / 2 }
            .maxByOrNull { (_, value) ->
                val fromMover = if (maximizing) value else -value
                fromMover + (random.nextGaussian() * difficulty.noiseCp).toInt()
            }?.first ?: bestEntry.first
    }

    private const val QUIESCENCE_DEPTH = 6
    private const val MIN_BUDGET_MS = 120L
}
