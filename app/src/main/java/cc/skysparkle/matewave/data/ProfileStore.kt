package cc.skysparkle.matewave.data

import cc.skysparkle.matewave.ai.AiDifficulty
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.pow
import kotlin.math.roundToInt

data class Profile(

    val userId: String,
    val name: String,
    val avatarBase64: String?,
    val backgroundBase64: String?,
    val elo: Int,
    val levelChosen: Boolean
)

object ProfileStore {
    private const val PREFS = "chess_profile_prefs"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_NAME = "name"
    private const val KEY_AVATAR = "avatar"
    private const val KEY_BACKGROUND = "background"
    private const val KEY_ELO = "elo"
    private const val KEY_LEVEL_CHOSEN = "level_chosen"
    private const val K_FACTOR = 32.0

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun prefs() = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun userId(): String {
        val p = prefs() ?: return "local"
        p.getString(KEY_USER_ID, null)?.let { return it }
        val fresh = java.util.UUID.randomUUID().toString()
        p.edit().putString(KEY_USER_ID, fresh).apply()
        return fresh
    }

    fun get(): Profile {
        val p = prefs()
        return Profile(
            userId = userId(),

            name = p?.getString(KEY_NAME, null)
                ?: appContext?.getString(cc.skysparkle.matewave.R.string.player_default_name)
                ?: "Player",
            avatarBase64 = p?.getString(KEY_AVATAR, null),
            backgroundBase64 = p?.getString(KEY_BACKGROUND, null),
            elo = p?.getInt(KEY_ELO, 1200) ?: 1200,
            levelChosen = p?.getBoolean(KEY_LEVEL_CHOSEN, false) ?: false
        )
    }

    fun setName(name: String) {
        prefs()?.edit()?.putString(KEY_NAME, name)?.apply()
    }

    fun setAvatar(base64: String?) {
        prefs()?.edit()?.putString(KEY_AVATAR, base64)?.apply()
    }

    fun resetAvatarToDefault() = setAvatar(null)

    fun setBackground(base64: String?) {
        prefs()?.edit()?.putString(KEY_BACKGROUND, base64)?.apply()
    }

    fun setStartingElo(elo: Int) {
        prefs()?.edit()
            ?.putInt(KEY_ELO, elo)
            ?.putBoolean(KEY_LEVEL_CHOSEN, true)
            ?.apply()
    }

    fun applyEloDelta(opponentElo: Int, result: Double) {
        val current = get().elo
        val expected = 1.0 / (1.0 + 10.0.pow((opponentElo - current) / 400.0))
        val newElo = (current + K_FACTOR * (result - expected)).roundToInt().coerceIn(100, 3000)
        prefs()?.edit()?.putInt(KEY_ELO, newElo)?.apply()
        cc.skysparkle.matewave.stats.Stats.recordRating(current, newElo)
    }

    fun aiNominalElo(difficulty: AiDifficulty): Int = when (difficulty) {
        AiDifficulty.EASY -> 800
        AiDifficulty.MEDIUM -> 1200
        AiDifficulty.HARD -> 1600
        AiDifficulty.EXPERT -> 2000
    }
}

data class FriendEntry(

    val userId: String,
    val name: String,
    val avatarBase64: String?,
    val backgroundBase64: String?,
    val elo: Int,
    val lastPlayedMs: Long,

    val favorite: Boolean = false,
    /** Last time the friend was seen nearby; together with the last game it gives "last seen". */
    val lastSeenMs: Long = 0
)

object FriendsStore {
    private const val PREFS = "chess_friends_prefs"
    private const val KEY_LIST = "friends_json"
    private const val MAX_FRIENDS = 30

    private var appContext: Context? = null

    private val lock = Any()

