package com.yumeka.anime.engine.episode

import org.junit.Assert.*
import org.junit.Test

class SequenceContractTest {
    private fun clip() = Clip(1, TrackType.SEQUENCE, 500, 1000, "Desenhos",
        frames = listOf(SequenceFrame("a.png", 200), SequenceFrame("b.png", 600), SequenceFrame("c.png", 1000)),
        sourceKey = "studio-drawings")

    @Test fun orderedFramesRespectHoldBoundaries() {
        val c = clip()
        assertEquals("a.png", c.frameAt(0)?.file)
        assertEquals("a.png", c.frameAt(199)?.file)
        assertEquals("b.png", c.frameAt(200)?.file)
        assertEquals("c.png", c.frameAt(600)?.file)
        assertEquals("c.png", c.frameAt(999)?.file)
    }
    @Test fun splitContinuesFromOriginalFrameInsteadOfRestarting() {
        val c = clip()
        val right = c.splitAt(2, 800) ?: error("Split failed")
        assertEquals(300L, right.sourceOffsetMs)
        assertEquals(700L, right.durationMs)
        assertEquals("b.png", right.frameAt(0)?.file)
        assertEquals("c.png", right.frameAt(300)?.file)
        assertEquals("c.png", right.copy(id = 3).frameAt(300)?.file)
        assertNull(c.splitAt(2, c.startMs))
        assertNull(c.splitAt(2, c.endMs))
    }
    @Test fun trimmedSequenceSurvivesSerializationAndUndo() {
        val c = clip().apply { sourceOffsetMs = 200; durationMs = 800 }
        val ep = Episode("Test").apply { clips.add(c) }
        val history = EpisodeHistory()
        history.push(ep)
        ep.clips.clear()
        val restored = history.undo(ep) ?: error("Undo failed")
        val read = restored.clips.single()
        assertEquals("studio-drawings", read.sourceKey)
        assertEquals(200L, read.sourceOffsetMs)
        assertEquals("b.png", read.frameAt(0)?.file)
        assertEquals(c.frames, read.frames)
    }
    @Test fun ordinaryClipsRemainBackwardCompatible() {
        val c = Clip(1, TrackType.IMAGE, 0, 3000, "Old image")
        assertNull(c.frameAt(0))
        assertEquals(200L, c.minimumDurationMs)
        assertTrue(Clip.fromJson(c.toJson()).frames.isEmpty())
    }
}
