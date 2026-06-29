package com.example.tgphotobackup.data

import java.security.MessageDigest
import java.util.Calendar

sealed class LicenseType {
    object None : LicenseType()
    data class ProMonthly(val expiryYYYYMM: String) : LicenseType()
    object ProMax : LicenseType()
}

object ProManager {
    private const val SECRET_PRO = "TGBACKUP_MH_2026_ABA_500090709"
    private const val SECRET_MAX = "TGBACKUP_MAX_MH_2026_ABA_500090709"

    fun validate(rawKey: String): LicenseType {
        val key = rawKey.trim().uppercase().replace("-", "").replace(" ", "")
        return when {
            key.startsWith("PROM")  -> validatePro(key)
            key.startsWith("PMAX")  -> validateMax(key)
            key.startsWith("TGPRO") -> validateLegacy(key)
            else -> LicenseType.None
        }
    }

    // PROM-YYYYMM-XXXX-CCCC  →  stripped = PROMYYYYMMXXXXCCCC  (18 chars)
    private fun validatePro(key: String): LicenseType {
        if (key.length != 18) return LicenseType.None
        val expiry = key.substring(4, 10)
        val data   = key.substring(10, 14)
        val check  = key.substring(14)
        if (!expiry.all { it.isDigit() }) return LicenseType.None
        if (sha256(expiry + data + SECRET_PRO).take(4).uppercase() != check) return LicenseType.None
        return LicenseType.ProMonthly(expiry)
    }

    // PMAX-XXXX-XXXX-CCCC  →  stripped = PMAXXXXXXXXXCCCC  (16 chars)
    private fun validateMax(key: String): LicenseType {
        if (key.length != 16) return LicenseType.None
        val data  = key.substring(4, 12)
        val check = key.substring(12)
        if (sha256(data + SECRET_MAX).take(4).uppercase() != check) return LicenseType.None
        return LicenseType.ProMax
    }

    // Legacy TGPRO-XXXX-XXXX-CCCC  →  treated as ProMonthly with far-future expiry
    private fun validateLegacy(key: String): LicenseType {
        if (key.length != 17) return LicenseType.None
        val data  = key.substring(5, 13)
        val check = key.substring(13)
        if (sha256(data + SECRET_PRO).take(4).uppercase() != check) return LicenseType.None
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

    /** Monthly Pro key: PROM-YYYYMM-XXXX-CCCC (e.g. PROM-202607-MH01-XXXX) */
    fun generateProKey(identifier: String, yyyyMM: String): String {
        val data  = identifier.uppercase().replace(Regex("[^A-Z0-9]"), "0").padEnd(4, '0').take(4)
        val check = sha256(yyyyMM + data + SECRET_PRO).take(4).uppercase()
        return "PROM-$yyyyMM-$data-$check"
    }

    /** Lifetime Pro Max key: PMAX-XXXX-XXXX-CCCC */
    fun generateMaxKey(identifier: String): String {
        val data  = identifier.uppercase().replace(Regex("[^A-Z0-9]"), "0").padEnd(8, '0').take(8)
        val check = sha256(data + SECRET_MAX).take(4).uppercase()
        return "PMAX-${data.take(4)}-${data.drop(4)}-$check"
    }

    private fun monthName(mm: String): String = when (mm) {
        "01" -> "January";  "02" -> "February"; "03" -> "March"
        "04" -> "April";    "05" -> "May";       "06" -> "June"
        "07" -> "July";     "08" -> "August";    "09" -> "September"
        "10" -> "October";  "11" -> "November"
        else -> "December"
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
