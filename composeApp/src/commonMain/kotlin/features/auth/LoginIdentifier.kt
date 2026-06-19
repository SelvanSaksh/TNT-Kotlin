package features.auth

private val EMAIL_REGEX = Regex("^[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,64}$")

fun phoneDigitsOnly(raw: String): String = raw.filter { it.isDigit() }

fun isValidEmail(value: String): Boolean = EMAIL_REGEX.matches(value.trim())

fun isValidPhone(value: String): Boolean {
    val digits = phoneDigitsOnly(value)
    return digits.length in 7..15
}

fun isValidLoginIdentifier(value: String): Boolean {
    val trimmed = value.trim()
    return isValidEmail(trimmed) || isValidPhone(trimmed)
}

/** API accepts email or phone in the `email` field (iOS parity). */
fun normalizedLoginIdentifier(value: String): String = value.trim()
