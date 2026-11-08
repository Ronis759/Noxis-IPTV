package app.iptv.tv

import android.app.Activity
import android.content.SharedPreferences

/**
 * Проверка обновлений из GitHub и диалоги установки; сама проверка и установка — в [Updater].
 * Найденное обновление запоминается ([available]) и сохраняется в настройках: значок «Обновление»
 * в левой полосе меню показывается, пока установленная сборка старее найденной, даже после перезапуска.
 * [onChange] вызывается из любого потока при изменении [available].
 */
class UpdateFlow(
    private val a: Activity,
    private val prefs: SharedPreferences,
    private val toast: (String) -> Unit,
    private val onChange: () -> Unit
) {
    /** Найденное обновление, которое новее установленной сборки (null — обновлений нет). */
    @Volatile var available: Updater.Info? = null
        private set

    @Volatile private var busy = false
    @Volatile private var lastTry = 0L

    private fun now() = System.currentTimeMillis() / 1000
    private fun alive() = !a.isFinishing && !a.isDestroyed
    private fun mine(): Long = try { Updater.installedCode(a) } catch (e: Exception) { Long.MAX_VALUE }

    init {
        // обновление, найденное раньше: показываем значок сразу, без сети
        if (BuildConfig.UPDATES_ENABLED) {
            val i = load()
            if (i != null && i.code > mine()) available = i else if (i != null) forget()
        }
    }

    private fun save(i: Updater.Info) { prefs.edit().putString("upd_info", i.encode()).apply() }

    private fun forget() { prefs.edit().remove("upd_info").apply() }

    private fun load(): Updater.Info? = Updater.Info.decode(prefs.getString("upd_info", ""))

    /**
     * [manual] = false: тихая проверка (не чаще раза в 6 часов, после ошибки сети — не чаще раза в 10 минут);
     * диалога нет — при находке появляется значок в меню. [manual] = true: проверка по кнопке, сразу с диалогом.
     */
    fun check(manual: Boolean) {
        val repo = a.getString(R.string.github_repo).trim()
        if (repo.isEmpty()) { if (manual) toast("Не задан репозиторий: впишите github_repo в strings.xml"); return }
        // Сборка не подписана постоянным ключом (локальная или CI без Secrets): новую версию поверх неё Android не установит — не предлагаем
        if (!BuildConfig.UPDATES_ENABLED) {
            if (manual) toast("Эта сборка подписана временным ключом — обновление поверх неё не установится. Подпишите APK своим ключом (см. README)")
            return
        }
        if (manual) {
            // найденное раньше обновление предлагаем сразу, без ещё одного запроса
            available?.let { ask(it, mine()); return }
        } else if (now() - prefs.getLong("upd_t", 0) < 6 * 3600 || now() - lastTry < 600) return
        if (busy) return
        busy = true
        lastTry = now()
        if (manual) toast("Проверка обновлений…")
        Thread {
            try {
                val info = Updater.fetch(repo)
                prefs.edit().putLong("upd_t", now()).apply()
                val m = mine()
                val newer = info != null && info.code > m
                if (newer) { save(info!!); available = info } else { forget(); available = null }
                a.runOnUiThread {
                    if (!alive()) return@runOnUiThread
                    onChange()
                    if (newer) {
                        if (manual) ask(info!!, m)
                        else if (prefs.getLong("upd_seen", 0) != info!!.code) {
                            prefs.edit().putLong("upd_seen", info.code).apply()
                            toast("Доступно обновление (сборка ${info.code}): значок ⇧ в меню слева")
                        }
                    } else if (manual) toast("Установлена последняя версия ($m)")
                }
            } catch (e: Exception) {
                if (manual) a.runOnUiThread { toast("Не удалось проверить обновления: " + Iptv.safeMsg(e)) }
            } finally {
                busy = false
            }
        }.start()
    }

    /** Предлагает установить найденное обновление (по значку в меню). */
    fun promptInstall() {
        val i = available
        if (i == null) { check(true); return }
        ask(i, mine())
    }

    private fun ask(info: Updater.Info, mine: Long) {
        android.app.AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Доступна версия ${info.code}")
            .setMessage("Установлена: $mine. Скачать и установить обновление?")
            .setPositiveButton("Обновить") { _, _ -> download(info) }
            .setNegativeButton("Позже", null)
            .show()
    }

    private fun download(info: Updater.Info) {
        toast("Загрузка обновления…")
        Thread {
            try {
                val f = Updater.download(a, info)
                a.runOnUiThread {
                    if (!alive()) return@runOnUiThread
                    val err = Updater.install(a, f)
                    if (err != null) toast(err)
                }
            } catch (e: Exception) {
                a.runOnUiThread { toast("Не удалось скачать обновление: " + Iptv.safeMsg(e)) }
            }
        }.start()
    }
}
