package cc.skysparkle.matewave.engine

enum class GameStatus {
    ONGOING, CHECKMATE, STALEMATE, DRAW_50_MOVES, DRAW_REPETITION, DRAW_MATERIAL, TIMEOUT,
    /** A flag fell, but the other side has too little material to ever mate. */
    TIMEOUT_DRAW,
    RESIGNATION, DRAW_AGREEMENT
}

/**
 * Rules of chess: legal move generation (castling, en passant, promotion), move
 * application and game-status detection (mate, stalemate, 50-move rule, threefold
 * repetition, insufficient material).
 */
object ChessEngine {
    private fun file(sq: Int) = sq % 8
    private fun rank(sq: Int) = sq / 8
    private fun inBounds(f: Int, r: Int) = f in 0..7 && r in 0..7
    private fun sq(f: Int, r: Int) = r * 8 + f

    private val KNIGHT_DF = intArrayOf(1, 2, 2, 1, -1, -2, -2, -1)
    private val KNIGHT_DR = intArrayOf(2, 1, -1, -2, -2, -1, 1, 2)
    private val DIAG_DF = intArrayOf(1, 1, -1, -1)
    private val DIAG_DR = intArrayOf(1, -1, 1, -1)
    private val ORTHO_DF = intArrayOf(1, -1, 0, 0)
    private val ORTHO_DR = intArrayOf(0, 0, 1, -1)
    private val ALL_DF = intArrayOf(1, -1, 0, 0, 1, 1, -1, -1)
    private val ALL_DR = intArrayOf(0, 0, 1, -1, 1, -1, 1, -1)
    private val PAWN_DF = intArrayOf(-1, 1)

    private fun pseudoLegalMoves(board: Board): List<Move> {
        val moves = ArrayList<Move>(48)
        for (from in 0 until 64) {
            val piece = board.squares[from] ?: continue
            if (piece.color != board.sideToMove) continue
            when (piece.type) {
                PieceType.PAWN -> generatePawnMoves(board, from, piece, moves)
                PieceType.KNIGHT -> generateKnightMoves(board, from, piece, moves)
                PieceType.BISHOP -> generateSlidingMoves(board, from, piece, moves, DIAG_DF, DIAG_DR)
                PieceType.ROOK -> generateSlidingMoves(board, from, piece, moves, ORTHO_DF, ORTHO_DR)
                PieceType.QUEEN -> generateSlidingMoves(board, from, piece, moves, ALL_DF, ALL_DR)
                PieceType.KING -> generateKingMoves(board, from, piece, moves)
            }
        }
        return moves
    }

    private fun generatePawnMoves(board: Board, from: Int, piece: Piece, out: MutableList<Move>) {
        val dir = if (piece.color == PieceColor.WHITE) 1 else -1
        val startRank = if (piece.color == PieceColor.WHITE) 1 else 6
        val promoRank = if (piece.color == PieceColor.WHITE) 7 else 0
        val f = file(from); val r = rank(from)

        val oneR = r + dir
        if (inBounds(f, oneR) && board.squares[sq(f, oneR)] == null) {
            addPawnMoveOrPromotion(from, sq(f, oneR), oneR, promoRank, out)
            val twoR = r + 2 * dir
            if (r == startRank && board.squares[sq(f, twoR)] == null) {
                out.add(Move(from, sq(f, twoR)))
            }
        }
        for (df in PAWN_DF) {
            val nf = f + df; val nr = r + dir
            if (!inBounds(nf, nr)) continue
            val target = sq(nf, nr)
            val targetPiece = board.squares[target]
            if (targetPiece != null && targetPiece.color != piece.color) {
                addPawnMoveOrPromotion(from, target, nr, promoRank, out)
            } else if (target == board.enPassantTarget) {
                out.add(Move(from, target, isEnPassant = true))
            }
        }
    }

