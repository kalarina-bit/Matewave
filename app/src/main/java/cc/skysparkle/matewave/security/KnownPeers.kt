package cc.skysparkle.matewave.security

import android.content.Context
import cc.skysparkle.matewave.network.NetworkLog
import org.json.JSONObject

enum class TrustResult {
    TRUSTED,

    FIRST_SEEN,

    IMPERSONATION,

    BAD_SIGNATURE,

    UNSIGNED
}

/**
 * Trust-on-first-use key pinning: the first public key seen for a player ID is stored,
 * and later profiles for that ID must be signed with the same key.
 */
class KnownPeers(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun publicKeyOf(userId: String): String? = prefs.getString(key(userId), null)

    fun verifyAndRemember(
        userId: String,
        publicKey: String?,
        signedData: String,
        signature: String?
    ): TrustResult {
        if (publicKey.isNullOrBlank() || signature.isNullOrBlank()) {
            if (publicKeyOf(userId) != null) {
                NetworkLog.log("TRUST", "Unsigned profile for known $userId rejected as impersonation")
                return TrustResult.IMPERSONATION
            }
            return TrustResult.UNSIGNED
        }
        if (!DeviceKeys.verify(publicKey, signedData, signature)) {
            NetworkLog.log("TRUST", "Signature mismatch for $userId")
            return TrustResult.BAD_SIGNATURE
        }

        val known = publicKeyOf(userId)
        return when {
            known == null -> {
                prefs.edit().putString(key(userId), publicKey).apply()
                TrustResult.FIRST_SEEN
            }
            known == publicKey -> TrustResult.TRUSTED
            else -> {
                NetworkLog.log("TRUST", "Impersonation attempt for $userId rejected")
                TrustResult.IMPERSONATION
            }
        }
    }

    fun profilePayload(
        userId: String,
        name: String,
        elo: Int,
        avatarBase64: String? = null,
        backgroundBase64: String? = null,

        ephemeralKey: String = ""
    ): String =
        JSONObject().apply {
            put("userId", userId)
            put("name", name)
            put("elo", elo)
            put("avatar", digestOf(avatarBase64))
            put("background", digestOf(backgroundBase64))
            put("ephemeral", ephemeralKey)
        }.toString()

    private fun digestOf(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .take(8).joinToString("") { "%02x".format(it) }
    }

    /** Forgets every pinned key, e.g. after opponents reinstalled the app. */
    fun forgetAll() {
        prefs.edit().clear().apply()
    }

    private fun key(userId: String) = "pk:$userId"

    private companion object {
        const val PREFS = "known_peers"
    }
}
