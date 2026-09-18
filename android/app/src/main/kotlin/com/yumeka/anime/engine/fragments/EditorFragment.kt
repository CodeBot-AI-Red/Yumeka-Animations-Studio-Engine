package com.yumeka.anime.engine.fragments

import android.animation.ObjectAnimator
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yumeka.anime.engine.R
import com.yumeka.anime.engine.views.FrameCanvasView

/**
 * EditorFragment — Editor principal do YASE.
 *
 * Cenas / Frames / Keyframes sao conceitos logicos separados.
 * Projeto comeca vazio — o usuario cria tudo do zero.
 * Ao selecionar um Frame, aparece o retangulo branco de desenho
 * e o usuario pode desenhar APENAS dentro dele.
 */
class EditorFragment : Fragment() {

    private var projectName = "Projeto"

    companion object {
        private const val ARG = "project_name"
        private const val DRAWER_MS = 220L
        fun newInstance(name: String) = EditorFragment().apply {
            arguments = Bundle().also { it.putString(ARG, name) }
        }
    }

    // Drawers
    private var leftOpen  = false
    private var rightOpen = false
    private var panelLeft:  View? = null
    private var panelRight: View? = null
    private var overlay:    View? = null
    private var drawerW = 0

    // Canvas
    private var canvasView:   FrameCanvasView? = null
    private var canvasEmpty:  View? = null
    private var brushToolbar: View? = null
    private var colorSwatch:  TextView? = null

    // Listas
    private var listScenes: RecyclerView? = null
    private var listFrames: RecyclerView? = null

    // Estado
    data class Cena(val id: Int, var nome: String, var inicio: String, var fim: String)
    data class Frame(val id: Int, var nome: String, var inicio: String, var fim: String)

    private val cenas = mutableListOf<Cena>()
    private var cenaSelecionada:  Cena?  = null
    private var frameSelecionado: Frame? = null
    private val framesPorCena = mutableMapOf<Int, MutableList<Frame>>()

    // Cor atual do pincel
    private var currentBrushColor = Color.BLACK

    // Paleta de cores rapidas
    private val palette = intArrayOf(
        Color.BLACK, Color.WHITE,
        Color.RED, Color.parseColor("#FF6B35"),
        Color.YELLOW, Color.GREEN,
        Color.CYAN, Color.BLUE,
        Color.parseColor("#9B6DFF"), Color.parseColor("#FF69B4")
    )

    // -------------------------------------------------------------------------

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        projectName = arguments?.getString(ARG) ?: "Projeto"
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        i.inflate(R.layout.fragment_editor, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        panelLeft   = view.findViewById(R.id.panel_left)
        panelRight  = view.findViewById(R.id.panel_right)
        overlay     = view.findViewById(R.id.drawer_overlay)
        listScenes  = view.findViewById(R.id.list_scenes)
        listFrames  = view.findViewById(R.id.list_frames)
        canvasView  = view.findViewById(R.id.canvas_view)
        canvasEmpty = view.findViewById(R.id.canvas_empty_state)
        brushToolbar= view.findViewById(R.id.brush_toolbar)
        colorSwatch = view.findViewById(R.id.brush_color_swatch)

        listScenes?.layoutManager = LinearLayoutManager(requireContext())
        listFrames?.layoutManager = LinearLayoutManager(requireContext())

        panelLeft?.post {
            drawerW = panelLeft?.width ?: dp(260)
            panelLeft?.translationX  = -drawerW.toFloat()
            panelRight?.translationX =  drawerW.toFloat()
        }

        setupTopbar(view)
        setupDrawers(view)
        setupTools(view)
        setupBrushToolbar(view)
        setupLayerNames(view)

        updateCanvasState()
        refreshScenes()
        refreshFrames()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        panelLeft = null; panelRight = null; overlay = null
        listScenes = null; listFrames = null
        canvasView = null; canvasEmpty = null; brushToolbar = null; colorSwatch = null
    }

    // -------------------------------------------------------------------------
    // Canvas
    // -------------------------------------------------------------------------

