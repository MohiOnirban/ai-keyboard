package com.kothakeys.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View

internal enum class ToolbarIcon(val description: String) {
    Grammar("Fix Grammar"), Tone("Improve & Tone"), Ask("Ask AI"), Translate("Translation"),
    Emoji("Emoji"), Clipboard("Clipboard"), More("Tools menu"), Voice("Voice input"),
    Back("Return to keyboard"), Language("Change typing language"),
    Close("Close tools"), Theme("Theme"), Edit("Text editing"), Layout("Keyboard layout"),
    Settings("Settings"), Numbers("Numeric keypad"),
    Shift("Shift"), CapsLock("Caps lock"), Delete("Backspace"),
    Return("Enter"), Done("Done"), Forward("Next"), Search("Search"), Send("Send"),
}

@SuppressLint("ViewConstructor")
internal class ToolbarGlyph(context: Context, private val icon: ToolbarIcon, color: Int, stroke: Float = 1.65f) :
    View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        strokeWidth = stroke
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.scale(width / 24f, height / 24f)
        when (icon) {
            ToolbarIcon.Grammar -> {
                stroke(canvas, 3f, 19f, 8f, 5f, 13f, 19f)
                stroke(canvas, 5f, 14f, 11f, 14f)
                stroke(canvas, 14f, 12f, 17f, 15f, 22f, 9f)
            }
            ToolbarIcon.Tone -> {
                stroke(canvas, 3f, 6f, 21f, 6f)
                stroke(canvas, 3f, 12f, 21f, 12f)
                stroke(canvas, 3f, 18f, 21f, 18f)
                stroke(canvas, 8f, 3.5f, 8f, 8.5f)
                stroke(canvas, 16f, 9.5f, 16f, 14.5f)
                stroke(canvas, 10f, 15.5f, 10f, 20.5f)
            }
            ToolbarIcon.Ask -> {
                stroke(canvas, 4f, 5f, 20f, 5f, 20f, 16f, 11f, 16f, 6f, 20f, 6f, 16f, 4f, 16f, 4f, 5f)
                stroke(canvas, 9f, 11f, 12f, 8f, 15f, 11f, 12f, 14f, 9f, 11f)
            }
            ToolbarIcon.Translate -> {
                stroke(canvas, 3f, 5f, 15f, 5f)
                stroke(canvas, 9f, 3f, 9f, 5f)
                stroke(canvas, 12f, 5f, 10f, 11f, 5f, 15f)
                stroke(canvas, 6f, 8f, 10f, 12f, 13f, 14f)
                stroke(canvas, 12f, 21f, 17f, 11f, 22f, 21f)
                stroke(canvas, 14f, 18f, 20f, 18f)
            }
            ToolbarIcon.Emoji -> {
                canvas.drawCircle(12f, 12f, 8.5f, paint)
                canvas.drawArc(8f, 9f, 16f, 16f, 12f, 156f, false, paint)
                dots(canvas, 9f, 10f, 15f, 10f)
            }
            ToolbarIcon.Clipboard -> {
                stroke(canvas, 8f, 5f, 5f, 5f, 5f, 21f, 19f, 21f, 19f, 5f, 16f, 5f)
                canvas.drawRoundRect(8f, 3f, 16f, 7f, 1f, 1f, paint)
                stroke(canvas, 9f, 11f, 15f, 11f)
                stroke(canvas, 9f, 15f, 15f, 15f)
            }
            ToolbarIcon.More -> {
                for (left in listOf(3f, 13.5f)) for (top in listOf(3f, 13.5f)) {
                    canvas.drawRoundRect(left, top, left + 7.5f, top + 7.5f, 1.2f, 1.2f, paint)
                }
            }
            ToolbarIcon.Close -> {
                stroke(canvas, 6f, 6f, 18f, 18f)
                stroke(canvas, 18f, 6f, 6f, 18f)
            }
            ToolbarIcon.Theme -> theme(canvas)
            ToolbarIcon.Edit -> editing(canvas)
            ToolbarIcon.Layout -> keyboard(canvas, numeric = false)
            ToolbarIcon.Numbers -> keyboard(canvas, numeric = true)
            ToolbarIcon.Settings -> settings(canvas)
            ToolbarIcon.Voice -> {
                canvas.drawRoundRect(9f, 3f, 15f, 14f, 3f, 3f, paint)
                canvas.drawArc(5.5f, 5.5f, 18.5f, 17.5f, 0f, 180f, false, paint)
                stroke(canvas, 12f, 18f, 12f, 21f)
                stroke(canvas, 9f, 21f, 15f, 21f)
            }
            ToolbarIcon.Back -> {
                stroke(canvas, 10f, 6f, 4f, 12f, 10f, 18f)
                stroke(canvas, 4f, 12f, 20f, 12f)
            }
            ToolbarIcon.Shift, ToolbarIcon.CapsLock -> {
                stroke(canvas, 3f, 11f, 12f, 3f, 21f, 11f, 17f, 11f, 17f, 19f, 7f, 19f, 7f, 11f, 3f, 11f)
                if (icon == ToolbarIcon.CapsLock) stroke(canvas, 7f, 22f, 17f, 22f)
            }
            ToolbarIcon.Delete -> {
                stroke(canvas, 2f, 12f, 8f, 5f, 21f, 5f, 21f, 19f, 8f, 19f, 2f, 12f)
                stroke(canvas, 10f, 9f, 16f, 15f)
                stroke(canvas, 10f, 15f, 16f, 9f)
            }
            ToolbarIcon.Return -> {
                stroke(canvas, 20f, 6f, 20f, 13f, 4f, 13f)
                stroke(canvas, 9f, 8f, 4f, 13f, 9f, 18f)
            }
            ToolbarIcon.Done -> stroke(canvas, 4f, 12f, 9f, 17f, 20f, 6f)
            ToolbarIcon.Forward -> {
                stroke(canvas, 4f, 12f, 20f, 12f)
                stroke(canvas, 14f, 6f, 20f, 12f, 14f, 18f)
            }
            ToolbarIcon.Search -> {
                canvas.drawCircle(10f, 10f, 5.5f, paint)
                stroke(canvas, 14f, 14f, 20f, 20f)
            }
            ToolbarIcon.Send -> {
                stroke(canvas, 3f, 4f, 21f, 12f, 3f, 20f, 6f, 12f, 3f, 4f)
                stroke(canvas, 6f, 12f, 21f, 12f)
            }
            ToolbarIcon.Language -> {
                canvas.drawCircle(12f, 12f, 8.8f, paint)
                canvas.drawOval(8f, 3.2f, 16f, 20.8f, paint)
                stroke(canvas, 3.5f, 12f, 20.5f, 12f)
            }
        }
        canvas.restore()
    }

    private fun theme(canvas: Canvas) {
        canvas.drawCircle(12f, 12f, 4.5f, paint)
        stroke(canvas, 12f, 2f, 12f, 4.5f)
        stroke(canvas, 12f, 19.5f, 12f, 22f)
        stroke(canvas, 2f, 12f, 4.5f, 12f)
        stroke(canvas, 19.5f, 12f, 22f, 12f)
        stroke(canvas, 5f, 5f, 6.7f, 6.7f)
        stroke(canvas, 17.3f, 17.3f, 19f, 19f)
        stroke(canvas, 5f, 19f, 6.7f, 17.3f)
        stroke(canvas, 17.3f, 6.7f, 19f, 5f)
    }

    private fun editing(canvas: Canvas) {
        stroke(canvas, 4f, 5f, 17f, 5f, 17f, 8f)
        stroke(canvas, 4f, 5f, 4f, 8f)
        stroke(canvas, 10.5f, 5f, 10.5f, 19f)
        stroke(canvas, 7f, 19f, 14f, 19f)
        stroke(canvas, 19f, 11f, 19f, 18f)
    }

    private fun keyboard(canvas: Canvas, numeric: Boolean) {
        if (numeric) {
            canvas.drawRoundRect(4f, 3f, 20f, 21f, 2f, 2f, paint)
            dots(canvas, 8f, 8f, 12f, 8f, 16f, 8f, 8f, 12f, 12f, 12f, 16f, 12f, 12f, 16f)
        } else {
            canvas.drawRoundRect(2f, 5f, 22f, 19f, 2f, 2f, paint)
            dots(canvas, 6f, 9.5f, 10f, 9.5f, 14f, 9.5f, 18f, 9.5f)
            stroke(canvas, 7f, 14.5f, 17f, 14.5f)
        }
    }

    private fun settings(canvas: Canvas) {
        stroke(canvas, 10f, 3f, 14f, 3f, 15f, 6f, 18f, 7.5f, 21f, 7f, 22.5f, 10f,
            20.5f, 12f, 22.5f, 14f, 21f, 17f, 18f, 16.5f, 15f, 18f, 14f, 21f, 10f, 21f,
            9f, 18f, 6f, 16.5f, 3f, 17f, 1.5f, 14f, 3.5f, 12f, 1.5f, 10f, 3f, 7f,
            6f, 7.5f, 9f, 6f, 10f, 3f)
        canvas.drawCircle(12f, 12f, 3f, paint)
    }

    private fun stroke(canvas: Canvas, vararg points: Float) {
        path.rewind()
        path.moveTo(points[0], points[1])
        for (index in 2 until points.size step 2) path.lineTo(points[index], points[index + 1])
        canvas.drawPath(path, paint)
    }

    private fun dots(canvas: Canvas, vararg points: Float) {
        paint.style = Paint.Style.FILL
        for (index in points.indices step 2) canvas.drawCircle(points[index], points[index + 1], 1.35f, paint)
        paint.style = Paint.Style.STROKE
    }
}
