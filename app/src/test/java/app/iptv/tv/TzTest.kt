package app.iptv.tv

import org.junit.Assert.assertEquals
import org.junit.Test

class TzTest {
    @Test fun parse_formats() {
        assertEquals(180, Tz.parse("3"))
        assertEquals(180, Tz.parse("+3"))
        assertEquals(-180, Tz.parse("-3"))
        assertEquals(330, Tz.parse("5.5"))
        assertEquals(330, Tz.parse("5,5"))
        assertEquals(330, Tz.parse("5:30"))
        assertEquals(-330, Tz.parse("-5:30"))
    }

    @Test fun parse_bad_and_out_of_range() {
        assertEquals(0, Tz.parse(""))
        assertEquals(0, Tz.parse("abc"))
        assertEquals(14 * 60, Tz.parse("20"))
        assertEquals(-14 * 60, Tz.parse("-20"))
    }

    @Test fun format_roundtrip() {
        assertEquals("0", Tz.format(0))
        assertEquals("3", Tz.format(180))
        assertEquals("-3", Tz.format(-180))
        assertEquals("5:30", Tz.format(330))
        assertEquals("-5:30", Tz.format(-330))
        for (m in listOf(0, 180, -180, 330, -330, 345)) assertEquals(m, Tz.parse(Tz.format(m)))
    }
}
