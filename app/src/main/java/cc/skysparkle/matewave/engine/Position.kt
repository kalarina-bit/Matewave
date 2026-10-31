package cc.skysparkle.matewave.engine

/**
 * Mutable position for move generation and search: pieces are int codes, moves are ints, and
 * [make]/[unmake] change one position in place instead of copying boards. [ChessEngine] wraps it
 * for the immutable [Board] API, so the rules exist once.
 *
 * Piece code: type (1 pawn .. 6 king) plus 8 for black, 0 for an empty square. Move: from in bits
 * 0-5, to in bits 6-11, promotion type in bits 12-14, then the castling and en passant flags.
 */
internal class Position(board: Board) {
    val squares = IntArray(64)
    var side = WHITE
        private set
    private var castling = 0
    private var enPassant = -1
    var halfmove = 0
        private set
    private var fullmove = 1

    /** King square per colour, -1 without a king. */
    private val kings = intArrayOf(-1, -1)

    private var undo = IntArray(UNDO_FIELDS * 16)
    private var undoDepth = 0

    init {
        for (sq in 0 until 64) {
            val piece = board.squares[sq] ?: continue
            val code = code(piece.type.ordinal + 1, piece.color.ordinal)
            squares[sq] = code
            if (piece.type == PieceType.KING && kings[piece.color.ordinal] == -1) kings[piece.color.ordinal] = sq
        }
        side = board.sideToMove.ordinal
        if (board.whiteKingSideCastle) castling = castling or CASTLE_WK
        if (board.whiteQueenSideCastle) castling = castling or CASTLE_WQ
        if (board.blackKingSideCastle) castling = castling or CASTLE_BK
        if (board.blackQueenSideCastle) castling = castling or CASTLE_BQ
        enPassant = board.enPassantTarget ?: -1
        halfmove = board.halfmoveClock
        fullmove = board.fullmoveNumber
    }

    fun toBoard(): Board {
        val pieces = arrayOfNulls<Piece>(64)
        for (sq in 0 until 64) {
            val code = squares[sq]
            if (code != 0) pieces[sq] = PIECES[code]
        }
        return Board(
            squares = pieces,
            sideToMove = if (side == WHITE) PieceColor.WHITE else PieceColor.BLACK,
            whiteKingSideCastle = (castling and CASTLE_WK) != 0,
            whiteQueenSideCastle = (castling and CASTLE_WQ) != 0,
            blackKingSideCastle = (castling and CASTLE_BK) != 0,
            blackQueenSideCastle = (castling and CASTLE_BQ) != 0,
            enPassantTarget = if (enPassant == -1) null else enPassant,
            halfmoveClock = halfmove,
            fullmoveNumber = fullmove
        )
    }

    /**
     * Writes the pseudo-legal moves of the side to move into [out] from [start] and returns the end
     * index; [out] needs room for [MAX_MOVES]. With [tacticalOnly] only captures, en passant and
     * promotions are written, in the same relative order.
     */
    fun generate(out: IntArray, start: Int, tacticalOnly: Boolean): Int {
        var n = start
        val us = side
        for (from in 0 until 64) {
            val piece = squares[from]
            if (piece == 0 || colorOf(piece) != us) continue
            n = when (typeOf(piece)) {
                PAWN -> pawnMoves(from, us, out, n, tacticalOnly)
                KNIGHT -> stepMoves(from, us, KNIGHT_TARGETS[from], out, n, tacticalOnly)
                BISHOP -> slidingMoves(from, us, 4, 8, out, n, tacticalOnly)
                ROOK -> slidingMoves(from, us, 0, 4, out, n, tacticalOnly)
                QUEEN -> slidingMoves(from, us, 0, 8, out, n, tacticalOnly)
                else -> kingMoves(from, us, out, n, tacticalOnly)
            }
        }
        return n
    }

