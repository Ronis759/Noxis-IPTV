package app.iptv.tv

/**
 * Сохранение настроек в файл и восстановление из него. Без зависимостей от Android: проверяется юнит-тестами.
 *
 * В файл попадают только известные настройки (см. [typeOf]); служебные (проверка обновлений, кэш программы передач,
 * флаг «открыть настройки») не сохраняются. Плейлист-файл («local») и servers.txt в копию не входят — это отдельные файлы.
 * При чтении неизвестные ключи и значения неверного типа отбрасываются: иначе `prefs.getInt` на строке роняет приложение.
 */
object SettingsBackup {
    const val FORMAT = "noxis-iptv-settings"
    const val VERSION = 1
    const val FILE_NAME = "noxis-settings.json"
    /** Больше этого файл настроек не читаем (история и «продолжить» занимают единицы килобайт). */
    const val MAX_BYTES = 4L * 1024 * 1024

    /** Ошибка чтения файла настроек; [message] можно показывать пользователю. */
    class BadBackup(message: String) : Exception(message)

    private enum class T { STR, INT, SET }

    private val exact: Map<String, T> = mapOf(
        "pl" to T.STR, "epg" to T.STR, "srvn" to T.STR, "ua" to T.STR, "ct" to T.STR,
        "theme" to T.STR, "aspect" to T.STR, "alang" to T.STR, "sub" to T.STR,
        "sort" to T.STR, "zapm" to T.STR, "order" to T.STR, "start" to T.STR, "lgrp" to T.STR, "lastk" to T.STR,
        "hist" to T.STR, "resume" to T.STR,
        "tzm" to T.INT, "tz" to T.INT, "fs" to T.INT, "dim" to T.INT, "last" to T.INT, "ctv" to T.INT,
        "fav" to T.SET, "hg" to T.SET
    )

    private const val MAX_SET = 100_000
    private const val MAX_STR = 1_000_000

    private fun typeOf(key: String): T? = exact[key] ?: when {
        key.startsWith(KeyMap.PREF) && key.length > KeyMap.PREF.length -> T.STR   // назначения кнопок пульта: id действия
        key.startsWith("arcv_") && key.length > 5 -> T.INT                         // запомненный формат ссылки архива по серверу
        else -> null
    }

    /** true — настройка входит в резервную копию (и при восстановлении заменяется). */
    fun isBackupKey(key: String): Boolean = typeOf(key) != null

