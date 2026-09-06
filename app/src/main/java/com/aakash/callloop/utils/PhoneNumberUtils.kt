package com.aakash.callloop.utils

import com.aakash.callloop.data.Country
import com.aakash.callloop.data.CountryData

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
     * Extracts only decimal digits from a string.
     */
    fun extractDigits(input: String): String {
        return input.filter { it.isDigit() }
    }

    /**
     * Validates whether a national phone number satisfies country-specific rules.
     * For India: strictly 10 digits.
     * For other countries: within country.minDigits..country.maxDigits.
     */
    fun isValidNationalNumber(nationalNumber: String, country: Country): Boolean {
        val digits = extractDigits(nationalNumber)
        return if (country.code == "IN") {
            digits.length == 10
        } else {
            digits.length in country.minDigits..country.maxDigits
        }
    }

    /**
     * Formats dialCode + nationalNumber into clean E.164 (e.g. +919836372837) without duplication.
     */
    fun formatE164(country: Country, nationalNumber: String): String {
        val cleanDialCode = if (country.dialCode.startsWith("+")) country.dialCode else "+${country.dialCode}"
        val digits = extractDigits(nationalNumber)
        return "$cleanDialCode$digits"
    }

    /**
     * Parses a raw contact phone number string and separates it into (Country, nationalNumber).
     * Prevents duplication of dial codes.
     */
    fun parseContactNumber(rawNumber: String, currentCountry: Country): Pair<Country, String> {
        val trimmed = rawNumber.trim().replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
        val digitsOnly = extractDigits(trimmed)

        // If number starts with +, check if it matches any country dial code
        if (trimmed.startsWith("+")) {
            // Sort by dial code length descending to match longer prefixes first (e.g. +971 before +9)
            val sortedCountries = CountryData.countries.sortedByDescending { it.dialCode.length }
            for (country in sortedCountries) {
                if (trimmed.startsWith(country.dialCode)) {
                    val national = trimmed.removePrefix(country.dialCode)
                    return Pair(country, extractDigits(national).take(country.maxDigits))
                }
            }
        }

        // Handle domestic trunk prefix '0' (common in India e.g. 09836372837)
        if (trimmed.startsWith("0") && digitsOnly.length == 11 && currentCountry.code == "IN") {
            return Pair(currentCountry, digitsOnly.drop(1).take(10))
        }

        // If digits starts with 91 and has 12 digits (e.g. 919836372837 without +)
        if (digitsOnly.startsWith("91") && digitsOnly.length == 12 && currentCountry.code == "IN") {
            return Pair(currentCountry, digitsOnly.drop(2).take(10))
        }

        // Default: use current country and take up to maxDigits
        return Pair(currentCountry, digitsOnly.take(currentCountry.maxDigits))
    }

    /**
     * Validates whether a given phone number input is usable for dialing.
     * Minimum 3 digits required.
     */
    fun isValidPhoneNumber(phoneNumber: String): Boolean {
        val cleaned = cleanPhoneNumber(phoneNumber)
        val digitCount = cleaned.count { it.isDigit() }
        return digitCount >= 7
    }
}
