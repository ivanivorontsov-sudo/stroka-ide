package org.ivanivorontsov.stroka.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import org.ivanivorontsov.stroka.R
import org.ivanivorontsov.stroka.core.ExtraKey
import org.ivanivorontsov.stroka.core.ExtraKeys
import org.ivanivorontsov.stroka.core.KeyAction
import kotlin.math.abs

class ExtraKeysView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {

    interface Listener {
        fun onKey(action: KeyAction)
        fun onCursorDrag(dx: Int, dy: Int)
    }

    var listener: Listener? = null
    var haptics = true
    private val density = resources.displayMetrics.density
    private val padRow = PadRow(context)
    private val symbolsScroll = HorizontalScrollView(context)
    private val symbols = LinearLayout(context)
    private var selectKey: TextView? = null
    private var micKey: TextView? = null

    init {
        orientation = VERTICAL
        setPadding(dp(2), dp(2), dp(2), dp(2))
        addView(padRow, LayoutParams(LayoutParams.MATCH_PARENT, dp(46)))
        symbolsScroll.isHorizontalScrollBarEnabled = false
        symbols.orientation = HORIZONTAL
        symbolsScroll.addView(symbols, LayoutParams(LayoutParams.WRAP_CONTENT, dp(46)))
        addView(symbolsScroll, LayoutParams(LayoutParams.MATCH_PARENT, dp(46)))
        val fixed = listOf(
            ExtraKey("Tab", KeyAction.Tab), ExtraKey("Выд", KeyAction.Select), ExtraKey("⇤", KeyAction.Home),
            ExtraKey("←", KeyAction.Left), ExtraKey("↑", KeyAction.Up), ExtraKey("↓", KeyAction.Down),
            ExtraKey("→", KeyAction.Right), ExtraKey("⇥", KeyAction.End), ExtraKey("🎤", KeyAction.Voice)
        )
        for (k in fixed) {
            val v = makeKey(k, repeat = k.action in setOf(KeyAction.Left, KeyAction.Right, KeyAction.Up, KeyAction.Down))
            v.setPadding(dp(1), 0, dp(1), 0)
            padRow.addView(v, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
            if (k.action == KeyAction.Select) selectKey = v
            if (k.action == KeyAction.Voice) micKey = v
        }
        setKeys(ExtraKeys.DEFAULT)
    }

    fun setKeys(config: String) {
        symbols.removeAllViews()
        val keys = ExtraKeys.parse(config.ifBlank { ExtraKeys.DEFAULT })
        for (k in keys) {
            val v = makeKey(k, repeat = false)
            v.minWidth = dp(46)
            symbols.addView(v, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
        }
    }

    fun setSelectActive(active: Boolean) {
        selectKey?.isActivated = active
        selectKey?.alpha = if (active) 1f else 0.85f
        selectKey?.setTextColor(if (active) resolveColor(com.google.android.material.R.attr.colorPrimary) else resolveColor(com.google.android.material.R.attr.colorOnSurface))
    }

    fun setMicActive(active: Boolean) {
        micKey?.text = if (active) "🔴" else "🎤"
    }

    private fun resolveColor(attr: Int): Int {
        val tv = TypedValue()
        context.theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    private fun dp(v: Int) = (v * density).toInt()

    @SuppressLint("ClickableViewAccessibility")
    private fun makeKey(k: ExtraKey, repeat: Boolean): TextView {
        val v = TextView(context)
        v.text = k.label
        v.gravity = Gravity.CENTER
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (k.label.length > 2) 13f else 18f)
        v.setSingleLine(true)
        v.includeFontPadding = false
        v.typeface = android.graphics.Typeface.MONOSPACE
        v.setBackgroundResource(R.drawable.key_bg)
        v.setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
        v.setPadding(if (repeat || k.label.length > 2) dp(1) else dp(8), 0, if (repeat || k.label.length > 2) dp(1) else dp(8), 0)
        v.contentDescription = when (k.action) {
            KeyAction.Tab -> "Отступ"
            KeyAction.Select -> "Режим выделения"
            KeyAction.Home -> "В начало строки"
            KeyAction.End -> "В конец строки"
            KeyAction.Left -> "Влево"
            KeyAction.Right -> "Вправо"
            KeyAction.Up -> "Вверх"
            KeyAction.Down -> "Вниз"
            KeyAction.Voice -> "Голосовой ввод"
            KeyAction.Undo -> "Отменить"
            KeyAction.Redo -> "Повторить"
            else -> k.label
        }
        if (repeat) {
            val h = Handler(Looper.getMainLooper())
            var repeating: Runnable? = null
            v.setOnTouchListener { view, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        view.isPressed = true
                        fire(view, k.action)
                        val r = object : Runnable {
                            override fun run() {
                                fire(view, k.action, haptic = false)
                                h.postDelayed(this, 45)
                            }
                        }
                        repeating = r
                        h.postDelayed(r, 380)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        view.isPressed = false
                        repeating?.let { h.removeCallbacks(it) }
                        true
                    }
                    else -> true
                }
            }
        } else {
            v.setOnClickListener { fire(it, k.action) }
        }
        return v
    }

    private fun fire(v: View, a: KeyAction, haptic: Boolean = true) {
        if (haptic && haptics) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        listener?.onKey(a)
    }

    private inner class PadRow(context: Context) : LinearLayout(context) {
        private val slop = ViewConfiguration.get(context).scaledTouchSlop * 1.5f
        private var downX = 0f
        private var downY = 0f
        private var lastX = 0f
        private var lastY = 0f
        private var dragging = false
        private val stepX = 11 * density
        private val stepY = 22 * density

        init {
            orientation = HORIZONTAL
        }

        override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y; lastX = e.x; lastY = e.y; dragging = false }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging && (abs(e.x - downX) > slop || abs(e.y - downY) > slop * 1.4f)) {
                        dragging = true
                        lastX = e.x; lastY = e.y
                        for (i in 0 until childCount) getChildAt(i).isPressed = false
                        return true
                    }
                }
            }
            return dragging
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    val dx = ((e.x - lastX) / stepX).toInt()
                    val dy = ((e.y - lastY) / stepY).toInt()
                    if (dx != 0) { lastX += dx * stepX; listener?.onCursorDrag(dx, 0) }
                    if (dy != 0) { lastY += dy * stepY; listener?.onCursorDrag(0, dy) }
                    if ((dx != 0 || dy != 0) && haptics) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
            }
            return true
        }
    }
}
