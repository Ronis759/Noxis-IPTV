package app.iptv.tv

/**
 * Набор строк с сохранением (избранное, скрытые группы). Не зависит от Android: [save] вызывается после каждого изменения.
 * Избранное хранится по [Channel.okey]: у одноимённых каналов (дубли в разных группах) ключи разные («Кино», «Кино#2»),
 * а у первого одноимённого он совпадает с прежним ключом-названием, поэтому старое избранное сохраняется.
 */
class PrefSet(initial: Set<String>, private val save: (Set<String>) -> Unit) {
    private val set: MutableSet<String> = HashSet(initial)

    operator fun contains(s: String): Boolean = s in set

    /** Очищает набор (например, «показать все скрытые группы») и сохраняет. */
    fun clear() {
        set.clear()
        save(HashSet(set))
    }

    /** Добавляет или убирает [s]; true — теперь элемент в наборе. */
    fun toggle(s: String): Boolean {
        val now = set.add(s)
        if (!now) set.remove(s)
        save(HashSet(set))
        return now
    }
}
