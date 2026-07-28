package com.daisuke.readinglock.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class LogicalDayResolverTest {

    private val tokyo = ZoneId.of("Asia/Tokyo")

    private fun at(text: String): Long =
        LocalDateTime.parse(text).atZone(tokyo).toInstant().toEpochMilli()

    @Test
    fun `03_59 は前日の論理日`() {
        assertEquals("2026-07-25", LogicalDayResolver.logicalDate(at("2026-07-26T03:59:59"), tokyo))
    }

    @Test
    fun `04_00 ちょうどは当日の論理日`() {
        assertEquals("2026-07-26", LogicalDayResolver.logicalDate(at("2026-07-26T04:00:00"), tokyo))
    }

    @Test
    fun `深夜0時は前日、正午は当日`() {
        assertEquals("2026-07-25", LogicalDayResolver.logicalDate(at("2026-07-26T00:00:00"), tokyo))
        assertEquals("2026-07-26", LogicalDayResolver.logicalDate(at("2026-07-26T12:00:00"), tokyo))
    }

    @Test
    fun `月初のまたぎ`() {
        assertEquals("2026-07-31", LogicalDayResolver.logicalDate(at("2026-08-01T02:00:00"), tokyo))
    }

    @Test
    fun `次のリセットは 03_59 なら当日 04_00`() {
        assertEquals(
            at("2026-07-26T04:00:00"),
            LogicalDayResolver.nextResetTime(at("2026-07-26T03:59:59"), tokyo),
        )
    }

    @Test
    fun `次のリセットは 04_00 ちょうどなら翌日 04_00`() {
        assertEquals(
            at("2026-07-27T04:00:00"),
            LogicalDayResolver.nextResetTime(at("2026-07-26T04:00:00"), tokyo),
        )
    }

    @Test
    fun `次のリセットは日中なら翌日 04_00`() {
        assertEquals(
            at("2026-07-27T04:00:00"),
            LogicalDayResolver.nextResetTime(at("2026-07-26T22:30:00"), tokyo),
        )
    }
}
