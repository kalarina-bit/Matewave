package cc.skysparkle.matewave.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import cc.skysparkle.matewave.engine.Board
import cc.skysparkle.matewave.engine.Move
import cc.skysparkle.matewave.engine.Piece
import cc.skysparkle.matewave.engine.PieceColor
import cc.skysparkle.matewave.engine.PieceType
import cc.skysparkle.matewave.settings.BoardAppearance
import cc.skysparkle.matewave.ui.theme.HighlightCheck
import cc.skysparkle.matewave.ui.theme.HighlightHint
import cc.skysparkle.matewave.ui.theme.HighlightLastMove
import cc.skysparkle.matewave.ui.theme.HighlightMove
import kotlin.math.floor
import kotlin.math.roundToInt

private object PieceImages {
    @Volatile private var cache: Array<ImageBitmap>? = null

    fun get(context: Context): Array<ImageBitmap> {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            val options = BitmapFactory.Options().apply { inScaled = false }
            val types = PieceType.values()
            val loaded = Array(PieceColor.values().size * types.size) { i ->
                val piece = Piece(types[i % types.size], PieceColor.values()[i / types.size])
                BitmapFactory.decodeResource(context.resources, piece.drawableRes(), options).asImageBitmap()
            }
            cache = loaded
            return loaded
        }
    }

    fun indexOf(piece: Piece): Int = piece.color.ordinal * PieceType.values().size + piece.type.ordinal
}

