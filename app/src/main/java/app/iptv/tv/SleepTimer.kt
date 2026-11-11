package app.iptv.tv

import android.os.Handler
import android.os.Looper

/**
 * Таймер сна: автоматическое выключение воспроизведения через заданное время.
 * Перед выключением плавно снижает громкость и показывает предупреждение.
 */
class SleepTimer(private val onWarning: (Long) -> Unit, private val onExpire: () -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var mainRunnable: Runnable? = null
    private var warningRunnable: Runnable? = null
    private var tickRunnable: Runnable? = null
    private var expiresAt: Long = 0
    private var isActive = false
    private var onTick: ((Long) -> Unit)? = null

    companion object {
        /** Доступные интервалы в минутах. */
        val INTERVALS = listOf(15, 30, 45, 60, 90, 120)

        /** Форматирование времени для UI: "15 мин", "1 ч 30 мин". */
        fun formatDuration(minutes: Int): String {
            val hours = minutes / 60
            val mins = minutes % 60
            return when {
                hours == 0 -> "$mins мин"
                mins == 0 -> "$hours ч"
                else -> "$hours ч $mins мин"
            }
        }

        /** Форматирование оставшегося времени в секундах: "5:23". */
        fun formatRemaining(seconds: Long): String {
            val m = seconds / 60
            val s = seconds % 60
            return String.format("%d:%02d", m, s)
        }
    }

    /**
     * Запустить таймер на [minutes] минут.
     * За 60 секунд до окончания вызывается [onWarning] с оставшимся временем в секундах.
     * По истечении времени вызывается [onExpire].
     *
     * @param tickCallback вызывается каждую секунду с оставшимся временем в секундах (для UI)
     */
    fun start(minutes: Int, tickCallback: ((Long) -> Unit)? = null) {
        cancel()

        val millis = minutes * 60L * 1000L
        expiresAt = System.currentTimeMillis() + millis
        isActive = true
        onTick = tickCallback

        // Предупреждение за 60 секунд
        val warningDelay = if (minutes > 1) millis - 60_000L else 0L
        if (warningDelay > 0) {
            warningRunnable = Runnable {
                if (isActive) {
                    val remaining = (expiresAt - System.currentTimeMillis()) / 1000
                    onWarning(remaining)
                }
            }
            handler.postDelayed(warningRunnable!!, warningDelay)
        }

        // Основной таймер
        mainRunnable = Runnable {
            if (isActive) {
                isActive = false
                onExpire()
            }
        }
        handler.postDelayed(mainRunnable!!, millis)

        // Тик каждую секунду для обновления UI
        if (tickCallback != null) {
            tickRunnable = object : Runnable {
                override fun run() {
                    if (isActive && onTick != null) {
                        val remaining = (expiresAt - System.currentTimeMillis()) / 1000
                        if (remaining > 0) {
                            onTick?.invoke(remaining)
                            handler.postDelayed(this, 1000)
                        }
                    }
                }
            }
            handler.post(tickRunnable!!)
        }
    }

    /** Отменить таймер. */
    fun cancel() {
        isActive = false
        mainRunnable?.let { handler.removeCallbacks(it) }
        warningRunnable?.let { handler.removeCallbacks(it) }
        tickRunnable?.let { handler.removeCallbacks(it) }
        mainRunnable = null
        warningRunnable = null
        tickRunnable = null
        expiresAt = 0
        onTick = null
    }

    /** Таймер активен? */
    fun isActive() = isActive

    /** Оставшееся время в секундах. */
    fun remaining(): Long = if (isActive) {
        ((expiresAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
    } else 0
}
