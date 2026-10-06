package com.yumeka.anime.engine.episode

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Linha do tempo multi-faixa. Cada clip ocupa sua propria faixa.
 *  - Toque na regua: move o cursor (playhead).
 *  - Arrastar o meio do clip: move no tempo.
 *  - Arrastar as bordas: redimensiona (corta inicio/fim).
 *  - Arrastar area vazia: rola; pinca com dois dedos: zoom.
 */
class EpisodeTimelineView(context: Context) : View(context) {

    var episode: Episode? = null
        set(v) { field = v; requestLayout(); invalidate() }
    var selectedId: Long = -1
        set(v) { field = v; invalidate() }

    /** Tempo global (inclui introducao). */
    var playheadMs: Long = 0
        set(v) { field = v.coerceAtLeast(0); ensureVisible(); invalidate() }

    var onPlayhead: ((Long) -> Unit)? = null
    var onSelect: ((Long) -> Unit)? = null
    var onEditStart: (() -> Unit)? = null
    var onEditEnd: (() -> Unit)? = null

    private val d = resources.displayMetrics.density
    private val rulerH = 28 * d
    private val rowH = 46 * d
    private val edge = 18 * d
    private val minDur = 200L
    private var pxPerMs = 0.06f * d
    private var scrollMs = 0f

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11 * d; color = 0xFFEEEEF5.toInt() }
    private val rect = RectF()

    private enum class Mode { NONE, SCRUB, MOVE, LEFT, RIGHT, PAN }
    private var mode = Mode.NONE
    private var downX = 0f
    private var downStart = 0L
    private var downDur = 0L
    private var downScroll = 0f
    private var dragClip: Clip? = null
    private var moved = false

    private val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(det: ScaleGestureDetector): Boolean {
            val focusMs = xToMs(det.focusX)
            pxPerMs = (pxPerMs * det.scaleFactor).coerceIn(0.005f * d, 0.6f * d)
            scrollMs = (focusMs - det.focusX / pxPerMs).coerceAtLeast(0f)
            mode = Mode.NONE
            invalidate(); return true
        }
    })

    private fun introMs() = episode?.introMs ?: 0L
    private fun msToX(ms: Float) = (ms - scrollMs) * pxPerMs
    private fun xToMs(x: Float) = x / pxPerMs + scrollMs

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val rows = max(episode?.clips?.size ?: 0, 3)
        val h = (rulerH + rows * rowH + 8 * d).toInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), h)
    }

    private fun ensureVisible() {
        if (width == 0) return
        val x = msToX(playheadMs.toFloat())
        if (x < 0 || x > width - 24 * d) scrollMs = (playheadMs - width * 0.3f / pxPerMs).coerceAtLeast(0f)
    }

    private fun clipRect(i: Int, c: Clip, out: RectF) {
        val top = rulerH + i * rowH + 4 * d
        out.set(msToX((introMs() + c.startMs).toFloat()), top, msToX((introMs() + c.endMs).toFloat()), top + rowH - 8 * d)
    }

    override fun onDraw(canvas: Canvas) {
        val ep = episode ?: return
        canvas.drawColor(0xFF0F0F17.toInt())
        // Regua
        p.color = 0xFF15151F.toInt(); canvas.drawRect(0f, 0f, width.toFloat(), rulerH, p)
        val stepMs = niceStep()
        var t = (scrollMs / stepMs).toLong() * stepMs
        p.color = 0xFF50506A.toInt()
        tp.color = 0xFF9090B0.toInt()
        while (msToX(t.toFloat()) < width) {
            val x = msToX(t.toFloat())
            canvas.drawRect(x, rulerH - 8 * d, x + d, rulerH, p)
            canvas.drawText(fmt(t), x + 3 * d, rulerH - 11 * d, tp)
            t += stepMs
        }
        // Blocos de introducao e desfecho na regua
        if (ep.intro.enabled) drawBookend(canvas, 0f, ep.introMs.toFloat(), "Intro")
        if (ep.outro.enabled) {
            val s = (ep.introMs + ep.bodyMs).toFloat()
            drawBookend(canvas, s, s + ep.outroMs, "Desfecho")
        }
        // Faixas
        ep.clips.forEachIndexed { i, c ->
            val top = rulerH + i * rowH
            p.color = if (i % 2 == 0) 0xFF12121B.toInt() else 0xFF15151F.toInt()
            canvas.drawRect(0f, top, width.toFloat(), top + rowH, p)
            clipRect(i, c, rect)
            p.color = c.type.color; p.alpha = if (c.id == selectedId) 255 else 190
            canvas.drawRoundRect(rect, 10 * d, 10 * d, p)
            p.alpha = 255
            if (c.id == selectedId) {
                p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f * d; p.color = 0xFFFFFFFF.toInt()
                canvas.drawRoundRect(rect, 10 * d, 10 * d, p); p.style = Paint.Style.FILL
                p.color = 0xAAFFFFFF.toInt()
                canvas.drawRoundRect(rect.left + 3 * d, rect.centerY() - 9 * d, rect.left + 7 * d, rect.centerY() + 9 * d, 2 * d, 2 * d, p)
                canvas.drawRoundRect(rect.right - 7 * d, rect.centerY() - 9 * d, rect.right - 3 * d, rect.centerY() + 9 * d, 2 * d, 2 * d, p)
            }
            tp.color = 0xFF0B0B12.toInt()
            canvas.save(); canvas.clipRect(rect)
            canvas.drawText("${c.type.icon} ${c.label}", max(rect.left, 0f) + 10 * d, rect.centerY() + 4 * d, tp)
            canvas.restore()
        }
        // Cursor
        val px = msToX(playheadMs.toFloat())
        p.color = 0xFFFF6080.toInt()
        canvas.drawRect(px - d, 0f, px + d, height.toFloat(), p)
        canvas.drawCircle(px, rulerH / 2, 6 * d, p)
    }

    private fun drawBookend(c: Canvas, s: Float, e: Float, label: String) {
        rect.set(msToX(s), 2 * d, msToX(e), rulerH - 16 * d)
        p.color = 0x669B6DFF; c.drawRoundRect(rect, 4 * d, 4 * d, p)
        tp.color = 0xFFEEEEF5.toInt(); tp.textSize = 9 * d
        c.drawText(label, rect.left + 3 * d, rect.bottom - 2 * d, tp)
        tp.textSize = 11 * d
    }

    private fun niceStep(): Long {
        val target = 80 * d / pxPerMs
        return listOf<Long>(100, 250, 500, 1000, 2000, 5000, 10000, 30000, 60000).firstOrNull { it >= target } ?: 120000
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        scaler.onTouchEvent(e)
        if (e.pointerCount > 1) { parent?.requestDisallowInterceptTouchEvent(true); return true }
        val ep = episode ?: return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; moved = false; downScroll = scrollMs
                parent?.requestDisallowInterceptTouchEvent(true)
                if (e.y < rulerH) { mode = Mode.SCRUB; scrub(e.x); return true }
                val row = ((e.y - rulerH) / rowH).toInt()
                val c = ep.clips.getOrNull(row)
                mode = Mode.PAN; dragClip = null
                if (c != null) {
                    clipRect(row, c, rect)
                    if (e.x >= rect.left - edge / 2 && e.x <= rect.right + edge / 2) {
                        dragClip = c; downStart = c.startMs; downDur = c.durationMs
                        mode = when {
                            c.id == selectedId && abs(e.x - rect.left) < edge -> Mode.LEFT
                            c.id == selectedId && abs(e.x - rect.right) < edge -> Mode.RIGHT
                            else -> Mode.MOVE
                        }
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - downX
                if (!moved && abs(dx) > 6 * d) {
                    moved = true
                    if (mode in listOf(Mode.MOVE, Mode.LEFT, Mode.RIGHT)) {
                        val c = dragClip
                        if (c != null && c.id != selectedId) { selectedId = c.id; onSelect?.invoke(c.id) }
                        onEditStart?.invoke()
                    }
                }
                if (!moved) return true
                val dms = (dx / pxPerMs).roundToLong()
                val c = dragClip
                when (mode) {
                    Mode.SCRUB -> scrub(e.x)
                    Mode.PAN -> { scrollMs = (downScroll - dx / pxPerMs).coerceAtLeast(0f); invalidate() }
                    Mode.MOVE -> if (c != null) { c.startMs = snap(downStart + dms).coerceAtLeast(0); changed() }
                    Mode.LEFT -> if (c != null) {
                        val ns = (downStart + dms).coerceIn(0, downStart + downDur - minDur)
                        c.durationMs = downDur - (ns - downStart); c.startMs = ns; changed()
                    }
                    Mode.RIGHT -> if (c != null) { c.durationMs = (downDur + dms).coerceAtLeast(minDur); changed() }
                    Mode.NONE -> {}
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!moved && mode != Mode.SCRUB && e.actionMasked == MotionEvent.ACTION_UP) {
                    val id = dragClip?.id ?: -1L
                    selectedId = id; onSelect?.invoke(id)
                    if (id == -1L) scrub(e.x)
                }
                if (moved && mode in listOf(Mode.MOVE, Mode.LEFT, Mode.RIGHT)) onEditEnd?.invoke()
                mode = Mode.NONE; dragClip = null
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    /** Ima: encaixa no cursor e nas bordas dos outros clips. */
    private fun snap(startMs: Long): Long {
        val ep = episode ?: return startMs
        val c = dragClip ?: return startMs
        val tol = (10 * d / pxPerMs).toLong()
        val targets = ep.clips.filter { it !== c }.flatMap { listOf(it.startMs, it.endMs) } + (playheadMs - ep.introMs) + 0L
        for (t in targets) {
            if (abs(startMs - t) < tol) return t
            if (abs(startMs + c.durationMs - t) < tol) return t - c.durationMs
        }
        return startMs
    }

    private fun changed() { requestLayout(); invalidate() }

    private fun scrub(x: Float) {
        val total = episode?.totalMs ?: 0L
        val ms = xToMs(x).toLong().coerceIn(0, max(total, 0L))
        playheadMs = ms; onPlayhead?.invoke(ms)
    }

    companion object {
        fun fmt(ms: Long): String {
            val s = ms / 1000; val dec = (ms % 1000) / 100
            return "%d:%02d.%d".format(s / 60, s % 60, dec)
        }
    }
}