@Composable
fun ChessBoardView(
    board: Board,
    selectedSquare: Int?,
    legalTargets: List<Move>,
    kingInCheckSquare: Int?,
    flipped: Boolean,
    onSquareClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    lastMove: Move? = null,
    hintSquare: Int? = null,
    interactionEnabled: Boolean = true,
    /** A square briefly tinted to confirm or reject a move (puzzles). */
    flashSquare: Int? = null,
    flashColor: Color = Color.Transparent
) {
    val context = LocalContext.current
    val appearance = remember { BoardAppearance(context) }
    val theme = appearance.theme
    val showCoords = appearance.showCoordinates
    val imagePieces = appearance.useImagePieces
    val showLastMove = appearance.showLastMove
    val showCheck = appearance.showCheckHighlight

    val pieceImages = if (imagePieces) remember(context) { PieceImages.get(context) } else null

    val targetMask = remember(legalTargets) {
        var mask = 0L
        for (m in legalTargets) mask = mask or (1L shl m.to)
        mask
    }

    val coordPaint = remember {
        Paint().apply { isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD }
    }
    val glyphPaint = remember {
        Paint().apply { isAntiAlias = true; textAlign = Paint.Align.CENTER }
    }

    val currentOnSquareClick by rememberUpdatedState(onSquareClick)
    val currentBoard by rememberUpdatedState(board)
    val currentSelected by rememberUpdatedState(selectedSquare)
    val currentTargetMask by rememberUpdatedState(targetMask)

    var dragSquare by remember { mutableStateOf<Int?>(null) }
    var dragPos by remember { mutableStateOf<Offset?>(null) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(flipped, interactionEnabled) {
                if (interactionEnabled) {
                    detectTapGestures { tap ->
                        val cellPx = size.width / 8f
                        val col = floor(tap.x / cellPx).toInt().coerceIn(0, 7)
                        val row = floor(tap.y / cellPx).toInt().coerceIn(0, 7)
                        val displayRank = if (flipped) row else 7 - row
                        val displayFile = if (flipped) 7 - col else col
                        currentOnSquareClick(displayRank * 8 + displayFile)
                    }
                }
            }
            .pointerInput(flipped, interactionEnabled) {
                if (interactionEnabled) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { start ->
                            val src = squareAt(start.x, start.y, size.width.toFloat(), flipped)
                            val piece = if (src >= 0) currentBoard.pieceAt(src) else null

                            if (piece != null && piece.color == currentBoard.sideToMove) {
                                dragSquare = src
                                dragPos = start

                                if (currentSelected == src) cc.skysparkle.matewave.settings.Haptics.select()

                                if (currentSelected != src) currentOnSquareClick(src)
                            }
                        },
                        onDrag = { change, _ ->
                            if (dragSquare != null) dragPos = change.position
                        },
                        onDragEnd = {
                            val src = dragSquare
                            val pos = dragPos
                            dragSquare = null
                            dragPos = null
                            if (src != null && pos != null && currentSelected == src) {
                                val target = squareAt(pos.x, pos.y, size.width.toFloat(), flipped)
                                if (target >= 0 && target != src &&
                                    ((currentTargetMask shr target) and 1L) != 0L
                                ) {
                                    currentOnSquareClick(target)
                                }
                            }
                        },
                        onDragCancel = {
                            dragSquare = null
                            dragPos = null
                        }
                    )
                }
            }
    ) {
        val cell = size.width / 8f
        val lastFrom = if (showLastMove) (lastMove?.from ?: -1) else -1
        val lastTo = if (showLastMove) (lastMove?.to ?: -1) else -1
        val selected = selectedSquare ?: -1
        val checkSquare = if (showCheck) (kingInCheckSquare ?: -1) else -1
        val hint = hintSquare ?: -1
        val flash = flashSquare ?: -1

        val dSq = dragSquare
        val dPos = dragPos
        val liftedSq = if (dSq != null && dPos != null && selected == dSq) dSq else -1
        val liftedAt: Offset? = if (liftedSq >= 0) dPos else null

        for (row in 0 until 8) {
            for (col in 0 until 8) {
                val displayRank = if (flipped) row else 7 - row
                val displayFile = if (flipped) 7 - col else col
                val sqIndex = displayRank * 8 + displayFile
                val isLight = (displayRank + displayFile) % 2 == 1
                var color = if (isLight) theme.light else theme.dark
                if (sqIndex == lastFrom || sqIndex == lastTo) color = HighlightLastMove
                if (sqIndex == hint) color = HighlightHint
                if (sqIndex == selected) color = HighlightMove
                if (sqIndex == checkSquare) color = HighlightCheck
                drawRect(
                    color = color,
                    topLeft = Offset(col * cell, row * cell),
                    size = Size(cell, cell)
                )
                if (sqIndex == flash) {
                    drawRect(color = flashColor, topLeft = Offset(col * cell, row * cell), size = Size(cell, cell))
                }

                if (showCoords) {
                    val labelColor = if (isLight) theme.dark else theme.light
                    if (row == 7) {
                        drawCoordinate(
                            ('a' + displayFile).toString(),
                            Offset(col * cell + cell * 0.78f, row * cell + cell * 0.80f),
                            cell, labelColor, coordPaint
                        )
                    }
                    if (col == 0) {
                        drawCoordinate(
                            (displayRank + 1).toString(),
                            Offset(col * cell + cell * 0.10f, row * cell + cell * 0.22f),
                            cell, labelColor, coordPaint
                        )
                    }
                }
                if ((targetMask shr sqIndex) and 1L != 0L) {
                    drawCircle(
                        color = HighlightMove,
                        radius = cell * 0.14f,
                        center = Offset(col * cell + cell / 2, row * cell + cell / 2)
                    )
                }
            }
        }

        val pieceSize = cell * 0.86f
        val inset = (cell - pieceSize) / 2f
        val pieceDst = IntSize(pieceSize.roundToInt(), pieceSize.roundToInt())
        if (pieceImages == null) {
            glyphPaint.textSize = cell * 0.62f
            glyphPaint.color = GLYPH_COLOR
        }
        for (row in 0 until 8) {
            for (col in 0 until 8) {
                val displayRank = if (flipped) row else 7 - row
                val displayFile = if (flipped) 7 - col else col
                val pieceSq = displayRank * 8 + displayFile
                if (pieceSq == liftedSq) continue
                val piece = board.pieceAt(pieceSq) ?: continue
                if (pieceImages != null) {
                    drawImage(
                        image = pieceImages[PieceImages.indexOf(piece)],
                        dstOffset = IntOffset(
                            (col * cell + inset).roundToInt(),
                            (row * cell + inset).roundToInt()
                        ),
                        dstSize = pieceDst,
                        filterQuality = FilterQuality.Medium
                    )
                } else {
                    val metrics = glyphPaint.fontMetrics
                    drawContext.canvas.nativeCanvas.drawText(
                        piece.unicode(),
                        col * cell + cell / 2f,
                        row * cell + cell / 2f - (metrics.ascent + metrics.descent) / 2f,
                        glyphPaint
                    )
                }
            }
        }

        if (liftedAt != null) {
            val liftedPiece = board.pieceAt(liftedSq)
            if (liftedPiece != null) {
                val hover = squareAt(liftedAt.x, liftedAt.y, size.width, flipped)
                if (hover >= 0 && ((targetMask shr hover) and 1L) != 0L) {
                    val hRank = hover / 8
                    val hFile = hover % 8
                    val hRow = if (flipped) hRank else 7 - hRank
                    val hCol = if (flipped) 7 - hFile else hFile
                    drawRect(
                        color = Color.White.copy(alpha = 0.75f),
                        topLeft = Offset(hCol * cell, hRow * cell),
                        size = Size(cell, cell),
                        style = Stroke(width = cell * 0.07f)
                    )
                }
                val liftedSize = pieceSize * LIFT_SCALE
                val cx = liftedAt.x
                val cy = liftedAt.y - cell * 0.55f
                drawOval(
                    color = Color.Black.copy(alpha = 0.30f),
                    topLeft = Offset(liftedAt.x - liftedSize * 0.36f, liftedAt.y + cell * 0.05f),
                    size = Size(liftedSize * 0.72f, liftedSize * 0.28f)
                )
                if (pieceImages != null) {
                    val liftedDst = IntSize(liftedSize.roundToInt(), liftedSize.roundToInt())
                    drawImage(
                        image = pieceImages[PieceImages.indexOf(liftedPiece)],
                        dstOffset = IntOffset(
                            (cx - liftedSize / 2f).roundToInt(),
                            (cy - liftedSize / 2f).roundToInt()
                        ),
                        dstSize = liftedDst,
                        filterQuality = FilterQuality.Medium
                    )
                } else {
                    glyphPaint.textSize = cell * 0.62f * LIFT_SCALE
                    val metrics = glyphPaint.fontMetrics
                    drawContext.canvas.nativeCanvas.drawText(
                        liftedPiece.unicode(),
                        cx,
                        cy - (metrics.ascent + metrics.descent) / 2f,
                        glyphPaint
                    )
                }
            }
        }
    }
}

private const val LIFT_SCALE = 1.6f

private fun squareAt(x: Float, y: Float, boardPx: Float, flipped: Boolean): Int {
    if (x < 0f || y < 0f || x >= boardPx || y >= boardPx) return -1
    val cellPx = boardPx / 8f
    val col = floor(x / cellPx).toInt().coerceIn(0, 7)
    val row = floor(y / cellPx).toInt().coerceIn(0, 7)
    val displayRank = if (flipped) row else 7 - row
    val displayFile = if (flipped) 7 - col else col
    return displayRank * 8 + displayFile
}

private val GLYPH_COLOR = Color(0xFF1B1B1B).toArgb()

private fun DrawScope.drawCoordinate(
    text: String,
    position: Offset,
    cell: Float,
    color: Color,
    paint: Paint
) {
    paint.color = color.toArgb()
    paint.textSize = cell * 0.20f
    drawContext.canvas.nativeCanvas.drawText(text, position.x, position.y, paint)
}
