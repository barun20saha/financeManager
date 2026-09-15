package com.example.myaccounts.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class EncryptionManager {

    private val keyAlias = "FireBallMasterKeyAlias"
    private val androidKeyStore = "AndroidKeyStore"
    private val transformation = "AES/GCM/NoPadding"
    private val ivSize = 12
    private val tagLengthBits = 128

    init {
        ensureKeyExists()
    }

    private fun ensureKeyExists() {
        try {
            val keyStore = KeyStore.getInstance(androidKeyStore)
            keyStore.load(null)
            if (!keyStore.containsAlias(keyAlias)) {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, androidKeyStore)
                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build()

                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(androidKeyStore)
        keyStore.load(null)
        val entry = keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
        return entry?.secretKey ?: throw IllegalStateException("Could not retrieve secret key from KeyStore")
    }

    fun encrypt(plainBytes: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(transformation)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv // 12 bytes generated automatically
        val cipherText = cipher.doFinal(plainBytes)

        // Prepend IV: [IV (12 bytes) | CipherText + Tag]
        val result = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, result, 0, iv.size)
        System.arraycopy(cipherText, 0, result, iv.size, cipherText.size)
        return result
    }

    fun decrypt(encryptedBytes: ByteArray): ByteArray {
        if (encryptedBytes.size < ivSize) {
            throw IllegalArgumentException("Encrypted data is too short")
        }

        val iv = ByteArray(ivSize)
        val cipherText = ByteArray(encryptedBytes.size - ivSize)
        System.arraycopy(encryptedBytes, 0, iv, 0, ivSize)
        System.arraycopy(encryptedBytes, ivSize, cipherText, 0, cipherText.size)

        val cipher = Cipher.getInstance(transformation)
        val spec = GCMParameterSpec(tagLengthBits, iv)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
        return cipher.doFinal(cipherText)
    }
}