    private fun pawnMoves(from: Int, us: Int, out: IntArray, start: Int, tacticalOnly: Boolean): Int {
        var n = start
        val file = from and 7
        val rank = from shr 3
        val toRank = if (us == WHITE) rank + 1 else rank - 1
        if (toRank !in 0..7) return n
        val promotes = toRank == (if (us == WHITE) 7 else 0)
        val one = toRank * 8 + file
        if (squares[one] == 0) {
            if (promotes) {
                n = promotions(from, one, out, n)
            } else if (!tacticalOnly) {
                out[n++] = moveCode(from, one)
                if (rank == (if (us == WHITE) 1 else 6)) {
                    val two = if (us == WHITE) one + 8 else one - 8
                    if (squares[two] == 0) out[n++] = moveCode(from, two)
                }
            }
        }
        if (file > 0) n = pawnCapture(from, one - 1, promotes, us, out, n)
        if (file < 7) n = pawnCapture(from, one + 1, promotes, us, out, n)
        return n
    }

    private fun pawnCapture(from: Int, to: Int, promotes: Boolean, us: Int, out: IntArray, start: Int): Int {
        var n = start
        val target = squares[to]
        if (target != 0 && colorOf(target) != us) {
            if (promotes) n = promotions(from, to, out, n) else out[n++] = moveCode(from, to)
        } else if (to == enPassant) {
            out[n++] = moveCode(from, to) or FLAG_EN_PASSANT
        }
        return n
    }

    private fun promotions(from: Int, to: Int, out: IntArray, start: Int): Int {
        var n = start
        val base = moveCode(from, to)
        out[n++] = base or (QUEEN shl 12)
        out[n++] = base or (ROOK shl 12)
        out[n++] = base or (BISHOP shl 12)
        out[n++] = base or (KNIGHT shl 12)
        return n
    }

    private fun stepMoves(from: Int, us: Int, targets: IntArray, out: IntArray, start: Int, tacticalOnly: Boolean): Int {
        var n = start
        for (to in targets) {
            val target = squares[to]
            if (target == 0) {
                if (!tacticalOnly) out[n++] = moveCode(from, to)
            } else if (colorOf(target) != us) {
                out[n++] = moveCode(from, to)
            }
        }
        return n
    }

    private fun slidingMoves(
        from: Int, us: Int, firstDir: Int, endDir: Int, out: IntArray, start: Int, tacticalOnly: Boolean
    ): Int {
        var n = start
        for (dir in firstDir until endDir) {
            val ray = dir * 64 + from
            val base = ray * 7
            for (i in 0 until RAY_LENGTH[ray]) {
                val to = RAYS[base + i]
                val target = squares[to]
                if (target == 0) {
                    if (!tacticalOnly) out[n++] = moveCode(from, to)
                } else {
                    if (colorOf(target) != us) out[n++] = moveCode(from, to)
                    break
                }
            }
        }
        return n
    }

    private fun kingMoves(from: Int, us: Int, out: IntArray, start: Int, tacticalOnly: Boolean): Int {
        var n = stepMoves(from, us, KING_TARGETS[from], out, start, tacticalOnly)
        if (tacticalOnly) return n
        val e = if (us == WHITE) 4 else 60
        if (from != e) return n
        val rook = code(ROOK, us)
        val them = us xor 1
        val kingSide = (castling and (if (us == WHITE) CASTLE_WK else CASTLE_BK)) != 0 && squares[e + 3] == rook
        val queenSide = (castling and (if (us == WHITE) CASTLE_WQ else CASTLE_BQ)) != 0 && squares[e - 4] == rook
        if (kingSide && squares[e + 1] == 0 && squares[e + 2] == 0 &&
            !isAttacked(e, them) && !isAttacked(e + 1, them) && !isAttacked(e + 2, them)
        ) {
            out[n++] = moveCode(from, e + 2) or FLAG_CASTLE_KING
        }
        if (queenSide && squares[e - 1] == 0 && squares[e - 2] == 0 && squares[e - 3] == 0 &&
            !isAttacked(e, them) && !isAttacked(e - 1, them) && !isAttacked(e - 2, them)
        ) {
            out[n++] = moveCode(from, e - 2) or FLAG_CASTLE_QUEEN
        }
        return n
    }

