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
 *
 * Формат v4: названия и описания хранятся двумя отдельными таблицами, а в каждой передаче —
 * только их номера. В старом формате при чтении на каждую передачу создавалась новая строка
 * (split), а при записи — ещё одна копия «название + описание»; на большом XMLTV этого
 * хватало, чтобы съесть память ТВ-бокса.
 */
object EpgCache {
    /** Защита от повреждённого кэша: отказ от заведомо нереалистичных чисел до выделения памяти. */
    private const val MAX_CHANNELS = 5000
    private const val MAX_TEXTS = 1000000
    private const val MAX_PROGRAMS = 50000

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
        val titles = LinkedHashMap<String, Int>()
        val descs = LinkedHashMap<String, Int>()
        for (l in map.values) for (p in l) {
            titles.getOrPut(p.title) { titles.size }
            descs.getOrPut(p.description) { descs.size }
        }
        // пишем во временный файл и переименовываем: прерванная запись не должна испортить рабочий кэш
        val tmp = File(cf.parentFile, cf.name + ".tmp")
        try {
            DataOutputStream(BufferedOutputStream(FileOutputStream(tmp), 65536)).use { o ->
                o.writeUTF(key); o.writeLong(src)
                o.writeInt(chKeys.size)
                for (i in chKeys.indices) { o.writeUTF(chKeys[i]); o.writeUTF(eids[i]) }
                o.writeInt(titles.size); titles.keys.forEach { o.writeUTF(it.take(Iptv.MAX_TITLE)) }
                o.writeInt(descs.size); descs.keys.forEach { o.writeUTF(it.take(Iptv.MAX_DESC)) }
                o.writeInt(map.size)
                for ((id, l) in map) {
                    o.writeUTF(id); o.writeInt(l.size)
                    for (p in l) {
                        o.writeLong(p.s); o.writeLong(p.e)
                        o.writeInt(titles[p.title] ?: 0); o.writeInt(descs[p.description] ?: 0)
                    }
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

    /** null — кэш относится к другому источнику (не совпал [key]) или повреждён. Совпадение [Data.src] с файлом проверяет вызывающий. */
    fun load(cf: File, key: String): Data? {
        DataInputStream(BufferedInputStream(FileInputStream(cf), 65536)).use { i ->
            if (i.readUTF() != key) return null
            val src = i.readLong()
            val n = i.readInt()
            if (n < 0 || n > MAX_CHANNELS) return null
            val keys = ArrayList<String>(n); val eids = ArrayList<String>(n)
            repeat(n) { keys.add(i.readUTF()); eids.add(i.readUTF()) }
            val nt = i.readInt()
            if (nt < 0 || nt > MAX_TEXTS) return null
            val titles = ArrayList<String>(nt)
            repeat(nt) { titles.add(i.readUTF()) }
            val nd = i.readInt()
            if (nd < 0 || nd > MAX_TEXTS) return null
            val descs = ArrayList<String>(nd)
            repeat(nd) { descs.add(i.readUTF()) }
            val nm = i.readInt()
            if (nm < 0 || nm > MAX_CHANNELS) return null
            val map = HashMap<String, List<Prog>>()
            repeat(nm) {
                val id = i.readUTF()
                val np = i.readInt()
                if (np < 0 || np > MAX_PROGRAMS) return null
                val l = ArrayList<Prog>(np)
                repeat(np) {
                    val s = i.readLong(); val e = i.readLong()
                    val ti = i.readInt(); val di = i.readInt()
                    if (ti !in titles.indices || di !in descs.indices) return null
                    l.add(Prog(s, e, titles[ti], descs[di]))
                }
                map[id] = l
            }
            return Data(src, keys, eids, map)
        }
    }
}
