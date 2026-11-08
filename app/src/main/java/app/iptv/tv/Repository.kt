package app.iptv.tv

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.io.File
import java.io.IOException

/** Получатель результатов загрузки программы передач; методы вызываются из фонового потока. */
interface EpgListener {
    /** Адрес программы нигде не задан. */
    fun onNoUrl()
    /** [eids] — идентификаторы XMLTV по порядку каналов, [matched] — у скольких каналов нашлась программа. */
    fun onEpg(eids: List<String>, map: Map<String, List<Prog>>, matched: Int)
    fun onError(e: Throwable)
}

/**
 * Слой данных: файлы серверов, плейлист с кэшем, программа передач с кэшем.
 * Ничего не знает об экранах — результат отдаётся возвращаемым значением или через [EpgListener].
 */
class Repository(private val ctx: Context, private val prefs: SharedPreferences) {
    class Loaded(val list: Playlist, val fromCache: Boolean)

    /** Серверы из servers.txt (внутренняя и внешняя папки приложения). Нечитаемый файл пропускается и попадает в журнал. */
    fun servers(): List<Server> {
        val l = ArrayList<Server>()
        for (f in listOfNotNull(File(ctx.filesDir, "servers.txt"), ctx.getExternalFilesDir(null)?.let { File(it, "servers.txt") })) {
            try {
                if (f.exists()) l += Iptv.parseServers(Iptv.text(f.readBytes()))
            } catch (e: Exception) {
                Log.w("Repository", L.f("не удалось прочитать {0}: {1}", f.name, e.javaClass.simpleName))
            }
        }
        return l.distinctBy { it.pl }
    }

    /**
     * Сохраняет выбранный пользователем файл: плейлист (есть #EXTINF) — как локальный, иначе — как servers.txt.
     * Читать и писать нужно из фонового потока. true — это был плейлист.
     */
    fun importText(t: String): Boolean {
        val isPlaylist = t.contains("#EXTINF")
        if (isPlaylist) {
            File(ctx.filesDir, "local.m3u").writeText(t)
            prefs.edit().putString("pl", "local").remove("srvn").apply()
        } else {
            File(ctx.filesDir, "servers.txt").writeText(t)
        }
        return isPlaylist
    }

    /** Загружает плейлист [pl] (адрес или «local»); при недоступном сервере берёт сохранённую копию. Вызывать из фонового потока. */
    fun loadPlaylist(pl: String): Loaded {
        File(ctx.filesDir, "pl.m3u").delete()   // старый общий кэш: он не различал серверы
        val f = File(ctx.filesDir, "pl_" + Integer.toHexString(pl.hashCode()) + ".m3u")
        var cached = false
        val txt = if (pl == "local") File(ctx.filesDir, "local.m3u").readText()
        else try { Iptv.text(Iptv.http(pl)).also { f.writeText(it) } }
        catch (e: Exception) { if (f.exists()) { cached = true; f.readText() } else throw e }
        val list = Iptv.parseM3u(txt)
        if (list.ch.isEmpty()) throw IOException(L.t("в плейлисте нет каналов"))
        return Loaded(list, cached)
    }

    /**
     * Программа передач: сначала мгновенно показывается кэш разобранной программы; свежий файл скачивается и разбирается только когда нужно.
     * [plEpg] — адрес из заголовка плейлиста, [defaultEpg] — запасной. Вызывать из фонового потока.
     */
    fun loadEpg(chs: List<Channel>, plEpg: String, defaultEpg: String, force: Boolean, tz: Int, l: EpgListener) {
        val eu = (prefs.getString("epg", "") ?: "").ifEmpty { plEpg }.ifEmpty { defaultEpg }
        if (eu.isEmpty()) { l.onNoUrl(); return }
        val ef = File(ctx.filesDir, "epg.bin")
        val cf = File(ctx.filesDir, "epg.cache")
        val key = eu + "|" + tz + "|" + chs.size + "|" + chs.joinToString("\u0001") { it.id + "\u0002" + it.name }.hashCode()
        var downloaded = false
        fun download() { Iptv.download(eu, ef); prefs.edit().putString("epgsrc", eu).apply(); downloaded = true }
        fun publish(eids: List<String>, map: Map<String, List<Prog>>): Int {
            var m = 0
            for ((i, c) in chs.withIndex()) { c.eid = eids[i]; if (c.eid.isNotEmpty() && map.containsKey(c.eid)) m++ }
            l.onEpg(eids, map, m)
            return m
        }
        fun parseAndPublish(): Int {
            val e = Iptv.parseEpg(ef, tz, chs)
            val eids = chs.map { Iptv.matchEid(e, it) }
            val m = publish(eids, e.map)
            try { EpgCache.save(cf, key, ef.lastModified(), eids, e.map) } catch (x: Exception) { cf.delete() }
            return m
        }
        try {
            var shownFromCache = false
            if (!force && cf.exists() && ef.exists()) {
                try {
                    val c = EpgCache.load(cf, key, ef.lastModified())
                    if (c != null && c.first.size == chs.size) { publish(c.first, c.second); shownFromCache = true }
                } catch (x: Exception) { cf.delete() }
            }
            val fresh = !force && ef.exists() && System.currentTimeMillis() - ef.lastModified() < 6 * 3600000L && prefs.getString("epgsrc", "") == eu
            if (shownFromCache && fresh) return
            if (!fresh) {
                try { download() } catch (dl: Exception) {
                    // не скачалось — остаёмся на прежних данных, если они от того же источника
                    if (shownFromCache) return
                    if (!(ef.exists() && prefs.getString("epgsrc", "") == eu)) throw dl
                }
            }
            if (shownFromCache && !downloaded) return
            var m = try { parseAndPublish() } catch (ex: Exception) { if (downloaded) throw ex; -1 }
            if (m <= 0 && !downloaded) { try { download(); m = parseAndPublish() } catch (ex: Exception) { if (m < 0) throw ex } }
        } catch (ex: Throwable) {
            l.onError(ex)
        }
    }
}
