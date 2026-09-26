package com.yumeka.anime.engine.fragments

import android.animation.ObjectAnimator
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yumeka.anime.engine.R
import com.yumeka.anime.engine.views.FrameCanvasView
import com.yumeka.anime.engine.services.EditorProjectStorage
import java.io.File

class EditorFragment : Fragment() {

    private var projectName = "Projeto"
    private var projectPath = ""
    private var storage: EditorProjectStorage? = null

    companion object {
        private const val ARG       = "project_name"
        private const val ARG_PATH  = "project_path"
        private const val DRAWER_MS = 220L
        fun newInstance(name: String, path: String) = EditorFragment().apply {
            arguments = Bundle().apply { putString(ARG, name); putString(ARG_PATH, path) }
        }
    }

    // ── UI refs ───────────────────────────────────────────────────────────────
    private var leftOpen   = false
    private var rightOpen  = false
    private var panelLeft  : View? = null
    private var panelRight : View? = null
    private var overlay    : View? = null
    private var drawerW    = 0
    private var canvasView    : FrameCanvasView? = null
    private var canvasEmpty   : View?            = null
    private var brushToolbar  : View?            = null
    private var colorSwatch   : TextView?        = null
    private var listScenes    : RecyclerView?    = null
    private var listFrames    : RecyclerView?    = null
    private var listKeyframes : RecyclerView?    = null

    // ── Playback ──────────────────────────────────────────────────────────────
    private val playbackHandler = Handler(Looper.getMainLooper())
    private var playbackTask: Runnable? = null
    private var playing = false

    // ── Layers ────────────────────────────────────────────────────────────────
    private var layersVisible   = true
    private var selectedLayerId = R.id.layer_desenho

    // ── Data model ────────────────────────────────────────────────────────────
    data class Cena(val id: Int, var nome: String, var inicio: String, var fim: String)
    data class Frame(
        val id: Int,
        var nome: String,
        var inicio: String,
        var fim: String,
        val keyframes: MutableList<EditorProjectStorage.Keyframe> = mutableListOf()
    )

    private val cenas            = mutableListOf<Cena>()
    private val framesPorCena    = mutableMapOf<Int, MutableList<Frame>>()
    private var cenaSelecionada  : Cena?  = null
    private var frameSelecionado : Frame? = null
    private var kfSelecionado    : EditorProjectStorage.Keyframe? = null

