package com.example.evida

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class EncryptionManager {

    /**
     * For Demonstration Purposes: A pair of RSA keys representing a Forensic Authority.
     * In a real app, the Public Key would be embedded in the app, and the Private Key 
     * would stay in the Forensic Lab's secure server.
     */
    companion object {
        private const val PREFS_NAME = "authority_keys"
        private const val PUB_KEY_LABEL = "auth_pub"
        private const val PRIV_KEY_LABEL = "auth_priv"

        var DEMO_AUTHORITY_PUBLIC_KEY = ""
        var DEMO_AUTHORITY_PRIVATE_KEY = ""

        fun initializeKeys(context: android.content.Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            val storedPub = prefs.getString(PUB_KEY_LABEL, null)
            val storedPriv = prefs.getString(PRIV_KEY_LABEL, null)

            if ((storedPub != null) && (storedPriv != null)) {
                DEMO_AUTHORITY_PUBLIC_KEY = storedPub
                DEMO_AUTHORITY_PRIVATE_KEY = storedPriv
            } else {
                // Generate and persist once for the life of the demo installation
                try {
                    val kpg = java.security.KeyPairGenerator.getInstance("RSA")
                    kpg.initialize(2048)
                    val kp = kpg.generateKeyPair()
                    
                    DEMO_AUTHORITY_PUBLIC_KEY = Base64.encodeToString(
                        kp.public.encoded, 
                        Base64.NO_WRAP,
                    )
                    
                    DEMO_AUTHORITY_PRIVATE_KEY = Base64.encodeToString(
                        kp.private.encoded, 
                        Base64.NO_WRAP,
                    )

                    prefs.edit {
                        putString(PUB_KEY_LABEL, DEMO_AUTHORITY_PUBLIC_KEY)
                        putString(PRIV_KEY_LABEL, DEMO_AUTHORITY_PRIVATE_KEY)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private val keyAlias = "EVIDA_AES_KEY"
    private val androidKeystore = "AndroidKeyStore"
    private val transformation = "AES/GCM/NoPadding"
    private val wrappingTransformation = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"

    init {
        generateKeyIfNeeded()
    }

    private fun generateKeyIfNeeded() {
        val keyStore = KeyStore.getInstance(androidKeystore)
        keyStore.load(null)
        
        if (!keyStore.containsAlias(keyAlias)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                androidKeystore
            )
            val parameterSpec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).run {
                setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                build()
            }
            keyGenerator.init(parameterSpec)
            keyGenerator.generateKey()
        }
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(androidKeystore)
        keyStore.load(null)
        return keyStore.getKey(keyAlias, null) as SecretKey
    }

    fun wrapKey(keyToWrap: SecretKey): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance(transformation)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv
        val wrappedKey = cipher.doFinal(keyToWrap.encoded)
        return Pair(wrappedKey, iv)
    }

    fun unwrapKey(wrappedKey: ByteArray, iv: ByteArray): SecretKey {
        val cipher = Cipher.getInstance(transformation)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
        val keyBytes = cipher.doFinal(wrappedKey)
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Digital Envelope Encryption: Wraps a unique AES key with a Public RSA Key.
     */
    fun encryptWithEnvelope(data: ByteArray, authorityPublicKeyBase64: String): EnvelopedData {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val ephemeralKey = keyGen.generateKey()

        val cipher = Cipher.getInstance(transformation)
        cipher.init(Cipher.ENCRYPT_MODE, ephemeralKey)
        val iv = cipher.iv
        val encryptedData = cipher.doFinal(data)

        val publicKey = decodePublicKey(authorityPublicKeyBase64)
        val wrapCipher = Cipher.getInstance(wrappingTransformation)
        // Use ENCRYPT_MODE for wrapping to be more robust across API levels than WRAP_MODE
        wrapCipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val wrappedKey = wrapCipher.doFinal(ephemeralKey.encoded)

        val (localWrappedKey, localWrappedKeyIv) = wrapKey(ephemeralKey)

        return EnvelopedData(
            encryptedData = encryptedData,
            iv = iv,
            authorityWrappedKey = wrappedKey,
            localWrappedKey = localWrappedKey,
            localWrappedKeyIv = localWrappedKeyIv
        )
    }

    data class EnvelopedData(
        val encryptedData: ByteArray,
        val iv: ByteArray,
        val authorityWrappedKey: ByteArray,
        val localWrappedKey: ByteArray,
        val localWrappedKeyIv: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as EnvelopedData

            if (!encryptedData.contentEquals(other.encryptedData)) return false
            if (!iv.contentEquals(other.iv)) return false
            if (!authorityWrappedKey.contentEquals(other.authorityWrappedKey)) return false
            if (!localWrappedKey.contentEquals(other.localWrappedKey)) return false
            if (!localWrappedKeyIv.contentEquals(other.localWrappedKeyIv)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = encryptedData.contentHashCode()
            result = (31 * result) + iv.contentHashCode()
            result = (31 * result) + authorityWrappedKey.contentHashCode()
            result = (31 * result) + localWrappedKey.contentHashCode()
            result = (31 * result) + localWrappedKeyIv.contentHashCode()
            return result
        }
    }

    /**
     * Decrypts a Digital Envelope using an Authority Private Key.
     */
    fun decryptWithEnvelope(
        encryptedData: ByteArray,
        iv: ByteArray,
        wrappedKey: ByteArray,
        privateKeyBase64: String
    ): ByteArray {
        val privateKey = decodePrivateKey(privateKeyBase64)
        
        // 1. Unwrap the ephemeral AES key
        val wrapCipher = Cipher.getInstance(wrappingTransformation)
        wrapCipher.init(Cipher.DECRYPT_MODE, privateKey)
        val keyBytes = wrapCipher.doFinal(wrappedKey)
        val ephemeralKey = SecretKeySpec(keyBytes, "AES")

        // 2. Decrypt the data
        val cipher = Cipher.getInstance(transformation)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, ephemeralKey, spec)
        return cipher.doFinal(encryptedData)
    }

    fun decrypt(encryptedData: ByteArray, iv: ByteArray, key: SecretKey? = null): ByteArray {
        val cipher = Cipher.getInstance(transformation)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, key ?: getSecretKey(), spec)
        return cipher.doFinal(encryptedData)
    }

    private fun decodePublicKey(base64Key: String): PublicKey {
        val keyBytes = Base64.decode(base64Key, Base64.DEFAULT)
        val spec = X509EncodedKeySpec(keyBytes)
        return KeyFactory.getInstance("RSA").generatePublic(spec)
    }

    private fun decodePrivateKey(base64Key: String): PrivateKey {
        val keyBytes = Base64.decode(base64Key, Base64.DEFAULT)
        val spec = PKCS8EncodedKeySpec(keyBytes)
        return KeyFactory.getInstance("RSA").generatePrivate(spec)
    }
}
