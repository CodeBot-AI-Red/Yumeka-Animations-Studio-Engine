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
        private const val ARG = "project_name"
        private const val ARG_PATH = "project_path"
        private const val DRAWER_MS = 220L
        fun newInstance(name: String, path: String) = EditorFragment().apply {
            arguments = Bundle().apply { putString(ARG, name); putString(ARG_PATH, path) }
        }
    }

    private var leftOpen = false
    private var rightOpen = false
    private var panelLeft: View? = null
    private var panelRight: View? = null
    private var overlay: View? = null
    private var drawerW = 0
    private var canvasView: FrameCanvasView? = null
    private var canvasEmpty: View? = null
    private var brushToolbar: View? = null
    private var colorSwatch: TextView? = null
    private var listScenes: RecyclerView? = null
    private var listFrames: RecyclerView? = null
    private val playbackHandler = Handler(Looper.getMainLooper())
    private var playbackTask: Runnable? = null
    private var playing = false
    private var layersVisible = true
    private var selectedLayerId = R.id.layer_desenho

    data class Cena(val id: Int, var nome: String, var inicio: String, var fim: String)
    data class Frame(val id: Int, var nome: String, var inicio: String, var fim: String, var artwork: Bitmap? = null)

    private val cenas = mutableListOf<Cena>()
    private var cenaSelecionada: Cena? = null
    private var frameSelecionado: Frame? = null
    private val framesPorCena = mutableMapOf<Int, MutableList<Frame>>()
    private var currentBrushColor = Color.BLACK
    private val palette = intArrayOf(Color.BLACK, Color.WHITE, Color.RED, Color.parseColor("#FF6B35"), Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.parseColor("#9B6DFF"), Color.parseColor("#FF69B4"))

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
        panelLeft = view.findViewById(R.id.panel_left); panelRight = view.findViewById(R.id.panel_right)
        overlay = view.findViewById(R.id.drawer_overlay); listScenes = view.findViewById(R.id.list_scenes)
        listFrames = view.findViewById(R.id.list_frames); canvasView = view.findViewById(R.id.canvas_view)
        canvasEmpty = view.findViewById(R.id.canvas_empty_state); brushToolbar = view.findViewById(R.id.brush_toolbar)
        colorSwatch = view.findViewById(R.id.brush_color_swatch)
        listScenes?.layoutManager = LinearLayoutManager(requireContext())
        listFrames?.layoutManager = LinearLayoutManager(requireContext())
        panelLeft?.post { drawerW = panelLeft?.width ?: dp(260); panelLeft?.translationX = -drawerW.toFloat(); panelRight?.translationX = drawerW.toFloat() }
        setupTopbar(view); setupDrawers(view); setupBrushToolbar(view); setupLayers(view)
        restoreProjectState()
        canvasView?.onArtworkChanged = { saveCurrentArtwork() }
        updateCanvasState(); refreshScenes(); refreshFrames()
        canvasView?.post { updateCanvasState() }
    }

    override fun onDestroyView() {
        stopPlayback(); super.onDestroyView()
        panelLeft = null; panelRight = null; overlay = null; listScenes = null; listFrames = null
        canvasView?.onArtworkChanged = null
        canvasView = null; canvasEmpty = null; brushToolbar = null; colorSwatch = null
    }

    private fun updateCanvasState() {
        val active = frameSelecionado != null
        canvasView?.frameActive = active
        canvasEmpty?.visibility = if (active) View.GONE else View.VISIBLE
        brushToolbar?.visibility = if (active) View.VISIBLE else View.GONE
        canvasView?.setFrameBitmap(frameSelecionado?.artwork)
    }

    private fun saveCurrentArtwork() {
        val frame = frameSelecionado ?: return
        frame.artwork = canvasView?.getFrameBitmap()
        storage?.saveArtwork(frame.id, frame.artwork)
        persistState()
    }

    private fun restoreProjectState() {
        val state = storage?.load() ?: return
        val loadedScenes = state.scenes.map { Cena(it.id, it.name, it.start, it.end) }
        cenas.addAll(loadedScenes)
        state.frames.forEach { saved ->
            framesPorCena.getOrPut(saved.sceneId) { mutableListOf() }
                .add(Frame(saved.id, saved.name, saved.start, saved.end, saved.artwork))
        }
        cenaSelecionada = cenas.firstOrNull()
    }

    private fun persistState() {
        val savedScenes = cenas.map { EditorProjectStorage.Scene(it.id, it.nome, it.inicio, it.fim) }
        val savedFrames = framesPorCena.flatMap { (sceneId, frames) ->
            frames.map { EditorProjectStorage.Frame(it.id, sceneId, it.nome, it.inicio, it.fim, null) }
        }
        storage?.saveState(savedScenes, savedFrames)
    }

    private fun setupBrushToolbar(root: View) {
        root.findViewById<TextView>(R.id.brush_btn_pencil).setOnClickListener { setEraser(root, false) }
        root.findViewById<TextView>(R.id.brush_btn_eraser).setOnClickListener { setEraser(root, true) }
        root.findViewById<TextView>(R.id.brush_btn_clear).setOnClickListener {
            AlertDialog.Builder(requireContext()).setTitle("Limpar quadro").setMessage("Apagar todos os traços deste quadro?")
                .setPositiveButton("Limpar") { _, _ -> canvasView?.clearFrame(); saveCurrentArtwork() }.setNegativeButton("Cancelar", null).show()
        }
        mapOf(R.id.brush_size_small to 3f, R.id.brush_size_medium to 6f, R.id.brush_size_large to 18f).forEach { (id, size) ->
            root.findViewById<TextView>(id).setOnClickListener { canvasView?.brushSize = size }
        }
        colorSwatch?.setOnClickListener { showColorPicker() }; colorSwatch?.setBackgroundColor(currentBrushColor)
    }

    private fun setEraser(root: View, eraser: Boolean) {
        canvasView?.isEraser = eraser
        root.findViewById<TextView>(R.id.brush_btn_pencil).setBackgroundColor(if (!eraser) 0xFF22223A.toInt() else 0xFF22222E.toInt())
        root.findViewById<TextView>(R.id.brush_btn_eraser).setBackgroundColor(if (eraser) 0xFF22223A.toInt() else 0xFF22222E.toInt())
    }

    private fun showColorPicker() {
        val grid = android.widget.GridLayout(requireContext()).apply { columnCount = 5; setPadding(dp(16), dp(16), dp(16), dp(16)) }
        palette.forEach { color ->
            grid.addView(View(requireContext()).apply { setBackgroundColor(color); layoutParams = android.widget.GridLayout.LayoutParams().apply { width = dp(44); height = dp(44); setMargins(dp(4), dp(4), dp(4), dp(4)) }; setOnClickListener { currentBrushColor = color; canvasView?.brushColor = color; canvasView?.isEraser = false; colorSwatch?.setBackgroundColor(color) } })
        }
        AlertDialog.Builder(requireContext()).setTitle("Escolher cor").setView(grid).setNegativeButton("Fechar", null).show()
    }

    private fun setupTopbar(root: View) {
        root.findViewById<TextView>(R.id.txt_project_name).text = projectName
        root.findViewById<TextView>(R.id.txt_season_episode).text = "Temporada 1 · Episódio 1"
        root.findViewById<TextView>(R.id.btn_editor_back).setOnClickListener { parentFragmentManager.popBackStack() }
        root.findViewById<TextView>(R.id.tab_quadros).setOnClickListener { openLeft() }
        root.findViewById<TextView>(R.id.btn_play).setOnClickListener { togglePlayback() }
    }

    private fun setupDrawers(root: View) {
        root.findViewById<TextView>(R.id.btn_toggle_left).setOnClickListener { if (leftOpen) closeLeft() else openLeft() }
        root.findViewById<TextView>(R.id.btn_toggle_right).setOnClickListener { if (rightOpen) closeRight() else openRight() }
        root.findViewById<TextView>(R.id.btn_close_left).setOnClickListener { closeLeft() }
        root.findViewById<TextView>(R.id.btn_close_right).setOnClickListener { closeRight() }
        overlay?.setOnClickListener { closeLeft(); closeRight() }
        root.findViewById<TextView>(R.id.btn_add_frame).setOnClickListener { addFrame() }
        root.findViewById<TextView>(R.id.btn_add_scene).setOnClickListener { addScene() }
    }

    private fun openLeft() { if (!leftOpen) { if (rightOpen) closeRight(); leftOpen = true; panelLeft?.visibility = View.VISIBLE; slide(panelLeft, -dw(), 0f); showOverlay() } }
    private fun closeLeft() { if (leftOpen) { leftOpen = false; slide(panelLeft, 0f, -dw()) { panelLeft?.visibility = View.INVISIBLE }; hideOverlayIfClosed() } }
    private fun openRight() { if (!rightOpen) { if (leftOpen) closeLeft(); rightOpen = true; panelRight?.visibility = View.VISIBLE; slide(panelRight, dw(), 0f); showOverlay() } }
    private fun closeRight() { if (rightOpen) { rightOpen = false; slide(panelRight, 0f, dw()) { panelRight?.visibility = View.INVISIBLE }; hideOverlayIfClosed() } }
    private fun dw() = (if (drawerW > 0) drawerW else dp(260)).toFloat()
    private fun slide(view: View?, from: Float, to: Float, done: (() -> Unit)? = null) { view ?: return; ObjectAnimator.ofFloat(view, "translationX", from, to).apply { duration = DRAWER_MS; if (done != null) doOnEnd(done); start() } }
    private fun ObjectAnimator.doOnEnd(action: () -> Unit) = addListener(object : android.animation.AnimatorListenerAdapter() { override fun onAnimationEnd(animation: android.animation.Animator) = action() })
    private fun showOverlay() { overlay?.apply { alpha = 0f; visibility = View.VISIBLE; animate().alpha(1f).setDuration(DRAWER_MS).start() } }
    private fun hideOverlayIfClosed() { if (!leftOpen && !rightOpen) overlay?.animate()?.alpha(0f)?.setDuration(DRAWER_MS)?.withEndAction { overlay?.visibility = View.GONE }?.start() }

    private fun setupLayers(root: View) {
        val layers = listOf(R.id.layer_personagem to "👤" to "Personagem", R.id.layer_3d to "🗂" to "3D", R.id.layer_fundo to "🖼" to "Fundo", R.id.layer_desenho to "✏" to "Desenho", R.id.layer_efeito to "✨" to "Efeito", R.id.layer_texto to "T" to "Texto", R.id.layer_audio to "🎵" to "Áudio")
        layers.forEach { (pair, name) -> val (id, icon) = pair; root.findViewById<View>(id).apply { findViewById<TextView>(R.id.layer_icon).text = icon; findViewById<TextView>(R.id.layer_name).text = name; setOnClickListener { selectedLayerId = id; updateLayerSelection(root) } } }
        root.findViewById<TextView>(R.id.btn_layers_visibility).setOnClickListener { layersVisible = !layersVisible; root.findViewById<TextView>(R.id.btn_layers_visibility).text = if (layersVisible) "👁" else "◉"; layers.forEach { (pair, _) -> root.findViewById<View>(pair.first).alpha = if (layersVisible) 1f else .35f } }
        updateLayerSelection(root)
    }
    private fun updateLayerSelection(root: View) { listOf(R.id.layer_personagem, R.id.layer_3d, R.id.layer_fundo, R.id.layer_desenho, R.id.layer_efeito, R.id.layer_texto, R.id.layer_audio).forEach { root.findViewById<View>(it).setBackgroundColor(if (it == selectedLayerId) 0xFF1E1E3A.toInt() else Color.TRANSPARENT) } }

    private fun addScene() { saveCurrentArtwork(); val id = (cenas.maxOfOrNull { it.id } ?: 0) + 1; val start = cenas.lastOrNull()?.fim ?: "00:00"; val scene = Cena(id, "Cena $id", start, nextTime(start)); cenas.add(scene); cenaSelecionada = scene; frameSelecionado = null; persistState(); refreshScenes(); refreshFrames(); updateCanvasState(); openLeft() }
    private fun addFrame() { val scene = cenaSelecionada ?: run { openRight(); Toast.makeText(context, "Crie uma cena primeiro.", Toast.LENGTH_SHORT).show(); return }; saveCurrentArtwork(); val frames = framesPorCena.getOrPut(scene.id) { mutableListOf() }; val id = (framesPorCena.values.flatten().maxOfOrNull { it.id } ?: 0) + 1; val start = frames.lastOrNull()?.fim ?: "0:00"; val frame = Frame(id, "Quadro $id", start, nextTimeFrame(start)); frames.add(frame); storage?.createFrameFolder(id); frameSelecionado = frame; persistState(); refreshFrames(); updateCanvasState() }
    private fun selectScene(scene: Cena) { saveCurrentArtwork(); cenaSelecionada = scene; frameSelecionado = null; refreshScenes(); refreshFrames(); updateCanvasState() }
    private fun selectFrame(frame: Frame) { saveCurrentArtwork(); frameSelecionado = frame; refreshFrames(); updateCanvasState() }
    private fun refreshScenes() { listScenes?.adapter = SceneAdapter(cenas, ::selectScene) }
    private fun refreshFrames() { listFrames?.adapter = FrameAdapter(cenaSelecionada?.let { framesPorCena[it.id] }.orEmpty(), ::selectFrame) }

    private fun showSceneMenu(scene: Cena) = AlertDialog.Builder(requireContext()).setItems(arrayOf("Renomear", "Excluir")) { _, which -> if (which == 0) rename(scene.nome) { scene.nome = it; persistState(); refreshScenes() } else { cenas.remove(scene); framesPorCena.remove(scene.id)?.forEach { storage?.deleteFrame(it.id) }; persistState(); if (cenaSelecionada == scene) { cenaSelecionada = cenas.firstOrNull(); frameSelecionado = null }; refreshScenes(); refreshFrames(); updateCanvasState() } }.show()
    private fun showFrameMenu(frame: Frame) = AlertDialog.Builder(requireContext()).setItems(arrayOf("Renomear", "Excluir")) { _, which -> if (which == 0) rename(frame.nome) { frame.nome = it; persistState(); refreshFrames() } else { val frames = cenaSelecionada?.let { framesPorCena[it.id] }; frames?.remove(frame); storage?.deleteFrame(frame.id); persistState(); if (frameSelecionado == frame) frameSelecionado = frames?.firstOrNull(); refreshFrames(); updateCanvasState() } }.show()
    private fun rename(value: String, onSave: (String) -> Unit) { val input = EditText(requireContext()).apply { setText(value); selectAll() }; AlertDialog.Builder(requireContext()).setTitle("Renomear").setView(input).setPositiveButton("Salvar") { _, _ -> input.text.toString().trim().takeIf { it.isNotEmpty() }?.let(onSave) }.setNegativeButton("Cancelar", null).show() }

    private fun togglePlayback() { if (playing) stopPlayback() else { val frames = cenaSelecionada?.let { framesPorCena[it.id] }.orEmpty(); if (frames.isEmpty()) { Toast.makeText(context, "Adicione quadros para visualizar.", Toast.LENGTH_SHORT).show(); return }; playing = true; playbackTask = object : Runnable { var index = 0; override fun run() { selectFrame(frames[index]); index = (index + 1) % frames.size; playbackHandler.postDelayed(this, 500) } }; playbackHandler.post(playbackTask!!) } }
    private fun stopPlayback() { playing = false; playbackTask?.let(playbackHandler::removeCallbacks); playbackTask = null }
    private fun nextTime(time: String) = shiftTime(time, 15, "%02d:%02d")
    private fun nextTimeFrame(time: String) = shiftTime(time, 3, "%d:%02d")
    private fun shiftTime(time: String, seconds: Int, format: String) = try { val parts = time.split(":"); val total = parts[0].toInt() * 60 + parts[1].toInt() + seconds; format.format(total / 60, total % 60) } catch (_: NumberFormatException) { if (seconds == 15) "00:15" else "0:03" }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    inner class SceneAdapter(private val items: List<Cena>, private val onClick: (Cena) -> Unit) : RecyclerView.Adapter<SceneAdapter.VH>() { inner class VH(v: View) : RecyclerView.ViewHolder(v) { val name: TextView = v.findViewById(R.id.scene_name); val time: TextView = v.findViewById(R.id.scene_time); val menu: TextView = v.findViewById(R.id.scene_menu) }; override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(LayoutInflater.from(p.context).inflate(R.layout.item_scene_row, p, false)); override fun onBindViewHolder(h: VH, pos: Int) { val scene = items[pos]; h.name.text = scene.nome; h.time.text = "${scene.inicio} - ${scene.fim}"; h.itemView.setBackgroundColor(if (scene == cenaSelecionada) 0xFF1E1E3A.toInt() else Color.TRANSPARENT); h.itemView.setOnClickListener { onClick(scene) }; h.menu.setOnClickListener { showSceneMenu(scene) } }; override fun getItemCount() = items.size }
    inner class FrameAdapter(private val items: List<Frame>, private val onClick: (Frame) -> Unit) : RecyclerView.Adapter<FrameAdapter.VH>() { inner class VH(v: View) : RecyclerView.ViewHolder(v) { val name: TextView = v.findViewById(R.id.frame_name); val time: TextView = v.findViewById(R.id.frame_time); val menu: TextView = v.findViewById(R.id.frame_menu) }; override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(LayoutInflater.from(p.context).inflate(R.layout.item_frame_row, p, false)); override fun onBindViewHolder(h: VH, pos: Int) { val frame = items[pos]; h.name.text = frame.nome; h.time.text = "${frame.inicio} - ${frame.fim}"; h.itemView.setBackgroundColor(if (frame == frameSelecionado) 0xFF1E1E3A.toInt() else Color.TRANSPARENT); h.itemView.setOnClickListener { onClick(frame) }; h.menu.setOnClickListener { showFrameMenu(frame) } }; override fun getItemCount() = items.size }
}
