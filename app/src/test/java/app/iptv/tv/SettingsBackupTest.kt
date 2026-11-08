package app.iptv.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SettingsBackupTest {
    private val prefs: Map<String, Any?> = mapOf(
        "pl" to "http://h/p.m3u?u=a&p=\"b\"", "epg" to "", "srvn" to "Сервер \"1\"", "ct" to "flussonic",
        "tzm" to 180, "fs" to 120, "dim" to 85, "theme" to "teal", "sort" to "custom",
        "order" to "A\nБ\nC", "fav" to setOf("Кино", "Кино#2"), "hg" to emptySet<String>(),
        "km_183" to "fav", "arcv_host" to 2,
        // служебное — в копию не попадает
        "reopen" to true, "upd_info" to "x", "epgsrc" to "http://e", "gua" to "VLC"
    )

    @Test fun roundtrip_keeps_values_and_types() {
        val r = SettingsBackup.parse(SettingsBackup.export(prefs, "2.3.0", "2026-11-08 12:00"))
        assertEquals("http://h/p.m3u?u=a&p=\"b\"", r["pl"])
        assertEquals("Сервер \"1\"", r["srvn"])
        assertEquals("A\nБ\nC", r["order"])
        assertEquals(180, r["tzm"])
        assertEquals(120, r["fs"])
        assertEquals(setOf("Кино", "Кино#2"), r["fav"])
        assertEquals(emptySet<String>(), r["hg"])
        assertEquals("fav", r["km_183"])
        assertEquals(2, r["arcv_host"])
    }

    @Test fun service_keys_are_not_exported() {
        val r = SettingsBackup.parse(SettingsBackup.export(prefs, "2.3.0", "d"))
        for (k in listOf("reopen", "upd_info", "epgsrc", "gua")) assertFalse(k in r)
        assertFalse(SettingsBackup.isBackupKey("reopen"))
        assertTrue(SettingsBackup.isBackupKey("km_82"))
        assertTrue(SettingsBackup.isBackupKey("fav"))
        assertFalse(SettingsBackup.isBackupKey("km_"))
    }

    @Test fun wrong_types_and_unknown_keys_are_dropped() {
        val t = """{"format":"noxis-iptv-settings","version":1,"settings":{
            "fs":"big","dim":90.5,"tzm":3,"pl":5,"fav":["a",1,null,"b"],"evil":"x","reopen":true,"ct":null}}"""
        val r = SettingsBackup.parse(t)
        assertEquals(mapOf<String, Any>("tzm" to 3, "fav" to setOf("a", "b")), r)
    }

    @Test fun bom_and_escapes_are_read() {
        val t = "\uFEFF{\"format\":\"noxis-iptv-settings\",\"version\":1,\"settings\":{\"pl\":\"a\\u0041\\/\\n\"}}"
        assertEquals("aA/\n", SettingsBackup.parse(t)["pl"])
    }

    private fun bad(t: String): String {
        try { SettingsBackup.parse(t) } catch (e: SettingsBackup.BadBackup) { return e.message ?: "" }
        fail("ожидалась ошибка для: $t")
        return ""
    }

    @Test fun rejects_foreign_broken_and_newer_files() {
        bad("")
        bad("#EXTM3U\n#EXTINF:-1,x\nhttp://h/1")
        bad("[1,2]")
        bad("{\"format\":\"other\",\"version\":1,\"settings\":{\"pl\":\"x\"}}")
        bad("{\"format\":\"noxis-iptv-settings\",\"version\":1,\"settings\":{\"pl\":\"x\"}} trailing")
        bad("{\"format\":\"noxis-iptv-settings\",\"version\":1,\"settings\":{\"pl\":\"x\"")
        assertTrue(bad("{\"format\":\"noxis-iptv-settings\",\"version\":99,\"settings\":{\"pl\":\"x\"}}").contains("новой"))
        bad("{\"format\":\"noxis-iptv-settings\",\"version\":1}")
        bad("{\"format\":\"noxis-iptv-settings\",\"version\":1,\"settings\":{\"unknown\":\"x\"}}")
    }

    @Test fun deeply_nested_json_is_rejected_not_crashed() {
        bad("[".repeat(10000))
        bad("{\"a\":".repeat(10000))
    }

    @Test fun empty_prefs_export_is_rejected_on_import() {
        bad(SettingsBackup.export(emptyMap<String, Any?>(), "1", "d"))
        assertNull(runCatching { SettingsBackup.parse(SettingsBackup.export(mapOf("fs" to 100), "1", "d"))["pl"] }.getOrNull())
    }
}
