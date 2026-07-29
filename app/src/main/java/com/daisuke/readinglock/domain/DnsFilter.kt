package com.daisuke.readinglock.domain

import com.daisuke.readinglock.data.BlockedDomain

/** DNS フィルタの純粋部分。パケット解釈とサフィックス一致。§5.2 */
object DnsFilter {

    /** QNAME と質問セクションの終端オフセット */
    data class Question(val name: String, val end: Int)

    /** 有効なブロック対象。削除予約は予約時刻(翌日4:00)までは有効のまま */
    fun activeDomains(rows: List<BlockedDomain>, now: Long): Set<String> =
        rows.asSequence()
            .filter { it.pendingDeleteAt == null || it.pendingDeleteAt > now }
            .map { it.domain.lowercase().trim('.') }
            .filter { it.isNotEmpty() }
            .toSet()

    /** 大小無視のサフィックス一致。完全一致 or ".domain" で終わるサブドメイン */
    fun isBlocked(name: String, blocked: Set<String>): Boolean {
        val q = name.lowercase().trim('.')
        if (q.isEmpty()) return false
        return blocked.any { q == it || q.endsWith(".$it") }
    }

    /** UDP ペイロードから質問を取り出す。問い合わせ以外・解釈できないものは null */
    fun question(packet: ByteArray): Question? {
        if (packet.size < 12) return null
        if (packet[2].toInt() and 0x80 != 0) return null // QR=1 は応答
        if (readShort(packet, 4) != 1) return null // QDCOUNT != 1 は扱わない
        val name = StringBuilder()
        var i = 12
        while (true) {
            if (i >= packet.size) return null
            val len = packet[i].toInt() and 0xFF
            if (len == 0) {
                i++
                break
            }
            // 64以上は圧縮ポインタ(0xC0)か不正。質問セクションには現れない
            if (len > 63 || i + 1 + len > packet.size) return null
            if (name.isNotEmpty()) name.append('.')
            name.append(String(packet, i + 1, len, Charsets.US_ASCII))
            i += 1 + len
        }
        if (name.isEmpty() || i + 4 > packet.size) return null
        return Question(name.toString(), i + 4)
    }

    /** 問い合わせパケットをそのまま NXDOMAIN 応答に組み替える */
    fun nxDomain(query: ByteArray, question: Question): ByteArray {
        val out = query.copyOf(question.end)
        out[2] = ((query[2].toInt() and 0x79) or 0x80).toByte() // QR=1、OPCODE と RD は保持
        out[3] = 0x83.toByte() // RA=1 / RCODE=3(NXDOMAIN)
        for (offset in intArrayOf(6, 8, 10)) { // ANCOUNT / NSCOUNT / ARCOUNT = 0
            out[offset] = 0
            out[offset + 1] = 0
        }
        return out
    }

    private fun readShort(b: ByteArray, i: Int): Int =
        ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)
}
