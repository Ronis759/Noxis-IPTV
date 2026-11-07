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
    /** [key] — признак того, что кэш собран для тех же адреса, пояса и списка каналов; [src] — время изменения исходного файла. */
    fun save(cf: File, key: String, src: Long, eids: List<String>, map: Map<String, List<Prog>>) {
        val titles = LinkedHashMap<String, Int>()
        for (l in map.values) for (p in l) titles.getOrPut(p.title) { titles.size }
        DataOutputStream(BufferedOutputStream(FileOutputStream(cf), 65536)).use { o ->
            o.writeUTF(key); o.writeLong(src)
            o.writeInt(eids.size); eids.forEach { o.writeUTF(it) }
            o.writeInt(titles.size); titles.keys.forEach { o.writeUTF(it) }
            o.writeInt(map.size)
            for ((id, l) in map) {
                o.writeUTF(id); o.writeInt(l.size)
                for (p in l) { o.writeLong(p.s); o.writeLong(p.e); o.writeInt(titles[p.title]!!) }
            }
        }
    }

    /** null — кэш относится к другим данным (не совпали [key] или [src]). */
    fun load(cf: File, key: String, src: Long): Pair<List<String>, Map<String, List<Prog>>>? {
        DataInputStream(BufferedInputStream(FileInputStream(cf), 65536)).use { i ->
            if (i.readUTF() != key || i.readLong() != src) return null
            val eids = List(i.readInt()) { i.readUTF() }
            val titles = List(i.readInt()) { i.readUTF() }
            val map = HashMap<String, List<Prog>>()
            repeat(i.readInt()) {
                val id = i.readUTF()
                map[id] = List(i.readInt()) { Prog(i.readLong(), i.readLong(), titles[i.readInt()]) }
            }
            return eids to map
        }
    }
}
