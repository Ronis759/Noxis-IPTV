package app.iptv.tv

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/** Контрольные суммы SHA-256 для проверки скачанного обновления. Без зависимостей от Android: проверяется юнит-тестами. */
object Integrity {
    private val HEX64 = Regex("^[0-9a-f]{64}$")

    /** SHA-256 файла в нижнем регистре (hex). */
    fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(f).use { i ->
            val buf = ByteArray(65536)
            while (true) {
                val n = i.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Поле `digest` ассета GitHub («sha256:<hex>»). null — поля нет или оно не SHA-256. */
    fun parseDigest(s: String?): String? {
        val t = s?.trim()?.lowercase() ?: return null
        if (!t.startsWith("sha256:")) return null
        return t.removePrefix("sha256:").takeIf { HEX64.matches(it) }
    }

    /** Файл `*.sha256` (формат `sha256sum`: «<hex>  имя» или просто «<hex>»). null — суммы в тексте нет. */
    fun parseSumFile(text: String): String? {
        val first = text.trim().lineSequence().firstOrNull()?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.lowercase()
        return first?.takeIf { HEX64.matches(it) }
    }
}
