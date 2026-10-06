package cc.skysparkle.matewave.settings

import android.content.Context
import cc.skysparkle.matewave.ai.AiDifficulty
import cc.skysparkle.matewave.clock.TimeControl
import cc.skysparkle.matewave.engine.PieceColor

/** Remembers the last choices on setup screens, so a repeat game is one tap away. */
class LastGameSetup(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("last_game_setup", Context.MODE_PRIVATE)

    var aiDifficulty: AiDifficulty
        get() = AiDifficulty.entries.firstOrNull { it.name == prefs.getString("ai_difficulty", null) } ?: AiDifficulty.MEDIUM
        set(value) { prefs.edit().putString("ai_difficulty", value.name).apply() }

    /** null means a random colour. */
    var aiColor: PieceColor?
        get() = when (prefs.getString("ai_color", "W")) { "W" -> PieceColor.WHITE; "B" -> PieceColor.BLACK; else -> null }
        set(value) { prefs.edit().putString("ai_color", when (value) { PieceColor.WHITE -> "W"; PieceColor.BLACK -> "B"; null -> "R" }).apply() }

    var aiTime: TimeControl
        get() = timeFor("ai_time", TimeControl.RAPID_10_0)
        set(value) { prefs.edit().putString("ai_time", value.label).apply() }

    var nearbyTime: TimeControl
        get() = timeFor("nearby_time", TimeControl.BLITZ_5_0)
        set(value) { prefs.edit().putString("nearby_time", value.label).apply() }

    private fun timeFor(key: String, default: TimeControl): TimeControl =
        TimeControl.presets.firstOrNull { it.label == prefs.getString(key, null) } ?: default
}
