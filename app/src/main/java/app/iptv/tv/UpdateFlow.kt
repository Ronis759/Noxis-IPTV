package app.iptv.tv

import android.app.Activity
import android.content.SharedPreferences

/** Диалоги и фоновые потоки обновления из GitHub; сама проверка и установка — в [Updater]. */
class UpdateFlow(private val a: Activity, private val prefs: SharedPreferences, private val toast: (String) -> Unit) {
    private fun now() = System.currentTimeMillis() / 1000
    private fun alive() = !a.isFinishing && !a.isDestroyed

    /** [manual] = false: тихая проверка при запуске (не чаще раза в 6 часов, молчит, если обновлений нет или сеть недоступна). */
    fun check(manual: Boolean) {
        val repo = a.getString(R.string.github_repo).trim()
        if (repo.isEmpty()) { if (manual) toast("Не задан репозиторий: впишите github_repo в strings.xml"); return }
        // Сборка не подписана постоянным ключом (локальная или CI без Secrets): новую версию поверх неё Android не установит — не предлагаем
        if (!BuildConfig.UPDATES_ENABLED) {
            if (manual) toast("Эта сборка подписана временным ключом — обновление поверх неё не установится. Подпишите APK своим ключом (см. README)")
            return
        }
        if (!manual && now() - prefs.getLong("upd_t", 0) < 6 * 3600) return
        if (manual) toast("Проверка обновлений…")
        Thread {
            try {
                val info = Updater.fetch(repo)
                prefs.edit().putLong("upd_t", now()).apply()
                val mine = Updater.installedCode(a)
                a.runOnUiThread {
                    if (!alive()) return@runOnUiThread
                    if (info != null && info.code > mine) ask(info, mine)
                    else if (manual) toast("Установлена последняя версия ($mine)")
                }
            } catch (e: Exception) {
                if (manual) a.runOnUiThread { toast("Не удалось проверить обновления: " + Iptv.safeMsg(e)) }
            }
        }.start()
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
