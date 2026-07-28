package com.daisuke.readinglock.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressCalculatorTest {

    @Test
    fun `経過分は切り捨て`() {
        assertEquals(0, ProgressCalculator.elapsedMinutes(59))
        assertEquals(12, ProgressCalculator.elapsedMinutes(12 * 60 + 59))
    }

    @Test
    fun `残り分は切り上げ`() {
        // 12分01秒 読了 → 残り 17分59秒 → 18分
        assertEquals(18, ProgressCalculator.remainingMinutes(12 * 60 + 1, 30))
        assertEquals(30, ProgressCalculator.remainingMinutes(0, 30))
        assertEquals(1, ProgressCalculator.remainingMinutes(30 * 60 - 1, 30))
    }

    @Test
    fun `達成後の残りは 0`() {
        assertEquals(0, ProgressCalculator.remainingMinutes(30 * 60, 30))
        assertEquals(0, ProgressCalculator.remainingMinutes(45 * 60, 30))
    }

    @Test
    fun `達成判定はノルマ秒ちょうどで true`() {
        assertFalse(ProgressCalculator.achieved(30 * 60 - 1, 30))
        assertTrue(ProgressCalculator.achieved(30 * 60, 30))
    }

    @Test
    fun `リング比率は 0 から 1 にクランプ`() {
        assertEquals(0f, ProgressCalculator.fraction(0, 30), 0f)
        assertEquals(0.5f, ProgressCalculator.fraction(15 * 60, 30), 1e-6f)
        assertEquals(1f, ProgressCalculator.fraction(99 * 60, 30), 0f)
    }
}

class TodayProgressTest {

    private fun progress(totalSeconds: Int, relockCount: Int = 0) =
        TodayProgress("2026-07-26", totalSeconds, quotaMinutes = 30, relockCount = relockCount)

    @Test
    fun `再ロックのたびにノルマが1周分積み増される`() {
        assertEquals(30, progress(0).effectiveQuotaMinutes)
        assertEquals(60, progress(0, relockCount = 1).effectiveQuotaMinutes)
        assertEquals(90, progress(0, relockCount = 2).effectiveQuotaMinutes)
    }

    @Test
    fun `解禁は実効ノルマ基準`() {
        // 30分読んで解禁 → 再ロックされると 60分まで解禁されない
        assertTrue(progress(30 * 60).unlocked)
        assertFalse(progress(30 * 60, relockCount = 1).unlocked)
        assertTrue(progress(60 * 60, relockCount = 1).unlocked)
    }

    @Test
    fun `達成は基準ノルマのまま(ストリークを再ロックで壊さない)`() {
        val relocked = progress(30 * 60, relockCount = 1)
        assertTrue(relocked.achieved)
        assertFalse(relocked.unlocked)
    }

    @Test
    fun `残り時間も実効ノルマ基準`() {
        assertEquals(0, progress(30 * 60).remainingMinutes)
        assertEquals(30, progress(30 * 60, relockCount = 1).remainingMinutes)
        assertEquals(30, progress(30 * 60).elapsedMinutes)
    }
}