    /** Whether a piece of colour [by] attacks [target]. */
    fun isAttacked(target: Int, by: Int): Boolean {
        val file = target and 7
        // Pawns of [by] capture towards the target from one rank behind it.
        val pawnRank = (target shr 3) + (if (by == WHITE) -1 else 1)
        if (pawnRank in 0..7) {
            val pawn = code(PAWN, by)
            if (file > 0 && squares[pawnRank * 8 + file - 1] == pawn) return true
            if (file < 7 && squares[pawnRank * 8 + file + 1] == pawn) return true
        }
        val knight = code(KNIGHT, by)
        for (sq in KNIGHT_TARGETS[target]) if (squares[sq] == knight) return true
        val king = code(KING, by)
        for (sq in KING_TARGETS[target]) if (squares[sq] == king) return true
        val queen = code(QUEEN, by)
        val rook = code(ROOK, by)
        val bishop = code(BISHOP, by)
        for (dir in 0 until 8) {
            val slider = if (dir < 4) rook else bishop
            val ray = dir * 64 + target
            val base = ray * 7
            for (i in 0 until RAY_LENGTH[ray]) {
                val piece = squares[RAYS[base + i]]
                if (piece != 0) {
                    if (piece == queen || piece == slider) return true
                    break
                }
            }
        }
        return false
    }

    /** Whether the king of the side to move is attacked. */
    fun inCheck(): Boolean {
        val king = kings[side]
        return king != -1 && isAttacked(king, side xor 1)
    }

    /** After [make]: whether the side that moved kept its king out of check. */
    fun moverKingSafe(): Boolean {
        val king = kings[side xor 1]
        return king == -1 || !isAttacked(king, side)
    }

    fun isLegal(move: Int): Boolean {
        make(move)
        val safe = moverKingSafe()
        unmake(move)
        return safe
    }

    /** Plays [move] for the piece on its from-square, which must not be empty. */
    fun make(move: Int) {
        val from = move and 63
        val to = (move shr 6) and 63
        val promotion = (move shr 12) and 7
        val moving = squares[from]
        val color = colorOf(moving)
        val captured = squares[to]

        if ((undoDepth + 1) * UNDO_FIELDS > undo.size) undo = undo.copyOf(undo.size * 2)
        val u = undoDepth * UNDO_FIELDS
        undoDepth++
        undo[u] = moving
        undo[u + 1] = captured
        undo[u + 2] = 0
        undo[u + 3] = castling
        undo[u + 4] = enPassant
        undo[u + 5] = halfmove
        undo[u + 6] = fullmove
        undo[u + 7] = kings[WHITE]
        undo[u + 8] = kings[BLACK]

        if ((move and FLAG_EN_PASSANT) != 0) {
            val victim = if (color == WHITE) to - 8 else to + 8
            undo[u + 2] = squares[victim]
            squares[victim] = 0
        }
        squares[to] = if (promotion != 0) code(promotion, color) else moving
        squares[from] = 0
        val backRank = from and 56
        if ((move and FLAG_CASTLE_KING) != 0) {
            squares[backRank + 5] = squares[backRank + 7]
            squares[backRank + 7] = 0
        }
        if ((move and FLAG_CASTLE_QUEEN) != 0) {
            squares[backRank + 3] = squares[backRank]
            squares[backRank] = 0
        }

        val type = typeOf(moving)
        if (type == KING) {
            val rights = if (color == WHITE) CASTLE_WK or CASTLE_WQ else CASTLE_BK or CASTLE_BQ
            castling = castling and rights.inv()
            kings[color] = to
        }
        if (captured != 0 && typeOf(captured) == KING) kings[colorOf(captured)] = -1
        if (from == 0 || to == 0) castling = castling and CASTLE_WQ.inv()
        if (from == 7 || to == 7) castling = castling and CASTLE_WK.inv()
        if (from == 56 || to == 56) castling = castling and CASTLE_BQ.inv()
        if (from == 63 || to == 63) castling = castling and CASTLE_BK.inv()

        val fromRank = from shr 3
        val toRank = to shr 3
        enPassant = if (type == PAWN && (toRank - fromRank == 2 || fromRank - toRank == 2)) {
            (fromRank + toRank) / 2 * 8 + (from and 7)
        } else -1
        halfmove = if (type == PAWN || captured != 0) 0 else halfmove + 1
        if (side == BLACK) fullmove++
        side = side xor 1
    }

