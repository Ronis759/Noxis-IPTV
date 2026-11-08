package app.iptv.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {
    @Test fun ids_are_unique() {
        assertEquals(Theme.all.size, Theme.all.map { it.id }.toSet().size)
    }

    @Test fun unknown_or_null_id_falls_back_to_default() {
        Theme.use("blue"); assertEquals("blue", Theme.current.id)
        Theme.use("нет такой"); assertEquals(Theme.all[0].id, Theme.current.id)
        Theme.use(null); assertEquals(Theme.all[0].id, Theme.current.id)
    }

    @Test fun colors_are_opaque_where_expected() {
        Theme.use("amber")
        assertEquals(0xFF, Theme.accent ushr 24)
        assertEquals(0xEE, Theme.hud ushr 24)
        assertTrue(Theme.panel in 0..0xFFFFFF)
    }
}
