package com.kothakeys.keyboard

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.roundToInt

internal data class KeyboardColors(
    val background: Int, val key: Int, val function: Int, val ink: Int, val secondary: Int,
    val accent: Int = 0xff006b58.toInt(), val onAccent: Int = 0xffffffff.toInt(),
) {
    companion object {
        fun from(dark: Boolean) = if (dark) KeyboardColors(
            0xff121816.toInt(), 0xff29312e.toInt(), 0xff354b42.toInt(),
            0xfff5f7f6.toInt(), 0xffa4b3ac.toInt(),
        ) else KeyboardColors(
            0xfff0f2ef.toInt(), 0xffffffff.toInt(), 0xffdce9e2.toInt(),
            0xff192923.toInt(), 0xff75847d.toInt(),
        )
    }
}

internal enum class KeyboardHeight(val label: String, val portrait: Int, val landscape: Int) {
    Compact("Compact", 202, 152),
    Standard("Standard", 226, 170),
    Tall("Tall", 250, 190),
}

internal class KeyboardViews(private val context: Context, val colors: KeyboardColors, height: KeyboardHeight) {
    private val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val headerHeight = 48
    val bodyHeight = if (landscape) height.landscape else height.portrait

    fun vertical() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    fun horizontal() = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun label(text: String, size: Float = 14f, strong: Boolean = false) = TextView(context).apply {
        this.text = text
        textSize = size
        setTextColor(colors.ink)
        if (strong) setTypeface(typeface, Typeface.BOLD)
    }

    private fun button(description: String, action: () -> Unit) = object : FrameLayout(context) {
        override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            info.className = "android.widget.Button"
        }
    }.apply {
        contentDescription = description
        isFocusable = true
        setOnClickListener { action() }
    }

    fun key(
        title: String, description: String = title, accented: Boolean = false, function: Boolean = false,
        alternate: String? = null, fontSize: Float = 19f, action: () -> Unit,
    ): View = button(description, action).apply {
        val fill = when {
            accented -> colors.accent
            function -> colors.function
            else -> colors.key
        }
        background = InsetDrawable(pressed(fill, if (function || accented) 14 else 8), dp(2), dp(2), dp(2), dp(2))
        val text = label(title, fontSize, function || accented).apply {
            gravity = Gravity.CENTER
            setPadding(dp(2), 0, dp(2), 0)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            if (accented) setTextColor(colors.onAccent)
        }
        addView(text, FrameLayout.LayoutParams(-1, -1).apply { if (alternate != null) topMargin = dp(3) })
        alternate?.let {
            addView(label(it, 9f, strong = true).apply {
                setTextColor(if (colors.key == -1) 0xff5a6862.toInt() else colors.secondary)
                includeFontPadding = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
                topMargin = dp(4)
                marginEnd = dp(5)
            })
        }
    }

    fun toolIcon(icon: ToolbarIcon, description: String, primary: Boolean, action: () -> Unit): View =
        button(description, action).apply {
            if (Build.VERSION.SDK_INT >= 26) tooltipText = description
            background = InsetDrawable(pressed(if (primary) colors.function else colors.background, 14),
                dp(2), dp(5), dp(2), dp(5))
            val glyph = ToolbarGlyph(context, icon,
                if (primary && colors.key == -1) colors.accent else colors.ink)
            glyph.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            addView(glyph, FrameLayout.LayoutParams(dp(21), dp(21), Gravity.CENTER))
        }

    fun gridItem(row: LinearLayout, item: View, weight: Float = 1f) {
        row.addView(item, LinearLayout.LayoutParams(0, -1, weight))
    }

    fun gridSpacer(row: LinearLayout, weight: Float) {
        gridItem(row, View(context).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, weight)
    }

    fun rowFill(grid: LinearLayout, row: View) {
        grid.addView(row, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    fun action(
        title: String, description: String = title, accent: Boolean = false, height: Int = 44, run: () -> Unit,
    ): TextView = label(title, 13f, strong = true).apply {
        gravity = Gravity.CENTER
        setPadding(dp(9), 0, dp(9), 0)
        minHeight = dp(height)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
        contentDescription = description
        setTextColor(if (accent) colors.onAccent else colors.ink)
        background = pressed(if (accent) colors.accent else colors.function, 12)
        isFocusable = true
        setOnClickListener { run() }
        accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = "android.widget.Button"
            }
        }
    }

    fun equal(row: LinearLayout, item: View, weight: Float = 1f) {
        row.addView(item, LinearLayout.LayoutParams(0, -2, weight).apply {
            setMargins(dp(2), dp(2), dp(2), dp(2))
        })
    }

    fun chips(names: List<String>, active: String?, select: (String) -> Unit): HorizontalScrollView {
        val line = horizontal()
        names.forEach { value ->
            line.addView(action(value, accent = value == active, height = 42) { select(value) },
                LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
        }
        return HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(line)
            val index = names.indexOf(active)
            if (index >= 0) post {
                val chip = line.getChildAt(index)
                if (chip.right > width) scrollTo(chip.left - dp(2), 0)
            }
        }
    }

    fun scrollPanel(children: View) = ScrollView(context).apply {
        isFillViewport = false
        clipToPadding = false
        addView(children)
        layoutParams = LinearLayout.LayoutParams(-1, -1)
    }

    fun rounded(color: Int, radius: Int = 10) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun pressed(color: Int, radius: Int) =
        RippleDrawable(ColorStateList.valueOf(0x30006b58), rounded(color, radius), null)

    fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()
}
