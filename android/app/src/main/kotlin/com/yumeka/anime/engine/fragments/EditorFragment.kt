package com.yumeka.anime.engine.fragments

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yumeka.anime.engine.R

/**
 * EditorFragment — Editor principal do YASE.
 *
 * Conceitos separados (nao confundir):
 *   Cena     = secao/parte do episodio (logica, sem pasta fisica)
 *   Frame    = quadro pertencente a Cena selecionada
 *   Keyframe = ponto de animacao dentro de um Frame
 *
 * Projeto comeca VAZIO — o usuario cria suas proprias cenas e frames.
 * Canvas exibe estado vazio enquanto nao ha frame selecionado.
 */
class EditorFragment : Fragment() {

    private var projectName: String = "Projeto"

    companion object {
        private const val ARG_PROJECT_NAME = "project_name"
        private const val DRAWER_ANIM_MS   = 220L

        fun newInstance(name: String) = EditorFragment().apply {
            arguments = Bundle().also { it.putString(ARG_PROJECT_NAME, name) }
        }
    }

    // Estado dos drawers
    private var leftOpen  = false
    private var rightOpen = false

    // Views
    private var panelLeft:      View? = null
    private var panelRight:     View? = null
    private var overlay:        View? = null
    private var listScenes:     RecyclerView? = null
    private var listFrames:     RecyclerView? = null
    private var canvasEmpty:    View? = null
    private var canvasView:     View? = null

    private var drawerWidthPx = 0

    // -------------------------------------------------------------------------
    // Dados em memoria — COMECAM VAZIOS (usuario cria)
    // -------------------------------------------------------------------------

    data class Cena(val id: Int, var nome: String, var inicio: String, var fim: String)
    data class Frame(val id: Int, var nome: String, var inicio: String, var fim: String)

    private val cenas = mutableListOf<Cena>()
    private var cenaSelecionada: Cena? = null
    private var frameSelecionado: Frame? = null

    private val framesPorCena: MutableMap<Int, MutableList<Frame>> = mutableMapOf()

    // -------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectName = arguments?.getString(ARG_PROJECT_NAME) ?: "Projeto"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_editor, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        panelLeft   = view.findViewById(R.id.panel_left)
        panelRight  = view.findViewById(R.id.panel_right)
        overlay     = view.findViewById(R.id.drawer_overlay)
        listScenes  = view.findViewById(R.id.list_scenes)
        listFrames  = view.findViewById(R.id.list_frames)
        canvasEmpty = view.findViewById(R.id.canvas_empty_state)
        canvasView  = view.findViewById(R.id.canvas_view)

        listScenes?.layoutManager = LinearLayoutManager(requireContext())
        listFrames?.layoutManager = LinearLayoutManager(requireContext())

        panelLeft?.post {
            drawerWidthPx = panelLeft?.width ?: dpToPx(260)
            panelLeft?.translationX  = -drawerWidthPx.toFloat()
            panelRight?.translationX =  drawerWidthPx.toFloat()
        }

        setupTopbar(view)
        setupDrawerControls(view)
        setupTools(view)
        setupLayerNames(view)

