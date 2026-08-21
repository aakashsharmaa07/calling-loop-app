package com.aakash.callloop.utils

object PhoneNumberUtils {
    /**
     * Clean phone number by removing non-dialable characters while preserving leading '+' if present.
     */
    fun cleanPhoneNumber(rawNumber: String): String {
        val trimmed = rawNumber.trim()
        if (trimmed.isEmpty()) return ""
        
        val startsWithPlus = trimmed.startsWith("+")
        val digitsOnly = trimmed.filter { it.isDigit() }
        
        return if (startsWithPlus) {
            "+$digitsOnly"
        } else {
            digitsOnly
        }
    }

    /**
     * Validates whether a given phone number input is usable for dialing.
     * Minimum 3 digits required.
     */
    fun isValidPhoneNumber(phoneNumber: String): Boolean {
        val cleaned = cleanPhoneNumber(phoneNumber)
        val digitCount = cleaned.count { it.isDigit() }
        return digitCount >= 3
    }
}
