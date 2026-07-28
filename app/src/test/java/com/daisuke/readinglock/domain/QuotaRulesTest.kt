package com.daisuke.readinglock.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class QuotaRulesTest {

    @Test
    fun `延長は即時反映で pending をクリア`() {
        assertEquals(45 to null, QuotaRules.request(current = 30, pending = null, requested = 45))
        assertEquals(45 to null, QuotaRules.request(current = 30, pending = 20, requested = 45))
    }

    @Test
    fun `短縮は当日据え置きで pending に保持`() {
        assertEquals(30 to 20, QuotaRules.request(current = 30, pending = null, requested = 20))
    }

    @Test
    fun `短縮の取り消し(現在値に戻す)は pending をクリア`() {
        assertEquals(30 to null, QuotaRules.request(current = 30, pending = 20, requested = 30))
    }

    @Test
    fun `短縮の上書きは新しい pending が勝つ`() {
        assertEquals(30 to 25, QuotaRules.request(current = 30, pending = 20, requested = 25))
    }

    @Test
    fun `4時リセットで pending を適用`() {
        assertEquals(20, QuotaRules.applyPending(current = 30, pending = 20))
        assertEquals(30, QuotaRules.applyPending(current = 30, pending = null))
    }
}
