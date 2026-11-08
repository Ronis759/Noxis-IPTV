package app.iptv.tv

import java.util.Locale

/**
 * Язык интерфейса. Тексты в коде написаны по-русски и служат ключами: при английском языке они заменяются
 * переводом из [En], непереведённое остаётся русским. Подстановки записываются как {0}, {1}… ([f]).
 * Выбор языка: в настройках «Автоматически / Русский / English»; «Автоматически» берёт язык системы.
 * Без зависимостей от Android.
 */
object L {
    /** true — интерфейс на английском. Выставляется один раз при создании экрана ([MainActivity.attachBaseContext]). */
    @Volatile var en = false

    /** Языки системы, при которых «Автоматически» оставляет русский (русский язык там привычен); для остальных — английский. */
    private val RU_LIKE = setOf("ru", "uk", "be", "kk")

    /** Значение настройки «lang» ("auto" / "ru" / "en") и язык системы (например "en", "ru") → интерфейс на английском? */
    fun resolveEn(pref: String?, systemLang: String?): Boolean = when (pref) {
        "en" -> true
        "ru" -> false
        else -> (systemLang ?: "ru").lowercase(Locale.ROOT) !in RU_LIKE
    }

    fun t(ru: String): String = if (en) En.map[ru] ?: ru else ru

    private val slot = Regex("\\{(\\d+)\\}")

    /** Перевод с подстановкой: {0}, {1}… заменяются аргументами по номеру. */
    fun f(ru: String, vararg a: Any?): String =
        slot.replace(t(ru)) { m -> a.getOrNull(m.groupValues[1].toInt())?.toString() ?: m.value }
}
