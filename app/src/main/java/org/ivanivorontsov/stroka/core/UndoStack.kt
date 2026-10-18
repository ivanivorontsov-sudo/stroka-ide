package org.ivanivorontsov.stroka.core

class UndoStack(private val limit: Int = 1000, private val mergeWindowMs: Long = 1200) {
    data class Change(val start: Int, val removed: String, val inserted: String)
    data class Op(val start: Int, val end: Int, val text: String)
    data class Result(val ops: List<Op>, val cursor: Int)

    private class Group(val changes: MutableList<Change>, var time: Long, var closed: Boolean = false)

    private val undo = ArrayDeque<Group>()
    private val redo = ArrayDeque<Group>()

    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()

    fun clear() {
        undo.clear(); redo.clear()
    }

    fun breakGroup() {
        undo.lastOrNull()?.closed = true
    }

    fun record(start: Int, removed: String, inserted: String, time: Long) {
        if (removed.isEmpty() && inserted.isEmpty()) return
        redo.clear()
        val c = Change(start, removed, inserted)
        val g = undo.lastOrNull()
        if (g != null && !g.closed && time - g.time <= mergeWindowMs && canMerge(g.changes.last(), c)) {
            g.changes.add(c)
            g.time = time
        } else {
            undo.addLast(Group(mutableListOf(c), time))
            while (undo.size > limit) undo.removeFirst()
        }
        if (inserted.contains('\n') && inserted.length > 1 && removed.isEmpty()) breakGroup()
    }

    private fun canMerge(last: Change, c: Change): Boolean {
        val lastEnd = last.start + last.inserted.length
        if (c.inserted.length > 1 && c.removed.isEmpty() && !c.inserted.isBlank()) {
            return c.start == lastEnd && last.inserted.isNotEmpty() && !last.inserted.endsWith("\n") && c.inserted.trim().none { it.isWhitespace() }
        }
        if (c.removed.isEmpty()) {
            if (c.start != lastEnd) return false
            if (last.inserted.isEmpty() && last.removed.isNotEmpty()) return false
            val ch = c.inserted.firstOrNull() ?: return true
            val prev = last.inserted.lastOrNull()
            if (ch.isWhitespace() && prev != null && !prev.isWhitespace() && ch != '\n') return true
            return !(prev != null && prev.isWhitespace() && !ch.isWhitespace())
        }
        if (c.inserted.isEmpty()) {
            if (last.inserted.isNotEmpty() && last.removed.isEmpty()) return false
            return c.start + c.removed.length == last.start || c.start == last.start
        }
        return c.start >= last.start && c.start <= lastEnd
    }

    fun undo(): Result? {
        val g = undo.removeLastOrNull() ?: return null
        redo.addLast(g)
        val ops = g.changes.asReversed().map { Op(it.start, it.start + it.inserted.length, it.removed) }
        val first = g.changes.first()
        return Result(ops, first.start + first.removed.length)
    }

    fun redo(): Result? {
        val g = redo.removeLastOrNull() ?: return null
        g.closed = true
        undo.addLast(g)
        val ops = g.changes.map { Op(it.start, it.start + it.removed.length, it.inserted) }
        val last = g.changes.last()
        return Result(ops, last.start + last.inserted.length)
    }
}
