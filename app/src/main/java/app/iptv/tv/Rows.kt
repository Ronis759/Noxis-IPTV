package app.iptv.tv

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/** Масштаб шрифта списков и всплывающих панелей (Настройки → «Размер шрифта»). 1.0 = 100 %. */
object UiScale { @Volatile var f = 1f }

/**
 * Строка списка. [num] — номер канала слева (0 — не показывать), [prog] — прогресс текущей передачи 0..1 (меньше нуля — без полосы),
 * [mark] — значки справа («▶» играет, «★» избранное, «⇅» переносится).
 */
class Row(
    val title: String, val sub: String = "", val tag: Any? = null, val dim: Boolean = false, val icon: String = "", val short: String = "",
    val num: Int = 0, val prog: Float = -1f, val mark: String = ""
)

class RowAdapter(private val ctx: Context) : BaseAdapter() {
    private var rows: List<Row> = emptyList()
    var icons = false
    var compact = false
        set(v) { if (field != v) { field = v; notifyDataSetChanged() } }
    var active = ""
    fun set(r: List<Row>) { rows = r; notifyDataSetChanged() }
    fun row(i: Int): Row = rows[i]
    override fun getCount() = rows.size
    override fun getItem(i: Int): Any = rows[i]
    override fun getItemId(i: Int) = i.toLong()
    override fun getView(i: Int, cv: View?, parent: ViewGroup?): View {
        val d = ctx.resources.displayMetrics.density
        fun px(x: Int) = (x * d).toInt()
        val box = (cv as? LinearLayout) ?: LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(12), px(8), px(12), px(8))
            // 0: номер канала
            addView(TextView(ctx).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL; maxLines = 1; minWidth = px(40); setTextColor(Theme.gray) },
                LinearLayout.LayoutParams(-2, -2).apply { rightMargin = px(10) })
            // 1: логотип
            addView(ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER }, LinearLayout.LayoutParams(px(60), px(40)))
            // 2: название, передача, полоса прогресса
            val lp = LinearLayout.LayoutParams(0, -2, 1f)
            lp.leftMargin = px(12)
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(ctx).apply { textSize = 20f; maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
                addView(TextView(ctx).apply { textSize = 15f; maxLines = 1; ellipsize = TextUtils.TruncateAt.END; setTextColor(Theme.gray) })
                addView(ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = 1000
                    progressTintList = ColorStateList.valueOf(Theme.accent)
                    progressBackgroundTintList = ColorStateList.valueOf(Theme.stroke)
                }, LinearLayout.LayoutParams(-1, px(3)).apply { topMargin = px(5) })
            }, lp)
            // 3: значки справа
            addView(TextView(ctx).apply { maxLines = 1; setTextColor(Theme.accent) }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = px(10) })
        }
        val r = rows[i]
        val comp = compact
        (box.getChildAt(0) as TextView).apply {
            visibility = if (r.num > 0 && !comp) View.VISIBLE else View.GONE
            text = r.num.toString(); textSize = 16f * UiScale.f
        }
        val img = box.getChildAt(1) as ImageView
        img.visibility = if (icons) View.VISIBLE else View.GONE
        Logos.load(img, r.icon)
        val tx = box.getChildAt(2) as LinearLayout
        box.gravity = Gravity.CENTER_VERTICAL
        (tx.getChildAt(0) as TextView).apply {
            text = if (comp) r.short.ifEmpty { r.title.take(1).uppercase() } else r.title
            textSize = if (comp) 24f else 20f * UiScale.f
            gravity = if (comp) Gravity.CENTER_HORIZONTAL else Gravity.START
            setTextColor(if (r.title == active && active.isNotEmpty()) Theme.accent else if (r.dim) Theme.gray else Color.WHITE)
        }
        (tx.getChildAt(1) as TextView).apply { textSize = 15f * UiScale.f; text = r.sub; visibility = if (r.sub.isEmpty() || comp) View.GONE else View.VISIBLE }
        (tx.getChildAt(2) as ProgressBar).apply {
            visibility = if (r.prog >= 0f && !comp) View.VISIBLE else View.GONE
            progress = (r.prog.coerceIn(0f, 1f) * 1000).toInt()
        }
        (box.getChildAt(3) as TextView).apply {
            visibility = if (r.mark.isEmpty() || comp) View.GONE else View.VISIBLE
            text = r.mark; textSize = 18f * UiScale.f
        }
        return box
    }
}
