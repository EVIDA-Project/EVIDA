package com.example.evida

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.util.Base64

class DigitalSignatureManager {

    private val keyAlias = "EVIDA_FORENSIC_KEY"
    private val androidKeystore = "AndroidKeyStore"

    init {
        generateKeyIfNeeded()
    }

    private fun generateKeyIfNeeded() {
        val keyStore = KeyStore.getInstance(androidKeystore)
        keyStore.load(null)
        
        if (!keyStore.containsAlias(keyAlias)) {
            val kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                androidKeystore
            )
            val parameterSpec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
            ).run {
                setDigests(KeyProperties.DIGEST_SHA256)
                setUserAuthenticationRequired(false) // Can be set to true to require fingerprint for each sign
                build()
            }
            kpg.initialize(parameterSpec)
            kpg.generateKeyPair()
        }
    }

    fun signData(data: String): String {
        val keyStore = KeyStore.getInstance(androidKeystore)
        keyStore.load(null)
        val privateKey = keyStore.getKey(keyAlias, null) as java.security.PrivateKey

        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(data.toByteArray())
        
        val signatureBytes = signature.sign()
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            Base64.getEncoder().encodeToString(signatureBytes)
        } else {
            android.util.Base64.encodeToString(signatureBytes, android.util.Base64.DEFAULT)
        }
    }

    fun verifySignature(data: String, signatureBase64: String): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(androidKeystore)
            keyStore.load(null)
            val publicKey = keyStore.getCertificate(keyAlias).publicKey

            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initVerify(publicKey)
            signature.update(data.toByteArray())

            val signatureBytes = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                Base64.getDecoder().decode(signatureBase64)
            } else {
                android.util.Base64.decode(signatureBase64, android.util.Base64.DEFAULT)
            }
            signature.verify(signatureBytes)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
