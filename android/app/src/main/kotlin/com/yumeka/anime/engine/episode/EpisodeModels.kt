package com.yumeka.anime.engine.episode

import org.json.JSONArray
import org.json.JSONObject

/** Tipos de faixa da linha do tempo do episodio. Cada item adicionado vira uma faixa propria. */
enum class TrackType(val label: String, val icon: String, val color: Int, val visual: Boolean) {
    SCENE("Cena", "🎬", 0xFFE8445A.toInt(), true),
    IMAGE("Imagem", "🖼", 0xFF9B6DFF.toInt(), true),
    VIDEO("Video", "📹", 0xFFFF5C8A.toInt(), true),
    TEXT("Texto", "🅣", 0xFFFBBF24.toInt(), true),
    VFX("Efeito visual", "✨", 0xFF6EE7F9.toInt(), true),
    DIALOGUE("Fala", "💬", 0xFFFF8FB1.toInt(), false),
    AUDIO("Audio", "🎙", 0xFF2EA8FF.toInt(), false),
    MUSIC("Musica", "🎵", 0xFF4ADE80.toInt(), false),
    SFX("Efeito sonoro", "🔊", 0xFFFF8A3D.toInt(), false);
}

/** Metadados de um quadro gerado por IA. Sempre salvos junto do quadro. */
data class FrameMeta(
    var prompt: String = "",
    var negativePrompt: String = "",
    var seed: Long = 0,
    var model: String = "",
    var width: Int = 0,
    var height: Int = 0,
    var steps: Int = 0,
    var guidance: Float = 1f,
    var createdAt: Long = System.currentTimeMillis(),
    var analysis: FrameAnalysis? = null
) {
    fun toJson(): JSONObject = JSONObject()
        .put("prompt", prompt).put("negativePrompt", negativePrompt).put("seed", seed)
        .put("model", model).put("width", width).put("height", height).put("steps", steps)
        .put("guidance", guidance.toDouble()).put("createdAt", createdAt)
        .put("analysis", analysis?.toJson() ?: JSONObject.NULL)

    companion object {
        fun fromJson(o: JSONObject) = FrameMeta(
            o.optString("prompt"), o.optString("negativePrompt"), o.optLong("seed"),
            o.optString("model"), o.optInt("width"), o.optInt("height"), o.optInt("steps"),
            o.optDouble("guidance", 1.0).toFloat(), o.optLong("createdAt"),
            o.optJSONObject("analysis")?.let { FrameAnalysis.fromJson(it) }
        )
    }
}

/** Resultado da analise do Moondream2. */
data class FrameAnalysis(
    val description: String,
    val elements: String,
    val problems: String,
    val quality: String,
    val suggestions: String,
    val correctedPrompt: String,
    val analyzedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject()
        .put("description", description).put("elements", elements).put("problems", problems)
        .put("quality", quality).put("suggestions", suggestions)
        .put("correctedPrompt", correctedPrompt).put("analyzedAt", analyzedAt)

    companion object {
        fun fromJson(o: JSONObject) = FrameAnalysis(
            o.optString("description"), o.optString("elements"), o.optString("problems"),
            o.optString("quality"), o.optString("suggestions"), o.optString("correctedPrompt"),
            o.optLong("analyzedAt")
        )
    }
}

data class Clip(
    val id: Long,
    var type: TrackType,
    var startMs: Long,
    var durationMs: Long,
    var label: String,
    /** Caminho absoluto de um arquivo local (imagem, video ou audio), ou vazio. */
    var file: String = "",
    /** Texto exibido (texto na tela, fala) ou tipo do efeito visual. */
    var text: String = "",
    /** "none", "in" (aproximar) ou "out" (afastar). */
    var zoom: String = "none",
    /** "none", "fade", "flash", "slide". */
    var transition: String = "none",
    var volume: Float = 1f,
    var meta: FrameMeta? = null
) {
    val endMs get() = startMs + durationMs

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("type", type.name).put("startMs", startMs).put("durationMs", durationMs)
        .put("label", label).put("file", file).put("text", text).put("zoom", zoom)
        .put("transition", transition).put("volume", volume.toDouble())
        .put("meta", meta?.toJson() ?: JSONObject.NULL)

    companion object {
        fun fromJson(o: JSONObject) = Clip(
            o.getLong("id"),
            runCatching { TrackType.valueOf(o.getString("type")) }.getOrDefault(TrackType.IMAGE),
            o.optLong("startMs"), o.optLong("durationMs", 3000), o.optString("label"),
            o.optString("file"), o.optString("text"), o.optString("zoom", "none"),
            o.optString("transition", "none"), o.optDouble("volume", 1.0).toFloat(),
            o.optJSONObject("meta")?.let { FrameMeta.fromJson(it) }
        )
    }
}

data class Bookend(
    var enabled: Boolean = false,
    var title: String = "",
    var subtitle: String = "",
    var durationMs: Long = 3000,
    var background: Int = 0xFF0B0B12.toInt()
) {
    fun toJson(): JSONObject = JSONObject().put("enabled", enabled).put("title", title)
        .put("subtitle", subtitle).put("durationMs", durationMs).put("background", background)

    companion object {
        fun fromJson(o: JSONObject?) = if (o == null) Bookend() else Bookend(
            o.optBoolean("enabled"), o.optString("title"), o.optString("subtitle"),
            o.optLong("durationMs", 3000), o.optInt("background", 0xFF0B0B12.toInt())
        )
    }
}

/**
 * Episodio editavel. O tempo dos clips e relativo ao "corpo" do episodio;
 * a introducao vem antes e o desfecho depois.
 */
class Episode(
    var name: String,
    var intro: Bookend = Bookend(),
    var outro: Bookend = Bookend(),
    val clips: MutableList<Clip> = mutableListOf(),
    var playheadMs: Long = 0,
    var nextId: Long = 1
) {
    val introMs get() = if (intro.enabled) intro.durationMs else 0L
    val bodyMs get() = clips.maxOfOrNull { it.endMs } ?: 0L
    val outroMs get() = if (outro.enabled) outro.durationMs else 0L
    val totalMs get() = introMs + bodyMs + outroMs

    fun newId() = nextId++

    fun clip(id: Long) = clips.firstOrNull { it.id == id }

    fun toJson(): JSONObject = JSONObject()
        .put("version", 1).put("name", name).put("intro", intro.toJson()).put("outro", outro.toJson())
        .put("playheadMs", playheadMs).put("nextId", nextId)
        .put("clips", JSONArray().apply { clips.forEach { put(it.toJson()) } })

    companion object {
        fun fromJson(o: JSONObject): Episode {
            val arr = o.optJSONArray("clips") ?: JSONArray()
            val list = MutableList(arr.length()) { Clip.fromJson(arr.getJSONObject(it)) }
            val ep = Episode(
                o.optString("name", "Episodio"), Bookend.fromJson(o.optJSONObject("intro")),
                Bookend.fromJson(o.optJSONObject("outro")), list, o.optLong("playheadMs"),
                o.optLong("nextId", 1)
            )
            val maxId = list.maxOfOrNull { it.id } ?: 0
            if (ep.nextId <= maxId) ep.nextId = maxId + 1
            return ep
        }
    }
}
