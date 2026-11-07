package app.iptv.tv

import java.util.Locale

/** Часовой пояс программы передач в минутах от UTC. Вынесено из MainActivity, чтобы можно было тестировать. */
object Tz {
    /** «3», «-3», «5.5», «5,5», «5:30» → минуты от UTC (в пределах ±14 ч). Пустая или непонятная строка — 0. */
    fun parse(s: String): Int {
        val t = s.trim().replace(',', '.')
        if (t.isEmpty()) return 0
        val neg = t.startsWith("-")
        val u = t.trimStart('+', '-')
        val m = if (u.contains(':')) {
            val parts = u.split(':')
            (parts[0].toIntOrNull() ?: 0) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0)
        } else Math.round((u.toDoubleOrNull() ?: 0.0) * 60).toInt()
        return (if (neg) -m else m).coerceIn(-14 * 60, 14 * 60)
    }

    /** Минуты → «3» (целые часы) или «5:30». */
    fun format(m: Int): String =
        if (m % 60 == 0) (m / 60).toString()
        else String.format(Locale.US, "%s%d:%02d", if (m < 0) "-" else "", Math.abs(m) / 60, Math.abs(m) % 60)
}
