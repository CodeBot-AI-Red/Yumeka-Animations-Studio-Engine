package com.yumeka.anime.engine.fragments

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
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
        private const val ARG      = "project_name"
        private const val ARG_PATH = "project_path"
        fun newInstance(name: String, path: String) = EditorFragment().apply {
            arguments = Bundle().apply { putString(ARG, name); putString(ARG_PATH, path) }
        }
    }

    // ── UI refs ───────────────────────────────────────────────────────────────
    private var canvasView    : FrameCanvasView? = null
    private var listFrames    : RecyclerView?    = null
    private var listKeyframes : RecyclerView?    = null

    // ── Playback ──────────────────────────────────────────────────────────────
    private val playbackHandler = Handler(Looper.getMainLooper())
    private var playbackTask: Runnable? = null
    private var playing = false

    // ── Data model ────────────────────────────────────────────────────────────
    data class Frame(
        val id: Int,
        var nome: String,
        var inicio: String,
        var fim: String,
        val keyframes: MutableList<EditorProjectStorage.Keyframe> = mutableListOf()
    )

    private val frames           = mutableListOf<Frame>()
    private var frameSelecionado : Frame? = null
    private var kfSelecionado    : EditorProjectStorage.Keyframe? = null

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

        canvasView    = view.findViewById(R.id.canvas_view)
        listFrames    = view.findViewById(R.id.rv_frames)
        listKeyframes = view.findViewById(R.id.list_keyframes)

        listFrames?.layoutManager    = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        listKeyframes?.layoutManager = LinearLayoutManager(requireContext())

        setupTopbar(view)
        setupStripButtons(view)
        setupToolbar(view)
        restoreProjectState()

        canvasView?.onArtworkChanged = { saveCurrentKeyframe() }

        refreshFrames()
        refreshKeyframes()
        updatePanelProperties(view)
    }

    override fun onDestroyView() {
        stopPlayback()
        super.onDestroyView()
        canvasView?.onArtworkChanged = null
        canvasView    = null
        listFrames    = null
        listKeyframes = null
    }

    // ── Canvas / Painel ───────────────────────────────────────────────────────

    private fun updateCanvasState() {
        canvasView?.setFrameBitmap(kfSelecionado?.bitmap)
    }

    private fun saveCurrentKeyframe() {
        val frame = frameSelecionado ?: return
        val kf    = kfSelecionado   ?: return
        kf.bitmap = canvasView?.getFrameBitmap()
        storage?.saveKeyframe(frame.id, kf.id, kf.bitmap)
    }

    private fun updatePanelProperties(root: View) {
        val frame = frameSelecionado
        root.findViewById<TextView?>(R.id.txt_frame_name)?.text =
            frame?.nome ?: "—"
        root.findViewById<TextView?>(R.id.txt_frame_duration)?.text =
            if (frame != null) "Duração: ${frame.inicio} - ${frame.fim}" else "—"
        root.findViewById<TextView?>(R.id.txt_frame_preview_label)?.text =
            if (frame != null) "Frame ${frame.id}" else ""
        root.findViewById<TextView?>(R.id.txt_frame_count)?.text =
            frames.size.toString()
        root.findViewById<TextView?>(R.id.txt_season_episode)?.text =
            "EP-1  ›  Frame ${frameSelecionado?.id ?: "-"} / ${frames.size}"
    }

    // ── Persistência ──────────────────────────────────────────────────────────

    private fun restoreProjectState() {
        val state = storage?.load() ?: return
        state.frames
            .filter { it.sceneId == 1 }
            .forEach { saved ->
                frames.add(Frame(saved.id, saved.name, saved.start, saved.end, saved.keyframes))
            }
        frameSelecionado = frames.firstOrNull()
        kfSelecionado    = frameSelecionado?.keyframes?.firstOrNull()
        updateCanvasState()
    }

    private fun persistState() {
        val savedScenes = listOf(EditorProjectStorage.Scene(1, projectName, "00:00", "00:00"))
        val savedFrames = frames.map { f ->
            EditorProjectStorage.Frame(f.id, 1, f.nome, f.inicio, f.fim, f.keyframes)
        }
        storage?.saveState(savedScenes, savedFrames)
    }

    // ── Quadros ───────────────────────────────────────────────────────────────

    private fun addFrame() {
        saveCurrentKeyframe()
        val id     = (frames.maxOfOrNull { it.id } ?: 0) + 1
        val start  = frames.lastOrNull()?.fim ?: "0:00"
        val stored = storage?.createFrame(id, 1, "Quadro $id", start, nextTimeFrame(start)) ?: return
        val frame  = Frame(stored.id, stored.name, stored.start, stored.end, stored.keyframes)
        frames.add(frame)
        frameSelecionado = frame
        kfSelecionado    = frame.keyframes.firstOrNull()
        persistState()
        refreshFrames(); refreshKeyframes(); updateCanvasState()
        view?.let { updatePanelProperties(it) }
    }

    private fun selectFrame(frame: Frame) {
        saveCurrentKeyframe()
        frameSelecionado = frame
        kfSelecionado    = frame.keyframes.firstOrNull()
        refreshFrames(); refreshKeyframes(); updateCanvasState()
        view?.let { updatePanelProperties(it) }
    }

    private fun showFrameMenu(frame: Frame) {
        AlertDialog.Builder(requireContext())
            .setItems(arrayOf("Renomear", "Excluir")) { _, which ->
                when (which) {
                    0 -> rename(frame.nome) { newName ->
                        frame.nome = newName
                        storage?.updateFrameMeta(frame.id, newName, frame.inicio, frame.fim)
                        persistState(); refreshFrames()
                        view?.let { updatePanelProperties(it) }
                    }
                    1 -> {
                        storage?.deleteFrame(frame.id)
                        frames.remove(frame)
                        if (frameSelecionado == frame) {
                            frameSelecionado = frames.firstOrNull()
                            kfSelecionado    = frameSelecionado?.keyframes?.firstOrNull()
                        }
                        persistState()
                        refreshFrames(); refreshKeyframes(); updateCanvasState()
                        view?.let { updatePanelProperties(it) }
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
                val kfDir = storage?.keyframesDir(frame.id)
                val updated = kfDir
                    ?.listFiles { f -> f.extension.equals("png", ignoreCase = true) }
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

    private fun refreshFrames() {
        listFrames?.adapter = FrameStripAdapter(frames, ::selectFrame, ::showFrameMenu)
    }

    private fun refreshKeyframes() {
        listKeyframes?.adapter = KeyframeAdapter(
            frameSelecionado?.keyframes.orEmpty(), ::selectKeyframe
        )
    }

    // ── Playback ──────────────────────────────────────────────────────────────

    private fun togglePlayback() {
        if (playing) { stopPlayback(); return }
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
                } else {
                    kfIdx = 0
                    frameIdx = (frameIdx + 1) % frames.size
                }
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
        root.findViewById<TextView?>(R.id.txt_project_name)?.text = projectName
        root.findViewById<TextView?>(R.id.btn_editor_back)?.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        root.findViewById<TextView?>(R.id.btn_play)?.setOnClickListener {
            togglePlayback()
        }
        root.findViewById<TextView?>(R.id.btn_tl_play)?.setOnClickListener {
            togglePlayback()
        }
    }

    private fun setupStripButtons(root: View) {
        root.findViewById<TextView?>(R.id.btn_add_frame)?.setOnClickListener { addFrame() }
        root.findViewById<TextView?>(R.id.btn_frame_delete)?.setOnClickListener   {
            frameSelecionado?.let { showFrameMenu(it) }
        }
        root.findViewById<TextView?>(R.id.btn_frame_more)?.setOnClickListener     {
            frameSelecionado?.let { showFrameMenu(it) }
        }
        root.findViewById<TextView?>(R.id.btn_add_keyframe)?.setOnClickListener   { addKeyframe() }
    }

    private fun setupToolbar(root: View) {
        val toolIds = listOf(
            R.id.tool_select, R.id.tool_brush, R.id.tool_eraser,
            R.id.tool_rect, R.id.tool_ellipse, R.id.tool_text, R.id.tool_layers
        )
        toolIds.forEach { id ->
            root.findViewById<TextView?>(id)?.setOnClickListener {
                // destaca o botão ativo
                toolIds.forEach { tid ->
                    root.findViewById<TextView?>(tid)?.setBackgroundResource(
                        if (tid == id) R.drawable.tool_active_bg else 0
                    )
                }
                when (id) {
                    R.id.tool_eraser -> canvasView?.isEraser = true
                    else             -> { canvasView?.isEraser = false }
                }
            }
        }
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

    private fun nextTimeFrame(time: String) = shiftTime(time, 3)
    private fun shiftTime(time: String, seconds: Int) = try {
        val parts = time.split(":")
        val total = parts[0].toInt() * 60 + parts[1].toInt() + seconds
        "%d:%02d".format(total / 60, total % 60)
    } catch (_: NumberFormatException) { "0:03" }

    // ── Adapters ──────────────────────────────────────────────────────────────

    inner class FrameStripAdapter(
        private val items: List<Frame>,
        private val onClick: (Frame) -> Unit,
        private val onLongClick: (Frame) -> Unit
    ) : RecyclerView.Adapter<FrameStripAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val thumb : ImageView = v.findViewById(R.id.img_frame_thumb)
            val label : TextView  = v.findViewById(R.id.txt_frame_number)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_frame_strip, parent, false))

        override fun onBindViewHolder(h: VH, pos: Int) {
            val f = items[pos]
            h.label.text = "${f.id}"
            // thumbnail: usa o bitmap do primeiro keyframe se disponível
            val bmp = f.keyframes.firstOrNull()?.bitmap
            if (bmp != null) h.thumb.setImageBitmap(bmp)
            else h.thumb.setImageDrawable(null)

            val isActive = f == frameSelecionado
            h.itemView.setBackgroundResource(
                if (isActive) R.drawable.frame_selected_border else R.drawable.frame_normal_border
            )
            h.itemView.setOnClickListener     { onClick(f) }
            h.itemView.setOnLongClickListener { onLongClick(f); true }
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

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_frame_row, parent, false))

        override fun onBindViewHolder(h: VH, pos: Int) {
            val kf = items[pos]
            h.name.text = "Keyframe ${kf.id}"
            h.time.text = kf.file.name
            h.itemView.setBackgroundColor(
                if (kf == kfSelecionado) 0xFF1E1E3A.toInt() else Color.TRANSPARENT
            )
            h.itemView.setOnClickListener { onClick(kf) }
            h.menu.setOnClickListener { showKeyframeMenu(kf) }
        }

        override fun getItemCount() = items.size
    }
}