        // Canvas comeca no estado vazio
        updateCanvasState()
        refreshScenes()
        refreshFrames()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        panelLeft   = null
        panelRight  = null
        overlay     = null
        listScenes  = null
        listFrames  = null
        canvasEmpty = null
        canvasView  = null
    }

    // -------------------------------------------------------------------------
    // Canvas: estado vazio x frame ativo
    // -------------------------------------------------------------------------

    private fun updateCanvasState() {
        val hasFrame = frameSelecionado != null
        canvasEmpty?.visibility = if (hasFrame) View.GONE else View.VISIBLE
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
            switchTab(root, isQuadros = true)
        }
        root.findViewById<TextView>(R.id.tab_3d)?.setOnClickListener {
            switchTab(root, isQuadros = false)
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

    // -------------------------------------------------------------------------
    // Drawers
    // -------------------------------------------------------------------------

    private fun setupDrawerControls(root: View) {
        root.findViewById<TextView>(R.id.btn_toggle_left)?.setOnClickListener {
            if (leftOpen) closeLeft() else openLeft()
        }
        root.findViewById<TextView>(R.id.btn_toggle_right)?.setOnClickListener {
            if (rightOpen) closeRight() else openRight()
        }
        root.findViewById<TextView>(R.id.btn_close_left)?.setOnClickListener  { closeLeft()  }
        root.findViewById<TextView>(R.id.btn_close_right)?.setOnClickListener { closeRight() }
        overlay?.setOnClickListener {
            if (leftOpen)  closeLeft()
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
        root.findViewById<TextView>(R.id.btn_add_frame)?.setOnClickListener { adicionarFrame() }
        root.findViewById<TextView>(R.id.btn_add_scene)?.setOnClickListener { adicionarCena() }
    }

    private fun openLeft() {
        if (leftOpen) return
        if (rightOpen) closeRight()
        leftOpen = true
        val panel = panelLeft ?: return
        panel.visibility = View.VISIBLE
        animateDrawer(panel, -drawerW(), 0f)
        showOverlay()
    }

    private fun closeLeft() {
        if (!leftOpen) return
        leftOpen = false
        val panel = panelLeft ?: return
        animateDrawer(panel, 0f, -drawerW()) { panel.visibility = View.INVISIBLE }
        hideOverlay()
    }

    private fun openRight() {
        if (rightOpen) return
        if (leftOpen) closeLeft()
        rightOpen = true
        val panel = panelRight ?: return
        panel.visibility = View.VISIBLE
        animateDrawer(panel, drawerW(), 0f)
        showOverlay()
    }

    private fun closeRight() {
        if (!rightOpen) return
        rightOpen = false
        val panel = panelRight ?: return
        animateDrawer(panel, 0f, drawerW()) { panel.visibility = View.INVISIBLE }
        hideOverlay()
    }

    private fun drawerW() =
        if (drawerWidthPx > 0) drawerWidthPx.toFloat() else dpToPx(260).toFloat()

    private fun animateDrawer(panel: View, from: Float, to: Float, onEnd: (() -> Unit)? = null) {
        ObjectAnimator.ofFloat(panel, "translationX", from, to).apply {
            duration = DRAWER_ANIM_MS
            if (onEnd != null) addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) { onEnd() }
            })
            start()
        }
    }

    private fun showOverlay() {
        overlay?.apply { visibility = View.VISIBLE; animate().alpha(1f).setDuration(DRAWER_ANIM_MS).start() }
    }

    private fun hideOverlay() {
        overlay?.animate()?.alpha(0f)?.setDuration(DRAWER_ANIM_MS)
            ?.withEndAction { overlay?.visibility = View.GONE }?.start()
    }

    // -------------------------------------------------------------------------
    // Tabs
    // -------------------------------------------------------------------------

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
    // Ferramentas
    // -------------------------------------------------------------------------

    private fun setupTools(root: View) {
        mapOf(
            R.id.tool_select to "Selecionar",
            R.id.tool_move   to "Mover",
            R.id.tool_rotate to "Rotacionar",
            R.id.tool_crop   to "Recortar",
            R.id.tool_brush  to "Pincel (Quadro)",
            R.id.tool_color  to "Cor",
            R.id.tool_fill   to "Balde",
            R.id.tool_more   to "Mais ferramentas"
        ).forEach { (id, label) ->
            root.findViewById<View>(id)?.setOnClickListener {
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
    // Cenas — usuario cria do zero
    // -------------------------------------------------------------------------

    private fun adicionarCena() {
        val novaId     = (cenas.maxOfOrNull { it.id } ?: 0) + 1
        val inicioPrev = cenas.lastOrNull()?.fim ?: "00:00"
        cenas.add(Cena(novaId, "Cena $novaId", inicioPrev, proximoTempo(inicioPrev)))
        if (cenaSelecionada == null) cenaSelecionada = cenas.first()
        refreshScenes()
        refreshFrames()
        Toast.makeText(context, "Cena $novaId criada", Toast.LENGTH_SHORT).show()
    }

    private fun refreshScenes() {
        listScenes?.adapter = SceneAdapter(cenas) { cena ->
            cenaSelecionada  = cena
            frameSelecionado = null
            refreshFrames()
            updateCanvasState()
        }
    }

    // -------------------------------------------------------------------------
    // Frames — usuario cria do zero
    // -------------------------------------------------------------------------

    private fun adicionarFrame() {
        val cena = cenaSelecionada
        if (cena == null) {
            Toast.makeText(context, "Crie uma Cena primeiro", Toast.LENGTH_SHORT).show()
            return
        }
        val frames     = framesPorCena.getOrPut(cena.id) { mutableListOf() }
        val novaId     = (frames.maxOfOrNull { it.id } ?: 0) + 1
        val inicioPrev = frames.lastOrNull()?.fim ?: "0:00"
        val novo       = Frame(novaId, "Frame $novaId", inicioPrev, proximoTempoFrame(inicioPrev))
        frames.add(novo)
        // Seleciona automaticamente o primeiro frame criado
        if (frameSelecionado == null) frameSelecionado = novo
        refreshFrames()
        updateCanvasState()
        Toast.makeText(context, "Frame $novaId criado", Toast.LENGTH_SHORT).show()
    }

    private fun refreshFrames() {
        val frames = cenaSelecionada?.let { framesPorCena[it.id] } ?: emptyList()
        listFrames?.adapter = FrameAdapter(frames.toList()) { frame ->
            frameSelecionado = frame
            updateCanvasState()
            Toast.makeText(context, "Frame: ${frame.nome}", Toast.LENGTH_SHORT).show()
        }
    }

    // -------------------------------------------------------------------------
    // Utilidades
    // -------------------------------------------------------------------------

    private fun proximoTempo(atual: String): String = try {
        val p = atual.split(":"); var m = p[0].toInt(); var s = p[1].toInt() + 15
        if (s >= 60) { s -= 60; m++ }; "%02d:%02d".format(m, s)
    } catch (_: Exception) { "00:15" }

    private fun proximoTempoFrame(atual: String): String = try {
        val p = atual.split(":"); var m = p[0].toInt(); var s = p[1].toInt() + 3
        if (s >= 60) { s -= 60; m++ }; "%d:%02d".format(m, s)
    } catch (_: Exception) { "0:03" }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

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
            h.name?.text = c.nome
            h.time?.text = "${c.inicio} - ${c.fim}"
            h.itemView.setOnClickListener { onClick(c) }
            h.menu?.setOnClickListener {
                Toast.makeText(context, "Opcoes: ${c.nome}", Toast.LENGTH_SHORT).show()
            }
            h.itemView.setBackgroundColor(
                if (c.id == cenaSelecionada?.id) 0xFF1E1E3A.toInt()
                else android.graphics.Color.TRANSPARENT
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
            h.name?.text = f.nome
            h.time?.text = "${f.inicio} - ${f.fim}"
            h.itemView.setOnClickListener { onClick(f) }
            h.menu?.setOnClickListener {
                Toast.makeText(context, "Opcoes: ${f.nome}", Toast.LENGTH_SHORT).show()
            }
            h.itemView.setBackgroundColor(
                if (f.id == frameSelecionado?.id) 0xFF1E1E3A.toInt()
                else android.graphics.Color.TRANSPARENT
            )
        }
        override fun getItemCount() = items.size
    }
}
