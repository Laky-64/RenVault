package io.lauren.renvault.lib

const val MinPassword = 8

enum class RvPasswordStrength { None, Weak, Fair, Strong }

private val Lower = Regex("[a-z]")
private val Upper = Regex("[A-Z]")
private val Digit = Regex("\\d")
private val Symbol = Regex("[^A-Za-z0-9]")

fun strengthOf(password: String): RvPasswordStrength {
    if (password.length < MinPassword) return RvPasswordStrength.None

    var classes = 0
    if (Lower.containsMatchIn(password)) classes++
    if (Upper.containsMatchIn(password)) classes++
    if (Digit.containsMatchIn(password)) classes++
    if (Symbol.containsMatchIn(password)) classes++

    if (password.toSet().size < 5) return RvPasswordStrength.Weak

    if (password.length >= 16 && classes >= 2) return RvPasswordStrength.Strong
    if (password.length >= 12 || classes >= 3) return RvPasswordStrength.Fair
    return RvPasswordStrength.Weak
}
