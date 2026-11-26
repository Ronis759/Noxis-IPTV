package app.iptv.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourcesTest {

    @Test fun parse_trims_drops_empty_and_duplicates() {
        val t = " https://a/p.m3u \n\nlocal:abc\nhttps://a/p.m3u\n  local:abc  \n"
        assertEquals(listOf("https://a/p.m3u", "local:abc"), Sources.parse(t))
    }

    @Test fun parse_keeps_local_file_keys() {
        assertEquals(listOf("local", "local:x1"), Sources.parse("local\nlocal:x1"))
    }

    @Test fun add_new_source() {
        assertEquals(listOf("a", "b"), Sources.add(listOf("a"), "b"))
    }

    @Test fun add_rejects_duplicate_and_blank() {
        assertNull(Sources.add(listOf("a"), " a "))
        assertNull(Sources.add(listOf("a"), "   "))
    }

    @Test fun add_stops_at_ten() {
        val ten = (1..10).map { "s$it" }
        assertNull(Sources.add(ten, "s11"))
        assertEquals(10, Sources.MAX)
    }

    @Test fun parse_limits_to_ten() {
        val t = (1..15).joinToString("\n") { "s$it" }
        assertEquals(10, Sources.parse(t).size)
    }
}
