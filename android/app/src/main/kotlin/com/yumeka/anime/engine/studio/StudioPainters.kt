package com.yumeka.anime.engine.studio

import android.graphics.*
import kotlin.math.sin
import kotlin.random.Random

object Backgrounds {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun vGrad(c: Canvas, top: Int, bottom: Int, y0: Float, y1: Float, w: Float) {
        p.shader = LinearGradient(0f, y0, 0f, y1, top, bottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, y0, w, y1, p)
        p.shader = null
    }

    private fun cloud(c: Canvas, x: Float, y: Float, s: Float, color: Int) {
        p.color = color
        c.drawCircle(x, y, 28f * s, p); c.drawCircle(x + 30f * s, y - 12f * s, 34f * s, p)
        c.drawCircle(x + 64f * s, y, 26f * s, p); c.drawRoundRect(x - 20f * s, y, x + 86f * s, y + 22f * s, 20f * s, 20f * s, p)
    }

    fun draw(c: Canvas, type: Int, tick: Int) {
        val w = ART_W.toFloat(); val h = ART_H.toFloat()
        p.style = Paint.Style.FILL; p.shader = null; p.alpha = 255
        when (type) {
            1 -> {
                vGrad(c, 0xFF5AB4FF.toInt(), 0xFFD6EEFF.toInt(), 0f, h * .75f, w)
                val drift = (tick * 1.5f) % (w + 300)
                cloud(c, 120f + drift * .3f, 110f, 1.2f, Color.WHITE)
                cloud(c, 560f + drift * .2f, 70f, .9f, 0xF0FFFFFF.toInt())
                cloud(c, 780f - drift * .15f, 170f, .7f, 0xE0FFFFFF.toInt())
                p.color = 0xFF7CCB6B.toInt(); c.drawRect(0f, h * .72f, w, h, p)
                p.color = 0xFF63B356.toInt(); c.drawOval(-200f, h * .66f, w * .6f, h * 1.1f, p)
            }
            2 -> {
                vGrad(c, 0xFF2B1A55.toInt(), 0xFFFF8A5C.toInt(), 0f, h * .7f, w)
                p.color = 0xFFFFD27A.toInt(); c.drawCircle(w * .62f, h * .62f, 70f, p)
                p.color = 0xFF3A2244.toInt()
                val path = Path().apply { moveTo(0f, h * .7f); lineTo(180f, h * .45f); lineTo(360f, h * .66f); lineTo(560f, h * .4f); lineTo(800f, h * .68f); lineTo(w, h * .5f); lineTo(w, h); lineTo(0f, h); close() }
                c.drawPath(path, p)
                p.color = 0xFF1E1230.toInt(); c.drawRect(0f, h * .78f, w, h, p)
            }
            3 -> {
                vGrad(c, 0xFF070816.toInt(), 0xFF1D2350.toInt(), 0f, h, w)
                val r = Random(7)
                for (i in 0 until 90) {
                    val tw = ((sin((tick + i) * .5) + 1) * 0.5).toFloat()
                    p.color = Color.argb((120 + 135 * tw).toInt(), 255, 255, 255)
                    c.drawCircle(r.nextFloat() * w, r.nextFloat() * h * .7f, 1f + r.nextFloat() * 2f, p)
                }
                p.color = 0xFFFFF4D6.toInt(); c.drawCircle(w * .8f, h * .2f, 46f, p)
                p.color = 0xFF0E1230.toInt(); c.drawCircle(w * .8f + 18f, h * .2f - 10f, 40f, p)
                p.color = 0xFF0B0E22.toInt(); c.drawRect(0f, h * .8f, w, h, p)
            }
            4 -> {
                vGrad(c, 0xFF7FB2FF.toInt(), 0xFFFFD6E8.toInt(), 0f, h, w)
                val r = Random(3)
                var x = 0f
                while (x < w) {
                    val bw = 60f + r.nextFloat() * 70f; val bh = h * (.3f + r.nextFloat() * .45f)
                    p.color = if (r.nextBoolean()) 0xFF3B4B7A.toInt() else 0xFF2E3A62.toInt()
                    c.drawRect(x, h - bh, x + bw, h, p)
                    p.color = 0xFFFFE89A.toInt()
                    var wy = h - bh + 14f
                    while (wy < h - 20f) {
                        var wx = x + 10f
                        while (wx < x + bw - 14f) { if (r.nextFloat() > .45f) c.drawRect(wx, wy, wx + 8f, wy + 10f, p); wx += 16f }
                        wy += 20f
                    }
                    x += bw + 6f
                }
                p.color = 0xFF555A6E.toInt(); c.drawRect(0f, h * .9f, w, h, p)
            }
            5 -> {
                p.color = 0xFFF4E9D8.toInt(); c.drawRect(0f, 0f, w, h, p)
                p.color = 0xFF2F5D46.toInt(); c.drawRect(w * .3f, h * .12f, w * .85f, h * .5f, p)
                p.color = 0xFF8B5E3C.toInt(); c.drawRect(w * .3f, h * .5f, w * .85f, h * .52f, p)
                p.color = 0xFF9FD3FF.toInt(); c.drawRect(w * .04f, h * .12f, w * .22f, h * .55f, p)
                p.color = 0xFFF4E9D8.toInt(); c.drawRect(w * .125f, h * .12f, w * .135f, h * .55f, p)
                c.drawRect(w * .04f, h * .33f, w * .22f, h * .34f, p)
                p.color = 0xFFC79A6B.toInt(); c.drawRect(0f, h * .75f, w, h, p)
                p.color = 0xFFB0855A.toInt()
                var lx = 0f
                while (lx < w) { c.drawRect(lx, h * .75f, lx + 2f, h, p); lx += 80f }
                p.color = Color.WHITE; p.textSize = 30f
                c.drawText("Yumeka", w * .36f, h * .25f, p)
            }
            6 -> {
                vGrad(c, 0xFFFFD6E5.toInt(), 0xFFFFF4F8.toInt(), 0f, h * .7f, w)
                p.color = 0xFF9AD08A.toInt(); c.drawRect(0f, h * .72f, w, h, p)
                p.color = 0xFF6B4A3A.toInt(); c.drawRect(w * .12f, h * .3f, w * .16f, h * .78f, p)
                p.color = 0xFFFFB3CB.toInt()
                c.drawCircle(w * .14f, h * .26f, 110f, p); c.drawCircle(w * .06f, h * .34f, 70f, p); c.drawCircle(w * .24f, h * .34f, 80f, p)
                val r = Random(11)
                p.color = 0xFFFF8FB1.toInt()
                for (i in 0 until 40) {
                    val bx = r.nextFloat() * w; val by = r.nextFloat() * h; val sp = 2f + r.nextFloat() * 3f
                    val px = (bx + tick * sp * 1.5f) % w; val py = (by + tick * sp) % h
                    c.drawOval(px, py, px + 9f, py + 5f, p)
                }
            }
            7 -> { p.color = 0xFF00C040.toInt(); c.drawRect(0f, 0f, w, h, p) }
            else -> { p.color = Color.WHITE; c.drawRect(0f, 0f, w, h, p) }
        }
    }
}

