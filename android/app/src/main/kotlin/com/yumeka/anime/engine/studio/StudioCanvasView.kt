package com.yumeka.anime.engine.studio

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

enum class Tool(val icon: String, val label: String) {
    BRUSH("🖌", "Pincel"), PENCIL("✏", "Lapis"), MARKER("🖍", "Marcador"), ERASER("🧽", "Borracha"),
    FILL("🪣", "Balde"), LINE("📏", "Linha"), RECT("▭", "Retang."), ELLIPSE("◯", "Elipse"),
    PICKER("💧", "Conta-gotas"), MOVE("✥", "Mover"), HAND("✋", "Navegar")
}

class StudioCanvasView(context: Context) : View(context) {

    var scene: StudioScene? = null
    var frameIndex = 0
    var activeLayerId = -1
    var tool = Tool.BRUSH
    var color = Color.BLACK
    var size = 8f
    var opacity = 255
    var onion = true
    var cameraPreview = false
    var playing = false
    var selectedCharId = -1

    var onStrokeStart: ((frame: StudioFrame, layerId: Int, before: Bitmap?) -> Unit)? = null
    var onArtChanged: ((frame: StudioFrame, layerId: Int) -> Unit)? = null
    var onColorPicked: ((Int) -> Unit)? = null
    var onCharacterMoved: ((StudioCharacter, Float, Float) -> Unit)? = null
    var onCharacterSelected: ((Int) -> Unit)? = null

