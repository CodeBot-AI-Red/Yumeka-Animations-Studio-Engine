package com.yumeka.anime.engine.studio

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

/**
 * Persistencia do Studio: Studio/project.json + imagens PNG em Studio/art
 */
class StudioStorage(private val projectDir: File) {
    val root = File(projectDir, "Studio")
    private val artDir = File(root, "art")
    val exportDir = File(projectDir, "Exportados")
    private val meta = File(root, "project.json")
    private val io = Executors.newSingleThreadExecutor()

    init {
        try { artDir.mkdirs(); exportDir.mkdirs() } catch (_: Exception) {}
    }

    private fun artFile(frameId: Int, layerId: Int) = File(artDir, "f${frameId}_l${layerId}.png")

    fun load(defaultName: String): StudioProject {
        try {
            if (meta.exists()) return parse(JSONObject(meta.readText())).also { saveMeta(it) }
        } catch (_: Exception) {}
        val p = Presets.newProject(defaultName)
        saveMeta(p)
        return p
    }

    private fun parse(o: JSONObject): StudioProject {
        val p = StudioProject(o.optString("name", "Projeto"))
        p.fps = o.optInt("fps", 12)
        p.nextId = o.optInt("nextId", 1)
        val sa = o.optJSONArray("scenes") ?: JSONArray()
        for (i in 0 until sa.length()) {
            val so = sa.getJSONObject(i)
            val s = StudioScene(so.getInt("id"), so.optString("name", "Cena"))
            s.background = so.optInt("bg", 1)
            s.shake = so.optBoolean("shake", false)
            val la = so.getJSONArray("layers")
            for (j in 0 until la.length()) {
                val lo = la.getJSONObject(j)
                s.layers.add(LayerInfo(lo.getInt("id"), lo.optString("name"), lo.optBoolean("vis", true), lo.optInt("op", 255), lo.optBoolean("above", true)))
            }
            val fa = so.getJSONArray("frames")
            for (j in 0 until fa.length()) {
                val fo = fa.getJSONObject(j)
                val f = StudioFrame(fo.getInt("id"))
                f.hold = fo.optInt("hold", 1)
                for (l in s.layers) {
                    val file = artFile(f.id, l.id)
                    if (file.exists()) {
                        val opts = BitmapFactory.Options().apply { inMutable = true }
                        BitmapFactory.decodeFile(file.absolutePath, opts)?.let { f.art[l.id] = it }
                    }
                }
                s.frames.add(f)
            }
            val ca = so.optJSONArray("chars") ?: JSONArray()
            for (j in 0 until ca.length()) {
                val co = ca.getJSONObject(j)
                val c = StudioCharacter(co.getInt("id"), co.optString("name"), co.getInt("hair"), co.getInt("skin"), co.getInt("outfit"), co.getInt("eyes"), co.optInt("style", 0))
                val ko = co.optJSONObject("keys") ?: JSONObject()
                ko.keys().forEach { k -> c.keys[k.toInt()] = poseFrom(ko.getJSONObject(k)) }
                s.characters.add(c)
            }
            val cko = so.optJSONObject("cam") ?: JSONObject()
            cko.keys().forEach { k ->
                val v = cko.getJSONObject(k)
                s.camKeys[k.toInt()] = CamKey(v.optDouble("z", 1.0).toFloat(), v.optDouble("x", .5).toFloat(), v.optDouble("y", .5).toFloat())
            }
            if (s.frames.isEmpty()) s.frames.add(StudioFrame(p.newId()))
            if (s.layers.isEmpty()) s.layers.add(LayerInfo(p.newId(), "Linha"))
            p.scenes.add(s)
        }
        if (p.scenes.isEmpty()) p.scenes.add(Presets.newScene(p, "Cena 1"))
        return p
    }

