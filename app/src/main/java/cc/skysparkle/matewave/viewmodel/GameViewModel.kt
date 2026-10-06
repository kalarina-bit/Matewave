package cc.skysparkle.matewave.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.skysparkle.matewave.ai.AiDifficulty
import cc.skysparkle.matewave.ai.ChessAI
import cc.skysparkle.matewave.audio.SoundManager
import cc.skysparkle.matewave.clock.ClockState
import cc.skysparkle.matewave.clock.GameClock
import cc.skysparkle.matewave.clock.TimeControl
import cc.skysparkle.matewave.data.FriendsStore
import cc.skysparkle.matewave.data.GameSaveStore
import cc.skysparkle.matewave.data.Profile
import cc.skysparkle.matewave.data.ProfileStore
import cc.skysparkle.matewave.data.SavedGame
import cc.skysparkle.matewave.engine.Board
import cc.skysparkle.matewave.engine.ChessEngine
import cc.skysparkle.matewave.engine.GameStatus
import cc.skysparkle.matewave.engine.Move
import cc.skysparkle.matewave.engine.Notation
import cc.skysparkle.matewave.engine.PieceColor
import cc.skysparkle.matewave.engine.PieceType
import cc.skysparkle.matewave.engine.opposite
import cc.skysparkle.matewave.network.ConnectionState
import cc.skysparkle.matewave.network.ConnectionTransport
import cc.skysparkle.matewave.network.GameMessage
import cc.skysparkle.matewave.network.NetworkLog
import cc.skysparkle.matewave.network.NetworkMetrics
import cc.skysparkle.matewave.network.TransportType
import cc.skysparkle.matewave.puzzles.Puzzle
import cc.skysparkle.matewave.security.DeviceKeys
import cc.skysparkle.matewave.security.KnownPeers
import cc.skysparkle.matewave.security.SessionAuth
import cc.skysparkle.matewave.security.TrustResult
import cc.skysparkle.matewave.settings.Haptics
import cc.skysparkle.matewave.stats.Stats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class HandshakeState { DISCONNECTED, TRANSPORT_CONNECTED, AUTHENTICATING, PLAYING }

enum class GameMode { VS_AI, PUZZLE, VS_PLAYER_NETWORK }

data class GameUiState(
    val board: Board = Board.startPosition(),
    val selectedSquare: Int? = null,
    val legalTargetsFromSelected: List<Move> = emptyList(),
    /** Position the game started from; [moveSans] are the moves played since. */
    val startBoard: Board = Board.startPosition(),
    /** SAN of every move played since [startBoard], for the move list and opening name. */
    val moveSans: List<String> = emptyList(),
    val status: GameStatus = GameStatus.ONGOING,
    val myColor: PieceColor = PieceColor.WHITE,
    val mode: GameMode = GameMode.VS_AI,

    val gameStarted: Boolean = false,
    val aiDifficulty: AiDifficulty = AiDifficulty.MEDIUM,
    val aiThinking: Boolean = false,
    val puzzle: Puzzle? = null,
    val puzzleMoveIndex: Int = 0,
    val puzzleSolved: Boolean = false,
    val puzzleFailedAttempt: Boolean = false,
    val puzzleStarted: Boolean = false,
    val puzzleAutoMoving: Boolean = false,
    val puzzleHintFrom: Int? = null,
    val puzzleSolutionRevealed: Boolean = false,
    val clockState: ClockState? = null,
    val timedOutSide: PieceColor? = null,
    val resignedSide: PieceColor? = null,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,

    val opponentUntrusted: Boolean = false,
    val opponentUnverified: Boolean = false,
    val opponentOutdated: Boolean = false,
    val handshake: HandshakeState = HandshakeState.DISCONNECTED,
    val newPeerFingerprint: String? = null,

    val lastMove: Move? = null,
    val pendingPromotion: PendingPromotion? = null,

    val chatMessages: List<ChatEntry> = emptyList(),
    val unreadChatCount: Int = 0,
    val drawOfferSentByMe: Boolean = false,
    val drawOfferFromOpponent: Boolean = false,

    val opponentProfile: Profile? = null,
    val opponentLeft: Boolean = false,
    val rematchOfferedByMe: Boolean = false,
    val opponentNotice: OpponentNotice? = null,
    val opponentLeftMidGame: Boolean = false,
    val rematchOfferFromOpponent: Boolean = false,

    val activeTransport: TransportType? = null
)

data class PendingPromotion(val from: Int, val to: Int)

data class ChatEntry(
    val text: String?,
    val imageBase64: String?,
    val fromMe: Boolean,
    val timestampMs: Long
)

enum class OpponentNotice { DRAW_DECLINED, REMATCH_DECLINED }

/** An unfinished game against the AI that can be continued from the home screen. */
data class ResumeInfo(val moveNumber: Int, val myTurn: Boolean, val myMillis: Long?)

/**
 * Game state for every mode: vs AI, puzzles and network play.
 *
 * Network flow: connect, exchange signed profiles with ephemeral ECDH keys, derive a
 * session secret, then send every game message encrypted and authenticated through
 * [SessionAuth]. Incoming messages are handled on the main thread in arrival order.
 */
class GameViewModel : ViewModel() {
    private var appContext: android.content.Context? = null
    private var knownPeers: KnownPeers? = null

