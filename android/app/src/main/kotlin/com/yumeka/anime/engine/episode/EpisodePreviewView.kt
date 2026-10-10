package com.yumeka.anime.engine.episode

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.media.MediaMetadataRetriever
import android.util.LruCache
import android.view.View
import kotlin.math.min
import kotlin.math.sin

/**
 * Pre-visualizacao 16:9 do episodio no tempo [timeMs].
 * Tambem usada pela exportacao para renderizar quadros (drawAt).
 */
class EpisodePreviewView(context: Context) : View(context) {

    var episode: Episode? = null
        set(v) { field = v; invalidate() }
    var timeMs: Long = 0
        set(v) { field = v; invalidate() }

    private val renderer = EpisodeRenderer(resources.displayMetrics.density)

    override fun onMeasure(w: Int, h: Int) {
        val width = MeasureSpec.getSize(w)
        val maxH = MeasureSpec.getSize(h)
        var height = width * 9 / 16
        var finalW = width
        if (MeasureSpec.getMode(h) != MeasureSpec.UNSPECIFIED && height > maxH) { height = maxH; finalW = height * 16 / 9 }
        setMeasuredDimension(finalW, height)
    }

    override fun onDraw(canvas: Canvas) {
        val ep = episode
        if (ep == null) { canvas.drawColor(Color.BLACK); return }
        renderer.drawAt(canvas, width, height, ep, timeMs, fastVideo = true)
    }

    fun release() = renderer.release()
}