    /** Takes back [move], which must be the last one made and a move from [generate]. */
    fun unmake(move: Int) {
        undoDepth--
        val u = undoDepth * UNDO_FIELDS
        val from = move and 63
        val to = (move shr 6) and 63
        side = side xor 1
        squares[from] = undo[u]
        squares[to] = undo[u + 1]
        if ((move and FLAG_EN_PASSANT) != 0) squares[if (side == WHITE) to - 8 else to + 8] = undo[u + 2]
        val backRank = from and 56
        if ((move and FLAG_CASTLE_KING) != 0) {
            squares[backRank + 7] = squares[backRank + 5]
            squares[backRank + 5] = 0
        }
        if ((move and FLAG_CASTLE_QUEEN) != 0) {
            squares[backRank] = squares[backRank + 3]
            squares[backRank + 3] = 0
        }
        castling = undo[u + 3]
        enPassant = undo[u + 4]
        halfmove = undo[u + 5]
        fullmove = undo[u + 6]
        kings[WHITE] = undo[u + 7]
        kings[BLACK] = undo[u + 8]
    }

    /** See [ChessEngine.isDeadPosition]. */
    fun isDeadPosition(): Boolean {
        var minors = 0
        var knights = false
        var shade = -1
        var oneShade = true
        for (sq in 0 until 64) {
            val code = squares[sq]
            if (code == 0) continue
            when (typeOf(code)) {
                KING -> Unit
                KNIGHT -> { minors++; knights = true }
                BISHOP -> {
                    minors++
                    val s = ((sq and 7) + (sq shr 3)) and 1
                    if (shade == -1) shade = s else if (s != shade) oneShade = false
                }
                else -> return false
            }
        }
        return minors <= 1 || (!knights && oneShade)
    }

    /**
     * Hash of what [Notation.positionKey] covers: pieces, side to move, castling rights and the
     * en passant square. Equal keys give equal hashes; different keys can still collide.
     */
    fun keyHash(): Long {
        var h = if (side == BLACK) ZOBRIST_BLACK else 0L
        for (sq in 0 until 64) {
            val code = squares[sq]
            if (code != 0) h = h xor ZOBRIST[code * 64 + sq]
        }
        h = h xor ZOBRIST[CASTLING_KEYS + castling]
        if (enPassant in 0..63) h = h xor ZOBRIST[EN_PASSANT_KEYS + enPassant]
        return h
    }

