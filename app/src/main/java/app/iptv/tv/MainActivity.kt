package app.iptv.tv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.net.Uri
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Коды запросов выбора файла: 7 — servers.txt / плейлист, 8 — сохранить настройки, 9 — восстановить настройки. */
private const val RC_EXPORT = 8
private const val RC_IMPORT = 9
private const val RC_PERM = 10

class MainActivity : Activity() {
    private lateinit var player: ExoPlayer
    private lateinit var pv: PlayerView
    private lateinit var root: FrameLayout
    private lateinit var hudBox: LinearLayout
    private lateinit var hudT: TextView
    private lateinit var hudP: TextView
    private lateinit var hudLeft: TextView
    private lateinit var bufView: ProgressBar
    private lateinit var hudBar: ProgressBar
    private lateinit var hudN: TextView
    private var lastHoldSeek = 0L
    private lateinit var toastTv: TextView
    private lateinit var chPanel: LinearLayout
    private lateinit var arPanel: LinearLayout
    private lateinit var setup: ScrollView
    private lateinit var grpList: ListView
    private lateinit var chList: ListView
    private lateinit var dayList: ListView
    private lateinit var progList: ListView
    private lateinit var arTitle: TextView   // название канала в шапке архива
    private lateinit var arPrefix: TextView  // надпись «Архив:»
    private lateinit var arLogo: ImageView   // значок канала в шапке архива
    private lateinit var arInfo: TextView    // тип архива и число дней
    private lateinit var etPl: EditText
    private lateinit var etEpg: EditText
    private lateinit var etTz: EditText
    private lateinit var btType: Button
    private lateinit var btSort: Button
    private lateinit var httpFactory: DefaultHttpDataSource.Factory
    private lateinit var etUa: EditText
    private lateinit var srvPanel: LinearLayout
    private lateinit var srvList: ListView
    private lateinit var tvStat: TextView
    private lateinit var tvSrv: TextView
    private lateinit var btServer: Button
    private lateinit var btAdv: Button
    private lateinit var advBox: LinearLayout
    // раскрывающиеся разделы настроек: заголовок-кнопка и тело раздела
    private val secTitles = ArrayList<String>()
    private val secHeads = ArrayList<Button>()
    private val secBodies = ArrayList<LinearLayout>()
    private val keyBtns = ArrayList<Button>()   // кнопки раздела «Кнопки пульта» (подписи обновляются при сбросе)
    private var srvBackToSetup = false   // панель серверов открыта из настроек (Назад вернёт туда)
    private lateinit var hudLogo: ImageView
    private lateinit var seekBox: LinearLayout
    private lateinit var seekBar: ProgressBar
    private lateinit var seekT: TextView
    private lateinit var seekL: TextView
    private lateinit var seekR: TextView
    // левая полоса значков (обновление, настройки, архив, избранное, группы)
    private lateinit var rail: LinearLayout
    private lateinit var railUpd: TextView
    private lateinit var railSet: TextView
    private lateinit var railArc: TextView
    private lateinit var railFav: TextView
    private lateinit var railGrp: TextView
    private var groupsOpen = false                             // развёрнут список групп рядом с каналами
    private var railHintText: String? = null                   // подпись выбранного значка (показывается в заголовке списка)
    // окно настроек
    private lateinit var btUpd: Button
    private lateinit var tvUpdStat: TextView   // состояние проверки обновлений программы
    private lateinit var btShowAllGroups: Button
    private lateinit var groupsBox: LinearLayout
    private lateinit var tvGroupsInfo: TextView
    private lateinit var chTitle: TextView
    private lateinit var numTv: TextView                       // набор номера канала цифрами
    private val numBuf = StringBuilder()
    private var reopenSetup = false                            // после смены цветовой схемы вернуться в настройки
    private lateinit var btTheme: Button
    private lateinit var btLang: Button
    private var query = ""                                     // текст поиска каналов
    private val history = ArrayList<String>()                  // история просмотра: Channel.okey, свежие первыми
    private val resume = LinkedHashMap<String, Long>()         // позиции в архиве: «okey@начало» -> мс
    private var arcOwner: Channel? = null                      // канал текущей архивной передачи
    private var startPos = 0L                                  // с какой позиции начинать архивную передачу
    private var aspect = "fit"
    private var fontPct = 100
    private lateinit var btFont: Button
    private var epgStatus = "не загружена"
        set(v) { field = v; Session.epgStatus = v; refreshTitle() }
    @Volatile private var loadGen = 0      // номер текущей загрузки плейлиста: устаревшие потоки ничего не публикуют
    private var started = false            // между onStart и onStop
    private var askedStorage = false      // разрешение на запись в «Загрузки» (Android 6–9) уже запрашивали
    private var restoring = false          // настройки восстановлены из файла: onStop не должен затирать их своими данными
    @Volatile private var plEpgUrl = ""   // адрес программы из заголовка плейлиста (url-tvg)

