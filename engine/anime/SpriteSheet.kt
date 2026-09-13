package com.yumeka.anime.engine.anime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect

/**
 * SpriteSheet - gerencia um spritesheet de anime.
 * Extrai frames individuais de um Bitmap em grid.
 */
class SpriteSheet(
    private val bitmap: Bitmap,
    private val columns: Int,
    private val rows: Int
) {
    val frameWidth: Int = bitmap.width / columns
    val frameHeight: Int = bitmap.height / rows
    val totalFrames: Int = columns * rows

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Extrai um frame especifico do spritesheet. */
    fun getFrame(index: Int): Bitmap {
        val col = index % columns
        val row = index / columns
        val src = Rect(
            col * frameWidth,
            row * frameHeight,
            (col + 1) * frameWidth,
            (row + 1) * frameHeight
        )
        return Bitmap.createBitmap(bitmap, src.left, src.top, src.width(), src.height())
    }

    /** Desenha o frame no canvas na posição dada. */
    fun drawFrame(canvas: Canvas, index: Int, x: Float, y: Float) {
        val col = index % columns
        val row = index / columns
        val src = Rect(
            col * frameWidth,
            row * frameHeight,
            (col + 1) * frameWidth,
            (row + 1) * frameHeight
        )
        val dst = Rect(x.toInt(), y.toInt(), (x + frameWidth).toInt(), (y + frameHeight).toInt())
        canvas.drawBitmap(bitmap, src, dst, paint)
    }
}