    private val view = Matrix()
    private val inverse = Matrix()
    private var userZoom = 1f
    private var panX = 0f
    private var panY = 0f
    private val bgPaint = Paint().apply { color = 0xFF0B0B12.toInt() }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66000000; maskFilter = BlurMaskFilter(24f, BlurMaskFilter.Blur.NORMAL) }
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val camPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f; color = 0xFFFBBF24.toInt(); pathEffect = DashPathEffect(floatArrayOf(18f, 10f), 0f)
    }
    private val path = Path()
    private var startX = 0f; private var startY = 0f
    private var curX = 0f; private var curY = 0f
    private var drawing = false
    private var multi = false
    private var lastMidX = 0f; private var lastMidY = 0f; private var lastDist = 0f
    private var dragChar: StudioCharacter? = null
    private var dragOffX = 0f; private var dragOffY = 0f
    private val onionPrev = Paint().apply { colorFilter = PorterDuffColorFilter(0xFFE8445A.toInt(), PorterDuff.Mode.SRC_IN); alpha = 70 }
    private val onionNext = Paint().apply { colorFilter = PorterDuffColorFilter(0xFF4ADE80.toInt(), PorterDuff.Mode.SRC_IN); alpha = 70 }

    init { setLayerType(LAYER_TYPE_HARDWARE, null) }

    fun resetView() { userZoom = 1f; panX = 0f; panY = 0f; invalidate() }

    private fun computeMatrix() {
        val pad = min(width, height) * 0.04f
        val s = min((width - pad * 2) / ART_W, (height - pad * 2) / ART_H) * userZoom
        view.reset()
        view.postScale(s, s)
        view.postTranslate((width - ART_W * s) / 2f + panX, (height - ART_H * s) / 2f + panY)
        view.invert(inverse)
    }

    private fun toArt(x: Float, y: Float): PointF {
        val pts = floatArrayOf(x, y); inverse.mapPoints(pts); return PointF(pts[0], pts[1])
    }

    private fun strokePaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        color = this@StudioCanvasView.color
        when (tool) {
            Tool.PENCIL -> { strokeWidth = max(1.5f, size * .35f); alpha = (opacity * .9f).toInt(); strokeCap = Paint.Cap.SQUARE }
            Tool.MARKER -> { strokeWidth = size * 2.2f; alpha = (opacity * .45f).toInt() }
            Tool.ERASER -> { strokeWidth = size * 3f; xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
            else -> { strokeWidth = size; alpha = opacity }
        }
    }

    private fun shapePath(): Path {
        val p = Path()
        when (tool) {
            Tool.LINE -> { p.moveTo(startX, startY); p.lineTo(curX, curY) }
            Tool.RECT -> p.addRect(min(startX, curX), min(startY, curY), max(startX, curX), max(startY, curY), Path.Direction.CW)
            Tool.ELLIPSE -> p.addOval(RectF(min(startX, curX), min(startY, curY), max(startX, curX), max(startY, curY)), Path.Direction.CW)
            else -> p.set(path)
        }
        return p
    }

    override fun onDraw(c: Canvas) {
        val s = scene ?: return
        computeMatrix()
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        c.save()
        c.concat(view)
        c.drawRect(6f, 10f, ART_W + 6f, ART_H + 10f, shadow)
        c.save()
        c.clipRect(0f, 0f, ART_W.toFloat(), ART_H.toFloat())
        val usingCam = playing || cameraPreview
        if (usingCam) FrameRenderer.applyCamera(c, s, frameIndex)
        val f = s.frames.getOrNull(frameIndex)
        if (f != null) {
            FrameRenderer.drawContent(c, s, frameIndex, if (playing) -1 else selectedCharId)
            if (onion && !playing) {
                s.frames.getOrNull(frameIndex - 1)?.art?.values?.forEach { c.drawBitmap(it, 0f, 0f, onionPrev) }
                s.frames.getOrNull(frameIndex + 1)?.art?.values?.forEach { c.drawBitmap(it, 0f, 0f, onionNext) }
            }
            if (drawing) {
                val sp = strokePaint()
                if (tool == Tool.ERASER) { sp.xfermode = null; sp.color = 0x88FFFFFF.toInt() }
                c.drawPath(shapePath(), sp)
            }
        }
        c.restore()
        if (!usingCam) {
            val cam = s.cameraAt(frameIndex)
            if (s.camKeys.isNotEmpty() || cam.zoom != 1f) {
                val cw = ART_W / cam.zoom; val chh = ART_H / cam.zoom
                c.drawRect(cam.x * ART_W - cw / 2, cam.y * ART_H - chh / 2, cam.x * ART_W + cw / 2, cam.y * ART_H + chh / 2, camPaint)
            }
        }
        c.restore()
    }

    private fun activeBitmap(f: StudioFrame, create: Boolean): Bitmap? {
        var b = f.art[activeLayerId]
        if (b == null && create) { b = Bitmap.createBitmap(ART_W, ART_H, Bitmap.Config.ARGB_8888); f.art[activeLayerId] = b }
        return b
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val s = scene ?: return false
        if (playing) return true
        val f = s.frames.getOrNull(frameIndex) ?: return false
        if (e.pointerCount >= 2) {
            val mx = (e.getX(0) + e.getX(1)) / 2; val my = (e.getY(0) + e.getY(1)) / 2
            val d = hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))
            if (!multi) { multi = true; drawing = false; path.reset(); dragChar = null; lastMidX = mx; lastMidY = my; lastDist = d; invalidate(); return true }
            if (e.actionMasked == MotionEvent.ACTION_MOVE) {
                if (lastDist > 10f) userZoom = (userZoom * d / lastDist).coerceIn(.5f, 6f)
                panX += mx - lastMidX; panY += my - lastMidY
                lastMidX = mx; lastMidY = my; lastDist = d
                invalidate()
            }
            return true
        }
        if (multi) { if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) multi = false; return true }
        computeMatrix()
        val pt = toArt(e.x, e.y)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = pt.x; startY = pt.y; curX = pt.x; curY = pt.y
                lastMidX = e.x; lastMidY = e.y
                when (tool) {
                    Tool.HAND -> {}
                    Tool.PICKER -> pick(s, pt)
                    Tool.FILL -> {
                        if (s.layers.none { it.id == activeLayerId }) return true
                        onStrokeStart?.invoke(f, activeLayerId, f.art[activeLayerId]?.copy(Bitmap.Config.ARGB_8888, true))
                        val b = activeBitmap(f, true)!!
                        floodFill(s, b, pt.x.toInt(), pt.y.toInt())
                        onArtChanged?.invoke(f, activeLayerId); invalidate()
                    }
                    Tool.MOVE -> {
                        val hit = s.characters.lastOrNull { CharacterPainter.bounds(it.poseAt(s, frameIndex)).contains(pt.x, pt.y) }
                        dragChar = hit
                        onCharacterSelected?.invoke(hit?.id ?: -1)
                        if (hit != null) { val po = hit.poseAt(s, frameIndex); dragOffX = po.x * ART_W - pt.x; dragOffY = po.y * ART_H - pt.y }
                    }
                    else -> {
                        if (s.layers.none { it.id == activeLayerId }) return true
                        drawing = true; path.reset(); path.moveTo(pt.x, pt.y)
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                when (tool) {
                    Tool.HAND -> { panX += e.x - lastMidX; panY += e.y - lastMidY; lastMidX = e.x; lastMidY = e.y; invalidate() }
                    Tool.MOVE -> dragChar?.let {
                        onCharacterMoved?.invoke(it, ((pt.x + dragOffX) / ART_W).coerceIn(0f, 1f), ((pt.y + dragOffY) / ART_H).coerceIn(0f, 1.3f)); invalidate()
                    }
                    Tool.PICKER -> pick(s, pt)
                    else -> if (drawing) {
                        path.quadTo(curX, curY, (curX + pt.x) / 2f, (curY + pt.y) / 2f)
                        curX = pt.x; curY = pt.y; invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (drawing) {
                    curX = pt.x; curY = pt.y
                    if (tool != Tool.LINE && tool != Tool.RECT && tool != Tool.ELLIPSE) path.lineTo(pt.x, pt.y)
                    onStrokeStart?.invoke(f, activeLayerId, f.art[activeLayerId]?.copy(Bitmap.Config.ARGB_8888, true))
                    val b = activeBitmap(f, true)!!
                    val dot = tool != Tool.LINE && tool != Tool.RECT && tool != Tool.ELLIPSE && hypot(startX - pt.x, startY - pt.y) < 1f
                    val sp = strokePaint()
                    val cv = Canvas(b)
                    if (dot) { sp.style = Paint.Style.FILL; cv.drawCircle(pt.x, pt.y, sp.strokeWidth / 2, sp) } else cv.drawPath(shapePath(), sp)
                    drawing = false; path.reset()
                    onArtChanged?.invoke(f, activeLayerId)
                    invalidate()
                }
                dragChar = null
            }
        }
        return true
    }

    private fun pick(s: StudioScene, pt: PointF) {
        val x = pt.x.toInt(); val y = pt.y.toInt()
        if (x !in 0 until ART_W || y !in 0 until ART_H) return
        val b = FrameRenderer.renderBitmap(s, frameIndex, ART_W, ART_H, false)
        onColorPicked?.invoke(b.getPixel(x, y) or 0xFF000000.toInt())
        b.recycle()
    }

    private fun floodFill(s: StudioScene, target: Bitmap, sx: Int, sy: Int) {
        if (sx !in 0 until ART_W || sy !in 0 until ART_H) return
        // Usa a composicao visivel como referencia de bordas, e pinta na camada ativa
        val ref = FrameRenderer.renderBitmap(s, frameIndex, ART_W, ART_H, false)
        val px = IntArray(ART_W * ART_H)
        ref.getPixels(px, 0, ART_W, 0, 0, ART_W, ART_H)
        ref.recycle()
        val out = IntArray(ART_W * ART_H)
        target.getPixels(out, 0, ART_W, 0, 0, ART_W, ART_H)
        val seed = px[sy * ART_W + sx]
        val fill = (color and 0x00FFFFFF) or (opacity shl 24)
        val tol = 40
        fun near(c: Int): Boolean =
            Math.abs(Color.red(c) - Color.red(seed)) <= tol && Math.abs(Color.green(c) - Color.green(seed)) <= tol && Math.abs(Color.blue(c) - Color.blue(seed)) <= tol
        val visited = BooleanArray(ART_W * ART_H)
        val stack = IntArray(ART_W * ART_H)
        var sp = 0
        stack[sp++] = sy * ART_W + sx
        while (sp > 0) {
            val i = stack[--sp]
            if (visited[i]) continue
            var x = i % ART_W; val y = i / ART_W
            while (x > 0 && !visited[y * ART_W + x - 1] && near(px[y * ART_W + x - 1])) x--
            var up = false; var down = false
            while (x < ART_W && !visited[y * ART_W + x] && near(px[y * ART_W + x])) {
                val k = y * ART_W + x
                visited[k] = true; out[k] = fill
                if (y > 0) { val a = k - ART_W; val ok = !visited[a] && near(px[a]); if (ok && !up) { stack[sp++] = a; up = true } else if (!ok) up = false }
                if (y < ART_H - 1) { val b = k + ART_W; val ok = !visited[b] && near(px[b]); if (ok && !down) { stack[sp++] = b; down = true } else if (!ok) down = false }
                x++
            }
        }
        // expande 1px para cobrir o antialias das linhas
        target.setPixels(out, 0, ART_W, 0, 0, ART_W, ART_H)
    }
}
