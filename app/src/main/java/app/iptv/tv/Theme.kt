package app.iptv.tv

/**
 * Цветовые схемы интерфейса (все тёмные, чтобы не слепили в темноте перед ТВ).
 * Схема выбирается в Настройках и применяется при создании экрана: [use] вызывается до построения интерфейса.
 * Без зависимостей от Android.
 */
object Theme {
    /** Цвета заданы как 0xRRGGBB. [panel] — подложка списков и плашек, [deep] — фон настроек, [onAccent] — текст на акцентном фоне. */
    class Scheme(
        val id: String, val name: String,
        val accent: Int, val onAccent: Int, val panel: Int, val deep: Int,
        val card: Int, val cardStroke: Int, val btn: Int, val stroke: Int,
        val field: Int, val fieldFocus: Int, val gray: Int
    )

    val all = listOf(
        Scheme("teal", "Бирюзовая", 0x6FE3C1, 0x06231B, 0x101519, 0x0F1519, 0x151D23, 0x202B32, 0x26323B, 0x3A4A54, 0x1B252C, 0x24343D, 0x8A9AA5),
        Scheme("blue", "Синяя", 0x6CB6FF, 0x071A2E, 0x0E1320, 0x0D111C, 0x141B2B, 0x1F2A40, 0x24304A, 0x3A4A6B, 0x1A2338, 0x243252, 0x8A98B5),
        Scheme("amber", "Янтарная", 0xFFB84D, 0x2B1A00, 0x161310, 0x14110E, 0x1E1A15, 0x2D2820, 0x332D24, 0x54493A, 0x262119, 0x3A3226, 0xA39A8A),
        Scheme("purple", "Фиолетовая", 0xC39BFF, 0x1D0B38, 0x15111E, 0x130F1B, 0x1B1726, 0x2A2438, 0x2E2840, 0x4D4566, 0x221D30, 0x322B47, 0x9A93B0),
        Scheme("red", "Красная", 0xFF7A7A, 0x330909, 0x1A1111, 0x170F0F, 0x211717, 0x312222, 0x3A2828, 0x5E4141, 0x2A1D1D, 0x3E2A2A, 0xAD9393),
        Scheme("contrast", "Контрастная (чёрная и жёлтая)", 0xFFE600, 0x000000, 0x000000, 0x000000, 0x0A0A0A, 0x444444, 0x1A1A1A, 0x777777, 0x0F0F0F, 0x2A2A00, 0xCCCCCC)
    )

    @Volatile var current: Scheme = all[0]
        private set

    /** Выбирает схему по идентификатору; неизвестный идентификатор — схема по умолчанию. */
    fun use(id: String?) { current = all.firstOrNull { it.id == id } ?: all[0] }

    private fun opaque(c: Int) = 0xFF000000.toInt() or c
    private fun alpha(a: Int, c: Int) = (a shl 24) or c

    val accent: Int get() = opaque(current.accent)
    val onAccent: Int get() = opaque(current.onAccent)
    /** Подложка списков без прозрачности (прозрачность добавляется отдельно). */
    val panel: Int get() = current.panel
    val hud: Int get() = alpha(0xEE, current.panel)
    val toast: Int get() = alpha(0xEE, current.btn)
    val selector: Int get() = alpha(0x88, current.accent)
    val deep: Int get() = opaque(current.deep)
    val card: Int get() = opaque(current.card)
    val cardStroke: Int get() = opaque(current.cardStroke)
    val btn: Int get() = opaque(current.btn)
    val stroke: Int get() = opaque(current.stroke)
    val field: Int get() = opaque(current.field)
    val fieldFocus: Int get() = opaque(current.fieldFocus)
    val gray: Int get() = opaque(current.gray)
}
