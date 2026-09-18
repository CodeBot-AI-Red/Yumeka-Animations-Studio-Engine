package com.yumeka.anime.engine.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * FrameCanvasView — area de desenho do YASE.
 *
 * Exibe um retangulo branco centralizado (o "quadro" do anime).
 * O desenho fica restrito APENAS dentro desse retangulo.
 * Proporcao 16:9 com margem pequena para caber bem no mobile.
 */
class FrameCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val frameRect  = RectF()
    private var drawBitmap: Bitmap? = null
    private var drawCanvas: Canvas? = null

    // Paints
    private val bgPaint = Paint().apply {
        color = Color.parseColor("#16162A")
        style = Paint.Style.FILL
    }
    private val framePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#55000000")
        style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL)
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color       = Color.parseColor("#CCCCCC")
        style       = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val brushPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style     = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin= Paint.Join.ROUND
        strokeWidth = 6f
        color = Color.BLACK
    }

    private val currentPath = Path()
    private var lastX = 0f
    private var lastY = 0f

    var brushColor: Int = Color.BLACK
        set(v) { field = v; brushPaint.color = v }

    var brushSize: Float = 6f
        set(v) { field = v; brushPaint.strokeWidth = v }

    var isEraser: Boolean = false

    var frameActive: Boolean = false
        set(v) { field = v; invalidate() }

    // Proporcao 16:9
    private val ratio = 16f / 9f

    // Margem reduzida: so 16dp de cada lado (px calculado no onSizeChanged)
    private var marginPx = 0f

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        // Margem: 2% da largura ou minimo 8px
        marginPx = (w * 0.02f).coerceAtLeast(8f)
        recalcRect(w.toFloat(), h.toFloat())
        recreateBitmap(w, h)
    }

    private fun recalcRect(w: Float, h: Float) {
        // Area disponivel descontando a toolbar de pincel (48dp ~ 10% da altura)
        val availW = w - marginPx * 2
        val availH = h - marginPx * 2 - (h * 0.12f) // reserva espaco pra toolbar

        val fW: Float
        val fH: Float
        if (availW / availH > ratio) {
            fH = availH; fW = fH * ratio
        } else {
            fW = availW; fH = fW / ratio
        }

        val left = (w - fW) / 2f
        // Posiciona um pouco acima do centro para nao sobrepor a toolbar
        val top  = marginPx + (availH - fH) / 2f
        frameRect.set(left, top, left + fW, top + fH)
    }

    private fun recreateBitmap(w: Int, h: Int) {
        drawBitmap?.recycle()
        if (w > 0 && h > 0) {
            drawBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            drawCanvas = Canvas(drawBitmap!!)
        }
    }

    override fun onDraw(canvas: Canvas) {
        // Fundo
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        if (!frameActive) return

        // Sombra
        canvas.drawRect(
            frameRect.left + 4f, frameRect.top + 6f,
            frameRect.right + 4f, frameRect.bottom + 6f,
            shadowPaint
        )
        // Quadro branco
        canvas.drawRect(frameRect, framePaint)

        // Tracos no bitmap (clipados)
        drawBitmap?.let { bmp ->
            canvas.save()
            canvas.clipRect(frameRect)
            canvas.drawBitmap(bmp, 0f, 0f, null)
            val p = activePaint()
            canvas.drawPath(currentPath, p)
            canvas.restore()
        }

        // Borda
        canvas.drawRect(frameRect, borderPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!frameActive) return false
        val x = event.x; val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (!frameRect.contains(x, y)) return false
                currentPath.moveTo(x, y); lastX = x; lastY = y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val cx = x.coerceIn(frameRect.left, frameRect.right)
                val cy = y.coerceIn(frameRect.top,  frameRect.bottom)
                currentPath.quadTo(lastX, lastY, (lastX + cx) / 2f, (lastY + cy) / 2f)
                lastX = cx; lastY = cy
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                drawCanvas?.save()
                drawCanvas?.clipRect(frameRect)
                drawCanvas?.drawPath(currentPath, activePaint())
                drawCanvas?.restore()
                currentPath.reset()
                invalidate()
            }
        }
        return true
    }

    private fun activePaint() = Paint(brushPaint).apply {
        color = if (isEraser) Color.WHITE else brushColor
        strokeWidth = if (isEraser) brushSize * 4 else brushSize
        xfermode = if (isEraser) PorterDuffXfermode(PorterDuff.Mode.CLEAR) else null
    }

    fun clearFrame() {
        drawBitmap?.eraseColor(Color.TRANSPARENT)
        currentPath.reset()
        invalidate()
    }

    fun getFrameBitmap(): Bitmap? {
        val bmp = drawBitmap ?: return null
        val l = frameRect.left.toInt().coerceAtLeast(0)
        val t = frameRect.top.toInt().coerceAtLeast(0)
        val w = frameRect.width().toInt().coerceAtMost(bmp.width - l)
        val h = frameRect.height().toInt().coerceAtMost(bmp.height - t)
        if (w <= 0 || h <= 0) return null
        return Bitmap.createBitmap(bmp, l, t, w, h)
    }
}
