package com.kothakeys.keyboard

import android.content.Context
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.roundToInt

internal data class KeyboardColors(val background: Int, val key: Int, val ink: Int, val accent: Int, val onAccent: Int) {
    companion object {
        fun from(dark: Boolean) = if (dark) KeyboardColors(
            0xff15221d.toInt(), 0xff283b32.toInt(), 0xffedf7f1.toInt(), 0xff75d8b8.toInt(), 0xff15221d.toInt()
        ) else KeyboardColors(
            0xfff5f6f2.toInt(), Color.WHITE, 0xff172822.toInt(), 0xff116b58.toInt(), Color.WHITE
        )
    }
}

internal class KeyboardViews(private val context: Context, val colors: KeyboardColors) {
    private val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val keyHeight = if (landscape) 37 else 47
    val panelHeight = if (landscape) 108 else 224

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

    fun key(
        title: String,
        description: String = title,
        accented: Boolean = false,
        height: Int = keyHeight,
        fontSize: Float = 16f,
        action: () -> Unit
    ): TextView = object : TextView(context) {
        override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            info.className = "android.widget.Button"
        }
    }.apply {
        text = title
        contentDescription = description
        gravity = Gravity.CENTER
        isSingleLine = true
        maxLines = 1
        setPadding(dp(2), 0, dp(2), 0)
        minimumHeight = dp(height)
        textSize = fontSize
        setTextColor(if (accented) colors.onAccent else colors.ink)
        background = RippleDrawable(
            ColorStateList.valueOf(if (accented) 0x27777a77 else 0x30116b58),
            rounded(if (accented) colors.accent else colors.key),
            null
        )
        isFocusable = true
        setOnClickListener { action() }
    }

    fun weighted(row: LinearLayout, item: View, weight: Float = 1f) {
        row.addView(item, LinearLayout.LayoutParams(0, -2, weight).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
    }

    fun chips(
        names: List<String>,
        active: String? = null,
        display: (String) -> String = { it },
        action: (String) -> Unit
    ): HorizontalScrollView {
        val line = horizontal()
        names.forEach { value ->
            val title = display(value)
            val chip = key(title, title, value == active, height = 36, fontSize = 13f) { action(value) }
            chip.setPadding(dp(10), 0, dp(10), 0)
            line.addView(chip, LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
        }
        return HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(line)
            if (active != null) {
                val index = names.indexOf(active)
                if (index >= 0) post { scrollTo(line.getChildAt(index).left - dp(2), 0) }
            }
        }
    }

    fun scrollPanel(children: LinearLayout) = ScrollView(context).apply {
        isFillViewport = true
        val maxHeight = dp(panelHeight)
        addView(children)
        layoutParams = LinearLayout.LayoutParams(-1, maxHeight)
    }

    fun rounded(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(8).toFloat()
    }

    fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()
}
