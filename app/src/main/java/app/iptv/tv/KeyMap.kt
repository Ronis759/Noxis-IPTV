package app.iptv.tv

import android.view.KeyEvent

/** Действие, которое можно назначить на кнопку пульта при просмотре эфира. */
enum class KeyAction(val id: String, val label: String) {
    NONE("none", "Нет действия"),
    FAV("fav", "Избранное: добавить / убрать"),
    SOUND("sound", "Звук, субтитры, формат"),
    ASPECT("aspect", "Пропорции картинки"),
    SEARCH("search", "Поиск канала"),
    SUBS("subs", "Субтитры"),
    AUDIO("audio", "Звуковая дорожка"),
    INFO("info", "Информация о канале"),
    SETTINGS("settings", "Настройки"),
    CHANNELS("channels", "Список каналов"),
    ARCHIVE("archive", "Архив текущего канала"),
    PLAYPAUSE("playpause", "Пауза / продолжить"),
    FWD("fwd", "Вперёд на 30 с"),
    BACK30("back30", "Назад на 30 с"),
    PREV("prev", "Канал выше по списку"),
    NEXT("next", "Канал ниже по списку"),
    ARCH_BACK("archback", "Архив / назад на 30 с"),
    ARCH_FWD("archfwd", "Архив / вперёд на 30 с"),
    REWIND("rewind", "Перемотка текущей передачи назад");

    companion object {
        fun byId(id: String?): KeyAction? = values().firstOrNull { it.id == id }
    }
}

/**
 * Назначение кнопок пульта при просмотре. Переназначаются «служебные» кнопки (цветные, меню, info и т. п.) и стрелки джойстика ↑ ↓ ← →
 * (только при просмотре эфира; в списках и меню стрелки работают как обычно). OK, «Назад» и цифры остаются как есть,
 * чтобы нельзя было остаться без управления.
 * Значения по умолчанию повторяют прежнее поведение приложения. Не зависит от Android: проверяется юнит-тестами.
 */
object KeyMap {
    /** Префикс ключа в настройках: «km_<код кнопки>» -> id действия. */
    const val PREF = "km_"

    /** Префикс ключа для долгого нажатия: «kl_<код кнопки>» -> id действия. */
    const val LONG_PREF = "kl_"

    class Btn(val code: Int, val name: String, val def: KeyAction)

    val buttons: List<Btn> = listOf(
        Btn(KeyEvent.KEYCODE_DPAD_UP, "Джойстик ↑", KeyAction.PREV),
        Btn(KeyEvent.KEYCODE_DPAD_DOWN, "Джойстик ↓", KeyAction.NEXT),
        Btn(KeyEvent.KEYCODE_DPAD_LEFT, "Джойстик ←", KeyAction.ARCH_BACK),
        Btn(KeyEvent.KEYCODE_DPAD_RIGHT, "Джойстик →", KeyAction.ARCH_FWD),
        Btn(KeyEvent.KEYCODE_PROG_RED, "Красная", KeyAction.FAV),
        Btn(KeyEvent.KEYCODE_PROG_GREEN, "Зелёная", KeyAction.NONE),
        Btn(KeyEvent.KEYCODE_PROG_YELLOW, "Жёлтая", KeyAction.ASPECT),
        Btn(KeyEvent.KEYCODE_PROG_BLUE, "Синяя", KeyAction.SOUND),
        Btn(KeyEvent.KEYCODE_MENU, "Меню", KeyAction.SETTINGS),
        Btn(KeyEvent.KEYCODE_INFO, "Info", KeyAction.INFO),
        Btn(KeyEvent.KEYCODE_SEARCH, "Поиск", KeyAction.SEARCH),
        Btn(KeyEvent.KEYCODE_CAPTIONS, "Субтитры", KeyAction.SUBS),
        Btn(KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK, "Аудио", KeyAction.AUDIO),
        Btn(KeyEvent.KEYCODE_STAR, "«*»", KeyAction.FAV),
        Btn(KeyEvent.KEYCODE_CHANNEL_UP, "CH+", KeyAction.PREV),
        Btn(KeyEvent.KEYCODE_CHANNEL_DOWN, "CH−", KeyAction.NEXT),
        Btn(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "Пауза / воспроизведение", KeyAction.PLAYPAUSE),
        Btn(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, "Перемотка вперёд", KeyAction.FWD),
        Btn(KeyEvent.KEYCODE_MEDIA_REWIND, "Перемотка назад", KeyAction.BACK30)
    )

    /** Кнопки, у которых можно назначить действие на долгое нажатие (≈0,5 с) при просмотре эфира. OK здесь тоже есть: короткое OK остаётся прежним. */
    val longButtons: List<Btn> = listOf(
        Btn(KeyEvent.KEYCODE_DPAD_CENTER, "Долгое нажатие: OK", KeyAction.NONE),
        Btn(KeyEvent.KEYCODE_DPAD_UP, "Долгое нажатие: вверх", KeyAction.NONE),
        Btn(KeyEvent.KEYCODE_DPAD_DOWN, "Долгое нажатие: вниз", KeyAction.NONE),
        Btn(KeyEvent.KEYCODE_DPAD_RIGHT, "Долгое нажатие: вправо", KeyAction.NONE),
        Btn(KeyEvent.KEYCODE_DPAD_LEFT, "Долгое нажатие: влево", KeyAction.REWIND)
    )

    /** Приводит ENTER / NUMPAD_ENTER к OK; остальные коды не меняются. */
    fun normLong(code: Int): Int =
        if (code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER) KeyEvent.KEYCODE_DPAD_CENTER else code

    /** Описание «долгой» кнопки по коду; null — для этой кнопки долгое нажатие не настраивается. */
    fun findLong(code: Int): Btn? = longButtons.firstOrNull { it.code == normLong(code) }

    /** Действие долгого нажатия: сохранённое [stored] (id) или действие по умолчанию. */
    fun resolveLong(code: Int, stored: String?): KeyAction =
        KeyAction.byId(stored) ?: findLong(code)?.def ?: KeyAction.NONE

    /** Описание кнопки по коду; null — кнопка не переназначается. */
    fun find(code: Int): Btn? = buttons.firstOrNull { it.code == code }

    /** Действие кнопки: сохранённое [stored] (id) или, если его нет или оно неизвестно, действие по умолчанию. */
    fun resolve(code: Int, stored: String?): KeyAction =
        KeyAction.byId(stored) ?: find(code)?.def ?: KeyAction.NONE

    /** Следующее ([dir] > 0) или предыдущее действие по кругу. */
    fun next(cur: KeyAction, dir: Int): KeyAction {
        val all = KeyAction.values()
        val i = all.indexOf(cur)
        return all[((i + (if (dir < 0) -1 else 1)) % all.size + all.size) % all.size]
    }
}
