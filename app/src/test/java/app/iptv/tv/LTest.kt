package app.iptv.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LTest {
    @Test fun auto_follows_system_language() {
        assertFalse(L.resolveEn("auto", "ru"))
        assertFalse(L.resolveEn(null, "ru"))
        assertFalse(L.resolveEn("auto", "uk"))   // языки, где русский привычен, остаются на русском
        assertTrue(L.resolveEn("auto", "en"))
        assertTrue(L.resolveEn("auto", "de"))
    }

    @Test fun manual_choice_beats_system_language() {
        assertTrue(L.resolveEn("en", "ru"))
        assertFalse(L.resolveEn("ru", "en"))
    }

    @Test fun russian_text_is_the_source() {
        L.en = false
        assertEquals("Нет канала №5", L.f("Нет канала №{0}", 5))
        assertEquals("Закрыть", L.t("Закрыть"))
    }

    @Test fun english_translates_and_falls_back_to_russian() {
        L.en = true
        try {
            assertEquals("No channel #5", L.f("Нет канала №{0}", 5))
            assertEquals("Close", L.t("Закрыть"))
            assertEquals("Строки нет в словаре", L.t("Строки нет в словаре"))
        } finally { L.en = false }
    }

    @Test fun every_translation_keeps_the_same_placeholders() {
        val slot = Regex("\\{\\d+\\}")
        for ((ru, en) in En.map) {
            assertEquals(ru, slot.findAll(ru).map { it.value }.sorted().toList(), slot.findAll(en).map { it.value }.sorted().toList())
        }
    }
}
