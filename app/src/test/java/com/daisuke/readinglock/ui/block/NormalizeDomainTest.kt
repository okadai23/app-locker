package com.daisuke.readinglock.ui.block

import org.junit.Assert.assertEquals
import org.junit.Test

/** DnsFilter はサフィックス一致なので、入力を素のホスト名に均せているかだけ確かめる */
class NormalizeDomainTest {

    @Test
    fun `URL を貼られてもホスト名だけ取り出す`() {
        assertEquals("comic-walker.com", normalizeDomain("https://www.comic-walker.com/manga/1?p=2"))
        assertEquals("nicovideo.jp", normalizeDomain("  NicoVideo.JP.  "))
        assertEquals("example.com", normalizeDomain("http://example.com:8080/path"))
        assertEquals("shonenjumpplus.com", normalizeDomain("https://shonenjumpplus.com/episode/123456?utm_source=x"))
    }

    @Test
    fun `ホスト名として成立しない入力は空`() {
        assertEquals("", normalizeDomain("example"))
        assertEquals("", normalizeDomain("マンガ サイト.com"))
        assertEquals("", normalizeDomain(""))
    }
}