    @Volatile private var cached: List<FriendEntry>? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        cached = null
    }

    private fun prefs() = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun list(): List<FriendEntry> = synchronized(lock) { readAll() }

    private fun readAll(): List<FriendEntry> {
        cached?.let { return it }
        val raw = prefs()?.getString(KEY_LIST, null) ?: return emptyList()
        val arr = try {
            JSONArray(raw)
        } catch (e: Exception) {
            return emptyList()
        }
        val result = ArrayList<FriendEntry>()
        for (i in 0 until arr.length()) {
            try {
                val o = arr.getJSONObject(i)
                result.add(
                    FriendEntry(
                        userId = o.optString("uid", "").ifBlank { o.getString("name") },
                        name = o.getString("name"),
                        avatarBase64 = if (o.has("avatar")) o.getString("avatar") else null,
                        backgroundBase64 = if (o.has("background")) o.getString("background") else null,
                        elo = o.optInt("elo", 1200),
                        lastPlayedMs = o.optLong("t", 0),
                        favorite = o.optBoolean("fav", false),
                        lastSeenMs = o.optLong("s", 0)
                    )
                )
            } catch (e: Exception) {
            }
        }
        val sorted = result.sortedByDescending { it.lastPlayedMs }
        cached = sorted
        return sorted
    }

    private fun writeAll(entries: List<FriendEntry>) {
        val p = prefs() ?: return
        val arr = JSONArray()
        entries.forEach { f ->
            val o = JSONObject()
            o.put("uid", f.userId)
            o.put("name", f.name)
            if (f.avatarBase64 != null) o.put("avatar", f.avatarBase64)
            if (f.backgroundBase64 != null) o.put("background", f.backgroundBase64)
            o.put("elo", f.elo)
            o.put("t", f.lastPlayedMs)
            if (f.favorite) o.put("fav", true)
            if (f.lastSeenMs > 0) o.put("s", f.lastSeenMs)
            arr.put(o)
        }
        p.edit().putString(KEY_LIST, arr.toString()).apply()
        cached = entries.sortedByDescending { it.lastPlayedMs }
    }

    fun preload() {
        synchronized(lock) { readAll() }
    }

    fun updateProfile(
        userId: String,
        name: String,
        elo: Int,
        avatarBase64: String? = null,
        backgroundBase64: String? = null
    ) {
        synchronized(lock) {
            val existing = readAll().firstOrNull { it.userId == userId } ?: return
            val updated = existing.copy(
                name = name.ifBlank { existing.name },
                elo = if (elo > 0) elo else existing.elo,
                avatarBase64 = avatarBase64 ?: existing.avatarBase64,
                backgroundBase64 = backgroundBase64 ?: existing.backgroundBase64
            )
            if (updated == existing) return
            recordOpponent(updated)
        }
    }

    fun toggleFavorite(userId: String) {
        synchronized(lock) {
            val existing = readAll().firstOrNull { it.userId == userId } ?: return
            recordOpponent(existing.copy(favorite = !existing.favorite))
        }
    }

    fun mergeOpponent(
        userId: String,
        name: String,
        elo: Int,
        avatarBase64: String? = null,
        backgroundBase64: String? = null
    ): FriendEntry = synchronized(lock) {
        val existing = readAll().firstOrNull { it.userId == userId }
        val merged = FriendEntry(
            userId = userId,
            name = name.ifBlank { existing?.name ?: name },
            avatarBase64 = avatarBase64 ?: existing?.avatarBase64,
            backgroundBase64 = backgroundBase64 ?: existing?.backgroundBase64,
            elo = if (elo > 0) elo else (existing?.elo ?: 1200),
            lastPlayedMs = System.currentTimeMillis(),
            favorite = existing?.favorite ?: false,
            lastSeenMs = existing?.lastSeenMs ?: 0
        )
        recordOpponent(merged)
        merged
    }

    /** Notes that a friend is nearby now. Written at most once a minute per friend. */
    fun markSeen(userId: String, at: Long) {
        synchronized(lock) {
            val all = readAll()
            val index = all.indexOfFirst { it.userId == userId }
            if (index < 0 || at - all[index].lastSeenMs < 60_000) return
            // Order is kept: being seen is not a game, so the friend does not jump to the top.
            writeAll(all.toMutableList().also { it[index] = all[index].copy(lastSeenMs = at) })
        }
    }

    fun recordOpponent(entry: FriendEntry) {
        synchronized(lock) {
            val current = readAll().filter { it.userId != entry.userId }
            writeAll((listOf(entry) + current).take(MAX_FRIENDS))
        }
    }
}
