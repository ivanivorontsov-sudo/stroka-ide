package org.ivanivorontsov.stroka.ui

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.ivanivorontsov.stroka.R
import org.ivanivorontsov.stroka.files.FNode
import java.util.concurrent.Executors

class FileTreeAdapter(
    private val onOpen: (FNode) -> Unit,
    private val onLongPress: (FNode, View, Boolean) -> Unit
) : RecyclerView.Adapter<FileTreeAdapter.VH>() {

    class Row(val node: FNode, val depth: Int, val isRoot: Boolean)

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val arrow: TextView = v.findViewById(R.id.arrow)
        val icon: ImageView = v.findViewById(R.id.icon)
        val name: TextView = v.findViewById(R.id.name)
    }

    private var roots: List<FNode> = emptyList()
    private val expanded = HashSet<String>()
    private val cache = HashMap<String, List<FNode>>()
    private var rows: List<Row> = emptyList()
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    var activeKey: String? = null
        set(value) { field = value; notifyDataSetChanged() }

    fun setRoots(r: List<FNode>, expandFirst: Boolean = true) {
        roots = r
        if (expandFirst) r.forEach { expanded.add(it.key) }
        refresh()
    }

    fun expandedKeys(): Set<String> = expanded

    fun restoreExpanded(keys: Collection<String>) {
        expanded.addAll(keys)
    }

    fun refresh() {
        val snapshotRoots = roots
        val exp = HashSet(expanded)
        io.execute {
            val newCache = HashMap<String, List<FNode>>()
            val out = ArrayList<Row>()
            fun walk(n: FNode, depth: Int, root: Boolean) {
                out.add(Row(n, depth, root))
                if (n.isDir && n.key in exp) {
                    val ch = try { n.children() } catch (e: Exception) { emptyList() }
                    newCache[n.key] = ch
                    for (c in ch) walk(c, depth + 1, false)
                }
            }
            for (r in snapshotRoots) walk(r, 0, true)
            main.post {
                cache.clear(); cache.putAll(newCache)
                rows = out
                notifyDataSetChanged()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_tree, parent, false))

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val row = rows[position]
        val n = row.node
        val d = h.itemView.resources.displayMetrics.density
        h.itemView.setPaddingRelative((row.depth * 16 * d).toInt() + (4 * d).toInt(), 0, (8 * d).toInt(), 0)
        h.arrow.text = if (n.isDir) (if (n.key in expanded) "▾" else "▸") else ""
        h.icon.setImageResource(if (n.isDir) R.drawable.ic_folder else R.drawable.ic_file)
        h.name.text = n.name
        h.name.setTypeface(null, if (row.isRoot) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        h.itemView.isActivated = n.key == activeKey
        h.itemView.alpha = 1f
        h.itemView.setBackgroundColor(if (n.key == activeKey) 0x223F5AA8 else 0)
        h.itemView.setOnClickListener {
            if (n.isDir) {
                if (!expanded.remove(n.key)) expanded.add(n.key)
                refresh()
            } else onOpen(n)
        }
        h.itemView.setOnLongClickListener { onLongPress(n, it, row.isRoot); true }
    }
}
