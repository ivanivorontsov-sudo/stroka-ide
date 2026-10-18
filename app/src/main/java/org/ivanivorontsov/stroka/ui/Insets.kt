package org.ivanivorontsov.stroka.ui

import android.app.Activity
import android.view.Display
import android.view.View
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

object Insets {
    fun applyTopBar(bar: View) {
        val base = bar.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(bar) { v, insets ->
            val i = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.updatePadding(top = base + i.top, left = i.left, right = i.right)
            insets
        }
    }

    fun preferHighRefresh(activity: Activity, enable: Boolean) {
        val w = activity.window
        val display: Display = (if (android.os.Build.VERSION.SDK_INT >= 30) activity.display else null)
            ?: @Suppress("DEPRECATION") activity.windowManager.defaultDisplay
        val lp: WindowManager.LayoutParams = w.attributes
        if (!enable) {
            lp.preferredDisplayModeId = 0
        } else {
            val cur = display.mode
            val best = display.supportedModes
                .filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
                .maxByOrNull { it.refreshRate }
            if (best != null) lp.preferredDisplayModeId = best.modeId
        }
        w.attributes = lp
    }
}
