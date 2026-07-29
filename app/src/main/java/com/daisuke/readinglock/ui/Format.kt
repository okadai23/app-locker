package com.daisuke.readinglock.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val DOW = listOf("日", "月", "火", "水", "木", "金", "土")

fun LocalDate.jp(): String = "${monthValue}月${dayOfMonth}日(${DOW[dayOfWeek.value % 7]})"

fun dowLabel(index: Int): String = DOW[index]

/** epoch ミリ → HH:mm */
fun clock(epochMs: Long): String {
    val t = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalTime()
    return "%02d:%02d".format(t.hour, t.minute)
}

/** 秒 → mm:ss(計測画面の大きい数字) */
fun mmss(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    return "%02d:%02d".format(s / 60, s % 60)
}

/** ミリ秒 → 「6時間12分」「58分」 */
fun duration(millis: Long): String {
    val minutes = (millis / 60_000L).toInt()
    return if (minutes >= 60) "${minutes / 60}時間${"%02d".format(minutes % 60)}分" else "${minutes}分"
}
