package com.daisuke.readinglock.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class StreakCalculatorTest {

    @Test
    fun `当日未達成でもストリークは切れない`() {
        val achieved = setOf("2026-07-23", "2026-07-24", "2026-07-25")
        assertEquals(3, StreakCalculator.streak(achieved, today = "2026-07-26"))
    }

    @Test
    fun `当日達成なら当日も数える`() {
        val achieved = setOf("2026-07-24", "2026-07-25", "2026-07-26")
        assertEquals(3, StreakCalculator.streak(achieved, today = "2026-07-26"))
    }

    @Test
    fun `一日空くと途切れる`() {
        val achieved = setOf("2026-07-22", "2026-07-24", "2026-07-25")
        assertEquals(2, StreakCalculator.streak(achieved, today = "2026-07-26"))
    }

    @Test
    fun `昨日も一昨日も未達成なら 0`() {
        val achieved = setOf("2026-07-20", "2026-07-21")
        assertEquals(0, StreakCalculator.streak(achieved, today = "2026-07-26"))
    }

    @Test
    fun `達成履歴なしは 0`() {
        assertEquals(0, StreakCalculator.streak(emptySet(), today = "2026-07-26"))
    }

    @Test
    fun `月をまたいで連続`() {
        val achieved = setOf("2026-06-29", "2026-06-30", "2026-07-01")
        assertEquals(3, StreakCalculator.streak(achieved, today = "2026-07-01"))
    }
}
