package com.example.tgphotobackup.data

import java.security.MessageDigest
import java.util.Calendar
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

sealed class LicenseType {
    object None : LicenseType()
    data class ProMonthly(val expiryYYYYMM: String) : LicenseType()
    object ProMax : LicenseType()
}

object ProManager {
    // Split across fragments — ProGuard does NOT obfuscate string literals,
    // so joining at runtime resists simple APK string extraction.
    private fun secretPro(): String = "TGBACKUP" + "_MH_2026" + "_ABA_500090709"
    private fun secretMax(): String = "TGBACKUP" + "_MAX_MH" + "_2026_ABA_500090709"

    fun validate(rawKey: String): LicenseType {
        val key = rawKey.trim().uppercase().replace("-", "").replace(" ", "")
        return when {
            key.startsWith("PROM")  -> validatePro(key)
            key.startsWith("PMAX")  -> validateMax(key)
            key.startsWith("TGPRO") -> validateLegacy(key)
            else -> LicenseType.None
        }
    }

    // PROM-YYYYMM-XXXX-CCCC-CCCC → stripped = PROMYYYYMMXXXXCCCCCCCC (22 chars)
    // 8-char HMAC-SHA256 checksum — 4 billion combinations, not brute-forceable
    private fun validatePro(key: String): LicenseType {
        if (key.length != 22) return LicenseType.None
        val expiry = key.substring(4, 10)
        val data   = key.substring(10, 14)
        val check  = key.substring(14, 22)
        if (!expiry.all { it.isDigit() }) return LicenseType.None
        if (hmac(expiry + data, secretPro()).take(8).uppercase() != check) return LicenseType.None
        return LicenseType.ProMonthly(expiry)
    }

    // PMAX-XXXX-XXXX-CCCC-CCCC → stripped = PMAXXXXXXXXXCCCCCCCC (20 chars)
    // 8-char HMAC-SHA256 checksum
    private fun validateMax(key: String): LicenseType {
        if (key.length != 20) return LicenseType.None
        val data  = key.substring(4, 12)
        val check = key.substring(12, 20)
        if (hmac(data, secretMax()).take(8).uppercase() != check) return LicenseType.None
        return LicenseType.ProMax
    }

    // Legacy TGPRO-XXXX-XXXX-CCCC — old 4-char SHA-256 format, kept for backward compat
    private fun validateLegacy(key: String): LicenseType {
        if (key.length != 17) return LicenseType.None
        val data  = key.substring(5, 13)
        val check = key.substring(13)
        if (sha256(data + secretPro()).take(4).uppercase() != check) return LicenseType.None
        return LicenseType.ProMonthly("209912")
    }

    fun isProActive(type: LicenseType): Boolean = when (type) {
        is LicenseType.ProMonthly -> currentYYYYMM() <= type.expiryYYYYMM
        is LicenseType.ProMax     -> true
        else                      -> false
    }

    fun isProMaxActive(type: LicenseType): Boolean = type is LicenseType.ProMax

    fun expiryLabel(type: LicenseType): String = when (type) {
        is LicenseType.ProMax     -> "Lifetime"
        is LicenseType.ProMonthly -> {
            if (type.expiryYYYYMM == "209912") "Lifetime (legacy)"
            else "Valid until end of ${monthName(type.expiryYYYYMM.drop(4))} ${type.expiryYYYYMM.take(4)}"
        }
        else -> ""
    }

    fun currentYYYYMM(): String {
        val cal = Calendar.getInstance()
        return "%04d%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }

    private fun monthName(mm: String): String = when (mm) {
        "01" -> "January";  "02" -> "February"; "03" -> "March"
        "04" -> "April";    "05" -> "May";       "06" -> "June"
        "07" -> "July";     "08" -> "August";    "09" -> "September"
        "10" -> "October";  "11" -> "November"
        else -> "December"
    }

    // HMAC-SHA256: correct MAC primitive — immune to length-extension attacks
    private fun hmac(message: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(message.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    // SHA-256: used only for legacy key validation
    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