    private fun poseFrom(o: JSONObject) = Pose(
        o.optDouble("x", .5).toFloat(), o.optDouble("y", .72).toFloat(), o.optDouble("s", 1.0).toFloat(),
        o.optBoolean("flip"), o.optDouble("h", 0.0).toFloat(), o.optDouble("t", 0.0).toFloat(),
        o.optDouble("al", 15.0).toFloat(), o.optDouble("ar", -15.0).toFloat(),
        o.optDouble("ll", 4.0).toFloat(), o.optDouble("lr", -4.0).toFloat(),
        o.optInt("e"), o.optDouble("m", 0.0).toFloat(), o.optBoolean("b")
    )

    private fun poseJson(p: Pose) = JSONObject().apply {
        put("x", p.x.toDouble()); put("y", p.y.toDouble()); put("s", p.scale.toDouble()); put("flip", p.flip)
        put("h", p.head.toDouble()); put("t", p.torso.toDouble()); put("al", p.armL.toDouble()); put("ar", p.armR.toDouble())
        put("ll", p.legL.toDouble()); put("lr", p.legR.toDouble()); put("e", p.expression); put("m", p.mouth.toDouble()); put("b", p.blink)
    }

    fun saveMeta(p: StudioProject) {
        val o = JSONObject()
        o.put("name", p.name); o.put("fps", p.fps); o.put("nextId", p.nextId)
        val sa = JSONArray()
        for (s in p.scenes) {
            val so = JSONObject()
            so.put("id", s.id); so.put("name", s.name); so.put("bg", s.background); so.put("shake", s.shake)
            so.put("layers", JSONArray().apply {
                s.layers.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("vis", it.visible).put("op", it.opacity).put("above", it.aboveCharacters)) }
            })
            so.put("frames", JSONArray().apply { s.frames.forEach { put(JSONObject().put("id", it.id).put("hold", it.hold)) } })
            so.put("chars", JSONArray().apply {
                s.characters.forEach { c ->
                    val ko = JSONObject()
                    c.keys.forEach { (k, v) -> ko.put(k.toString(), poseJson(v)) }
                    put(JSONObject().put("id", c.id).put("name", c.name).put("hair", c.hairColor).put("skin", c.skin)
                        .put("outfit", c.outfit).put("eyes", c.eyeColor).put("style", c.hairStyle).put("keys", ko))
                }
            })
            val cko = JSONObject()
            s.camKeys.forEach { (k, v) -> cko.put(k.toString(), JSONObject().put("z", v.zoom.toDouble()).put("x", v.x.toDouble()).put("y", v.y.toDouble())) }
            so.put("cam", cko)
            sa.put(so)
        }
        o.put("scenes", sa)
        val text = o.toString()
        val snap = snapshot(p)
        lastSnap = snap
        io.execute {
            try { root.mkdirs(); meta.writeText(text) } catch (_: Exception) {}
            syncTree(snap)
        }
    }

    fun saveArt(frameId: Int, layerId: Int, bmp: Bitmap?) {
        val copy = bmp?.copy(Bitmap.Config.ARGB_8888, false)
        io.execute {
            try {
                val f = artFile(frameId, layerId)
                if (copy == null) f.delete()
                else FileOutputStream(f).use { copy.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } catch (_: Exception) {}
            lastSnap?.let { syncTree(it) }
        }
    }

    fun deleteFrame(frame: StudioFrame, scene: StudioScene) {
        scene.layers.forEach { l -> io.execute { artFile(frame.id, l.id).delete() } }
    }

    // ── Espelho em tempo real na estrutura oficial de pastas do projeto ──────
    // Temporadas/Temporada 1/Episodios/EP-1/Cenas/<Cena>/Quadros/Quadro N/Keyframes/<Camada>.png
    // Assets/Characters/<Personagem>.json

    private class SnapScene(val name: String, val frames: List<Pair<Int, Int>>, val layers: List<Pair<Int, String>>)
    private class SnapChar(val name: String, val json: String)
    private class Snap(val name: String, val fps: Int, val scenes: List<SnapScene>, val chars: List<SnapChar>)

    @Volatile private var lastSnap: Snap? = null

    private fun snapshot(p: StudioProject) = Snap(
        p.name, p.fps,
        p.scenes.map { sc -> SnapScene(sc.name, sc.frames.map { it.id to it.hold }, sc.layers.map { it.id to it.name }) },
        p.scenes.flatMap { it.characters }.distinctBy { it.name }.map { c ->
            SnapChar(c.name, JSONObject().put("name", c.name).put("hair", c.hairColor).put("skin", c.skin)
                .put("outfit", c.outfit).put("eyes", c.eyeColor).put("style", c.hairStyle).toString(2))
        }
    )

    private fun safe(n: String): String =
        n.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().trimEnd('.').ifEmpty { "Sem nome" }

    private fun uniqueNames(names: List<String>): List<String> {
        val used = mutableMapOf<String, Int>()
        return names.map { raw ->
            val b = safe(raw); val k = b.lowercase(); val c = used.getOrDefault(k, 0); used[k] = c + 1
            if (c == 0) b else "$b ($c)"
        }
    }

    private fun prune(dir: File, keep: Set<String>) {
        dir.listFiles()?.forEach { if (it.name !in keep) it.deleteRecursively() }
    }

    private fun syncTree(snap: Snap) {
        try {
            val ep = File(projectDir, "Temporadas/Temporada 1/Episodios/EP-1")
            val cenas = File(ep, "Cenas").apply { mkdirs() }
            val sceneNames = uniqueNames(snap.scenes.map { it.name })
            snap.scenes.forEachIndexed { si, sc ->
                val sDir = File(cenas, sceneNames[si])
                val quadros = File(sDir, "Quadros").apply { mkdirs() }
                val qNames = mutableSetOf<String>()
                val layerNames = uniqueNames(sc.layers.map { it.second })
                sc.frames.forEachIndexed { fi, (fid, hold) ->
                    val qn = "Quadro ${fi + 1}"; qNames += qn
                    val kf = File(quadros, "$qn/Keyframes").apply { mkdirs() }
                    val keep = mutableSetOf<String>()
                    sc.layers.forEachIndexed { li, (lid, _) ->
                        val src = artFile(fid, lid)
                        val dst = File(kf, "${layerNames[li]}.png")
                        if (src.exists()) {
                            keep += dst.name
                            if (!dst.exists() || dst.length() != src.length() || dst.lastModified() < src.lastModified()) src.copyTo(dst, true)
                        }
                    }
                    prune(kf, keep)
                    File(quadros, "$qn/quadro.json").writeText(JSONObject().put("ordem", fi + 1).put("duracao", hold).toString())
                }
                prune(quadros, qNames)
            }
            prune(cenas, sceneNames.toSet())

            val chDir = File(projectDir, "Assets/Characters").apply { mkdirs() }
            val chNames = uniqueNames(snap.chars.map { it.name })
            val keepCh = mutableSetOf<String>()
            snap.chars.forEachIndexed { i, c ->
                val f = File(chDir, "${chNames[i]}.json"); keepCh += f.name
                if (!f.exists() || f.readText() != c.json) f.writeText(c.json)
            }
            chDir.listFiles()?.forEach { if (it.isFile && it.name.endsWith(".json") && it.name !in keepCh) it.delete() }

            val cfg = File(projectDir, "yase.project")
            val lines = (if (cfg.exists()) cfg.readLines() else listOf("# YASE Project Configuration"))
                .filterNot { it.startsWith("scenes=") || it.startsWith("fps=") || it.startsWith("updated_at=") }
            val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).format(java.util.Date())
            cfg.writeText((lines + listOf("fps=${snap.fps}", "scenes=${snap.scenes.size}", "updated_at=$now")).joinToString("\n"))
        } catch (_: Exception) {}
    }
}
