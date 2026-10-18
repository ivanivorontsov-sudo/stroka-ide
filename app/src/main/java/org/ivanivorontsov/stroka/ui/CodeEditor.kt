package org.ivanivorontsov.stroka.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import org.ivanivorontsov.stroka.R
import org.ivanivorontsov.stroka.core.EditorLogic
import org.ivanivorontsov.stroka.core.Highlighter
import org.ivanivorontsov.stroka.core.Lang
import org.ivanivorontsov.stroka.core.LineIndex
import org.ivanivorontsov.stroka.core.Token
import org.ivanivorontsov.stroka.core.TokenType
import org.ivanivorontsov.stroka.core.UndoStack
import java.util.concurrent.Executors
import kotlin.math.abs

class CodeEditor @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : AppCompatEditText(context, attrs) {

    class SyntaxSpan(color: Int) : ForegroundColorSpan(color)
    class SearchSpan(color: Int) : BackgroundColorSpan(color)

    interface Listener {
        fun onEdited()
        fun onCursorMoved()
        fun onFontSizeChanged(sp: Float)
    }

    var listener: Listener? = null
    var lang: Lang = Lang.PLAIN
        set(value) { field = value; scheduleHighlight(0) }
    var indentUnit = "    "
    var autoPair = true
    var autoIndent = true
    var showLineNumbers = true
        set(value) { field = value; updateGutter(true) }
    var undoStack = UndoStack()
    var selectMode = false

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var generation = 0
    private var tokens: List<Token> = emptyList()
    private var spanFrom = -1
    private var spanTo = -1
    private val lineIndex = LineIndex()
    private var internal = 0
    private var applyingHistory = false
    private var pendingRemoved = ""
    private var gutterWidth = 0
    private var gutterDigits = 0
    private val gutterPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gutterBg = Paint()
    private val linePaint = Paint()
    private val colors = HashMap<TokenType, Int>()
    private var searchColor = 0
    private val density = resources.displayMetrics.density
    private var scaling = false
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean { scaling = true; return true }
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val sp = textSize / resources.displayMetrics.scaledDensity
            val next = (sp * detector.scaleFactor).coerceIn(8f, 36f)
            if (abs(next - sp) > 0.05f) setTextSize(TypedValue.COMPLEX_UNIT_SP, next)
            return true
        }
        override fun onScaleEnd(detector: ScaleGestureDetector) {
            listener?.onFontSizeChanged(textSize / resources.displayMetrics.scaledDensity)
            main.postDelayed({ scaling = false }, 120)
        }
    })

    private val highlightTask = Runnable { runHighlight() }
    private val spansTask = Runnable { applySpans(force = true) }

    init {
        typeface = Typeface.MONOSPACE
        gravity = Gravity.TOP or Gravity.START
        background = null
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
        isVerticalScrollBarEnabled = true
        isHorizontalScrollBarEnabled = true
        setHorizontallyScrolling(false)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setLineSpacing(0f, 1.12f)
        includeFontPadding = false
        overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
        reloadColors()
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                pendingRemoved = if (count > 0) s.subSequence(start, start + count).toString() else ""
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                val inserted = if (count > 0) s.subSequence(start, start + count).toString() else ""
                if (!applyingHistory) undoStack.record(start, pendingRemoved, inserted, System.currentTimeMillis())
                if (internal == 0 && !applyingHistory) smartEdit(start, pendingRemoved, inserted)
            }

            override fun afterTextChanged(s: Editable) {
                lineIndex.rebuild(s)
                updateGutter(false)
                if (pendingSmart != null) {
                    val e = pendingSmart
                    pendingSmart = null
                    if (e != null) applyEdit(e, record = true)
                }
                scheduleHighlight(110)
                listener?.onEdited()
            }
        })
    }

    private var pendingSmart: org.ivanivorontsov.stroka.core.Edit? = null

    private fun smartEdit(start: Int, removed: String, inserted: String) {
        val t = text ?: return
        if (inserted.isNotEmpty() && (removed.isEmpty() || inserted.startsWith(removed)) && inserted.length == removed.length + 1) {
            val pos = start + inserted.length - 1
            val ch = inserted.last()
            pendingSmart = if (ch == '\n') {
                if (autoIndent) EditorLogic.afterNewline(t, pos, lang, indentUnit) else null
            } else if (ch in "()[]{}\"'`") {
                EditorLogic.afterTyped(t, pos, lang, indentUnit, autoPair)
            } else null
        } else if (inserted.isEmpty() && removed.length == 1 && autoPair) {
            pendingSmart = EditorLogic.afterDeleted(t, start, removed[0])
        }
    }

    fun applyEdit(e: org.ivanivorontsov.stroka.core.Edit, record: Boolean = true) {
        val t = text ?: return
        if (e.start < 0 || e.end > t.length || e.start > e.end) return
        internal++
        try {
            if (!record) applyingHistory = true
            t.replace(e.start, e.end, e.text)
        } finally {
            applyingHistory = false
            internal--
        }
        val len = text?.length ?: 0
        setSelection(e.cursor.coerceIn(0, len), e.selEnd.coerceIn(0, len))
    }

    fun reloadColors() {
        val c = context
        fun col(id: Int) = ContextCompat.getColor(c, id)
        colors[TokenType.KEYWORD] = col(R.color.syn_keyword)
        colors[TokenType.TYPE] = col(R.color.syn_type)
        colors[TokenType.BUILTIN] = col(R.color.syn_builtin)
        colors[TokenType.CONSTANT] = col(R.color.syn_constant)
        colors[TokenType.STRING] = col(R.color.syn_string)
        colors[TokenType.NUMBER] = col(R.color.syn_number)
        colors[TokenType.COMMENT] = col(R.color.syn_comment)
        colors[TokenType.FUNCTION] = col(R.color.syn_function)
        colors[TokenType.TAG] = col(R.color.syn_tag)
        colors[TokenType.ATTR] = col(R.color.syn_attr)
        colors[TokenType.HEADING] = col(R.color.syn_heading)
        colors[TokenType.META] = col(R.color.syn_meta)
        colors[TokenType.LABEL] = col(R.color.syn_label)
        colors[TokenType.EMPHASIS] = col(R.color.syn_emphasis)
        colors[TokenType.LINK] = col(R.color.syn_link)
        searchColor = col(R.color.search_hit)
        setTextColor(col(R.color.editor_fg))
        setBackgroundColor(col(R.color.editor_bg))
        highlightColor = col(R.color.selection)
        gutterPaint.color = col(R.color.gutter_fg)
        gutterPaint.typeface = Typeface.MONOSPACE
        gutterBg.color = col(R.color.gutter_bg)
        linePaint.color = col(R.color.current_line)
        spanFrom = -1
        applySpans(force = true)
        invalidate()
    }

    fun setDocument(content: String, stack: UndoStack, language: Lang, selStart: Int, selEnd: Int, scroll: Int) {
        undoStack = stack
        internal++
        applyingHistory = true
        try {
            setText(content)
        } finally {
            applyingHistory = false
            internal--
        }
        tokens = emptyList()
        spanFrom = -1
        lang = language
        val len = content.length
        setSelection(selStart.coerceIn(0, len), selEnd.coerceIn(0, len))
        post { scrollTo(0, scroll.coerceAtLeast(0)) }
        runHighlightNow()
    }

    fun setWrap(wrap: Boolean) {
        setHorizontallyScrolling(!wrap)
        spanFrom = -1
        scheduleHighlight(0)
    }

    private fun scheduleHighlight(delay: Long) {
        main.removeCallbacks(highlightTask)
        main.postDelayed(highlightTask, delay)
    }

    private fun runHighlight() {
        val snapshot = text?.toString() ?: return
        val l = lang
        val gen = ++generation
        worker.execute {
            val toks = try { Highlighter.tokenize(snapshot, l) } catch (e: Throwable) { emptyList() }
            main.post {
                if (gen == generation && text?.length == snapshot.length) {
                    tokens = toks
                    applySpans(force = true)
                }
            }
        }
    }

    private fun runHighlightNow() {
        val snapshot = text?.toString() ?: return
        if (snapshot.length < 200_000) {
            generation++
            tokens = try { Highlighter.tokenize(snapshot, lang) } catch (e: Throwable) { emptyList() }
            applySpans(force = true)
        } else scheduleHighlight(0)
    }

    private fun visibleRange(): IntRange {
        val l = layout ?: return 0 until 0
        val t = text ?: return 0 until 0
        val top = (scrollY - totalPaddingTop).coerceAtLeast(0)
        val h = height.coerceAtLeast(1)
        val firstLine = l.getLineForVertical((top - h).coerceAtLeast(0))
        val lastLine = l.getLineForVertical(top + 2 * h)
        val a = l.getLineStart(firstLine).coerceIn(0, t.length)
        val b = l.getLineEnd(lastLine).coerceIn(0, t.length)
        return a until b
    }

    private fun applySpans(force: Boolean) {
        val t = text ?: return
        val range = visibleRange()
        if (range.isEmpty() && t.isNotEmpty() && layout == null) return
        if (!force && range.first >= spanFrom && range.last <= spanTo) return
        val a = range.first
        val b = range.last + 1
        for (s in t.getSpans(0, t.length, SyntaxSpan::class.java)) t.removeSpan(s)
        val toks = tokens
        var lo = 0
        var hi = toks.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (toks[mid].end <= a) lo = mid + 1 else hi = mid
        }
        var i = lo
        while (i < toks.size) {
            val tk = toks[i]
            if (tk.start >= b) break
            val s = maxOf(tk.start, a)
            val e = minOf(tk.end, b, t.length)
            if (s < e) t.setSpan(SyntaxSpan(colors[tk.type] ?: currentTextColor), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            i++
        }
        spanFrom = a
        spanTo = b
    }

    override fun onScrollChanged(horiz: Int, vert: Int, oldHoriz: Int, oldVert: Int) {
        super.onScrollChanged(horiz, vert, oldHoriz, oldVert)
        val r = visibleRange()
        if (r.first < spanFrom || r.last > spanTo) {
            main.removeCallbacks(spansTask)
            main.postDelayed(spansTask, 40)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        spanFrom = -1
        main.post { applySpans(force = true) }
    }

    fun setSearchHighlights(ranges: List<IntRange>) {
        val t = text ?: return
        for (s in t.getSpans(0, t.length, SearchSpan::class.java)) t.removeSpan(s)
        for (r in ranges.take(3000)) {
            if (r.last < t.length) t.setSpan(SearchSpan(searchColor), r.first, r.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun updateGutter(force: Boolean) {
        val digits = if (showLineNumbers) maxOf(2, lineIndex.count.toString().length) else 0
        if (!force && digits == gutterDigits) return
        gutterDigits = digits
        gutterPaint.textSize = textSize * 0.82f
        gutterWidth = if (showLineNumbers) (gutterPaint.measureText("0") * digits + 14 * density).toInt() else 0
        setPadding(gutterWidth + (8 * density).toInt(), (6 * density).toInt(), (12 * density).toInt(), (48 * density).toInt())
    }

    override fun setTextSize(unit: Int, size: Float) {
        super.setTextSize(unit, size)
        updateGutter(true)
    }

    override fun onDraw(canvas: Canvas) {
        val l = layout
        if (l != null) {
            val sel = selectionStart
            if (sel >= 0 && sel == selectionEnd) {
                val line = l.getLineForOffset(sel)
                val top = l.getLineTop(line) + totalPaddingTop
                val bottom = l.getLineBottom(line) + totalPaddingTop
                canvas.drawRect(scrollX.toFloat(), top.toFloat(), (scrollX + width).toFloat(), bottom.toFloat(), linePaint)
            }
        }
        super.onDraw(canvas)
        if (!showLineNumbers || l == null) return
        val t = text ?: return
        val left = scrollX.toFloat()
        canvas.drawRect(left, scrollY.toFloat(), left + gutterWidth, (scrollY + height).toFloat(), gutterBg)
        val first = l.getLineForVertical((scrollY - totalPaddingTop).coerceAtLeast(0))
        val last = l.getLineForVertical(scrollY + height - totalPaddingTop)
        val right = left + gutterWidth - 6 * density
        gutterPaint.textAlign = Paint.Align.RIGHT
        val curLine = if (selectionStart >= 0) lineIndex.lineOf(selectionStart) else -1
        val baseColor = gutterPaint.color
        for (i in first..last) {
            val start = l.getLineStart(i)
            if (start != 0 && (start > t.length || t[start - 1] != '\n')) continue
            val n = lineIndex.lineOf(start)
            val y = (l.getLineBaseline(i) + totalPaddingTop).toFloat()
            if (n == curLine) gutterPaint.color = currentTextColor
            canvas.drawText((n + 1).toString(), right, y, gutterPaint)
            gutterPaint.color = baseColor
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (event.pointerCount > 1 || scaling) {
            if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                val cancel = MotionEvent.obtain(event)
                cancel.action = MotionEvent.ACTION_CANCEL
                super.onTouchEvent(cancel)
                cancel.recycle()
            }
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        listener?.onCursorMoved()
        invalidate()
    }

    private fun clearComposing() {
        text?.let { BaseInputConnection.removeComposingSpans(it) }
    }

    fun undo() {
        clearComposing()
        val r = undoStack.undo() ?: return
        applyHistory(r)
    }

    fun redo() {
        clearComposing()
        val r = undoStack.redo() ?: return
        applyHistory(r)
    }

    private fun applyHistory(r: UndoStack.Result) {
        val t = text ?: return
        applyingHistory = true
        internal++
        try {
            for (op in r.ops) {
                if (op.start <= t.length && op.end <= t.length && op.start <= op.end) t.replace(op.start, op.end, op.text)
            }
        } finally {
            internal--
            applyingHistory = false
        }
        val c = r.cursor.coerceIn(0, t.length)
        setSelection(c)
        bringPointIntoView(c)
    }

    fun insertText(s: String) {
        val t = text ?: return
        clearComposing()
        val a = minOf(selectionStart, selectionEnd).coerceAtLeast(0)
        val b = maxOf(selectionStart, selectionEnd).coerceAtLeast(0)
        if (s.length > 1) undoStack.breakGroup()
        t.replace(a, b, s)
        if (s.length > 1) undoStack.breakGroup()
    }

    fun insertIndent() {
        val a = selectionStart
        val b = selectionEnd
        val t = text ?: return
        if (a != b && t.subSequence(minOf(a, b), maxOf(a, b)).contains('\n')) {
            applyEdit(EditorLogic.indentLines(t, a, b, indentUnit, false))
        } else insertText(indentUnit)
    }

    fun outdent() {
        val t = text ?: return
        applyEdit(EditorLogic.indentLines(t, selectionStart, selectionEnd, indentUnit, true))
    }

    fun toggleComment() {
        val t = text ?: return
        EditorLogic.toggleComment(t, selectionStart, selectionEnd, lang)?.let { applyEdit(it) }
    }

    private fun moveTo(pos: Int) {
        val len = text?.length ?: 0
        val p = pos.coerceIn(0, len)
        if (selectMode) setSelection(selectionStart.coerceIn(0, len), p) else setSelection(p)
        bringPointIntoView(p)
    }

    fun moveChars(n: Int) {
        val cur = if (selectMode) selectionEnd else if (n < 0) minOf(selectionStart, selectionEnd) else maxOf(selectionStart, selectionEnd)
        if (!selectMode && selectionStart != selectionEnd) { moveTo(cur); return }
        moveTo(cur + n)
    }

    fun moveLines(n: Int) {
        val l = layout ?: return
        val cur = selectionEnd.coerceAtLeast(0)
        val line = l.getLineForOffset(cur)
        val x = l.getPrimaryHorizontal(cur)
        val target = (line + n).coerceIn(0, l.lineCount - 1)
        moveTo(l.getOffsetForHorizontal(target, x))
    }

    fun moveHome() {
        val t = text ?: return
        val cur = selectionEnd
        val ls = EditorLogic.lineStart(t, cur)
        val firstNon = ls + EditorLogic.leadingWhitespace(t, ls, t.length).length
        moveTo(if (cur == firstNon) ls else firstNon)
    }

    fun moveEnd() {
        val t = text ?: return
        moveTo(EditorLogic.lineEnd(t, selectionEnd))
    }

    fun goToLine(line: Int) {
        val t = text ?: return
        val off = EditorLogic.offsetOfLine(t, line - 1)
        setSelection(off.coerceIn(0, t.length))
        requestFocus()
        post { bringPointIntoView(off.coerceIn(0, t.length)); centerCursor() }
    }

    fun centerCursor() {
        val l = layout ?: return
        val line = l.getLineForOffset(selectionStart.coerceAtLeast(0))
        val y = l.getLineTop(line) - height / 3
        val maxY = (l.height + totalPaddingTop + totalPaddingBottom - height).coerceAtLeast(0)
        scrollTo(scrollX, y.coerceIn(0, maxY))
    }

    fun backspace() {
        val t = text ?: return
        val a = minOf(selectionStart, selectionEnd)
        val b = maxOf(selectionStart, selectionEnd)
        if (a != b) t.delete(a, b) else if (a > 0) t.delete(a - 1, a)
    }

    fun deleteWord() {
        val t = text ?: return
        val cur = selectionStart
        val s = EditorLogic.previousWordStart(t, cur)
        if (s < cur) t.delete(s, cur)
    }

    fun deleteLine() {
        val t = text ?: return
        val ls = EditorLogic.lineStart(t, selectionStart)
        var le = EditorLogic.lineEnd(t, selectionEnd)
        if (le < t.length) le++
        undoStack.breakGroup()
        t.delete(ls, le)
        undoStack.breakGroup()
    }

    fun currentLineCol(): Pair<Int, Int> {
        val t = text ?: return 1 to 1
        val p = selectionStart.coerceIn(0, t.length)
        val line = lineIndex.lineOf(p)
        return (line + 1) to (p - lineIndex.startOf(line) + 1)
    }

    val lineCount2: Int get() = lineIndex.count

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        main.removeCallbacksAndMessages(null)
    }
}
