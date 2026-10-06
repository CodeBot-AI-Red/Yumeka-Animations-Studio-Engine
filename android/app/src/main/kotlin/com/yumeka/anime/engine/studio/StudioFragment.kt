package com.yumeka.anime.engine.studio

import android.app.AlertDialog
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import java.io.File
import kotlin.math.sin

/**
 * Yumeka Studio — editor de anime adaptavel (celular retrato/paisagem e tablet).
 */
class StudioFragment : Fragment() {

    companion object {
        private const val ARG_NAME = "name"
        private const val ARG_PATH = "path"
        fun newInstance(name: String, path: String) = StudioFragment().apply {
            arguments = Bundle().apply { putString(ARG_NAME, name); putString(ARG_PATH, path) }
        }
        val PALETTE = intArrayOf(
            0xFF111118.toInt(), 0xFFFFFFFF.toInt(), 0xFFE8445A.toInt(), 0xFFFF8A3D.toInt(), 0xFFFBBF24.toInt(), 0xFF4ADE80.toInt(),
            0xFF2EA8FF.toInt(), 0xFF6E3BFF.toInt(), 0xFFFF8FB1.toInt(), 0xFFFFE3D1.toInt(), 0xFF7A4A2A.toInt(), 0xFF9090B0.toInt()
        )
        val TAB_NAMES = listOf("Camadas", "Personagens", "Cena", "Camera")
        val TAB_ICONS = listOf("🗂", "🧍", "🏞", "🎥")
    }

    private class UndoEntry(val frame: StudioFrame, val layerId: Int, val bitmap: Bitmap?)

    private lateinit var project: StudioProject
    private lateinit var storage: StudioStorage
    private lateinit var root: FrameLayout
    private lateinit var canvasView: StudioCanvasView

    private var sceneIdx = 0
    private var frameIdx = 0
    private var activeLayerId = -1
    private var tool = Tool.BRUSH
    private var color = Color.BLACK
    private var size = 8f
    private var opacity = 255
    private var selectedChar = -1
    private var tab = 0
    private var sheetOpen = false
    private var playing = false
    private var holdTick = 0

    private val undo = ArrayDeque<UndoEntry>()
    private val redo = ArrayDeque<UndoEntry>()
    private val thumbs = HashMap<Int, Bitmap>()
    private val handler = Handler(Looper.getMainLooper())

    private var toolBox: LinearLayout? = null
    private var timelineStrip: LinearLayout? = null
    private var timelineScroll: HorizontalScrollView? = null
    private var panelHost: FrameLayout? = null
    private var playBtn: TextView? = null
    private var titleText: TextView? = null
    private var swatchBox: LinearLayout? = null

    private val scene get() = project.scenes[sceneIdx.coerceIn(0, project.scenes.size - 1)]
    private val frame get() = scene.frames[frameIdx.coerceIn(0, scene.frames.size - 1)]

    // ── Ciclo de vida ────────────────────────────────────────────────────────

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
        val ctx = requireContext()
        val name = arguments?.getString(ARG_NAME) ?: "Projeto"
        val path = arguments?.getString(ARG_PATH).orEmpty()
        val dir = if (path.isNotBlank()) File(path) else File(ctx.filesDir, "projetos/$name")
        storage = StudioStorage(dir)
        project = storage.load(name)
        activeLayerId = scene.layers.last().id
        selectedChar = scene.characters.firstOrNull()?.id ?: -1

