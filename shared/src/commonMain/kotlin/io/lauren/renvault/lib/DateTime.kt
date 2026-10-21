package io.lauren.renvault.lib

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

enum class RvDateStyle { Short, Medium, Long }

private const val DayMs = 86_400_000L
private const val YearTwoMs = -62_104_060_800_000L

expect fun localizedDate(millis: Long, style: RvDateStyle): String

private fun unset(millis: Long): Boolean = millis == 0L || millis < YearTwoMs

@OptIn(ExperimentalTime::class)
fun daysLeft(millis: Long, span: Int): Long {
    if (unset(millis)) return 0
    val gone = (Clock.System.now().toEpochMilliseconds() - millis) / DayMs
    return maxOf(0L, span - gone)
}

fun formatDate(millis: Long, style: RvDateStyle = RvDateStyle.Medium): String {
    if (unset(millis)) return ""
    return localizedDate(millis, style)
}

enum class RvRelativeUnit { Now, Minute, Hour, Day, Month, Year }

data class RvRelative(val unit: RvRelativeUnit, val count: Int)

private const val MinuteMs = 60_000L
private const val HourMs = 3_600_000L
private const val MonthMs = 2_629_746_000L
private const val YearMs = 31_556_952_000L
private const val JustNowMs = 45_000L

@OptIn(ExperimentalTime::class)
fun relativeOf(millis: Long, now: Long = Clock.System.now().toEpochMilliseconds()): RvRelative? {
    if (unset(millis)) return null
    val gone = maxOf(0L, now - millis)
    return when {
        gone < JustNowMs -> RvRelative(RvRelativeUnit.Now, 0)
        gone < HourMs -> RvRelative(RvRelativeUnit.Minute, (gone / MinuteMs).toInt())
        gone < DayMs -> RvRelative(RvRelativeUnit.Hour, (gone / HourMs).toInt())
        gone < MonthMs -> RvRelative(RvRelativeUnit.Day, (gone / DayMs).toInt())
        gone < YearMs -> RvRelative(RvRelativeUnit.Month, (gone / MonthMs).toInt())
        else -> RvRelative(RvRelativeUnit.Year, (gone / YearMs).toInt())
    }
}
