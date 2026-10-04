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
class StudioStorage(projectDir: File) {
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
            if (meta.exists()) return parse(JSONObject(meta.readText()))
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
        io.execute { try { root.mkdirs(); meta.writeText(text) } catch (_: Exception) {} }
    }

    fun saveArt(frameId: Int, layerId: Int, bmp: Bitmap?) {
        val copy = bmp?.copy(Bitmap.Config.ARGB_8888, false)
        io.execute {
            try {
                val f = artFile(frameId, layerId)
                if (copy == null) f.delete()
                else FileOutputStream(f).use { copy.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } catch (_: Exception) {}
        }
    }

    fun deleteFrame(frame: StudioFrame, scene: StudioScene) {
        scene.layers.forEach { l -> io.execute { artFile(frame.id, l.id).delete() } }
    }
}