object CharacterPainter {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = 0xFF1A1A26.toInt() }

    private fun darker(c: Int, f: Float = .8f) = Color.argb(Color.alpha(c), (Color.red(c) * f).toInt(), (Color.green(c) * f).toInt(), (Color.blue(c) * f).toInt())

    /** Retangulo aproximado do personagem (para selecao/arrastar). */
    fun bounds(pose: Pose): RectF {
        val u = ART_H * .55f * pose.scale / 8f
        val cx = pose.x * ART_W; val cy = pose.y * ART_H
        return RectF(cx - 2.2f * u, cy - 6.6f * u, cx + 2.2f * u, cy + 3.6f * u)
    }

    fun draw(c: Canvas, ch: StudioCharacter, pose: Pose, selected: Boolean = false) {
        val u = ART_H * .55f * pose.scale / 8f
        c.save()
        c.translate(pose.x * ART_W, pose.y * ART_H)
        if (pose.flip) c.scale(-1f, 1f)
        line.strokeWidth = u * .12f
        p.style = Paint.Style.FILL

        // sombra
        p.color = 0x33000000
        c.drawOval(-1.6f * u, 3.1f * u, 1.6f * u, 3.6f * u, p)

        // pernas
        limb(c, -.45f * u, 0f, pose.legL, 3.2f * u, .72f * u, darker(ch.outfit, .7f), 0xFF2A2A36.toInt(), u)
        limb(c, .45f * u, 0f, pose.legR, 3.2f * u, .72f * u, darker(ch.outfit, .7f), 0xFF2A2A36.toInt(), u)

        c.save()
        c.rotate(pose.torso)
        // braco de tras
        limb(c, .85f * u, -2.6f * u, pose.armR, 2.6f * u, .55f * u, ch.outfit, ch.skin, u, hand = true)
        // tronco
        val torso = Path().apply {
            moveTo(-.8f * u, -2.85f * u); lineTo(.8f * u, -2.85f * u)
            quadTo(.95f * u, -1.2f * u, .7f * u, .1f * u); lineTo(-.7f * u, .1f * u)
            quadTo(-.95f * u, -1.2f * u, -.8f * u, -2.85f * u); close()
        }
        p.color = ch.outfit; c.drawPath(torso, p); c.drawPath(torso, line)
        p.color = darker(ch.outfit, .75f); c.drawRect(-.72f * u, -.6f * u, .72f * u, -.35f * u, p)
        // gola
        p.color = Color.WHITE
        c.drawPath(Path().apply { moveTo(-.4f * u, -2.85f * u); lineTo(0f, -2.3f * u); lineTo(.4f * u, -2.85f * u); close() }, p)
        // pescoco
        p.color = ch.skin; c.drawRect(-.22f * u, -3.2f * u, .22f * u, -2.8f * u, p)
        // cabeca
        c.save()
        c.translate(0f, -3f * u)
        c.rotate(pose.head)
        head(c, ch, pose, u)
        c.restore()
        // braco da frente
        limb(c, -.85f * u, -2.6f * u, pose.armL, 2.6f * u, .55f * u, ch.outfit, ch.skin, u, hand = true)
        c.restore()

        if (selected) {
            val sel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 3f; color = 0xFFFF6080.toInt()
                pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
            }
            c.drawRoundRect(-2.2f * u, -6.6f * u, 2.2f * u, 3.6f * u, 16f, 16f, sel)
        }
        c.restore()
    }

    private fun limb(c: Canvas, x: Float, y: Float, angle: Float, len: Float, wdt: Float, color: Int, end: Int, u: Float, hand: Boolean = false) {
        c.save()
        c.translate(x, y)
        c.rotate(angle)
        val r = RectF(-wdt / 2, 0f, wdt / 2, len)
        p.color = if (hand) end else color
        c.drawRoundRect(r, wdt / 2, wdt / 2, p)
        if (hand) { p.color = color; c.drawRoundRect(-wdt / 2 - 2f, 0f, wdt / 2 + 2f, len * .55f, wdt / 2, wdt / 2, p) }
        c.drawRoundRect(r, wdt / 2, wdt / 2, line)
        if (hand) { p.color = end; c.drawCircle(0f, len, wdt * .55f, p); c.drawCircle(0f, len, wdt * .55f, line) }
        else { p.color = end; c.drawOval(-wdt * .7f, len - wdt * .3f, wdt * .9f, len + wdt * .35f, p) }
        c.restore()
    }

    private fun head(c: Canvas, ch: StudioCharacter, pose: Pose, u: Float) {
        val r = 1.3f * u
        val cy = -r * .95f
        // cabelo de tras
        p.color = darker(ch.hairColor, .85f)
        when (ch.hairStyle) {
            1 -> c.drawRoundRect(-r * 1.1f, cy - r * .4f, r * 1.1f, cy + r * 2.4f, r * .8f, r * .8f, p)
            2 -> { c.drawOval(-r * 1.9f, cy - r * .3f, -r * .8f, cy + r * 2.2f, p); c.drawOval(r * .8f, cy - r * .3f, r * 1.9f, cy + r * 2.2f, p) }
            3 -> c.drawRoundRect(-r * 1.15f, cy - r * .6f, r * 1.15f, cy + r * 1.05f, r * .6f, r * .6f, p)
        }
        p.color = ch.hairColor; c.drawCircle(0f, cy - r * .08f, r * 1.12f, p)
        // rosto
        p.color = ch.skin
        val face = Path().apply {
            moveTo(-r * .95f, cy - r * .2f)
            quadTo(-r, cy + r * .6f, 0f, cy + r * 1.05f)
            quadTo(r, cy + r * .6f, r * .95f, cy - r * .2f)
            arcTo(RectF(-r * .95f, cy - r * 1.1f, r * .95f, cy + r * .7f), 0f, -180f)
            close()
        }
        c.drawPath(face, p); c.drawPath(face, line)

        // olhos
        val ey = cy + r * .22f
        for (side in intArrayOf(-1, 1)) {
            val ex = side * r * .42f
            if (pose.blink || pose.expression == 1) {
                val arc = RectF(ex - r * .2f, ey - r * .12f, ex + r * .2f, ey + r * .18f)
                line.strokeWidth = u * .14f
                if (pose.blink) c.drawLine(ex - r * .2f, ey + r * .05f, ex + r * .2f, ey + r * .05f, line)
                else c.drawArc(arc, 200f, 140f, false, line)
            } else {
                val ew = if (pose.expression == 4) r * .19f else r * .17f
                p.color = Color.WHITE; c.drawOval(ex - ew, ey - r * .25f, ex + ew, ey + r * .2f, p)
                p.color = ch.eyeColor; c.drawOval(ex - ew * .8f, ey - r * .2f, ex + ew * .8f, ey + r * .2f, p)
                p.color = darker(ch.eyeColor, .4f); c.drawOval(ex - ew * .4f, ey - r * .08f, ex + ew * .4f, ey + r * .14f, p)
                p.color = Color.WHITE; c.drawCircle(ex - ew * .3f, ey - r * .08f, r * .06f, p)
                line.strokeWidth = u * .16f
                c.drawLine(ex - ew * 1.1f, ey - r * .26f, ex + ew * 1.1f, ey - r * .28f, line)
            }
            // sobrancelhas
            line.strokeWidth = u * .09f
            val by = ey - r * .45f
            when (pose.expression) {
                2 -> c.drawLine(ex - side * r * .2f, by - r * .1f, ex + side * r * .05f, by + r * .08f, line)
                3 -> c.drawLine(ex - side * r * .2f, by + r * .08f, ex + side * r * .15f, by - r * .08f, line)
                4 -> c.drawLine(ex - r * .16f, by - r * .12f, ex + r * .16f, by - r * .12f, line)
                else -> c.drawLine(ex - r * .16f, by, ex + r * .16f, by, line)
            }
        }
        // bochechas
        if (pose.expression == 1 || pose.expression == 4) {
            p.color = 0x55FF6080
            c.drawOval(-r * .78f, ey + r * .22f, -r * .45f, ey + r * .36f, p)
            c.drawOval(r * .45f, ey + r * .22f, r * .78f, ey + r * .36f, p)
        }
        // boca
        val my = cy + r * .68f
        val open = pose.mouth.coerceIn(0f, 1f)
        line.strokeWidth = u * .09f
        if (open > .05f || pose.expression == 4) {
            val mh = r * (.06f + .22f * maxOf(open, if (pose.expression == 4) .6f else 0f))
            p.color = 0xFF7A1F2E.toInt(); c.drawOval(-r * .14f, my - mh / 2, r * .14f, my + mh / 2, p)
            c.drawOval(-r * .14f, my - mh / 2, r * .14f, my + mh / 2, line)
        } else when (pose.expression) {
            1 -> c.drawArc(RectF(-r * .18f, my - r * .14f, r * .18f, my + r * .06f), 20f, 140f, false, line)
            3, 2 -> c.drawArc(RectF(-r * .14f, my, r * .14f, my + r * .14f), 200f, 140f, false, line)
            else -> c.drawLine(-r * .09f, my, r * .09f, my, line)
        }
        // franja
        p.color = ch.hairColor
        val bangs = Path().apply {
            moveTo(-r * 1.1f, cy - r * .05f)
            val n = 6
            for (i in 0..n) {
                val x = -r * 1.1f + (2.2f * r) * i / n
                val tipY = if (ch.hairStyle == 0) cy - r * .05f else cy - r * .2f
                lineTo(x - r * .18f, cy - r * .55f)
                lineTo(x, if (i % 2 == 0) tipY + r * .05f else tipY - r * .1f)
            }
            lineTo(r * 1.1f, cy - r * .8f)
            arcTo(RectF(-r * 1.12f, cy - r * 1.2f, r * 1.12f, cy + r * .3f), 0f, -180f)
            close()
        }
        c.drawPath(bangs, p)
        if (ch.hairStyle == 0) {
            c.drawPath(Path().apply { moveTo(-r * .3f, cy - r); lineTo(0f, cy - r * 1.6f); lineTo(r * .3f, cy - r); close() }, p)
        }
        p.color = 0x55FFFFFF; c.drawArc(RectF(-r * .7f, cy - r * 1f, r * .5f, cy - r * .6f), 200f, 80f, false, p)
    }
}

