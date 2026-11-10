package app.iptv.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

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
        assertEquals(false, java.io.File(f.parentFile, "epg3.cache.tmp").exists())
    }
}