    private fun addPawnMoveOrPromotion(from: Int, to: Int, toRank: Int, promoRank: Int, out: MutableList<Move>) {
        if (toRank == promoRank) {
            out.add(Move(from, to, promotion = PieceType.QUEEN))
            out.add(Move(from, to, promotion = PieceType.ROOK))
            out.add(Move(from, to, promotion = PieceType.BISHOP))
            out.add(Move(from, to, promotion = PieceType.KNIGHT))
        } else {
            out.add(Move(from, to))
        }
    }

    private fun generateKnightMoves(board: Board, from: Int, piece: Piece, out: MutableList<Move>) {
        val f = file(from); val r = rank(from)
        for (i in 0 until 8) {
            val nf = f + KNIGHT_DF[i]; val nr = r + KNIGHT_DR[i]
            if (!inBounds(nf, nr)) continue
            val target = sq(nf, nr)
            val targetPiece = board.squares[target]
            if (targetPiece == null || targetPiece.color != piece.color) out.add(Move(from, target))
        }
    }

    private fun generateSlidingMoves(
        board: Board, from: Int, piece: Piece, out: MutableList<Move>, dfs: IntArray, drs: IntArray
    ) {
        val f = file(from); val r = rank(from)
        for (i in dfs.indices) {
            val df = dfs[i]; val dr = drs[i]
            var nf = f + df; var nr = r + dr
            while (inBounds(nf, nr)) {
                val target = sq(nf, nr)
                val targetPiece = board.squares[target]
                if (targetPiece == null) {
                    out.add(Move(from, target))
                } else {
                    if (targetPiece.color != piece.color) out.add(Move(from, target))
                    break
                }
                nf += df; nr += dr
            }
        }
    }

    private fun generateKingMoves(board: Board, from: Int, piece: Piece, out: MutableList<Move>) {
        val f = file(from); val r = rank(from)
        for (df in -1..1) for (dr in -1..1) {
            if (df == 0 && dr == 0) continue
            val nf = f + df; val nr = r + dr
            if (!inBounds(nf, nr)) continue
            val target = sq(nf, nr)
            val targetPiece = board.squares[target]
            if (targetPiece == null || targetPiece.color != piece.color) out.add(Move(from, target))
        }
        val backRank = if (piece.color == PieceColor.WHITE) 0 else 7
        if (from == sq(4, backRank)) {
            val ownRook = Piece(PieceType.ROOK, piece.color)
            val kingSide = (if (piece.color == PieceColor.WHITE) board.whiteKingSideCastle else board.blackKingSideCastle) &&
                board.squares[sq(7, backRank)] == ownRook
            val queenSide = (if (piece.color == PieceColor.WHITE) board.whiteQueenSideCastle else board.blackQueenSideCastle) &&
                board.squares[sq(0, backRank)] == ownRook
            val enemy = piece.color.opposite()
            if (kingSide &&
                board.squares[sq(5, backRank)] == null && board.squares[sq(6, backRank)] == null &&
                !isSquareAttacked(board, sq(4, backRank), enemy) &&
                !isSquareAttacked(board, sq(5, backRank), enemy) &&
                !isSquareAttacked(board, sq(6, backRank), enemy)
            ) {
                out.add(Move(from, sq(6, backRank), isCastleKingSide = true))
            }
            if (queenSide &&
                board.squares[sq(3, backRank)] == null && board.squares[sq(2, backRank)] == null && board.squares[sq(1, backRank)] == null &&
                !isSquareAttacked(board, sq(4, backRank), enemy) &&
                !isSquareAttacked(board, sq(3, backRank), enemy) &&
                !isSquareAttacked(board, sq(2, backRank), enemy)
            ) {
                out.add(Move(from, sq(2, backRank), isCastleQueenSide = true))
            }
        }
    }

    fun isSquareAttacked(board: Board, target: Int, byColor: PieceColor): Boolean =
        attacked(board.squares, target, byColor)

