package io.lauren.renvault.lib

private const val Letters = "abcdefghijkmnopqrstuvwxyz"
private const val Digits = "23456789"
private const val Groups = 3
private const val GroupLength = 6

expect fun secureRandomUInt(): UInt

private fun randomInt(max: Int): Int {
    val bound = max.toUInt()
    val limit = (UInt.MAX_VALUE / bound) * bound
    var value: UInt
    do {
        value = secureRandomUInt()
    } while (value >= limit)
    return (value % bound).toInt()
}

private fun pick(set: String): Char = set[randomInt(set.length)]

fun generatePassword(): String {
    val total = Groups * GroupLength
    val chars = CharArray(total) { pick(Letters) }

    val digitAt = randomInt(total)
    chars[digitAt] = pick(Digits)

    var upperAt = randomInt(total)
    while (upperAt == digitAt) upperAt = randomInt(total)
    chars[upperAt] = chars[upperAt].uppercaseChar()

    return (0 until total step GroupLength).joinToString("-") { start ->
        chars.concatToString(start, start + GroupLength)
    }
}
