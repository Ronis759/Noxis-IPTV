package app.iptv.tv

/**
 * Подключённые источники (плейлисты). Без зависимостей от Android: проверяется юнит-тестами.
 * Ключ источника — адрес плейлиста или «local»/«local:метка» для выбранного файла.
 */
object Sources {
    /** Больше этого числа источников одновременно не подключаем. */
    const val MAX = 10

    /** Сохранённая строка (ключи через перевод строки) → список без пустых и повторов, не больше [MAX]. */
    fun parse(s: String): List<String> =
        s.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX)

    /** Добавляет источник к списку. null — пустой ключ, уже есть или достигнут лимит. */
    fun add(list: List<String>, key: String, max: Int = MAX): List<String>? {
        val k = key.trim()
        if (k.isEmpty() || k in list || list.size >= max) return null
        return list + k
    }
}