    private val ui = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("cfg", MODE_PRIVATE) }
    private val types = listOf("flussonic", "shift", "append")
    private val sorts = listOf("list", "az", "fav", "custom")
    private val sortNames = mapOf("list" to "как в плейлисте", "az" to "по алфавиту", "fav" to "избранные сверху", "custom" to "свой порядок")
    private var sortMode = "list"
    private var customOrder: MutableList<String> = ArrayList()   // свой порядок: названия каналов
    private var moving = false                                    // режим перемещения канала в списке
    private var chans: List<Channel> = emptyList()
    private var groups: List<String> = emptyList()
    private var epg: Map<String, List<Prog>> = emptyMap()
    private var shown: List<Channel> = emptyList()
    private val G_ALL = "Все"
    private val G_FAV = "★ Избранное"
    private val G_HIST = "◷ История"
    private val G_FIND = "⌕ Поиск…"
    private var grp = G_ALL
    private lateinit var favs: PrefSet           // избранные каналы (по Channel.okey), общие для всех плейлистов
    private lateinit var hiddenGroups: PrefSet   // скрытые группы (по названию)
    private lateinit var repo: Repository        // слой данных: серверы, плейлист, программа передач
    private lateinit var updates: UpdateFlow
    private var zapGrp: String? = null                        // группа, по которой листают ↑/↓ (выбранная в панели); null — все каналы
    private var zapSnap: List<Channel> = emptyList()          // для «Истории» и поиска: список на момент выбора
    private var cur: Channel? = null
    private var arch: Prog? = null
    private var dayOff = 0
    private var lastUrl = ""
    private var arcCands: List<Pair<Int, String>> = emptyList()   // варианты ссылки на архив
    private var arcIdx = 0
    private var guide: Channel? = null   // канал, чья телепрограмма открыта в панели архива
    private lateinit var btStart: Button
    private lateinit var btZap: Button
    private lateinit var btDim: Button
    private lateinit var dimOverlay: View
    private val DIM_MIN = 30
    private var panelDim = 95   // непрозрачность подложки панелей (списки каналов, телепрограмма, группы, серверы), %
    private var triedHls = false
    private var ctype = "flussonic"
    // автовосстановление потока
    private var retries = 0
    private var retryHls = false
    private var readyMode: Boolean? = null   // null = поток ещё не заработал; true/false = с принудительным HLS или без
    private val maxRetries = 5
    private val retryRun = Runnable { if (lastUrl.isNotEmpty()) playUrl(lastUrl, retryHls) }
    private val stallRun = Runnable {
        if (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING && !scheduleRetry()) toast(L.t("Поток не отвечает"))
    }
    private val hideSeek = Runnable { seekBox.visibility = View.GONE; ui.removeCallbacks(seekTick) }
    private val seekTick: Runnable = object : Runnable {
        override fun run() { if (seekBox.visibility == View.VISIBLE && arch != null) { updateSeek(); ui.postDelayed(this, 500) } }
    }
    private val hideHud = Runnable { hudBox.visibility = View.GONE }
    private val showBuf = Runnable { bufView.visibility = View.VISIBLE }
    private val hideToast = Runnable { toastTv.visibility = View.GONE }

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()
    private fun now() = System.currentTimeMillis() / 1000
    private fun hm(t: Long) = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(t * 1000))
    private fun ad(l: ListView) = l.adapter as RowAdapter
    private fun nowProg(c: Channel): Prog? { val n = now(); return epg[c.eid]?.firstOrNull { it.s <= n && n < it.e } }

    /** Язык интерфейса: настройка «Автоматически / Русский / English»; «Автоматически» — по языку системы. */
    override fun attachBaseContext(base: Context) {
        val pref = base.getSharedPreferences("cfg", MODE_PRIVATE).getString("lang", "auto")
        val en = L.resolveEn(pref, systemLang())
        L.en = en
        val loc = Locale(if (en) "en" else "ru")
        Locale.setDefault(loc)   // названия дней и языков звука — на языке интерфейса
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(loc)
        super.attachBaseContext(base.createConfigurationContext(cfg))
    }

    /** Язык системы (не язык приложения: [Locale.setDefault] его меняет). */
    @Suppress("DEPRECATION")
    private fun systemLang(): String {
        val c = android.content.res.Resources.getSystem().configuration
        return (if (android.os.Build.VERSION.SDK_INT >= 24) c.locales[0] else c.locale).language
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        setContentView(root)

        Theme.use(prefs.getString("theme", "teal"))
        reopenSetup = prefs.getBoolean("reopen", false)
        if (reopenSetup) prefs.edit().remove("reopen").apply()
        Iptv.customUa = prefs.getString("ua", "") ?: ""
        Iptv.goodUa = prefs.getString("gua", "")?.ifEmpty { null }
        httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(Iptv.customUa.ifEmpty { Iptv.goodUa ?: Iptv.DEFAULT_UA })
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000).setReadTimeoutMs(20000)
        // Плеер получает тот же User-Agent, с которым сервер ответил на запрос плейлиста
        Iptv.onUa = { ua ->
            prefs.edit().putString("gua", ua).apply()
            ui.post { if (Iptv.customUa.isEmpty()) httpFactory.setUserAgent(ua) }
        }
        // DefaultDataSource поверх HTTP-фабрики: кроме http(s) работают udp://, rtp://, file:, content:
        // Сырой MPEG-TS (не HLS): без IDR-кадров и без разделителей access unit многие IPTV-потоки стартуют долго или с артефактами.
        // Эти флаги включают разбор таких потоков (на HLS-сегменты они не влияют — там свой экстрактор).
        val extractors = DefaultExtractorsFactory().setTsExtractorFlags(
            DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
        )
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, httpFactory), extractors)).build()
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                ui.removeCallbacks(stallRun)
                ui.removeCallbacks(showBuf); bufView.visibility = View.GONE
                when (state) {
                    Player.STATE_BUFFERING -> { ui.postDelayed(stallRun, 20000); ui.postDelayed(showBuf, 700) }
                    Player.STATE_READY -> {
                        readyMode = triedHls; retries = 0
                        // запоминаем рабочий формат архива для этого сервера
                        val cc = cur
                        if (arch != null && cc != null && arcIdx in arcCands.indices) prefs.edit().putInt("arcv_" + Iptv.hostOf(cc.url), arcCands[arcIdx].first).apply()
                    }
                    // сервер закрыл соединение с прямым эфиром — переподключаемся
                    Player.STATE_ENDED -> if (arch == null) { if (lastUrl.isNotEmpty()) scheduleRetry() } else if (readyMode != null) archEnded()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    player.seekToDefaultPosition(); player.prepare(); return
                }
                // фолбэк на HLS только пока поток ни разу не заработал
                if (readyMode == null && !triedHls && lastUrl.startsWith("http") && !lastUrl.contains(".m3u8")) { playUrl(lastUrl, true); return }
                // архив повторяем только при проблемах сети: ошибка адреса/типа сама не пройдёт
                val netErr = error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                    error.errorCode == PlaybackException.ERROR_CODE_TIMEOUT
                // архив не открылся — пробуем следующий формат ссылки
                if (arch != null && !netErr && arcIdx + 1 < arcCands.size) {
                    arcIdx++
                    ui.removeCallbacks(retryRun); retries = 0; readyMode = null
                    toast(L.f("Архив: пробую другой формат ({0} из {1})…", arcIdx, arcCands.size - 1))
                    playUrl(arcCands[arcIdx].second)
                    return
                }
                if ((arch == null || netErr) && scheduleRetry()) return
                toast(if (arch != null) L.f("Архив не воспроизводится (тип: {0}) — смените тип в настройках. ", archType()) + error.errorCodeName else L.t("Ошибка воспроизведения: ") + error.errorCodeName)
            }
        })
        pv = PlayerView(this)
        pv.useController = false
        pv.isFocusable = false
        pv.player = player
        pv.setOnClickListener { if (!panelOpen()) openChans() }
        root.addView(pv, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        // индикатор буферизации поверх видео, под панелями; появляется не сразу, чтобы не мигать на коротких паузах
        bufView = ProgressBar(this).apply {
            indeterminateTintList = ColorStateList.valueOf(Theme.accent)
            background = roundBg(Theme.hud)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            isFocusable = false; visibility = View.GONE
        }
        root.addView(bufView, FrameLayout.LayoutParams(dp(76), dp(76), Gravity.CENTER))
        aspect = prefs.getString("aspect", "fit") ?: "fit"
        applyAspect()
        applyTrackPrefs()

        fontPct = prefs.getInt("fs", 100).coerceIn(70, 160)
        UiScale.f = fontPct / 100f
        history.addAll((prefs.getString("hist", "") ?: "").split("\n").filter { it.isNotEmpty() })
        for (ln in (prefs.getString("resume", "") ?: "").split("\n")) {
            val i = ln.indexOf('|')
            if (i > 0) ln.substring(0, i).toLongOrNull()?.let { resume[ln.substring(i + 1)] = it }
        }
        panelDim = prefs.getInt("dim", 95).coerceIn(DIM_MIN, 100)
        repo = Repository(this, prefs)
        updates = UpdateFlow(this, prefs, { toast(it) }) { runOnUiThread { refreshRail(); refreshUpdateUi() } }
        favs = PrefSet(prefs.getStringSet("fav", emptySet()) ?: emptySet()) { prefs.edit().putStringSet("fav", HashSet(it)).apply() }
        hiddenGroups = PrefSet(prefs.getStringSet("hg", emptySet()) ?: emptySet()) { prefs.edit().putStringSet("hg", HashSet(it)).apply() }
        buildHud(); buildSeek(); buildPanels(); buildSetup(); buildServers(); buildToast(); applyFont()
        // тип архива по умолчанию — flussonic (один раз переводим и ранее сохранённое значение по умолчанию)
        if (prefs.getInt("ctv", 0) < 1) prefs.edit().putString("ct", "flussonic").putInt("ctv", 1).apply()
        ctype = prefs.getString("ct", "flussonic") ?: "flussonic"
        sortMode = prefs.getString("sort", "list") ?: "list"
        customOrder = (prefs.getString("order", "") ?: "").split("\n").filter { it.isNotEmpty() }.toMutableList()
        // по умолчанию — сетевой плейлист, а не локальный файл
        if (prefs.getString("pl", "") == "local" && getString(R.string.default_playlist).isNotEmpty())
            prefs.edit().putString("pl", getString(R.string.default_playlist)).putString("epg", getString(R.string.default_epg)).apply()
        // после смены цветовой схемы (recreate) плейлист и программа берутся из памяти, без сети
        val keep = Session.keep && Session.chans.isNotEmpty() && Session.pl == (prefs.getString("pl", "") ?: "")
        Session.keep = false
        if (prefs.getString("pl", "").isNullOrEmpty()) {
            val d = getString(R.string.default_playlist)
            if (d.isNotEmpty()) {
                prefs.edit().putString("pl", d).putString("epg", getString(R.string.default_epg)).apply()
                loadAll()
            } else openSetup()
        } else if (keep) restoreSession() else loadAll()
        refreshRail()
        updates.check(false)
    }

    /* ---------- интерфейс ---------- */
    private fun roundBg(c: Int) = GradientDrawable().apply { setColor(c); cornerRadius = dp(16).toFloat() }

    private fun buildHud() {
        hudBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundBg(Theme.hud)
            setPadding(dp(28), dp(16), dp(28), dp(16))
            visibility = View.GONE
        }
        // значок канала слева от названия; полупрозрачная подложка, чтобы читались и тёмные, и светлые логотипы
        hudLogo = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(0x44FFFFFF)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            visibility = View.GONE
        }
        val lpLogo = LinearLayout.LayoutParams(dp(104), dp(64))
        lpLogo.rightMargin = dp(18)
        hudBox.addView(hudLogo, lpLogo)
        hudT = TextView(this).apply { textSize = 26f; setTextColor(Color.WHITE) }
        hudP = TextView(this).apply { textSize = 18f; setTextColor(Theme.accent) }
        hudN = TextView(this).apply { textSize = 16f; setTextColor(Theme.gray); maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        hudP.maxLines = 1; hudP.ellipsize = TextUtils.TruncateAt.END
        hudLeft = TextView(this).apply { textSize = 16f; setTextColor(Theme.gray); maxLines = 1; visibility = View.GONE }
        val hudRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(hudP, LinearLayout.LayoutParams(0, -2, 1f))
            addView(hudLeft, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(16) })
        }
        hudBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000; visibility = View.GONE
            progressTintList = ColorStateList.valueOf(Theme.accent)
            progressBackgroundTintList = ColorStateList.valueOf(Theme.stroke)
        }
        val hudCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(hudT); addView(hudRow)
            addView(hudBar, LinearLayout.LayoutParams(-1, dp(4)).apply { topMargin = dp(6); bottomMargin = dp(4) })
            addView(hudN)
        }
        hudBox.addView(hudCol, LinearLayout.LayoutParams(0, -2, 1f))
        val lp = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM)
        lp.setMargins(dp(20), 0, dp(20), dp(28))
        root.addView(hudBox, lp)
    }

    /** Панель перемотки архива: название передачи, полоса прогресса, время. */
    private fun buildSeek() {
        seekBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundBg(Theme.hud)
            setPadding(dp(28), dp(16), dp(28), dp(16))
            visibility = View.GONE
        }
        seekT = TextView(this).apply { textSize = 18f; setTextColor(Theme.accent); maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        seekBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progressTintList = ColorStateList.valueOf(Theme.accent)
            progressBackgroundTintList = ColorStateList.valueOf(Theme.stroke)
        }
        seekL = TextView(this).apply { textSize = 16f; setTextColor(Color.WHITE) }
        seekR = TextView(this).apply { textSize = 16f; setTextColor(Theme.gray); gravity = Gravity.END }
        val times = LinearLayout(this).apply {
            addView(seekL, LinearLayout.LayoutParams(0, -2, 1f))
            addView(seekR, LinearLayout.LayoutParams(0, -2, 1f))
        }
        seekBox.addView(seekT)
        seekBox.addView(seekBar, LinearLayout.LayoutParams(-1, dp(10)).apply { topMargin = dp(8); bottomMargin = dp(6) })
        seekBox.addView(times)
        val lp = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM)
        lp.setMargins(dp(20), 0, dp(20), dp(28))
        root.addView(seekBox, lp)
    }

    private fun fmtT(ms: Long): String {
        val t = ms / 1000
        return String.format(Locale.US, "%d:%02d:%02d", t / 3600, t / 60 % 60, t % 60)
    }

    private fun updateSeek() {
        val a = arch ?: return
        val total = (a.e - a.s) * 1000
        if (total <= 0) return
        val pos = player.currentPosition.coerceIn(0L, total)
        seekT.text = "⏪ " + a.title + "   " + hm(a.s) + "–" + hm(a.e)
        seekBar.progress = (pos * 1000 / total).toInt()
        seekL.text = fmtT(pos) + "   (" + hm(a.s + pos / 1000) + ")"
        seekR.text = fmtT(total)
    }

    private fun showSeek() {
        if (arch == null) return
        hudBox.visibility = View.GONE
        updateSeek()
        seekBox.visibility = View.VISIBLE
        ui.removeCallbacks(hideSeek); ui.postDelayed(hideSeek, 5000)
        ui.removeCallbacks(seekTick); ui.postDelayed(seekTick, 500)
    }

    /** Перемотка в архиве на [d] мс с показом панели перемотки. */
    private fun seekArch(d: Long) {
        val a = arch ?: return
        val total = (a.e - a.s) * 1000
        player.seekTo((player.currentPosition + d).coerceIn(0L, maxOf(0L, total - 1000)))
        showSeek()
    }

    private fun buildToast() {
        toastTv = TextView(this).apply {
            textSize = 18f; setTextColor(Color.WHITE)
            background = roundBg(Theme.toast)
            setPadding(dp(20), dp(10), dp(20), dp(10)); visibility = View.GONE
        }
        val lp = FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        lp.topMargin = dp(24)
        root.addView(toastTv, lp)
        numTv = TextView(this).apply {
            textSize = 44f; setTextColor(Theme.accent)
            background = roundBg(Theme.hud)
            setPadding(dp(28), dp(12), dp(28), dp(12)); visibility = View.GONE
        }
        val lpn = FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END)
        lpn.topMargin = dp(24); lpn.rightMargin = dp(32)
        root.addView(numTv, lpn)
    }

    private fun lv(): ListView = ListView(this).apply {
        divider = null
        selector = GradientDrawable().apply {   // заметная подсветка: заливка акцентным цветом и рамка
            setColor(Theme.selector); setStroke(dp(2), Theme.accent); cornerRadius = dp(10).toFloat()
        }
        isFocusable = true
        adapter = RowAdapter(this@MainActivity)
    }

    private fun panel(title: View, a: ListView, b: ListView, frac: Float, g: Int): LinearLayout {
        val p = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(panelColor())
            setPadding(dp(20), dp(20), dp(20), dp(20))
            visibility = View.GONE
        }
        p.addView(title)
        val row = LinearLayout(this)
        row.addView(a, LinearLayout.LayoutParams(dp(120), -1))   // точная ширина задаётся в fitDayList()
        row.addView(b, LinearLayout.LayoutParams(0, -1, 1f))
        p.addView(row, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(p, FrameLayout.LayoutParams((resources.displayMetrics.widthPixels * frac).toInt(), -1, g))
        return p
    }

    private fun buildPanels() {
        grpList = lv(); chList = lv(); dayList = lv(); progList = lv()
        (chList.adapter as RowAdapter).icons = true
        chTitle = TextView(this).apply { textSize = 20f; maxLines = 2; text = L.f("Каналы   ·   программа: {0}", L.t(epgStatus)) }
        buildChPanel()
        // шапка архива: «Архив:» · значок канала · название канала, ниже — тип архива и число дней
        arPrefix = TextView(this).apply { text = L.t("Архив:"); textSize = 24f; setTextColor(Theme.gray) }
        arLogo = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER; visibility = View.GONE }
        arTitle = TextView(this).apply {
            textSize = 24f; setTextColor(Color.WHITE); maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        arInfo = TextView(this).apply { textSize = 14f; setTextColor(Theme.gray); maxLines = 2 }
        val arHead = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(10))
            addView(LinearLayout(this@MainActivity).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(arPrefix)
                addView(arLogo, LinearLayout.LayoutParams(dp(56), dp(40)).apply { leftMargin = dp(12) })
                addView(arTitle, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(10) })
            })
            addView(arInfo)
        }
        arPanel = panel(arHead, dayList, progList, 0.57f, Gravity.END)

        // группы каналов: список свёрнут и разворачивается значком ☰ в левой полосе; OK — показать группу
        grpList.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ -> selectGroup(ad(grpList).row(pos).title) }
        // удержание OK на группе — скрыть её (вернуть: Настройки → Группы каналов)
        grpList.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, pos, _ ->
            val r = ad(grpList).row(pos)
            if (r.tag == "g") {
                toggleGroup(r.title)
                toast(L.f("Группа скрыта: {0}. Вернуть: Настройки → Группы каналов", groupName(r.title)))
                onGroupsChanged()
                grpList.setSelection(minOf(pos, ad(grpList).getCount() - 1))
            }
            true
        }
        chList.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
            if (pos < shown.size) { setZap(); playChan(shown[pos]); closePanels() }
        }
        // удержание OK на канале — в избранное / из избранного
        chList.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, pos, _ ->
            if (pos in shown.indices) {
                val c = shown[pos]
                android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle(c.name)
                    .setItems(arrayOf(if (isFav(c)) L.t("★ Убрать из избранного") else L.t("★ В избранное"), L.t("⇅ Переместить (↑/↓)"), L.t("↺ Архив канала"))) { _, w ->
                        if (w == 0) favAt(pos) else if (w == 1) startMove(pos) else { setZap(); playChan(c); closePanels(); openArch() }
                    }.show()
            }
            true
        }
        dayList.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { dayOff = pos; renderProgs() }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        dayList.onItemClickListener = AdapterView.OnItemClickListener { _, _, _, _ -> progList.requestFocus() }
        progList.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
            val p = ad(progList).row(pos).tag as? Prog
            val c = guide ?: cur
            if (p != null && c != null) {
                val n = now()
                when {
                    p.s > n -> toast(L.t("Эта передача ещё не вышла"))
                    p.s <= n && n < p.e -> { playChan(c); closePanels() }
                    else -> { if (cur !== c) cur = c; playArch(p); closePanels() }
                }
            }
        }
    }

    /** Фон с заметной рамкой и цветом в фокусе (для навигации пультом). */
    private fun focusBg(normal: Int, stroke: Int, fill: Int): StateListDrawable {
        fun shape(f: Int, st: Int, w: Int) = GradientDrawable().apply { setColor(f); setStroke(dp(w), st); cornerRadius = dp(8).toFloat() }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), shape(fill, Theme.accent, 3))
            addState(intArrayOf(), shape(normal, stroke, 1))
        }
    }

    /** Кнопка для ТВ: тёмная, а в фокусе — яркая бирюзовая с тёмным текстом и белой рамкой. */
    private fun tvButton(t: String): Button = Button(this).apply {
        text = t; textSize = 18f; isAllCaps = false
        stateListAnimator = null
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        minHeight = dp(52); minimumHeight = dp(52)
        setPadding(dp(18), dp(8), dp(18), dp(8))
        setTextColor(ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf(android.R.attr.state_pressed), intArrayOf()),
            intArrayOf(Theme.onAccent, Theme.onAccent, Color.WHITE)))
        fun shape(f: Int, st: Int, w: Int) = GradientDrawable().apply { setColor(f); setStroke(dp(w), st); cornerRadius = dp(8).toFloat() }
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), shape(Theme.accent, Color.WHITE, 4))
            addState(intArrayOf(android.R.attr.state_pressed), shape(Theme.accent, Color.WHITE, 4))
            addState(intArrayOf(), shape(Theme.btn, Theme.stroke, 1))
        }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
    }

    /** Левая панель: тонкая полоса значков и справа — группы (свёрнуты) и список каналов. */
    private fun buildChPanel() {
        val w = resources.displayMetrics.widthPixels
        railUpd = railBtn("⇧", L.t("Обновление")) { updates.promptInstall() }
        railSet = railBtn("⚙", L.t("Настройки")) { openSetup() }
        railArc = railBtn("↺", L.t("Архив")) { if (cur != null) openArch() else toast(L.t("Сначала выберите канал")) }
        railFav = railBtn("★", L.t("Избранное")) { selectGroup(G_FAV) }
        railGrp = railBtn("☰", L.t("Группы каналов")) { toggleGroupsOpen() }
        rail = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Theme.deep)
            setPadding(dp(6), dp(16), dp(6), dp(16))
            for (b in listOf(railUpd, railSet, railArc, railFav, railGrp)) addView(b)
        }
        chTitle.setTextColor(Color.WHITE); chTitle.setPadding(0, 0, 0, dp(10))
        val lists = LinearLayout(this).apply {
            addView(grpList, LinearLayout.LayoutParams((w * 0.22f).toInt(), -1))
            addView(chList, LinearLayout.LayoutParams(0, -1, 1f))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(20), dp(20))
            addView(chTitle)
            addView(lists, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        chPanel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(panelColor())
            visibility = View.GONE
            addView(rail, LinearLayout.LayoutParams(dp(68), -1))
            addView(content, LinearLayout.LayoutParams(0, -1, 1f))
        }
        root.addView(chPanel, FrameLayout.LayoutParams((w * 0.43f).toInt(), -1, Gravity.START))
        setGroupsOpen(false)
    }

    /** Значок левой полосы: только пиктограмма; подпись показывается в заголовке списка, пока значок выбран. */
    private fun railBtn(glyph: String, hint: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = glyph; textSize = 26f; gravity = Gravity.CENTER
        isFocusable = true; isClickable = true
        contentDescription = hint
        layoutParams = LinearLayout.LayoutParams(dp(54), dp(54)).apply { topMargin = dp(8) }
        setOnClickListener { onClick() }
        onFocusChangeListener = View.OnFocusChangeListener { _, has ->
            if (has) setRailHint(hint) else if (!railFocused()) setRailHint(null)
        }
    }

    private fun railFocused() = listOf(railUpd, railSet, railArc, railFav, railGrp).any { it.isFocused }

    private fun setRailHint(t: String?) { railHintText = t; refreshTitle() }

    /** Заголовок списка каналов: название группы и состояние программы; у выбранного значка полосы — его название. */
    /** Название группы для показа: служебные группы и «Без группы» переводятся, группы из плейлиста остаются как есть. */
    private fun groupName(g: String) = if (g == G_ALL || g == G_FAV || g == G_HIST || g == G_FIND || g == "Без группы") L.t(g) else g

    private fun refreshTitle() {
        if (!::chTitle.isInitialized) return
        val head = when (grp) { G_ALL -> L.t("Все каналы"); G_FIND -> L.f("Поиск «{0}»", query); else -> groupName(grp) }
        chTitle.text = railHintText ?: L.f("{0}   ·   программа: {1}", head, L.t(epgStatus))
    }

    /** Обновляет вид значков: значок «Обновление» виден только при найденной новой версии, у выбранного режима — рамка. */
    private fun refreshRail() {
        if (!::railGrp.isInitialized || !::updates.isInitialized) return
        railUpd.visibility = if (updates.available != null) View.VISIBLE else View.GONE
        styleRail(railUpd, false, true)
        styleRail(railSet, false)
        styleRail(railArc, false)
        styleRail(railFav, grp == G_FAV)
        styleRail(railGrp, groupsOpen || grp != G_FAV)
    }

    private fun styleRail(v: TextView, active: Boolean, special: Boolean = false) {
        fun shape(f: Int, st: Int, wd: Int) = GradientDrawable().apply { setColor(f); setStroke(dp(wd), st); cornerRadius = dp(12).toFloat() }
        v.background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), shape(Theme.accent, Color.WHITE, 3))
            addState(intArrayOf(), when {
                special -> shape(Theme.accent, Theme.accent, 1)
                active -> shape(Theme.card, Theme.accent, 2)
                else -> shape(Theme.btn, Theme.stroke, 1)
            })
        }
        val idle = if (special) Theme.onAccent else if (active) Theme.accent else Color.WHITE
        v.setTextColor(ColorStateList(arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()), intArrayOf(Theme.onAccent, idle)))
    }

    /** Разворачивает / сворачивает список групп рядом с каналами; панель при этом расширяется, чтобы каналы не сжимались. */
    private fun setGroupsOpen(on: Boolean) {
        groupsOpen = on
        grpList.visibility = if (on) View.VISIBLE else View.GONE
        val lp = chPanel.layoutParams
        lp.width = (resources.displayMetrics.widthPixels * (if (on) 0.62f else 0.43f)).toInt()
        chPanel.layoutParams = lp
        refreshRail()
    }

    private fun toggleGroupsOpen() {
        if (groupsOpen) { setGroupsOpen(false); railGrp.requestFocus(); return }
        val rows = groupRows()
        ad(grpList).active = grp
        ad(grpList).set(rows)
        setGroupsOpen(true)
        grpList.setSelection(rows.indexOfFirst { it.title == grp }.coerceAtLeast(0))
        grpList.requestFocus()
    }

    /** Показывает каналы группы [g] (в том числе избранное), сворачивает список групп и переводит фокус на каналы. */
    private fun selectGroup(g: String) {
        grp = g
        prefs.edit().putString("lgrp", g).apply()
        setGroupsOpen(false)
        ad(grpList).active = g
        ad(grpList).notifyDataSetChanged()
        renderChans()
        refreshRail()
        if (shown.isEmpty()) {
            toast(when (g) {
                G_FAV -> L.t("Избранное пусто. Удерживайте OK на канале")
                G_HIST -> L.t("История пуста")
                else -> L.t("В группе нет каналов")
            })
            (if (g == G_FAV) railFav else railGrp).requestFocus()
        } else chList.requestFocus()
    }

    /** Показ или скрытие группы изменилось: обновляем список групп, каналы и значки. */
    private fun onGroupsChanged() {
        zapGrp?.let { if (it in hiddenGroups) zapGrp = null }
        ad(grpList).set(groupRows())
        renderChans()
        refreshRail()
    }

    /** Цвет подложки панелей: тёмно-серый с непрозрачностью [panelDim] %. */
    private fun panelColor(): Int = (((panelDim * 255 + 50) / 100) shl 24) or Theme.panel

    private fun dimLabel() = L.f("Затемнение подложки списков: {0}%  (◄ ► ±5)", panelDim)

    /** Меняет затемнение сразу для всех панелей и сохраняет значение. */
    private fun setDim(v: Int) {
        panelDim = v.coerceIn(DIM_MIN, 100)
        prefs.edit().putInt("dim", panelDim).apply()
        val c = panelColor()
        for (p in listOf(chPanel, arPanel, srvPanel)) p.setBackgroundColor(c)
        dimOverlay.setBackgroundColor(c)
        btDim.text = dimLabel()
    }

    private fun cardBg() = GradientDrawable().apply {
        setColor(Theme.card); setStroke(dp(1), Theme.cardStroke); cornerRadius = dp(14).toFloat()
    }

    private fun buildSetup() {
        val gray = Theme.gray
        fun field(h: String) = EditText(this).apply {
            setTextColor(Color.WHITE); setHintTextColor(0xFF667780.toInt()); hint = h; textSize = 15f
            setSingleLine(); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            background = focusBg(Theme.field, Theme.stroke, Theme.fieldFocus)
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
        fun label(t: String) = TextView(this).apply {
            text = t; textSize = 12f; setTextColor(gray); setPadding(dp(2), dp(8), 0, dp(3))
        }
        // компактная кнопка: ниже и мельче стандартной
        fun small(b: Button): Button = b.apply {
            textSize = 16f; minHeight = dp(44); minimumHeight = dp(44)
            setPadding(dp(14), dp(4), dp(14), dp(4))
            (layoutParams as LinearLayout.LayoutParams).topMargin = dp(6)
        }
        // раскрывающийся раздел: заголовок-кнопка (OK или ►/◄) и скрытое по умолчанию тело
        val sectionsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun section(title: String, build: LinearLayout.() -> Unit) {
            val i = secTitles.size
            val body = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = cardBg()
                setPadding(dp(16), dp(8), dp(16), dp(14))
                visibility = View.GONE
                build()
            }
            val head = tvButton("▸  $title").apply {
                setOnClickListener { showSection(i, secBodies[i].visibility != View.VISIBLE) }
                setOnKeyListener { _, k, e ->
                    if (e.action == KeyEvent.ACTION_DOWN && (k == KeyEvent.KEYCODE_DPAD_RIGHT || k == KeyEvent.KEYCODE_DPAD_LEFT)) {
                        showSection(i, k == KeyEvent.KEYCODE_DPAD_RIGHT)
                        true
                    } else false
                }
            }
            secTitles.add(title); secHeads.add(head); secBodies.add(body)
            sectionsBox.addView(head, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(if (i == 0) 0 else 8) })
            sectionsBox.addView(body, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        }
        // несколько кнопок в одну строку
        fun row(vararg bs: Button) = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            bs.forEachIndexed { i, b ->
                b.gravity = Gravity.CENTER
                addView(b, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    topMargin = dp(6)
                    if (i > 0) leftMargin = dp(8)
                })
            }
        }
        fun gap() = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) }

        etPl = field("http://…/playlist.m3u")
        etEpg = field(L.t("пусто = адрес из плейлиста (url-tvg)"))
        etTz = field(L.t("0, 3, 5.5 или 5:30")).apply { inputType = InputType.TYPE_CLASS_TEXT }
        etUa = field(L.t("пусто = автоподбор (VLC, okhttp, Mozilla…)"))

        // ----- Источник -----
        tvSrv = TextView(this).apply {
            textSize = 18f; setTextColor(Color.WHITE); setPadding(dp(2), 0, 0, dp(2))
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        btServer = small(tvButton(L.t("⇄ Выбрать сервер"))).apply { setOnClickListener { openServers() } }
        val btFile = small(tvButton(L.t("Загрузить файл…"))).apply { setOnClickListener { pickFile() } }
        val btReload = small(tvButton(L.t("Обновить плейлист"))).apply {
            setOnClickListener { if (applySource(true) == 2) closePanels() }
        }
        // «Применить» перезагружает только то, что изменилось: ссылка плейлиста — плейлист; программа или часовой пояс — только программу
        val btApply = small(tvButton(L.t("Применить"))).apply {
            gravity = Gravity.CENTER
            setOnClickListener {
                when (applySource()) {
                    2 -> closePanels()
                    0 -> toast(L.t("Изменений нет"))
                    else -> {}
                }
            }
        }
        advBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            addView(label(L.t("Ссылка на плейлист (M3U)"))); addView(etPl)
            addView(label(L.t("Программа передач (XMLTV)"))); addView(etEpg)
            addView(label(L.t("User-Agent (если сервер отвечает 403)"))); addView(etUa)
            addView(label(L.t("Часовой пояс программы, ч от UTC (если не указан в EPG)"))); addView(etTz)
            addView(btApply)
        }
        btAdv = small(tvButton("")).apply { setOnClickListener { setAdvanced(advBox.visibility != View.VISIBLE) } }

        // ----- Воспроизведение -----
        btType = small(tvButton("")).apply {
            setOnClickListener { ctype = types[(types.indexOf(ctype) + 1) % types.size]; prefs.edit().putString("ct", ctype).apply(); text = typeLabel() }
        }
        val btSound = small(tvButton(L.t("♫ Звук, субтитры, формат"))).apply { setOnClickListener { openPlayerOptions() } }
        val btFindCh = small(tvButton(L.t("⌕ Поиск канала…"))).apply { setOnClickListener { openSearch() } }
        btZap = small(tvButton("")).apply {
            setOnClickListener { prefs.edit().putString("zapm", if (zapByList()) "all" else "list").apply(); text = zapLabel() }
        }

        // ----- Список каналов -----
        btSort = small(tvButton("")).apply {
            setOnClickListener { setSort(sorts[(sorts.indexOf(sortMode) + 1) % sorts.size]) }
        }
        btStart = small(tvButton("")).apply {
            setOnClickListener {
                val modes = listOf("fav", "all", "last")
                val st = prefs.getString("start", "fav")
                prefs.edit().putString("start", modes[(modes.indexOf(st) + 1) % modes.size]).apply()
                text = startLabel()
            }
        }

        // ----- Оформление: всё применяется сразу, перезагрузки нет -----
        btTheme = small(tvButton("")).apply { setOnClickListener { chooseTheme() } }
        btLang = small(tvButton("")).apply { setOnClickListener { chooseLang() } }
        // размер шрифта: OK — следующее значение (шаг 10), ◄/► — ±10
        btFont = small(tvButton("")).apply {
            setOnClickListener { setFont(if (fontPct + 10 > 160) 70 else fontPct + 10) }
            setOnKeyListener { _, k, e ->
                if (e.action == KeyEvent.ACTION_DOWN && (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                    setFont(fontPct + if (k == KeyEvent.KEYCODE_DPAD_LEFT) -10 else 10)
                    true
                } else false
            }
        }
        // затемнение подложки: OK — следующее значение (шаг 10), ◄/► — точная подстройка на 5 %
        btDim = small(tvButton("")).apply {
            setOnClickListener {
                val n = (panelDim / 10 + 1) * 10
                setDim(if (n > 100) DIM_MIN else n)
            }
            setOnKeyListener { _, k, e ->
                if (e.action == KeyEvent.ACTION_DOWN && (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                    setDim(panelDim + if (k == KeyEvent.KEYCODE_DPAD_LEFT) -5 else 5)
                    true
                } else false
            }
        }
        // пример: яркий «кадр» и поверх него подложка с выбранной прозрачностью
        dimOverlay = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), 0)
            setBackgroundColor(panelColor())
            addView(TextView(this@MainActivity).apply { text = L.t("Канал · передача"); textSize = 15f; setTextColor(Color.WHITE) })
        }
        val dimPreview = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xFF2E86AB.toInt(), 0xFFF6C85F.toInt(), 0xFFE4572E.toInt()))
            addView(dimOverlay, FrameLayout.LayoutParams(-1, -1).apply { leftMargin = dp(70) })
        }

        // ----- Обновление и система -----
        tvStat = TextView(this).apply { textSize = 13f; setTextColor(gray); setPadding(dp(2), dp(10), 0, 0) }
        val btEpg = small(tvButton(L.t("Обновить программу передач"))).apply {
            setOnClickListener {
                if (chans.isEmpty()) { toast(L.t("Сначала загрузите плейлист")) } else {
                    toast(L.t("Загрузка программы передач…"))
                    epgStatus = L.t("загрузка…")
                    val tzv = Tz.parse(etTz.text.toString())
                    prefs.edit().putString("epg", etEpg.text.toString().trim()).putInt("tzm", tzv).apply()
                    val cs = chans; val g = loadGen
                    Thread { loadEpgNow(cs, plEpgUrl, true, tzv, g) }.start()
                }
            }
        }
        btUpd = small(tvButton(L.t("Проверить обновления"))).apply { setOnClickListener { updates.check(true) } }
        tvUpdStat = TextView(this).apply { textSize = 13f; setTextColor(gray); setPadding(dp(2), dp(8), 0, 0) }
        val btAbout = small(tvButton(L.t("О программе"))).apply { setOnClickListener { showAbout() } }

        // ----- Группы каналов: нажатие — показать / скрыть -----
        groupsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tvGroupsInfo = TextView(this).apply { textSize = 13f; setTextColor(gray); setPadding(dp(2), dp(8), 0, dp(2)) }
        btShowAllGroups = small(tvButton(L.t("Показать все группы"))).apply {
            gravity = Gravity.CENTER
            setOnClickListener {
                hiddenGroups.clear()
                onGroupsChanged()
                renderGroupsCard(if (groups.isEmpty()) -1 else 0)
            }
        }
        // ----- Раскрывающиеся вкладки (порядок на экране) -----
        section(L.t("Источник")) {
            addView(label(L.t("Текущий сервер"))); addView(tvSrv)
            addView(row(btServer, btFile))
            addView(btAdv); addView(advBox)
        }
        section(L.t("Обновление")) { addView(btReload); addView(btEpg); addView(btUpd); addView(tvUpdStat); addView(tvStat) }
        section(L.t("Воспроизведение")) { addView(btType); addView(btZap); addView(btSound); addView(btFindCh) }
        section(L.t("Оформление")) {
            addView(btLang); addView(btTheme); addView(btFont); addView(btDim)
            addView(dimPreview, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(6) })
        }
        section(L.t("Список каналов")) { addView(btSort); addView(btStart) }
        section(L.t("Группы каналов")) { addView(tvGroupsInfo); addView(btShowAllGroups); addView(groupsBox) }
        // ----- Кнопки пульта: OK / ► — следующее действие, ◄ — предыдущее -----
        val keyInfo = TextView(this).apply {
            text = L.t("Назначение кнопок при просмотре эфира. OK или ► — следующее действие, ◄ — предыдущее. ") +
                L.t("Стрелки джойстика переназначаются только при просмотре эфира. OK, «Назад» и цифры не меняются; в списках и меню кнопки работают как раньше.")
            textSize = 13f; setTextColor(gray); setPadding(dp(2), dp(4), 0, dp(4))
        }
        for (kb in KeyMap.buttons) {
            val b = small(tvButton(keyLabel(kb))).apply {
                setOnClickListener { setKeyAction(kb, KeyMap.next(keyAction(kb.code), 1)); text = keyLabel(kb) }
                setOnKeyListener { _, k, e ->
                    if (e.action == KeyEvent.ACTION_DOWN && (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                        setKeyAction(kb, KeyMap.next(keyAction(kb.code), if (k == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1))
                        text = keyLabel(kb)
                        true
                    } else false
                }
            }
            keyBtns.add(b)
        }
        val btKeyReset = small(tvButton(L.t("Сбросить на стандартные"))).apply {
            gravity = Gravity.CENTER
            setOnClickListener {
                val ed = prefs.edit()
                for (kb in KeyMap.buttons) ed.remove(KeyMap.PREF + kb.code)
                ed.apply()
                refreshKeyButtons()
                toast(L.t("Кнопки пульта сброшены"))
            }
        }
        section(L.t("Кнопки пульта")) {
            addView(keyInfo)
            for (b in keyBtns) addView(b)
            addView(btKeyReset)
        }
        // ----- Резервная копия: настройки в файл и обратно -----
        val btBackup = small(tvButton(L.t("⤓ Сохранить настройки в файл"))).apply { setOnClickListener { exportSettings() } }
        val btBackupAs = small(tvButton(L.t("⤓ Сохранить как…"))).apply { setOnClickListener { exportSettingsAs() } }
        val btRestore = small(tvButton(L.t("⤒ Восстановить настройки из файла…"))).apply { setOnClickListener { importSettings() } }
        val tvBackupInfo = TextView(this).apply {
            text = L.t("В файл попадают применённые настройки: сервер и ссылки, оформление, порядок каналов, избранное, скрытые группы, ") +
                L.t("кнопки пульта, история. Плейлист из файла и servers.txt не входят. В ссылках бывают логины и ключи — не публикуйте файл.")
            textSize = 13f; setTextColor(gray); setPadding(dp(2), dp(8), 0, dp(2))
        }
        val btRestoreUrl = small(tvButton(L.t("⤒ Восстановить по ссылке…"))).apply { setOnClickListener { importSettingsFromUrl() } }
        section(L.t("Резервная копия")) { addView(btBackup); addView(btBackupAs); addView(btRestore); addView(btRestoreUrl); addView(tvBackupInfo) }
        section(L.t("Система")) { addView(btAbout) }

        val btClose = tvButton(L.t("Закрыть")).apply { gravity = Gravity.CENTER; setOnClickListener { closeSetup() } }

        val ver = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "?" }
        val build = try { Updater.installedCode(this) } catch (e: Exception) { 0L }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@MainActivity).apply { text = L.t("Настройки"); textSize = 24f; setTextColor(Color.WHITE) },
                LinearLayout.LayoutParams(0, -2, 1f))
            addView(TextView(this@MainActivity).apply {
                text = getString(R.string.app_name) + " · v" + ver + L.t(" · сборка ") + build; textSize = 13f; setTextColor(gray)
            })
        }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(32), dp(16), dp(32), dp(16))
            addView(header)
            addView(sectionsBox, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
            addView(btClose, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        }
        setup = ScrollView(this).apply { setBackgroundColor(Theme.deep); visibility = View.GONE; addView(col) }
        root.addView(setup, FrameLayout.LayoutParams(-1, -1))
        setAdvanced(false)
    }

    /** Кнопка-«плитка» группы: ✓ показана, ✗ скрыта. */
    private fun groupChip(name: String, count: Int, hidden: Boolean): Button =
        tvButton((if (hidden) "✗  " else "✓  ") + groupName(name) + "  ($count)").apply {
            textSize = 15f; minHeight = dp(44); minimumHeight = dp(44)
            setPadding(dp(12), dp(4), dp(12), dp(4))
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            if (hidden) setTextColor(ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()), intArrayOf(Theme.onAccent, Theme.gray)))
        }

    /** Перерисовывает карточку «Группы каналов» в настройках; [focusIdx] — плитка, на которую вернуть фокус. */
    private fun renderGroupsCard(focusIdx: Int = -1) {
        if (!::groupsBox.isInitialized) return
        groupsBox.removeAllViews()
        val hid = groups.count { it in hiddenGroups }
        tvGroupsInfo.text = if (groups.isEmpty()) L.t("Групп пока нет — загрузите плейлист")
        else L.f("Всего: {0} · скрыто: {1} · OK — показать или скрыть группу", groups.size, hid)
        btShowAllGroups.visibility = if (hid > 0) View.VISIBLE else View.GONE
        val counts = chans.groupingBy { it.group }.eachCount()
        val perRow = 3
        val chips = ArrayList<Button>()
        var i = 0
        while (i < groups.size) {
            val line = LinearLayout(this)
            for (k in 0 until perRow) {
                val idx = i + k
                val lp = LinearLayout.LayoutParams(0, -2, 1f).apply { topMargin = dp(6); if (k > 0) leftMargin = dp(8) }
                if (idx < groups.size) {
                    val g = groups[idx]
                    val b = groupChip(g, counts[g] ?: 0, g in hiddenGroups)
                    b.setOnClickListener { toggleGroup(g); onGroupsChanged(); renderGroupsCard(idx) }
                    chips.add(b)
                    line.addView(b, lp)
                } else line.addView(View(this), lp)   // пустое место, чтобы плитки в последней строке не растягивались
            }
            groupsBox.addView(line)
            i += perRow
        }
        if (focusIdx >= 0) chips.getOrNull(focusIdx)?.requestFocus()
    }

    private fun showPanel(p: View?) {
        chPanel.visibility = View.GONE; arPanel.visibility = View.GONE; setup.visibility = View.GONE; srvPanel.visibility = View.GONE
        hudBox.visibility = View.GONE
        seekBox.visibility = View.GONE
        p?.visibility = View.VISIBLE
    }
    private fun closePanels() = showPanel(null)
    private fun panelOpen() = chPanel.visibility == View.VISIBLE || arPanel.visibility == View.VISIBLE || setup.visibility == View.VISIBLE || srvPanel.visibility == View.VISIBLE

    private fun toast(t: String) {
        toastTv.text = t; toastTv.visibility = View.VISIBLE
        ui.removeCallbacks(hideToast); ui.postDelayed(hideToast, 3000)
    }

    /** «ещё 14 мин» / «ещё 1 ч 05 мин» */
    private fun leftLabel(sec: Long): String {
        val m = ((sec + 59) / 60).coerceAtLeast(1)
        return if (m >= 60) L.f("ещё {0} ч {1} мин", m / 60, String.format(Locale.US, "%02d", m % 60)) else L.f("ещё {0} мин", m)
    }

    private fun hud() {
        val c = cur ?: return
        val a = arch
        val p = a ?: nowProg(c)
        val num = numList().indexOfFirst { it === c }
        hudT.text = (if (num >= 0) "${num + 1}  " else "") + (if (isFav(c)) "★ " else "") + c.name + if (a != null) L.t("  ·  архив") else ""
        Logos.load(hudLogo, c.logo, true)
        hudP.text = if (p != null) "${hm(p.s)}–${hm(p.e)}  ${p.title}" else ""
        val nx = if (p != null) epg[c.eid]?.firstOrNull { it.s >= p.e } else null
        hudN.text = if (nx != null) L.f("Далее: {0}–{1}  {2}", hm(nx.s), hm(nx.e), nx.title) else ""
        hudN.visibility = if (nx != null) View.VISIBLE else View.GONE
        // полоса прогресса и «ещё N мин» — только для прямого эфира с известной передачей
        val t = now()
        if (a == null && p != null && p.e > p.s && t in p.s until p.e) {
            hudBar.progress = ((t - p.s) * 1000 / (p.e - p.s)).toInt(); hudBar.visibility = View.VISIBLE
            hudLeft.text = leftLabel(p.e - t); hudLeft.visibility = View.VISIBLE
        } else { hudBar.visibility = View.GONE; hudLeft.visibility = View.GONE }
        hudBox.visibility = View.VISIBLE
        ui.removeCallbacks(hideHud); ui.postDelayed(hideHud, 4500)
    }

    /* ---------- панели ---------- */
    private fun openChans() {
        ad(grpList).active = grp
        ad(grpList).set(groupRows())
        renderChans()
        showPanel(chPanel)
        setGroupsOpen(false)   // группы свёрнуты: разворачиваются значком ☰
        setRailHint(null)
        refreshRail()
        chList.setSelection(shown.indexOfFirst { it === cur }.coerceAtLeast(0))
        chList.requestFocus()
        updates.check(false)   // не чаще раза в 6 часов; найденное обновление покажет значок в левой полосе
    }

    /** Строки развёрнутого списка групп: «Все», история и нескрытые группы (избранное — отдельный значок ★). */
    private fun groupRows(): List<Row> {
        val l = ArrayList<Row>()
        l.add(Row(G_ALL, tag = "all"))
        l.add(Row(G_HIST, tag = "hist"))
        for (g in groups) if (g !in hiddenGroups) l.add(Row(g, tag = "g"))
        return l
    }

    /** Каналы в выбранном порядке (сортировка устойчивая: равные остаются в порядке плейлиста). */
    private fun ordered(): List<Channel> = when (sortMode) {
        "az" -> chans.sortedBy { it.name.lowercase() }
        "fav" -> chans.sortedBy { if (isFav(it)) 0 else 1 }
        "custom" -> { val pos = HashMap<String, Int>(); customOrder.forEachIndexed { i, n -> pos.putIfAbsent(n, i) }; chans.sortedBy { pos[it.okey] ?: Int.MAX_VALUE } }
        else -> chans
    }

    private fun setSort(m: String) {
        sortMode = m
        if (m == "custom" && customOrder.isEmpty()) { customOrder = chans.map { it.okey }.toMutableList(); saveOrder() }
        prefs.edit().putString("sort", m).apply()
        btSort.text = L.f("Порядок каналов: {0}", L.t(sortNames[m].orEmpty()))
    }

    private fun startLabel() = L.t("При запуске открывать: ") + when (prefs.getString("start", "fav")) {
        "all" -> L.t("Все каналы")
        "last" -> L.t("последнюю группу")
        else -> L.t("★ Избранное")
    }

    private fun saveOrder() = prefs.edit().putString("order", customOrder.joinToString("\n")).apply()

    /** Зелёная кнопка / «#»: включает режим перемещения выбранного канала (↑/↓ — двигать, OK — готово). */
    private fun startMove(pos: Int = chList.selectedItemPosition) {
        if (pos < 0 || pos >= shown.size) return
        if (sortMode != "custom") {
            customOrder = ordered().map { it.okey }.toMutableList(); saveOrder()
            setSort("custom")
        } else {
            // добавляем в свой порядок новые каналы, которых там ещё нет
            val known = customOrder.toHashSet(); chans.forEach { if (known.add(it.okey)) customOrder.add(it.okey) }
        }
        moving = true
        renderChans(); chList.setSelection(pos)
        toast(L.t("Перемещение: ↑/↓ — двигать, OK — готово"))
    }

    private fun moveBy(d: Int) {
        val pos = chList.selectedItemPosition; val t = pos + d
        if (pos < 0 || t < 0 || t >= shown.size) return
        val a = customOrder.indexOf(shown[pos].okey); val b = customOrder.indexOf(shown[t].okey)
        if (a < 0 || b < 0) return
        val x = customOrder[a]; customOrder[a] = customOrder[b]; customOrder[b] = x
        saveOrder()
        renderChans(); chList.setSelection(t)
    }

    private fun stopMove() { moving = false; renderChans(); toast(L.t("Порядок сохранён")) }

    private fun renderChans() {
        val all = ordered()
        shown = if (grp == G_FAV) all.filter { isFav(it) }
        else if (grp == G_HIST) { val m = chans.associateBy { it.okey }; history.mapNotNull { m[it] } }
        else if (grp == G_FIND) searchIn(all)
        else if (grp == G_ALL) all.filter { it.group !in hiddenGroups }
        else all.filter { it.group == grp }
        val sel = if (moving) chList.selectedItemPosition else -1
        val nums = java.util.IdentityHashMap<Channel, Int>().also { m -> numList().forEachIndexed { k, c -> m[c] = k + 1 } }
        val t = now()
        ad(chList).set(shown.mapIndexed { i, it ->
            val p = nowProg(it)
            val mark = (if (i == sel) "⇅" else if (it === cur) "▶" else "") + (if (isFav(it)) (if (i == sel || it === cur) " ★" else "★") else "")
            Row(it.name, if (p != null) hm(p.s) + "–" + hm(p.e) + "  " + p.title else "", it, false, it.logo,
                num = nums[it] ?: 0, prog = if (p != null && p.e > p.s) (t - p.s).toFloat() / (p.e - p.s) else -1f, mark = mark)
        })
        refreshTitle()
    }

    /* ---------- избранное и скрытые группы ---------- */
    private fun isFav(c: Channel) = c.okey in favs

    private fun toggleFav(c: Channel) {
        favs.toggle(c.okey)
        toast(if (isFav(c)) L.f("★ В избранное: {0}", c.name) else L.f("Убрано из избранного: {0}", c.name))
    }

    /** Переключает избранное у канала с номером [pos] в текущем списке и сохраняет позицию в списке. */
    private fun favAt(pos: Int) {
        if (pos < 0 || pos >= shown.size) return
        toggleFav(shown[pos])
        renderChans()
        chList.setSelection(minOf(pos, maxOf(shown.size - 1, 0)))
    }

    private fun toggleGroup(g: String) {
        val hidden = hiddenGroups.toggle(g)
        if (hidden && grp == g) grp = G_ALL
        if (hidden && zapGrp == g) zapGrp = null
    }

    /** [keepChannels] — телепрограмма открывается справа от списка каналов, список остаётся на экране. */
    private fun openArch(ch: Channel? = cur, keepChannels: Boolean = false) {
        val c = ch ?: return
        guide = c
        arTitle.text = c.name
        Logos.load(arLogo, c.logo, true)
        arInfo.text = L.f("тип: {0}, дней: {1}", archType(c), if (c.days > 0) c.days else 7) + (if (c.days == 0 && c.ctype.isEmpty() && c.csrc.isEmpty()) L.t(", в плейлисте нет catchup") else "")
        val n = minOf(if (c.days > 0) c.days else 7, 14)
        val f = SimpleDateFormat("dd.MM", Locale.getDefault())
        ad(dayList).set((0..n).map { i ->
            Row(when (i) { 0 -> L.t("Сегодня"); 1 -> L.t("Вчера"); else -> f.format(Date(System.currentTimeMillis() - i * 86400000L)) })
        })
        dayOff = 0
        if (keepChannels) { setGroupsOpen(false); hudBox.visibility = View.GONE; seekBox.visibility = View.GONE; arPanel.visibility = View.VISIBLE }
        else showPanel(arPanel)
        renderProgs()
        progList.requestFocus()
    }

    private fun renderProgs() {
        val c = guide ?: cur ?: return
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.DAY_OF_YEAR, -dayOff)
        val s0 = cal.timeInMillis / 1000
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val s1 = cal.timeInMillis / 1000
        var l = (epg[c.eid] ?: emptyList()).filter { it.e > s0 && it.s < s1 }
        if (l.isEmpty()) l = (0 until 24).map { Prog(s0 + it * 3600L, s0 + (it + 1) * 3600L, L.t("Эфир ") + hm(s0 + it * 3600L)) }
        val n = now()
        val lim = n - (if (c.days > 0) c.days else 7) * 86400L
        ad(progList).set(l.map { p ->
            val fut = p.s > n; val live = p.s <= n && n < p.e; val ok = p.e > lim
            Row(p.title, hm(p.s) + "–" + hm(p.e) + when { live -> L.t(" · в эфире"); fut -> L.t(" · ещё не было"); !ok -> L.t(" · нет в архиве"); else -> "" }, p, fut || !ok)
        })
        var i = l.indexOfFirst { it.s <= n && n < it.e }
        if (i < 0) i = l.indexOfLast { it.s <= n }
        progList.setSelection(i.coerceAtLeast(0))
    }

    private fun typeLabel() = L.f("Тип архива по умолчанию: {0}", ctype)

    /** Название выбранного сервера для экрана настроек. */
    private fun srvLabel(): String {
        val pl = prefs.getString("pl", "") ?: ""
        return when {
            pl.isEmpty() -> L.t("не выбран")
            pl == "local" -> L.t("локальный файл")
            else -> (prefs.getString("srvn", "") ?: "").ifEmpty { Iptv.hostOf(pl) }
        }
    }

    private fun setAdvanced(on: Boolean) {
        advBox.visibility = if (on) View.VISIBLE else View.GONE
        btAdv.text = if (on) L.t("Дополнительно  ▴") else L.t("Дополнительно  ▾")
    }

    /** Действие, назначенное на кнопку пульта [code]. */
    private fun keyAction(code: Int): KeyAction = KeyMap.resolve(code, prefs.getString(KeyMap.PREF + code, null))

    private fun setKeyAction(b: KeyMap.Btn, a: KeyAction) {
        prefs.edit().putString(KeyMap.PREF + b.code, a.id).apply()
    }

    private fun keyLabel(b: KeyMap.Btn) = L.t(b.name) + "  →  " + L.t(keyAction(b.code).label)

    private fun refreshKeyButtons() {
        for ((i, kb) in KeyMap.buttons.withIndex()) keyBtns.getOrNull(i)?.text = keyLabel(kb)
    }

    /** Выполняет действие, назначенное на кнопку пульта (при просмотре эфира). */
    private fun runKeyAction(a: KeyAction) {
        when (a) {
            KeyAction.NONE -> {}
            KeyAction.FAV -> cur?.let { toggleFav(it); hud() }
            KeyAction.SOUND -> openPlayerOptions()
            KeyAction.ASPECT -> cycleAspect()
            KeyAction.SEARCH -> openSearch()
            KeyAction.SUBS -> showSubs()
            KeyAction.AUDIO -> showAudio()
            KeyAction.INFO -> hud()
            KeyAction.SETTINGS -> openSetup()
            KeyAction.CHANNELS -> openChans()
            KeyAction.ARCHIVE -> openArch()
            KeyAction.PLAYPAUSE -> { player.playWhenReady = !player.playWhenReady; showSeek() }
            KeyAction.FWD -> if (arch != null) seekArch(30000) else player.seekTo(player.currentPosition + 30000)
            KeyAction.BACK30 -> if (arch != null) seekArch(-30000) else player.seekTo(maxOf(0L, player.currentPosition - 30000))
            KeyAction.PREV -> step(-1)
            KeyAction.NEXT -> step(1)
            KeyAction.ARCH_BACK -> if (arch != null) seekArch(-30000) else openArch()
            KeyAction.ARCH_FWD -> if (arch != null) seekArch(30000) else openArch()
        }
    }

    /** Раскрывает раздел настроек [i] (остальные сворачиваются) или сворачивает все, если [open] = false. */
    private fun showSection(i: Int, open: Boolean) {
        for (k in secBodies.indices) {
            val on = open && k == i
            secBodies[k].visibility = if (on) View.VISIBLE else View.GONE
            secHeads[k].text = (if (on) "▾  " else "▸  ") + secTitles[k]
        }
        if (open && i in secHeads.indices) setup.post {
            // заголовок раскрытого раздела поднимаем к верху экрана
            var y = 0
            var v: View = secHeads[i]
            val inner = setup.getChildAt(0)
            while (v !== inner && v.parent is View) { y += v.top; v = v.parent as View }
            setup.smoothScrollTo(0, (y - dp(12)).coerceAtLeast(0))
        }
    }

    private fun openSetup() {
        val pl = (prefs.getString("pl", "") ?: "").ifEmpty { getString(R.string.default_playlist) }
        etPl.setText(pl)
        etEpg.setText(prefs.getString("epg", "") ?: "")
        etTz.setText(Tz.format(tzMin()))
        etUa.setText(prefs.getString("ua", ""))
        tvSrv.text = srvLabel()
        btType.text = typeLabel()
        btSort.text = L.f("Порядок каналов: {0}", L.t(sortNames[sortMode].orEmpty()))
        btStart.text = startLabel()
        btZap.text = zapLabel()
        btDim.text = dimLabel()
        btFont.text = fontLabel()
        btTheme.text = themeLabel()
        btLang.text = langLabel()
        tvStat.text = L.f("Программа передач: {0}", L.t(epgStatus))
        setAdvanced(pl.isEmpty())   // если плейлиста нет, сразу показываем поля для ручного ввода
        showSection(0, pl.isEmpty())   // нет плейлиста — раскрыт «Источник», иначе все вкладки свёрнуты
        renderGroupsCard()
        refreshUpdateUi()
        showPanel(setup)
        setup.scrollTo(0, 0)
        if (pl.isEmpty()) btServer.requestFocus() else secHeads[0].requestFocus()
    }

    /** Сохраняет поля настроек (без перезагрузки плейлиста). */
    private fun persistSetup() {
        val newPl = etPl.text.toString().trim()
        val ed = prefs.edit()
        if (newPl != (prefs.getString("pl", "") ?: "")) ed.remove("srvn")   // адрес изменили вручную — имя сервера уже не то
        ed.putString("pl", newPl).putString("epg", etEpg.text.toString().trim())
            .putInt("tzm", Tz.parse(etTz.text.toString())).putString("ct", ctype).putString("ua", etUa.text.toString().trim()).apply()
    }

    private fun langPref() = prefs.getString("lang", "auto") ?: "auto"

    private fun langLabel() = L.f("Язык: {0}", when (langPref()) {
        "ru" -> "Русский"
        "en" -> "English"
        else -> L.t("Автоматически (язык системы)")
    })

    /** Выбор языка. Тексты задаются при построении интерфейса, поэтому экран пересоздаётся (как при смене цветовой схемы). */
    private fun chooseLang() {
        val codes = listOf("auto", "ru", "en")
        showChoice(L.t("Язык"), listOf(L.t("Автоматически (язык системы)"), "Русский", "English"), codes.indexOf(langPref()).coerceAtLeast(0)) { w ->
            if (codes[w] != langPref()) {
                persistSetup()
                prefs.edit().putString("lang", codes[w]).putBoolean("reopen", true).apply()
                Session.keep = true
                recreate()
            }
        }
    }

    private fun themeLabel() = L.f("Цветовая схема: {0}", L.t(Theme.current.name))

    /**
     * Выбор цветовой схемы. Цвета задаются при построении интерфейса, поэтому экран пересоздаётся;
     * плейлист и программа передач при этом остаются в [Session] и заново не загружаются (поток запускается снова).
     */
    private fun chooseTheme() {
        showChoice(L.t("Цветовая схема"), Theme.all.map { L.t(it.name) }, Theme.all.indexOf(Theme.current)) { w ->
            val t = Theme.all[w]
            if (t !== Theme.current) {
                persistSetup()
                prefs.edit().putString("theme", t.id).putBoolean("reopen", true).apply()
                Session.keep = true
                recreate()
            }
        }
    }

    /**
     * Применяет поля источника и перезагружает только то, что изменилось: другая ссылка плейлиста
     * (или [forcePlaylist], или список каналов пуст) — плейлист; другой адрес программы или часовой пояс — только программу.
     * Результат: 2 — запущена загрузка плейлиста, 1 — только программы передач, 0 — ничего не изменилось.
     * Остальные настройки (шрифт, тема, порядок, группы…) применяются сразу и сами ничего не перезагружают.
     */
    private fun applySource(forcePlaylist: Boolean = false): Int {
        val oldPl = prefs.getString("pl", "") ?: ""
        val oldEpg = prefs.getString("epg", "") ?: ""
        val oldTz = tzMin()
        val oldUa = prefs.getString("ua", "") ?: ""
        val newPl = etPl.text.toString().trim()
        val newEpg = etEpg.text.toString().trim()
        val newTz = Tz.parse(etTz.text.toString())
        val newUa = etUa.text.toString().trim()
        val ed = prefs.edit()
        if (newPl != oldPl) ed.remove("srvn")   // адрес изменили вручную — имя сервера уже не то
        ed.putString("pl", newPl).putString("epg", newEpg).putInt("tzm", newTz).putString("ct", ctype).putString("ua", newUa).apply()
        if (newUa != oldUa) {
            Iptv.customUa = newUa
            httpFactory.setUserAgent(newUa.ifEmpty { Iptv.goodUa ?: Iptv.DEFAULT_UA })
        }
        if (newPl.isNotEmpty() && (newPl != oldPl || forcePlaylist || chans.isEmpty())) { loadAll(false); return 2 }
        if (chans.isNotEmpty() && (newEpg != oldEpg || newTz != oldTz)) {
            toast(L.t("Загрузка программы передач…"))
            epgStatus = L.t("загрузка…")
            val cs = chans; val g = loadGen
            Thread { loadEpgNow(cs, plEpgUrl, false, newTz, g) }.start()
            return 1
        }
        return 0
    }

    /** Закрывает настройки; изменённые поля источника применяются. */
    private fun closeSetup() {
        if (chans.isEmpty() && etPl.text.toString().trim().isEmpty()) { toast(L.t("Выберите сервер или впишите ссылку на плейлист")); return }
        applySource()
        closePanels()
    }

    /** Подпись кнопки обновления в настройках: найденная новая версия или обычная проверка. */
    private fun refreshUpdateUi() {
        if (!::btUpd.isInitialized || !::updates.isInitialized) return
        val i = updates.available
        if (::tvUpdStat.isInitialized) tvUpdStat.text = L.t("Обновление программы: ") + updates.status
        btUpd.text = if (i != null) L.f("⇧ Установить обновление (сборка {0})", i.code) else L.t("Проверить обновления")
    }

    /* ---------- размер шрифта ---------- */
    private fun fontLabel() = L.f("Размер шрифта списков: {0}%  (◄ ► ±10)", fontPct)

    private fun setFont(v: Int) {
        fontPct = v.coerceIn(70, 160)
        UiScale.f = fontPct / 100f
        prefs.edit().putInt("fs", fontPct).apply()
        applyFont()
        btFont.text = fontLabel()
    }

    /** Колонка дат архива: ширина по самой длинной надписи «Сегодня», всё остальное место отдано передачам. */
    private fun fitDayList() {
        val paint = android.graphics.Paint()
        paint.textSize = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_SP, 20f * UiScale.f, resources.displayMetrics)
        // в ширину строки входят отступы (12 + 12) и левое поле текста (12); +4 — запас, чтобы «Сегодня» не обрезалось
        dayList.layoutParams = LinearLayout.LayoutParams(paint.measureText(L.t("Сегодня")).toInt() + dp(40), -1)
    }

    /** Применяет масштаб шрифта к плашкам, сообщениям и всем спискам. */
    private fun applyFont() {
        val f = UiScale.f
        hudT.textSize = 26f * f; hudP.textSize = 18f * f; hudN.textSize = 16f * f; hudLeft.textSize = 16f * f
        seekT.textSize = 18f * f; seekL.textSize = 16f * f; seekR.textSize = 16f * f
        toastTv.textSize = 18f * f
        chTitle.textSize = 20f * f; arTitle.textSize = 24f * f; arPrefix.textSize = 24f * f; arInfo.textSize = 14f * f
        fitDayList()
        for (l in listOf(grpList, chList, dayList, progList, srvList)) ad(l).notifyDataSetChanged()
    }

    /* ---------- пропорции картинки ---------- */
    private val aspectKeys = listOf("fit", "zoom", "fill", "r169", "r43")
    private val aspectNames get() = listOf(
        L.t("По размеру (без искажений)"), L.t("Увеличить (края обрезаются)"), L.t("Растянуть на весь экран"),
        L.t("16:9 (растянуть по центру)"), L.t("4:3 (растянуть по центру)")
    )

    private fun applyAspect() {
        val w = resources.displayMetrics.widthPixels
        val h = resources.displayMetrics.heightPixels
        var width = -1
        pv.resizeMode = when (aspect) {
            "zoom" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            "fill" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            "r169" -> { width = minOf(w, h * 16 / 9); AspectRatioFrameLayout.RESIZE_MODE_FILL }
            "r43" -> { width = minOf(w, h * 4 / 3); AspectRatioFrameLayout.RESIZE_MODE_FILL }
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        val lp = pv.layoutParams as FrameLayout.LayoutParams
        lp.width = width; lp.height = -1; lp.gravity = Gravity.CENTER
        pv.layoutParams = lp
    }

    private fun setAspect(key: String) {
        aspect = key
        prefs.edit().putString("aspect", key).apply()
        applyAspect()
        toast(L.t("Картинка: ") + aspectNames[aspectKeys.indexOf(key).coerceAtLeast(0)])
    }

    private fun cycleAspect() = setAspect(aspectKeys[(aspectKeys.indexOf(aspect) + 1) % aspectKeys.size])

    private fun showAspect() =
        showChoice(L.t("Пропорции картинки"), aspectNames, aspectKeys.indexOf(aspect)) { setAspect(aspectKeys[it]) }

    /* ---------- звуковые дорожки и субтитры ---------- */
    private fun showChoice(title: String, items: List<String>, checked: Int, pick: (Int) -> Unit) {
        android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(title)
            .setSingleChoiceItems(items.toTypedArray(), checked) { d, w -> d.dismiss(); pick(w) }
            .setNegativeButton(L.t("Закрыть"), null)
            .show()
    }

    private fun openPlayerOptions() {
        val items = listOf(
            L.t("♫ Звуковая дорожка…"), L.t("≡ Субтитры…"),
            L.t("▭ Пропорции: ") + aspectNames[aspectKeys.indexOf(aspect).coerceAtLeast(0)], L.t("⌕ Поиск канала…")
        )
        showChoice(L.t("Плеер"), items, -1) { when (it) { 0 -> showAudio(); 1 -> showSubs(); 2 -> showAspect(); else -> openSearch() } }
    }

    private class TrackOpt(val label: String, val group: Tracks.Group, val idx: Int, val selected: Boolean)

    private fun trackLabel(f: Format, n: Int): String {
        val lab = f.label?.takeIf { it.isNotEmpty() }
        val lang = f.language?.takeIf { it.isNotEmpty() && it != "und" }?.let {
            val nm = Locale.forLanguageTag(it).getDisplayLanguage(Locale.getDefault())
            if (nm.isEmpty() || nm.equals(it, true)) it.uppercase() else nm.replaceFirstChar { c -> c.uppercase() }
        }
        val extra = ArrayList<String>()
        if (lab != null && lang != null && !lab.contains(lang, true)) extra.add(lang)
        if (f.channelCount > 0) extra.add(when (f.channelCount) { 1 -> L.t("моно"); 2 -> L.t("стерео"); 6 -> "5.1"; else -> L.f("{0} кан.", f.channelCount) })
        val base = lab ?: lang ?: L.f("Дорожка {0}", n)
        return if (extra.isEmpty()) base else base + " · " + extra.joinToString(", ")
    }

    private fun trackOpts(type: Int): List<TrackOpt> {
        val l = ArrayList<TrackOpt>()
        for (g in player.currentTracks.groups) {
            if (g.type != type) continue
            for (i in 0 until g.length) if (g.isTrackSupported(i)) l.add(TrackOpt(trackLabel(g.getTrackFormat(i), l.size + 1), g, i, g.isTrackSelected(i)))
        }
        return l
    }

    private fun showAudio() {
        val o = trackOpts(C.TRACK_TYPE_AUDIO)
        if (o.isEmpty()) { toast(L.t("Звуковые дорожки не найдены (поток ещё не запущен?)")); return }
        showChoice(L.t("Звуковая дорожка"), o.map { it.label }, o.indexOfFirst { it.selected }) { w ->
            val t = o[w]
            val lang = t.group.getTrackFormat(t.idx).language?.takeIf { it.isNotEmpty() && it != "und" }
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setOverrideForType(TrackSelectionOverride(t.group.mediaTrackGroup, t.idx))
                .setPreferredAudioLanguage(lang)
                .build()
            prefs.edit().putString("alang", lang ?: "").apply()
            toast(L.t("Звук: ") + t.label)
        }
    }

    private fun showSubs() {
        val o = trackOpts(C.TRACK_TYPE_TEXT)
        val off = C.TRACK_TYPE_TEXT in player.trackSelectionParameters.disabledTrackTypes
        if (o.isEmpty() && off) { toast(L.t("Субтитры в этом потоке не найдены")); return }
        val items = listOf(L.t("Выключены")) + o.map { it.label }
        val checked = if (off) 0 else o.indexOfFirst { it.selected } + 1
        showChoice(L.t("Субтитры"), items, checked) { w ->
            val b = player.trackSelectionParameters.buildUpon()
            if (w == 0) {
                b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                prefs.edit().putString("sub", "off").apply()
                toast(L.t("Субтитры выключены"))
            } else {
                val t = o[w - 1]
                val lang = t.group.getTrackFormat(t.idx).language?.takeIf { it.isNotEmpty() && it != "und" }
                b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(TrackSelectionOverride(t.group.mediaTrackGroup, t.idx))
                    .setPreferredTextLanguage(lang)
                prefs.edit().putString("sub", lang ?: "on").apply()
                toast(L.t("Субтитры: ") + t.label)
            }
            player.trackSelectionParameters = b.build()
        }
    }

    /** Запомненный язык звука и режим субтитров применяются ко всем следующим каналам. */
    private fun applyTrackPrefs() {
        val b = player.trackSelectionParameters.buildUpon()
        prefs.getString("alang", "")?.takeIf { it.isNotEmpty() }?.let { b.setPreferredAudioLanguage(it) }
        val sub = prefs.getString("sub", "off") ?: "off"
        if (sub == "off") b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        else { b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false); if (sub != "on") b.setPreferredTextLanguage(sub) }
        player.trackSelectionParameters = b.build()
    }

    /* ---------- поиск и номера каналов ---------- */
    /** Каналы с номерами: все нескрытые, в выбранном порядке. */
    private fun numList(): List<Channel> = ordered().filter { it.group !in hiddenGroups }

    private fun searchIn(all: List<Channel>): List<Channel> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val nq = Iptv.norm(q)
        val lq = q.lowercase()
        return all.filter { (nq.isNotEmpty() && Iptv.norm(it.name).contains(nq)) || it.name.lowercase().contains(lq) }
    }

    private fun openSearch() {
        if (chans.isEmpty()) { toast(L.t("Сначала загрузите плейлист")); return }
        val et = EditText(this).apply {
            setSingleLine(); setText(query); setSelection(text.length)
            hint = L.t("Название канала"); textSize = 20f
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setTextColor(Color.WHITE); setHintTextColor(0xFF667780.toInt())
            setPadding(dp(24), dp(12), dp(24), dp(12))
        }
        val d = android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(L.t("Поиск канала"))
            .setView(et)
            .setPositiveButton(L.t("Найти")) { _, _ -> doSearch(et.text.toString()) }
            .setNegativeButton(L.t("Отмена"), null)
            .create()
        et.setOnEditorActionListener { _, _, _ -> d.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.performClick(); true }
        d.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        d.show()
    }

    private fun doSearch(q: String) {
        query = q.trim()
        grp = if (query.isEmpty()) G_ALL else G_FIND
        openChans()
        if (query.isNotEmpty()) toast(if (shown.isEmpty()) L.f("Ничего не найдено: «{0}»", query) else L.f("Найдено: {0}", shown.size))
        if (shown.isNotEmpty()) chList.requestFocus()
    }

    private val numRun = Runnable { commitNum() }

    private fun typeDigit(d: Int) {
        if (numBuf.length >= 4) numBuf.setLength(0)
        numBuf.append(d)
        numTv.text = numBuf.toString(); numTv.visibility = View.VISIBLE
        ui.removeCallbacks(numRun); ui.postDelayed(numRun, 1800)
    }

    private fun commitNum() {
        ui.removeCallbacks(numRun)
        val n = numBuf.toString().toIntOrNull()
        numBuf.setLength(0); numTv.visibility = View.GONE
        if (n == null) return
        val l = numList()
        if (n in 1..l.size) { zapGrp = null; playChan(l[n - 1]) } else toast(L.f("Нет канала №{0}", n))
    }

    /* ---------- история и запоминание позиции в архиве ---------- */
    private fun addHist(c: Channel) {
        history.remove(c.okey); history.add(0, c.okey)
        while (history.size > 30) history.removeAt(history.size - 1)
        prefs.edit().putString("hist", history.joinToString("\n")).apply()
    }

    private fun arcKey(c: Channel, p: Prog) = c.okey + "@" + p.s

    /** Запоминает, на каком месте остановились в архивной передаче (не больше 20 передач). Начало и конец (≈1 мин) не запоминаются. */
    private fun saveArcPos() {
        if (restoring) return
        val a = arch ?: return
        val c = arcOwner ?: return
        if (readyMode == null) return
        val pos = player.currentPosition
        val total = (a.e - a.s) * 1000
        val k = arcKey(c, a)
        resume.remove(k)
        if (pos > 60000 && pos < total - 60000) resume[k] = pos
        while (resume.size > 20) resume.remove(resume.keys.first())
        prefs.edit().putString("resume", resume.entries.joinToString("\n") { "${it.value}|${it.key}" }).apply()
    }

    /* ---------- воспроизведение ---------- */
    private fun playUrl(url: String, hls: Boolean = false) {
        lastUrl = url; triedHls = hls
        val mi = MediaItem.Builder().setUri(url)
        if (hls) mi.setMimeType(MimeTypes.APPLICATION_M3U8)
        // архив: начинаем с запомненной позиции (или с места обрыва при переподключении)
        if (arch != null && startPos > 0) player.setMediaItem(mi.build(), startPos) else player.setMediaItem(mi.build())
        player.prepare()
        player.playWhenReady = true
    }

    /** Новое воспроизведение: сбрасывает отложенные повторы и состояние восстановления. */
    private fun startUrl(url: String) {
        ui.removeCallbacks(retryRun); ui.removeCallbacks(stallRun)
        retries = 0; readyMode = null
        playUrl(url)
    }

    /** Планирует переподключение с нарастающей паузой (1, 2, 4, 8, 8 с). false — попытки исчерпаны. */
    private fun scheduleRetry(): Boolean {
        if (lastUrl.isEmpty() || retries >= maxRetries) return false
        retries++
        if (arch != null && readyMode != null && player.currentPosition > 0) startPos = player.currentPosition
        retryHls = readyMode == true
        toast(L.f("Связь потеряна, повтор {0} из {1}…", retries, maxRetries))
        ui.removeCallbacks(retryRun)
        ui.postDelayed(retryRun, minOf(1000L shl (retries - 1), 8000L))
        return true
    }

    private fun playChan(c: Channel) {
        saveArcPos()
        cur = c; arch = null; arcCands = emptyList(); startPos = 0L
        hideSeek.run()
        prefs.edit().putInt("last", c.idx).putString("lastk", c.okey).apply()
        addHist(c)
        startUrl(c.url)
        hud()
    }

    private fun playArch(p: Prog) {
        val c = cur ?: return
        saveArcPos()
        arch = p; arcOwner = c
        val pref = prefs.getInt("arcv_" + Iptv.hostOf(c.url), -1)
        arcCands = Iptv.arcCandidates(c, p.s, p.e, ctype).sortedByDescending { it.first == pref }
        arcIdx = 0
        startPos = resume[arcKey(c, p)] ?: 0L
        startUrl(arcCands[0].second)
        hud()
        toast(if (startPos > 0) L.f("Архив: продолжаю с {0} · {1}", fmtT(startPos), p.title) else L.t("Архив: ") + hm(p.s) + " " + p.title)
    }

    /** Листать ↑/↓ по списку, выбранному в панели (группа / избранное)? По умолчанию — да; иначе по всем каналам. */
    private fun zapByList() = prefs.getString("zapm", "list") != "all"

    private fun zapLabel() = L.t("Листать ↑/↓: ") + if (zapByList()) L.t("по выбранному списку (группа, избранное)") else L.t("по всем каналам, по порядку номеров")

    /** Запоминает, из какого списка выбран канал: дальше ↑/↓ листают именно его. */
    private fun setZap() { zapGrp = grp; zapSnap = shown }

    /** Список для ↑/↓, каждый раз собирается заново: всегда совпадает с тем, что видно в панели (сортировка, избранное, свой порядок). */
    private fun zapList(): List<Channel> {
        val g = zapGrp ?: return emptyList()
        return when (g) {
            G_ALL -> emptyList()
            G_FAV -> ordered().filter { isFav(it) }
            G_HIST, G_FIND -> zapSnap
            else -> ordered().filter { it.group == g }
        }
    }

    /** ↑/↓: строго по порядку, на один канал за нажатие. */
    private fun step(d: Int) {
        // лишние события кнопки, пришедшие сразу следом за нажатием, игнорируем
        val t = android.os.SystemClock.uptimeMillis()
        if (t - lastStepAt < 250) return
        lastStepAt = t
        val all = ordered()
        var l = if (zapByList()) zapList() else emptyList()
        if (l.isEmpty()) l = numList()
        if (l.isEmpty()) l = all
        if (l.isEmpty()) return
        val i = l.indexOfFirst { it === cur }
        val n: Channel
        if (i >= 0) n = l[(i + d + l.size) % l.size]
        else {
            // текущего канала нет в списке (например, убран из избранного): берём ближайший канал списка в нужную сторону по общему порядку
            val inList = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Channel, Boolean>()).apply { addAll(l) }
            val ai = all.indexOfFirst { it === cur }
            if (ai < 0) n = if (d > 0) l[0] else l[l.size - 1]
            else {
                var found: Channel? = null
                for (k in 1..all.size) {
                    val c = all[((ai + (if (d > 0) k else -k)) % all.size + all.size) % all.size]
                    if (c in inList) { found = c; break }
                }
                n = found ?: l[0]
            }
        }
        playChan(n)
    }

    /* ---------- серверы и файлы ---------- */
    private fun archType(ch: Channel? = cur): String {
        val c = ch ?: return ctype
        return c.ctype.ifEmpty { if (c.csrc.isNotEmpty()) "default" else ctype }
    }

    private fun buildServers() {
        srvList = lv()
        srvPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(panelColor())
            setPadding(dp(20), dp(20), dp(20), dp(20))
            visibility = View.GONE
            addView(TextView(this@MainActivity).apply { text = L.t("Выберите сервер"); textSize = 24f; setTextColor(Color.WHITE); setPadding(0, 0, 0, dp(10)) })
            addView(srvList, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        root.addView(srvPanel, FrameLayout.LayoutParams((resources.displayMetrics.widthPixels * 0.7f).toInt(), -1, Gravity.CENTER_HORIZONTAL))
        srvList.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
            val sv = ad(srvList).row(pos).tag as? Server
            if (sv != null) {
                // тот же сервер выбран повторно: ничего не перезагружаем (обновить плейлист — в настройках)
                val same = sv.pl == (prefs.getString("pl", "") ?: "") && sv.epg == (prefs.getString("epg", "") ?: "") && chans.isNotEmpty()
                prefs.edit().putString("pl", sv.pl).putString("epg", sv.epg).putString("srvn", sv.name).apply()
                closePanels()
                // программа скачивается заново сама, если у нового сервера другой её адрес; принудительно — только по кнопке в настройках
                if (same) toast(L.t("Этот сервер уже выбран")) else loadAll(false)
            }
        }
    }

    /* ---------- часовой пояс программы (в минутах) ---------- */
    /** Сохранённый пояс в минутах; старое значение в часах («tz») пересчитывается. */
    private fun tzMin(): Int = if (prefs.contains("tzm")) prefs.getInt("tzm", 0) else prefs.getInt("tz", 0) * 60

    /** Архивная передача закончилась: включаем следующую из программы, а если её нет (или она ещё не вышла) — прямой эфир. */
    private fun archEnded() {
        val a = arch ?: return
        val c = cur ?: return
        val nx = epg[c.eid]?.firstOrNull { it.s >= a.e }
        if (nx != null && nx.s < now()) playArch(nx)
        else { playChan(c); toast(L.t("Архив закончился — прямой эфир")) }
    }

    /** «О программе»: версия, описание, изменения в текущей версии, разработчик. */
    private fun showAbout() {
        val ver = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "?" }
        val build = try { Updater.installedCode(this) } catch (e: Exception) { 0L }
        fun tx(t: String, size: Float, color: Int, top: Int = 0) = TextView(this).apply {
            text = t; textSize = size; setTextColor(color); setPadding(0, dp(top), 0, 0)
        }
        val gray = Theme.gray
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(8))
            addView(tx(L.f("Версия {0} · сборка {1}", ver, build), 17f, Theme.accent))
            addView(tx(L.t("Разработчик: ") + getString(R.string.about_dev), 16f, Color.WHITE, 4))
            addView(tx(L.t("О ПРОГРАММЕ"), 12f, gray, 16))
            addView(tx(getString(R.string.about_desc), 16f, Color.WHITE, 3))
            addView(tx(L.f("ЧТО НОВОГО В ВЕРСИИ {0}", ver), 12f, gray, 16))
            addView(tx(getString(R.string.about_changes), 16f, Color.WHITE, 3))
        }
        // фокусируемый ScrollView: на пульте текст прокручивается стрелками
        val sv = ScrollView(this).apply { isFocusable = true; addView(col) }
        android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(getString(R.string.app_name))
            .setView(sv)
            .setPositiveButton(L.t("Закрыть"), null)
            .show()
    }

    private fun openServers() {
        val l = repo.servers()
        if (l.isEmpty()) { toast(L.t("Список серверов пуст. Загрузите servers.txt кнопкой «Загрузить файл…»")); openSetup(); return }
        srvBackToSetup = setup.visibility == View.VISIBLE
        val act = prefs.getString("srvn", "") ?: ""
        ad(srvList).active = act
        ad(srvList).set(l.map { Row(it.name, Iptv.hostOf(it.pl), it) })
        showPanel(srvPanel)
        srvList.setSelection(l.indexOfFirst { it.name == act }.coerceAtLeast(0))
        srvList.requestFocus()
    }

    private fun pickFile() {
        try {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "*/*"
            if (!canHandle(i)) throw IOException(L.t("нет окна выбора файлов"))
            startActivityForResult(i, 7)
        } catch (e: Exception) {
            toast(L.t("Нет выбора файлов. Положите servers.txt в ") + getExternalFilesDir(null)?.absolutePath)
        }
    }

    /* ---------- резервная копия настроек ---------- */
    private fun appVersion(): String = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "?" } catch (e: Exception) { "?" }

    /** Есть ли в системе приложение, умеющее выполнить [i]. Без проверки на боксах без окна выбора файлов система сама пишет «Действие не поддерживается». */
    private fun canHandle(i: Intent): Boolean = try { i.resolveActivity(packageManager) != null } catch (e: Exception) { false }

    private fun backupName() = "noxis-settings-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".json"

    private fun hasStoragePerm(): Boolean =
        android.os.Build.VERSION.SDK_INT >= 29 || android.os.Build.VERSION.SDK_INT < 23 ||
            checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /** Сохранить настройки сразу, без окна выбора файлов: в «Загрузки» и в папку приложения. На Android 6–9 один раз спросит разрешение. */
    private fun exportSettings() {
        if (!hasStoragePerm() && !askedStorage) {
            askedStorage = true
            requestPermissions(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), RC_PERM)
            return
        }
        Thread { writeBackup(null, backupName()) }.start()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RC_PERM) exportSettings()   // разрешили или нет — сохраняем: без разрешения копия попадёт в папку приложения
    }

    /** Показывает результат окном: путь к файлу должен быть виден, пока его не прочитают. Вызывать из потока интерфейса. */
    private fun showInfo(title: String, msg: String) {
        if (isFinishing || isDestroyed) return
        android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(title).setMessage(msg).setPositiveButton("OK", null).show()
    }

    /** «Сохранить как…»: системное окно выбора места и имени файла. */
    private fun exportSettingsAs() {
        val name = backupName()
        try {
            val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "application/json"
            i.putExtra(Intent.EXTRA_TITLE, name)
            if (!canHandle(i)) throw IOException(L.t("нет окна выбора файлов"))
            startActivityForResult(i, RC_EXPORT)
        } catch (e: Exception) {
            toast(L.t("Нет окна выбора файлов — сохраняю в «Загрузки» и в папку приложения"))
            exportSettings()
        }
    }

    /** Кладёт копию в общую папку «Загрузки» (Android 10+, разрешения не нужны). false — не вышло или система старше. */
    private fun saveToDownloads(bytes: ByteArray, name: String): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 29) {
            if (!hasStoragePerm()) return false
            return try {
                @Suppress("DEPRECATION")
                val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                File(dir, name).writeBytes(bytes)
                true
            } catch (e: Exception) { false }
        }
        return try {
            val cv = android.content.ContentValues()
            cv.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
            cv.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/json")
            cv.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            val uri = contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: return false
            (contentResolver.openOutputStream(uri) ?: return false).use { it.write(bytes) }
            true
        } catch (e: Exception) { false }
    }

    /** Последняя копия, которую это приложение само положило в «Загрузки» (чужие файлы без разрешения не видны). */
    private fun findDownloadsBackup(): Uri? {
        if (android.os.Build.VERSION.SDK_INT < 29) return null
        return try {
            val col = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
            contentResolver.query(col, arrayOf(android.provider.MediaStore.MediaColumns._ID),
                android.provider.MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ?", arrayOf("noxis-settings%"),
                android.provider.MediaStore.MediaColumns.DATE_ADDED + " DESC")?.use { c ->
                if (c.moveToFirst()) android.content.ContentUris.withAppendedId(col, c.getLong(0)) else null
            }
        } catch (e: Exception) { null }
    }

    /** Копия в общей папке «Загрузки» на Android 6–9 (нужно разрешение на запись; на 10+ используется [findDownloadsBackup]). */
    private fun findLegacyBackup(): File? {
        if (android.os.Build.VERSION.SDK_INT >= 29 || !hasStoragePerm()) return null
        return try {
            @Suppress("DEPRECATION")
            val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            dir.listFiles { x -> x.name.startsWith("noxis-settings") && x.name.endsWith(".json") }?.maxByOrNull { it.lastModified() }
        } catch (e: Exception) { null }
    }

    /**
     * Записывает настройки в [u] (выбранный файл) или, если [u] == null (нет окна выбора файлов), в папку приложения
     * и дополнительно в «Загрузки». Вызывать из фонового потока.
     */
    private fun writeBackup(u: Uri?, name: String) {
        try {
            val bytes = SettingsBackup.export(prefs.all, appVersion(), SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()))
                .toByteArray(Charsets.UTF_8)
            if (u != null) {
                (contentResolver.openOutputStream(u, "wt") ?: throw IOException(L.t("нет доступа к файлу"))).use { it.write(bytes) }
                runOnUiThread { toast(L.t("Настройки сохранены в файл")) }
                return
            }
            val lines = ArrayList<String>()
            var err = ""
            try {
                val dir = getExternalFilesDir(null) ?: throw IOException(L.t("нет папки приложения"))
                val f = File(dir, SettingsBackup.FILE_NAME)
                f.writeBytes(bytes)
                lines.add(L.t("Папка приложения:\n") + f.absolutePath)
            } catch (e: Exception) { err = e.javaClass.simpleName + ": " + e.message }
            if (saveToDownloads(bytes, name)) lines.add(0, L.f("Папка «Загрузки»:\n{0}", name))
            runOnUiThread {
                if (lines.isEmpty()) showInfo(L.t("Не удалось сохранить"), err.ifEmpty { L.t("некуда записать файл") })
                else showInfo(L.t("Настройки сохранены"), lines.joinToString("\n\n"))
            }
        } catch (e: Exception) {
            val why = e.javaClass.simpleName + ": " + e.message
            runOnUiThread { showInfo(L.t("Не удалось сохранить"), why) }
        }
    }

    /** Восстановить настройки из файла: системный выбор файла; на приставках без него — копия из папки приложения или «Загрузок». */
    private fun importSettings() {
        try {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "*/*"
            if (!canHandle(i)) throw IOException(L.t("нет окна выбора файлов"))
            startActivityForResult(i, RC_IMPORT)
        } catch (e: Exception) {
            Thread {
                val dir = getExternalFilesDir(null)
                val f = dir?.let { File(it, SettingsBackup.FILE_NAME) }
                val d = findDownloadsBackup()
                val lg = findLegacyBackup()
                when {
                    f != null && f.exists() -> readBackup(null, f)
                    d != null -> readBackup(d, null)
                    lg != null -> readBackup(null, lg)
                    else -> runOnUiThread {
                        toast(L.t("Нет выбора файлов. Положите ") + SettingsBackup.FILE_NAME + L.t(" в ") + dir?.absolutePath + L.t(" или восстановите по ссылке"))
                    }
                }
            }.start()
        }
    }

    /** Общая часть чтения: получает байты, проверяет файл настроек и спрашивает подтверждение. Вызывать из фонового потока. */
    private fun runBackupRead(failMsg: String, load: () -> ByteArray) {
        try {
            val m = SettingsBackup.parse(Iptv.text(load()))
            runOnUiThread { confirmRestore(m) }
        } catch (e: SettingsBackup.BadBackup) {
            runOnUiThread { toast(e.message ?: L.t("Это не файл настроек")) }
        } catch (e: TooBigException) {
            runOnUiThread { toast(L.t("Файл слишком большой для файла настроек")) }
        } catch (e: Exception) {
            runOnUiThread { toast(failMsg) }
        }
    }

    private fun readBackup(u: Uri?, f: File?) = runBackupRead(L.t("Не удалось прочитать файл")) {
        val ins = (if (u != null) contentResolver.openInputStream(u) else f?.inputStream()) ?: throw IOException(L.t("нет доступа к файлу"))
        ins.use { Iptv.readLimited(it, SettingsBackup.MAX_BYTES) }
    }

    /** Восстановить настройки по ссылке (прямая ссылка на файл настроек, например raw-ссылка GitHub). */
    private fun importSettingsFromUrl() {
        val et = EditText(this).apply {
            hint = "https://…/noxis-settings.json"
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        val pad = dp(20)
        val box = FrameLayout(this).apply { setPadding(pad, dp(8), pad, 0); addView(et, FrameLayout.LayoutParams(-1, -2)) }
        android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(L.t("Восстановить по ссылке"))
            .setMessage(L.t("Прямая ссылка на файл настроек (http или https)."))
            .setView(box)
            .setPositiveButton(L.t("Скачать")) { _, _ ->
                val url = et.text.toString().trim()
                if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) toast(L.t("Ссылка должна начинаться с http:// или https://"))
                else {
                    toast(L.t("Загрузка…"))
                    Thread { runBackupRead(L.t("Не удалось скачать файл")) { Iptv.http(url, false, SettingsBackup.MAX_BYTES) } }.start()
                }
            }
            .setNegativeButton(L.t("Отмена"), null)
            .show()
    }

    private fun confirmRestore(m: Map<String, Any>) {
        if (isFinishing || isDestroyed) return
        android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(L.t("Восстановить настройки?"))
            .setMessage(L.f("Параметров в файле: {0}. Текущие настройки (сервер, оформление, избранное, кнопки пульта…) будут заменены, экран перезапустится.", m.size))
            .setPositiveButton(L.t("Восстановить")) { _, _ -> applyRestore(m) }
            .setNegativeButton(L.t("Отмена"), null)
            .show()
    }

    /** Заменяет все настройки из резервной копии значениями из файла и перезапускает экран: он заново читает всё из настроек. */
    private fun applyRestore(m: Map<String, Any>) {
        val vals = LinkedHashMap(m)
        // локальный плейлист в копию не входит: без файла ссылка «local» бесполезна
        if (vals["pl"] == "local" && !File(filesDir, "local.m3u").exists()) { vals.remove("pl"); vals.remove("srvn") }
        restoring = true   // onStop старого экрана не должен записать поверх восстановленного свои данные
        val ed = prefs.edit()
        for (k in prefs.all.keys) if (SettingsBackup.isBackupKey(k)) ed.remove(k)
        for ((k, v) in vals) when (v) {
            is String -> ed.putString(k, v)
            is Int -> ed.putInt(k, v)
            is Set<*> -> ed.putStringSet(k, v.filterIsInstance<String>().toSet())
        }
        ed.putBoolean("reopen", true)
        ed.commit()
        Session.clear()   // плейлист другого сервера: заново загрузить
        recreate()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val u = data?.data
        if (requestCode == RC_EXPORT && resultCode == RESULT_OK && u != null) { Thread { writeBackup(u, backupName()) }.start(); return }
        if (requestCode == RC_IMPORT && resultCode == RESULT_OK && u != null) { Thread { readBackup(u, null) }.start(); return }
        if (requestCode == 7 && resultCode == RESULT_OK && u != null) {
            Thread {
                try {
                    // тот же лимит и та же кодировка (UTF-8 / windows-1251, gzip, BOM), что и у сетевого плейлиста
                    val bytes = (contentResolver.openInputStream(u) ?: throw IOException(L.t("нет доступа к файлу")))
                        .use { Iptv.readLimited(it, Iptv.MAX_PLAYLIST) }
                    val t = Iptv.text(bytes)
                    val isPlaylist = repo.importText(t)
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        if (isPlaylist) { closePanels(); loadAll(false) }
                        else if (Iptv.parseServers(t).isEmpty()) toast(L.t("В файле нет адресов. Формат: Название | http://… | EPG"))
                        else openServers()
                    }
                } catch (e: TooBigException) {
                    runOnUiThread { toast(L.f("Файл слишком большой (лимит {0} МБ)", Iptv.MAX_PLAYLIST / 1048576)) }
                } catch (e: Exception) {
                    runOnUiThread { toast(L.t("Не удалось прочитать файл")) }
                }
            }.start()
        }
    }

    /* ---------- загрузка данных ---------- */
    /** Показывает каналы: группа при запуске, текущий канал, значки меню. Вызывать из потока интерфейса. */
    private fun applyChannels(ch: List<Channel>, msg: String) {
        chans = ch
        groups = ch.map { it.group }.distinct()
        val sm = prefs.getString("start", "fav")
        val lg = prefs.getString("lgrp", G_ALL) ?: G_ALL
        val startFav = sm == "fav" && ch.any { isFav(it) }
        grp = when {
            startFav -> G_FAV
            sm == "last" && (lg == G_FAV || lg == G_HIST || (lg in groups && lg !in hiddenGroups)) -> lg
            else -> G_ALL
        }
        zapGrp = if (startFav) G_FAV else null
        if (msg.isNotEmpty()) toast(msg)
        val lk = prefs.getString("lastk", "") ?: ""
        val first = ch.firstOrNull { lk.isNotEmpty() && it.okey == lk } ?: ch.getOrElse(prefs.getInt("last", 0)) { ch[0] }
        // приложение свёрнуто — не запускаем звук в фоне, onStart сам включит выбранный канал
        if (started) playChan(first) else { cur = first; arch = null }
        renderGroupsCard()
        refreshRail()
        if (reopenSetup) { reopenSetup = false; openSetup() }
    }

    /** Возврат к уже загруженным данным после recreate() (смена темы): без запросов к серверу и без разбора программы. */
    private fun restoreSession() {
        epg = Session.epg
        plEpgUrl = Session.plEpgUrl
        epgStatus = Session.epgStatus
        applyChannels(Session.chans, "")
        if (epg.isEmpty()) {
            // программа к моменту смены темы ещё не успела загрузиться — берём её из кэша (быстро, без скачивания)
            epgStatus = L.t("загрузка…")
            val cs = chans; val g = loadGen; val tz = tzMin(); val pe = plEpgUrl
            Thread { loadEpgNow(cs, pe, false, tz, g) }.start()
        }
    }

    private fun loadAll(force: Boolean = false) {
        val gen = ++loadGen   // результаты более ранних загрузок будут отброшены
        val pl = prefs.getString("pl", "") ?: ""
        val tz = tzMin()
        epg = emptyMap(); Session.epg = emptyMap(); epgStatus = L.t("загрузка…")
        toast(L.t("Загрузка плейлиста…"))
        Thread {
            try {
                val loaded = repo.loadPlaylist(pl)
                val list = loaded.list
                val fromCache = loaded.fromCache
                if (gen != loadGen) return@Thread
                runOnUiThread {
                    if (gen != loadGen || isFinishing || isDestroyed) return@runOnUiThread
                    applyChannels(list.ch, if (fromCache) L.f("Сервер недоступен — показан сохранённый список: {0}", list.ch.size) else L.f("Каналов: {0}", list.ch.size))
                    Session.chans = chans; Session.groups = groups; Session.pl = pl; Session.plEpgUrl = list.epgUrl
                }
                if (gen != loadGen) return@Thread
                plEpgUrl = list.epgUrl
                loadEpgNow(list.ch, list.epgUrl, force, tz, gen)
            } catch (e: Exception) {
                runOnUiThread { if (gen == loadGen) { toast(L.t("Ошибка: ") + Iptv.safeMsg(e)); if (chans.isEmpty()) openSetup() } }
            }
        }.start()
    }

    /** Загрузка программы передач (вызывать из фонового потока): работу делает [Repository], здесь только показ результата. */
    private fun loadEpgNow(chs: List<Channel>, plEpg: String, force: Boolean, tz: Int, gen: Int = loadGen) {
        repo.loadEpg(chs, plEpg, getString(R.string.default_epg), force, tz, object : EpgListener {
            override fun onNoUrl() {
                runOnUiThread { epgStatus = L.t("адрес не указан"); toast(L.t("В плейлисте нет адреса программы передач (url-tvg) — его можно указать в настройках")) }
            }

            override fun onEpg(eids: List<String>, map: Map<String, List<Prog>>, matched: Int) {
                val total = map.values.sumOf { it.size }
                runOnUiThread {
                    if (gen != loadGen) return@runOnUiThread
                    epg = map
                    Session.epg = map
                    epgStatus = L.f("передач {0}, каналов с программой {1} из {2}", total, matched, chs.size)
                    toast(L.f("Программа: {0}", L.t(epgStatus)))
                    if (chPanel.visibility == View.VISIBLE && !moving) { val sp = chList.selectedItemPosition; renderChans(); chList.setSelection(sp.coerceAtLeast(0)) }
                    if (setup.visibility == View.VISIBLE) tvStat.text = L.f("Программа передач: {0}", L.t(epgStatus))
                    hud()
                }
            }

            override fun onError(e: Throwable) {
                runOnUiThread {
                    if (gen != loadGen) return@runOnUiThread
                    epgStatus = L.t("ошибка: ") + Iptv.safeMsg(e) + (if (e.message?.contains("403") == true) L.t(" — сервер отклонил запрос; проверьте ссылку в браузере или задайте User-Agent в настройках") else "")
                    toast(L.f("Программа передач: {0}", L.t(epgStatus)))
                    if (setup.visibility == View.VISIBLE) tvStat.text = L.f("Программа передач: {0}", L.t(epgStatus))
                }
            }
        })
    }

    /* ---------- пульт ---------- */
    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val k = e.keyCode
        if (panelOpen()) {
            // телепрограмма рядом со списком каналов: ← из списка дней или «Назад» возвращает к выбору канала
            if (arPanel.visibility == View.VISIBLE && chPanel.visibility == View.VISIBLE &&
                (k == KeyEvent.KEYCODE_BACK || (k == KeyEvent.KEYCODE_DPAD_LEFT && dayList.hasFocus()))) {
                // BACK обрабатываем только на отпускание (иначе ACTION_UP после скрытия панели закрыл бы и список каналов), ← — на нажатие
                val fire = if (k == KeyEvent.KEYCODE_BACK) e.action == KeyEvent.ACTION_UP
                else e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0
                if (fire) { arPanel.visibility = View.GONE; chList.requestFocus() }
                return true
            }
            if (moving) {
                if (chPanel.visibility != View.VISIBLE) moving = false
                else {
                    when (k) {
                        KeyEvent.KEYCODE_DPAD_UP -> { if (e.action == KeyEvent.ACTION_DOWN) moveBy(-1); return true }
                        KeyEvent.KEYCODE_DPAD_DOWN -> { if (e.action == KeyEvent.ACTION_DOWN) moveBy(1); return true }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BACK,
                        KeyEvent.KEYCODE_PROG_GREEN, KeyEvent.KEYCODE_POUND -> { if (e.action == KeyEvent.ACTION_UP) stopMove(); return true }
                        else -> return true
                    }
                }
            }
            if (k == KeyEvent.KEYCODE_BACK) {
                if (setup.visibility == View.VISIBLE && chans.isEmpty()) return super.dispatchKeyEvent(e)
                if (e.action == KeyEvent.ACTION_UP) {
                    when {
                        srvPanel.visibility == View.VISIBLE -> if (srvBackToSetup) openSetup() else openChans()
                        setup.visibility == View.VISIBLE -> closeSetup()
                        // первый «Назад» сворачивает список групп, второй закрывает панель
                        chPanel.visibility == View.VISIBLE && groupsOpen -> { setGroupsOpen(false); chList.requestFocus() }
                        else -> closePanels()
                    }
                }
                return true
            }
            if (k == KeyEvent.KEYCODE_SEARCH) {
                if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) openSearch()
                return true
            }
            // запасные клавиши избранного в списке каналов: Меню, «*», жёлтая
            if (chPanel.visibility == View.VISIBLE && chList.hasFocus() &&
                (k == KeyEvent.KEYCODE_MENU || k == KeyEvent.KEYCODE_STAR || k == KeyEvent.KEYCODE_PROG_YELLOW)) {
                if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) favAt(chList.selectedItemPosition)
                return true
            }
            // → на выбранном канале в списке — телепрограмма и архив этого канала
            if (chPanel.visibility == View.VISIBLE && chList.hasFocus() && k == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) shown.getOrNull(chList.selectedItemPosition)?.let { openArch(it, true) }
                return true
            }
            if (chPanel.visibility == View.VISIBLE && chList.hasFocus() && (k == KeyEvent.KEYCODE_PROG_GREEN || k == KeyEvent.KEYCODE_POUND)) {
                if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) startMove()
                return true
            }
            return super.dispatchKeyEvent(e)
        }
        // цифры: набор номера канала (номер = позиция в списке всех нескрытых каналов, виден в плашке канала)
        val dg = when (k) {
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> k - KeyEvent.KEYCODE_0
            in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> k - KeyEvent.KEYCODE_NUMPAD_0
            else -> -1
        }
        if (dg >= 0) {
            if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) typeDigit(dg)
            return true
        }
        val live = setOf(
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN
        ) + KeyMap.buttons.map { it.code }   // переназначаемые кнопки (раздел настроек «Кнопки пульта»)
        // «Назад» в прямом эфире — подтверждение выхода
        if (k == KeyEvent.KEYCODE_BACK && arch == null) {
            if (e.action == KeyEvent.ACTION_UP) confirmExit()
            return true
        }
        // «Назад» во время архива — возврат в прямой эфир
        val live0 = cur
        if (k == KeyEvent.KEYCODE_BACK && arch != null && live0 != null) {
            if (e.action == KeyEvent.ACTION_UP) { playChan(live0); toast(L.t("Прямой эфир")) }
            return true
        }
        if (k !in live) return super.dispatchKeyEvent(e)
        if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) onLiveKey(k)
        // удержание ←/→ в архиве: непрерывная перемотка с ускорением (шаг растёт, пока кнопка зажата)
        else if (e.action == KeyEvent.ACTION_DOWN && arch != null && (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) &&
            keyAction(k).let { it == KeyAction.ARCH_BACK || it == KeyAction.ARCH_FWD }) {
            val t = android.os.SystemClock.uptimeMillis()
            if (t - lastHoldSeek >= 250) {
                lastHoldSeek = t
                val hold = e.eventTime - e.downTime
                val step = when { hold < 1500 -> 30000L; hold < 3500 -> 60000L; else -> 120000L }
                seekArch(if (keyAction(k) == KeyAction.ARCH_BACK) -step else step)
            }
        }
        return true
    }

    private var exitDlg: android.app.AlertDialog? = null
    private var lastStepAt = 0L

    private fun confirmExit() {
        if (exitDlg?.isShowing == true) return
        exitDlg = android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(L.t("Выйти из программы?"))
            .setPositiveButton(L.t("Да")) { _, _ -> finish() }
            .setNegativeButton(L.t("Нет"), null)
            .create().also {
                it.show()
                it.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)?.requestFocus()   // по умолчанию — «Нет»
            }
    }

    private fun onLiveKey(k: Int) {
        // переназначаемые кнопки: действие берётся из настроек (по умолчанию — прежнее поведение)
        if (KeyMap.find(k) != null) { runKeyAction(keyAction(k)); return }
        when (k) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> if (numBuf.isNotEmpty()) commitNum() else openChans()
            KeyEvent.KEYCODE_DPAD_LEFT -> if (arch != null) seekArch(-30000) else openArch()
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (arch != null) seekArch(30000) else openArch()
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_PAGE_UP -> step(-1)
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> step(1)
        }
    }

    /* ---------- жизненный цикл ---------- */
    @Suppress("DEPRECATION")
    override fun onWindowFocusChanged(has: Boolean) {
        super.onWindowFocusChanged(has)
        if (has) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
    }
    override fun onStop() {
        super.onStop()
        started = false
        saveArcPos()
        ui.removeCallbacks(retryRun); ui.removeCallbacks(stallRun)
        player.playWhenReady = false
    }
    override fun onStart() {
        super.onStart()
        started = true
        val c = cur
        if (c != null) {
            if (arch == null) startUrl(c.url) else player.playWhenReady = true
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        ui.removeCallbacksAndMessages(null)
        Iptv.onUa = null
        player.release()
    }
}
