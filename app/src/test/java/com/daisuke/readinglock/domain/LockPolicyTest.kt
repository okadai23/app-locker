package com.daisuke.readinglock.domain

import com.daisuke.readinglock.data.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingAppsTest {

    @Test
    fun `DMM は Play 版と直販版のどちらも計測対象`() {
        val dmm = ReadingApps.packagesFor(Source.DMM)
        assertTrue("com.dmm.app.dmmbooks_google_play" in dmm)
        assertTrue("com.dmm.app.bookviewer" in dmm)
    }

    @Test
    fun `Kindle と DMM は混ざらない`() {
        assertTrue("com.amazon.kindle" in ReadingApps.packagesFor(Source.KINDLE))
        assertFalse("com.amazon.kindle" in ReadingApps.packagesFor(Source.DMM))
        assertFalse("com.dmm.app.bookviewer" in ReadingApps.packagesFor(Source.KINDLE))
    }

    @Test
    fun `紙は対象パッケージなし`() {
        assertEquals(emptySet<String>(), ReadingApps.packagesFor(Source.PAPER))
    }
}

class LockPolicyTest {

    private val blocked = setOf("com.twitter.android")

    @Test
    fun `未解禁のブロック対象だけロックする`() {
        assertTrue(LockPolicy.shouldLock("com.twitter.android", blocked, unlocked = false, whitelist = emptySet()))
        assertFalse(LockPolicy.shouldLock("com.twitter.android", blocked, unlocked = true, whitelist = emptySet()))
        assertFalse(LockPolicy.shouldLock("com.amazon.kindle", blocked, unlocked = false, whitelist = emptySet()))
        assertFalse(LockPolicy.shouldLock(null, blocked, unlocked = false, whitelist = emptySet()))
    }

    @Test
    fun `ホワイトリストはロックしない`() {
        val white = setOf("com.twitter.android")
        assertFalse(LockPolicy.shouldLock("com.twitter.android", blocked, unlocked = false, whitelist = white))
    }

    @Test
    fun `上限ちょうどで再ロック`() {
        assertFalse(LockPolicy.shouldRelock(true, true, usedSeconds = 30 * 60 - 1, limitMinutes = 30))
        assertTrue(LockPolicy.shouldRelock(true, true, usedSeconds = 30 * 60, limitMinutes = 30))
    }

    @Test
    fun `上限0は無制限`() {
        assertFalse(LockPolicy.shouldRelock(true, true, usedSeconds = 99 * 60, limitMinutes = 0))
    }

    @Test
    fun `未解禁中とブロック対象以外では再ロックしない`() {
        assertFalse(LockPolicy.shouldRelock(false, true, usedSeconds = 60 * 60, limitMinutes = 30))
        assertFalse(LockPolicy.shouldRelock(true, false, usedSeconds = 60 * 60, limitMinutes = 30))
    }
}
