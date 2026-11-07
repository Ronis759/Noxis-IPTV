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
        EpgCache.save(f, "key", 42L, listOf("a", "", "b"), map)
        val r = EpgCache.load(f, "key", 42L)
        assertNotNull(r)
        assertEquals(listOf("a", "", "b"), r!!.first)
        assertEquals(2, r.second.size)
        val a = r.second["a"]!!
        assertEquals(2, a.size)
        assertEquals(200L, a[1].s); assertEquals(300L, a[1].e); assertEquals("Кино", a[1].title)
        assertEquals("Новости", r.second["b"]!![0].title)
    }

    @Test fun stale_cache_is_rejected() {
        val f = tmp.newFile("epg2.cache")
        EpgCache.save(f, "key", 42L, listOf("a"), map)
        assertNull(EpgCache.load(f, "other", 42L))   // другой источник/пояс/список каналов
        assertNull(EpgCache.load(f, "key", 43L))     // файл программы обновился
    }
}
