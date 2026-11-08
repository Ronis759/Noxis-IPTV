package app.iptv.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UpdaterInfoTest {
    private val sha = "a".repeat(64)

    @Test fun info_survives_encode_decode() {
        val i = Updater.Info(42, "v42", "https://github.com/x/y/releases/download/v42/app-release.apk", 1234, sha)
        val d = Updater.Info.decode(i.encode())
        assertNotNull(d)
        assertEquals(42L, d!!.code)
        assertEquals("v42", d.tag)
        assertEquals(i.url, d.url)
        assertEquals(1234L, d.size)
        assertEquals(sha, d.sha256)
    }

    @Test fun broken_or_insecure_info_is_rejected() {
        assertNull(Updater.Info.decode(null))
        assertNull(Updater.Info.decode(""))
        assertNull(Updater.Info.decode("1\nv1\nhttps://h/a.apk\n5"))                 // не хватает суммы
        assertNull(Updater.Info.decode("x\nv1\nhttps://h/a.apk\n5\n$sha"))           // номер не число
        assertNull(Updater.Info.decode("1\nv1\nhttp://h/a.apk\n5\n$sha"))            // не https
        assertNull(Updater.Info.decode("1\nv1\nhttps://h/a.apk\n5\n"))               // пустая сумма
    }
}
