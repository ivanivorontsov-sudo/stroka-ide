package org.ivanivorontsov.stroka.core

class LineIndex {
    private var starts = IntArray(1)
    var count = 1
        private set

    fun rebuild(t: CharSequence) {
        var n = 1
        for (i in 0 until t.length) if (t[i] == '\n') n++
        if (starts.size < n) starts = IntArray(n + n / 4 + 16)
        starts[0] = 0
        var k = 1
        for (i in 0 until t.length) if (t[i] == '\n') starts[k++] = i + 1
        count = n
    }

    fun lineOf(offset: Int): Int {
        var lo = 0
        var hi = count - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (starts[mid] <= offset) lo = mid else hi = mid - 1
        }
        return lo
    }

    fun startOf(line: Int): Int = starts[line.coerceIn(0, count - 1)]
}
