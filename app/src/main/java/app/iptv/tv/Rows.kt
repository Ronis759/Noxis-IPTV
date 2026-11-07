package app.iptv.tv

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class Row(val title: String, val sub: String = "", val tag: Any? = null, val dim: Boolean = false, val icon: String = "", val short: String = "")

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
            addView(ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER }, LinearLayout.LayoutParams(px(60), px(40)))
            val lp = LinearLayout.LayoutParams(0, -2, 1f)
            lp.leftMargin = px(12)
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(ctx).apply { textSize = 20f; maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
                addView(TextView(ctx).apply { textSize = 15f; maxLines = 1; setTextColor(0xFF8A9AA5.toInt()) })
            }, lp)
        }
        val r = rows[i]
        val img = box.getChildAt(0) as ImageView
        img.visibility = if (icons) View.VISIBLE else View.GONE
        Logos.load(img, r.icon)
        val tx = box.getChildAt(1) as LinearLayout
        val comp = compact
        box.gravity = Gravity.CENTER_VERTICAL
        (tx.getChildAt(0) as TextView).apply {
            text = if (comp) r.short.ifEmpty { r.title.take(1).uppercase() } else r.title
            textSize = if (comp) 24f else 20f
            gravity = if (comp) Gravity.CENTER_HORIZONTAL else Gravity.START
            setTextColor(if (r.title == active && active.isNotEmpty()) 0xFF6FE3C1.toInt() else if (r.dim) 0xFF8A9AA5.toInt() else Color.WHITE)
        }
        (tx.getChildAt(1) as TextView).apply { text = r.sub; visibility = if (r.sub.isEmpty() || comp) View.GONE else View.VISIBLE }
        return box
    }
}
