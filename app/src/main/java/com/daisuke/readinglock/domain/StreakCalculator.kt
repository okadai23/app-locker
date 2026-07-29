package com.daisuke.readinglock.domain

import java.time.LocalDate

/**
 * 連続達成日数。当日がまだ未達成でもストリークは切れない(判定は論理日確定時)。§2
 * 起点は「今日 or 昨日」で、そこから連続している達成日を数える。
 */
object StreakCalculator {

    fun streak(achievedDates: Set<String>, today: String): Int {
        val todayDate = LocalDate.parse(today)
        var cursor = if (todayDate.toString() in achievedDates) todayDate else todayDate.minusDays(1)
        var count = 0
        while (cursor.toString() in achievedDates) {
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }
}
