package utils

import kotlin.text.Regex

// Multiplatform email validation
object EmailValidator {
    private val EMAIL_REGEX = Regex(
        pattern = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$"
    )

    fun isValidEmail(email: String): Boolean {
        return EMAIL_REGEX.matches(email.trim())
    }
}