object FrameRenderer {
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    fun drawContent(c: Canvas, s: StudioScene, idx: Int, selectedChar: Int = -1) {
        val f = s.frames.getOrNull(idx) ?: return
        Backgrounds.draw(c, s.background, idx)
        for (l in s.layers) if (!l.aboveCharacters) drawLayer(c, f, l)
        for (ch in s.characters) CharacterPainter.draw(c, ch, ch.poseAt(s, idx), ch.id == selectedChar)
        for (l in s.layers) if (l.aboveCharacters) drawLayer(c, f, l)
    }

    private fun drawLayer(c: Canvas, f: StudioFrame, l: LayerInfo) {
        if (!l.visible) return
        val b = f.art[l.id] ?: return
        bmpPaint.alpha = l.opacity
        c.drawBitmap(b, 0f, 0f, bmpPaint)
    }

    fun applyCamera(c: Canvas, s: StudioScene, idx: Int) {
        val cam = s.cameraAt(idx)
        c.translate(ART_W / 2f, ART_H / 2f)
        c.scale(cam.zoom, cam.zoom)
        var sx = 0f; var sy = 0f
        if (s.shake) { sx = (sin(idx * 2.3) * 6).toFloat(); sy = (sin(idx * 3.7) * 5).toFloat() }
        c.translate(-cam.x * ART_W + sx, -cam.y * ART_H + sy)
    }

    fun render(c: Canvas, s: StudioScene, idx: Int, camera: Boolean) {
        c.save()
        c.clipRect(0f, 0f, ART_W.toFloat(), ART_H.toFloat())
        if (camera) applyCamera(c, s, idx)
        drawContent(c, s, idx)
        c.restore()
    }

    fun renderBitmap(s: StudioScene, idx: Int, w: Int, h: Int, camera: Boolean = true): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.scale(w / ART_W.toFloat(), h / ART_H.toFloat())
        render(c, s, idx, camera)
        return b
    }
}
