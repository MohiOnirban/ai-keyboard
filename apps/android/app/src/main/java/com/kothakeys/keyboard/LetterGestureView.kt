package com.kothakeys.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.LinearLayout
import kotlin.math.hypot

/** Routes ordinary taps to their original child and consumes only continuous drags originating on a letter. */
internal class LetterGestureView(
    context: Context, private val allowGlide: Boolean, private val trail: Boolean, accent: Int,
    private val tapHint: (Char?) -> Unit, private val commit: (List<GlidePoint>) -> Unit,
) : LinearLayout(context) {
    private val centers = linkedMapOf<Char, PointF>()
    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent; alpha = 90; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND; strokeWidth = 7 * resources.displayMetrics.density
    }
    private val points = ArrayList<GlidePoint>()
    private var first: Char? = null
    private var swiping = false
    private var pitch = 1f
    private var rowHeight = 1f

    init { orientation = VERTICAL }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        centers.clear()
        for (i in 0 until childCount) {
            val row = getChildAt(i) as? LinearLayout ?: continue
            for (j in 0 until row.childCount) {
                val key: View = row.getChildAt(j)
                val letter = key.tag as? Char ?: continue
                centers[letter] = PointF(row.left + key.left + key.width / 2f, row.top + key.top + key.height / 2f)
            }
        }
        val q = centers['q'] ?: return
        pitch = ((centers['w']?.x ?: q.x) - q.x).coerceAtLeast(1f)
        rowHeight = ((centers['a']?.y ?: q.y) - q.y).coerceAtLeast(1f)
    }

    private fun distance(point: PointF, x: Float, y: Float) =
        hypot(((x - point.x) / pitch).toDouble(), ((y - point.y) / rowHeight).toDouble())

    private fun point(x: Float, y: Float): GlidePoint {
        val q = centers.getValue('q')
        return GlidePoint(((x - q.x) / pitch).toDouble(), ((y - q.y) / rowHeight).toDouble())
    }

    private fun append(x: Float, y: Float) {
        val next = point(x, y)
        val previous = points.lastOrNull()
        if (previous != null && hypot(previous.x - next.x, previous.y - next.y) < .09) return
        if (points.size >= 192) {
            for (i in points.lastIndex - 1 downTo 1 step 2) points.removeAt(i)
            path.reset()
            val origin = centers.getValue('q')
            points.forEachIndexed { i, p ->
                val px = origin.x + p.x.toFloat() * pitch
                val py = origin.y + p.y.toFloat() * rowHeight
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
        }
        points.add(next)
        if (previous == null) path.moveTo(x, y) else path.lineTo(x, y)
    }

    private fun reset() {
        first = null
        swiping = false
        points.clear()
        path.reset()
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (centers.isEmpty()) return super.dispatchTouchEvent(event)
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                reset()
                first = centers.entries.firstOrNull { (_, p) ->
                    kotlin.math.abs(p.x - x) <= pitch / 2 && kotlin.math.abs(p.y - y) <= rowHeight / 2
                }?.key
                if (first != null) append(x, y)
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> {
                val consumed = swiping
                reset()
                if (consumed) return true
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                val start = first ?: return super.dispatchTouchEvent(event)
                for (i in 0 until event.historySize) append(event.getHistoricalX(i), event.getHistoricalY(i))
                append(x, y)
                val origin = points.first()
                val dest = points.last()
                if (!swiping && allowGlide && event.eventTime - event.downTime < ViewConfiguration.getLongPressTimeout() &&
                    hypot(dest.x - origin.x, dest.y - origin.y) > 1.05) {
                    val cancel = MotionEvent.obtain(event)
                    cancel.action = MotionEvent.ACTION_CANCEL
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                    swiping = true
                }
                if (swiping) {
                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        val result = points.toList()
                        reset()
                        commit(result)
                    } else if (trail) invalidate()
                    return true
                }
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    val center = centers.getValue(start)
                    val alternate = centers.entries.asSequence().filter { it.key != start }
                        .minByOrNull { distance(it.value, x, y) }?.takeIf {
                            distance(center, x, y) > .30 && distance(it.value, x, y) < .77
                        }?.key
                    tapHint(alternate)
                    return try { super.dispatchTouchEvent(event) } finally { reset() }
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (swiping && trail) canvas.drawPath(path, paint)
    }
}
