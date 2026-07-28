package com.daisuke.readinglock.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 論理日 = 4:00 境界。03:59 は前日、04:00 は当日。§3 */
object LogicalDayResolver {

    const val RESET_HOUR = 4

    private val FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun logicalDate(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        logicalLocalDate(epochMs, zone).format(FORMAT)

    fun logicalLocalDate(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate {
        val local = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDateTime()
        return if (local.hour < RESET_HOUR) local.toLocalDate().minusDays(1) else local.toLocalDate()
    }

    /** 次の 4:00 の epoch ミリ秒。ちょうど 4:00 なら翌日の 4:00 */
    fun nextResetTime(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val local = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDateTime()
        val todayReset = local.toLocalDate().atTime(LocalTime.of(RESET_HOUR, 0))
        val next = if (local < todayReset) todayReset else todayReset.plusDays(1)
        return next.atZone(zone).toInstant().toEpochMilli()
    }

    fun previousDate(date: String): String = LocalDate.parse(date, FORMAT).minusDays(1).format(FORMAT)
}
