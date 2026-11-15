package app.iptv.tv

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Быстрый бинарный кэш уже разобранной программы передач: читается за доли секунды,
 * в отличие от разбора большого XMLTV. Вынесено из MainActivity.
 */
object EpgCache {
    /**
     * Содержимое кэша. [chKeys] и [eids] — параллельные списки: ключ канала плейлиста («id␂название») и его идентификатор в XMLTV.
     * [src] — время изменения файла программы, из которого кэш собран.
     */
    class Data(val src: Long, val chKeys: List<String>, val eids: List<String>, val map: Map<String, List<Prog>>)

    /**
     * [key] — признак источника (адрес и пояс); от списка каналов он намеренно НЕ зависит, чтобы правка плейлиста
     * не стирала программу. [src] — время изменения исходного файла.
     */
    fun save(cf: File, key: String, src: Long, chKeys: List<String>, eids: List<String>, map: Map<String, List<Prog>>) {
        val texts = LinkedHashMap<String, Int>()
        for (l in map.values) for (p in l) texts.getOrPut(p.title + "\u0000" + p.description) { texts.size }
        // пишем во временный файл и переименовываем: прерванная запись не должна испортить рабочий кэш
        val tmp = File(cf.parentFile, cf.name + ".tmp")
        try {
            DataOutputStream(BufferedOutputStream(FileOutputStream(tmp), 65536)).use { o ->
                o.writeUTF(key); o.writeLong(src)
                o.writeInt(chKeys.size); for (i in chKeys.indices) { o.writeUTF(chKeys[i]); o.writeUTF(eids[i]) }
                o.writeInt(texts.size); texts.keys.forEach { o.writeUTF(it) }
                o.writeInt(map.size)
                for ((id, l) in map) {
                    o.writeUTF(id); o.writeInt(l.size)
                    for (p in l) { o.writeLong(p.s); o.writeLong(p.e); o.writeInt(texts[p.title + "\u0000" + p.description]!!) }
                }
            }
            if (!tmp.renameTo(cf)) {
                cf.delete()
                if (!tmp.renameTo(cf)) throw java.io.IOException("rename failed")
            }
        } catch (e: Exception) {
            // При любой ошибке удаляем временный файл
            tmp.delete()
            throw e
        }
    }

    /** null — кэш относится к другому источнику (не совпал [key]). Совпадение [Data.src] с файлом проверяет вызывающий. */
    fun load(cf: File, key: String): Data? {
        DataInputStream(BufferedInputStream(FileInputStream(cf), 65536)).use { i ->
            if (i.readUTF() != key) return null
            val src = i.readLong()
            val n = i.readInt()
            val keys = ArrayList<String>(n); val eids = ArrayList<String>(n)
            repeat(n) { keys.add(i.readUTF()); eids.add(i.readUTF()) }
            val texts = List(i.readInt()) { i.readUTF() }
            val map = HashMap<String, List<Prog>>()
            repeat(i.readInt()) {
                val id = i.readUTF()
                map[id] = List(i.readInt()) {
                    val s = i.readLong()
                    val e = i.readLong()
                    val parts = texts[i.readInt()].split("\u0000", limit = 2)
                    Prog(s, e, parts[0], parts.getOrElse(1) { "" })
                }
            }
            return Data(src, keys, eids, map)
        }
    }
}