    /**
     * Текст файла настроек. [all] — `SharedPreferences.all`; значения неизвестных или неподходящих типов пропускаются.
     * [app] — версия приложения, [created] — дата создания (только для информации в файле).
     */
    fun export(all: Map<String, *>, app: String, created: String): String {
        val keys = all.keys.filter { isBackupKey(it) }.sorted()
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"format\": ").append(quote(FORMAT)).append(",\n")
        sb.append("  \"version\": ").append(VERSION).append(",\n")
        sb.append("  \"app\": ").append(quote(app)).append(",\n")
        sb.append("  \"created\": ").append(quote(created)).append(",\n")
        sb.append("  \"settings\": {")
        var first = true
        for (k in keys) {
            val v = all[k]
            val json = when (typeOf(k)) {
                T.STR -> (v as? String)?.let { quote(it) }
                T.INT -> (v as? Int)?.toString()
                T.SET -> (v as? Set<*>)?.let { s -> s.filterIsInstance<String>().sorted().joinToString(", ", "[", "]") { quote(it) } }
                null -> null
            } ?: continue
            sb.append(if (first) "\n" else ",\n").append("    ").append(quote(k)).append(": ").append(json)
            first = false
        }
        sb.append(if (first) "}\n" else "\n  }\n")
        sb.append("}\n")
        return sb.toString()
    }

    /**
     * Разбирает файл настроек и возвращает проверенные значения: String, Int или Set<String>.
     * @throws BadBackup файл не от этого приложения, повреждён, создан более новой версией или в нём нет настроек
     */
    fun parse(text: String): Map<String, Any> {
        val root = try {
            Json(text.removePrefix("\uFEFF")).readDocument()
        } catch (e: IllegalArgumentException) {
            throw BadBackup("Это не файл настроек (ошибка в JSON)")
        } as? Map<*, *> ?: throw BadBackup("Это не файл настроек")
        if (root["format"] != FORMAT) throw BadBackup("Это не файл настроек Noxis IPTV")
        val ver = (root["version"] as? Number)?.toInt() ?: 0
        if (ver < 1) throw BadBackup("Неизвестная версия файла настроек")
        if (ver > VERSION) throw BadBackup("Файл создан более новой версией приложения — обновите программу")
        val st = root["settings"] as? Map<*, *> ?: throw BadBackup("В файле нет настроек")
        val out = LinkedHashMap<String, Any>()
        for ((k, v) in st) {
            if (k !is String) continue
            when (typeOf(k) ?: continue) {
                T.STR -> if (v is String && v.length <= MAX_STR) out[k] = v
                T.INT -> if (v is Number && v.toDouble() == Math.floor(v.toDouble()) && Math.abs(v.toDouble()) <= Int.MAX_VALUE) out[k] = v.toInt()
                T.SET -> if (v is List<*> && v.size <= MAX_SET) out[k] = v.filterIsInstance<String>().toSet()
            }
        }
        if (out.isEmpty()) throw BadBackup("В файле нет подходящих настроек")
        return out
    }

    private fun quote(s: String): String {
        val sb = StringBuilder(s.length + 2)
        sb.append('"')
        for (c in s) when {
            c == '"' -> sb.append("\\\"")
            c == '\\' -> sb.append("\\\\")
            c == '\n' -> sb.append("\\n")
            c == '\r' -> sb.append("\\r")
            c == '\t' -> sb.append("\\t")
            c < ' ' -> sb.append("\\u%04x".format(c.code))
            else -> sb.append(c)
        }
        return sb.append('"').toString()
    }

    /** Минимальный разбор JSON: объекты, массивы, строки, числа, true/false/null. Ошибка — IllegalArgumentException. */
    private class Json(private val s: String) {
        private var i = 0

        fun readDocument(): Any? {
            val v = value(0)
            ws()
            if (i != s.length) fail()
            return v
        }

        private fun fail(): Nothing = throw IllegalArgumentException("json at $i")

        private fun ws() { while (i < s.length && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t')) i++ }

        private fun value(depth: Int): Any? {
            if (depth > 16) fail()
            ws()
            if (i >= s.length) fail()
            return when (s[i]) {
                '{' -> obj(depth)
                '[' -> arr(depth)
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> num()
            }
        }

        private fun lit(w: String, v: Any?): Any? {
            if (!s.startsWith(w, i)) fail()
            i += w.length
            return v
        }

        private fun num(): Number {
            val st = i
            if (i < s.length && s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] == '.' || s[i] == 'e' || s[i] == 'E' || s[i] == '+' || s[i] == '-')) i++
            val t = s.substring(st, i)
            return t.toLongOrNull() ?: t.toDoubleOrNull() ?: fail()
        }

        private fun str(): String {
            i++   // открывающая кавычка
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) fail()
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= s.length) fail()
                        when (val e = s[i++]) {
                            '"', '\\', '/' -> sb.append(e)
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> {
                                if (i + 4 > s.length) fail()
                                sb.append((s.substring(i, i + 4).toIntOrNull(16) ?: fail()).toChar())
                                i += 4
                            }
                            else -> fail()
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun arr(depth: Int): List<Any?> {
            i++
            val l = ArrayList<Any?>()
            ws()
            if (i < s.length && s[i] == ']') { i++; return l }
            while (true) {
                l.add(value(depth + 1))
                ws()
                if (i >= s.length) fail()
                when (s[i++]) { ',' -> {}; ']' -> return l; else -> fail() }
            }
        }

        private fun obj(depth: Int): Map<String, Any?> {
            i++
            val m = LinkedHashMap<String, Any?>()
            ws()
            if (i < s.length && s[i] == '}') { i++; return m }
            while (true) {
                ws()
                if (i >= s.length || s[i] != '"') fail()
                val k = str()
                ws()
                if (i >= s.length || s[i++] != ':') fail()
                m[k] = value(depth + 1)
                ws()
                if (i >= s.length) fail()
                when (s[i++]) { ',' -> {}; '}' -> return m; else -> fail() }
            }
        }
    }
}
