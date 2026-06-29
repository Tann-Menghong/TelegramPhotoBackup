package com.example.tgphotobackup.backup

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object EncryptionManager {

    // Key derived from bot token — always recoverable as long as user has the token
    fun deriveKey(botToken: String): SecretKey {
        val keyBytes = MessageDigest.getInstance("SHA-256")
            .digest("TGENC_$botToken".toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    // Output: random IV (16 bytes) prepended to AES-256-CBC encrypted data
    fun encrypt(plainBytes: ByteArray, key: SecretKey): ByteArray {
        val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        return iv + cipher.doFinal(plainBytes)
    }

    // Input: IV (16 bytes) + encrypted data
    fun decrypt(encryptedData: ByteArray, key: SecretKey): ByteArray {
        val iv   = encryptedData.copyOfRange(0, 16)
        val data = encryptedData.copyOfRange(16, encryptedData.size)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
        return cipher.doFinal(data)
    }

    fun isEncryptedName(name: String): Boolean = name.endsWith(".enc", ignoreCase = true)
    fun encryptedName(name: String): String    = if (isEncryptedName(name)) name else "$name.enc"
    fun decryptedName(name: String): String    = if (isEncryptedName(name)) name.dropLast(4) else name
}
