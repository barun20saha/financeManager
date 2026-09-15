package com.example.myaccounts.utils

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {

    private const val PBKDF2_ITERATIONS = 100_000
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH_BITS = 128

    fun generateSalt(): String {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        return Base64.getEncoder().encodeToString(salt)
    }

    fun hashPin(pin: String, saltBase64: String): String {
        val salt = Base64.getDecoder().decode(saltBase64)
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return Base64.getEncoder().encodeToString(hash)
    }

    fun verifyPin(pin: String, saltBase64: String, expectedHashBase64: String): Boolean {
        val computedHash = hashPin(pin, saltBase64)
        return computedHash == expectedHashBase64
    }

    fun encryptWithPassword(plainBytes: ByteArray, password: String): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        val iv = ByteArray(IV_LENGTH)
        val random = SecureRandom()
        random.nextBytes(salt)
        random.nextBytes(iv)

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH)
        val key = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        val cipherText = cipher.doFinal(plainBytes)

        // Result: [Salt (16 bytes) | IV (12 bytes) | CipherText + Tag]
        val result = ByteArray(salt.size + iv.size + cipherText.size)
        System.arraycopy(salt, 0, result, 0, salt.size)
        System.arraycopy(iv, 0, result, salt.size, iv.size)
        System.arraycopy(cipherText, 0, result, salt.size + iv.size, cipherText.size)
        return result
    }

    fun decryptWithPassword(encryptedBytes: ByteArray, password: String): ByteArray {
        if (encryptedBytes.size < SALT_LENGTH + IV_LENGTH) {
            throw IllegalArgumentException("Data is too short to be valid encrypted backup")
        }

        val salt = ByteArray(SALT_LENGTH)
        val iv = ByteArray(IV_LENGTH)
        val cipherText = ByteArray(encryptedBytes.size - SALT_LENGTH - IV_LENGTH)

        System.arraycopy(encryptedBytes, 0, salt, 0, SALT_LENGTH)
        System.arraycopy(encryptedBytes, SALT_LENGTH, iv, 0, IV_LENGTH)
        System.arraycopy(encryptedBytes, SALT_LENGTH + IV_LENGTH, cipherText, 0, cipherText.size)

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH)
        val key = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return cipher.doFinal(cipherText)
    }
}