    private var currentBrushColor = Color.BLACK
    private val palette = intArrayOf(
        Color.BLACK, Color.WHITE, Color.RED,
        Color.parseColor("#FF6B35"), Color.YELLOW, Color.GREEN,
        Color.CYAN, Color.BLUE,
        Color.parseColor("#9B6DFF"), Color.parseColor("#FF69B4")
    )

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectName = arguments?.getString(ARG) ?: projectName
        projectPath = arguments?.getString(ARG_PATH).orEmpty()
        if (projectPath.isNotBlank()) storage = EditorProjectStorage(File(projectPath))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?) =
        inflater.inflate(R.layout.fragment_editor, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)

        panelLeft     = view.findViewById(R.id.panel_left)
        panelRight    = view.findViewById(R.id.panel_right)
        overlay       = view.findViewById(R.id.drawer_overlay)
        listScenes    = view.findViewById(R.id.list_scenes)
        listFrames    = view.findViewById(R.id.list_frames)
        listKeyframes = view.findViewById(R.id.list_keyframes)
        canvasView    = view.findViewById(R.id.canvas_view)
        canvasEmpty   = view.findViewById(R.id.canvas_empty_state)
        brushToolbar  = view.findViewById(R.id.brush_toolbar)
        colorSwatch   = view.findViewById(R.id.brush_color_swatch)

        listScenes?.layoutManager    = LinearLayoutManager(requireContext())
        listFrames?.layoutManager    = LinearLayoutManager(requireContext())
        listKeyframes?.layoutManager = LinearLayoutManager(requireContext())

        panelLeft?.post {
            drawerW = panelLeft?.width ?: dp(260)
            panelLeft?.translationX  = -drawerW.toFloat()
            panelRight?.translationX =  drawerW.toFloat()
        }

        setupTopbar(view)
        setupDrawers(view)
        setupBrushToolbar(view)
        setupLayers(view)
        restoreProjectState()

        canvasView?.onArtworkChanged = { saveCurrentKeyframe() }

        updateCanvasState()
        refreshScenes()
        refreshFrames()
        refreshKeyframes()
        canvasView?.post { updateCanvasState() }
    }

    override fun onDestroyView() {
        stopPlayback()
        super.onDestroyView()
        panelLeft = null; panelRight = null; overlay = null
        listScenes = null; listFrames = null; listKeyframes = null
        canvasView?.onArtworkChanged = null
        canvasView = null; canvasEmpty = null; brushToolbar = null; colorSwatch = null
    }

    // ── Canvas ────────────────────────────────────────────────────────────────

    private fun updateCanvasState() {
        val kf     = kfSelecionado
        val active = frameSelecionado != null && kf != null
        canvasView?.frameActive  = active
        canvasEmpty?.visibility  = if (active) View.GONE    else View.VISIBLE
        brushToolbar?.visibility = if (active) View.VISIBLE else View.GONE
        canvasView?.setFrameBitmap(kf?.bitmap)
    }

    private fun saveCurrentKeyframe() {
        val frame = frameSelecionado ?: return
        val kf    = kfSelecionado   ?: return
        kf.bitmap = canvasView?.getFrameBitmap()
        storage?.saveKeyframe(frame.id, kf.id, kf.bitmap)
    }

    // ── Persistência ──────────────────────────────────────────────────────────

    private fun restoreProjectState() {
        val state = storage?.load() ?: return
        cenas.addAll(state.scenes.map { Cena(it.id, it.name, it.start, it.end) })
        state.frames.forEach { saved ->
            val frame = Frame(saved.id, saved.name, saved.start, saved.end, saved.keyframes)
            framesPorCena.getOrPut(saved.sceneId) { mutableListOf() }.add(frame)
        }
        cenaSelecionada = cenas.firstOrNull()
        cenaSelecionada?.let { cena ->
            frameSelecionado = framesPorCena[cena.id]?.firstOrNull()
            kfSelecionado    = frameSelecionado?.keyframes?.firstOrNull()
        }
    }

    private fun persistState() {
        val savedScenes = cenas.map { EditorProjectStorage.Scene(it.id, it.nome, it.inicio, it.fim) }
        val savedFrames = framesPorCena.flatMap { (sceneId, frames) ->
            frames.map { f ->
                EditorProjectStorage.Frame(f.id, sceneId, f.nome, f.inicio, f.fim, f.keyframes)
            }
        }
        storage?.saveState(savedScenes, savedFrames)
    }

    // ── Cenas ─────────────────────────────────────────────────────────────────

    private fun addScene() {
        saveCurrentKeyframe()
        val id    = (cenas.maxOfOrNull { it.id } ?: 0) + 1
        val start = cenas.lastOrNull()?.fim ?: "00:00"
        val scene = Cena(id, "Cena $id", start, nextTime(start))
        cenas.add(scene)
        // ► Cria Cenas/Cena N/Quadros/ em tempo real
        storage?.createSceneFolder(id)
        cenaSelecionada  = scene
        frameSelecionado = null
        kfSelecionado    = null
        persistState()
        refreshScenes(); refreshFrames(); refreshKeyframes(); updateCanvasState()
        openLeft()
    }

    private fun selectScene(scene: Cena) {
        saveCurrentKeyframe()
        cenaSelecionada  = scene
        frameSelecionado = null
        kfSelecionado    = null
        refreshScenes(); refreshFrames(); refreshKeyframes(); updateCanvasState()
    }

    private fun showSceneMenu(scene: Cena) {
        AlertDialog.Builder(requireContext())
            .setItems(arrayOf("Renomear", "Excluir")) { _, which ->
                when (which) {
                    0 -> rename(scene.nome) { scene.nome = it; persistState(); refreshScenes() }
                    1 -> {
                        framesPorCena.remove(scene.id)?.forEach { storage?.deleteFrame(it.id) }
                        storage?.deleteScene(scene.id)
                        cenas.remove(scene)
                        if (cenaSelecionada == scene) {
                            cenaSelecionada  = cenas.firstOrNull()
                            frameSelecionado = null
                            kfSelecionado    = null
                        }
                        persistState()
                        refreshScenes(); refreshFrames(); refreshKeyframes(); updateCanvasState()
                    }
                }
            }.show()
    }

    // ── Quadros ───────────────────────────────────────────────────────────────

    private fun addFrame() {
        val scene = cenaSelecionada ?: run {
            Toast.makeText(context, "Crie uma cena primeiro.", Toast.LENGTH_SHORT).show()
            return
        }
        saveCurrentKeyframe()

        val frames = framesPorCena.getOrPut(scene.id) { mutableListOf() }
        val id     = (framesPorCena.values.flatten().maxOfOrNull { it.id } ?: 0) + 1
        val start  = frames.lastOrNull()?.fim ?: "0:00"

        // ► Cria Cenas/Cena N/Quadros/Quadro M/Keyframes/Keyframe 1.png em tempo real
        val stored = storage?.createFrame(id, scene.id, "Quadro $id", start, nextTimeFrame(start))
            ?: return

        val frame = Frame(stored.id, stored.name, stored.start, stored.end, stored.keyframes)
        frames.add(frame)
        frameSelecionado = frame
        kfSelecionado    = frame.keyframes.firstOrNull()

        persistState()
        refreshFrames(); refreshKeyframes(); updateCanvasState()
    }

    private fun selectFrame(frame: Frame) {
        saveCurrentKeyframe()
        frameSelecionado = frame
        kfSelecionado    = frame.keyframes.firstOrNull()
        refreshFrames(); refreshKeyframes(); updateCanvasState()
    }

    private fun showFrameMenu(frame: Frame) {
        AlertDialog.Builder(requireContext())
            .setItems(arrayOf("Renomear", "Excluir")) { _, which ->
                when (which) {
                    0 -> rename(frame.nome) { newName ->
                        frame.nome = newName
                        storage?.updateFrameMeta(frame.id, newName, frame.inicio, frame.fim)
                        persistState(); refreshFrames()
                    }
                    1 -> {
                        val frameList = cenaSelecionada?.let { framesPorCena[it.id] }
                        frameList?.remove(frame)
                        storage?.deleteFrame(frame.id)
                        if (frameSelecionado == frame) {
                            frameSelecionado = frameList?.firstOrNull()
                            kfSelecionado    = frameSelecionado?.keyframes?.firstOrNull()
                        }
                        persistState()
                        refreshFrames(); refreshKeyframes(); updateCanvasState()
                    }
                }
            }.show()
    }

    // ── Keyframes ─────────────────────────────────────────────────────────────

    private fun addKeyframe() {
        val frame = frameSelecionado ?: run {
            Toast.makeText(context, "Selecione um quadro primeiro.", Toast.LENGTH_SHORT).show()
            return
        }
        saveCurrentKeyframe()
        // ► Cria Keyframe N.png em tempo real
        val kf = storage?.createKeyframe(frame.id) ?: return
        frame.keyframes.add(kf)
        kfSelecionado = kf
        refreshKeyframes(); updateCanvasState()
    }

    private fun selectKeyframe(kf: EditorProjectStorage.Keyframe) {
        saveCurrentKeyframe()
        if (kf.bitmap == null && kf.file.exists() && kf.file.length() > 0) {
            kf.bitmap = storage?.loadKeyframeBitmap(frameSelecionado?.id ?: return, kf.id)
        }
        kfSelecionado = kf
        updateCanvasState(); refreshKeyframes()
    }

    private fun showKeyframeMenu(kf: EditorProjectStorage.Keyframe) {
        val frame = frameSelecionado ?: return
        AlertDialog.Builder(requireContext())
            .setTitle("Keyframe ${kf.id}")
            .setItems(arrayOf("Excluir")) { _, _ ->
                storage?.deleteKeyframe(frame.id, kf.id)
                // Recarrega lista do disco após renumeração
                val kfDir = storage?.keyframesDir(frame.id)
                val updated = kfDir?.listFiles { f -> f.extension.equals("png", ignoreCase = true) }
                    ?.mapNotNull { f ->
                        val n = f.nameWithoutExtension.removePrefix("Keyframe ").toIntOrNull()
                            ?: return@mapNotNull null
                        EditorProjectStorage.Keyframe(n, f, null)
                    }
                    ?.sortedBy { it.id }
                    ?.toMutableList() ?: mutableListOf()
                frame.keyframes.clear()
                frame.keyframes.addAll(updated)
                if (kfSelecionado?.id == kf.id) {
                    kfSelecionado = frame.keyframes.firstOrNull()
                    kfSelecionado?.let { it.bitmap = storage?.loadKeyframeBitmap(frame.id, it.id) }
                }
                refreshKeyframes(); updateCanvasState()
            }.show()
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    private fun refreshScenes()    { listScenes?.adapter  = SceneAdapter(cenas, ::selectScene) }
    private fun refreshFrames()    { listFrames?.adapter  = FrameAdapter(
        cenaSelecionada?.let { framesPorCena[it.id] }.orEmpty(), ::selectFrame) }
    private fun refreshKeyframes() { listKeyframes?.adapter = KeyframeAdapter(
        frameSelecionado?.keyframes.orEmpty(), ::selectKeyframe) }

    // ── Playback ──────────────────────────────────────────────────────────────

    private fun togglePlayback() {
        if (playing) { stopPlayback(); return }
        val frames = cenaSelecionada?.let { framesPorCena[it.id] }.orEmpty()
        if (frames.isEmpty()) {
            Toast.makeText(context, "Adicione quadros para visualizar.", Toast.LENGTH_SHORT).show()
            return
        }
        playing = true
        playbackTask = object : Runnable {
            var frameIdx = 0; var kfIdx = 0
            override fun run() {
                val frame = frames.getOrNull(frameIdx) ?: run { stopPlayback(); return }
                val kf    = frame.keyframes.getOrNull(kfIdx)
                if (kf != null) {
                    if (kf.bitmap == null && kf.file.exists() && kf.file.length() > 0)
                        kf.bitmap = storage?.loadKeyframeBitmap(frame.id, kf.id)
                    canvasView?.setFrameBitmap(kf.bitmap)
                    kfIdx++
                } else { kfIdx = 0; frameIdx = (frameIdx + 1) % frames.size }
                playbackHandler.postDelayed(this, 83)
            }
        }
        playbackHandler.post(playbackTask!!)
    }

    private fun stopPlayback() {
        playing = false
        playbackTask?.let(playbackHandler::removeCallbacks)
        playbackTask = null
    }

    // ── Setup UI ──────────────────────────────────────────────────────────────

    private fun setupTopbar(root: View) {
        root.findViewById<TextView>(R.id.txt_project_name).text = projectName
        root.findViewById<TextView>(R.id.txt_season_episode).text = "Temporada 1 · Episódio 1"
        root.findViewById<TextView>(R.id.btn_editor_back).setOnClickListener { parentFragmentManager.popBackStack() }
        root.findViewById<TextView>(R.id.tab_quadros).setOnClickListener { openLeft() }
        root.findViewById<TextView>(R.id.btn_play).setOnClickListener { togglePlayback() }
    }

    private fun setupDrawers(root: View) {
        root.findViewById<TextView>(R.id.btn_toggle_left).setOnClickListener  { if (leftOpen)  closeLeft()  else openLeft() }
        root.findViewById<TextView>(R.id.btn_toggle_right).setOnClickListener { if (rightOpen) closeRight() else openRight() }
        root.findViewById<TextView>(R.id.btn_close_left).setOnClickListener   { closeLeft() }
        root.findViewById<TextView>(R.id.btn_close_right).setOnClickListener  { closeRight() }
        overlay?.setOnClickListener { closeLeft(); closeRight() }
        root.findViewById<TextView>(R.id.btn_add_frame).setOnClickListener    { addFrame() }
        root.findViewById<TextView>(R.id.btn_add_scene).setOnClickListener    { addScene() }
        root.findViewById<TextView?>(R.id.btn_add_keyframe)?.setOnClickListener { addKeyframe() }
    }

    private fun openLeft()   { if (!leftOpen)  { if (rightOpen) closeRight(); leftOpen  = true;  panelLeft?.visibility  = View.VISIBLE; slide(panelLeft,  -dw(), 0f); showOverlay() } }
    private fun closeLeft()  { if (leftOpen)   { leftOpen  = false; slide(panelLeft,  0f, -dw()) { panelLeft?.visibility  = View.INVISIBLE }; hideOverlayIfClosed() } }
    private fun openRight()  { if (!rightOpen) { if (leftOpen)  closeLeft();  rightOpen = true;  panelRight?.visibility = View.VISIBLE; slide(panelRight,  dw(), 0f); showOverlay() } }
    private fun closeRight() { if (rightOpen)  { rightOpen = false; slide(panelRight, 0f,  dw()) { panelRight?.visibility = View.INVISIBLE }; hideOverlayIfClosed() } }
    private fun dw() = (if (drawerW > 0) drawerW else dp(260)).toFloat()
    private fun slide(view: View?, from: Float, to: Float, done: (() -> Unit)? = null) {
        view ?: return
        ObjectAnimator.ofFloat(view, "translationX", from, to).apply {
            duration = DRAWER_MS
            if (done != null) doOnEnd(done)
            start()
        }
    }
    private fun ObjectAnimator.doOnEnd(action: () -> Unit) =
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(a: android.animation.Animator) = action()
        })
    private fun showOverlay() {
        overlay?.apply { alpha = 0f; visibility = View.VISIBLE; animate().alpha(1f).setDuration(DRAWER_MS).start() }
    }
    private fun hideOverlayIfClosed() {
        if (!leftOpen && !rightOpen)
            overlay?.animate()?.alpha(0f)?.setDuration(DRAWER_MS)
                ?.withEndAction { overlay?.visibility = View.GONE }?.start()
    }

    private fun setupBrushToolbar(root: View) {
        root.findViewById<TextView>(R.id.brush_btn_pencil).setOnClickListener { setEraser(root, false) }
        root.findViewById<TextView>(R.id.brush_btn_eraser).setOnClickListener { setEraser(root, true) }
        root.findViewById<TextView>(R.id.brush_btn_clear).setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Limpar keyframe")
                .setMessage("Apagar todos os traços deste keyframe?")
                .setPositiveButton("Limpar") { _, _ -> canvasView?.clearFrame(); saveCurrentKeyframe() }
                .setNegativeButton("Cancelar", null).show()
        }
        mapOf(R.id.brush_size_small to 3f, R.id.brush_size_medium to 6f, R.id.brush_size_large to 18f).forEach { (id, size) ->
            root.findViewById<TextView>(id).setOnClickListener { canvasView?.brushSize = size }
        }
        colorSwatch?.setOnClickListener { showColorPicker() }
        colorSwatch?.setBackgroundColor(currentBrushColor)
    }

    private fun setEraser(root: View, eraser: Boolean) {
        canvasView?.isEraser = eraser
        root.findViewById<TextView>(R.id.brush_btn_pencil).setBackgroundColor(if (!eraser) 0xFF22223A.toInt() else 0xFF22222E.toInt())
        root.findViewById<TextView>(R.id.brush_btn_eraser).setBackgroundColor(if (eraser)  0xFF22223A.toInt() else 0xFF22222E.toInt())
    }

    private fun showColorPicker() {
        val grid = android.widget.GridLayout(requireContext()).apply {
            columnCount = 5; setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        palette.forEach { color ->
            grid.addView(View(requireContext()).apply {
                setBackgroundColor(color)
                layoutParams = android.widget.GridLayout.LayoutParams().apply {
                    width = dp(44); height = dp(44); setMargins(dp(4), dp(4), dp(4), dp(4))
                }
                setOnClickListener {
                    currentBrushColor = color; canvasView?.brushColor = color
                    canvasView?.isEraser = false; colorSwatch?.setBackgroundColor(color)
                }
            })
        }
        AlertDialog.Builder(requireContext()).setTitle("Escolher cor").setView(grid).setNegativeButton("Fechar", null).show()
    }

    private fun setupLayers(root: View) {
        val layers = listOf(
            R.id.layer_personagem to ("👤" to "Personagem"),
            R.id.layer_3d         to ("🗂" to "3D"),
            R.id.layer_fundo      to ("🖼" to "Fundo"),
            R.id.layer_desenho    to ("✏" to "Desenho"),
            R.id.layer_efeito     to ("✨" to "Efeito"),
            R.id.layer_texto      to ("T"  to "Texto"),
            R.id.layer_audio      to ("🎵" to "Áudio")
        )
        layers.forEach { (id, meta) ->
            root.findViewById<View>(id).apply {
                findViewById<TextView>(R.id.layer_icon).text = meta.first
                findViewById<TextView>(R.id.layer_name).text = meta.second
                setOnClickListener { selectedLayerId = id; updateLayerSelection(root) }
            }
        }
        root.findViewById<TextView>(R.id.btn_layers_visibility).setOnClickListener {
            layersVisible = !layersVisible
            root.findViewById<TextView>(R.id.btn_layers_visibility).text = if (layersVisible) "👁" else "◉"
            layers.forEach { (id, _) -> root.findViewById<View>(id).alpha = if (layersVisible) 1f else .35f }
        }
        updateLayerSelection(root)
    }

    private fun updateLayerSelection(root: View) {
        listOf(R.id.layer_personagem, R.id.layer_3d, R.id.layer_fundo,
               R.id.layer_desenho, R.id.layer_efeito, R.id.layer_texto, R.id.layer_audio)
            .forEach { root.findViewById<View>(it).setBackgroundColor(
                if (it == selectedLayerId) 0xFF1E1E3A.toInt() else Color.TRANSPARENT) }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun rename(value: String, onSave: (String) -> Unit) {
        val input = EditText(requireContext()).apply { setText(value); selectAll() }
        AlertDialog.Builder(requireContext())
            .setTitle("Renomear").setView(input)
            .setPositiveButton("Salvar") { _, _ ->
                input.text.toString().trim().takeIf { it.isNotEmpty() }?.let(onSave)
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun nextTime(time: String)      = shiftTime(time, 15, "%02d:%02d")
    private fun nextTimeFrame(time: String) = shiftTime(time,  3,  "%d:%02d")
    private fun shiftTime(time: String, seconds: Int, format: String) = try {
        val parts = time.split(":")
        val total = parts[0].toInt() * 60 + parts[1].toInt() + seconds
        format.format(total / 60, total % 60)
    } catch (_: NumberFormatException) { if (seconds == 15) "00:15" else "0:03" }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    // ── Adapters ──────────────────────────────────────────────────────────────

    inner class SceneAdapter(
        private val items: List<Cena>, private val onClick: (Cena) -> Unit
    ) : RecyclerView.Adapter<SceneAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.scene_name)
            val time: TextView = v.findViewById(R.id.scene_time)
            val menu: TextView = v.findViewById(R.id.scene_menu)
        }
        override fun onCreateViewHolder(p: ViewGroup, t: Int) =
            VH(LayoutInflater.from(p.context).inflate(R.layout.item_scene_row, p, false))
        override fun onBindViewHolder(h: VH, pos: Int) {
            val s = items[pos]
            h.name.text = s.nome; h.time.text = "${s.inicio} - ${s.fim}"
            h.itemView.setBackgroundColor(if (s == cenaSelecionada) 0xFF1E1E3A.toInt() else Color.TRANSPARENT)
            h.itemView.setOnClickListener { onClick(s) }
            h.menu.setOnClickListener { showSceneMenu(s) }
        }
        override fun getItemCount() = items.size
    }

    inner class FrameAdapter(
        private val items: List<Frame>, private val onClick: (Frame) -> Unit
    ) : RecyclerView.Adapter<FrameAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.frame_name)
            val time: TextView = v.findViewById(R.id.frame_time)
            val menu: TextView = v.findViewById(R.id.frame_menu)
        }
        override fun onCreateViewHolder(p: ViewGroup, t: Int) =
            VH(LayoutInflater.from(p.context).inflate(R.layout.item_frame_row, p, false))
        override fun onBindViewHolder(h: VH, pos: Int) {
            val f = items[pos]
            h.name.text = "${f.nome}  (${f.keyframes.size} kf)"
            h.time.text = "${f.inicio} - ${f.fim}"
            h.itemView.setBackgroundColor(if (f == frameSelecionado) 0xFF1E1E3A.toInt() else Color.TRANSPARENT)
            h.itemView.setOnClickListener { onClick(f) }
            h.menu.setOnClickListener { showFrameMenu(f) }
        }
        override fun getItemCount() = items.size
    }

    inner class KeyframeAdapter(
        private val items: List<EditorProjectStorage.Keyframe>,
        private val onClick: (EditorProjectStorage.Keyframe) -> Unit
    ) : RecyclerView.Adapter<KeyframeAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.frame_name)
            val time: TextView = v.findViewById(R.id.frame_time)
            val menu: TextView = v.findViewById(R.id.frame_menu)
        }
        override fun onCreateViewHolder(p: ViewGroup, t: Int) =
            VH(LayoutInflater.from(p.context).inflate(R.layout.item_frame_row, p, false))
        override fun onBindViewHolder(h: VH, pos: Int) {
            val kf = items[pos]
            h.name.text = "Keyframe ${kf.id}"
            h.time.text = kf.file.name
            h.itemView.setBackgroundColor(if (kf == kfSelecionado) 0xFF1E1E3A.toInt() else Color.TRANSPARENT)
            h.itemView.setOnClickListener { onClick(kf) }
            h.menu.setOnClickListener { showKeyframeMenu(kf) }
        }
        override fun getItemCount() = items.size
    }
}
