package io.lauren.renvault.lib

import kotlin.math.log2

const val MinPassword = 8

enum class RvPasswordStrength { None, Weak, Fair, Strong, VeryStrong }

private const val LowerPool = 26
private const val UpperPool = 26
private const val DigitPool = 10
private const val SymbolPool = 33
private const val FairBits = 45.0
private const val StrongBits = 70.0
private const val VeryStrongBits = 100.0

fun strengthOf(password: String): RvPasswordStrength {
    if (password.length < MinPassword) return RvPasswordStrength.None
    val bits = entropyOf(password)
    return when {
        bits < FairBits -> RvPasswordStrength.Weak
        bits < StrongBits -> RvPasswordStrength.Fair
        bits < VeryStrongBits -> RvPasswordStrength.Strong
        else -> RvPasswordStrength.VeryStrong
    }
}

fun RvStrings.strengthLabel(level: RvPasswordStrength): String? = when (level) {
    RvPasswordStrength.None -> null
    RvPasswordStrength.Weak -> strengthWeak
    RvPasswordStrength.Fair -> strengthFair
    RvPasswordStrength.Strong -> strengthStrong
    RvPasswordStrength.VeryStrong -> strengthVeryStrong
}

private fun entropyOf(password: String): Double {
    var pool = 0
    if (password.any { it in 'a'..'z' }) pool += LowerPool
    if (password.any { it in 'A'..'Z' }) pool += UpperPool
    if (password.any { it.isDigit() }) pool += DigitPool
    if (password.any { !it.isLetterOrDigit() }) pool += SymbolPool
    var effective = 1
    for (i in 1 until password.length) {
        val step = password[i] - password[i - 1]
        if (step != 0 && step != 1) effective++
    }
    return effective * log2(pool.toDouble())
}
