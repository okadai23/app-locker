package com.daisuke.readinglock.domain

import com.daisuke.readinglock.domain.FaceDownDetector.Action
import com.daisuke.readinglock.domain.FaceDownDetector.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceDownDetectorTest {

    private val grace = 10_000L

    @Test
    fun `伏せるまではカウントしない`() {
        val detector = FaceDownDetector(grace)
        assertEquals(Action.NONE, detector.update(faceDown = false, now = 0))
        assertEquals(State.IDLE, detector.state)
        assertFalse(detector.counting)
    }

    @Test
    fun `伏せたらカウント開始`() {
        val detector = FaceDownDetector(grace)
        assertEquals(Action.NONE, detector.update(faceDown = true, now = 0))
        assertEquals(State.COUNTING, detector.state)
        assertTrue(detector.counting)
    }

    @Test
    fun `持ち上げて猶予を過ぎたら PAUSE`() {
        val detector = FaceDownDetector(grace)
        detector.update(faceDown = true, now = 0)
        assertEquals(Action.NONE, detector.update(faceDown = false, now = 1_000))
        assertEquals(State.GRACE, detector.state)
        assertTrue("猶予中はカウント継続", detector.counting)

        assertEquals(Action.NONE, detector.update(faceDown = false, now = 10_000))
        assertEquals(Action.PAUSE, detector.update(faceDown = false, now = 11_000))
        assertEquals(State.PAUSED, detector.state)
        assertFalse(detector.counting)
    }

    @Test
    fun `猶予内に伏せ直せば PAUSE しない`() {
        val detector = FaceDownDetector(grace)
        detector.update(faceDown = true, now = 0)
        detector.update(faceDown = false, now = 1_000)
        assertEquals(Action.NONE, detector.update(faceDown = true, now = 5_000))
        assertEquals(State.COUNTING, detector.state)
        // 猶予は伏せ直しでリセットされる
        detector.update(faceDown = false, now = 6_000)
        assertEquals(Action.NONE, detector.update(faceDown = false, now = 12_000))
        assertEquals(Action.PAUSE, detector.update(faceDown = false, now = 16_000))
    }

    @Test
    fun `PAUSE 後に伏せたら RESUME`() {
        val detector = FaceDownDetector(grace)
        detector.update(faceDown = true, now = 0)
        detector.update(faceDown = false, now = 0)
        detector.update(faceDown = false, now = grace)
        assertEquals(State.PAUSED, detector.state)

        assertEquals(Action.RESUME, detector.update(faceDown = true, now = grace + 1))
        assertEquals(State.COUNTING, detector.state)
        // 連続で伏せていても RESUME は一度だけ
        assertEquals(Action.NONE, detector.update(faceDown = true, now = grace + 2))
    }

    @Test
    fun `PAUSE は一度だけ発火する`() {
        val detector = FaceDownDetector(grace)
        detector.update(faceDown = true, now = 0)
        detector.update(faceDown = false, now = 0)
        assertEquals(Action.PAUSE, detector.update(faceDown = false, now = grace))
        assertEquals(Action.NONE, detector.update(faceDown = false, now = grace * 3))
    }
}
