package cc.skysparkle.matewave.security

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Per-session message protection: AES-256-GCM for confidentiality and a truncated
 * HMAC-SHA256 tag bound to the sender's direction. A sliding window of sequence numbers
 * rejects replays while tolerating out-of-order delivery.
 */
class SessionAuth(private val secret: ByteArray) {
    private var lastAcceptedSeq = 0
    private var outgoingSeq = 0

    private val seenInWindow = java.util.TreeSet<Int>()

    fun nextSeq(): Int {
        outgoingSeq += 1
        return outgoingSeq
    }

    fun acceptSeq(seq: Int): Boolean {
        if (seq <= 0) return false
        if (seq > lastAcceptedSeq) {
            lastAcceptedSeq = seq
            seenInWindow.add(seq)

            while (seenInWindow.isNotEmpty() && seenInWindow.first() < lastAcceptedSeq - REPLAY_WINDOW) {
                seenInWindow.pollFirst()
            }
            return true
        }

        if (seq < lastAcceptedSeq - REPLAY_WINDOW) return false

        return seenInWindow.add(seq)
    }

    fun tag(message: String): String? = try {
        val mac = Mac.getInstance(MAC_ALGORITHM)
        mac.init(SecretKeySpec(secret, MAC_ALGORITHM))
        val full = mac.doFinal(message.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(full.copyOfRange(0, TAG_BYTES), Base64.NO_WRAP)
    } catch (e: Exception) {
        null
    }

    fun verify(message: String, providedTag: String?): Boolean {
        if (providedTag == null) return false
        val expected = tag(message) ?: return false
        if (expected.length != providedTag.length) return false
        var diff = 0
        for (i in expected.indices) {
            diff = diff or (expected[i].code xor providedTag[i].code)
        }
        return diff == 0
    }

    private val encryptionKey: ByteArray by lazy {
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(secret + KDF_LABEL)
    }

    fun encrypt(plaintext: String): String? = try {
        val nonce = ByteArray(GCM_NONCE_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(encryptionKey, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, nonce)
        )
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(nonce + ciphertext, Base64.NO_WRAP)
    } catch (e: Exception) {
        null
    }

    fun decrypt(payload: String): String? = try {
        val raw = Base64.decode(payload, Base64.NO_WRAP)
        if (raw.size <= GCM_NONCE_BYTES) null else {
            val nonce = raw.copyOfRange(0, GCM_NONCE_BYTES)
            val ciphertext = raw.copyOfRange(GCM_NONCE_BYTES, raw.size)
            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(encryptionKey, "AES"),
                GCMParameterSpec(GCM_TAG_BITS, nonce)
            )
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        }
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val MAC_ALGORITHM = "HmacSHA256"
        const val TAG_BYTES = 12
        const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
        const val GCM_NONCE_BYTES = 12
        const val GCM_TAG_BITS = 128
        const val REPLAY_WINDOW = 256
        val KDF_LABEL = "chessapp-session-aes-gcm-v1".toByteArray(Charsets.UTF_8)
    }
}
