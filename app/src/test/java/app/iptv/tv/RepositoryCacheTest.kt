package app.iptv.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.File

/** Файлы кэша плейлистов: имя файла должно совпадать при сохранении и при чтении кэша при запуске. */
class RepositoryCacheTest {
    private val dir = File("cache")

    /** Локальный плейлист без метки и с меткой: два разных файла, префикса «local:» в имени нет. */
    @Test fun local_playlists_have_own_files() {
        assertEquals("local.m3u", Repository.playlistFile(dir, "local").name)
        assertEquals("local_другая.m3u", Repository.playlistFile(dir, "local:другая").name)
        assertEquals("local_метка.m3u", Repository.playlistFile(dir, "local:метка").name)
        assertNotEquals(
            Repository.playlistFile(dir, "local:метка").name,
            Repository.playlistFile(dir, "local:другая").name
        )
    }

    /** Сетевые плейлисты кэшируются по адресу: один и тот же адрес — тот же файл, разные — разные. */
    @Test fun network_playlists_are_cached_by_url() {
        val a = Repository.playlistFile(dir, "http://example.com/a.m3u")
        val b = Repository.playlistFile(dir, "http://example.com/a.m3u")
        val c = Repository.playlistFile(dir, "http://example.com/b.m3u")
        assertEquals(a.name, b.name)
        assertNotEquals(a.name, c.name)
        assertEquals("cache", a.parentFile?.name)
    }
}
