package cc.skysparkle.matewave.security

import android.content.Context
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

/**
 * Long-term ECDSA identity key (kept in Android Keystore) plus per-game ephemeral ECDH
 * keys, so a leaked session secret does not expose other games.
 */
object DeviceKeys {
    private const val KEYSTORE = "AndroidKeyStore"
    // Kept from before the rename: a new alias would create a new identity and break pinned keys.
    private const val KEY_ALIAS = "chessapp_identity"
    private const val PREFS = "device_keys"
    private const val KEY_PRIVATE = "private"
    private const val KEY_PUBLIC = "public"
    private const val ALGORITHM = "EC"
    private const val CURVE = "secp256r1"
    private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"

    @Volatile private var cached: KeyPair? = null

    private fun keystoreKeyPair(): KeyPair? = try {
        val store = java.security.KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val existing = store.getEntry(KEY_ALIAS, null) as? java.security.KeyStore.PrivateKeyEntry
        if (existing != null) {
            KeyPair(existing.certificate.publicKey, existing.privateKey)
        } else {
            val generator = KeyPairGenerator.getInstance(ALGORITHM, KEYSTORE)
            generator.initialize(
                android.security.keystore.KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    android.security.keystore.KeyProperties.PURPOSE_SIGN or
                        android.security.keystore.KeyProperties.PURPOSE_VERIFY
                )
                    .setAlgorithmParameterSpec(ECGenParameterSpec(CURVE))
                    .setDigests(android.security.keystore.KeyProperties.DIGEST_SHA256)
                    .build()
            )
            generator.generateKeyPair()
        }
    } catch (e: Exception) {
        null
    }

    private fun keyPair(context: Context): KeyPair? {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }

            keystoreKeyPair()?.let {
                cached = it
                return it
            }

            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val storedPrivate = prefs.getString(KEY_PRIVATE, null)
            val storedPublic = prefs.getString(KEY_PUBLIC, null)

            if (storedPrivate != null && storedPublic != null) {
                val restored = try {
                    val factory = KeyFactory.getInstance(ALGORITHM)
                    KeyPair(
                        factory.generatePublic(X509EncodedKeySpec(decode(storedPublic))),
                        factory.generatePrivate(PKCS8EncodedKeySpec(decode(storedPrivate)))
                    )
                } catch (e: Exception) {
                    null
                }
                if (restored != null) {
                    cached = restored
                    return restored
                }
            }

            return try {
                val generator = KeyPairGenerator.getInstance(ALGORITHM)
                generator.initialize(ECGenParameterSpec(CURVE))
                val fresh = generator.generateKeyPair()
                prefs.edit()
                    .putString(KEY_PRIVATE, encode(fresh.private.encoded))
                    .putString(KEY_PUBLIC, encode(fresh.public.encoded))
                    .apply()
                cached = fresh
                fresh
            } catch (e: Exception) {
                null
            }
        }
    }

    fun publicKey(context: Context): String? =
        keyPair(context)?.public?.encoded?.let { encode(it) }

    fun publicKeyFingerprint(context: Context): String? =
        publicKey(context)?.let { fingerprintOf(it) }

    fun fingerprintOf(publicKey: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(publicKey.toByteArray(Charsets.UTF_8))
            .take(16).joinToString("") { "%02x".format(it) }

    fun sign(context: Context, data: String): String? {
        val privateKey: PrivateKey = keyPair(context)?.private ?: return null
        return try {
            val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
            signature.initSign(privateKey)
            signature.update(data.toByteArray(Charsets.UTF_8))
            encode(signature.sign())
        } catch (e: Exception) {
            null
        }
    }

    fun verify(publicKeyEncoded: String, data: String, signatureEncoded: String): Boolean {
        return try {
            val key: PublicKey = KeyFactory.getInstance(ALGORITHM)
                .generatePublic(X509EncodedKeySpec(decode(publicKeyEncoded)))
            val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
            signature.initVerify(key)
            signature.update(data.toByteArray(Charsets.UTF_8))
            signature.verify(decode(signatureEncoded))
        } catch (e: Exception) {
            false
        }
    }

    fun generateEphemeral(): KeyPair? = try {
        val generator = KeyPairGenerator.getInstance(ALGORITHM)
        generator.initialize(ECGenParameterSpec(CURVE))
        generator.generateKeyPair()
    } catch (e: Exception) {
        null
    }

    fun encodePublic(keyPair: KeyPair): String = encode(keyPair.public.encoded)

    fun sharedSecretWith(ephemeral: KeyPair, peerEphemeralPublic: String): ByteArray? = try {
        val peer = KeyFactory.getInstance(ALGORITHM)
            .generatePublic(X509EncodedKeySpec(decode(peerEphemeralPublic)))
        val agreement = javax.crypto.KeyAgreement.getInstance("ECDH")
        agreement.init(ephemeral.private)
        agreement.doPhase(peer, true)
        MessageDigest.getInstance("SHA-256").digest(agreement.generateSecret())
    } catch (e: Exception) {
        null
    }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)
}
