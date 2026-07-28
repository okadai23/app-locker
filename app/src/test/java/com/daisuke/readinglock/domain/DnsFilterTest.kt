package com.daisuke.readinglock.domain

import com.daisuke.readinglock.data.BlockedDomain
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsFilterTest {

    private val blocked = setOf("shonenjumpplus.com", "nicovideo.jp")

    @Test
    fun `完全一致とサブドメインをブロックする`() {
        assertTrue(DnsFilter.isBlocked("shonenjumpplus.com", blocked))
        assertTrue(DnsFilter.isBlocked("cdn.img.shonenjumpplus.com", blocked))
        assertTrue(DnsFilter.isBlocked("SHONENJUMPPLUS.COM", blocked))
        assertTrue(DnsFilter.isBlocked("www.nicovideo.jp.", blocked))
    }

    @Test
    fun `部分一致や別ドメインはブロックしない`() {
        assertFalse(DnsFilter.isBlocked("notshonenjumpplus.com", blocked))
        assertFalse(DnsFilter.isBlocked("shonenjumpplus.com.evil.example", blocked))
        assertFalse(DnsFilter.isBlocked("example.com", blocked))
        assertFalse(DnsFilter.isBlocked("", blocked))
    }

    @Test
    fun `削除予約は予約時刻を過ぎるまで有効`() {
        val rows = listOf(
            BlockedDomain(domain = "a.com", addedAt = 0),
            BlockedDomain(domain = "b.com", addedAt = 0, pendingDeleteAt = 2_000),
            BlockedDomain(domain = "c.com", addedAt = 0, pendingDeleteAt = 500),
        )
        assertEquals(setOf("a.com", "b.com"), DnsFilter.activeDomains(rows, now = 1_000))
    }

    @Test
    fun `問い合わせから QNAME を取り出す`() {
        val name = "cdn.shonenjumpplus.com"
        val question = DnsFilter.question(query(name))
        assertEquals(name, question?.name)
        // ヘッダ12 + QNAME(名前長+先頭長バイト+終端0) + QTYPE/QCLASS 4
        assertEquals(12 + name.length + 2 + 4, question?.end)
    }

    @Test
    fun `応答パケットや壊れたパケットは null`() {
        val response = query("example.com").also { it[2] = 0x80.toByte() }
        assertNull(DnsFilter.question(response))
        assertNull(DnsFilter.question(ByteArray(5)))
        // 圧縮ポインタ(0xC0)は質問セクションでは不正
        val pointer = query("example.com").also { it[12] = 0xC0.toByte() }
        assertNull(DnsFilter.question(pointer))
    }

    @Test
    fun `NXDOMAIN 応答を組み立てる`() {
        val query = query("shonenjumpplus.com")
        val question = requireNotNull(DnsFilter.question(query))
        val response = DnsFilter.nxDomain(query, question)

        assertEquals(question.end, response.size) // 質問セクションまでで打ち切り
        assertArrayEquals(query.copyOfRange(0, 2), response.copyOfRange(0, 2)) // ID は据え置き
        assertEquals(0x81, response[2].toInt() and 0xFF) // QR=1 / RD=1
        assertEquals(0x83, response[3].toInt() and 0xFF) // RA=1 / RCODE=3
        assertEquals(1, readShort(response, 4)) // QDCOUNT
        assertEquals(0, readShort(response, 6)) // ANCOUNT
        assertEquals(0, readShort(response, 8)) // NSCOUNT
        assertEquals(0, readShort(response, 10)) // ARCOUNT
        // 質問セクションはそのまま
        assertArrayEquals(query.copyOfRange(12, question.end), response.copyOfRange(12, question.end))
    }

    /** 手組みの A レコード問い合わせ */
    private fun query(name: String): ByteArray {
        val body = mutableListOf<Int>()
        body += listOf(0x12, 0x34) // ID
        body += listOf(0x01, 0x00) // RD=1
        body += listOf(0x00, 0x01) // QDCOUNT=1
        body += listOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00) // AN/NS/AR = 0
        name.split('.').forEach { label ->
            body += label.length
            body += label.map { it.code }
        }
        body += 0x00 // 名前の終端
        body += listOf(0x00, 0x01, 0x00, 0x01) // QTYPE=A / QCLASS=IN
        return ByteArray(body.size) { body[it].toByte() }
    }

    private fun readShort(b: ByteArray, i: Int) = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)
}