    private fun attacked(squares: Array<Piece?>, target: Int, byColor: PieceColor): Boolean {
        val tf = file(target); val tr = rank(target)
        val pawnDir = if (byColor == PieceColor.WHITE) -1 else 1
        for (df in PAWN_DF) {
            val f = tf + df; val r = tr + pawnDir
            if (inBounds(f, r)) {
                val p = squares[sq(f, r)]
                if (p != null && p.color == byColor && p.type == PieceType.PAWN) return true
            }
        }
        for (i in 0 until 8) {
            val f = tf + KNIGHT_DF[i]; val r = tr + KNIGHT_DR[i]
            if (inBounds(f, r)) {
                val p = squares[sq(f, r)]
                if (p != null && p.color == byColor && p.type == PieceType.KNIGHT) return true
            }
        }
        for (i in 0 until 8) {
            val f = tf + ALL_DF[i]; val r = tr + ALL_DR[i]
            if (inBounds(f, r)) {
                val p = squares[sq(f, r)]
                if (p != null && p.color == byColor && p.type == PieceType.KING) return true
            }
        }
        for (i in 0 until 4) {
            val df = DIAG_DF[i]; val dr = DIAG_DR[i]
            var f = tf + df; var r = tr + dr
            while (inBounds(f, r)) {
                val p = squares[sq(f, r)]
                if (p != null) {
                    if (p.color == byColor && (p.type == PieceType.BISHOP || p.type == PieceType.QUEEN)) return true
                    break
                }
                f += df; r += dr
            }
        }
        for (i in 0 until 4) {
            val df = ORTHO_DF[i]; val dr = ORTHO_DR[i]
            var f = tf + df; var r = tr + dr
            while (inBounds(f, r)) {
                val p = squares[sq(f, r)]
                if (p != null) {
                    if (p.color == byColor && (p.type == PieceType.ROOK || p.type == PieceType.QUEEN)) return true
                    break
                }
                f += df; r += dr
            }
        }
        return false
    }

    fun isInCheck(board: Board, color: PieceColor): Boolean {
        val kingSq = board.kingSquare(color)
        if (kingSq == -1) return false
        return isSquareAttacked(board, kingSq, color.opposite())
    }

    fun legalMoves(board: Board): List<Move> {
        val pseudo = pseudoLegalMoves(board)
        val side = board.sideToMove
        val enemy = side.opposite()
        val work = board.squares.copyOf()
        val ownKing = board.kingSquare(side)
        val result = ArrayList<Move>(pseudo.size)
        for (m in pseudo) {
            val moving = work[m.from] ?: continue
            val captured = work[m.to]

            var epIdx = -1
            var epPiece: Piece? = null
            if (m.isEnPassant) {
                val capturedRank = if (moving.color == PieceColor.WHITE) rank(m.to) - 1 else rank(m.to) + 1
                epIdx = sq(file(m.to), capturedRank)
                epPiece = work[epIdx]
                work[epIdx] = null
            }
            work[m.to] = if (m.promotion != null) Piece(m.promotion, moving.color) else moving
            work[m.from] = null

            var rookFrom = -1
            var rookTo = -1
            var rook: Piece? = null
            if (m.isCastleKingSide) {
                val backRank = rank(m.from); rookFrom = sq(7, backRank); rookTo = sq(5, backRank)
            } else if (m.isCastleQueenSide) {
                val backRank = rank(m.from); rookFrom = sq(0, backRank); rookTo = sq(3, backRank)
            }
            if (rookFrom >= 0) {
                rook = work[rookFrom]
                work[rookTo] = rook
                work[rookFrom] = null
            }

            val kingSq = if (moving.type == PieceType.KING) m.to else ownKing
            val safe = kingSq == -1 || !attacked(work, kingSq, enemy)

            if (rookFrom >= 0) {
                work[rookFrom] = rook
                work[rookTo] = null
            }
            work[m.from] = moving
            work[m.to] = captured
            if (epIdx >= 0) work[epIdx] = epPiece

            if (safe) result.add(m)
        }
        return result
    }

    fun legalMovesFrom(board: Board, from: Int): List<Move> = legalMoves(board).filter { it.from == from }

