package app.iptv.tv

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class KeyMapTest {
    @Test fun defaults_keep_old_behaviour() {
        assertEquals(KeyAction.FAV, KeyMap.resolve(KeyEvent.KEYCODE_PROG_RED, null))
        assertEquals(KeyAction.FAV, KeyMap.resolve(KeyEvent.KEYCODE_STAR, null))
        assertEquals(KeyAction.ASPECT, KeyMap.resolve(KeyEvent.KEYCODE_PROG_YELLOW, null))
        assertEquals(KeyAction.SOUND, KeyMap.resolve(KeyEvent.KEYCODE_PROG_BLUE, null))
        assertEquals(KeyAction.PREV, KeyMap.resolve(KeyEvent.KEYCODE_CHANNEL_UP, null))
        assertEquals(KeyAction.NEXT, KeyMap.resolve(KeyEvent.KEYCODE_CHANNEL_DOWN, null))
        assertEquals(KeyAction.NONE, KeyMap.resolve(KeyEvent.KEYCODE_PROG_GREEN, null))
    }

    @Test fun stored_value_wins_and_unknown_falls_back() {
        assertEquals(KeyAction.SEARCH, KeyMap.resolve(KeyEvent.KEYCODE_PROG_RED, "search"))
        assertEquals(KeyAction.NONE, KeyMap.resolve(KeyEvent.KEYCODE_PROG_RED, "none"))
        assertEquals(KeyAction.FAV, KeyMap.resolve(KeyEvent.KEYCODE_PROG_RED, "bogus"))
    }

    @Test fun navigation_keys_are_not_remappable() {
        assertNull(KeyMap.find(KeyEvent.KEYCODE_DPAD_UP))
        assertNull(KeyMap.find(KeyEvent.KEYCODE_DPAD_CENTER))
        assertNull(KeyMap.find(KeyEvent.KEYCODE_BACK))
        assertNotNull(KeyMap.find(KeyEvent.KEYCODE_PROG_BLUE))
    }

    @Test fun codes_and_ids_are_unique() {
        assertEquals(KeyMap.buttons.size, KeyMap.buttons.map { it.code }.toSet().size)
        assertEquals(KeyAction.values().size, KeyAction.values().map { it.id }.toSet().size)
    }

    @Test fun next_wraps_in_both_directions() {
        val all = KeyAction.values()
        assertEquals(all.first(), KeyMap.next(all.last(), 1))
        assertEquals(all.last(), KeyMap.next(all.first(), -1))
        assertEquals(KeyAction.FAV, KeyMap.next(KeyAction.NONE, 1))
    }
}
