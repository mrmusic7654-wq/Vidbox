package com.vidbox.data.database

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

interface SecretCipher {
    fun encrypt(plain: String): String
    fun decrypt(encrypted: String): String
}

/** Original/thumbnail URLs may contain expiring bearer tokens. Keep them out of plaintext Room rows. */
@Singleton
class KeystoreSecretCipher @Inject constructor() : SecretCipher {
    private val alias = "vidbox.downloads.v1"
    private val secretKey: SecretKey by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { loadKey() }
    private fun loadKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build())
        }.generateKey()
    }
    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secretKey) }
        val encoded = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return "v1:" + Base64.encodeToString(encoded, Base64.NO_WRAP)
    }
    override fun decrypt(encrypted: String): String {
        check(encrypted.startsWith("v1:")) { "Unsupported encrypted record version" }
        val bytes = Base64.decode(encrypted.removePrefix("v1:"), Base64.NO_WRAP)
        check(bytes.size >= 28) { "Invalid encrypted record" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, bytes, 0, 12))
        }
        return cipher.doFinal(bytes, 12, bytes.size - 12).toString(Charsets.UTF_8)
    }
}