    fun applyMove(board: Board, move: Move): Board {
        val squares = board.deepCopySquares()
        val moving = squares[move.from] ?: return board
        val isPawn = moving.type == PieceType.PAWN
        val isCapture = squares[move.to] != null

        if (move.isEnPassant) {
            val capturedRank = if (moving.color == PieceColor.WHITE) rank(move.to) - 1 else rank(move.to) + 1
            squares[sq(file(move.to), capturedRank)] = null
        }

        squares[move.to] = if (move.promotion != null) Piece(move.promotion, moving.color) else moving
        squares[move.from] = null

        if (move.isCastleKingSide) {
            val backRank = rank(move.from)
            squares[sq(5, backRank)] = squares[sq(7, backRank)]
            squares[sq(7, backRank)] = null
        }
        if (move.isCastleQueenSide) {
            val backRank = rank(move.from)
            squares[sq(3, backRank)] = squares[sq(0, backRank)]
            squares[sq(0, backRank)] = null
        }

        var wk = board.whiteKingSideCastle
        var wq = board.whiteQueenSideCastle
        var bk = board.blackKingSideCastle
        var bq = board.blackQueenSideCastle
        if (moving.type == PieceType.KING) {
            if (moving.color == PieceColor.WHITE) { wk = false; wq = false } else { bk = false; bq = false }
        }
        if (move.from == sq(0, 0) || move.to == sq(0, 0)) wq = false
        if (move.from == sq(7, 0) || move.to == sq(7, 0)) wk = false
        if (move.from == sq(0, 7) || move.to == sq(0, 7)) bq = false
        if (move.from == sq(7, 7) || move.to == sq(7, 7)) bk = false

        val newEnPassant = if (isPawn && kotlin.math.abs(rank(move.to) - rank(move.from)) == 2) {
            sq(file(move.from), (rank(move.from) + rank(move.to)) / 2)
        } else null

        val newHalfmove = if (isPawn || isCapture) 0 else board.halfmoveClock + 1
        val newFullmove = if (board.sideToMove == PieceColor.BLACK) board.fullmoveNumber + 1 else board.fullmoveNumber

        return Board(
            squares = squares,
            sideToMove = board.sideToMove.opposite(),
            whiteKingSideCastle = wk,
            whiteQueenSideCastle = wq,
            blackKingSideCastle = bk,
            blackQueenSideCastle = bq,
            enPassantTarget = newEnPassant,
            halfmoveClock = newHalfmove,
            fullmoveNumber = newFullmove
        )
    }

    fun gameStatus(board: Board, positionHistory: List<String> = emptyList()): GameStatus {
        val moves = legalMoves(board)
        if (moves.isEmpty()) {
            return if (isInCheck(board, board.sideToMove)) GameStatus.CHECKMATE else GameStatus.STALEMATE
        }
        if (board.halfmoveClock >= 100) return GameStatus.DRAW_50_MOVES
        val key = Notation.positionKey(board)
        if (positionHistory.count { it == key } >= 3) return GameStatus.DRAW_REPETITION
        if (isDeadPosition(board)) return GameStatus.DRAW_MATERIAL
        return GameStatus.ONGOING
    }

    /**
     * Neither side can ever mate: kings alone, a single minor piece, or only bishops that all
     * stand on squares of one colour.
     */
    fun isDeadPosition(board: Board): Boolean {
        val minors = ArrayList<Int>(4)
        for (i in 0 until 64) {
            val p = board.squares[i] ?: continue
            when (p.type) {
                PieceType.KING -> Unit
                PieceType.BISHOP, PieceType.KNIGHT -> minors.add(i)
                else -> return false
            }
        }
        if (minors.size <= 1) return true
        if (minors.any { board.squares[it]?.type != PieceType.BISHOP }) return false
        val shade = (file(minors[0]) + rank(minors[0])) % 2
        return minors.all { (file(it) + rank(it)) % 2 == shade }
    }

    /**
     * Whether [color] has enough material to mate at all. A player whose flag falls loses only if
     * the opponent could still mate; otherwise the game is drawn.
     */
    fun canMate(board: Board, color: PieceColor): Boolean {
        var minors = 0
        for (p in board.squares) {
            if (p == null || p.color != color) continue
            when (p.type) {
                PieceType.KING -> Unit
                PieceType.BISHOP, PieceType.KNIGHT -> minors++
                else -> return true
            }
        }
        return minors >= 2
    }
}
