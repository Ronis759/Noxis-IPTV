package app.iptv.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.Charset
import java.util.zip.GZIPOutputStream

class IptvTest {
    private fun ch(url: String, ctype: String = "", csrc: String = "", name: String = "Канал") =
        Channel(0, "", name, "", "", "Группа", url, 0, ctype, csrc)

    private val s = 1700000000L
    private val e = 1700003600L

    // ---------- M3U ----------

    @Test fun m3u_attributes_name_group_header() {
        val t = """#EXTM3U url-tvg="http://epg.example/x.xml.gz, http://other" catchup="shift"
#EXTINF:-1 tvg-id="a1" tvg-name="Первый HD" tvg-logo="http://l/a.png" group-title="Новости" catchup-days="3",Первый, канал
http://h/a
#EXTINF:-1,Без атрибутов
#EXTGRP:Кино
http://h/b
"""
        val p = Iptv.parseM3u(t)
        assertEquals("http://epg.example/x.xml.gz", p.epgUrl)
        assertEquals(2, p.ch.size)
        val a = p.ch[0]
        assertEquals("a1", a.id)
        assertEquals("Первый, канал", a.name)      // запятая в названии не обрезает его
        assertEquals("Первый HD", a.tname)
        assertEquals("http://l/a.png", a.logo)
        assertEquals("Новости", a.group)
        assertEquals(3, a.days)
        assertEquals("shift", a.ctype)             // тип архива берётся из заголовка
        assertEquals("http://h/a", a.url)
        val b = p.ch[1]
        assertEquals("Без атрибутов", b.name)
        assertEquals("Кино", b.group)              // #EXTGRP после #EXTINF
        assertEquals(1, b.idx)
    }

    @Test fun m3u_comma_inside_quoted_attribute() {
        val p = Iptv.parseM3u("#EXTM3U\n#EXTINF:-1 tvg-name=\"A, B\",Имя\nhttp://h/x\n")
        assertEquals("Имя", p.ch[0].name)
        assertEquals("A, B", p.ch[0].tname)
        assertEquals("Без группы", p.ch[0].group)
    }

    @Test fun m3u_duplicate_names_get_unique_order_keys() {
        val p = Iptv.parseM3u("#EXTM3U\n#EXTINF:-1,Кино\nhttp://h/1\n#EXTINF:-1,Кино\nhttp://h/2\n")
        assertEquals("Кино", p.ch[0].okey)
        assertEquals("Кино#2", p.ch[1].okey)
    }

    @Test fun m3u_url_without_extinf_is_ignored() {
        assertEquals(0, Iptv.parseM3u("#EXTM3U\nhttp://h/orphan\n").ch.size)
    }

    // ---------- servers.txt ----------

    @Test fun servers_file() {
        val t = """# комментарий
Домашний | https://example.com/p.m3u | https://example.com/e.xml.gz
Дача | https://example.org/p.m3u
https://solo.example.net/list.m3u
ftp://bad | x
// ещё комментарий
"""
        val l = Iptv.parseServers(t)
        assertEquals(3, l.size)
        assertEquals("Домашний", l[0].name)
        assertEquals("https://example.com/e.xml.gz", l[0].epg)
        assertEquals("", l[1].epg)
        assertEquals("solo.example.net", l[2].name)   // без названия — имя хоста
    }

    // ---------- ссылки на архив ----------

    @Test fun archive_flussonic_hls_and_mpegts() {
        assertEquals(
            "http://h/live/a/index-1700000000-3600.m3u8?token=1",
            Iptv.arcUrl(ch("http://h/live/a/index.m3u8?token=1", "flussonic"), s, e, "flussonic")
        )
        assertEquals(
            "http://h/live/a/timeshift_abs-1700000000.ts?token=1",
            Iptv.arcUrl(ch("http://h/live/a/mpegts?token=1", "flussonic"), s, e, "flussonic")
        )
    }

    @Test fun archive_catchup_source_template() {
        assertEquals(
            "http://h/a?s=1700000000&d=3600",
            Iptv.arcUrl(ch("http://h/a", csrc = "?s={start}&d={duration}"), s, e, "flussonic")
        )
    }

    @Test fun archive_append_and_shift_fallback() {
        assertTrue(Iptv.arcUrl(ch("http://h/a", "append"), s, e, "x").startsWith("http://h/a?utc=1700000000&lutc="))
        assertTrue(Iptv.arcUrl(ch("http://h/a?k=1"), s, e, "shift").startsWith("http://h/a?k=1&utc=1700000000&lutc="))
    }

    @Test fun archive_candidates_are_deduplicated() {
        val l = Iptv.arcCandidates(ch("http://h/live/a/index.m3u8", "flussonic"), s, e, "flussonic")
        assertEquals(listOf(0, 1, 2), l.map { it.first })   // формат index-… совпал с первым и убран
        assertEquals(l.size, l.map { it.second }.distinct().size)
    }

    // ---------- кодировка и размер ----------

    @Test fun decode_utf8_and_cp1251() {
        assertEquals("Привет", Iptv.decode("Привет".toByteArray(Charsets.UTF_8)))
        assertEquals("Привет", Iptv.decode("Привет".toByteArray(Charset.forName("windows-1251"))))
    }

    @Test fun text_strips_bom_and_unpacks_gzip() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "abc".toByteArray()
        assertEquals("abc", Iptv.text(bom))
        val bo = ByteArrayOutputStream()
        GZIPOutputStream(bo).use { it.write("Привет".toByteArray(Charsets.UTF_8)) }
        assertEquals("Привет", Iptv.text(bo.toByteArray()))
    }

    @Test fun readLimited_enforces_limit() {
        assertEquals(100, Iptv.readLimited(ByteArrayInputStream(ByteArray(100)), 100).size)
        try {
            Iptv.readLimited(ByteArrayInputStream(ByteArray(101)), 100)
            fail("ожидалось TooBigException")
        } catch (x: TooBigException) { /* ok */ }
    }

    // ---------- скрытие ссылок в ошибках ----------

    @Test fun redact_url_drops_credentials_path_and_query() {
        assertEquals("http://host.tld:8080", Iptv.redactUrl("http://user:pass@host.tld:8080/path?x=1"))
        assertEquals("udp://239.1.1.1:1234", Iptv.redactUrl("udp://@239.1.1.1:1234"))
    }

    @Test fun safeMsg_removes_secrets_from_message() {
        val m = Iptv.safeMsg(IOException("Failed http://h.tld/pl.m3u?key=SECRET now"))
        assertEquals("Failed http://h.tld now", m)
        assertFalse(m.contains("SECRET"))
        assertEquals("IOException", Iptv.safeMsg(IOException()))
    }

    // ---------- сопоставление программы ----------

    @Test fun epg_matching_by_id_and_by_name() {
        val epg = Epg(emptyMap(), mapOf("первый" to "ch1"), mapOf("ch1" to "ch1"))
        assertEquals("ch1", Iptv.matchEid(epg, Channel(0, "CH1", "x", "", "", "g", "u", 0, "", "")))
        assertEquals("ch1", Iptv.matchEid(epg, Channel(0, "", "Первый HD", "", "", "g", "u", 0, "", "")))
        assertEquals("", Iptv.matchEid(epg, Channel(0, "", "Неизвестный", "", "", "g", "u", 0, "", "")))
        assertEquals("первыйhd", Iptv.norm("Первый HD!"))
    }
}
