package cc.skysparkle.matewave.ai

import cc.skysparkle.matewave.engine.Board
import cc.skysparkle.matewave.engine.ChessEngine
import cc.skysparkle.matewave.engine.Move
import cc.skysparkle.matewave.engine.Notation
import cc.skysparkle.matewave.engine.PieceColor
import cc.skysparkle.matewave.engine.PieceType

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
 */
object ChessAI {
    private fun valueOf(type: PieceType): Int = when (type) {
        PieceType.PAWN -> 100
        PieceType.KNIGHT -> 320
        PieceType.BISHOP -> 330
        PieceType.ROOK -> 500
        PieceType.QUEEN -> 900
        PieceType.KING -> 20000
    }

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

    internal fun evaluate(board: Board): Int {
        var nonPawnMaterial = 0
        for (sq in 0 until 64) {
            val p = board.squares[sq] ?: continue
            if (p.type != PieceType.PAWN && p.type != PieceType.KING) nonPawnMaterial += valueOf(p.type)
        }
        val endgame = nonPawnMaterial <= 1_300

        var score = 0
        for (sq in 0 until 64) {
            val piece = board.squares[sq] ?: continue
            val white = piece.color == PieceColor.WHITE
            val idx = if (white) sq else sq xor 56
            val positional = when (piece.type) {
                PieceType.PAWN -> pawnTable[idx]
                PieceType.KNIGHT -> knightTable[idx]
                PieceType.BISHOP -> bishopTable[idx]
                PieceType.ROOK -> rookTable[idx]
                PieceType.QUEEN -> queenTable[idx]
                PieceType.KING -> if (endgame) kingEndTable[idx] else kingMiddleTable[idx]
            }
            val value = valueOf(piece.type) + positional
            score += if (white) value else -value
        }
        return score
    }

    private fun moveScore(board: Board, m: Move): Int {
        var score = 0
        val victim = board.squares[m.to]
        if (victim != null) {
            val attacker = board.squares[m.from]
            score = valueOf(victim.type) * 16 - (if (attacker != null) valueOf(attacker.type) / 100 else 0)
        } else if (m.isEnPassant) {
            score = valueOf(PieceType.PAWN) * 16
        }
        if (m.promotion == PieceType.QUEEN) score += 800
        return score
    }

    internal fun orderMoves(board: Board, moves: List<Move>): List<Move> =
        moves.sortedByDescending { m -> moveScore(board, m) }

    internal fun isTactical(board: Board, m: Move): Boolean =
        board.squares[m.to] != null || m.isEnPassant || m.promotion != null

    private class Search(private val deadline: Long, val history: Set<String>) {
        private var nodes = 0
        var timedOut = false
            private set

        fun timeUp(): Boolean {
            if (timedOut) return true
            nodes++
            if ((nodes and 1023) == 0 && System.currentTimeMillis() >= deadline) timedOut = true
            return timedOut
        }
    }

    private fun quiesce(
        board: Board, alphaIn: Int, betaIn: Int, maximizing: Boolean, qDepth: Int, search: Search
    ): Int {
        val standPat = evaluate(board)
        if (search.timeUp() || qDepth == 0) return standPat
        var alpha = alphaIn
        var beta = betaIn
        if (maximizing) {
            if (standPat >= beta) return standPat
            if (standPat > alpha) alpha = standPat
        } else {
            if (standPat <= alpha) return standPat
            if (standPat < beta) beta = standPat
        }
        val captures = ChessEngine.legalMoves(board).filter { isTactical(board, it) }
        if (captures.isEmpty()) return standPat
        var best = standPat
        for (m in orderMoves(board, captures)) {
            val value = quiesce(ChessEngine.applyMove(board, m), alpha, beta, !maximizing, qDepth - 1, search)
            if (maximizing) {
                if (value > best) best = value
                if (value > alpha) alpha = value
            } else {
                if (value < best) best = value
                if (value < beta) beta = value
            }
            if (beta <= alpha || search.timedOut) break
        }
        return best
    }

    private fun minimax(
        board: Board, depth: Int, alphaIn: Int, betaIn: Int, maximizing: Boolean, ply: Int, search: Search
    ): Int {
        if (search.timeUp()) return evaluate(board)
        var alpha = alphaIn
        var beta = betaIn

        if (ply in 1..2 && Notation.positionKey(board) in search.history) return 0

        if (board.halfmoveClock >= 100 || ChessEngine.isDeadPosition(board)) return 0

        val inCheck = ChessEngine.isInCheck(board, board.sideToMove)
        if (depth <= 0 && !inCheck) return quiesce(board, alpha, beta, maximizing, QUIESCENCE_DEPTH, search)

        val moves = ChessEngine.legalMoves(board)
        if (moves.isEmpty()) {
            return if (inCheck) {
                if (maximizing) -MATE + ply else MATE - ply
            } else 0
        }

        val nextDepth = if (depth <= 0) 0 else depth - 1

        val ordered = orderMoves(board, moves)
        if (maximizing) {
            var best = Int.MIN_VALUE
            for (m in ordered) {
                val value = minimax(ChessEngine.applyMove(board, m), nextDepth, alpha, beta, false, ply + 1, search)
                best = maxOf(best, value)
                alpha = maxOf(alpha, value)
                if (beta <= alpha || search.timedOut) break
            }
            return best
        } else {
            var best = Int.MAX_VALUE
            for (m in ordered) {
                val value = minimax(ChessEngine.applyMove(board, m), nextDepth, alpha, beta, true, ply + 1, search)
                best = minOf(best, value)
                beta = minOf(beta, value)
                if (beta <= alpha || search.timedOut) break
            }
            return best
        }
    }

    fun bestMove(
        board: Board,
        difficulty: AiDifficulty,
        history: Set<String> = emptySet(),
        clockMillis: Long? = null,
        incrementMillis: Long = 0L,
        random: java.util.Random = java.util.Random()
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
        val search = Search(System.currentTimeMillis() + budget, history)

        var order = orderMoves(board, rootMoves)
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
                val next = ChessEngine.applyMove(board, m)

                val a = if (difficulty.noiseCp > 0) Int.MIN_VALUE else alpha
                val b = if (difficulty.noiseCp > 0) Int.MAX_VALUE else beta
                val value = minimax(next, depth - 1, a, b, !maximizing, 1, search)

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