class EpisodeRenderer(private val density: Float) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER }
    private val rect = RectF()
    private val images = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val retrievers = HashMap<String, MediaMetadataRetriever>()

    fun drawAt(canvas: Canvas, w: Int, h: Int, ep: Episode, t: Long, fastVideo: Boolean) {
        canvas.drawColor(Color.BLACK)
        if (ep.intro.enabled && t < ep.introMs) { drawBookend(canvas, w, h, ep.intro, t, ep.introMs); return }
        val bodyT = t - ep.introMs
        if (ep.outro.enabled && bodyT >= ep.bodyMs) { drawBookend(canvas, w, h, ep.outro, bodyT - ep.bodyMs, ep.outroMs); return }

        val active = ep.clips.filter { bodyT >= it.startMs && bodyT < it.endMs }
        // Camadas visuais na ordem das faixas (faixas mais abaixo ficam por cima)
        for (c in active) {
            val local = bodyT - c.startMs
            val alpha = transitionAlpha(c, local)
            when (c.type) {
                TrackType.SCENE, TrackType.IMAGE -> drawImage(canvas, w, h, c, local, alpha)
                TrackType.VIDEO -> drawVideo(canvas, w, h, c, local, alpha, fastVideo)
                else -> {}
            }
        }
        for (c in active) {
            val local = bodyT - c.startMs
            when (c.type) {
                TrackType.VFX -> drawVfx(canvas, w, h, c, local)
                TrackType.TEXT -> drawText(canvas, w, h, c.text.ifBlank { c.label }, h * 0.5f, 0.075f, transitionAlpha(c, local))
                TrackType.DIALOGUE -> drawSubtitle(canvas, w, h, c.text.ifBlank { c.label })
                else -> {}
            }
        }
        if (active.none { it.type.visual } && active.none { it.type == TrackType.DIALOGUE }) {
            tp.textSize = h * 0.045f; tp.color = 0xFF50506A.toInt()
            canvas.drawText(if (ep.clips.isEmpty()) "Adicione cenas, imagens ou videos" else "—", w / 2f, h / 2f, tp)
        }
    }

    private fun transitionAlpha(c: Clip, local: Long): Int {
        if (c.transition != "fade") return 255
        val f = 400f
        val a = min(local / f, (c.durationMs - local) / f).coerceIn(0f, 1f)
        return (a * 255).toInt()
    }

    private fun drawBookend(canvas: Canvas, w: Int, h: Int, b: Bookend, local: Long, dur: Long) {
        canvas.drawColor(b.background)
        val fade = min(local / 500f, (dur - local) / 500f).coerceIn(0f, 1f)
        drawText(canvas, w, h, b.title.ifBlank { "Sem titulo" }, h * 0.47f, 0.09f, (fade * 255).toInt())
        if (b.subtitle.isNotBlank()) drawText(canvas, w, h, b.subtitle, h * 0.62f, 0.05f, (fade * 220).toInt())
    }

    private fun fit(bmp: Bitmap, w: Int, h: Int, scale: Float) {
        val s = minOf(w / bmp.width.toFloat(), h / bmp.height.toFloat()) * scale
        val bw = bmp.width * s; val bh = bmp.height * s
        rect.set((w - bw) / 2, (h - bh) / 2, (w + bw) / 2, (h + bh) / 2)
    }

    private fun zoomScale(c: Clip, local: Long): Float {
        val k = (local / c.durationMs.toFloat()).coerceIn(0f, 1f)
        return when (c.zoom) { "in" -> 1f + 0.18f * k; "out" -> 1.18f - 0.18f * k; else -> 1f }
    }

    private fun drawImage(canvas: Canvas, w: Int, h: Int, c: Clip, local: Long, alpha: Int) {
        if (c.file.isBlank()) {
            p.color = 0xFF1C1C28.toInt(); p.alpha = alpha; canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            drawText(canvas, w, h, c.label, h * 0.5f, 0.06f, alpha); return
        }
        val bmp = image(c.file) ?: return missing(canvas, w, h, c)
        fit(bmp, w, h, zoomScale(c, local)); p.alpha = alpha
        canvas.drawBitmap(bmp, null, rect, p); p.alpha = 255
    }

    private fun drawVideo(canvas: Canvas, w: Int, h: Int, c: Clip, local: Long, alpha: Int, fast: Boolean) {
        val r = retrievers.getOrPut(c.file) {
            MediaMetadataRetriever().apply { runCatching { setDataSource(c.file) } }
        }
        val bucket = if (fast) (local / 250) * 250 else local
        val key = "${c.file}#$bucket"
        val bmp = images.get(key) ?: runCatching {
            r.getFrameAtTime(bucket * 1000, if (fast) MediaMetadataRetriever.OPTION_CLOSEST_SYNC else MediaMetadataRetriever.OPTION_CLOSEST)
        }.getOrNull()?.also { if (fast) images.put(key, it) }
        if (bmp == null) return missing(canvas, w, h, c)
        fit(bmp, w, h, zoomScale(c, local)); p.alpha = alpha
        canvas.drawBitmap(bmp, null, rect, p); p.alpha = 255
    }

    private fun missing(canvas: Canvas, w: Int, h: Int, c: Clip) {
        p.color = 0xFF2A0D12.toInt(); canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        drawText(canvas, w, h, "Arquivo nao encontrado: ${c.label}", h * 0.5f, 0.045f, 255)
    }

    private fun drawVfx(canvas: Canvas, w: Int, h: Int, c: Clip, local: Long) {
        val k = local / c.durationMs.toFloat()
        when (c.text) {
            "flash" -> { p.color = Color.WHITE; p.alpha = ((1f - k) * 255).toInt().coerceIn(0, 255); canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p) }
            "escurecer" -> { p.color = Color.BLACK; p.alpha = (k * 255).toInt().coerceIn(0, 255); canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p) }
            "clarear" -> { p.color = Color.BLACK; p.alpha = ((1f - k) * 255).toInt().coerceIn(0, 255); canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p) }
            "vinheta" -> {
                p.shader = RadialGradient(w / 2f, h / 2f, w * 0.7f, intArrayOf(0x00000000, 0xCC000000.toInt()), floatArrayOf(0.5f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p); p.shader = null
            }
            "linhas de velocidade" -> {
                p.color = 0xAAFFFFFF.toInt(); p.strokeWidth = 2 * density
                for (i in 0 until 40) {
                    val a = i * 0.157f + local / 90f
                    val r1 = w * 0.35f; val r2 = w * 0.8f
                    canvas.drawLine(w / 2f + r1 * kotlin.math.cos(a), h / 2f + r1 * sin(a), w / 2f + r2 * kotlin.math.cos(a), h / 2f + r2 * sin(a), p)
                }
            }
            "sakura" -> {
                p.color = 0xFFFF9EC4.toInt()
                for (i in 0 until 24) {
                    val x = ((i * 97 + local / 12f) % w); val y = ((i * 53 + local / 7f) % h)
                    canvas.drawCircle(x, y, (3 + i % 3) * density, p)
                }
            }
            else -> { p.color = 0x3300FFFF; canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p) }
        }
    }

    private fun drawText(canvas: Canvas, w: Int, h: Int, text: String, y: Float, size: Float, alpha: Int) {
        tp.textSize = h * size; tp.color = Color.WHITE; tp.alpha = alpha
        tp.setShadowLayer(6 * density, 0f, 2 * density, Color.BLACK)
        canvas.drawText(text, w / 2f, y, tp)
        tp.clearShadowLayer(); tp.alpha = 255
    }

    private fun drawSubtitle(canvas: Canvas, w: Int, h: Int, text: String) {
        tp.textSize = h * 0.05f
        val tw = tp.measureText(text)
        rect.set(w / 2f - tw / 2 - 12 * density, h * 0.82f, w / 2f + tw / 2 + 12 * density, h * 0.82f + tp.textSize * 1.6f)
        p.color = 0xAA000000.toInt(); canvas.drawRoundRect(rect, 8 * density, 8 * density, p)
        tp.color = 0xFFFFF3B0.toInt(); canvas.drawText(text, w / 2f, rect.centerY() + tp.textSize * 0.35f, tp)
    }

    private fun image(path: String): Bitmap? {
        images.get(path)?.let { return it }
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        if (o.outWidth <= 0) return null
        var sample = 1
        while (o.outWidth / (sample * 2) >= 1280) sample *= 2
        val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        images.put(path, bmp); return bmp
    }

    fun invalidateFile(path: String) { images.remove(path) }

    fun release() {
        images.evictAll()
        retrievers.values.forEach { runCatching { it.release() } }
        retrievers.clear()
    }
}
