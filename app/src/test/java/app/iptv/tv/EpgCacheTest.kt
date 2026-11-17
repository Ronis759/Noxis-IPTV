package app.iptv.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class EpgCacheTest {
    @get:Rule val tmp = TemporaryFolder()

    private val map = mapOf(
        "a" to listOf(Prog(100, 200, "Новости"), Prog(200, 300, "Кино")),
        "b" to listOf(Prog(100, 150, "Новости"))
    )

    @Test fun roundtrip() {
        val f = tmp.newFile("epg.cache")
        EpgCache.save(f, "key", 42L, listOf("1|A", "2|B", "3|C"), listOf("a", "", "b"), map)
        val r = EpgCache.load(f, "key")
        assertNotNull(r)
        assertEquals(42L, r!!.src)
        assertEquals(listOf("1|A", "2|B", "3|C"), r.chKeys)
        assertEquals(listOf("a", "", "b"), r.eids)
        assertEquals(2, r.map.size)
        val a = r.map["a"]!!
        assertEquals(2, a.size)
        assertEquals(200L, a[1].s); assertEquals(300L, a[1].e); assertEquals("Кино", a[1].title)
        assertEquals("Новости", r.map["b"]!![0].title)
    }

    @Test fun other_source_is_rejected() {
        val f = tmp.newFile("epg2.cache")
        EpgCache.save(f, "key", 42L, listOf("1|A"), listOf("a"), map)
        assertNull(EpgCache.load(f, "other"))   // другой адрес/пояс
    }

    @Test fun save_leaves_no_temp_file() {
        val f = tmp.newFile("epg3.cache")
        EpgCache.save(f, "key", 1L, listOf("1|A"), listOf("a"), map)
        assertEquals(false, File(f.parentFile, "epg3.cache.tmp").exists())
    }

    /** Разбор XMLTV через kxml2: android.util.Xml в JVM-тестах недоступен. */
    private fun parse(xml: String): Epg {
        val f = File.createTempFile("epg", ".xml")
        try {
            f.writeText(xml)
            return Iptv.parseEpg(f, 0, null, { org.kxml2.io.KXmlParser() })
        } finally { f.delete() }
    }

    /** Очень длинное описание не должно попадать в память и кэш целиком. */
    @Test fun long_description_is_truncated() {
        val long = "Описание".repeat(1000)
        val fmt = SimpleDateFormat("yyyyMMddHHmmss ' +0000'", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val now = System.currentTimeMillis() / 1000
        val start = fmt.format(Date((now - 3600) * 1000))
        val stop = fmt.format(Date(now * 1000))
        val e = parse(
            """<tv><channel id="a"><display-name>A</display-name></channel>
               <programme start="$start" stop="$stop" channel="a">
                 <title>Передача</title><desc>$long</desc>
               </programme></tv>"""
        )
        val p = e.map["a"]!!.first()
        assertEquals(Iptv.MAX_DESC, p.description.length)
        assertEquals("Передача", p.title)
    }

    /** Повреждённый кэш с нереалистичным числом каналов отклоняется до выделения памяти. */
    @Test fun corrupt_cache_is_rejected() {
        val f = tmp.newFile("epg4.cache")
        DataOutputStream(FileOutputStream(f)).use { o ->
            o.writeUTF("key"); o.writeLong(1L); o.writeInt(Int.MAX_VALUE)
        }
        assertNull(EpgCache.load(f, "key"))
    }
}
