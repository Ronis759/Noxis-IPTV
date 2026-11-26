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

    /** Серверы из server_IPTV.txt (новое имя) и servers.txt (старое), внутренняя и внешняя папки приложения. */
    fun servers(): List<Server> {
        val l = ArrayList<Server>()
        val dirs = listOfNotNull(ctx.filesDir, ctx.getExternalFilesDir(null))
        val names = listOf("server_IPTV.txt", "servers.txt")
        for (d in dirs) for (n in names) {
            val f = File(d, n)
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
     * [key] — «local» или «local:метка». В имени файла метка без префикса «local:»:
     * раньше файл писался как «local_local:метка.m3u», а читался «local_метка.m3u» — выбранный файл «не добавлялся».
     */
    fun importText(t: String, key: String = "local"): Boolean {
        val isPlaylist = t.contains("#EXTINF")
        if (isPlaylist) {
            val id = key.removePrefix("local:")
            if (id.isEmpty()) File(ctx.filesDir, "local.m3u").writeText(t)
            else File(ctx.filesDir, "local_$id.m3u").writeText(t)
        } else {
            File(ctx.filesDir, "server_IPTV.txt").writeText(t)
        }
        return isPlaylist
    }

    /**
     * Сохранённая копия плейлиста [pl] без обращения к сети: экран заполняется сразу при запуске.
     * null — копии ещё нет или файл пуст/битый. Вызывать из фонового потока.
     */
    fun loadCachedPlaylist(pl: String): Loaded? = try {
        val f = playlistFile(ctx.filesDir, pl)
        if (f.exists()) Iptv.parseM3u(f.readText()).takeIf { it.ch.isNotEmpty() }?.let { Loaded(it, true) } else null
    } catch (e: Exception) { null }

    /** Загружает плейлист [pl] (адрес или «local»); при недоступном сервере берёт сохранённую копию. Вызывать из фонового потока. */
    fun loadPlaylist(pl: String): Loaded {
        File(ctx.filesDir, "pl.m3u").delete()   // старый общий кэш: он не различал серверы
        val f = playlistFile(ctx.filesDir, pl)
        var cached = false
        val txt = if (pl.startsWith("local")) f.readText()
        else try { Iptv.text(Iptv.http(pl)).also { f.writeText(it) } }
        catch (e: Exception) { if (f.exists()) { cached = true; f.readText() } else throw e }
        val list = Iptv.parseM3u(txt)
        if (list.ch.isEmpty()) throw IOException(L.t("в плейлисте нет каналов"))
        return Loaded(list, cached)
    }

    private val epgLock = Any()

    /**
     * Программа передач. Правила:
     *  1. Кэш разобранной программы показывается сразу и не зависит ни от списка каналов, ни от наличия скачанного файла.
     *  2. Свежий файл скачивается и разбирается в фоне, только если старому больше [EPG_MAX_AGE_MS] (или кэш не совпал с каналами).
     *  3. Любая ошибка сети/разбора оставляет на экране прежнюю программу.
     * [haveData] — программа уже показана (тихое обновление по таймеру): кэш повторно не читаем.
     * [plEpg] — адрес из заголовка плейлиста, [defaultEpg] — запасной. Вызывать из фонового потока.
     */
    fun loadEpg(chs: List<Channel>, plEpg: String, defaultEpg: String, force: Boolean, tz: Int, l: EpgListener, haveData: Boolean = false) {
        synchronized(epgLock) {   // два обновления одновременно не должны писать в одни и те же файлы
            try { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND) } catch (e: Exception) {}
            val eu = (prefs.getString("epg", "") ?: "").ifEmpty { plEpg }.ifEmpty { defaultEpg }
            if (eu.isEmpty()) { if (!haveData) l.onNoUrl(); return }
            val ef = File(ctx.filesDir, "epg.bin")
            val cf = File(ctx.filesDir, "epg.cache")
            val key = "v4|" + eu + "|" + tz   // с v4 названия и описания в кэше хранятся отдельными таблицами
            val ckeys = chs.map { it.id + "\u0002" + it.name }
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
                try { EpgCache.save(cf, key, ef.lastModified(), ckeys, eids, e.map) } catch (x: Exception) { cf.delete() }
                return m
            }
            try {
                var shown = haveData      // на экране уже есть программа
                var inSync = haveData     // и она соответствует текущему файлу и текущему списку каналов
                if (!force && !haveData && cf.exists()) {
                    try {
                        val c = EpgCache.load(cf, key)
                        if (c != null) {
                            val byKey = HashMap<String, String>()
                            c.chKeys.forEachIndexed { i, k -> byKey[k] = c.eids[i] }
                            publish(ckeys.map { byKey[it] ?: "" }, c.map)
                            shown = true
                            inSync = ef.exists() && c.src == ef.lastModified() && ckeys.all { byKey.containsKey(it) }
                        }
                    } catch (x: Exception) { cf.delete() }
                }
                val canParse = ef.exists() && prefs.getString("epgsrc", "") == eu
                val fresh = !force && canParse && System.currentTimeMillis() - ef.lastModified() < EPG_MAX_AGE_MS
                if (shown && inSync && fresh) return
                if (!fresh) {
                    try { download() } catch (dl: Exception) {
                        // не скачалось — остаёмся на прежних данных
                        if (shown && (inSync || !canParse)) return
                        if (!canParse) throw dl
                    }
                }
                if (shown && inSync && !downloaded) return
                var m = try { parseAndPublish() } catch (ex: Exception) {
                    if (downloaded) { ef.delete(); throw ex }   // битый файл: в следующий раз скачаем заново
                    -1
                }
                if (m <= 0 && !downloaded) { try { download(); m = parseAndPublish() } catch (ex: Exception) { if (m < 0) throw ex } }
            } catch (ex: Throwable) {
                l.onError(ex)
            }
        }
    }

    companion object {
        /** Файл программы старше этого возраста скачивается заново. */
        const val EPG_MAX_AGE_MS = 6 * 3600000L

        /**
         * Где хранится плейлист [pl]: локальный файл лежит под своим именем, остальные — в кэше по хэшу адреса.
         * Метка локального плейлиста («local:метка») в имени файла идёт без префикса «local:».
         */
        fun playlistFile(dir: File, pl: String): File = when {
            pl == "local" -> File(dir, "local.m3u")
            pl.startsWith("local:") -> File(dir, "local_" + pl.removePrefix("local:") + ".m3u")
            else -> File(dir, "pl_" + Integer.toHexString(pl.hashCode()) + ".m3u")
        }
    }
}
