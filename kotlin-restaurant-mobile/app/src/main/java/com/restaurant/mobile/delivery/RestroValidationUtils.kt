package com.restaurant.mobile.delivery

/**
 * Production-grade validation utilities for Restro Super-App.
 * Strictly validates 10-digit mobile phone numbers and RFC-compliant domain emails.
 */
object RestroValidationUtils {
    // Validates Indian 10-digit mobile phone numbers starting with 6, 7, 8, 9
    val PHONE_REGEX = Regex("^[6-9]\\d{9}$")

    // Strict email regex requiring proper alphanumeric domain and valid 2-10 letter TLD (e.g., .com, .org, .in, .co.in, .app)
    val STRICT_EMAIL_REGEX = Regex(
        "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,10}$"
    )

    fun sanitizePhone(input: String): String {
        var digits = input.filter { it.isDigit() }
        if (digits.length > 10 && digits.startsWith("91")) {
            digits = digits.substring(2)
        } else if (digits.length == 11 && digits.startsWith("0")) {
            digits = digits.substring(1)
        }
        return digits.take(10)
    }

    fun isValidPhone(phone: String): Boolean {
        val clean = sanitizePhone(phone)
        return clean.length == 10 && clean.matches(PHONE_REGEX)
    }

    fun getPhoneValidationMessage(phone: String): String? {
        val clean = sanitizePhone(phone)
        return when {
            clean.isEmpty() -> null
            clean.length < 10 -> "Enter 10-digit mobile number (${clean.length}/10 digits)"
            !clean.matches(Regex("^[6-9].*")) -> "Mobile number must start with 6, 7, 8, or 9"
            !clean.matches(PHONE_REGEX) -> "Please enter a valid 10-digit mobile number"
            else -> null
        }
    }

    fun isValidEmail(email: String): Boolean {
        val trimmed = email.trim()
        if (!trimmed.matches(STRICT_EMAIL_REGEX)) return false
        val domain = trimmed.substringAfter("@")
        if (domain.startsWith("-") || domain.endsWith("-") || domain.startsWith(".") || domain.endsWith(".")) return false
        if (domain.contains("..")) return false
        val tld = domain.substringAfterLast(".")
        return tld.length in 2..10 && tld.all { it.isLetter() }
    }

    fun getEmailValidationMessage(email: String): String? {
        val trimmed = email.trim()
        if (trimmed.isEmpty()) return null
        if (!trimmed.contains("@")) return "Email must contain an @ symbol"
        val domain = trimmed.substringAfter("@")
        if (domain.isEmpty()) return "Enter domain name (e.g. gmail.com)"
        if (!domain.contains(".")) return "Domain must include an extension (e.g. .com, .in, .org)"
        val tld = domain.substringAfterLast(".")
        if (tld.length < 2) return "Domain extension too short"
        if (!isValidEmail(trimmed)) return "Enter a valid email with a proper domain (e.g. name@domain.com)"
        return null
    }

    fun isValidPassword(password: String): Boolean {
        return password.length >= 8
    }

    fun isValidName(name: String): Boolean {
        return name.trim().length >= 2
    }
}
