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
 * O desenho com o dedo fica restrito APENAS dentro desse retangulo.
 * Fora dele nao e possivel desenhar — os tracos sao clipados.
 *
 * O fundo fora do retangulo e o padrao roxo escuro do editor.
 */
class FrameCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    // Retangulo do quadro (calculado no onSizeChanged)
    private val frameRect = RectF()

    // Bitmap onde os tracos sao armazenados
    private var drawBitmap: Bitmap? = null
    private var drawCanvas: Canvas? = null

    // Paint do traço atual
    private val brushPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = Color.BLACK
        style     = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin= Paint.Join.ROUND
        strokeWidth = 6f
    }

    // Paint do fundo externo (roxo escuro)
    private val bgPaint = Paint().apply {
        color = Color.parseColor("#16162A")
        style = Paint.Style.FILL
    }

    // Paint do quadro branco
    private val framePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    // Paint da sombra do quadro
    private val shadowPaint = Paint().apply {
        color   = Color.parseColor("#44000000")
        style   = Paint.Style.FILL
        maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
    }

    // Paint da borda do quadro
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color       = Color.parseColor("#DDDDDD")
        style       = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    // Caminho atual do dedo
    private val currentPath = Path()
    private var lastX = 0f
    private var lastY = 0f

    // Cor e tamanho atuais do pincel
    var brushColor: Int = Color.BLACK
        set(value) { field = value; brushPaint.color = value }

    var brushSize: Float = 6f
        set(value) { field = value; brushPaint.strokeWidth = value }

    // Modo borracha
    var isEraser: Boolean = false

    // Indica se ha um frame selecionado (controla visibilidade do retangulo)
    var frameActive: Boolean = false
        set(value) { field = value; invalidate() }

    // Proporcao do quadro (padrao 16:9 de anime)
    private val frameRatio = 16f / 9f

    // Margem ao redor do retangulo
    private val margin = 48f

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        recalcFrameRect(w.toFloat(), h.toFloat())
        recreateBitmap(w, h)
    }

    private fun recalcFrameRect(w: Float, h: Float) {
        val availW = w - margin * 2
        val availH = h - margin * 2

        // Ajusta para caber na tela mantendo 16:9
        val frameW: Float
        val frameH: Float
        if (availW / availH > frameRatio) {
            frameH = availH
            frameW = frameH * frameRatio
        } else {
            frameW = availW
            frameH = frameW / frameRatio
        }

        val left   = (w - frameW) / 2f
        val top    = (h - frameH) / 2f
        frameRect.set(left, top, left + frameW, top + frameH)
    }

    private fun recreateBitmap(w: Int, h: Int) {
        drawBitmap?.recycle()
        if (w > 0 && h > 0) {
            drawBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            drawCanvas = Canvas(drawBitmap!!)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1 - Fundo roxo escuro
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        if (!frameActive) return

        // 2 - Sombra do quadro
        canvas.drawRect(
            frameRect.left  + 6f,
            frameRect.top   + 8f,
            frameRect.right + 6f,
            frameRect.bottom + 8f,
            shadowPaint
        )

        // 3 - Quadro branco
        canvas.drawRect(frameRect, framePaint)

        // 4 - Tracos desenhados (clipados ao frameRect)
        drawBitmap?.let { bmp ->
            canvas.save()
            canvas.clipRect(frameRect)
            canvas.drawBitmap(bmp, 0f, 0f, null)
            // Traço atual do dedo (ainda nao foi commitado no bitmap)
            val p = Paint(brushPaint).apply {
                color       = if (isEraser) Color.WHITE else brushColor
                strokeWidth = if (isEraser) brushSize * 4 else brushSize
                xfermode    = if (isEraser)
                    PorterDuffXfermode(PorterDuff.Mode.CLEAR) else null
            }
            canvas.drawPath(currentPath, p)
            canvas.restore()
        }

        // 5 - Borda do quadro
        canvas.drawRect(frameRect, borderPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!frameActive) return false

        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // So comeca se o toque for DENTRO do retangulo
                if (!frameRect.contains(x, y)) return false
                currentPath.moveTo(x, y)
                lastX = x; lastY = y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                // Clipa coordenadas ao frameRect
                val cx = x.coerceIn(frameRect.left, frameRect.right)
                val cy = y.coerceIn(frameRect.top,  frameRect.bottom)
                val mx = (lastX + cx) / 2f
                val my = (lastY + cy) / 2f
                currentPath.quadTo(lastX, lastY, mx, my)
                lastX = cx; lastY = cy
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Commita o traço no bitmap
                val p = Paint(brushPaint).apply {
                    color       = if (isEraser) Color.WHITE else brushColor
                    strokeWidth = if (isEraser) brushSize * 4 else brushSize
                    xfermode    = if (isEraser)
                        PorterDuffXfermode(PorterDuff.Mode.CLEAR) else null
                }
                drawCanvas?.save()
                drawCanvas?.clipRect(frameRect)
                drawCanvas?.drawPath(currentPath, p)
                drawCanvas?.restore()
                currentPath.reset()
                invalidate()
            }
        }
        return true
    }

    /** Limpa todos os tracos do quadro atual */
    fun clearFrame() {
        drawBitmap?.eraseColor(Color.TRANSPARENT)
        currentPath.reset()
        invalidate()
    }

    /** Retorna o bitmap somente da area do frame (para salvar) */
    fun getFrameBitmap(): Bitmap? {
        val bmp = drawBitmap ?: return null
        val l = frameRect.left.toInt().coerceAtLeast(0)
        val t = frameRect.top.toInt().coerceAtLeast(0)
        val w = frameRect.width().toInt().coerceAtMost(bmp.width  - l)
        val h = frameRect.height().toInt().coerceAtMost(bmp.height - t)
        if (w <= 0 || h <= 0) return null
        return Bitmap.createBitmap(bmp, l, t, w, h)
    }
}
