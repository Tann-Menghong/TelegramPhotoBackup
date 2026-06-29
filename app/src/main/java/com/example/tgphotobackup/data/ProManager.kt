package com.example.tgphotobackup.data

import java.security.MessageDigest

object ProManager {
    private const val SECRET = "TGBACKUP_MH_2026_ABA_500090709"

    // Key format: TGPRO-XXXX-XXXX-CCCC
    // After stripping dashes: TGPROXXXXXXXXCCCC (17 chars)
    // TGPRO(5) + data(8) + checksum(4) = 17
    fun validate(rawKey: String): Boolean {
        val key = rawKey.trim().uppercase().replace("-", "").replace(" ", "")
        if (!key.startsWith("TGPRO") || key.length != 17) return false
        val data  = key.substring(5, 13)
        val check = key.substring(13)
        return sha256(data + SECRET).take(4).uppercase() == check
    }

    // Developer tool: generateKey("MH000001") → "TGPRO-MH00-0001-XXXX"
    fun generateKey(identifier: String): String {
        val data  = identifier.uppercase().replace(Regex("[^A-Z0-9]"), "0").padEnd(8, '0').take(8)
        val check = sha256(data + SECRET).take(4).uppercase()
        return "TGPRO-${data.take(4)}-${data.drop(4)}-$check"
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