    companion object {
        const val WHITE = 0
        const val BLACK = 1

        const val PAWN = 1
        const val KNIGHT = 2
        const val BISHOP = 3
        const val ROOK = 4
        const val QUEEN = 5
        const val KING = 6

        /** More pseudo-legal moves than any position can have. */
        const val MAX_MOVES = 512

        private const val FLAG_CASTLE_KING = 1 shl 15
        private const val FLAG_CASTLE_QUEEN = 1 shl 16
        private const val FLAG_EN_PASSANT = 1 shl 17

        private const val CASTLE_WK = 1
        private const val CASTLE_WQ = 2
        private const val CASTLE_BK = 4
        private const val CASTLE_BQ = 8

        private const val UNDO_FIELDS = 9

        fun code(type: Int, color: Int): Int = type or (color shl 3)
        fun typeOf(code: Int): Int = code and 7
        fun colorOf(code: Int): Int = code shr 3

        fun moveFrom(move: Int): Int = move and 63
        fun moveTo(move: Int): Int = (move shr 6) and 63
        fun promotionOf(move: Int): Int = (move shr 12) and 7
        fun isEnPassant(move: Int): Boolean = (move and FLAG_EN_PASSANT) != 0

        private fun moveCode(from: Int, to: Int): Int = from or (to shl 6)

        private val TYPES = PieceType.entries.toTypedArray()

        /** Shared [Piece] per code; pieces are immutable values. */
        private val PIECES = arrayOfNulls<Piece>(16).also {
            for (type in TYPES) for (color in PieceColor.entries) {
                it[code(type.ordinal + 1, color.ordinal)] = Piece(type, color)
            }
        }

        fun encode(move: Move): Int {
            var m = moveCode(move.from, move.to)
            if (move.promotion != null) m = m or ((move.promotion.ordinal + 1) shl 12)
            if (move.isCastleKingSide) m = m or FLAG_CASTLE_KING
            if (move.isCastleQueenSide) m = m or FLAG_CASTLE_QUEEN
            if (move.isEnPassant) m = m or FLAG_EN_PASSANT
            return m
        }

        fun toMove(move: Int): Move {
            val promotion = promotionOf(move)
            return Move(
                from = moveFrom(move),
                to = moveTo(move),
                promotion = if (promotion == 0) null else TYPES[promotion - 1],
                isCastleKingSide = (move and FLAG_CASTLE_KING) != 0,
                isCastleQueenSide = (move and FLAG_CASTLE_QUEEN) != 0,
                isEnPassant = (move and FLAG_EN_PASSANT) != 0
            )
        }

        /** Knight targets per square, in the order moves are generated. */
        private val KNIGHT_TARGETS = targets(intArrayOf(1, 2, 2, 1, -1, -2, -2, -1), intArrayOf(2, 1, -1, -2, -2, -1, 1, 2))

        /** King targets per square, file offset first, then rank offset. */
        private val KING_TARGETS = targets(intArrayOf(-1, -1, -1, 0, 0, 1, 1, 1), intArrayOf(-1, 0, 1, -1, 1, -1, 0, 1))

        private fun targets(df: IntArray, dr: IntArray): Array<IntArray> = Array(64) { sq ->
            val list = ArrayList<Int>(8)
            for (i in df.indices) {
                val f = (sq and 7) + df[i]
                val r = (sq shr 3) + dr[i]
                if (f in 0..7 && r in 0..7) list.add(r * 8 + f)
            }
            list.toIntArray()
        }

        /** Directions 0-3 are orthogonal, 4-7 diagonal, in the order moves are generated. */
        private val DIR_FILE = intArrayOf(1, -1, 0, 0, 1, 1, -1, -1)
        private val DIR_RANK = intArrayOf(0, 0, 1, -1, 1, -1, 1, -1)

        /** Squares along each direction from each square: 7 slots per ray (direction * 64 + square). */
        private val RAYS = IntArray(8 * 64 * 7)
        private val RAY_LENGTH = IntArray(8 * 64)

        init {
            for (dir in 0 until 8) for (sq in 0 until 64) {
                val ray = dir * 64 + sq
                var f = (sq and 7) + DIR_FILE[dir]
                var r = (sq shr 3) + DIR_RANK[dir]
                var length = 0
                while (f in 0..7 && r in 0..7) {
                    RAYS[ray * 7 + length++] = r * 8 + f
                    f += DIR_FILE[dir]
                    r += DIR_RANK[dir]
                }
                RAY_LENGTH[ray] = length
            }
        }

        private const val CASTLING_KEYS = 16 * 64
        private const val EN_PASSANT_KEYS = CASTLING_KEYS + 16

        /** Fixed pseudo-random keys (splitmix64): piece and square, castling rights, en passant. */
        private val ZOBRIST = LongArray(EN_PASSANT_KEYS + 64).also {
            var state = 0x2545F4914F6CDD1DL
            for (i in it.indices) {
                state += -0x61c8864680b583ebL
                var z = state
                z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
                z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
                it[i] = z xor (z ushr 31)
            }
        }
        private val ZOBRIST_BLACK = ZOBRIST[0]
    }
}
