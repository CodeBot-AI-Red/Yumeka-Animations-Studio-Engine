package com.yumeka.anime.engine.anime

import android.os.Handler
import android.os.Looper

/**
 * Yumeka Animations Studio Engine
 * AnimePlayer - responsável por controlar a reprodução de cenas anime.
 *
 * Suporta frames estáticos (spritesheet), animações Lottie e sequências de imagem.
 */
class AnimePlayer(
    private var fps: Int = 24,
    private var loop: Boolean = true
) {

    private var currentFrame: Int = 0
    private var totalFrames: Int = 0
    private var isPlaying: Boolean = false
    private var onFrameChanged: ((frame: Int) -> Unit)? = null
    private var onComplete: (() -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            if (!isPlaying) return
            onFrameChanged?.invoke(currentFrame)
            currentFrame++
            if (currentFrame >= totalFrames) {
                if (loop) {
                    currentFrame = 0
                } else {
                    isPlaying = false
                    onComplete?.invoke()
                    return
                }
            }
            handler.postDelayed(this, (1000L / fps))
        }
    }

    fun load(frameCount: Int) {
        totalFrames = frameCount
        currentFrame = 0
    }

    fun play() {
        if (isPlaying) return
        isPlaying = true
        handler.post(tick)
    }

    fun pause() {
        isPlaying = false
        handler.removeCallbacks(tick)
    }

    fun stop() {
        pause()
        currentFrame = 0
    }

    fun seekTo(frame: Int) {
        currentFrame = frame.coerceIn(0, totalFrames - 1)
    }

    fun onFrame(listener: (frame: Int) -> Unit) { onFrameChanged = listener }
    fun onComplete(listener: () -> Unit) { onComplete = listener }
}
