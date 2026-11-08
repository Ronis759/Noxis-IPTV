package app.iptv.tv

/**
 * Данные, которые переживают пересоздание экрана (смена цветовой схемы вызывает recreate()):
 * плейлист и программа передач не скачиваются и не разбираются заново.
 * Живёт только в памяти процесса; при новом запуске приложения всё загружается как обычно.
 */
object Session {
    @Volatile var chans: List<Channel> = emptyList()
    @Volatile var groups: List<String> = emptyList()
    @Volatile var epg: Map<String, List<Prog>> = emptyMap()
    @Volatile var epgStatus: String = "не загружена"
    @Volatile var plEpgUrl: String = ""
    /** Ссылка плейлиста («local» — локальный файл), для которой сохранены данные. */
    @Volatile var pl: String = ""
    /** true — следующий onCreate должен взять данные отсюда (ставится перед recreate()). */
    @Volatile var keep: Boolean = false

    fun clear() {
        chans = emptyList(); groups = emptyList(); epg = emptyMap()
        epgStatus = "не загружена"; plEpgUrl = ""; pl = ""; keep = false
    }
}