    fun init(context: android.content.Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        knownPeers = KnownPeers(app)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { cc.skysparkle.matewave.openings.OpeningBook.load(app) }
        }
    }

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState

    private val _resumable = MutableStateFlow<ResumeInfo?>(null)

    /** The game against the AI that is saved and can be continued, or null. */
    val resumable: StateFlow<ResumeInfo?> = _resumable

    private var clock: GameClock? = null
    private var clockCollectorJob: Job? = null
    private var transport: ConnectionTransport? = null

    /** Position keys after every move, the start included, for threefold repetition. */
    private val fenHistory = mutableListOf<String>()

    /** UCI of every move since the start, saved so a resumed game keeps its move list. */
    private val moveLog = mutableListOf<String>()

    private var lastVsAiSettings: Triple<AiDifficulty, PieceColor, TimeControl>? = null
    private var lastVsAiRandomColor = false
    private var aiJob: Job? = null

    private var lastNetworkTimeControl: TimeControl? = null
    private var isNetworkHost: Boolean = false
    private var unsubscribeMessages: (() -> Unit)? = null
    private var heartbeat: cc.skysparkle.matewave.network.Heartbeat? = null
    private var connectionWatcher: Job? = null
    private var networkResultRecorded = false
    private var opponentFlagJob: Job? = null
    private var handshakeTimeoutJob: Job? = null
    private var pendingLeave: Pair<Job, ConnectionTransport>? = null
    private var sessionAuth: SessionAuth? = null
    private var ephemeralKeys: java.security.KeyPair? = null
    private var sessionGeneration = 0
    private var networkGameId: String = ""
    private var pendingOpponentImages: Pair<String?, String?>? = null
    private var currentPly: Int = 0
    private var lastOpponentRemaining: Long = Long.MAX_VALUE

    init {
        GameSaveStore.load()?.let { saved -> restoreSaved(saved) }
    }

    // region History

    private fun resetHistory(start: Board) {
        fenHistory.clear()
        moveLog.clear()
        fenHistory.add(Notation.positionKey(start))
    }

    /**
     * Rebuilds a saved game. The move list is replayed from the start when it was saved;
     * otherwise, or if it does not lead to the saved position, the bare position is used.
     */
    private fun restoreSaved(saved: SavedGame): Boolean {
        val savedBoard = runCatching { Board.fromFen(saved.fen) }.getOrNull() ?: return false
        val difficulty = AiDifficulty.entries.find { it.name == saved.difficultyName } ?: AiDifficulty.MEDIUM
        val myColor = if (saved.myColorWhite) PieceColor.WHITE else PieceColor.BLACK
        val timeControl = TimeControl("", saved.initialSeconds, saved.incrementSeconds)

        var start = Board.startPosition()
        var board = start
        val sans = ArrayList<String>()
        val ucis = ArrayList<String>()
        val keys = arrayListOf(Notation.positionKey(board))
        var lastMove: Move? = null
        var replayed = saved.movesUci.isNotBlank()
        if (replayed) {
            for (uci in saved.movesUci.trim().split(' ')) {
                val move = resolveUciAgainstLegalMoves(board, uci)
                if (move == null) { replayed = false; break }
                sans.add(Notation.toSan(board, move))
                board = ChessEngine.applyMove(board, move)
                ucis.add(move.toUci())
                keys.add(Notation.positionKey(board))
                lastMove = move
            }
            if (replayed && Notation.positionKey(board) != Notation.positionKey(savedBoard)) replayed = false
        }
        if (!replayed) {
            start = savedBoard
            board = savedBoard
            sans.clear(); ucis.clear(); keys.clear()
            keys.add(Notation.positionKey(board))
            lastMove = null
        }

        cancelAi()
        disposeClock()
        fenHistory.clear(); fenHistory.addAll(keys)
        moveLog.clear(); moveLog.addAll(ucis)
        lastVsAiSettings = Triple(difficulty, myColor, timeControl)
        _uiState.value = GameUiState(
            board = board,
            startBoard = start,
            moveSans = sans,
            mode = GameMode.VS_AI,
            myColor = myColor,
            aiDifficulty = difficulty,
            gameStarted = true,
            lastMove = lastMove
        )
        setupClock(
            timeControl,
            startSide = board.sideToMove,
            restoreWhiteMillis = saved.whiteMillis,
            restoreBlackMillis = saved.blackMillis
        )
        clock?.pause()
        _resumable.value = resumeInfoOf(_uiState.value)
        return true
    }

    private fun resumeInfoOf(state: GameUiState): ResumeInfo {
        val unlimited = lastVsAiSettings?.third?.initialSeconds == Int.MAX_VALUE
        val millis = if (unlimited) null else (clock?.state?.value ?: state.clockState)?.let {
            if (state.myColor == PieceColor.WHITE) it.whiteMillis else it.blackMillis
        }
        return ResumeInfo(state.board.fullmoveNumber, state.board.sideToMove == state.myColor, millis)
    }

    private fun persistIfResumable() {
        val state = _uiState.value
        if (state.mode != GameMode.VS_AI || state.status != GameStatus.ONGOING) return
        val clockState = clock?.state?.value
        GameSaveStore.save(
            SavedGame(
                fen = state.board.toFen(),
                myColorWhite = state.myColor == PieceColor.WHITE,
                difficultyName = state.aiDifficulty.name,
                initialSeconds = lastVsAiSettings?.third?.initialSeconds ?: TimeControl.RAPID_10_0.initialSeconds,
                incrementSeconds = lastVsAiSettings?.third?.incrementSeconds ?: 0,
                whiteMillis = clockState?.whiteMillis ?: -1,
                blackMillis = clockState?.blackMillis ?: -1,
                movesUci = if (Notation.isStandardStart(state.startBoard)) moveLog.joinToString(" ") else ""
            )
        )
        _resumable.value = resumeInfoOf(state)
    }

    private fun clearSavedGame() {
        GameSaveStore.clear()
        _resumable.value = null
    }

    // endregion

    // region Moves

    fun onSquareClicked(square: Int) {
        val state = _uiState.value
        if (state.status != GameStatus.ONGOING) return
        if (state.pendingPromotion != null) return
        if (state.mode == GameMode.PUZZLE && (!state.puzzleStarted || state.puzzleAutoMoving)) return
        if (state.board.sideToMove != state.myColor) return
        if (state.mode == GameMode.VS_PLAYER_NETWORK && !canSendGameMessages()) return

        val selected = state.selectedSquare
        if (selected == null) {
            val legal = ChessEngine.legalMovesFrom(state.board, square)
            if (legal.isNotEmpty()) {
                SoundManager.playSelect()
                Haptics.select()
                _uiState.value = state.copy(selectedSquare = square, legalTargetsFromSelected = legal)
            }
            return
        }

        if (selected == square) {
            _uiState.value = state.copy(selectedSquare = null, legalTargetsFromSelected = emptyList())
            return
        }

        val chosen = state.legalTargetsFromSelected.filter { it.to == square }
        if (chosen.isEmpty()) {
            val legal = ChessEngine.legalMovesFrom(state.board, square)
            if (legal.isNotEmpty()) {
                SoundManager.playSelect()
                Haptics.select()
            }
            _uiState.value = state.copy(
                selectedSquare = if (legal.isNotEmpty()) square else null,
                legalTargetsFromSelected = legal
            )
            return
        }

        if (chosen.size > 1) {
            _uiState.value = state.copy(
                pendingPromotion = PendingPromotion(from = selected, to = square),
                selectedSquare = null,
                legalTargetsFromSelected = emptyList()
            )
            return
        }

        makeMove(chosen.first())
    }

    fun choosePromotion(pieceType: PieceType) {
        val state = _uiState.value
        val pending = state.pendingPromotion ?: return
        val move = ChessEngine.legalMovesFrom(state.board, pending.from)
            .find { it.to == pending.to && it.promotion == pieceType }
        _uiState.value = state.copy(pendingPromotion = null)
        if (move != null) makeMove(move)
    }

    fun cancelPromotion() {
        _uiState.value = _uiState.value.copy(pendingPromotion = null)
    }

    private fun makeMove(move: Move, sendToNetwork: Boolean = true) {
        val state = _uiState.value
        val movedSide = state.board.sideToMove
        val isCapture = state.board.squares[move.to] != null || move.isEnPassant
        val san = Notation.toSan(state.board, move)
        val newBoard = ChessEngine.applyMove(state.board, move)
        fenHistory.add(Notation.positionKey(newBoard))
        moveLog.add(move.toUci())
        val status = ChessEngine.gameStatus(newBoard, fenHistory)
        if (isCapture) SoundManager.playCapture() else SoundManager.playMove()

        when {
            status != GameStatus.ONGOING || ChessEngine.isInCheck(newBoard, newBoard.sideToMove) -> Haptics.alert()
            isCapture -> Haptics.capture()
            else -> Haptics.move()
        }

        _uiState.value = state.copy(
            board = newBoard,
            selectedSquare = null,
            legalTargetsFromSelected = emptyList(),
            moveSans = state.moveSans + san,
            status = status,
            lastMove = move
        )

        if (status == GameStatus.ONGOING) clock?.onMoveMade(movedSide) else clock?.stop()

        val result = resultFor(status, newBoard, state.myColor)
        when (state.mode) {
            GameMode.VS_AI -> {
                if (result != null) recordAiResult(state.aiDifficulty, result)
                if (status == GameStatus.ONGOING) {
                    persistIfResumable()
                    triggerAiMoveIfNeeded()
                } else {
                    clearSavedGame()
                }
            }
            GameMode.PUZZLE -> handlePuzzleMove(move, state, status)
            GameMode.VS_PLAYER_NETWORK -> {
                if (sendToNetwork) {
                    val remaining = clock?.state?.value?.let {
                        if (movedSide == PieceColor.WHITE) it.whiteMillis else it.blackMillis
                    } ?: 0L
                    currentPly += 1
                    sendGameMessage(GameMessage.MoveMsg(move.toUci(), remaining, networkGameId, currentPly))
                }
                if (result != null) applyNetworkResultElo(result)
            }
        }
    }

    /** My score for a finished game (1 win, 0.5 draw, 0 loss), or null while it goes on. */
    private fun resultFor(status: GameStatus, board: Board, myColor: PieceColor): Double? = when (status) {
        GameStatus.CHECKMATE -> if (board.sideToMove != myColor) 1.0 else 0.0
        GameStatus.STALEMATE, GameStatus.DRAW_50_MOVES, GameStatus.DRAW_REPETITION,
        GameStatus.DRAW_MATERIAL -> 0.5
        else -> null
    }

    private fun recordAiResult(difficulty: AiDifficulty, result: Double) {
        Stats.recordResult(result)
        ProfileStore.applyEloDelta(ProfileStore.aiNominalElo(difficulty), result)
    }

    private fun resolveUciAgainstLegalMoves(board: Board, uci: String): Move? {
        val candidate = runCatching { Move.fromUci(uci) }.getOrNull() ?: return null
        val matches = ChessEngine.legalMoves(board).filter { it.from == candidate.from && it.to == candidate.to }
        return matches.firstOrNull { it.promotion == candidate.promotion } ?: matches.firstOrNull()
    }

    // endregion

    // region Against the AI

    fun startVsAi(
        difficulty: AiDifficulty,
        myColor: PieceColor,
        timeControl: TimeControl,
        randomColor: Boolean = false
    ) {
        lastVsAiRandomColor = randomColor
        cancelAi()
        clearSavedGame()
        val start = Board.startPosition()
        resetHistory(start)
        lastVsAiSettings = Triple(difficulty, myColor, timeControl)
        _uiState.value = GameUiState(
            board = start,
            startBoard = start,
            mode = GameMode.VS_AI,
            aiDifficulty = difficulty,
            myColor = myColor,
            gameStarted = true
        )
        setupClock(timeControl)
        persistIfResumable()
        if (myColor == PieceColor.BLACK) triggerAiMoveIfNeeded()
    }

    fun rematchVsAi() {
        val settings = lastVsAiSettings ?: return
        val color = if (lastVsAiRandomColor) {
            listOf(PieceColor.WHITE, PieceColor.BLACK).random()
        } else settings.second
        startVsAi(settings.first, color, settings.third, randomColor = lastVsAiRandomColor)
    }

    /** Continues the saved game against the AI, reloading it if another mode was played meanwhile. */
    fun resumeVsAi(): Boolean {
        val current = _uiState.value
        if (current.mode != GameMode.VS_AI || current.status != GameStatus.ONGOING) {
            val saved = GameSaveStore.load() ?: return false
            if (!restoreSaved(saved)) return false
        }
        val state = _uiState.value
        clock?.start(state.board.sideToMove)
        triggerAiMoveIfNeeded()
        return true
    }

    private fun cancelAi() {
        aiJob?.cancel()
        aiJob = null
        if (_uiState.value.aiThinking) _uiState.value = _uiState.value.copy(aiThinking = false)
    }

    private fun triggerAiMoveIfNeeded() {
        val state = _uiState.value
        if (state.mode != GameMode.VS_AI || state.status != GameStatus.ONGOING) return
        if (state.board.sideToMove == state.myColor) return
        if (aiJob?.isActive == true) return
        _uiState.value = state.copy(aiThinking = true)
        val boardAtStart = state.board
        val history = fenHistory.toSet()
        val aiColor = boardAtStart.sideToMove
        val clockMillis = clock?.state?.value?.let {
            if (aiColor == PieceColor.WHITE) it.whiteMillis else it.blackMillis
        }
        val incrementMillis = (lastVsAiSettings?.third?.incrementSeconds ?: 0) * 1000L
        aiJob = viewModelScope.launch {
            val move = withContext(Dispatchers.Default) {
                ChessAI.bestMove(boardAtStart, state.aiDifficulty, history, clockMillis, incrementMillis)
            }
            val now = _uiState.value
            if (now.board !== boardAtStart || now.mode != GameMode.VS_AI || now.status != GameStatus.ONGOING) {
                if (now.aiThinking) _uiState.value = now.copy(aiThinking = false)
                return@launch
            }
            _uiState.value = now.copy(aiThinking = false)
            if (move != null) makeMove(move)
        }
    }

    // endregion

    // region Puzzles

    fun startPuzzle(puzzle: Puzzle) {
        cancelAi()
        disposeClock()
        val board = runCatching { Board.fromFen(puzzle.fen) }.getOrNull() ?: return
        resetHistory(board)
        _uiState.value = GameUiState(
            board = board,
            startBoard = board,
            mode = GameMode.PUZZLE,
            myColor = board.sideToMove,
            puzzle = puzzle,
            puzzleMoveIndex = 0,
            puzzleStarted = true,
            gameStarted = true
        )
    }

    private fun handlePuzzleMove(move: Move, before: GameUiState, statusAfter: GameStatus) {
        val state = _uiState.value
        val puzzle = state.puzzle ?: return
        val expected = puzzle.solutionUci.getOrNull(state.puzzleMoveIndex)
        val playedUci = move.toUci()
        val isLastMove = state.puzzleMoveIndex == puzzle.solutionUci.size - 1

        val matches = (expected != null && (playedUci == expected || playedUci.dropLast(1) == expected)) ||
            (isLastMove && statusAfter == GameStatus.CHECKMATE)
        if (!matches) {
            _uiState.value = state.copy(puzzleFailedAttempt = true, puzzleAutoMoving = true)
            val puzzleId = puzzle.id
            val index = state.puzzleMoveIndex
            val boardAfterWrong = state.board
            viewModelScope.launch {
                delay(600)
                val now = _uiState.value
                if (now.mode != GameMode.PUZZLE || now.puzzle?.id != puzzleId ||
                    now.puzzleMoveIndex != index || now.board !== boardAfterWrong
                ) return@launch
                fenHistory.removeLastOrNull()
                moveLog.removeLastOrNull()
                _uiState.value = now.copy(
                    board = before.board,
                    moveSans = before.moveSans,
                    lastMove = before.lastMove,
                    status = GameStatus.ONGOING,
                    puzzleAutoMoving = false
                )
            }
            return
        }
        val nextIndex = state.puzzleMoveIndex + 1
        if (nextIndex >= puzzle.solutionUci.size) {
            Stats.markPuzzleSolved(puzzle.id)
            _uiState.value = _uiState.value.copy(
                puzzleSolved = true,
                puzzleFailedAttempt = false,
                puzzleHintFrom = null
            )
            return
        }

        val replyMove = resolveUciAgainstLegalMoves(_uiState.value.board, puzzle.solutionUci[nextIndex])
        _uiState.value = _uiState.value.copy(
            puzzleMoveIndex = nextIndex + 1,
            puzzleFailedAttempt = false,
            puzzleAutoMoving = replyMove != null,
            puzzleHintFrom = null
        )
        if (replyMove != null) {
            val puzzleId = puzzle.id
            val expectedIndex = nextIndex + 1
            val boardBeforeReply = _uiState.value.board
            viewModelScope.launch {
                delay(500)
                val now = _uiState.value
                if (now.mode != GameMode.PUZZLE || now.puzzle?.id != puzzleId ||
                    now.puzzleMoveIndex != expectedIndex || now.board !== boardBeforeReply
                ) return@launch
                val wasCapture = now.board.squares[replyMove.to] != null || replyMove.isEnPassant
                val san = Notation.toSan(now.board, replyMove)
                val newBoard = ChessEngine.applyMove(now.board, replyMove)
                fenHistory.add(Notation.positionKey(newBoard))
                moveLog.add(replyMove.toUci())
                if (wasCapture) SoundManager.playCapture() else SoundManager.playMove()
                if (wasCapture) Haptics.capture() else Haptics.move()
                _uiState.value = now.copy(
                    board = newBoard,
                    moveSans = now.moveSans + san,
                    lastMove = replyMove,
                    puzzleAutoMoving = false
                )
            }
        }
    }

    fun showPuzzleHint() {
        val state = _uiState.value
        if (state.mode != GameMode.PUZZLE || !state.puzzleStarted || state.puzzleAutoMoving) return
        if (state.puzzleSolved) return
        val puzzle = state.puzzle ?: return
        val expected = puzzle.solutionUci.getOrNull(state.puzzleMoveIndex) ?: return
        val from = runCatching { Move.fromUci(expected).from }.getOrNull() ?: return
        _uiState.value = state.copy(puzzleHintFrom = from)
    }

    fun revealPuzzleSolution() {
        val state = _uiState.value
        if (state.mode != GameMode.PUZZLE) return
        _uiState.value = state.copy(puzzleSolutionRevealed = true)
    }

    // endregion

    // region Network

    private fun outgoingDirection() = if (isNetworkHost) "H" else "G"
    private fun incomingDirection() = if (isNetworkHost) "G" else "H"

    private fun canSendGameMessages(): Boolean {
        val state = _uiState.value
        return sessionAuth != null &&
            transport != null &&
            state.connectionState == ConnectionState.CONNECTED &&
            state.handshake == HandshakeState.PLAYING
    }

    private fun sendGameMessage(message: GameMessage): Boolean {
        val auth = sessionAuth
        val transport = this.transport ?: return false
        if (auth == null) {
            NetworkLog.log("GAME", "Send cancelled: session not established")
            return false
        }
        val encrypted = auth.encrypt(message.toJson())
        if (encrypted == null) {
            NetworkLog.log("GAME", "Send cancelled: encryption failed")
            return false
        }
        val seq = auth.nextSeq()
        val tag = auth.tag("${outgoingDirection()}|$seq|$encrypted")
        if (tag == null) {
            NetworkLog.log("GAME", "Send cancelled: could not sign message")
            return false
        }
        transport.send(GameMessage.SignedMsg(encrypted, tag, seq))
        return true
    }

    private fun markReadyIfPossible() {
        if (sessionAuth == null) return
        if (_uiState.value.handshake == HandshakeState.PLAYING) return
        _uiState.value = _uiState.value.copy(
            handshake = HandshakeState.PLAYING,
            gameStarted = true
        )
        handshakeTimeoutJob?.cancel()
        handshakeTimeoutJob = null
        lastNetworkTimeControl?.let { setupClock(it) }

        val myProfile = ProfileStore.get()
        myProfile.avatarBase64?.let {
            sendGameMessage(GameMessage.ProfileImagesMsg(avatarBase64 = it, gameId = networkGameId))
        }
        // Backgrounds are large; over Bluetooth they would hold up the game for seconds.
        if (transport?.type != TransportType.BLUETOOTH) {
            myProfile.backgroundBase64?.let {
                sendGameMessage(GameMessage.ProfileImagesMsg(backgroundBase64 = it, gameId = networkGameId))
            }
        }
    }

    fun startVsPlayerNetwork(
        transport: ConnectionTransport,
        myColor: PieceColor,
        timeControl: TimeControl,
        isHost: Boolean,
        targetId: String? = null
    ) {
        pendingLeave?.let { (job, oldTransport) ->
            job.cancel()
            oldTransport.disconnect()
        }
        pendingLeave = null
        handshakeTimeoutJob?.cancel()
        handshakeTimeoutJob = null
        cancelAi()
        // A paused game against the AI stays saved and can be continued later.
        persistIfResumable()
        disposeClock()
        val start = Board.startPosition()
        resetHistory(start)
        this.transport = transport
        lastNetworkTimeControl = timeControl
        isNetworkHost = isHost

        ephemeralKeys = DeviceKeys.generateEphemeral()

        sessionGeneration += 1
        networkResultRecorded = false
        opponentFlagJob?.cancel()
        opponentFlagJob = null
        networkGameId = if (isHost) java.util.UUID.randomUUID().toString() else ""
        currentPly = 0
        lastOpponentRemaining = Long.MAX_VALUE

        _uiState.value = GameUiState(
            board = start,
            startBoard = start,
            mode = GameMode.VS_PLAYER_NETWORK,
            myColor = myColor,
            gameStarted = false,
            connectionState = ConnectionState.CONNECTING,
            activeTransport = transport.type
        )

        cc.skysparkle.matewave.network.Transports.releaseForGame()

        unsubscribeMessages?.invoke()
        unsubscribeMessages = transport.addMessageListener { msg ->
            viewModelScope.launch(Dispatchers.Main) { handleNetworkMessage(msg) }
        }

        heartbeat?.stop()
        heartbeat = cc.skysparkle.matewave.network.Heartbeat(viewModelScope, transport).also { it.start() }

        var profileSent = false
        var connectedOnce = false
        val connectStartedAt = System.currentTimeMillis()
        pendingOpponentImages = null
        NetworkMetrics.connectStarted()

        if (isHost) {
            transport.startHosting()
        } else {
            transport.startDiscoveryAndConnect(targetId)
        }

        connectionWatcher?.cancel()
        connectionWatcher = viewModelScope.launch {
            transport.connectionState.collect { connState ->
                _uiState.value = _uiState.value.copy(
                    connectionState = connState,
                    activeTransport = transport.type
                )

                if (connState == ConnectionState.CONNECTED && !connectedOnce) {
                    connectedOnce = true
                    NetworkMetrics.connectSucceeded(System.currentTimeMillis() - connectStartedAt)
                }

                if (connState == ConnectionState.CONNECTED && !profileSent) {
                    handshakeTimeoutJob?.cancel()
                    handshakeTimeoutJob = viewModelScope.launch {
                        delay(HANDSHAKE_TIMEOUT_MS)
                        if (_uiState.value.handshake != HandshakeState.PLAYING) {
                            NetworkLog.log("GAME", "Handshake not completed in ${HANDSHAKE_TIMEOUT_MS / 1000} s, disconnected")
                            transport.disconnect()
                        }
                    }
                    profileSent = true
                    sendProfile(transport)
                    _uiState.value = _uiState.value.copy(handshake = HandshakeState.AUTHENTICATING)
                }

                _uiState.value = _uiState.value.copy(
                    handshake = when (connState) {
                        ConnectionState.CONNECTED ->
                            if (sessionAuth != null) HandshakeState.PLAYING
                            else HandshakeState.TRANSPORT_CONNECTED
                        else -> HandshakeState.DISCONNECTED
                    }
                )
                if (connState != ConnectionState.CONNECTED) {
                    handshakeTimeoutJob?.cancel()
                    handshakeTimeoutJob = null
                    profileSent = false
                    sessionAuth = null
                    sessionGeneration += 1
                    ephemeralKeys = DeviceKeys.generateEphemeral()
                }

                if (connState == ConnectionState.DISCONNECTED || connState == ConnectionState.FAILED) {
                    clock?.stop()
                    endNetworkSession()
                }
            }
        }
    }

    private fun sendProfile(transport: ConnectionTransport) {
        val profile = ProfileStore.get()
        val ctx = appContext
        val myEphemeral = ephemeralKeys?.let { DeviceKeys.encodePublic(it) } ?: ""
        val signedPayload = knownPeers?.profilePayload(
            profile.userId, profile.name, profile.elo, null, null, myEphemeral
        )
        transport.send(
            GameMessage.ProfileMsg(
                profile.userId, profile.name, null, null, profile.elo,
                publicKey = if (ctx != null) DeviceKeys.publicKey(ctx) ?: "" else "",
                initialSeconds = if (isNetworkHost) lastNetworkTimeControl?.initialSeconds ?: 0 else 0,
                incrementSeconds = if (isNetworkHost) lastNetworkTimeControl?.incrementSeconds ?: 0 else 0,
                ephemeralKey = myEphemeral,
                gameId = if (isNetworkHost) networkGameId else "",
                signature = if (ctx != null && signedPayload != null) DeviceKeys.sign(ctx, signedPayload) ?: "" else ""
            )
        )
    }

    private fun belongsToCurrentGame(gameId: String): Boolean =
        gameId.isEmpty() || networkGameId.isEmpty() || gameId == networkGameId

    private fun requiresSignature(msg: GameMessage): Boolean = when (msg) {
        is GameMessage.ProfileMsg, is GameMessage.PingMsg, is GameMessage.SignedMsg -> false
        else -> true
    }

    private fun handleNetworkMessage(msg: GameMessage, verified: Boolean = false) {
        if (!verified && requiresSignature(msg)) {
            NetworkLog.log("GAME", "Unauthenticated message rejected: ${msg::class.simpleName}")
            return
        }
        when (msg) {
            is GameMessage.MoveMsg -> onOpponentMove(msg)
            is GameMessage.ResignMsg -> {
                if (!belongsToCurrentGame(msg.gameId)) return
                val state = _uiState.value
                if (state.status == GameStatus.ONGOING) {
                    clock?.stop()
                    _uiState.value = state.copy(status = GameStatus.RESIGNATION, resignedSide = state.myColor.opposite())
                    applyNetworkResultElo(1.0)
                }
            }
            is GameMessage.DrawOfferMsg -> {
                if (!belongsToCurrentGame(msg.gameId)) return
                val state = _uiState.value
                if (state.status != GameStatus.ONGOING) return
                when (msg.accept) {
                    null -> _uiState.value = state.copy(drawOfferFromOpponent = true)
                    true -> {
                        if (!state.drawOfferSentByMe) return
                        clock?.stop()
                        _uiState.value = state.copy(status = GameStatus.DRAW_AGREEMENT, drawOfferSentByMe = false)
                        applyNetworkResultElo(0.5)
                    }
                    false -> {
                        if (!state.drawOfferSentByMe) return
                        _uiState.value = state.copy(
                            drawOfferSentByMe = false,
                            opponentNotice = OpponentNotice.DRAW_DECLINED
                        )
                    }
                }
            }
            is GameMessage.ChatMsg -> {
                if (!belongsToCurrentGame(msg.gameId)) return
                if ((msg.text?.length ?: 0) > MAX_CHAT_TEXT_CHARS ||
                    (msg.imageBase64?.length ?: 0) > MAX_CHAT_IMAGE_CHARS
                ) {
                    NetworkLog.log("GAME", "Oversized chat message rejected")
                    return
                }
                val state = _uiState.value
                _uiState.value = state.copy(
                    chatMessages = state.chatMessages + ChatEntry(msg.text, msg.imageBase64, fromMe = false, System.currentTimeMillis()),
                    unreadChatCount = state.unreadChatCount + 1
                )
            }
            is GameMessage.ProfileMsg -> onOpponentProfile(msg)
            is GameMessage.LeaveMsg -> {
                if (!belongsToCurrentGame(msg.gameId)) return
                val before = _uiState.value
                if (before.status == GameStatus.ONGOING && before.handshake == HandshakeState.PLAYING) {
                    applyNetworkResultElo(1.0)
                }
                opponentFlagJob?.cancel()
                val midGame = before.status == GameStatus.ONGOING
                _uiState.value = _uiState.value.copy(
                    opponentLeft = true,
                    opponentLeftMidGame = midGame,
                    status = if (midGame) GameStatus.RESIGNATION else before.status,
                    resignedSide = if (midGame) before.myColor.opposite() else before.resignedSide,
                    rematchOfferedByMe = false,
                    rematchOfferFromOpponent = false,
                    drawOfferSentByMe = false,
                    drawOfferFromOpponent = false
                )
                clock?.stop()
            }
            is GameMessage.RematchMsg -> {
                if (msg.gameId.isNotEmpty() && networkGameId.isNotEmpty() && msg.gameId != networkGameId) return
                val state = _uiState.value
                if (state.status == GameStatus.ONGOING && !state.opponentLeft) return
                when (msg.action) {
                    "offer" -> {
                        if (state.rematchOfferedByMe) {
                            // Both offered at once: the host starts the new game.
                            if (isNetworkHost) acceptRematchInternal()
                        } else {
                            _uiState.value = state.copy(rematchOfferFromOpponent = true)
                        }
                    }
                    "decline" -> _uiState.value = state.copy(
                        rematchOfferedByMe = false,
                        rematchOfferFromOpponent = false,
                        opponentNotice = if (state.rematchOfferedByMe) OpponentNotice.REMATCH_DECLINED else state.opponentNotice
                    )
                    "accept" -> {
                        if (!state.rematchOfferedByMe || msg.newGameId.isEmpty()) return
                        startRematch(newGameId = msg.newGameId, iAmWhiteNow = state.myColor == PieceColor.BLACK)
                    }
                }
            }
            is GameMessage.SignedMsg -> {
                val auth = sessionAuth
                if (auth == null) {
                    NetworkLog.log("GAME", "Signed envelope before session setup rejected")
                    return
                }
                if (!auth.verify("${incomingDirection()}|${msg.seq}|${msg.inner}", msg.tag)) {
                    NetworkLog.log("GAME", "Message with invalid authentication tag rejected")
                    _uiState.value = _uiState.value.copy(opponentUntrusted = true)
                    return
                }
                if (!auth.acceptSeq(msg.seq)) {
                    NetworkLog.log("GAME", "Replay of message #${msg.seq} rejected")
                    return
                }
                val decrypted = auth.decrypt(msg.inner)
                if (decrypted == null) {
                    NetworkLog.log("GAME", "Could not decrypt message, discarded")
                    return
                }
                runCatching { handleNetworkMessage(GameMessage.fromJson(decrypted), verified = true) }
            }
            is GameMessage.ProfileImagesMsg -> {
                if (!belongsToCurrentGame(msg.gameId)) return
                val avatar = msg.avatarBase64?.takeIf { it.length <= MAX_CHAT_IMAGE_CHARS }
                val background = msg.backgroundBase64?.takeIf { it.length <= MAX_CHAT_IMAGE_CHARS }
                if (avatar == null && background == null) return
                val current = _uiState.value.opponentProfile
                if (current == null) {
                    val prev = pendingOpponentImages
                    pendingOpponentImages = Pair(avatar ?: prev?.first, background ?: prev?.second)
                    return
                }
                val saved = FriendsStore.mergeOpponent(
                    userId = current.userId,
                    name = current.name,
                    elo = current.elo,
                    avatarBase64 = avatar,
                    backgroundBase64 = background
                )
                _uiState.value = _uiState.value.copy(
                    opponentProfile = current.copy(
                        avatarBase64 = saved.avatarBase64,
                        backgroundBase64 = saved.backgroundBase64
                    )
                )
            }
            is GameMessage.PingMsg -> Unit
            is GameMessage.TimeoutMsg -> {
                if (!belongsToCurrentGame(msg.gameId)) return
                val state = _uiState.value
                if (state.status != GameStatus.ONGOING) return
                val flagged = if (msg.flaggedWhite) PieceColor.WHITE else PieceColor.BLACK
                if (flagged != state.myColor) {
                    declareNetworkTimeout(flagged, sendClaim = false)
                } else {
                    val mine = clock?.state?.value?.let {
                        if (state.myColor == PieceColor.WHITE) it.whiteMillis else it.blackMillis
                    } ?: 0L
                    if (mine <= CLOCK_TOLERANCE_MS) {
                        declareNetworkTimeout(flagged, sendClaim = false)
                    } else {
                        NetworkLog.log("GAME", "Rejected flag claim: our clock shows $mine ms left")
                    }
                }
            }
        }
    }

    private fun onOpponentMove(msg: GameMessage.MoveMsg) {
        val state = _uiState.value
        if (state.status != GameStatus.ONGOING) return

        if (msg.gameId.isNotEmpty()) {
            if (networkGameId.isEmpty()) {
                networkGameId = msg.gameId
            } else if (msg.gameId != networkGameId) {
                return
            }
        }

        if (msg.ply > 0 && msg.ply != currentPly + 1) {
            if (msg.ply <= currentPly) return
            NetworkLog.log("GAME", "Move gap: expected #${currentPly + 1}, got #${msg.ply}")
            return
        }

        val opponentColor = state.myColor.opposite()
        if (state.board.sideToMove != opponentColor) return

        val move = resolveUciAgainstLegalMoves(state.board, msg.uci) ?: return
        currentPly = if (msg.ply > 0) msg.ply else currentPly + 1

        val limit = lastNetworkTimeControl?.let { it.initialSeconds * 1000L + 60_000L }
        val increment = lastNetworkTimeControl?.let { it.incrementSeconds * 1000L } ?: 0L
        val allowedMax = if (lastOpponentRemaining == Long.MAX_VALUE) {
            limit ?: Long.MAX_VALUE
        } else {
            lastOpponentRemaining + increment + CLOCK_TOLERANCE_MS
        }
        if (msg.remainingMillisSelf >= 0 &&
            (limit == null || msg.remainingMillisSelf <= limit) &&
            msg.remainingMillisSelf <= allowedMax
        ) {
            lastOpponentRemaining = msg.remainingMillisSelf
            clock?.applyRemoteTime(opponentColor, msg.remainingMillisSelf)
        } else if (msg.remainingMillisSelf > 0) {
            NetworkLog.log("GAME", "Invalid clock value from opponent rejected: ${msg.remainingMillisSelf}")
        }

        opponentFlagJob?.cancel()
        opponentFlagJob = null
        makeMove(move, sendToNetwork = false)
    }

    private fun onOpponentProfile(msg: GameMessage.ProfileMsg) {
        val myGeneration = sessionGeneration
        if (sessionAuth != null) {
            NetworkLog.log("GAME", "Repeated profile in current session ignored")
            return
        }
        if (msg.protocolVersion < GameMessage.PROTOCOL_VERSION) {
            NetworkLog.log(
                "GAME", "Opponent protocol version ${msg.protocolVersion} < ${GameMessage.PROTOCOL_VERSION}, incompatible"
            )
            _uiState.value = _uiState.value.copy(opponentOutdated = true)
            return
        }
        val peers = knownPeers
        if (peers == null) {
            NetworkLog.log("GAME", "Key store not ready, profile rejected")
            return
        }
        val trust = peers.verifyAndRemember(
            userId = msg.userId,
            publicKey = msg.publicKey.ifBlank { null },
            signedData = peers.profilePayload(
                msg.userId, msg.name, msg.elo,
                msg.avatarBase64, msg.backgroundBase64, msg.ephemeralKey
            ),
            signature = msg.signature.ifBlank { null }
        )
        if (trust == TrustResult.FIRST_SEEN && msg.publicKey.isNotBlank()) {
            _uiState.value = _uiState.value.copy(newPeerFingerprint = DeviceKeys.fingerprintOf(msg.publicKey))
        }
        if (trust == TrustResult.IMPERSONATION || trust == TrustResult.BAD_SIGNATURE) {
            _uiState.value = _uiState.value.copy(opponentUntrusted = true)
            return
        }

        val mine = ephemeralKeys
        val secret = if (mine != null && msg.ephemeralKey.isNotBlank()) {
            DeviceKeys.sharedSecretWith(mine, msg.ephemeralKey)
        } else {
            NetworkLog.log("GAME", "Opponent sent no session key (version ${msg.protocolVersion})")
            null
        }
        sessionAuth = secret?.let { SessionAuth(it) }

        if (myGeneration != sessionGeneration) {
            sessionAuth = null
            NetworkLog.log("GAME", "Profile processed after disconnect, discarded")
            return
        }
        if (!isNetworkHost && networkGameId.isEmpty() && msg.gameId.isNotEmpty()) {
            networkGameId = msg.gameId
        }
        if (!isNetworkHost && msg.initialSeconds > 0) {
            lastNetworkTimeControl = TimeControl(
                label = "",
                initialSeconds = msg.initialSeconds,
                incrementSeconds = msg.incrementSeconds
            )
        }
        markReadyIfPossible()

        _uiState.value = _uiState.value.copy(opponentUnverified = trust == TrustResult.UNSIGNED)

        val pending = pendingOpponentImages
        pendingOpponentImages = null
        val saved = FriendsStore.mergeOpponent(
            userId = msg.userId,
            name = msg.name,
            elo = msg.elo,
            avatarBase64 = msg.avatarBase64 ?: pending?.first,
            backgroundBase64 = msg.backgroundBase64 ?: pending?.second
        )
        _uiState.value = _uiState.value.copy(
            opponentProfile = Profile(
                msg.userId, msg.name, saved.avatarBase64, saved.backgroundBase64,
                msg.elo, levelChosen = true
            )
        )
    }

    private fun endNetworkSession() {
        opponentFlagJob?.cancel()
        opponentFlagJob = null
        clock?.stop()
        heartbeat?.stop()
        sessionAuth = null
        ephemeralKeys = null
        unsubscribeMessages?.invoke()
        unsubscribeMessages = null
    }

    /**
     * Leaves the game screen. A network game is left for good (a loss if it was still going);
     * a game against the AI is only paused and saved.
     */
    fun leaveNetworkGame() {
        val state = _uiState.value

        if (state.mode != GameMode.VS_PLAYER_NETWORK) {
            cancelAi()
            clock?.pause()
            persistIfResumable()
            return
        }

        if (state.status == GameStatus.ONGOING && state.handshake == HandshakeState.PLAYING) {
            applyNetworkResultElo(0.0)
        }
        opponentFlagJob?.cancel()
        opponentFlagJob = null
        val leaveQueued = sendGameMessage(GameMessage.LeaveMsg(state.myColor == PieceColor.WHITE, networkGameId))
        handshakeTimeoutJob?.cancel()
        handshakeTimeoutJob = null
        clock?.stop()

        ephemeralKeys = null
        sessionAuth = null
        heartbeat?.stop()
        heartbeat = null
        unsubscribeMessages?.invoke()
        unsubscribeMessages = null
        connectionWatcher?.cancel()
        connectionWatcher = null

        val t = transport ?: return
        transport = null
        if (!leaveQueued) {
            t.disconnect()
            return
        }
        // Give the leave message a moment to go out before the connection is closed.
        val job = viewModelScope.launch {
            delay(LEAVE_FLUSH_MS)
            t.disconnect()
            pendingLeave = null
        }
        pendingLeave = job to t
    }

    fun offerRematch() {
        val state = _uiState.value
        if (state.status == GameStatus.ONGOING || state.rematchOfferedByMe) return
        if (!sendGameMessage(GameMessage.RematchMsg("offer", networkGameId))) return
        _uiState.value = _uiState.value.copy(rematchOfferedByMe = true)
    }

    fun declineRematch() {
        sendGameMessage(GameMessage.RematchMsg("decline", networkGameId))
        _uiState.value = _uiState.value.copy(rematchOfferFromOpponent = false)
    }

    fun acceptRematch() {
        if (!_uiState.value.rematchOfferFromOpponent) return
        acceptRematchInternal()
    }

    private fun acceptRematchInternal() {
        val newId = java.util.UUID.randomUUID().toString()
        if (!sendGameMessage(GameMessage.RematchMsg("accept", networkGameId, newId))) return
        startRematch(newGameId = newId, iAmWhiteNow = _uiState.value.myColor == PieceColor.BLACK)
    }

    private fun startRematch(newGameId: String, iAmWhiteNow: Boolean) {
        val state = _uiState.value
        val timeControl = lastNetworkTimeControl ?: TimeControl.BLITZ_5_0

        networkGameId = newGameId
        networkResultRecorded = false
        opponentFlagJob?.cancel()
        opponentFlagJob = null
        currentPly = 0
        lastOpponentRemaining = Long.MAX_VALUE
        val start = Board.startPosition()
        resetHistory(start)

        _uiState.value = GameUiState(
            board = start,
            startBoard = start,
            mode = GameMode.VS_PLAYER_NETWORK,
            myColor = if (iAmWhiteNow) PieceColor.WHITE else PieceColor.BLACK,
            gameStarted = true,
            connectionState = state.connectionState,
            opponentProfile = state.opponentProfile,
            handshake = state.handshake,
            activeTransport = state.activeTransport,
            opponentUnverified = state.opponentUnverified,
            chatMessages = state.chatMessages
        )
        setupClock(timeControl)
    }

    private fun declareNetworkTimeout(flagged: PieceColor, sendClaim: Boolean) {
        val state = _uiState.value
        if (state.status != GameStatus.ONGOING) return
        opponentFlagJob?.cancel()
        opponentFlagJob = null
        clock?.stop()
        // The flag loses only if the other side could still mate.
        val draw = !ChessEngine.canMate(state.board, flagged.opposite())
        _uiState.value = state.copy(
            status = if (draw) GameStatus.TIMEOUT_DRAW else GameStatus.TIMEOUT,
            timedOutSide = flagged
        )
        if (sendClaim) {
            sendGameMessage(GameMessage.TimeoutMsg(flagged == PieceColor.WHITE, networkGameId))
        }
        applyNetworkResultElo(
            when {
                draw -> 0.5
                flagged == state.myColor -> 0.0
                else -> 1.0
            }
        )
    }

    private fun applyNetworkResultElo(result: Double) {
        if (networkResultRecorded) return
        networkResultRecorded = true
        val opponentElo = _uiState.value.opponentProfile?.elo ?: 1200
        ProfileStore.applyEloDelta(opponentElo, result)
        // Network games count in the statistics and the rating graph, like games against the AI.
        Stats.recordResult(result)
    }

    fun resign() {
        val state = _uiState.value
        if (state.status != GameStatus.ONGOING) return
        clock?.stop()
        _uiState.value = state.copy(status = GameStatus.RESIGNATION, resignedSide = state.myColor)
        when (state.mode) {
            GameMode.VS_PLAYER_NETWORK -> {
                sendGameMessage(GameMessage.ResignMsg(state.myColor == PieceColor.WHITE, networkGameId))
                applyNetworkResultElo(0.0)
            }
            GameMode.VS_AI -> {
                cancelAi()
                recordAiResult(state.aiDifficulty, 0.0)
                clearSavedGame()
            }
            GameMode.PUZZLE -> Unit
        }
    }

    fun offerDraw() {
        val state = _uiState.value
        if (state.mode != GameMode.VS_PLAYER_NETWORK || state.status != GameStatus.ONGOING || state.drawOfferSentByMe) return
        if (!sendGameMessage(GameMessage.DrawOfferMsg(null, networkGameId))) return
        _uiState.value = state.copy(drawOfferSentByMe = true)
    }

    fun respondToDrawOffer(accept: Boolean) {
        val state = _uiState.value
        if (!state.drawOfferFromOpponent || state.status != GameStatus.ONGOING) return
        if (!sendGameMessage(GameMessage.DrawOfferMsg(accept, networkGameId))) {
            _uiState.value = state.copy(drawOfferFromOpponent = false)
            return
        }
        if (accept) {
            clock?.stop()
            _uiState.value = state.copy(status = GameStatus.DRAW_AGREEMENT, drawOfferFromOpponent = false)
            applyNetworkResultElo(0.5)
        } else {
            _uiState.value = state.copy(drawOfferFromOpponent = false)
        }
    }

    fun dismissOpponentNotice() {
        if (_uiState.value.opponentNotice != null) {
            _uiState.value = _uiState.value.copy(opponentNotice = null)
        }
    }

    fun sendChatMessage(text: String) {
        val state = _uiState.value
        if (state.mode != GameMode.VS_PLAYER_NETWORK || text.isBlank()) return
        val trimmed = text.trim().take(MAX_CHAT_TEXT_CHARS)
        if (!sendGameMessage(GameMessage.ChatMsg(text = trimmed, gameId = networkGameId))) return
        _uiState.value = _uiState.value.copy(
            chatMessages = _uiState.value.chatMessages + ChatEntry(trimmed, null, fromMe = true, System.currentTimeMillis())
        )
    }

    fun sendChatImage(base64: String) {
        val state = _uiState.value
        if (state.mode != GameMode.VS_PLAYER_NETWORK) return
        if (base64.length > MAX_CHAT_IMAGE_CHARS) {
            NetworkLog.log("GAME", "Image too large to send")
            return
        }
        if (!sendGameMessage(GameMessage.ChatMsg(imageBase64 = base64, gameId = networkGameId))) return
        _uiState.value = _uiState.value.copy(
            chatMessages = _uiState.value.chatMessages + ChatEntry(null, base64, fromMe = true, System.currentTimeMillis())
        )
    }

    fun markChatRead() {
        _uiState.value = _uiState.value.copy(unreadChatCount = 0)
    }

    // endregion

    // region Clock

    private fun disposeClock() {
        clockCollectorJob?.cancel()
        clockCollectorJob = null
        clock?.stop()
        clock = null
    }

    private fun setupClock(
        timeControl: TimeControl,
        startSide: PieceColor = PieceColor.WHITE,
        restoreWhiteMillis: Long = -1,
        restoreBlackMillis: Long = -1
    ) {
        disposeClock()
        opponentFlagJob?.cancel()
        opponentFlagJob = null
        val newClock = GameClock(timeControl, viewModelScope)
        clock = newClock
        clockCollectorJob = viewModelScope.launch {
            newClock.state.collect { cs -> onClockTick(cs) }
        }
        if (restoreWhiteMillis >= 0 || restoreBlackMillis >= 0) {
            newClock.restore(restoreWhiteMillis, restoreBlackMillis)
        }
        newClock.start(startSide)
    }

    private fun onClockTick(cs: ClockState) {
        val current = _uiState.value
        val flag = cs.flagged
        if (flag == null || current.status != GameStatus.ONGOING) {
            _uiState.value = current.copy(clockState = cs)
            return
        }
        when (current.mode) {
            GameMode.VS_PLAYER_NETWORK -> {
                _uiState.value = current.copy(clockState = cs)
                if (flag == current.myColor) {
                    declareNetworkTimeout(flag, sendClaim = true)
                } else if (opponentFlagJob?.isActive != true) {
                    // The opponent's move may still be on its way: wait a moment before claiming.
                    val plyAtFlag = currentPly
                    opponentFlagJob = viewModelScope.launch {
                        delay(OPPONENT_FLAG_GRACE_MS)
                        val st = _uiState.value
                        if (st.status == GameStatus.ONGOING &&
                            currentPly == plyAtFlag &&
                            clock?.state?.value?.flagged == flag
                        ) {
                            declareNetworkTimeout(flag, sendClaim = true)
                        }
                    }
                }
            }
            GameMode.VS_AI -> {
                cancelAi()
                val draw = !ChessEngine.canMate(current.board, flag.opposite())
                val result = when {
                    draw -> 0.5
                    flag == current.myColor -> 0.0
                    else -> 1.0
                }
                recordAiResult(current.aiDifficulty, result)
                clearSavedGame()
                _uiState.value = current.copy(
                    clockState = cs,
                    status = if (draw) GameStatus.TIMEOUT_DRAW else GameStatus.TIMEOUT,
                    timedOutSide = flag
                )
            }
            GameMode.PUZZLE -> _uiState.value = current.copy(clockState = cs)
        }
    }

    // endregion

    override fun onCleared() {
        aiJob?.cancel()
        handshakeTimeoutJob?.cancel()
        opponentFlagJob?.cancel()
        pendingLeave?.first?.cancel()
        pendingLeave?.second?.disconnect()
        pendingLeave = null
        persistIfResumable()
        clock?.stop()

        ephemeralKeys = null
        sessionAuth = null
        heartbeat?.stop()
        heartbeat = null
        unsubscribeMessages?.invoke()
        unsubscribeMessages = null
        connectionWatcher?.cancel()
        connectionWatcher = null
        transport?.disconnect()

        super.onCleared()
    }

    private companion object {
        const val MAX_CHAT_TEXT_CHARS = 8_000
        const val MAX_CHAT_IMAGE_CHARS = 400_000
        const val CLOCK_TOLERANCE_MS = 2_000L
        const val HANDSHAKE_TIMEOUT_MS = 20_000L
        const val OPPONENT_FLAG_GRACE_MS = 4_000L
        const val LEAVE_FLUSH_MS = 700L
    }
}
