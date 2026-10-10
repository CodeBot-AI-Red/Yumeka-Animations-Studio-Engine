package com.yumeka.anime.engine.studio

import android.graphics.Bitmap

/** Resolucao interna quadrada de cada quadro. */
const val ART_W = 1024
const val ART_H = 1024

class StudioProject(var name: String) {
    var fps = 12
    val scenes = mutableListOf<StudioScene>()
    var nextId = 1
    fun newId() = nextId++
}

class StudioScene(val id: Int, var name: String) {
    var background = 1
    var shake = false
    val layers = mutableListOf<LayerInfo>()
    val frames = mutableListOf<StudioFrame>()
    val characters = mutableListOf<StudioCharacter>()
    val camKeys = mutableMapOf<Int, CamKey>()

    fun <T> interp(keys: Map<Int, T>, idx: Int, default: T, lerp: (T, T, Float) -> T): T {
        if (keys.isEmpty() || frames.isEmpty()) return default
        var prevI = -1
        var nextI = -1
        for (i in frames.indices) {
            if (keys.containsKey(frames[i].id)) {
                if (i <= idx) prevI = i
                if (i >= idx && nextI == -1) nextI = i
            }
        }
        if (prevI == -1 && nextI == -1) return default
        if (prevI == -1) return keys[frames[nextI].id]!!
        if (nextI == -1 || prevI == nextI) return keys[frames[prevI].id]!!
        val t = (idx - prevI).toFloat() / (nextI - prevI).toFloat()
        return lerp(keys[frames[prevI].id]!!, keys[frames[nextI].id]!!, t)
    }

    fun cameraAt(idx: Int): CamKey = interp(camKeys, idx, CamKey()) { a, b, t ->
        CamKey(lerpF(a.zoom, b.zoom, t), lerpF(a.x, b.x, t), lerpF(a.y, b.y, t))
    }
}

class LayerInfo(
    val id: Int,
    var name: String,
    var visible: Boolean = true,
    var opacity: Int = 255,
    var aboveCharacters: Boolean = true
)

class StudioFrame(val id: Int) {
    var hold = 1
    val art = mutableMapOf<Int, Bitmap>()
}

data class CamKey(var zoom: Float = 1f, var x: Float = 0.5f, var y: Float = 0.5f)

data class Pose(
    var x: Float = 0.5f,
    var y: Float = 0.72f,
    var scale: Float = 1f,
    var flip: Boolean = false,
    var head: Float = 0f,
    var torso: Float = 0f,
    var armL: Float = 15f,
    var armR: Float = -15f,
    var legL: Float = 4f,
    var legR: Float = -4f,
    var expression: Int = 0,
    var mouth: Float = 0f,
    var blink: Boolean = false
)

class StudioCharacter(
    val id: Int,
    var name: String,
    var hairColor: Int,
    var skin: Int,
    var outfit: Int,
    var eyeColor: Int,
    var hairStyle: Int
) {
    val keys = mutableMapOf<Int, Pose>()

    fun poseAt(scene: StudioScene, idx: Int): Pose = scene.interp(keys, idx, Pose()) { a, b, t ->
        Pose(
            lerpF(a.x, b.x, t), lerpF(a.y, b.y, t), lerpF(a.scale, b.scale, t),
            if (t < 0.5f) a.flip else b.flip,
            lerpF(a.head, b.head, t), lerpF(a.torso, b.torso, t),
            lerpF(a.armL, b.armL, t), lerpF(a.armR, b.armR, t),
            lerpF(a.legL, b.legL, t), lerpF(a.legR, b.legR, t),
            if (t < 0.5f) a.expression else b.expression,
            lerpF(a.mouth, b.mouth, t),
            if (t < 0.5f) a.blink else b.blink
        )
    }
}

fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t

object Presets {
    val hair = intArrayOf(0xFF2B2B3A.toInt(), 0xFFE8445A.toInt(), 0xFFF5D06B.toInt(), 0xFF6EA8FF.toInt(), 0xFFB57BFF.toInt(), 0xFFF2F2F7.toInt(), 0xFF7A4A2A.toInt())
    val skin = intArrayOf(0xFFFFE3D1.toInt(), 0xFFF2C6A5.toInt(), 0xFFD49A73.toInt(), 0xFF8D5A3B.toInt())
    val outfit = intArrayOf(0xFF1F2A55.toInt(), 0xFFC9142B.toInt(), 0xFF2E8B57.toInt(), 0xFFF2F2F7.toInt(), 0xFF2B2B3A.toInt(), 0xFFFF8A3D.toInt())
    val eyes = intArrayOf(0xFF6E3BFF.toInt(), 0xFFE8445A.toInt(), 0xFF2EA8FF.toInt(), 0xFF3FBF6F.toInt(), 0xFFFBBF24.toInt())
    val backgrounds = listOf("Branco", "Ceu", "Por do sol", "Noite", "Cidade", "Sala de aula", "Sakura", "Chroma")
    val hairStyles = listOf("Curto", "Longo", "Maria-chiquinha", "Chanel")
    val expressions = listOf("Neutro", "Feliz", "Bravo", "Triste", "Surpreso")
    val names = listOf("Yumi", "Kenji", "Aiko", "Ren", "Hana", "Sora")

    fun character(p: StudioProject, n: Int): StudioCharacter = StudioCharacter(
        p.newId(), names[n % names.size],
        hair[n % hair.size], skin[n % skin.size], outfit[n % outfit.size], eyes[n % eyes.size], n % hairStyles.size
    )

    /** Cena nova: em branco, 1 quadro vazio, fundo branco, sem personagens. */
    fun newScene(p: StudioProject, name: String, frames: Int = 1): StudioScene {
        val s = StudioScene(p.newId(), name)
        s.background = 0
        s.layers.add(LayerInfo(p.newId(), "Fundo", aboveCharacters = false))
        s.layers.add(LayerInfo(p.newId(), "Cor"))
        s.layers.add(LayerInfo(p.newId(), "Linha"))
        repeat(frames) { s.frames.add(StudioFrame(p.newId())) }
        return s
    }

    /** Projeto novo: totalmente em branco — o usuario cria tudo. */
    fun newProject(name: String): StudioProject {
        val p = StudioProject(name)
        p.scenes.add(newScene(p, "Cena 1"))
        return p
    }

    /** Personagem novo criado pelo usuario, com aparencia neutra para ele personalizar. */
    fun blankCharacter(p: StudioProject, name: String): StudioCharacter =
        StudioCharacter(p.newId(), name, hair[0], skin[0], outfit[3], eyes[2], 0)
}
