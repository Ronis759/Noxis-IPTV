package app.iptv.tv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Проверка и установка обновлений из GitHub Releases (последний релиз, в нём файл .apk).
 * Скачанный APK проверяется по SHA-256 из релиза: без контрольной суммы или при несовпадении файл не устанавливается.
 */
object Updater {
    /** [sha256] — ожидаемая контрольная сумма APK (hex, нижний регистр). */
    class Info(val code: Long, val tag: String, val url: String, val size: Long, val sha256: String)

    /** Служебный User-Agent: запросы к GitHub не участвуют в подборе UA для плейлистов. */
    private const val UA = "Noxis-IPTV-Updater"
    private const val MAX_APK = 200L * 1024 * 1024
    private const val MAX_SUM = 4L * 1024

    /** Номер установленной сборки (versionCode = номер запуска GitHub Actions). */
    fun installedCode(ctx: Context): Long =
        PackageInfoCompat.getLongVersionCode(ctx.packageManager.getPackageInfo(ctx.packageName, 0))

    /**
     * Последний релиз репозитория [repo] («владелец/имя»). null — в релизе нет APK или номер не прочитался.
     * IOException — у APK нет контрольной суммы SHA-256 (поле digest ассета или файл `<apk>.sha256` в релизе).
     */
    fun fetch(repo: String): Info? {
        val j = try {
            JSONObject(Iptv.text(Iptv.http("https://api.github.com/repos/$repo/releases/latest", false, Iptv.MAX_JSON, UA)))
        } catch (e: HttpCodeException) {
            if (e.code == 404) throw IOException("релизов пока нет") else throw e
        }
        val tag = j.optString("tag_name")
        val code = Regex("\\d+").findAll(tag).lastOrNull()?.value?.toLongOrNull() ?: return null
        val assets = j.optJSONArray("assets") ?: return null
        var apk: JSONObject? = null
        val sums = HashMap<String, String>()   // имя файла суммы -> ссылка
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val n = a.optString("name")
            if (n.endsWith(".apk", true) && apk == null) apk = a
            else if (n.endsWith(".sha256", true)) sums[n.lowercase()] = a.optString("browser_download_url")
        }
        val a = apk ?: return null
        val url = a.getString("browser_download_url")
        if (!url.startsWith("https://", true)) throw IOException("ссылка на APK не https")
        val name = a.optString("name")
        var sha = Integrity.parseDigest(a.optString("digest"))
        if (sha == null) {
            val sumUrl = sums[name.lowercase() + ".sha256"]
            if (sumUrl != null && sumUrl.startsWith("https://", true))
                sha = Integrity.parseSumFile(Iptv.text(Iptv.http(sumUrl, false, MAX_SUM, UA)))
        }
        if (sha == null) throw IOException("в релизе нет контрольной суммы SHA-256 — установка отменена")
        return Info(code, tag, url, a.optLong("size"), sha)
    }

    /** Скачивает APK во внутреннюю папку и сверяет SHA-256. Файл с неверной суммой или размером удаляется. */
    fun download(ctx: Context, info: Info): File {
        val dir = File(ctx.filesDir, "updates").apply { mkdirs() }
        val f = File(dir, "update-${info.code}.apk")
        dir.listFiles()?.forEach { if (it != f) it.delete() }
        // уже скачанный файл годится, только если сумма совпала
        if (f.exists() && (info.size <= 0 || f.length() == info.size) && Integrity.sha256(f) == info.sha256) return f
        f.delete()
        Iptv.download(info.url, f, UA, MAX_APK)
        if (info.size > 0 && f.length() != info.size) { f.delete(); throw IOException("файл загружен не полностью") }
        if (Integrity.sha256(f) != info.sha256) { f.delete(); throw IOException("контрольная сумма не совпала — файл удалён") }
        return f
    }

    /** Запускает системный установщик. null — всё в порядке, иначе текст ошибки для пользователя. */
    fun install(a: Activity, f: File): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !a.packageManager.canRequestPackageInstalls()) {
            return try {
                a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.packageName)))
                "Разрешите установку из этого приложения и снова нажмите «Проверить обновления»"
            } catch (e: Exception) {
                "Разрешите установку из неизвестных источников для этого приложения в настройках ТВ"
            }
        }
        return try {
            val uri = FileProvider.getUriForFile(a, a.packageName + ".fileprovider", f)
            a.startActivity(Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            null
        } catch (e: Exception) {
            "На устройстве нет установщика приложений. Установите APK вручную"
        }
    }
}
