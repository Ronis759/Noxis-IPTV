package app.iptv.tv

/** Пользовательские подборки каналов. */
class ChannelCategories(
    initial: Map<String, Set<String>>,
    private val save: (Map<String, Set<String>>) -> Unit
) {
    private val data = LinkedHashMap<String, MutableSet<String>>()

    init {
        initial.forEach { (name, keys) ->
            if (name.isNotBlank()) data[name] = keys.toMutableSet()
        }
    }

    fun names(): List<String> = data.keys.toList()
    fun move(name: String, delta: Int): Boolean {
        val keys = data.keys.toMutableList()
        val from = keys.indexOf(name)
        val to = from + delta
        if (from < 0 || to !in keys.indices) return false
        keys.add(to, keys.removeAt(from))
        val copy = keys.associateWith { data[it]!!.toSet() }
        data.clear()
        copy.forEach { (key, values) -> data[key] = values.toMutableSet() }
        persist()
        return true
    }
    fun contains(name: String, key: String): Boolean = data[name]?.contains(key) == true
    fun channels(name: String): Set<String> = data[name]?.toSet() ?: emptySet()

    fun create(name: String): Boolean {
        val n = name.trim()
        if (n.isEmpty() || data.containsKey(n)) return false
        data[n] = linkedSetOf()
        persist()
        return true
    }

    fun rename(old: String, name: String): Boolean {
        val n = name.trim()
        if (old !in data || n.isEmpty() || (n != old && n in data)) return false
        if (n != old) {
            val values = data.remove(old) ?: return false
            data[n] = values
            persist()
        }
        return true
    }

    fun remove(name: String): Boolean = data.remove(name)?.let { persist(); true } ?: false

    fun toggle(name: String, key: String): Boolean {
        val set = data[name] ?: return false
        val added = set.add(key)
        if (!added) set.remove(key)
        persist()
        return added
    }

    private fun persist() = save(data.mapValues { it.value.toSet() })
}
