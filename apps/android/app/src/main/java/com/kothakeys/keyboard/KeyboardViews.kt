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

internal enum class TypingKeyFace(val radius: Int, val insetReduction: Int = 0) {
    Regular(6), Numeric(6, insetReduction = 2), Pill(100),
}

internal class KeyboardViews(val context: Context, val colors: KeyboardColors, height: KeyboardHeight) {
    private val configuration = context.resources.configuration
    private val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val headerHeight = 48
    val suggestionHeight = 34
    // The reference has an 85 px row pitch at 600 px wide. Cap the portrait
    // scale on tablets and keep the existing shorter landscape height presets.
    val bodyHeight = if (landscape) height.landscape else {
        val standard = configuration.screenWidthDp.coerceIn(280, 430) * (4f * 85 / 600)
        (standard * height.portrait / KeyboardHeight.Standard.portrait).roundToInt()
    }
    private val typingInset = if (landscape) 3 else 5

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
        alternate: String? = null, fontSize: Float = 19f, face: TypingKeyFace? = null,
        action: () -> Unit,
    ): View = button(description, action).apply {
        val fill = when {
            accented -> colors.accent
            function -> colors.function
            else -> colors.key
        }
        background = if (face == null) {
            InsetDrawable(pressed(fill, if (function || accented) 14 else 8), dp(2), dp(2), dp(2), dp(2))
        } else typingBackground(fill, face)
        val text = label(title, fontSize, face == null && (function || accented)).apply {
            gravity = Gravity.CENTER
            if (face != null) includeFontPadding = false
            setPadding(dp(2), 0, dp(2), 0)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            if (accented) setTextColor(colors.onAccent)
        }
        addView(text, FrameLayout.LayoutParams(-1, -1).apply { if (alternate != null && face == null) topMargin = dp(3) })
        alternate?.let {
            addView(label(it, 10f, strong = face == null).apply {
                setTextColor(if (colors.key == -1) 0xff5a6862.toInt() else colors.secondary)
                includeFontPadding = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
                topMargin = dp(if (face == null) 4 else typingInset - 1)
                marginEnd = dp(if (face == null) 5 else 3)
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

    fun typingIcon(
        icon: ToolbarIcon, description: String = icon.description,
        function: Boolean = true, accented: Boolean = false, face: TypingKeyFace = TypingKeyFace.Regular,
        action: () -> Unit,
    ): View = button(description, action).apply {
        val fill = if (accented) colors.accent else if (function) colors.function else colors.key
        background = typingBackground(fill, face)
        val glyph = ToolbarGlyph(context, icon, if (accented) colors.onAccent else colors.ink, stroke = 2f)
        glyph.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(glyph, FrameLayout.LayoutParams(dp(23), dp(23), Gravity.CENTER))
    }

    private fun typingBackground(color: Int, face: TypingKeyFace): InsetDrawable {
        val inset = dp((typingInset - face.insetReduction).coerceAtLeast(2))
        return InsetDrawable(pressed(color, face.radius), dp(2), inset, dp(2), inset)
    }

    fun suggestion(title: String, description: String, undo: Boolean, action: () -> Unit): View =
        button(description, action).apply {
            background = if (undo) InsetDrawable(pressed(colors.function, 100), dp(8), dp(2), dp(8), dp(2))
                else surfaceRipple()
            addView(label(title, 15f, strong = undo).apply {
                gravity = Gravity.CENTER
                includeFontPadding = false
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(6), 0, dp(6), 0)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, FrameLayout.LayoutParams(-1, -1))
        }

    fun dashboardTool(title: String, icon: ToolbarIcon, action: () -> Unit): View =
        button(title, action).apply {
            background = InsetDrawable(pressed(colors.key, 15), dp(3), dp(3), dp(3), dp(3))
            addView(horizontal().apply {
                setPaddingRelative(dp(15), 0, dp(9), 0)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                addView(ToolbarGlyph(context, icon, colors.ink), LinearLayout.LayoutParams(dp(20), dp(20)))
                addView(label(title, 14f, strong = true).apply {
                    isSingleLine = true
                    ellipsize = TextUtils.TruncateAt.END
                    includeFontPadding = false
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(11) })
            }, FrameLayout.LayoutParams(-1, -1))
        }

    fun operatorStack() = vertical().apply {
        val face = TypingKeyFace.Numeric
        val inset = dp((typingInset - face.insetReduction).coerceAtLeast(2))
        background = InsetDrawable(rounded(colors.function, face.radius), dp(2), inset, dp(2), inset)
    }

    fun stackedOperator(value: String, description: String, action: () -> Unit): View =
        button(description, action).apply {
            background = surfaceRipple()
            addView(label(value, 19f).apply {
                gravity = Gravity.CENTER
                includeFontPadding = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, FrameLayout.LayoutParams(-1, -1))
        }

    private fun surfaceRipple() =
        RippleDrawable(ColorStateList.valueOf(0x30006b58), null, rounded(-1, 8))

    fun gridItem(row: LinearLayout, item: View, weight: Float = 1f) {
        row.addView(item, LinearLayout.LayoutParams(0, -1, weight))
    }

    fun gridSpacer(row: LinearLayout, weight: Float) {
        gridItem(row, View(context).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, weight)
    }

    fun rowFill(grid: LinearLayout, row: View, weight: Float = 1f) {
        grid.addView(row, LinearLayout.LayoutParams(-1, 0, weight))
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
