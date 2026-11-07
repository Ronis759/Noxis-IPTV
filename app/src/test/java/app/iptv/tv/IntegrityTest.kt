package app.iptv.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class IntegrityTest {
    private val abc = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"   // SHA-256("abc")

    @Test fun sha256_of_file() {
        val f = File.createTempFile("integrity", ".bin")
        try {
            f.writeText("abc")
            assertEquals(abc, Integrity.sha256(f))
        } finally { f.delete() }
    }

    @Test fun digest_field_is_parsed_strictly() {
        assertEquals(abc, Integrity.parseDigest("sha256:$abc"))
        assertEquals(abc, Integrity.parseDigest("  SHA256:${abc.uppercase()} "))
        assertNull(Integrity.parseDigest(null))
        assertNull(Integrity.parseDigest(""))
        assertNull(Integrity.parseDigest("sha1:$abc"))
        assertNull(Integrity.parseDigest("sha256:zz"))
        assertNull(Integrity.parseDigest("sha256:" + abc.dropLast(1)))
    }

    @Test fun sum_file_formats() {
        assertEquals(abc, Integrity.parseSumFile("$abc  app-release.apk\n"))
        assertEquals(abc, Integrity.parseSumFile("$abc *app-release.apk"))
        assertEquals(abc, Integrity.parseSumFile("\n$abc\n"))
        assertNull(Integrity.parseSumFile(""))
        assertNull(Integrity.parseSumFile("<html>404</html>"))
    }
}