        canvasView = StudioCanvasView(ctx).apply {
            onStrokeStart = { f, l, before -> pushUndo(UndoEntry(f, l, before)) }
            onArtChanged = { f, l -> storage.saveArt(f.id, l, f.art[l]); thumbs.remove(f.id); refreshTimeline() }
            onColorPicked = { c -> color = c; syncCanvas(); refreshSwatches() }
            onCharacterSelected = { id -> selectedChar = id; syncCanvas(); if (tab == 1) refreshPanel() }
            onCharacterMoved = { ch, x, y -> editPose(ch) { it.x = x; it.y = y } }
        }
        root = FrameLayout(ctx).apply { setBackgroundColor(SC.BG) }
        buildUi()
        return root
    }

    override fun onResume() {
        super.onResume()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
    }

    override fun onPause() {
        super.onPause()
        stopPlayback()
        storage.saveMeta(project)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (view != null) buildUi()
    }

    // ── Layout adaptavel ─────────────────────────────────────────────────────

    private fun buildUi() {
        val ctx = requireContext()
        val cfg = resources.configuration
        val w = cfg.screenWidthDp; val h = cfg.screenHeightDp
        val landscape = w > h
        val railLeft = landscape || w >= 600
        val side = w >= 900 || (w >= 720 && h >= 480)
        if (side) sheetOpen = false

        (canvasView.parent as? ViewGroup)?.removeView(canvasView)
        root.removeAllViews()
        syncCanvas()

        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        col.addView(topBar(compactTabs = railLeft && !side))

        val mid = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        if (railLeft) {
            val rail = ScrollView(ctx).apply { isVerticalScrollBarEnabled = false; setBackgroundColor(SC.PANEL) }
            toolBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(ctx.dp(4), ctx.dp(4), ctx.dp(4), ctx.dp(4)) }
            rail.addView(toolBox)
            mid.addView(rail, LinearLayout.LayoutParams(ctx.dp(if (h < 420) 60 else 72), ViewGroup.LayoutParams.MATCH_PARENT))
        }
        val center = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        center.addView(canvasView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        if (!railLeft) {
            val hs = HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; setBackgroundColor(SC.PANEL) }
            toolBox = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(ctx.dp(6), ctx.dp(2), ctx.dp(6), ctx.dp(2)) }
            hs.addView(toolBox)
            center.addView(hs)
        }
        center.addView(brushBar())
        mid.addView(center, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        if (side) {
            val panel = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; background = roundBg(SC.PANEL, 0f) }
            panel.addView(tabChips(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            panelHost = FrameLayout(ctx)
            panel.addView(panelHost, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            mid.addView(panel, LinearLayout.LayoutParams(ctx.dp(if (w >= 1100) 340 else 300), ViewGroup.LayoutParams.MATCH_PARENT))
        } else panelHost = null
        col.addView(mid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        col.addView(timeline(compact = h < 480))
        if (!railLeft) col.addView(bottomTabs())
        root.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        if (sheetOpen && !side) {
            val scrim = View(ctx).apply { setBackgroundColor(0x99000000.toInt()); setOnClickListener { sheetOpen = false; buildUi() } }
            root.addView(scrim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            val sheet = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                background = roundBg(SC.PANEL, ctx.dp(22).toFloat(), ctx.dp(1), SC.BORDER)
                elevation = ctx.dp(16).toFloat()
                isClickable = true
            }
            val handle = View(ctx).apply { background = roundBg(SC.MUTED, ctx.dp(3).toFloat()) }
            sheet.addView(handle, LinearLayout.LayoutParams(ctx.dp(44), ctx.dp(5)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = ctx.dp(8) })
            val head = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            head.addView(tabChips(), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            head.addView(ctx.toolButton("✕", null, false, 40) { sheetOpen = false; buildUi() })
            sheet.addView(head)
            panelHost = FrameLayout(ctx)
            sheet.addView(panelHost, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            val sheetH = (resources.displayMetrics.heightPixels * if (landscape) .86f else .58f).toInt()
            val sheetW = if (landscape) ctx.dp(380) else ViewGroup.LayoutParams.MATCH_PARENT
            root.addView(sheet, FrameLayout.LayoutParams(sheetW, sheetH, if (landscape) Gravity.END or Gravity.BOTTOM else Gravity.BOTTOM).apply {
                setMargins(ctx.dp(6), 0, ctx.dp(6), ctx.dp(6))
            })
        }
        refreshTools(vertical = railLeft)
        refreshTimeline()
        refreshPanel()
    }

    private fun topBar(compactTabs: Boolean): View {
        val ctx = requireContext()
        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(ctx.dp(6), ctx.dp(4), ctx.dp(6), ctx.dp(4))
            background = gradBg(0f, 0xFF15101C.toInt(), SC.PANEL)
        }
        bar.addView(ctx.toolButton("←", null, false, 40) { activity?.onBackPressedDispatcher?.onBackPressed() })
        val titles = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(ctx.dp(8), 0, ctx.dp(8), 0) }
        titles.addView(ctx.label("YUMEKA STUDIO", 9f, SC.PINK, true).apply { letterSpacing = .2f })
        titleText = ctx.label("", 15f, SC.TEXT, true).apply { maxLines = 1 }
        titles.addView(titleText)
        bar.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        updateTitle()
        if (compactTabs) {
            TAB_ICONS.forEachIndexed { i, ic ->
                bar.addView(ctx.toolButton(ic, null, sheetOpen && tab == i, 40) { openTab(i) })
            }
            bar.addView(View(ctx), LinearLayout.LayoutParams(ctx.dp(8), 1))
        }
        bar.addView(ctx.pill("🎬 Editar episódio", true, SC.PINK) {
            storage.saveMeta(project)
            (activity as? com.yumeka.anime.engine.MainActivity)?.openEpisodeEditor(project.name, arguments?.getString(ARG_PATH).orEmpty())
        }.apply {
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ctx.dp(44)).apply { rightMargin = ctx.dp(4) }
        })
        bar.addView(ctx.pill("✨ Gerar quadro com IA", false, SC.PURPLE) {
            storage.saveMeta(project)
            (activity as? com.yumeka.anime.engine.MainActivity)?.openAiFrame(project.name, arguments?.getString(ARG_PATH).orEmpty())
        }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ctx.dp(44)).apply { rightMargin = ctx.dp(4) }
        })
        bar.addView(ctx.toolButton("↶", null, false, 40) { doUndo() })
        bar.addView(ctx.toolButton("↷", null, false, 40) { doRedo() })
        playBtn = ctx.toolButton(if (playing) "⏸" else "▶", null, playing, 40) { togglePlay() }.getChildAt(0) as TextView
        bar.addView(playBtn!!.parent as View)
        bar.addView(ctx.pill("Exportar", true) { showExport() }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = ctx.dp(4) }
        })
        return bar
    }

    private fun updateTitle() {
        titleText?.text = "${project.name} · ${scene.name} · ${frameIdx + 1}/${scene.frames.size}"
    }

    private fun refreshTools(vertical: Boolean) {
        val ctx = requireContext()
        val box = toolBox ?: return
        box.removeAllViews()
        val sz = if (vertical && resources.configuration.screenHeightDp < 420) 50 else 56
        Tool.values().forEach { t ->
            box.addView(ctx.toolButton(t.icon, t.label, t == tool, sz) {
                tool = t; syncCanvas(); refreshTools(vertical)
                if (t == Tool.MOVE && scene.characters.isEmpty()) toast("Adicione um personagem na aba Personagens")
            })
        }
        box.addView(ctx.toolButton("⟲", "Centralizar", false, sz) { canvasView.resetView() })
    }

    private fun brushBar(): View {
        val ctx = requireContext()
        val hs = HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; setBackgroundColor(SC.PANEL) }
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(ctx.dp(8), ctx.dp(4), ctx.dp(8), ctx.dp(4)) }
        swatchBox = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(swatchBox)
        refreshSwatches()
        row.addView(miniSeek("Tam", size, 1f, 60f) { size = it; syncCanvas() })
        row.addView(miniSeek("Opac", opacity.toFloat(), 20f, 255f) { opacity = it.toInt(); syncCanvas() })
        row.addView(ctx.pill(if (canvasView.onion) "🧅 Papel cebola" else "🧅 Desligado", canvasView.onion, SC.PURPLE) {
            canvasView.onion = !canvasView.onion; canvasView.invalidate(); buildUi()
        })
        hs.addView(row)
        return hs
    }

    private fun refreshSwatches() {
        val ctx = context ?: return
        val box = swatchBox ?: return
        box.removeAllViews()
        val current = View(ctx).apply {
            background = roundBg(color, ctx.dp(8).toFloat(), ctx.dp(2), Color.WHITE)
            contentDescription = "Cor atual"
        }
        box.addView(current, LinearLayout.LayoutParams(ctx.dp(38), ctx.dp(30)).apply { rightMargin = ctx.dp(6) })
        PALETTE.forEach { c -> box.addView(ctx.swatch(c, c == color) { color = c; syncCanvas(); refreshSwatches() }) }
    }

    private fun miniSeek(name: String, value: Float, min: Float, max: Float, on: (Float) -> Unit): View {
        val ctx = requireContext()
        val box = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(ctx.dp(10), 0, ctx.dp(4), 0) }
        box.addView(ctx.label(name, 11f, SC.TEXT2))
        val sb = SeekBar(ctx).apply {
            this.max = 100
            progress = (((value - min) / (max - min)) * 100).toInt()
            progressTintList = android.content.res.ColorStateList.valueOf(SC.PINK)
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { if (u) on(min + (max - min) * p / 100f) }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        box.addView(sb, LinearLayout.LayoutParams(ctx.dp(110), ViewGroup.LayoutParams.WRAP_CONTENT))
        return box
    }

    private fun tabChips(): View {
        val ctx = requireContext()
        val hs = HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(ctx.dp(10), ctx.dp(10), ctx.dp(10), ctx.dp(4)) }
        TAB_NAMES.forEachIndexed { i, n ->
            row.addView(ctx.pill("${TAB_ICONS[i]} $n", tab == i) { tab = i; buildUi() }.apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = ctx.dp(6) }
            })
        }
        hs.addView(row)
        return hs
    }

    private fun bottomTabs(): View {
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(SC.PANEL); setPadding(0, ctx.dp(2), 0, ctx.dp(4)) }
        TAB_NAMES.forEachIndexed { i, n ->
            val active = sheetOpen && tab == i
            val item = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setPadding(0, ctx.dp(6), 0, ctx.dp(6))
                background = ripple(roundBg(if (active) SC.CARD2 else SC.PANEL, ctx.dp(12).toFloat()))
                addView(ctx.label(TAB_ICONS[i], 18f).apply { gravity = Gravity.CENTER })
                addView(ctx.label(n, 10f, if (active) SC.PINK else SC.TEXT2, active).apply { gravity = Gravity.CENTER })
                setOnClickListener { openTab(i) }
            }
            row.addView(item, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(ctx.dp(4), 0, ctx.dp(4), 0) })
        }
        return row
    }

    private fun openTab(i: Int) {
        if (sheetOpen && tab == i) sheetOpen = false else { tab = i; sheetOpen = true }
        buildUi()
    }

    // ── Linha do tempo ───────────────────────────────────────────────────────

    private fun timeline(compact: Boolean): View {
        val ctx = requireContext()
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF0E0E16.toInt()); setPadding(ctx.dp(6), ctx.dp(6), ctx.dp(6), ctx.dp(6))
        }
        val ctrls = LinearLayout(ctx).apply { orientation = if (compact) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL }
        val bs = if (compact) 40 else 44
        val r1 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        r1.addView(ctx.toolButton("＋", null, false, bs) { addFrame(false) })
        r1.addView(ctx.toolButton("⧉", null, false, bs) { addFrame(true) })
        val r2 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        r2.addView(ctx.toolButton("🗑", null, false, bs) { deleteFrame() })
        r2.addView(ctx.toolButton("×${frame.hold}", null, false, bs) {
            frame.hold = if (frame.hold >= 4) 1 else frame.hold + 1; scheduleSave(); buildUi()
            toast("Duracao do quadro: ${frame.hold} tempo(s)")
        })
        ctrls.addView(r1); ctrls.addView(r2)
        box.addView(ctrls)
        timelineScroll = HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false }
        timelineStrip = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(ctx.dp(6), 0, ctx.dp(6), 0) }
        timelineScroll!!.addView(timelineStrip)
        box.addView(timelineScroll, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return box
    }

    private fun thumbFor(f: StudioFrame, idx: Int): Bitmap = thumbs.getOrPut(f.id) {
        val b = Bitmap.createBitmap(128, 72, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.scale(128f / ART_W, 72f / ART_H)
        FrameRenderer.render(c, scene, idx, false)
        b
    }

    private fun refreshTimeline() {
        val ctx = context ?: return
        val strip = timelineStrip ?: return
        strip.removeAllViews()
        val compact = resources.configuration.screenHeightDp < 480
        val tw = ctx.dp(if (compact) 72 else 88); val th = tw * 9 / 16
        scene.frames.forEachIndexed { i, f ->
            val sel = i == frameIdx
            val cell = FrameLayout(ctx).apply {
                background = roundBg(if (sel) SC.CARD2 else SC.CARD, ctx.dp(10).toFloat(), ctx.dp(if (sel) 2 else 1), if (sel) SC.PINK else SC.BORDER)
                setPadding(ctx.dp(3), ctx.dp(3), ctx.dp(3), ctx.dp(3))
                setOnClickListener { selectFrame(i) }
                setOnLongClickListener { frameMenu(i); true }
            }
            val inner = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            inner.addView(ImageView(ctx).apply { setImageBitmap(thumbFor(f, i)); scaleType = ImageView.ScaleType.FIT_XY }, LinearLayout.LayoutParams(tw, th))
            val hasKey = scene.characters.any { it.keys.containsKey(f.id) } || scene.camKeys.containsKey(f.id)
            val cap = (if (hasKey) "◆ " else "") + "${i + 1}" + (if (f.hold > 1) "  ×${f.hold}" else "")
            inner.addView(ctx.label(cap, 10f, if (hasKey) SC.AMBER else SC.TEXT2, sel).apply { gravity = Gravity.CENTER })
            cell.addView(inner)
            strip.addView(cell, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = ctx.dp(6) })
        }
        val add = ctx.toolButton("＋", "Quadro", false, if (compact) 56 else 64) { addFrame(false) }
        strip.addView(add)
        updateTitle()
        timelineScroll?.post {
            val c = strip.getChildAt(frameIdx) ?: return@post
            val sv = timelineScroll ?: return@post
            if (c.left < sv.scrollX || c.right > sv.scrollX + sv.width) sv.smoothScrollTo(c.left - ctx.dp(20), 0)
        }
    }

    private fun frameMenu(i: Int) {
        selectFrame(i)
        val opts = arrayOf("Duplicar", "Mover para a esquerda", "Mover para a direita", "Limpar desenhos", "Excluir")
        AlertDialog.Builder(requireContext()).setTitle("Quadro ${i + 1}").setItems(opts) { _, w ->
            when (w) {
                0 -> addFrame(true)
                1 -> if (i > 0) { java.util.Collections.swap(scene.frames, i, i - 1); frameIdx = i - 1; thumbs.clear(); changed() }
                2 -> if (i < scene.frames.size - 1) { java.util.Collections.swap(scene.frames, i, i + 1); frameIdx = i + 1; thumbs.clear(); changed() }
                3 -> { frame.art.keys.toList().forEach { l -> storage.saveArt(frame.id, l, null) }; frame.art.clear(); thumbs.remove(frame.id); changed() }
                4 -> deleteFrame()
            }
        }.show()
    }

    private fun selectFrame(i: Int) {
        frameIdx = i.coerceIn(0, scene.frames.size - 1)
        syncCanvas(); refreshTimeline(); refreshPanel()
    }

    private fun addFrame(copy: Boolean) {
        val f = StudioFrame(project.newId())
        if (copy) {
            f.hold = frame.hold
            frame.art.forEach { (l, b) -> f.art[l] = b.copy(Bitmap.Config.ARGB_8888, true); storage.saveArt(f.id, l, f.art[l]) }
            scene.characters.forEach { ch -> ch.keys[f.id] = ch.poseAt(scene, frameIdx).copy() }
        }
        scene.frames.add(frameIdx + 1, f)
        frameIdx++
        changed()
    }

    private fun deleteFrame() {
        if (scene.frames.size <= 1) { toast("A cena precisa de ao menos um quadro"); return }
        val f = frame
        storage.deleteFrame(f, scene)
        scene.characters.forEach { it.keys.remove(f.id) }
        scene.camKeys.remove(f.id)
        scene.frames.remove(f)
        frameIdx = frameIdx.coerceAtMost(scene.frames.size - 1)
        thumbs.clear()
        changed()
    }

    // ── Paineis ──────────────────────────────────────────────────────────────

    private fun refreshPanel() {
        val host = panelHost ?: return
        val ctx = requireContext()
        host.removeAllViews()
        val sv = ScrollView(ctx)
        val c = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(ctx.dp(14), 0, ctx.dp(14), ctx.dp(24)) }
        when (tab) {
            0 -> layersPanel(c)
            1 -> charactersPanel(c)
            2 -> scenePanel(c)
            else -> cameraPanel(c)
        }
        sv.addView(c)
        host.addView(sv)
    }

    private fun layersPanel(c: LinearLayout) {
        val ctx = requireContext()
        c.addView(ctx.sectionTitle("Camadas do quadro ${frameIdx + 1}"))
        scene.layers.asReversed().forEach { l ->
            val active = l.id == activeLayerId
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                background = ripple(roundBg(if (active) SC.CARD2 else SC.CARD, ctx.dp(12).toFloat(), ctx.dp(if (active) 2 else 1), if (active) SC.PINK else SC.BORDER))
                setPadding(ctx.dp(6), ctx.dp(6), ctx.dp(10), ctx.dp(6))
                setOnClickListener { activeLayerId = l.id; syncCanvas(); refreshPanel() }
            }
            row.addView(ctx.toolButton(if (l.visible) "👁" else "🚫", null, false, 38) {
                l.visible = !l.visible; thumbs.clear(); changed()
            })
            val txt = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(ctx.dp(8), 0, 0, 0) }
            txt.addView(ctx.label(l.name, 14f, SC.TEXT, active))
            txt.addView(ctx.label(if (l.aboveCharacters) "Na frente dos personagens" else "Atras dos personagens", 10f, SC.TEXT2))
            row.addView(txt, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            c.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = ctx.dp(6) })
        }
        val l = scene.layers.firstOrNull { it.id == activeLayerId } ?: return
        c.addView(ctx.slider("Opacidade da camada", l.opacity.toFloat(), 0f, 255f, { "${(it / 2.55f).toInt()}%" }) {
            l.opacity = it.toInt(); thumbs.clear(); canvasView.invalidate(); scheduleSave()
        })
        c.addView(ctx.flow(
            ctx.pill("+ Camada") {
                val nl = LayerInfo(project.newId(), "Camada ${scene.layers.size + 1}")
                scene.layers.add(nl); activeLayerId = nl.id; changed()
            },
            ctx.pill("Renomear") { prompt("Nome da camada", l.name) { l.name = it; changed() } },
            ctx.pill(if (l.aboveCharacters) "Mandar p/ tras" else "Trazer p/ frente") { l.aboveCharacters = !l.aboveCharacters; thumbs.clear(); changed() },
            ctx.pill("▲ Subir") { val i = scene.layers.indexOf(l); if (i < scene.layers.size - 1) { java.util.Collections.swap(scene.layers, i, i + 1); thumbs.clear(); changed() } },
            ctx.pill("▼ Descer") { val i = scene.layers.indexOf(l); if (i > 0) { java.util.Collections.swap(scene.layers, i, i - 1); thumbs.clear(); changed() } },
            ctx.pill("Copiar do quadro anterior") {
                val prev = scene.frames.getOrNull(frameIdx - 1)?.art?.get(l.id)
                if (prev == null) toast("Nada para copiar") else {
                    pushUndo(UndoEntry(frame, l.id, frame.art[l.id]?.copy(Bitmap.Config.ARGB_8888, true)))
                    frame.art[l.id] = prev.copy(Bitmap.Config.ARGB_8888, true); storage.saveArt(frame.id, l.id, frame.art[l.id]); thumbs.remove(frame.id); changed()
                }
            },
            ctx.pill("Limpar") {
                pushUndo(UndoEntry(frame, l.id, frame.art[l.id]?.copy(Bitmap.Config.ARGB_8888, true)))
                frame.art.remove(l.id); storage.saveArt(frame.id, l.id, null); thumbs.remove(frame.id); changed()
            },
            ctx.pill("Excluir", accent = SC.RED) {
                if (scene.layers.size <= 1) toast("Mantenha ao menos uma camada") else {
                    scene.layers.remove(l); scene.frames.forEach { f -> f.art.remove(l.id); storage.saveArt(f.id, l.id, null) }
                    activeLayerId = scene.layers.last().id; thumbs.clear(); changed()
                }
            }
        ))
    }

    private fun charactersPanel(c: LinearLayout) {
        val ctx = requireContext()
        c.addView(ctx.sectionTitle("Elenco da cena"))
        val chips = scene.characters.map { ch ->
            ctx.pill("🧍 ${ch.name}", ch.id == selectedChar) { selectedChar = ch.id; syncCanvas(); refreshPanel() }
        }.toMutableList<View>()
        chips.add(ctx.pill("+ Novo personagem", accent = SC.PURPLE) {
            val n = scene.characters.size
            prompt("Nome do personagem", "") { nm ->
                val ch = Presets.blankCharacter(project, nm)
                ch.keys[frame.id] = Pose(x = (0.25f + 0.25f * (n % 3)).coerceAtMost(.85f))
                scene.characters.add(ch); selectedChar = ch.id; thumbs.clear(); changed()
            }
        })
        c.addView(ctx.flow(*chips.toTypedArray()))
        val ch = scene.characters.firstOrNull { it.id == selectedChar }
        if (ch == null) {
            c.addView(ctx.label("Crie ou selecione um personagem. Depois use a ferramenta ✥ Mover para arrasta-lo no quadro.", 12f, SC.TEXT2).apply { setPadding(0, ctx.dp(12), 0, 0) })
            return
        }
        val pose = ch.poseAt(scene, frameIdx)
        val keyed = ch.keys.containsKey(frame.id)
        c.addView(ctx.label(if (keyed) "◆ Pose-chave neste quadro" else "◇ Pose interpolada (ajuste para criar chave)", 12f, if (keyed) SC.AMBER else SC.TEXT2, true).apply { setPadding(0, ctx.dp(10), 0, 0) })

        c.addView(ctx.sectionTitle("Movimentos prontos"))
        c.addView(ctx.flow(
            ctx.pill("🚶 Andar") { motion(ch, "walk") },
            ctx.pill("👋 Acenar") { motion(ch, "wave") },
            ctx.pill("🦘 Pular") { motion(ch, "jump") },
            ctx.pill("💬 Falar") { motion(ch, "talk") },
            ctx.pill("😉 Piscar") { motion(ch, "blink") }
        ))

        c.addView(ctx.sectionTitle("Expressao"))
        c.addView(ctx.flow(*Presets.expressions.mapIndexed { i, e ->
            ctx.pill(e, pose.expression == i) { editPose(ch) { it.expression = i }; refreshPanel() }
        }.toTypedArray()))
        c.addView(ctx.flow(
            ctx.pill(if (pose.flip) "↔ Virado" else "↔ Virar", pose.flip) { editPose(ch) { it.flip = !it.flip }; refreshPanel() },
            ctx.pill(if (pose.blink) "Olhos fechados" else "Olhos abertos", pose.blink) { editPose(ch) { it.blink = !it.blink }; refreshPanel() },
            ctx.pill("Remover chave") { ch.keys.remove(frame.id); thumbs.clear(); changed() }
        ))

        c.addView(ctx.sectionTitle("Pose"))
        c.addView(ctx.slider("Posicao X", pose.x, 0f, 1f, { "${(it * 100).toInt()}" }) { v -> editPose(ch) { it.x = v } })
        c.addView(ctx.slider("Posicao Y", pose.y, .3f, 1.25f, { "${(it * 100).toInt()}" }) { v -> editPose(ch) { it.y = v } })
        c.addView(ctx.slider("Escala", pose.scale, .4f, 2.2f, { "%.2fx".format(it) }) { v -> editPose(ch) { it.scale = v } })
        c.addView(ctx.slider("Cabeca", pose.head, -40f, 40f, { "${it.toInt()}°" }) { v -> editPose(ch) { it.head = v } })
        c.addView(ctx.slider("Tronco", pose.torso, -35f, 35f, { "${it.toInt()}°" }) { v -> editPose(ch) { it.torso = v } })
        c.addView(ctx.slider("Braco esquerdo", pose.armL, -180f, 180f, { "${it.toInt()}°" }) { v -> editPose(ch) { it.armL = v } })
        c.addView(ctx.slider("Braco direito", pose.armR, -180f, 180f, { "${it.toInt()}°" }) { v -> editPose(ch) { it.armR = v } })
        c.addView(ctx.slider("Perna esquerda", pose.legL, -70f, 70f, { "${it.toInt()}°" }) { v -> editPose(ch) { it.legL = v } })
        c.addView(ctx.slider("Perna direita", pose.legR, -70f, 70f, { "${it.toInt()}°" }) { v -> editPose(ch) { it.legR = v } })
        c.addView(ctx.slider("Boca aberta", pose.mouth, 0f, 1f, { "${(it * 100).toInt()}%" }) { v -> editPose(ch) { it.mouth = v } })

        c.addView(ctx.sectionTitle("Visual"))
        c.addView(ctx.flow(*Presets.hairStyles.mapIndexed { i, s -> ctx.pill(s, ch.hairStyle == i) { ch.hairStyle = i; lookChanged() } }.toTypedArray()))
        c.addView(colorRow("Cabelo", Presets.hair, ch.hairColor) { ch.hairColor = it; lookChanged() })
        c.addView(colorRow("Olhos", Presets.eyes, ch.eyeColor) { ch.eyeColor = it; lookChanged() })
        c.addView(colorRow("Roupa", Presets.outfit, ch.outfit) { ch.outfit = it; lookChanged() })
        c.addView(colorRow("Pele", Presets.skin, ch.skin) { ch.skin = it; lookChanged() })
        c.addView(ctx.flow(
            ctx.pill("Renomear") { prompt("Nome do personagem", ch.name) { ch.name = it; changed() } },
            ctx.pill("Excluir personagem", accent = SC.RED) { scene.characters.remove(ch); selectedChar = -1; thumbs.clear(); changed() }
        ))
    }

    private fun colorRow(name: String, colors: IntArray, current: Int, on: (Int) -> Unit): View {
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, ctx.dp(6), 0, ctx.dp(6)) }
        row.addView(ctx.label(name, 12f, SC.TEXT2), LinearLayout.LayoutParams(ctx.dp(60), ViewGroup.LayoutParams.WRAP_CONTENT))
        colors.forEach { col -> row.addView(ctx.swatch(col, col == current) { on(col) }) }
        return HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; addView(row) }
    }

    private fun lookChanged() { thumbs.clear(); canvasView.invalidate(); scheduleSave(); refreshPanel(); refreshTimeline() }

    private fun motion(ch: StudioCharacter, type: String) {
        val base = ch.poseAt(scene, frameIdx).copy()
        val n = scene.frames.size
        scene.frames.forEachIndexed { i, f ->
            val p = base.copy()
            val t = i.toFloat() / maxOf(1, n - 1)
            val ph = (i % 4) * (Math.PI / 2)
            when (type) {
                "walk" -> {
                    val sw = (sin(ph) * 28).toFloat()
                    p.legL = sw; p.legR = -sw; p.armL = -sw * .8f; p.armR = sw * .8f
                    p.x = (base.x + (if (base.flip) -1 else 1) * .35f * t).coerceIn(0f, 1f)
                    p.y = base.y - (Math.abs(sin(ph)) * .015f).toFloat()
                }
                "wave" -> { p.armR = if (i % 2 == 0) -150f else -115f; p.expression = 1; p.head = if (i % 2 == 0) 6f else -4f }
                "jump" -> {
                    val arc = (sin(t * Math.PI)).toFloat()
                    p.y = base.y - .25f * arc; p.legL = 25f * arc; p.legR = -25f * arc
                    p.armL = 40f + 90f * arc; p.armR = -40f - 90f * arc; p.expression = if (arc > .3f) 4 else base.expression
                }
                "talk" -> p.mouth = floatArrayOf(.1f, .7f, .3f, .9f, .2f, .6f)[i % 6]
                "blink" -> p.blink = i % 6 == 3
            }
            ch.keys[f.id] = p
        }
        thumbs.clear(); changed()
        toast("Movimento aplicado em ${n} quadros — aperte ▶")
    }

    private fun scenePanel(c: LinearLayout) {
        val ctx = requireContext()
        c.addView(ctx.sectionTitle("Cenas do episodio"))
        val chips = project.scenes.mapIndexed { i, s ->
            ctx.pill("🎬 ${s.name}", i == sceneIdx) { stopPlayback(); sceneIdx = i; frameIdx = 0; activeLayerId = scene.layers.last().id; selectedChar = scene.characters.firstOrNull()?.id ?: -1; thumbs.clear(); undo.clear(); redo.clear(); changed() }
        }.toMutableList<View>()
        chips.add(ctx.pill("+ Nova cena", accent = SC.PURPLE) {
            prompt("Nome da cena", "Cena ${project.scenes.size + 1}") { nm ->
                val s = Presets.newScene(project, nm)
                project.scenes.add(s); sceneIdx = project.scenes.size - 1; frameIdx = 0; activeLayerId = s.layers.last().id; selectedChar = -1; thumbs.clear(); changed()
            }
        })
        c.addView(ctx.flow(*chips.toTypedArray()))
        c.addView(ctx.flow(
            ctx.pill("Renomear cena") { prompt("Nome da cena", scene.name) { scene.name = it; changed() } },
            ctx.pill("Excluir cena", accent = SC.RED) {
                if (project.scenes.size <= 1) toast("O projeto precisa de ao menos uma cena") else {
                    project.scenes.removeAt(sceneIdx); sceneIdx = 0; frameIdx = 0; activeLayerId = scene.layers.last().id; thumbs.clear(); changed()
                }
            }
        ))
        c.addView(ctx.sectionTitle("Cenario"))
        c.addView(ctx.flow(*Presets.backgrounds.mapIndexed { i, b ->
            ctx.pill(b, scene.background == i) { scene.background = i; thumbs.clear(); changed() }
        }.toTypedArray()))
        c.addView(ctx.sectionTitle("Velocidade (quadros por segundo)"))
        c.addView(ctx.flow(*intArrayOf(6, 8, 12, 24).map { f -> ctx.pill("$f fps", project.fps == f) { project.fps = f; changed() } }.toTypedArray()))
        c.addView(ctx.sectionTitle("Efeitos"))
        c.addView(ctx.flow(ctx.pill(if (scene.shake) "📳 Tremor ligado" else "📳 Tremor de camera", scene.shake) { scene.shake = !scene.shake; changed() }))
        val ticks = project.scenes.sumOf { s -> s.frames.sumOf { it.hold } }
        c.addView(ctx.label("Duracao total: %.1f s · %d cena(s) · %d quadro(s)".format(ticks.toFloat() / project.fps, project.scenes.size, project.scenes.sumOf { it.frames.size }), 12f, SC.TEXT2).apply { setPadding(0, ctx.dp(14), 0, 0) })
    }

    private fun cameraPanel(c: LinearLayout) {
        val ctx = requireContext()
        val cam = scene.cameraAt(frameIdx)
        c.addView(ctx.sectionTitle("Camera do quadro ${frameIdx + 1}"))
        c.addView(ctx.flow(
            ctx.pill(if (canvasView.cameraPreview) "🎥 Vendo pela camera" else "🎥 Ver pela camera", canvasView.cameraPreview, SC.AMBER) {
                canvasView.cameraPreview = !canvasView.cameraPreview; canvasView.invalidate(); refreshPanel()
            }
        ))
        c.addView(ctx.label(if (scene.camKeys.containsKey(frame.id)) "◆ Chave de camera neste quadro" else "◇ Camera interpolada", 12f, SC.AMBER).apply { setPadding(0, ctx.dp(8), 0, 0) })
        c.addView(ctx.slider("Zoom", cam.zoom, 1f, 3f, { "%.2fx".format(it) }) { v -> editCam { it.zoom = v } })
        c.addView(ctx.slider("Centro X", cam.x, 0f, 1f, { "${(it * 100).toInt()}" }) { v -> editCam { it.x = v } })
        c.addView(ctx.slider("Centro Y", cam.y, 0f, 1f, { "${(it * 100).toInt()}" }) { v -> editCam { it.y = v } })
        c.addView(ctx.sectionTitle("Movimentos de camera"))
        c.addView(ctx.flow(
            ctx.pill("🔍 Zoom dramatico") { camMove { t -> CamKey(1f + .8f * t, .5f, .45f) } },
            ctx.pill("➡ Panoramica") { camMove { t -> CamKey(1.4f, .35f + .3f * t, .5f) } },
            ctx.pill("⬆ Subir") { camMove { t -> CamKey(1.4f, .5f, .65f - .3f * t) } },
            ctx.pill("Remover chave") { scene.camKeys.remove(frame.id); changed() },
            ctx.pill("Resetar camera", accent = SC.RED) { scene.camKeys.clear(); changed() }
        ))
        c.addView(ctx.label("O retangulo amarelo no quadro mostra o enquadramento final. A camera e aplicada na reproducao e na exportacao.", 12f, SC.TEXT2).apply { setPadding(0, ctx.dp(12), 0, 0) })
    }

    private fun camMove(fn: (Float) -> CamKey) {
        scene.camKeys.clear()
        val n = scene.frames.size
        scene.camKeys[scene.frames.first().id] = fn(0f)
        scene.camKeys[scene.frames.last().id] = fn(if (n > 1) 1f else 0f)
        canvasView.cameraPreview = true
        changed()
    }

    // ── Edicao ───────────────────────────────────────────────────────────────

    private fun editPose(ch: StudioCharacter, mod: (Pose) -> Unit) {
        val p = ch.poseAt(scene, frameIdx).copy()
        mod(p)
        ch.keys[frame.id] = p
        thumbs.clear()
        canvasView.invalidate()
        scheduleSave()
    }

    private fun editCam(mod: (CamKey) -> Unit) {
        val k = scene.cameraAt(frameIdx).copy()
        mod(k)
        scene.camKeys[frame.id] = k
        canvasView.invalidate()
        scheduleSave()
    }

    private fun pushUndo(e: UndoEntry) {
        undo.addLast(e); if (undo.size > 30) undo.removeFirst(); redo.clear()
    }

    private fun swap(from: ArrayDeque<UndoEntry>, to: ArrayDeque<UndoEntry>) {
        val e = from.removeLastOrNull() ?: run { toast("Nada para desfazer"); return }
        to.addLast(UndoEntry(e.frame, e.layerId, e.frame.art[e.layerId]))
        if (e.bitmap == null) e.frame.art.remove(e.layerId) else e.frame.art[e.layerId] = e.bitmap
        storage.saveArt(e.frame.id, e.layerId, e.bitmap)
        thumbs.remove(e.frame.id)
        canvasView.invalidate(); refreshTimeline()
    }

    private fun doUndo() = swap(undo, redo)
    private fun doRedo() = swap(redo, undo)

    private fun syncCanvas() {
        canvasView.scene = scene
        canvasView.frameIndex = frameIdx
        canvasView.activeLayerId = activeLayerId
        canvasView.tool = tool
        canvasView.color = color
        canvasView.size = size
        canvasView.opacity = opacity
        canvasView.selectedCharId = selectedChar
        canvasView.playing = playing
        canvasView.invalidate()
    }

    private val saveTask = Runnable { storage.saveMeta(project); refreshTimeline() }
    private fun scheduleSave() { handler.removeCallbacks(saveTask); handler.postDelayed(saveTask, 600) }

    private fun changed() {
        storage.saveMeta(project)
        syncCanvas(); refreshTimeline(); refreshPanel()
    }

    // ── Reproducao ───────────────────────────────────────────────────────────

    private val playTask = object : Runnable {
        override fun run() {
            if (!playing) return
            holdTick++
            if (holdTick >= frame.hold) {
                holdTick = 0
                frameIdx = (frameIdx + 1) % scene.frames.size
                canvasView.frameIndex = frameIdx
                canvasView.invalidate()
                highlight()
            } else canvasView.invalidate()
            handler.postDelayed(this, (1000L / project.fps))
        }
    }

    private fun highlight() {
        val strip = timelineStrip ?: return
        for (i in 0 until strip.childCount - 1) {
            val sel = i == frameIdx
            strip.getChildAt(i).background = roundBg(if (sel) SC.CARD2 else SC.CARD, requireContext().dp(10).toFloat(), requireContext().dp(if (sel) 2 else 1), if (sel) SC.PINK else SC.BORDER)
        }
        updateTitle()
    }

    private fun togglePlay() { if (playing) stopPlayback() else startPlayback() }

    private fun startPlayback() {
        playing = true; holdTick = 0
        syncCanvas()
        playBtn?.text = "⏸"
        handler.postDelayed(playTask, 1000L / project.fps)
    }

    private fun stopPlayback() {
        if (!playing) return
        playing = false
        handler.removeCallbacks(playTask)
        playBtn?.text = "▶"
        syncCanvas(); refreshTimeline(); refreshPanel()
    }

    // ── Exportacao ───────────────────────────────────────────────────────────

    private fun showExport() {
        stopPlayback()
        val opts = arrayOf("🎞  Video MP4 (1280×720)", "🖼  GIF animado (480×270)", "🗂  Sequencia de imagens PNG")
        AlertDialog.Builder(requireContext()).setTitle("Exportar anime").setItems(opts) { _, w -> runExport(w) }.show()
    }

    private fun runExport(kind: Int) {
        val ctx = requireContext()
        storage.saveMeta(project)
        val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(ctx.dp(24), ctx.dp(16), ctx.dp(24), ctx.dp(8)) }
        val bar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        val txt = TextView(ctx).apply { text = "Renderizando..." }
        box.addView(txt); box.addView(bar)
        val dlg = AlertDialog.Builder(ctx).setTitle("Exportando").setView(box).setCancelable(false).show()
        Thread {
            val result = try {
                val f = when (kind) {
                    0 -> StudioExporter.exportMp4(project, storage.exportDir) { p -> handler.post { bar.progress = (p * 100).toInt() } }
                    1 -> StudioExporter.exportGif(project, storage.exportDir) { p -> handler.post { bar.progress = (p * 100).toInt() } }
                    else -> StudioExporter.exportPng(project, storage.exportDir) { p -> handler.post { bar.progress = (p * 100).toInt() } }
                }
                "Salvo em:\n${f.absolutePath}"
            } catch (e: Throwable) {
                "Nao foi possivel exportar: ${e.message}"
            }
            handler.post {
                if (!isAdded) return@post
                dlg.dismiss()
                AlertDialog.Builder(ctx).setTitle("Exportacao").setMessage(result).setPositiveButton("OK", null).show()
            }
        }.start()
    }

    // ── Util ─────────────────────────────────────────────────────────────────

    private fun prompt(title: String, value: String, on: (String) -> Unit) {
        val ctx = requireContext()
        val et = EditText(ctx).apply { setText(value); setSelection(value.length) }
        val wrap = FrameLayout(ctx).apply { setPadding(ctx.dp(20), ctx.dp(8), ctx.dp(20), 0); addView(et) }
        AlertDialog.Builder(ctx).setTitle(title).setView(wrap)
            .setPositiveButton("Salvar") { _, _ -> et.text.toString().trim().takeIf { it.isNotEmpty() }?.let(on) }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun toast(msg: String) { context?.let { Toast.makeText(it, msg, Toast.LENGTH_SHORT).show() } }
}
