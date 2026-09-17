package com.yumeka.anime.engine.fragments

import android.animation.AnimatorSet
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
 * Layout mobile-first: paineis laterais sao DRAWERS animados.
 * Apenas canvas + ferramentas ficam visiveis por padrao.
 * O usuario abre os drawers pelos botoes na topbar.
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
    private var panelLeft:     View? = null
    private var panelRight:    View? = null
    private var overlay:       View? = null
    private var listScenes:    RecyclerView? = null
    private var listFrames:    RecyclerView? = null

    // Largura do drawer em pixels (calculada apos inflate)
    private var drawerWidthPx = 0

    // -------------------------------------------------------------------------
    // Dados em memoria: Cenas e Frames (nao sao pastas fisicas)
    // -------------------------------------------------------------------------

    data class Cena(val id: Int, val nome: String, val inicio: String, val fim: String)
    data class Frame(val id: Int, val nome: String, val inicio: String, val fim: String)

    private val cenas = mutableListOf(
        Cena(1, "Cena 1", "00:00", "00:15"),
        Cena(2, "Cena 2", "00:15", "00:30"),
        Cena(3, "Cena 3", "00:30", "00:45"),
        Cena(4, "Cena 4", "00:45", "01:00")
    )
    private var cenaSelecionada = cenas.first()

    private val framesPorCena: MutableMap<Int, MutableList<Frame>> = mutableMapOf(
        1 to mutableListOf(
            Frame(1, "Frame 1", "0:00", "0:03"),
            Frame(2, "Frame 2", "0:03", "0:06"),
            Frame(3, "Frame 3", "0:06", "0:09"),
            Frame(4, "Frame 4", "0:09", "0:12"),
            Frame(5, "Frame 5", "0:12", "0:15")
        )
    )

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

        // Referencias dos drawers
        panelLeft  = view.findViewById(R.id.panel_left)
        panelRight = view.findViewById(R.id.panel_right)
        overlay    = view.findViewById(R.id.drawer_overlay)
        listScenes = view.findViewById(R.id.list_scenes)
        listFrames = view.findViewById(R.id.list_frames)

        listScenes?.layoutManager = LinearLayoutManager(requireContext())
        listFrames?.layoutManager = LinearLayoutManager(requireContext())

        // Calcula largura do drawer apos layout
        panelLeft?.post {
            drawerWidthPx = panelLeft?.width ?: dpToPx(260)
            // Garante posicao inicial fora da tela
            panelLeft?.translationX  = -drawerWidthPx.toFloat()
            panelRight?.translationX =  drawerWidthPx.toFloat()
        }

        setupTopbar(view)
        setupDrawerControls(view)
        setupTools(view)
        setupLayerNames(view)

        refreshScenes()
        refreshFrames()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        panelLeft  = null
        panelRight = null
        overlay    = null
        listScenes = null
        listFrames = null
    }

    // -------------------------------------------------------------------------
    // Topbar
    // -------------------------------------------------------------------------

    private fun setupTopbar(root: View) {
        root.findViewById<TextView>(R.id.txt_project_name)?.text    = projectName
        root.findViewById<TextView>(R.id.txt_season_episode)?.text  = "Temporada 1 Episodio 1"

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
        // Botoes toggle na topbar
        root.findViewById<TextView>(R.id.btn_toggle_left)?.setOnClickListener {
            if (leftOpen) closeLeft() else openLeft()
        }
        root.findViewById<TextView>(R.id.btn_toggle_right)?.setOnClickListener {
            if (rightOpen) closeRight() else openRight()
        }

        // Botoes fechar dentro dos drawers
        root.findViewById<TextView>(R.id.btn_close_left)?.setOnClickListener  { closeLeft()  }
        root.findViewById<TextView>(R.id.btn_close_right)?.setOnClickListener { closeRight() }

        // Overlay fecha o drawer aberto
        overlay?.setOnClickListener {
            if (leftOpen)  closeLeft()
            if (rightOpen) closeRight()
        }

        // Keyframes
        root.findViewById<TextView>(R.id.btn_add_keyframe)?.setOnClickListener {
            Toast.makeText(context, "Adicionar keyframe — em breve", Toast.LENGTH_SHORT).show()
        }
        root.findViewById<TextView>(R.id.btn_del_keyframe)?.setOnClickListener {
            Toast.makeText(context, "Excluir keyframe — em breve", Toast.LENGTH_SHORT).show()
        }
        root.findViewById<TextView>(R.id.btn_choose_keyframe)?.setOnClickListener {
            Toast.makeText(context, "Escolher keyframes — em breve", Toast.LENGTH_SHORT).show()
        }

        // Frames e Cenas
        root.findViewById<TextView>(R.id.btn_add_frame)?.setOnClickListener { adicionarFrame() }
        root.findViewById<TextView>(R.id.btn_add_scene)?.setOnClickListener { adicionarCena() }
    }

    private fun openLeft() {
        if (leftOpen) return
        if (rightOpen) closeRight()
        leftOpen = true
        val panel = panelLeft ?: return
        val w = if (drawerWidthPx > 0) drawerWidthPx.toFloat() else dpToPx(260).toFloat()
        panel.visibility = View.VISIBLE
        animateDrawer(panel, -w, 0f)
        showOverlay()
    }

    private fun closeLeft() {
        if (!leftOpen) return
        leftOpen = false
        val panel = panelLeft ?: return
        val w = if (drawerWidthPx > 0) drawerWidthPx.toFloat() else dpToPx(260).toFloat()
        animateDrawer(panel, 0f, -w) {
            panel.visibility = View.INVISIBLE
        }
        hideOverlay()
    }

    private fun openRight() {
        if (rightOpen) return
        if (leftOpen) closeLeft()
        rightOpen = true
        val panel = panelRight ?: return
        val w = if (drawerWidthPx > 0) drawerWidthPx.toFloat() else dpToPx(260).toFloat()
        panel.visibility = View.VISIBLE
        animateDrawer(panel, w, 0f)
        showOverlay()
    }

    private fun closeRight() {
        if (!rightOpen) return
        rightOpen = false
        val panel = panelRight ?: return
        val w = if (drawerWidthPx > 0) drawerWidthPx.toFloat() else dpToPx(260).toFloat()
        animateDrawer(panel, 0f, w) {
            panel.visibility = View.INVISIBLE
        }
        hideOverlay()
    }

    private fun animateDrawer(panel: View, from: Float, to: Float, onEnd: (() -> Unit)? = null) {
        val anim = ObjectAnimator.ofFloat(panel, "translationX", from, to).apply {
            duration = DRAWER_ANIM_MS
        }
        if (onEnd != null) {
            anim.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) { onEnd() }
            })
        }
        anim.start()
    }

    private fun showOverlay() {
        overlay?.apply {
            visibility = View.VISIBLE
            animate().alpha(1f).setDuration(DRAWER_ANIM_MS).start()
        }
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
        data class LayerDef(val viewId: Int, val icon: String, val name: String)
        listOf(
            LayerDef(R.id.layer_personagem, "\uD83D\uDC64", "Personagem"),
            LayerDef(R.id.layer_3d,         "\uD83D\uDDC2", "3D"),
            LayerDef(R.id.layer_fundo,      "\uD83D\uDDBC", "Fundo"),
            LayerDef(R.id.layer_desenho,    "\u270F",        "Desenho"),
            LayerDef(R.id.layer_efeito,     "\u2728",        "Efeito"),
            LayerDef(R.id.layer_texto,      "T",             "Texto"),
            LayerDef(R.id.layer_audio,      "\uD83C\uDFB5", "Audio")
        ).forEach { def ->
            val row = root.findViewById<View>(def.viewId) ?: return@forEach
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

    private fun adicionarCena() {
        val novaId     = (cenas.maxOfOrNull { it.id } ?: 0) + 1
        val inicioPrev = cenas.lastOrNull()?.fim ?: "00:00"
        cenas.add(Cena(novaId, "Cena $novaId", inicioPrev, proximoTempo(inicioPrev)))
        refreshScenes()
        Toast.makeText(context, "Cena $novaId adicionada", Toast.LENGTH_SHORT).show()
    }

    private fun refreshScenes() {
        listScenes?.adapter = SceneAdapter(cenas) { cena ->
            cenaSelecionada = cena
            framesPorCena.getOrPut(cena.id) { mutableListOf() }
            refreshFrames()
        }
    }

    // -------------------------------------------------------------------------
    // Frames
    // -------------------------------------------------------------------------

    private fun adicionarFrame() {
        val frames     = framesPorCena.getOrPut(cenaSelecionada.id) { mutableListOf() }
        val novaId     = (frames.maxOfOrNull { it.id } ?: 0) + 1
        val inicioPrev = frames.lastOrNull()?.fim ?: "0:00"
        frames.add(Frame(novaId, "Frame $novaId", inicioPrev, proximoTempoFrame(inicioPrev)))
        refreshFrames()
        Toast.makeText(context, "Frame $novaId adicionado", Toast.LENGTH_SHORT).show()
    }

    private fun refreshFrames() {
        val frames = framesPorCena.getOrPut(cenaSelecionada.id) { mutableListOf() }
        listFrames?.adapter = FrameAdapter(frames) { frame ->
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
    // Adapters internos
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

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context)
                .inflate(R.layout.item_scene_row, parent, false))

        override fun onBindViewHolder(h: VH, pos: Int) {
            val c = items[pos]
            h.name?.text = c.nome
            h.time?.text = "${c.inicio} - ${c.fim}"
            h.itemView.setOnClickListener { onClick(c) }
            h.menu?.setOnClickListener {
                Toast.makeText(context, "Opcoes: ${c.nome}", Toast.LENGTH_SHORT).show()
            }
            h.itemView.setBackgroundColor(
                if (c.id == cenaSelecionada.id) 0xFF1E1E3A.toInt()
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

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context)
                .inflate(R.layout.item_frame_row, parent, false))

        override fun onBindViewHolder(h: VH, pos: Int) {
            val f = items[pos]
            h.name?.text = f.nome
            h.time?.text = "${f.inicio} - ${f.fim}"
            h.itemView.setOnClickListener { onClick(f) }
            h.menu?.setOnClickListener {
                Toast.makeText(context, "Opcoes: ${f.nome}", Toast.LENGTH_SHORT).show()
            }
        }

        override fun getItemCount() = items.size
    }
}
