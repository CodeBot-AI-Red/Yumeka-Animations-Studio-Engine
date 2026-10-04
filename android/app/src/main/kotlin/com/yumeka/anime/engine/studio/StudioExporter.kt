package com.yumeka.anime.engine.studio

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object StudioExporter {

    /** Lista de (cena, indice) na ordem de reproducao, respeitando o "hold" de cada quadro. */
    private fun sequence(p: StudioProject): List<Pair<StudioScene, Int>> {
        val out = mutableListOf<Pair<StudioScene, Int>>()
        for (s in p.scenes) for (i in s.frames.indices) repeat(s.frames[i].hold.coerceAtLeast(1)) { out.add(s to i) }
        return out
    }

    private fun safe(n: String) = n.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "anime" }

    fun exportPng(p: StudioProject, dir: File, progress: (Float) -> Unit): File {
        val out = File(dir, "${safe(p.name)}_quadros_${System.currentTimeMillis() / 1000}")
        out.mkdirs()
        val seq = sequence(p)
        seq.forEachIndexed { i, (s, idx) ->
            val b = FrameRenderer.renderBitmap(s, idx, 1280, 720)
            FileOutputStream(File(out, "quadro_%04d.png".format(i + 1))).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            b.recycle()
            progress((i + 1f) / seq.size)
        }
        return out
    }

    fun exportGif(p: StudioProject, dir: File, progress: (Float) -> Unit): File {
        val file = File(dir, "${safe(p.name)}_${System.currentTimeMillis() / 1000}.gif")
        val w = 480; val h = 270
        val seq = sequence(p)
        BufferedOutputStream(FileOutputStream(file)).use { os ->
            val gif = GifWriter(os, w, h)
            val delayCs = (100f / p.fps).toInt().coerceAtLeast(2)
            seq.forEachIndexed { i, (s, idx) ->
                val b = FrameRenderer.renderBitmap(s, idx, w, h)
                gif.addFrame(b, delayCs)
                b.recycle()
                progress((i + 1f) / seq.size)
            }
            gif.finish()
        }
        return file
    }

    fun exportMp4(p: StudioProject, dir: File, progress: (Float) -> Unit): File {
        if (Build.VERSION.SDK_INT < 23) throw IllegalStateException("Video MP4 requer Android 6.0 ou superior. Use GIF.")
        val file = File(dir, "${safe(p.name)}_${System.currentTimeMillis() / 1000}.mp4")
        val w = 1280; val h = 720
        val fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 6_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, p.fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = codec.createInputSurface()
        codec.start()
        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val info = MediaCodec.BufferInfo()
        var track = -1
        var started = false
        var written = 0L

        fun drain(end: Boolean) {
            if (end) codec.signalEndOfInputStream()
            var spins = 0
            while (true) {
                val i = codec.dequeueOutputBuffer(info, 10_000)
                if (i == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (!end || ++spins > 300) break else continue
                } else if (i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = muxer.addTrack(codec.outputFormat); muxer.start(); started = true
                } else if (i >= 0) {
                    val buf = codec.getOutputBuffer(i)
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (buf != null && info.size > 0 && started) {
                        info.presentationTimeUs = written * 1_000_000L / p.fps
                        written++
                        buf.position(info.offset); buf.limit(info.offset + info.size)
                        muxer.writeSampleData(track, buf, info)
                    }
                    codec.releaseOutputBuffer(i, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        }

        try {
            val seq = sequence(p)
            seq.forEachIndexed { i, (s, idx) ->
                val c = surface.lockHardwareCanvas()
                c.save()
                c.scale(w / ART_W.toFloat(), h / ART_H.toFloat())
                FrameRenderer.render(c, s, idx, true)
                c.restore()
                surface.unlockCanvasAndPost(c)
                drain(false)
                progress((i + 1f) / seq.size)
            }
            drain(true)
        } finally {
            try { codec.stop() } catch (_: Exception) {}
            codec.release(); surface.release()
            try { if (started) muxer.stop() } catch (_: Exception) {}
            muxer.release()
        }
        return file
    }
}

/** Codificador GIF89a animado (paleta fixa 6x6x6 + cinzas, com pontilhado ordenado). */
class GifWriter(private val os: OutputStream, private val w: Int, private val h: Int) {
    private val palette = ByteArray(256 * 3)
    private val bayer = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

    init {
        var k = 0
        for (r in 0..5) for (g in 0..5) for (b in 0..5) {
            palette[k * 3] = (r * 51).toByte(); palette[k * 3 + 1] = (g * 51).toByte(); palette[k * 3 + 2] = (b * 51).toByte(); k++
        }
        while (k < 256) { val v = ((k - 216) * 255 / 39); palette[k * 3] = v.toByte(); palette[k * 3 + 1] = v.toByte(); palette[k * 3 + 2] = v.toByte(); k++ }
        os.write("GIF89a".toByteArray())
        short(w); short(h)
        os.write(0xF7); os.write(0); os.write(0)
        os.write(palette)
        // loop infinito
        os.write(byteArrayOf(0x21, 0xFF.toByte(), 0x0B)); os.write("NETSCAPE2.0".toByteArray())
        os.write(byteArrayOf(3, 1, 0, 0, 0))
    }

    private fun short(v: Int) { os.write(v and 0xFF); os.write((v shr 8) and 0xFF) }

    fun addFrame(b: Bitmap, delayCs: Int) {
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        val idx = ByteArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val c = px[y * w + x]
            val d = (bayer[(y and 3) * 4 + (x and 3)] - 8) * 3
            val r = (((c shr 16) and 0xFF) + d).coerceIn(0, 255)
            val g = (((c shr 8) and 0xFF) + d).coerceIn(0, 255)
            val bl = ((c and 0xFF) + d).coerceIn(0, 255)
            idx[y * w + x] = (((r + 25) / 51) * 36 + ((g + 25) / 51) * 6 + ((bl + 25) / 51)).toByte()
        }
        os.write(byteArrayOf(0x21, 0xF9.toByte(), 4, 0)); short(delayCs); os.write(0); os.write(0)
        os.write(0x2C); short(0); short(0); short(w); short(h); os.write(0)
        lzw(idx)
    }

    private fun lzw(data: ByteArray) {
        val minCode = 8
        os.write(minCode)
        val clear = 1 shl minCode
        val eoi = clear + 1
        val block = ByteArray(255)
        var blockLen = 0
        var bitBuf = 0; var bitCount = 0
        fun flushBlock() { if (blockLen > 0) { os.write(blockLen); os.write(block, 0, blockLen); blockLen = 0 } }
        fun emit(code: Int, size: Int) {
            bitBuf = bitBuf or (code shl bitCount); bitCount += size
            while (bitCount >= 8) { block[blockLen++] = (bitBuf and 0xFF).toByte(); bitBuf = bitBuf ushr 8; bitCount -= 8; if (blockLen == 255) flushBlock() }
        }
        val dict = HashMap<Int, Int>(8192)
        var codeSize = minCode + 1
        var next = eoi + 1
        emit(clear, codeSize)
        var prefix = data[0].toInt() and 0xFF
        for (i in 1 until data.size) {
            val k = data[i].toInt() and 0xFF
            val key = (prefix shl 8) or k
            val found = dict[key]
            if (found != null) { prefix = found; continue }
            emit(prefix, codeSize)
            if (next < 4096) {
                dict[key] = next++
                if (next > (1 shl codeSize) && codeSize < 12) codeSize++
            } else {
                emit(clear, codeSize); dict.clear(); codeSize = minCode + 1; next = eoi + 1
            }
            prefix = k
        }
        emit(prefix, codeSize)
        emit(eoi, codeSize)
        if (bitCount > 0) { block[blockLen++] = (bitBuf and 0xFF).toByte(); if (blockLen == 255) flushBlock() }
        flushBlock()
        os.write(0)
    }

    fun finish() { os.write(0x3B); os.flush() }
}