    private fun updateCanvasState() {
        val active = frameSelecionado != null
        canvasView?.frameActive  = active
        canvasEmpty?.visibility  = if (active) View.GONE else View.VISIBLE
        brushToolbar?.visibility = if (active) View.VISIBLE else View.GONE
    }

    // -------------------------------------------------------------------------
    // Mini toolbar de pincel
    // -------------------------------------------------------------------------

    private fun setupBrushToolbar(root: View) {
        // Pincel
        root.findViewById<TextView>(R.id.brush_btn_pencil)?.setOnClickListener {
            canvasView?.isEraser = false
            highlightBrushBtn(root, pencil = true)
        }
        // Borracha
        root.findViewById<TextView>(R.id.brush_btn_eraser)?.setOnClickListener {
            canvasView?.isEraser = true
            highlightBrushBtn(root, pencil = false)
        }
        // Limpar frame
        root.findViewById<TextView>(R.id.brush_btn_clear)?.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Limpar frame")
                .setMessage("Apagar todos os tracos deste frame?")
                .setPositiveButton("Limpar") { _, _ -> canvasView?.clearFrame() }
                .setNegativeButton("Cancelar", null)
                .show()
        }
        // Tamanhos
        root.findViewById<TextView>(R.id.brush_size_small)?.setOnClickListener {
            canvasView?.brushSize = 3f
            Toast.makeText(context, "Pincel fino", Toast.LENGTH_SHORT).show()
        }
        root.findViewById<TextView>(R.id.brush_size_medium)?.setOnClickListener {
            canvasView?.brushSize = 6f
            Toast.makeText(context, "Pincel medio", Toast.LENGTH_SHORT).show()
        }
        root.findViewById<TextView>(R.id.brush_size_large)?.setOnClickListener {
            canvasView?.brushSize = 18f
            Toast.makeText(context, "Pincel grosso", Toast.LENGTH_SHORT).show()
        }
        // Swatch de cor — abre paleta
        colorSwatch?.setOnClickListener { showColorPicker() }
        colorSwatch?.setBackgroundColor(currentBrushColor)
    }

    private fun highlightBrushBtn(root: View, pencil: Boolean) {
        root.findViewById<TextView>(R.id.brush_btn_pencil)
            ?.setBackgroundColor(if (pencil) 0xFF22223A.toInt() else 0xFF22222E.toInt())
        root.findViewById<TextView>(R.id.brush_btn_eraser)
            ?.setBackgroundColor(if (!pencil) 0xFF22223A.toInt() else 0xFF22222E.toInt())
    }

    private fun showColorPicker() {
        val ctx = requireContext()
        val grid = android.widget.GridLayout(ctx).apply {
            columnCount = 5
            setPadding(24, 24, 24, 24)
        }
        palette.forEach { color ->
            val swatch = View(ctx).apply {
                setBackgroundColor(color)
                val size = dp(40)
                layoutParams = android.widget.GridLayout.LayoutParams().apply {
                    width = size; height = size
                    setMargins(4, 4, 4, 4)
                }
            }
            swatch.setOnClickListener {
                currentBrushColor = color
                canvasView?.brushColor = color
                canvasView?.isEraser   = false
                colorSwatch?.setBackgroundColor(color)
            }
            grid.addView(swatch)
        }
        AlertDialog.Builder(ctx)
            .setTitle("Escolher cor")
            .setView(grid)
            .setNegativeButton("Fechar", null)
            .show()
    }

    // -------------------------------------------------------------------------
    // Topbar
    // -------------------------------------------------------------------------

    private fun setupTopbar(root: View) {
        root.findViewById<TextView>(R.id.txt_project_name)?.text   = projectName
        root.findViewById<TextView>(R.id.txt_season_episode)?.text = "Temporada 1 Episodio 1"
        root.findViewById<TextView>(R.id.btn_editor_back)?.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        root.findViewById<TextView>(R.id.tab_quadros)?.setOnClickListener {
            switchTab(root, true)
        }
        root.findViewById<TextView>(R.id.tab_3d)?.setOnClickListener {
            switchTab(root, false)
        }
        root.findViewById<TextView>(R.id.btn_play)?.setOnClickListener {
            Toast.makeText(context, "Reproduzindo episodio...", Toast.LENGTH_SHORT).show()
        }
        root.findViewById<TextView>(R.id.btn_alinhar_ia)?.setOnClickListener {
            Toast.makeText(context, "Alinhar com IA — em breve", Toast.LENGTH_SHORT).show()
        }
        root.findViewById<TextView>(R.id.btn_editar_video)?.setOnClickListener {
            Toast.makeText(context, "Editor de Video — em breve", Toast.LENGTH_SHORT).show()
        }
    }

    private fun switchTab(root: View, isQuadros: Boolean) {
        root.findViewById<TextView>(R.id.tab_quadros)?.apply {
            setBackgroundColor(if (isQuadros) 0xFF9B6DFF.toInt() else 0xFF22222E.toInt())
            setTextColor(if (isQuadros) 0xFFFFFFFF.toInt() else 0xFFA0A0B8.toInt())
        }
        root.findViewById<TextView>(R.id.tab_3d)?.apply {
            setBackgroundColor(if (isQuadros) 0xFF22222E.toInt() else 0xFF9B6DFF.toInt())
            setTextColor(if (isQuadros) 0xFFA0A0B8.toInt() else 0xFFFFFFFF.toInt())
        }
    }

    // -------------------------------------------------------------------------
    // Drawers
    // -------------------------------------------------------------------------

    private fun setupDrawers(root: View) {
        root.findViewById<TextView>(R.id.btn_toggle_left)?.setOnClickListener {
            if (leftOpen) closeLeft() else openLeft()
        }
        root.findViewById<TextView>(R.id.btn_toggle_right)?.setOnClickListener {
            if (rightOpen) closeRight() else openRight()
        }
        root.findViewById<TextView>(R.id.btn_close_left)?.setOnClickListener  { closeLeft()  }
        root.findViewById<TextView>(R.id.btn_close_right)?.setOnClickListener { closeRight() }
        overlay?.setOnClickListener {
            if (leftOpen) closeLeft()
            if (rightOpen) closeRight()
        }
        root.findViewById<TextView>(R.id.btn_add_keyframe)?.setOnClickListener {
            Toast.makeText(context, "Adicionar keyframe — em breve", Toast.LENGTH_SHORT).show()
        }
        root.findViewById<TextView>(R.id.btn_del_keyframe)?.setOnClickListener {
            Toast.makeText(context, "Excluir keyframe — em breve", Toast.LENGTH_SHORT).show()
        }
        root.findViewById<TextView>(R.id.btn_choose_keyframe)?.setOnClickListener {
            Toast.makeText(context, "Escolher keyframes — em breve", Toast.LENGTH_SHORT).show()
        }
        root.findViewById<TextView>(R.id.btn_add_frame)?.setOnClickListener { addFrame() }
        root.findViewById<TextView>(R.id.btn_add_scene)?.setOnClickListener { addScene() }
    }

    private fun openLeft() {
        if (leftOpen) return; if (rightOpen) closeRight()
        leftOpen = true
        panelLeft?.visibility = View.VISIBLE
        slide(panelLeft, -dw(), 0f); showOverlay()
    }
    private fun closeLeft() {
        if (!leftOpen) return; leftOpen = false
        slide(panelLeft, 0f, -dw()) { panelLeft?.visibility = View.INVISIBLE }; hideOverlay()
    }
    private fun openRight() {
        if (rightOpen) return; if (leftOpen) closeLeft()
        rightOpen = true
        panelRight?.visibility = View.VISIBLE
        slide(panelRight, dw(), 0f); showOverlay()
    }
    private fun closeRight() {
        if (!rightOpen) return; rightOpen = false
        slide(panelRight, 0f, dw()) { panelRight?.visibility = View.INVISIBLE }; hideOverlay()
    }
    private fun dw() = if (drawerW > 0) drawerW.toFloat() else dp(260).toFloat()
    private fun slide(v: View?, from: Float, to: Float, onEnd: (() -> Unit)? = null) {
        v ?: return
        ObjectAnimator.ofFloat(v, "translationX", from, to).apply {
            duration = DRAWER_MS
            if (onEnd != null) addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) { onEnd() }
            })
            start()
        }
    }
    private fun showOverlay() {
        overlay?.apply { visibility = View.VISIBLE; animate().alpha(1f).setDuration(DRAWER_MS).start() }
    }
    private fun hideOverlay() {
        overlay?.animate()?.alpha(0f)?.setDuration(DRAWER_MS)
            ?.withEndAction { overlay?.visibility = View.GONE }?.start()
    }

    // -------------------------------------------------------------------------
    // Ferramentas laterais
    // -------------------------------------------------------------------------

    private fun setupTools(root: View) {
        mapOf(
            R.id.tool_select to "Selecionar",
            R.id.tool_move   to "Mover",
            R.id.tool_rotate to "Rotacionar",
            R.id.tool_crop   to "Recortar",
            R.id.tool_brush  to "Pincel",
            R.id.tool_color  to "Cor",
            R.id.tool_fill   to "Balde",
            R.id.tool_more   to "Mais ferramentas"
        ).forEach { (id, label) ->
            root.findViewById<View>(id)?.setOnClickListener {
                // Ativar pincel ao clicar na ferramenta de pincel
                if (id == R.id.tool_brush) {
                    canvasView?.isEraser = false
                }
                Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // -------------------------------------------------------------------------
    // Camadas
    // -------------------------------------------------------------------------

    private fun setupLayerNames(root: View) {
        data class L(val id: Int, val icon: String, val name: String)
        listOf(
            L(R.id.layer_personagem, "\uD83D\uDC64", "Personagem"),
            L(R.id.layer_3d,         "\uD83D\uDDC2", "3D"),
            L(R.id.layer_fundo,      "\uD83D\uDDBC", "Fundo"),
            L(R.id.layer_desenho,    "\u270F",        "Desenho"),
            L(R.id.layer_efeito,     "\u2728",        "Efeito"),
            L(R.id.layer_texto,      "T",             "Texto"),
            L(R.id.layer_audio,      "\uD83C\uDFB5", "Audio")
        ).forEach { def ->
            val row = root.findViewById<View>(def.id) ?: return@forEach
            row.findViewById<TextView>(R.id.layer_icon)?.text = def.icon
            row.findViewById<TextView>(R.id.layer_name)?.text = def.name
            row.setOnClickListener {
                Toast.makeText(context, "Camada: ${def.name}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // -------------------------------------------------------------------------
    // Cenas
    // -------------------------------------------------------------------------

    private fun addScene() {
        val id   = (cenas.maxOfOrNull { it.id } ?: 0) + 1
        val ini  = cenas.lastOrNull()?.fim ?: "00:00"
        cenas.add(Cena(id, "Cena $id", ini, nextTime(ini)))
        if (cenaSelecionada == null) cenaSelecionada = cenas.first()
        refreshScenes(); refreshFrames()
        Toast.makeText(context, "Cena $id criada", Toast.LENGTH_SHORT).show()
    }

    private fun refreshScenes() {
        listScenes?.adapter = SceneAdapter(cenas) { cena ->
            cenaSelecionada  = cena
            frameSelecionado = null
            canvasView?.clearFrame()
            refreshFrames()
            updateCanvasState()
        }
    }

    // -------------------------------------------------------------------------
    // Frames
    // -------------------------------------------------------------------------

    private fun addFrame() {
        val cena = cenaSelecionada ?: run {
            Toast.makeText(context, "Crie uma Cena primeiro", Toast.LENGTH_SHORT).show()
            return
        }
        val frames = framesPorCena.getOrPut(cena.id) { mutableListOf() }
        val id     = (frames.maxOfOrNull { it.id } ?: 0) + 1
        val ini    = frames.lastOrNull()?.fim ?: "0:00"
        val novo   = Frame(id, "Frame $id", ini, nextTimeFrame(ini))
        frames.add(novo)
        if (frameSelecionado == null) {
            frameSelecionado = novo
            canvasView?.clearFrame()
        }
        refreshFrames(); updateCanvasState()
        Toast.makeText(context, "Frame $id criado", Toast.LENGTH_SHORT).show()
    }

    private fun refreshFrames() {
        val frames = cenaSelecionada?.let { framesPorCena[it.id] } ?: emptyList()
        listFrames?.adapter = FrameAdapter(frames.toList()) { frame ->
            frameSelecionado = frame
            canvasView?.clearFrame()
            updateCanvasState()
            Toast.makeText(context, "${frame.nome} selecionado", Toast.LENGTH_SHORT).show()
        }
    }

    // -------------------------------------------------------------------------
    // Utilidades
    // -------------------------------------------------------------------------

    private fun nextTime(t: String) = try {
        val p = t.split(":"); var m = p[0].toInt(); var s = p[1].toInt() + 15
        if (s >= 60) { s -= 60; m++ }; "%02d:%02d".format(m, s)
    } catch (_: Exception) { "00:15" }

    private fun nextTimeFrame(t: String) = try {
        val p = t.split(":"); var m = p[0].toInt(); var s = p[1].toInt() + 3
        if (s >= 60) { s -= 60; m++ }; "%d:%02d".format(m, s)
    } catch (_: Exception) { "0:03" }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // =========================================================================
    // Adapters
    // =========================================================================

    inner class SceneAdapter(
        private val items: List<Cena>,
        private val onClick: (Cena) -> Unit
    ) : RecyclerView.Adapter<SceneAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView? = v.findViewById(R.id.scene_name)
            val time: TextView? = v.findViewById(R.id.scene_time)
            val menu: TextView? = v.findViewById(R.id.scene_menu)
        }
        override fun onCreateViewHolder(p: ViewGroup, t: Int) =
            VH(LayoutInflater.from(p.context).inflate(R.layout.item_scene_row, p, false))
        override fun onBindViewHolder(h: VH, pos: Int) {
            val c = items[pos]
            h.name?.text = c.nome; h.time?.text = "${c.inicio} - ${c.fim}"
            h.itemView.setOnClickListener { onClick(c) }
            h.menu?.setOnClickListener {
                Toast.makeText(context, "Opcoes: ${c.nome}", Toast.LENGTH_SHORT).show()
            }
            h.itemView.setBackgroundColor(
                if (c.id == cenaSelecionada?.id) 0xFF1E1E3A.toInt() else Color.TRANSPARENT
            )
        }
        override fun getItemCount() = items.size
    }

    inner class FrameAdapter(
        private val items: List<Frame>,
        private val onClick: (Frame) -> Unit
    ) : RecyclerView.Adapter<FrameAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView? = v.findViewById(R.id.frame_name)
            val time: TextView? = v.findViewById(R.id.frame_time)
            val menu: TextView? = v.findViewById(R.id.frame_menu)
        }
        override fun onCreateViewHolder(p: ViewGroup, t: Int) =
            VH(LayoutInflater.from(p.context).inflate(R.layout.item_frame_row, p, false))
        override fun onBindViewHolder(h: VH, pos: Int) {
            val f = items[pos]
            h.name?.text = f.nome; h.time?.text = "${f.inicio} - ${f.fim}"
            h.itemView.setOnClickListener { onClick(f) }
            h.menu?.setOnClickListener {
                Toast.makeText(context, "Opcoes: ${f.nome}", Toast.LENGTH_SHORT).show()
            }
            h.itemView.setBackgroundColor(
                if (f.id == frameSelecionado?.id) 0xFF1E1E3A.toInt() else Color.TRANSPARENT
            )
        }
        override fun getItemCount() = items.size
    }
}
