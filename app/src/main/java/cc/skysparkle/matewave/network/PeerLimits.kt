package cc.skysparkle.matewave.network

/** Bounds for what another device may send us; anything outside them is not stored or used. */
object PeerLimits {
    const val MAX_NAME_CHARS = 40
    const val MAX_USER_ID_CHARS = 64
    const val MIN_ELO = 100
    const val MAX_ELO = 3000
    const val MAX_INITIAL_SECONDS = 3 * 60 * 60
    const val MAX_INCREMENT_SECONDS = 180

    /** "No time limit" is sent as Int.MAX_VALUE; 0 means the peer sent no time control. */
    private fun isTimeControl(initialSeconds: Int, incrementSeconds: Int): Boolean =
        (initialSeconds == 0 || initialSeconds == Int.MAX_VALUE || initialSeconds in 1..MAX_INITIAL_SECONDS) &&
            incrementSeconds in 0..MAX_INCREMENT_SECONDS

    fun isPlausibleName(name: String): Boolean =
        name.isNotBlank() && name.length <= MAX_NAME_CHARS && name.none { it.isISOControl() }

    fun isPlausibleUserId(userId: String): Boolean =
        userId.isNotBlank() && userId.length <= MAX_USER_ID_CHARS && userId.none { it.isISOControl() }

    /**
     * Checked before the profile is trusted or stored. The fields are signed as they are, so an
     * out-of-range profile is refused rather than trimmed.
     */
    fun isPlausibleProfile(msg: GameMessage.ProfileMsg): Boolean =
        isPlausibleUserId(msg.userId) &&
            isPlausibleName(msg.name) &&
            msg.elo in MIN_ELO..MAX_ELO &&
            isTimeControl(msg.initialSeconds, msg.incrementSeconds)

    /**
     * The most the opponent's clock may show after their move: what our clock gives them, plus
     * the increment and some slack for network delay. A higher claim would mean time not spent.
     */
    fun maxOpponentClockAfterMove(localRemainingMs: Long, incrementMs: Long, toleranceMs: Long): Long =
        localRemainingMs.coerceAtLeast(0) + incrementMs + toleranceMs
}
