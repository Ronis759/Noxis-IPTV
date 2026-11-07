package app.iptv.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefSetTest {
    @Test fun toggle_adds_removes_and_saves() {
        val saved = ArrayList<Set<String>>()
        val s = PrefSet(setOf("a")) { saved.add(it) }
        assertTrue("a" in s)
        assertTrue(s.toggle("b"))
        assertTrue("b" in s)
        assertFalse(s.toggle("a"))
        assertFalse("a" in s)
        assertEquals(listOf(setOf("a", "b"), setOf("b")), saved)
    }

    @Test fun favorites_keep_same_named_channels_apart() {
        val p = Iptv.parseM3u("#EXTM3U\n#EXTINF:-1 group-title=\"A\",Кино\nhttp://h/1\n#EXTINF:-1 group-title=\"B\",Кино\nhttp://h/2\n")
        val favs = PrefSet(setOf("Кино")) { }   // старое избранное хранилось по названию
        assertTrue(p.ch[0].okey in favs)        // первый одноимённый канал остаётся избранным
        assertFalse(p.ch[1].okey in favs)       // его дубль в другой группе — нет
        favs.toggle(p.ch[1].okey)
        assertTrue(p.ch[1].okey in favs)
        assertTrue(p.ch[0].okey in favs)
    }
}
